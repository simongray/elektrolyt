(ns dk.simongray.elektrolyt.server.artwork-test
  "The handler of thumbnails of artwork, over a stub of the network and a
  temporary folder: the refusals, the redirects, the cache and its checks
  with the artwork's ETag, the wait after a failure, and the jobs."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dk.simongray.elektrolyt.server.artwork :as artwork]
            [dk.simongray.wary-fetch.proxy :as proxy]
            [dk.simongray.wary-fetch.url :as url]
            [taoensso.telemere :as tel])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- with-temp-dir
  "Call `f` with a fresh temporary directory, a `java.io.File`, and delete
  it with everything in it afterwards."
  [f]
  (let [dir (.toFile (Files/createTempDirectory "artwork-test" (make-array FileAttribute 0)))]
    (try
      (f dir)
      (finally
        (doseq [file (reverse (file-seq dir))]
          (io/delete-file file true))))))

(defn- jpeg
  "The bytes of a JPEG of `w` by `h` pixels, all blue."
  [w h]
  (let [img (java.awt.image.BufferedImage. w h java.awt.image.BufferedImage/TYPE_INT_RGB)
        out (java.io.ByteArrayOutputStream.)]
    (doseq [x (range w) y (range h)]
      (.setRGB img x y (unchecked-int 0xFF2040A0)))
    (javax.imageio.ImageIO/write img "jpeg" out)
    (.toByteArray out)))

(defn- stub
  "A :send that answers each request with the next of the `answers`,
  functions of the request, and logs the requests in the atom `sent`."
  [sent answers]
  (let [left (atom answers)]
    (fn [request]
      (swap! sent conj request)
      (let [answer (first @left)]
        (swap! left rest)
        (answer request)))))

(defn- asked
  "The request of an img for the thumbnail `w` wide of the artwork at
  `url`, with the `headers` over those of an img of the same site."
  ([url w]
   (asked url w {}))
  ([url w headers]
   {:request-method :get
    :query-string   (str "url=" (url/percent-encode url) "&w=" w)
    :headers        (merge {"sec-fetch-mode" "no-cors"
                            "sec-fetch-site" "same-origin"}
                           headers)}))

(defn- body-bytes
  "The bytes of the body of the `response`, from a file or as they are."
  [{:keys [body]}]
  (if (instance? java.io.File body)
    (java.nio.file.Files/readAllBytes (.toPath ^java.io.File body))
    body))

(def art
  "The URL of the artwork of the tests."
  "https://example.org/art.jpg")

(defn- eventually
  "Whether `pred` holds within two seconds."
  [pred]
  (loop [n 0]
    (cond
      (pred)    true
      (< n 200) (do (Thread/sleep 10) (recur (inc n)))
      :else     false)))

