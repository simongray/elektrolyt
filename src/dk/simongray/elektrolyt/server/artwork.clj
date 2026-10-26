(ns dk.simongray.elektrolyt.server.artwork
  "The server's thumbnails of artwork, since most feeds name a single
  artwork of 3000 pixels and a list of episodes draws it at 48.

  An artwork that the server can't fetch or scale gets a redirect to
  itself, so that the page shows the artwork instead."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [dk.simongray.podcast.format.image :as image]
            [dk.simongray.podcast.util.client :as client]
            [dk.simongray.podcast.util.ring :as ring]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.wary-fetch :as http]
            [dk.simongray.wary-fetch.headers :as headers]
            [dk.simongray.wary-fetch.proxy :as proxy]
            [dk.simongray.wary-fetch.url :as url]
            [taoensso.telemere :as tel])
  (:import [java.io File]
           [java.nio.file CopyOption Files StandardCopyOption]
           [java.security MessageDigest]
           [java.util Date HexFormat]
           [java.util.concurrent Semaphore TimeUnit]))

(def defaults
  "The default options of the handler, which the options you give replace
  one by one:

  - :widths, the widths of the thumbnails
  - :cache-dir, the folder that keeps the thumbnails, or nil for none
  - :max-cache-bytes, the most that the folder holds, dropping the
    thumbnails used longest ago first
  - :max-age, the seconds that a browser keeps a thumbnail that isn't
    stale
  - :max-jobs, the most artworks that it fetches and keeps at once, each
    up to :max-bytes
  - :max-scales, the most of those that it scales at once, since each
    can take some 40 MB of heap while it decodes an artwork of 20 MB
  - :wait-seconds, how long a request waits for a job, or for another
    request's fetch of the same artwork, when the folder has no
    thumbnail for it, before it gets a redirect
  - :retry-seconds, how long the folder keeps a failed fetch, so that no
    request fetches the artwork again until then
  - :send, the function that sends a request
  - :allow-pred, a predicate of the URLs that it fetches
  - :max-bytes, the most that an artwork can hold, as Podverse allows
  - :timeout-seconds and :user-agent, which go on to wary-fetch

  The options can hold format.image's :max-pixels too."
  ;; a browser opens about six connections to the server over HTTP/1.1,
  ;; which the proxy shares, so more than four jobs would buy little
  {:widths          artwork/widths
   :cache-dir       nil
   :max-cache-bytes (* 200 1024 1024)
   :max-age         604800
   :max-jobs        4
   :max-scales      2
   :wait-seconds    10
   :retry-seconds   300
   :send            client/send!
   :allow-pred      url/public?
   :max-bytes       (* 20 1024 1024)
   :timeout-seconds 30})

