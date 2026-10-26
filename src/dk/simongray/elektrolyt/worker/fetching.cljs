(ns ^:runtime dk.simongray.elektrolyt.worker.fetching
  "Fetching feeds in the app's web worker, for a refresh of the library or
  for a Show page, building the search documents of a feed for a
  subscription, and the page's client of that worker.

  The page and the worker exchange EDN. For a refresh, the worker puts the
  documents of the feeds that changed in the library's store and writes
  nothing else, so the page applies the result to the library. Only one
  worker refreshes at a time across the tabs, and the others answer that
  they're busy. What a refresh found, a feed and its search documents go
  beside the EDN in the library's codec, which the page reads far faster
  than EDN.

  With :alternative-feeds? in a request's options, a feed of DR's own is
  supplemented by its sibling on drpodcast.nu. DR's feeds have no
  validators, so the library keeps checking a supplemented feed by its
  start, which is supplemented too."
  (:require [cljs.reader :as reader]
            [dk.simongray.podcast :as podcast]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed :as feed]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.elektrolyt.library.options :as library-options]
            [dk.simongray.elektrolyt.runtime.perf :as perf]
            [dk.simongray.elektrolyt.search.drpodcast :as drpodcast]))

(def refresh-lock
  "The name of the Web Lock that a refresh holds, which podcast-clj's
  app.worker used, so that tabs of an earlier version take turns with
  this one."
  "dk.simongray.podcast.app.worker/refresh")

;; Web Locks API, W3C Working Draft of 24 September 2025, section 3.2.1:
;; with ifAvailable, the callback gets null when another holds the lock
(defn when-free
  "A promise of what `f` returns while this worker holds the Web Lock
  `name`, or of what `busy` returns when the lock is taken."
  [name f busy]
  (if-let [^js locks (some-> js/globalThis .-navigator .-locks)]
    (.request locks name #js {:ifAvailable true}
              (fn [lock] (if lock (f) (busy))))
    (js/Promise.resolve (f))))