(defn- renewed?
  "Whether the folder holds the entry `file` again with a freshness that
  hasn't ended, after a check that runs behind the answer."
  [file]
  (eventually #(pos? (:fresh-until (read-string (slurp file))))))

(deftest refusals
  (let [h (artwork/handler {:send (fn [_] (throw (ex-info "no network" {})))})]
    (is (= 405 (:status (h (assoc (asked art 64) :request-method :post)))))
    (is (= 403 (:status (h (asked art 64 {"sec-fetch-site" "cross-site"}))))
        "a page of another site")
    (is (= 403 (:status (h (asked art 64 {"sec-fetch-mode" "navigate"}))))
        "a link that opens it as a page, so that its redirect sends no reader anywhere")
    (is (= 400 (:status (h (asked "file:///etc/passwd" 64)))))
    (is (= 502 (:status (h (asked art 64 {"via" "1.1 wary-fetch"}))))
        "a request that it sent itself, for an artwork whose url names it")
    (is (= 400 (:status (h (asked art 100)))) "a width outside the list")
    (is (= 400 (:status (h {:request-method :get :query-string "w=64" :headers {}}))))))

(deftest thumbnails-and-their-cache
  (with-temp-dir
    (fn [dir]
      (let [sent    (atom [])
            b       (jpeg 1000 1000)
            ok      (fn [_] {:status  200
                             :headers {"etag"          "\"v1\""
                                       "cache-control" "max-age=0"
                                       "content-type"  "application/octet-stream"}
                             :body    b})
            h       (artwork/handler {:cache-dir (io/file dir "artwork")
                                      :send      (stub sent [ok (fn [_] {:status 304 :headers {}})])})
            made    (h (asked art 128))
            again   (h (asked art 64))]
        (testing "the first request"
          (is (= [200 "image/jpeg" "max-age=604800"]
                 [(:status made) (get-in made [:headers "Content-Type"])
                  (get-in made [:headers "Cache-Control"])]))
          (is (= [128 128] (let [img (javax.imageio.ImageIO/read (java.io.ByteArrayInputStream. (body-bytes made)))]
                             [(.getWidth img) (.getHeight img)])))
          (is (= {:url art :as :bytes :headers {"Via" proxy/via}} (select-keys (first @sent) [:url :as :headers]))
              "with the Via of wary-fetch's proxy, which the handler refuses"))
        (testing "another width, from the folder, without a request, as the server checks the artwork no sooner than a day"
          (is (= [200 1] [(:status again) (count @sent)]))
          (is (= 64 (.getWidth (javax.imageio.ImageIO/read (java.io.ByteArrayInputStream. (body-bytes again)))))))
        (testing "a browser that has the thumbnail"
          (is (= 304 (:status (h (asked art 64 {"if-none-match" (get-in again [:headers "ETag"])}))))))
        (testing "a new handler over the same folder, as after a restart"
          (let [h2 (artwork/handler {:cache-dir (io/file dir "artwork")
                                     :send      (fn [_] (throw (ex-info "no network" {})))})]
            (is (= 200 (:status (h2 (asked art 256)))))))))))

(deftest the-files-of-the-folder
  (with-temp-dir
    (fn [dir]
      (let [cache (io/file dir "artwork")
            h     (artwork/handler {:cache-dir cache
                                    :send      (fn [_] {:status 200 :headers {} :body (jpeg 100 100)})})]
        (h (asked art 64))
        (is (= #{"e7a68e61b8fff8876a78d9970166bfc644d1e84ab1c1b698c1cc9e04c5d679e4.edn"}
               (set (map #(.getName ^java.io.File %)
                         (filter #(re-find #"\.edn$" (.getName ^java.io.File %)) (file-seq cache)))))
            "named by the SHA-256 of the artwork's URL, as podcast-clj's handler named them")))))

(deftest a-check-with-the-etag
  (with-temp-dir
    (fn [dir]
      (let [sent   (atom [])
            cache  (io/file dir "artwork")
            b      (jpeg 200 200)
            h      (artwork/handler {:cache-dir cache
                                     :send      (stub sent [(fn [_] {:status 200 :headers {"etag" "\"v1\""} :body b})
                                                            (fn [_] {:status 304 :headers {}})
                                                            (fn [_] (throw (ex-info "offline" {})))])})
            expire #(spit % (pr-str (assoc (read-string (slurp %)) :fresh-until 0)))]
        (is (= 200 (:status (h (asked art 64)))))
        (let [entry (first (filter #(re-find #"\.edn$" (.getName ^java.io.File %)) (file-seq cache)))]
          (expire entry)
          (is (= 200 (:status (h (asked art 64)))) "the thumbnails at once, while it checks the artwork")
          (is (renewed? entry) "and a 304 keeps them")
          (is (= {"If-None-Match" "\"v1\"" "Via" proxy/via} (:headers (second @sent))))
          (expire entry)
          (is (= 200 (:status (h (asked art 64))))
              "and a failed check sends the thumbnails it has")
          (is (renewed? entry))
          (is (= 3 (count @sent)))
          (is (= 200 (:status (h (asked art 64)))))
          (is (= 3 (count @sent)) "and waits a while before it checks again"))))))

(deftest a-check-with-headers-as-vectors
  (with-temp-dir
    (fn [dir]
      (let [sent    (atom [])
            cache   (io/file dir "artwork")
            date    "Wed, 21 Oct 2026 07:28:00 GMT"
            ok      (fn [_] {:status  200
                             :headers {"etag" ["\"v1\""] "last-modified" [date]}
                             :body    (jpeg 300 300)})
            handler (fn [widths answers]
                      (artwork/handler {:cache-dir cache
                                        :widths    widths
                                        :send      (stub sent answers)}))
            h       (handler [64 128] [ok (fn [_] {:status 304 :headers {}})])
            expire  #(spit % (pr-str (assoc (read-string (slurp %)) :fresh-until 0)))
            entry   #(first (filter (fn [^java.io.File f] (re-find #"\.edn$" (.getName f)))
                                    (file-seq cache)))]
        (is (= 200 (:status (h (asked art 64)))))
        (expire (entry))
        (testing "a response whose headers are vectors of values, as wary-fetch returns them"
          (is (= 200 (:status (h (asked art 64)))))
          (is (renewed? (entry)))
          (is (= {"If-None-Match" "\"v1\"" "If-Modified-Since" date "Via" proxy/via}
                 (:headers (second @sent)))))
        (expire (entry))
        (testing "but none for an entry that lacks a width added since, which a 304 would keep"
          (let [wider (handler [64 128 256] [ok])
                made  (wider (asked art 256))]
            (is (= {"Via" proxy/via} (:headers (nth @sent 2))))
            (is (= 200 (:status made)))
            (is (= 256 (.getWidth (javax.imageio.ImageIO/read
                                   (java.io.ByteArrayInputStream. (body-bytes made))))))))))))

(deftest answers-that-dont-wait-for-a-fetch
  (with-temp-dir
    (fn [dir]
      (let [slow  "https://slow.example/art.jpg"
            gate  (promise)
            hang? (atom false)
            sent  (atom [])
            cache (io/file dir "artwork")
            b     (jpeg 200 200)
            h     (artwork/handler {:cache-dir    cache
                                    :wait-seconds 0
                                    :send         (fn [{:keys [url]}]
                                                    (swap! sent conj url)
                                                    (when @hang? @gate)
                                                    {:status 200 :headers {} :body b})})]
        (h (asked art 64))
        (let [entry (first (filter #(re-find #"\.edn$" (.getName ^java.io.File %)) (file-seq cache)))]
          (spit entry (pr-str (assoc (read-string (slurp entry)) :fresh-until 0))))
        (reset! hang? true)
        (is (= [200 "max-age=0"]
               ((juxt :status #(get-in % [:headers "Cache-Control"]))
                (deref (future (h (asked art 64))) 1000 nil)))
            "the thumbnails of a stale artwork, while its check hangs, which the browser asks for again")
        (let [busy (future (h (asked slow 64)))]
          (is (eventually #(some #{slow} @sent)))
          (is (= [307 "no-store"] ((juxt :status #(get-in % [:headers "Cache-Control"]))
                                   (h (asked slow 64))))
              "and the artwork itself to a request that would wait on another's fetch")
          (deliver gate true)
          (is (= 200 (:status @busy))))))))

(deftest a-folder-that-cant-be-written
  (with-temp-dir
    (fn [dir]
      (let [file (doto (io/file dir "file") (spit ""))
            h    (artwork/handler {:cache-dir (io/file file "artwork")
                                   :send      (fn [_] {:status 200 :headers {} :body (jpeg 200 200)})})
            {made :value :keys [signals]} (tel/with-signals true (h (asked art 64)))]
        (is (= 200 (:status made)) "sends the thumbnails it made all the same")
        (is (= 64 (.getWidth (javax.imageio.ImageIO/read (java.io.ByteArrayInputStream. (body-bytes made))))))
        (is (= [:warn] (keep #(when (= :artwork/store-failed (:id %)) (:level %)) signals))
            "and logs a warning")))))

(deftest two-jobs-over-the-cap
  (with-temp-dir
    (fn [dir]
      (let [cache   (io/file dir "artwork")
            b       (jpeg 200 200)
            handler #(artwork/handler {:cache-dir       cache
                                       :max-cache-bytes %
                                       :send            (fn [_] {:status 200 :headers {} :body b})})
            bytes   #(reduce + (map (fn [^java.io.File f] (.length f)) (rest (file-seq cache))))
            [oldest one two] (map #(str "https://example.org/" % ".jpg") (range 3))
            _       ((handler 1000000) (asked oldest 64))
            h       (handler (long (* 2.5 (bytes))))
            write   @#'artwork/written!
            paused? (atom true)
            writing (promise)
            gate    (promise)]
        ;; the first job stops between its thumbnails and its entry, where
        ;; the second job's prune would take them for the oldest
        (with-redefs-fn {#'artwork/written! (fn [^java.io.File file b]
                                              (when (and (str/ends-with? (.getName file) ".edn")
                                                         (compare-and-set! paused? true false))
                                                (deliver writing true)
                                                @gate)
                                              (write file b))}
          (fn []
            (let [first-job  (future (h (asked one 64)))
                  _          (deref writing 10000 nil)
                  second-job (future (h (asked two 64)))]
              ;; a second for the second job to store, which it can't do
              ;; while the first job holds the lock
              (deref second-job 1000 nil)
              (deliver gate true)
              (is (= [200 200] (map (comp :status deref) [first-job second-job]))))))
        (let [offline (artwork/handler {:cache-dir cache
                                        :send      (fn [_] (throw (ex-info "offline" {})))})]
          (is (= 200 (:status (offline (asked one 64))))
              "the files of an artwork that another job was writing stay")
          (is (= 200 (:status (offline (asked two 64))))))))))

(deftest a-failure-waits-a-while
  (with-temp-dir
    (fn [dir]
      (let [sent   (atom [])
            cache  (io/file dir "artwork")
            h      (artwork/handler {:cache-dir cache
                                     :send      (stub sent [(fn [_] (throw (ex-info "offline" {})))
                                                            (fn [_] {:status 200 :headers {} :body (jpeg 200 200)})])})
            expire #(spit % (pr-str (assoc (read-string (slurp %)) :fresh-until 0)))]
        (is (= [307 "no-store"] ((juxt :status #(get-in % [:headers "Cache-Control"])) (h (asked art 64)))))
        (is (= 307 (:status (h (asked art 128)))))
        (is (= 1 (count @sent)) "without a fetch, though the folder has no thumbnails")
        (run! expire (filter #(re-find #"\.edn$" (.getName ^java.io.File %)) (file-seq cache)))
        (is (= 200 (:status (h (asked art 64)))) "and fetches the artwork after the while")
        (is (= 2 (count @sent)))))))

(deftest an-error-that-isnt-an-exception
  (with-temp-dir
    (fn [dir]
      (let [sent (atom [])
            h    (artwork/handler {:cache-dir    (io/file dir "artwork")
                                   :wait-seconds 1
                                   :send         (stub sent [(fn [_] (throw (StackOverflowError.)))
                                                             (fn [_] {:status 200 :headers {} :body (jpeg 200 200)})])})]
        (is (thrown? StackOverflowError (h (asked art 64))) "goes on up, as an Error")
        (is (= 200 (:status (h (asked art 64))))
            "and leaves the artwork free, so that the next request fetches it")
        (is (= 2 (count @sent)))))))

(deftest jobs-at-most
  (with-temp-dir
    (fn [dir]
      (let [slow    "https://slow.example/art.jpg"
            started (promise)
            gate    (promise)
            sent    (atom [])
            b       (jpeg 200 200)
            h       (artwork/handler {:cache-dir    (io/file dir "artwork")
                                      :max-jobs     1
                                      :wait-seconds 0
                                      :send         (fn [{:keys [url]}]
                                                      (swap! sent conj url)
                                                      (when (= slow url)
                                                        (deliver started true)
                                                        @gate)
                                                      {:status 200 :headers {} :body b})})
            _       (h (asked art 64))
            busy    (future (h (asked slow 64)))]
        @started
        (is (= 200 (:status (h (asked art 128)))) "thumbnails from the folder, without a job")
        (is (= [307 "no-store"] ((juxt :status #(get-in % [:headers "Cache-Control"]))
                                 (h (asked "https://example.org/other.jpg" 64))))
            "and the artwork itself when no job is free")
        (deliver gate true)
        (is (= 200 (:status @busy)))
        (is (= [art slow] @sent))))))

(deftest one-copy-above-the-artworks-width
  (with-temp-dir
    (fn [dir]
      (let [cache (io/file dir "artwork")
            h     (artwork/handler {:cache-dir cache
                                    :send      (fn [_] {:status 200 :headers {} :body (jpeg 100 100)})})
            width #(.getWidth (javax.imageio.ImageIO/read (java.io.ByteArrayInputStream. (body-bytes %))))]
        (is (= 64 (width (h (asked art 64)))))
        (is (= 100 (width (h (asked art 128)))) "the artwork at its own width")
        (is (= [307 (str "?url=" (url/percent-encode art) "&w=128") "max-age=604800"]
               ((juxt :status #(get-in % [:headers "Location"]) #(get-in % [:headers "Cache-Control"]))
                (h (asked art 512))))
            "and a wider width leads to it, so that a browser keeps one copy")
        (is (= 3 (count (rest (file-seq cache)))) "as does the folder, beside the entry")))))

(deftest redirects-to-the-artwork-itself
  (let [sent  (atom [])
        webp  (byte-array [0x52 0x49 0x46 0x46 0 0 0 0 0x57 0x45 0x42 0x50])
        h     (artwork/handler {:send (stub sent [(fn [_] {:status 200 :headers {} :body webp})
                                                  (fn [_] {:status 404 :headers {} :body (byte-array 0)})
                                                  (fn [_] (throw (ex-info "offline" {})))])})
        check (fn [response]
                [(:status response) (get-in response [:headers "Location"])
                 (get-in response [:headers "Cache-Control"])])]
    (is (= [307 art "max-age=86400"] (check (h (asked art 64))))
        "an artwork that format.image can't scale, which a browser keeps for a day")
    (is (= [307 art "no-store"] (check (h (asked art 64)))) "an artwork that isn't there")
    (is (= [307 art "no-store"] (check (h (asked art 64)))) "a fetch that fails")
    (is (= [307 "https://example.org/a%20b.jpg" "no-store"]
           (check ((artwork/handler {:send (fn [_] (throw (ex-info "offline" {})))})
                   (asked "https://example.org/a b.jpg" 64))))
        "a Location that HTTP allows")
    (with-temp-dir
      (fn [dir]
        (let [fetches (atom [])
              cached  (artwork/handler {:cache-dir (io/file dir "artwork")
                                        :send      (stub fetches [(fn [_] {:status 200 :headers {} :body webp})])})]
          (is (= [307 art "max-age=86400"] (check (cached (asked art 64)))))
          (is (= [307 art "max-age=86400"] (check (cached (asked art 128))))
              "and so does the cache that keeps it, at another width")
          (is (= 1 (count @fetches)) "without a fetch"))))
    (with-temp-dir
      (fn [dir]
        (let [fetches (atom [])
              cache   (io/file dir "artwork")
              cached  (artwork/handler {:cache-dir cache
                                        :send      (stub fetches [(fn [_] {:status 200 :headers {"etag" "\"w1\""} :body webp})
                                                                  (fn [_] {:status 304 :headers {}})])})]
          (cached (asked art 64))
          (let [entry (first (filter #(re-find #"\.edn$" (.getName ^java.io.File %)) (file-seq cache)))]
            (spit entry (pr-str (assoc (read-string (slurp entry)) :fresh-until 0))))
          (is (= [307 art "max-age=86400"] (check (cached (asked art 64)))))
          (is (= {"If-None-Match" "\"w1\"" "Via" proxy/via} (:headers (second @fetches)))
              "and checks it with its ETag, since it has no thumbnail to lack"))))))

(deftest the-oldest-go-first
  (with-temp-dir
    (fn [dir]
      (let [cache   (io/file dir "artwork")
            b       (jpeg 1000 1000)
            handler #(artwork/handler {:cache-dir       cache
                                       :max-cache-bytes %
                                       :send            (fn [_] {:status 200 :headers {} :body b})})
            bytes   #(reduce + (map (fn [^java.io.File f] (.length f)) (rest (file-seq cache))))
            urls    (map #(str "https://example.org/" % ".jpg") (range 3))
            _       ((handler 1000000) (asked (first urls) 64))
            cap     (long (* 2.5 (bytes)))
            h       (handler cap)]
        (doseq [u (rest urls)]
          (Thread/sleep 20)
          (is (= 200 (:status (h (asked u 64))))))
        (let [kept (->> (file-seq cache)
                        (filter #(re-find #"\.edn$" (.getName ^java.io.File %)))
                        (map #(:url (read-string (slurp %))))
                        set)]
          (is (= (set (take-last 2 urls)) kept)
              "the two used last, since it drops no more than it must")
          (is (<= (bytes) cap)))))))