(defn refusal
  "The Ring response that refuses `request`, or nil when the handler
  answers it with `opts` over the defaults. It refuses whatever
  wary-fetch's proxy refuses, and a w parameter that isn't one of the
  :widths."
  ([request]
   (refusal request {}))
  ([request opts]
   (or (proxy/refusal request)
       (let [{:strs [w]} (url/query-params (:query-string request))
             widths      (:widths (merge defaults opts))]
         (when-not (some #(= (str %) w) widths)
           (ring/text-response 400 (str "The w parameter is one of "
                                        (str/join ", " widths) ".")
                               proxy/safety))))))

(defn- sha-256
  "The SHA-256 of the bytes `b`, in hexadecimal."
  [^bytes b]
  (.formatHex (HexFormat/of) (.digest (MessageDigest/getInstance "SHA-256") b)))

(defn- hashed
  "The SHA-256 of the text `s`, in hexadecimal."
  [^String s]
  (sha-256 (.getBytes s "UTF-8")))

(defn- entry-file
  "The entry file in `dir` of the artwork with the hash `h`."
  ^File [dir h]
  (io/file dir (str h ".edn")))

(defn- image-file
  "The file in `dir` that holds the `thumbnail` of the artwork with the
  hash `h`."
  ^File [dir h {:keys [type width]}]
  (io/file dir (str h "-" width (if (= "image/png" type) ".png" ".jpg"))))

(defn- written!
  "Write the bytes `b` to the `file` whole, through a file beside it, so
  that a reader never sees half of it."
  [^File file ^bytes b]
  (let [part (io/file (.getParentFile file) (str (.getName file) ".part"))]
    (io/copy b part)
    (Files/move (.toPath part) (.toPath file)
                (into-array CopyOption [StandardCopyOption/REPLACE_EXISTING
                                        StandardCopyOption/ATOMIC_MOVE]))))

(defn- stored
  "The entry of the artwork at `url` in the folder `dir`, or nil when the
  folder lacks it or a file of its thumbnails."
  [dir url]
  (when dir
    (let [h    (hashed url)
          file (entry-file dir h)]
      (when (.isFile file)
        (try
          (let [entry (edn/read-string (slurp file))]
            (when (and (= url (:url entry))
                       (every? #(.isFile (image-file dir h %))
                               (vals (:thumbnails entry))))
              entry))
          (catch Exception _
            nil))))))

(defn- prune!
  "Delete the artworks in the folder `dir` that were used longest ago
  until it holds no more than `max-bytes`, but never the one with the
  hash `kept`. An artwork was last used when its entry file last changed."
  [^File dir max-bytes kept]
  (let [files  (filter #(.isFile ^File %) (.listFiles dir))
        groups (group-by #(re-find #"^[0-9a-f]+" (.getName ^File %)) files)
        ;; read once, since a sort can throw when touch! changes them
        ;; while it runs
        used   (into {} (for [h (keys groups)]
                          [h (.lastModified (entry-file dir h))]))
        size   #(reduce + (map (fn [^File f] (.length f)) %))]
    (loop [[[_ group :as oldest] & more] (sort-by (comp used key)
                                                  (dissoc groups kept))
           total                         (size files)]
      (when (and oldest (> total max-bytes))
        (let [freed (size group)]
          (run! #(.delete ^File %) group)
          (recur more (- total freed)))))))

(defn- store!
  "Keep the `entry` of an artwork in the folder `dir`, with each distinct
  thumbnail's :data in a file of its own, and drop the artworks used
  longest ago past `max-bytes`.

  It holds the `lock` meanwhile, so that one job's prune never deletes the
  files that another job is still writing. A store that fails, e.g. on a
  full disk, is logged, since the handler can still send the thumbnails
  from their :data."
  [lock dir max-bytes {:keys [url thumbnails] :as entry}]
  (when dir
    (try
      (locking lock
        (let [h (hashed url)]
          (.mkdirs (io/file dir))
          (doseq [thumbnail (vals (into {} (map (juxt :width identity))
                                        (vals thumbnails)))
                  :when     (:data thumbnail)]
            (written! (image-file dir h thumbnail) (:data thumbnail)))
          ;; an artwork that format.image can't scale keeps nil as its
          ;; thumbnails
          (written! (entry-file dir h)
                    (.getBytes (pr-str (cond-> entry
                                         thumbnails (update :thumbnails update-vals
                                                            #(dissoc % :data))))
                               "UTF-8"))
          (prune! (io/file dir) max-bytes h)))
      (catch Exception e
        (tel/log! {:level :warn :id :artwork/store-failed
                   :data  {:url url} :error e}
                  "The thumbnails of the artwork weren't kept")))))

(defn- touch!
  "Mark the artwork at `url` in the folder `dir` as used now."
  [dir url]
  (when dir
    (.setLastModified (entry-file dir (hashed url))
                      (System/currentTimeMillis))))

;; RFC 9111, section 4.2.1, through wary-fetch; Next.js keeps a thumbnail
;; for the larger of 4 hours and the original's max-age
;; https://nextjs.org/docs/app/api-reference/components/image#minimumcachettl
(defn- fresh-until
  "When to check the artwork again, in epoch milliseconds, by the
  `headers` of its response at `now`: when their freshness ends, but no
  sooner than a day and no later than 30 days."
  [headers now]
  (let [day   (* 24 60 60 1000)
        until (some-> (headers/not-before headers (Date. (long now))
                                          {:max-wait-seconds (* 30 86400)})
                      inst-ms)]
    (max (+ now day) (or until 0))))

(defn- with-tags
  "The `thumbnails`, each with an :etag of its bytes."
  [thumbnails]
  (update-vals thumbnails
               (fn [{:keys [data] :as thumbnail}]
                 (let [tag (subs (sha-256 data) 0 32)]
                   (assoc thumbnail :etag (str "\"" tag "\""))))))

(defn- validators
  "The headers that check whether the artwork of the `stale` entry has
  changed. They're nil when the entry lacks a thumbnail at one of the
  `widths`, e.g. one added since it was made, because a 304 would keep the
  entry as it is. An artwork that format.image can't scale, whose
  thumbnails are nil, lacks none."
  [{:keys [etag last-modified thumbnails] :as stale} widths]
  (when (or (nil? thumbnails) (every? #(contains? thumbnails %) widths))
    (cond-> {}
      etag          (assoc "If-None-Match" etag)
      last-modified (assoc "If-Modified-Since" last-modified))))

(defn- scaled
  "The thumbnails of the artwork's bytes `b` at the `widths`, as
  format.image makes them with `opts`, once one of the `scales` is free."
  [^Semaphore scales b widths opts]
  (.acquire scales)
  (try
    (image/thumbnails b widths opts)
    (finally
      (.release scales))))

;; RFC 9110, section 7.6.3: the Via header of wary-fetch's proxy, which
;; refusal refuses, so that an artwork URL that names the handler can't
;; loop
(defn- fetched
  "The entry of the artwork at `url` fetched with `opts` and scaled with
  one of the `scales`, or the `stale` entry renewed when the artwork
  hasn't changed. An entry holds the :url, the artwork's :etag and
  :last-modified, its :fresh-until and its :thumbnails, which are nil when
  format.image can't scale it. It throws when the fetch fails."
  [url stale scales {:keys [send widths] :as opts}]
  (let [request  (http/with-options
                   {:url     url
                    :as      :bytes
                    :headers (merge {"Via" proxy/via}
                                    (validators stale widths))}
                   opts)
        response (send request)
        now      (System/currentTimeMillis)]
    (cond
      (and stale (= 304 (:status response)))
      (assoc stale :fresh-until (fresh-until (:headers response) now))

      (<= 200 (:status response) 299)
      {:url           url
       :etag          (headers/header (:headers response) "etag")
       :last-modified (headers/header (:headers response) "last-modified")
       :fresh-until   (fresh-until (:headers response) now)
       :thumbnails    (some-> (scaled scales (:body response) widths opts)
                              with-tags)}

      :else
      (throw (ex-info (str "The artwork answered " (:status response) ".")
                      {:status (:status response)})))))

(defn- failed
  "The entry of the artwork at `url` after a fetch at `now` failed: the
  `stale` entry, or else a :failed? one, fresh for `retry-seconds` so that
  no request fetches the artwork again until then."
  [url stale now retry-seconds]
  (assoc (or stale {:url url :failed? true})
         :fresh-until (+ now (* 1000 retry-seconds))))

(defn- renewed!
  "The entry of the artwork at `url`, fetched again with `opts` in place
  of the `stale` one, scaled with one of the `scales` and kept in the
  folder through the `lock`, when one of the `jobs` is free. A request
  with stale thumbnails takes a job only if one is free at once, and any
  other waits :wait-seconds for one. Without a job, it's the stale entry,
  or nil.

  The job is held from the fetch to the folder, so that the artworks held
  at once stay at :max-jobs."
  [url stale ^Semaphore jobs scales lock
   {:keys [cache-dir max-cache-bytes wait-seconds retry-seconds] :as opts}]
  (if (.tryAcquire jobs (if (seq (:thumbnails stale)) 0 wait-seconds)
                   TimeUnit/SECONDS)
    (try
      (let [entry (try
                    (fetched url stale scales opts)
                    (catch Exception _
                      (failed url stale (System/currentTimeMillis)
                              retry-seconds)))]
        (store! lock cache-dir max-cache-bytes entry)
        entry)
      (finally
        (.release jobs)))
    stale))

(defn- redirect
  "The redirect to `url`, which a browser keeps for `seconds`, or not at
  all when that's nil."
  [url seconds]
  {:status  307
   :headers (assoc proxy/safety
                   "Location"      (url/legal-url url)
                   "Cache-Control" (if seconds
                                     (str "max-age=" seconds)
                                     "no-store"))
   :body    ""})

(defn- sent
  "The response to `request` with the thumbnail at `width` in `entry`,
  which a browser keeps for `max-age` seconds, or a 304 when the browser
  has it. The thumbnail comes from the folder `dir` unless it holds its
  :data."
  [request entry width dir max-age]
  (let [{:keys [type etag data] :as thumbnail} (get-in entry [:thumbnails width])
        headers (assoc proxy/safety
                       "ETag"          etag
                       "Cache-Control" (str "max-age=" max-age))]
    (if (ring/not-modified? (update-keys (:headers request) str/lower-case)
                            etag nil)
      {:status 304 :headers headers :body ""}
      (ring/response 200 type
                     (or data (image-file dir (hashed (:url entry)) thumbnail))
                     headers))))

(defn- smallest-width
  "The smallest width in `entry` whose thumbnail is as wide as the one at
  `width`, which differs from `width` when the widths above the artwork's
  own share a thumbnail."
  [entry width]
  (let [{:keys [thumbnails]} entry
        shown                (:width (get thumbnails width))]
    (apply min (keep (fn [[w thumbnail]]
                       (when (= shown (:width thumbnail)) w))
                     thumbnails))))

(defn- answer
  "The response to `request` for the thumbnail at `width` of the artwork
  at `url`, by its `entry` and the :cache-dir and :max-age of `opts`."
  [request url entry width {:keys [cache-dir max-age] :as opts}]
  (cond
    (or (nil? entry) (:failed? entry))
    (redirect url nil)

    ;; an artwork that format.image can't scale, or an entry from an
    ;; earlier version, whose thumbnails are empty
    (empty? (:thumbnails entry))
    (redirect url 86400)

    ;; a width that the entry lacks, e.g. one added since it was made
    (nil? (get-in entry [:thumbnails width]))
    (redirect url nil)

    ;; every width above the artwork's own leads to one URL, which a
    ;; browser fetches and keeps once. The URL is relative, so it keeps
    ;; the handler's path
    (< (smallest-width entry width) width)
    (redirect (artwork/thumbnail-url "" url (smallest-width entry width))
              max-age)

    ;; a stale thumbnail goes out at once while its check runs, so the
    ;; browser asks again next time rather than keeping it for max-age
    :else
    (sent request entry width cache-dir
          (if (< (:fresh-until entry) (System/currentTimeMillis)) 0 max-age))))

(defn- shared
  "What `f` returns, called once for all the threads that ask for the key
  `k` while it runs. The atom `running` holds the keys in progress. The
  thread that calls `f` waits for it, and any other for `ms` milliseconds
  at most, after which it gets nil."
  [running k f ms]
  (let [mine (promise)
        now  (get (swap! running #(if (contains? % k) % (assoc % k mine))) k)
        {:keys [value error]}
        (if (identical? now mine)
          (try
            (let [result (try {:value (f)} (catch Exception e {:error e}))]
              (deliver mine result)
              result)
            ;; after an Error too, e.g. out of memory, which goes on up in
            ;; this thread while the others get nil, so that the key is free
            (finally
              (swap! running dissoc k)
              (deliver mine nil)))
          (deref now ms nil))]
    (if error (throw error) value)))

(defn- entry!
  "The entry of the artwork at `url` for its thumbnail at `width`: the one
  in the :cache-dir of `opts` while it's fresh, or else one renewed with
  `jobs`, `scales`, `lock` and `opts`, shared through `running` with the
  requests that ask meanwhile.

  A stale entry with that thumbnail is the answer at once, while the
  renewal runs on a thread of its own. A request that finds another's
  renewal under way waits :wait-seconds for it at most. When the renewal
  throws, it's the stale entry if that has thumbnails."
  [url width jobs scales lock running {:keys [cache-dir wait-seconds] :as opts}]
  (let [stale (stored cache-dir url)
        renew #(renewed! url stale jobs scales lock opts)]
    (cond
      (and stale (< (System/currentTimeMillis) (:fresh-until stale)))
      (do (touch! cache-dir url)
          stale)

      (get-in stale [:thumbnails width])
      (do (future (shared running url renew 0))
          stale)

      :else
      (try
        (or (shared running url renew (* 1000 wait-seconds)) stale)
        (catch Exception _
          (when (seq (:thumbnails stale))
            stale))))))

(defn handler
  "A Ring handler of thumbnails of artwork, with the `opts` over the
  defaults.

  It answers a request with a refusal, or with the thumbnail of the
  artwork at its url parameter at the width of its w parameter. It keeps
  each artwork's thumbnails in the :cache-dir, and once the artwork's
  freshness ends, it checks it again with its ETag and Last-Modified,
  meanwhile sending the thumbnails it has. It fetches at most :max-jobs
  artworks at once and scales at most :max-scales of them, but sends
  cached thumbnails without waiting for a job. After a failed fetch, it
  sends the thumbnails it has and waits :retry-seconds before it fetches
  the artwork again.

  It redirects to the artwork itself when format.image can't scale it,
  or when it has no thumbnails and the fetch fails or waits too long for
  a job. A width above the artwork's own gets a redirect to the smallest
  such width, since they share one thumbnail."
  ([]
   (handler {}))
  ([opts]
   (let [{:keys [max-jobs max-scales] :as opts} (merge defaults opts)
         jobs    (Semaphore. max-jobs)
         scales  (Semaphore. max-scales)
         lock    (Object.)
         running (atom {})]
     (fn [request]
       (or (refusal request opts)
           (let [{:strs [url w]} (url/query-params (:query-string request))
                 width           (parse-long w)]
             (answer request url (entry! url width jobs scales lock running opts)
                     width opts)))))))