;; HTML, 2.7 Safe passing of structured data: a string is copied as it is,
;; while reading a feed of 2 MB as EDN took the page 120 ms, against 5 ms
;; to decode it in the library's codec
;; https://html.spec.whatwg.org/multipage/structured-data.html#structuredserializeinternal
(defn packed
  "The `message` as the page and the worker post it: its EDN, and its
  :encoded part, a string in the library's codec, beside it."
  [{:keys [encoded] :as message}]
  #js {:edn (pr-str (dissoc message :encoded)) :encoded encoded})

(defn unpacked
  "The message that the page or the worker posted as `data`, with its
  :encoded part, if any, as the string it is."
  [^js data]
  (cond-> (reader/read-string (.-edn data))
    (.-encoded data) (assoc :encoded (.-encoded data))))

(defn coded
  "The value `x` as a string in the library's codec, measured as
  codec/encode with its length."
  [x]
  (let [start (js/performance.now)
        s     ((:encode (:codec library-options/options)) x)]
    (when ^boolean perf/on?
      (perf/measure! "codec/encode" {:chars (count s)} start))
    s))

(defn decoded
  "The value of the string `s` in the library's codec, measured as
  codec/decode with its length."
  [s]
  (perf/measured! "codec/decode" {:chars (count s)}
                  #((:decode (:codec library-options/options)) s)))

(defn post!
  "Post the `message` to the page."
  [message]
  (js/postMessage (packed message)))

(defonce ^{:doc "A promise of drpodcast.nu's slugs by production number, or
  nil until they're fetched."}
  known-slugs
  (atom nil))

(defn slugs!
  "A promise of drpodcast.nu's slugs by production number, fetched once
  per worker, or again after a fetch that failed."
  []
  (or @known-slugs
      (reset! known-slugs
              (-> (library-options/send {:url drpodcast/slugs-url :as :json})
                  (.then #(drpodcast/slugs (:body %)))
                  (.catch (fn [_]
                            (reset! known-slugs nil)
                            {}))))))

(defn supplement
  "The `answer` to a fetch of DR's own feed, with its :feed supplemented by
  the parsed feed `theirs` and without DR's promos of DR Lyd, when both
  are of the same show."
  [{ours :feed :as answer} theirs]
  (cond-> answer
    (drpodcast/same-show? ours theirs)
    (update :feed #(feed/supplemented (drpodcast/without-promos %) theirs))))

(defn sibling!
  "A promise of the parsed feed of the series `slug` on drpodcast.nu,
  fetched with `fetch` and `opts`, or of nil when the fetch fails."
  [fetch opts slug]
  (-> (fetch (drpodcast/feed-url slug) opts)
      (.then #(:feed %))
      (.catch (constantly nil))))

(defn renamed-sibling!
  "A promise of the sibling of DR's feed `ours` under a slug other than
  `slug`, looked up by production number and fetched with `fetch-sibling`,
  or of nil."
  [fetch-sibling slug ours]
  (.then (slugs!)
         (fn [by-number]
           (let [other (get by-number (drpodcast/production-number ours))]
             (when (and other (not= slug other))
               (fetch-sibling other))))))

;; Nearly every series on drpodcast.nu has DR's own slug, so the sibling
;; at that slug is fetched alongside DR's feed, and slugs.json is only for
;; the rest
(defn supplementing
  "A fetch like `fetch` that supplements a feed of DR's own with its
  sibling on drpodcast.nu. The sibling's fetch takes `sibling-opts` and
  the request's options that don't concern DR's feed alone, e.g. :send."
  [fetch sibling-opts]
  (fn [url opts]
    (if-let [slug (drpodcast/official-slug url)]
      (let [fetch-sibling (partial sibling! fetch
                                   (merge (select-keys opts [:send :timeout-seconds :max-bytes
                                                             :allow-pred :signal :lenient-parser])
                                          sibling-opts))
            guessed       (fetch-sibling slug)]
        (.then (fetch url opts)
               (fn [{ours :feed :as answer}]
                 (if ours
                   (-> guessed
                       (.then #(if (drpodcast/same-show? ours %)
                                 %
                                 (renamed-sibling! fetch-sibling slug ours)))
                       (.then #(-> answer
                                   (update :feed drpodcast/marked)
                                   (supplement %))))
                   answer))))
      (fetch url opts))))

;; DR may date its newest episodes days after drpodcast.nu does, so the
;; sibling's start reaches further back for them to pair up
(def sibling-start
  "The options for fetching the start of a sibling on drpodcast.nu, whose
  items fit in the 64 KB that a fetch of a start reads at least."
  {:items 20})

;; Hosts often put an episode's notes in three or four fields at once, and
;; the app reads only the one that resolve/description picks, so the
;; worker keeps that one alone, e.g. a third of the text of the library's
;; feeds, which a tab decodes at each open
(defn leaned
  "A version of the fetch `f`, a function of a URL and its options, whose
  answer's feed keeps only the notes that resolve/description reads."
  [f]
  (fn [url opts]
    (.then (js/Promise.resolve (f url opts))
           #(cond-> % (:feed %) (update :feed resolve/lean)))))

(defn fetches
  "The :fetch and :fetch-head of the worker, whose feeds keep only the
  notes that resolve/description reads, and which supplement DR's own
  feeds when the `opts` of a request have :alternative-feeds?."
  [opts]
  (if (:alternative-feeds? opts)
    {:fetch      (leaned (supplementing podcast/fetch! {}))
     :fetch-head (leaned (supplementing discovery/fetch-head! sibling-start))}
    {:fetch      (leaned podcast/fetch!)
     :fetch-head (leaned discovery/fetch-head!)}))

(defn found!
  "A promise of what a refresh of the feeds that are `due` found, fetched
  with `opts` and the library's own options, posting the progress of the
  request `id` to the page after each feed."
  [id due opts]
  (let [progress (fn [{:keys [done total]}]
                   (post! {:id id :progress {:done done :total total}}))]
    (library/fetch-due! due library-options/store
                        (merge library-options/config library-options/options
                               (dissoc opts :alternative-feeds?)
                               (fetches opts)
                               {:on-feed-fn progress}))))

(defn fetched!
  "A promise of the answer to a fetch of the feed at `url` with `opts`
  through the proxy, without its :body and :report, which the page doesn't
  read, and encoded in the library's codec."
  [url opts]
  (let [fetch (:fetch (fetches opts))]
    (.then (fetch url (merge (select-keys library-options/options [:send])
                             (dissoc opts :alternative-feeds?)))
           #(coded (dissoc % :body :report)))))

(defn documents!
  "A promise of the search documents of the whole feed `encoded` in the
  library's codec, fetched from `url`, as podcast-clj builds them for a
  subscription, encoded in that codec."
  [encoded url]
  ;; in the promise, so that a feed that throws fails this answer alone
  (.then (js/Promise.resolve encoded)
         #(coded (:search-documents (library/with-search-documents (decoded %) url)))))

(defn answer!
  "Answer the page's `request` with what a refresh found, a fetched feed,
  the search documents of a feed, :busy? while another worker refreshes,
  or why it :failed."
  [{:keys [id due fetch documents url opts encoded] :as request}]
  (-> (cond
        due       (when-free refresh-lock
                             #(.then (found! id due opts)
                                     (fn [found]
                                       {:id id :encoded (coded {:found found})}))
                             #(js/Promise.resolve {:id id :busy? true}))
        fetch     (.then (fetched! url opts) (fn [fetched] {:id id :encoded fetched}))
        documents (.then (documents! encoded url) (fn [docs] {:id id :encoded docs})))
      (.then post!)
      (.catch (fn [e] (post! {:id id :failed (str e)})))))

(defn serve!
  "Answer the page's requests in this web worker."
  []
  (set! (.-onmessage js/self)
        (fn [^js e]
          (answer! (unpacked (.-data e))))))

(defn client
  "A client in the page of the web worker at `url`: a map of the :worker,
  its :pending requests by id and the :next id. Once the worker fails, the
  client rejects the pending requests, terminates the worker and calls
  `on-failed`."
  [url on-failed]
  (let [worker  (js/Worker. url)
        pending (atom {})
        settle! (fn [id f]
                  (when-let [{:keys [resolve reject]} (get @pending id)]
                    (swap! pending dissoc id)
                    (try
                      (resolve (f))
                      (catch :default e
                        (reject e)))))]
    (.addEventListener worker "message"
                       (fn [^js e]
                         (let [{:keys [id progress encoded failed] :as answer}
                               (unpacked (.-data e))
                               {:keys [on-progress keep?]} (get @pending id)]
                           (cond
                             progress (some-> on-progress (apply [progress]))
                             failed   (settle! id #(throw (js/Error. failed)))
                             encoded  (settle! id #(cond-> (decoded encoded)
                                                     keep? (vary-meta assoc ::encoded encoded)))
                             :else    (settle! id #(dissoc answer :id))))))
    ;; HTML, sections 10.2.4 and 10.2.5: a worker fires error when its
    ;; script fails to load, and then never runs, or when nothing catches
    ;; what it throws, which may have dropped any request
    ;; https://html.spec.whatwg.org/multipage/workers.html#worker-processing-model
    (.addEventListener worker "error"
                       (fn [^js e]
                         (.terminate worker)
                         (doseq [[id _] @pending]
                           (settle! id #(throw (js/Error. (or (.-message e)
                                                              "The worker failed")))))
                         (on-failed))
                       #js {:once true})
    {:worker worker :pending pending :next (atom 0)}))

(defonce ^{:doc "The client of the app's web worker, or nil before the first
  request and after the worker fails."}
  web-worker
  (atom nil))

(defn web-worker!
  "The client of the app's web worker, made now if there's none."
  []
  (or @web-worker
      (reset! web-worker (client "/js/worker.js" #(reset! web-worker nil)))))

;; A worker takes a quarter of a second to load and start, which a request
;; that starts it waits for, e.g. the whole feed of the session's first new
;; show. One that runs unused takes a heap of its own, which on a phone can
;; make the browser drop background tabs sooner, so it starts only where a
;; request is near.
(defn warm!
  "Start the web worker, unless it runs, so that it's ready for a request
  that's near."
  []
  (web-worker!)
  nil)

(defn ask!
  "A promise of the web worker's answer to `request`, calling `on-progress`
  with each report of progress. It rejects when the worker fails. The
  answer to a fetch keeps the string that it came as, which encoded gives."
  [request on-progress]
  (let [{:keys [^js worker pending next]} (web-worker!)
        id                                (swap! next inc)]
    (js/Promise.
     (fn [resolve reject]
       (swap! pending assoc id {:resolve     resolve
                                :reject      reject
                                :on-progress on-progress
                                :keep?       (:fetch request)})
       (.postMessage worker (packed (assoc request :id id)))))))

(defn encoded
  "The string in the library's codec that the web worker's answer to a
  fetch, `fetched`, came as, or nil for any other value."
  [fetched]
  (::encoded (meta fetched)))

(defn fetch-due!
  "A promise of what a refresh of the feeds that are `due` found in the
  web worker, with `opts`, which must be data: a map of the :found, or of
  :busy? while another tab's worker refreshes. It calls `on-progress` with
  the :done and :total as each fetch ends."
  [due opts on-progress]
  (ask! {:due due :opts opts} on-progress))

(defonce ^{:doc "The promises of the fetches that the web worker hasn't
  answered yet, by URL and options."}
  in-flight
  (atom {}))

;; A subscription while the Show page fetches the same whole feed would
;; fetch it again, and parse and decode it twice, so it waits for that
;; answer instead, as SWR dedupes a request in flight
;; https://swr.vercel.app/docs/advanced/performance
(defn fetch!
  "A promise of the answer to a fetch of the feed at `url` with `opts`, in
  the web worker and without the :body, or of the same answer as an equal
  fetch that's in flight. The `opts` must be data."
  [url opts]
  (let [k [url opts]]
    (or (get @in-flight k)
        (let [answer (.finally (ask! {:fetch true :url url :opts opts} nil)
                               #(swap! in-flight dissoc k))]
          (swap! in-flight assoc k answer)
          answer))))

;; The page sends the feed it subscribes to, e.g. one from the cache, since
;; encoding it takes a fraction of the time that building its documents does
(defn search-documents!
  "A promise of the search documents of the whole feed `fetched` from
  `url`, built in the web worker for a subscription, as podcast-clj's
  library/with-search-documents builds them."
  [fetched url]
  (ask! {:documents true
         :url       url
         :encoded   (or (encoded fetched) (coded fetched))}
        nil))
