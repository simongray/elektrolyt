(ns ^:runtime dk.simongray.podcast-app.fetching
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

  The worker, its messages and the Web Lock reach outside the store, as
  dk.simongray.podcast-app.runtime does in the page."
  (:require [cljs.reader :as reader]
            [dk.simongray.podcast :as podcast]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.library :as app-library]))

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

(defn found!
  "A promise of what the request `id` found of what is `due`, as
  library/fetch-due! gives it with the `opts`, and the config and the
  options of the library, posting the progress of each fetch to the page."
  [id due opts]
  (let [progress (fn [{:keys [done total]}]
                   (post! {:id id :progress {:done done :total total}}))]
    (library/fetch-due! due app-library/store
                        (merge app-library/config app-library/options opts
                               {:on-feed-fn progress}))))

(defn fetched!
  "A promise of the feed at `url` fetched with `opts` through the proxy, as
  podcast/fetch! gives it but without the :body, in the codec of the
  library."
  [url opts]
  (.then (podcast/fetch! url (merge (select-keys app-library/options [:send]) opts))
         #((:encode (:codec app-library/options)) (dissoc % :body))))

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
