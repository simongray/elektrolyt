(ns ^:runtime dk.simongray.elektrolyt.worker.fetching
  "Fetching feeds in the app's web worker, for a refresh of the library or
  for a Show page, and the page's client of that worker.

  The page and the worker exchange EDN. For a refresh, the worker puts the
  documents of the feeds that changed in the library's store and writes
  nothing else, so the page applies the result to the library. Only one
  worker refreshes at a time across the tabs, and the others answer that
  they're busy. A fetched feed comes back in the library's codec, since
  EDN reads slower than the feed parses.

  With :alternative-feeds? in a request's options, a feed of DR's own is
  supplemented by its sibling on drpodcast.nu. DR's feeds have no
  validators, so the library keeps checking a supplemented feed by its
  start, which is supplemented too."
  (:require [cljs.reader :as reader]
            [dk.simongray.podcast :as podcast]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed :as feed]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.elektrolyt.library :as app-library]
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

(defn post!
  "Post the `message` to the page as EDN."
  [message]
  (js/postMessage (pr-str message)))

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
              (-> (app-library/send {:url drpodcast/slugs-url :as :json})
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

(defn fetches
  "The :fetch and :fetch-head that supplement DR's own feeds when the
  `opts` of a request have :alternative-feeds?, or else nil."
  [opts]
  (when (:alternative-feeds? opts)
    {:fetch      (supplementing podcast/fetch! {})
     :fetch-head (supplementing discovery/fetch-head! sibling-start)}))

(defn found!
  "A promise of what a refresh of the feeds that are `due` found, fetched
  with `opts` and the library's own options, posting the progress of the
  request `id` to the page after each feed."
  [id due opts]
  (let [progress (fn [{:keys [done total]}]
                   (post! {:id id :progress {:done done :total total}}))]
    (library/fetch-due! due app-library/store
                        (merge app-library/config app-library/options
                               (dissoc opts :alternative-feeds?)
                               (fetches opts)
                               {:on-feed-fn progress}))))

(defn fetched!
  "A promise of the answer to a fetch of the feed at `url` with `opts`
  through the proxy, without its :body and encoded in the library's codec."
  [url opts]
  (let [fetch (:fetch (fetches opts) podcast/fetch!)]
    (.then (fetch url (merge (select-keys app-library/options [:send])
                             (dissoc opts :alternative-feeds?)))
           #((:encode (:codec app-library/options)) (dissoc % :body)))))

(defn answer!
  "Answer the page's `request` with what a refresh found, a fetched feed,
  :busy? while another worker refreshes, or why it :failed."
  [{:keys [id due fetch url opts] :as request}]
  (-> (cond
        due   (when-free refresh-lock
                         #(.then (found! id due opts) (fn [found] {:id id :found found}))
                         #(js/Promise.resolve {:id id :busy? true}))
        fetch (.then (fetched! url opts) (fn [fetched] {:id id :fetched fetched})))
      (.then post!)
      (.catch (fn [e] (post! {:id id :failed (str e)})))))

(defn serve!
  "Answer the page's requests in this web worker."
  []
  (set! (.-onmessage js/self)
        (fn [^js e]
          (answer! (reader/read-string (.-data e))))))

(defn client
  "A client in the page of the web worker at `url`: a map of the :worker,
  its :pending requests by id and the :next id. Once the worker fails, the
  client rejects the pending requests, terminates the worker and calls
  `on-failed`."
  [url on-failed]
  (let [worker  (js/Worker. url)
        pending (atom {})
        decode  (:decode (:codec app-library/options))
        settle! (fn [id f]
                  (when-let [{:keys [resolve reject]} (get @pending id)]
                    (swap! pending dissoc id)
                    (try
                      (resolve (f))
                      (catch :default e
                        (reject e)))))]
    (.addEventListener worker "message"
                       (fn [^js e]
                         (let [{:keys [id progress fetched failed] :as answer}
                               (reader/read-string (.-data e))]
                           (cond
                             progress (some-> (get-in @pending [id :on-progress])
                                              (apply [progress]))
                             failed   (settle! id #(throw (js/Error. failed)))
                             fetched  (settle! id #(decode fetched))
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

(defn ask!
  "A promise of the web worker's answer to `request`, calling `on-progress`
  with each report of progress. It rejects when the worker fails."
  [request on-progress]
  (let [{:keys [^js worker pending next]} (web-worker!)
        id                                (swap! next inc)]
    (js/Promise.
     (fn [resolve reject]
       (swap! pending assoc id {:resolve resolve :reject reject :on-progress on-progress})
       (.postMessage worker (pr-str (assoc request :id id)))))))

(defn fetch-due!
  "A promise of what a refresh of the feeds that are `due` found in the
  web worker, with `opts`, which must be data: a map of the :found, or of
  :busy? while another tab's worker refreshes. It calls `on-progress` with
  the :done and :total as each fetch ends."
  [due opts on-progress]
  (ask! {:due due :opts opts} on-progress))

(defn fetch!
  "A promise of the answer to a fetch of the feed at `url` with `opts`, in
  the web worker and without the :body. The `opts` must be data."
  [url opts]
  (ask! {:fetch true :url url :opts opts} nil))
