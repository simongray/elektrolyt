(ns ^:runtime dk.simongray.elektrolyt.worker.fetching
  "The feeds that the app's web worker fetches for the page, so that
  parsing a feed never holds the page up: those that a refresh of the
  library is due to fetch, and the feed of a Show page.

  The page asks in EDN, and the worker answers with what it found, as
  data. For a refresh, that's what library/fetch-due! gives: the entries
  of the feeds that it fetched and the documents of those that changed,
  which it puts in the library's store. It writes nothing else, so the
  page applies what the worker found to the library. One refresh runs at
  a time across the tabs, by a Web Lock, and the worker of another tab
  answers that it's busy. A fetched feed comes back in the codec of the
  library, since EDN reads slower than the feed parses.

  With :alternative-feeds? among the options of a request, a feed of DR's
  own comes supplemented by its sibling on drpodcast.nu, as
  dk.simongray.elektrolyt.search.drpodcast has it. The supplemented feed has
  only DR's validators, i.e. none, so the library goes on checking it by
  its start, which is supplemented too.

  The worker, its messages and the Web Lock reach outside the store, as
  dk.simongray.elektrolyt.runtime does in the page."
  (:require [cljs.reader :as reader]
            [dk.simongray.podcast :as podcast]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed :as feed]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.search.drpodcast :as drpodcast]))

(def refresh-lock
  "The Web Lock that a refresh holds, by the name that podcast-clj's
  app.worker gave it, so that the tabs of an earlier version of the app
  take turns with this one."
  "dk.simongray.podcast.app.worker/refresh")

;; Web Locks API, W3C Working Draft of 24 September 2025, section 3.2.1:
;; with ifAvailable, the callback gets null when another holds the lock.
(defn when-free
  "A promise of what `f` gives while this worker holds the Web Lock
  `name`, or of what `busy` gives when another holds it."
  [name f busy]
  (if-let [^js locks (some-> js/globalThis .-navigator .-locks)]
    (.request locks name #js {:ifAvailable true}
              (fn [lock] (if lock (f) (busy))))
    (js/Promise.resolve (f))))

(defn post!
  "Post the `message` to the page, as EDN."
  [message]
  (js/postMessage (pr-str message)))

(defonce ^{:doc "A promise of drpodcast.nu's slugs by production number, as
  slugs! fetches them once for the life of the worker, or nil before."}
  known-slugs
  (atom nil))

(defn slugs!
  "A promise of drpodcast.nu's slugs by production number, as
  drpodcast/slugs has them, fetched at the first ask, and at the next once
  a fetch failed."
  []
  (or @known-slugs
      (reset! known-slugs
              (-> (app-library/send {:url drpodcast/slugs-url :as :json})
                  (.then #(drpodcast/slugs (:body %)))
                  (.catch (fn [_]
                            (reset! known-slugs nil)
                            {}))))))

(defn supplement
  "The `answer` of a fetch of DR's own feed with its :feed supplemented by
  the parsed feed `theirs`, without DR's promos of DR Lyd, when that is of
  the same show."
  [{ours :feed :as answer} theirs]
  (cond-> answer
    (drpodcast/same-show? ours theirs)
    (update :feed #(feed/supplemented (drpodcast/without-promos %) theirs))))

;; drpodcast.nu publishes nearly every series under DR's own slug, so the
;; guess goes out beside DR's feed, and slugs.json only names the others
(defn supplementing
  "A fetch like `fetch`, a function of a URL and options that gives a
  promise of an answer with the :feed, which supplements a feed of DR's own
  with its sibling on drpodcast.nu. The sibling's fetch takes the options
  of a request that don't concern DR's feed, and `sibling-opts`."
  [fetch sibling-opts]
  (fn [url opts]
    (if-let [slug (drpodcast/official-slug url)]
      (let [theirs    (fn [slug]
                        (-> (fetch (drpodcast/feed-url slug)
                                   (merge (select-keys opts [:send :timeout-seconds :max-bytes
                                                             :allow-pred :signal :lenient-parser])
                                          sibling-opts))
                            (.then #(:feed %))
                            (.catch (constantly nil))))
            guessed   (theirs slug)
            elsewhere (fn [ours]
                        (.then (slugs!)
                               (fn [by-number]
                                 (let [other (get by-number (drpodcast/production-number ours))]
                                   (when (and other (not= slug other))
                                     (theirs other))))))]
        (.then (fetch url opts)
               (fn [{ours :feed :as answer}]
                 (if-not ours
                   answer
                   (let [answer (update answer :feed drpodcast/marked)]
                     (.then guessed
                            (fn [found]
                              (if (drpodcast/same-show? ours found)
                                (supplement answer found)
                                (.then (elsewhere ours) #(supplement answer %))))))))))
      (fetch url opts))))

;; The start of DR's feed has its newest episodes, which DR may date days
;; after drpodcast.nu does, so the sibling's start goes further back for
;; them to pair
(def sibling-start
  "The options of the fetch of the start of a sibling on drpodcast.nu,
  beside the start of DR's own feed: its 20 newest items, which fit the 64
  KB that a fetch of a start asks for at least."
  {:items 20})

(defn fetches
  "The options of library/fetch-due! that fetch with the `opts` of a
  request, which supplement DR's own feeds where they have
  :alternative-feeds?."
  [opts]
  (when (:alternative-feeds? opts)
    {:fetch      (supplementing podcast/fetch! {})
     :fetch-head (supplementing discovery/fetch-head! sibling-start)}))

(defn found!
  "A promise of what the request `id` found of what is `due`, as
  library/fetch-due! gives it with the `opts`, and the config and the
  options of the library, posting the progress of each fetch to the page."
  [id due opts]
  (let [progress (fn [{:keys [done total]}]
                   (post! {:id id :progress {:done done :total total}}))]
    (library/fetch-due! due app-library/store
                        (merge app-library/config app-library/options
                               (dissoc opts :alternative-feeds?)
                               (fetches opts)
                               {:on-feed-fn progress}))))

(defn fetched!
  "A promise of the feed at `url` fetched with `opts` through the proxy, as
  podcast/fetch! gives it but without the :body, in the codec of the
  library."
  [url opts]
  (let [fetch (:fetch (fetches opts) podcast/fetch!)]
    (.then (fetch url (merge (select-keys app-library/options [:send])
                             (dissoc opts :alternative-feeds?)))
           #((:encode (:codec app-library/options)) (dissoc % :body)))))

(defn answer!
  "Answer the `request` of the page: what a refresh found, or :busy? while
  another refreshes, or a fetched feed, or why it :failed."
  [{:keys [id due fetch url opts] :as request}]
  (-> (cond
        due   (when-free refresh-lock
                         #(.then (found! id due opts) (fn [found] {:id id :found found}))
                         #(js/Promise.resolve {:id id :busy? true}))
        fetch (.then (fetched! url opts) (fn [fetched] {:id id :fetched fetched})))
      (.then post!)
      (.catch (fn [e] (post! {:id id :failed (str e)})))))

(defn serve!
  "Answer the requests of the page in this web worker."
  []
  (set! (.-onmessage js/self)
        (fn [^js e]
          (answer! (reader/read-string (.-data e))))))

(defn client
  "The web worker of the script at `url`, which serve! answers, for the
  page: a map of the :worker, the requests that it hasn't answered yet by
  id, and the :next id. Once the worker fails, the client rejects those
  requests, stops it and calls `on-failed`."
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
    ;; script fails to load, and then never runs, or when it throws past
    ;; answer!, which may have dropped any request
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
  ask and once its worker failed."}
  web-worker
  (atom nil))

(defn web-worker!
  "The client of the app's web worker, made now unless there's one, e.g. at
  the first ask, or at the ask after its worker failed."
  []
  (or @web-worker
      (reset! web-worker (client "/js/worker.js" #(reset! web-worker nil)))))

(defn ask!
  "A promise of the answer of the web worker to `request`, with
  `on-progress` called with each report of its progress, which fails when
  the worker does."
  [request on-progress]
  (let [{:keys [^js worker pending next]} (web-worker!)
        id                                (swap! next inc)]
    (js/Promise.
     (fn [resolve reject]
       (swap! pending assoc id {:resolve resolve :reject reject :on-progress on-progress})
       (.postMessage worker (pr-str (assoc request :id id)))))))

(defn fetch-due!
  "A promise of what the web worker found of what is `due`, as library/due
  gives it, fetched with the `opts` of library/fetch-due! that are data,
  e.g. :head-check?, calling `on-progress` with the :done and :total of
  each fetch that ends: a map of the :found, or :busy? while another tab's
  worker refreshes."
  [due opts on-progress]
  (ask! {:due due :opts opts} on-progress))

(defn fetch!
  "A promise of the feed at `url`, fetched in the web worker with the
  `opts` of podcast/fetch! that are data, as podcast/fetch! gives it but
  without the :body."
  [url opts]
  (ask! {:fetch true :url url :opts opts} nil))
