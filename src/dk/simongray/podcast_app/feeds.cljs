(ns dk.simongray.podcast-app.feeds
  "The feeds of the shows of the Show and Episode pages, and the files of
  their episodes: opened from the cache, fetched in the worker, checked
  again once a visit, or found by guid in the Podcast Index, the
  subscriptions to them, and the shows that they link to.

  The namespace dk.simongray.podcast-app.show describes what the state
  keeps of each show, and dk.simongray.podcast-app.linked what it keeps of
  the shows that a show links to."
  (:require [dk.simongray.podcast.app.episode :as episode]
            [dk.simongray.podcast.app.show :as app.show]
            [dk.simongray.podcast.directory :as directory]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast-app.audio :as audio]
            [dk.simongray.podcast-app.cache :as app-cache]
            [dk.simongray.podcast-app.fetching :as fetching]
            [dk.simongray.podcast-app.library :as app-library]
            [dk.simongray.podcast-app.linked :as linked]
            [dk.simongray.podcast-app.page :as page]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.search :as search]
            [dk.simongray.podcast-app.show :as show]
            [dk.simongray.podcast-app.subscription :as subscription]
            [reitit.frontend.easy :as rfe]))

(def fetch-opts
  "The options of the worker's fetch of a whole feed: at most 20 pages of a
  paged feed, joined."
  {:max-pages 20})

(defn keep-show!
  "Keep the whole feed at `url` that a fetch gave, `fetched`, in the cache."
  [url fetched]
  (app-cache/put! app-cache/shows (show/cache-key url) (show/kept fetched)))

(declare load-show!)

(defn look-up-show!
  "Find the feed of the show `guid` in the cache, or else in the Podcast
  Index through the app's server, and load the show into the `store` as
  load-show! does with `preview?`. A show that the index doesn't know is
  :not-found. When `fresh?`, only the index is asked, e.g. once the feed
  at the URL from the cache is gone."
  [store guid preview? fresh?]
  (let [not-found! (fn [_]
                     (swap! store update-in [:shows guid] assoc :status :not-found))
        found!     (fn [url cached?]
                     (swap! store update-in [:shows guid] assoc
                            :url url :status nil :cached-url? cached?)
                     (load-show! store guid url preview?))
        ask!       #(-> (directory/call! :podcastindex :lookup {:id guid}
                                         {:send app-cache/send :credentials {}})
                        (.then (fn [[show]]
                                 (if-let [url (some-> show resolve/self-url)]
                                   (do (app-cache/put! app-cache/feed-urls guid url)
                                       (found! url false))
                                   (not-found! nil))))
                        (.catch not-found!))]
    (swap! store update-in [:shows guid] assoc :status :looking-up)
    (if fresh?
      (ask!)
      (.then (app-cache/fetch! app-cache/feed-urls guid)
             (fn [kept]
               (if-let [url (:value kept)]
                 (found! url true)
                 (ask!)))))))

(defn show-failed!
  "Note in the `store` that the whole feed of the show `guid` failed with
  `error`, or look the guid up in the Podcast Index again when the feed
  is gone from the URL that the cache had for it."
  [store guid error]
  (if (and (get-in @store [:shows guid :cached-url?])
           (#{404 410} (:status error)))
    (do (swap! store update-in [:shows guid] dissoc :url :cached-url?)
        (look-up-show! store guid false true))
    (swap! store update-in [:shows guid] assoc :status :failed :error error)))

(defn show-fetched!
  "Put the whole feed of the show `guid` at `url` that the worker
  `fetched` into the :shows of the `store`, and keep it in the cache."
  [store guid url fetched]
  (page/canonicalize! (swap! store show/whole guid url fetched (js/Date.) true))
  (keep-show! url fetched))

(defn fetch-show!
  "Fetch the show `guid` at `url` into the :shows of the `store`: the start
  of its feed, and the whole feed too unless `preview?`. Each is fetched
  once, however often it's asked for, but a whole feed whose fetch failed
  is fetched again."
  [store guid url preview?]
  (let [status   (get-in @store [:shows guid :status])
        arrived! #(page/canonicalize! (swap! store show/arrived guid url %1 %2))]
    (when (nil? status)
      (swap! store update-in [:shows guid] assoc :url url :status :previewing)
      (.then (discovery/fetch-head! url {:items show/page-size :send app-library/send})
             #(some-> (:feed %) (arrived! false))))
    (when (and (not preview?) (contains? #{nil :previewing :previewed :failed} status))
      (swap! store update-in [:shows guid] assoc :url url :status :loading)
      ;; in the worker, since a feed of 20 MB takes a third of a second or
      ;; more to parse; the start of the feed already shows the first
      ;; episodes, parsed here, since it's small
      (-> (fetching/fetch! url fetch-opts)
          (.then (fn [{:keys [feed error] :as fetched}]
                   (if feed
                     (show-fetched! store guid url fetched)
                     (show-failed! store guid error))))
          (.catch #(show-failed! store guid {:message (ex-message %)}))))))

;; RFC 9110, sections 13.1.1 and 13.1.3: the validators of the kept feed
;; ask whether it changed, and a 304 says it didn't
(defn check-show!
  "Fetch the feed of the show `guid` at `url` in the `store` again, with
  the validators of the one from the cache, once a visit and only when
  it's past its time. A 304 keeps it for another while, and a new feed
  goes into the cache and onto the page, or waits when it brings new
  episodes."
  [store guid url]
  (let [{:keys [feed validators checked?]} (get-in @store [:shows guid])]
    (when (and feed (not checked?))
      (swap! store update-in [:shows guid] assoc :checked? true :checking? true)
      (-> (fetching/fetch! url (merge fetch-opts (select-keys validators [:etag :last-modified])))
          (.then (fn [{:keys [status] :as fetched}]
                   (cond
                     (:feed fetched)
                     (do (swap! store show/refetched guid url fetched (js/Date.))
                         (keep-show! url fetched))

                     ;; the time of the old fetch has passed, so only the
                     ;; 304's own counts
                     (= 304 status)
                     (keep-show! url (merge {:url url :feed feed}
                                            (dissoc validators :not-before)
                                            (show/validators fetched))))))
          (.catch #(js/console.warn "The show wasn't checked" %))
          (.finally #(swap! store update-in [:shows guid] dissoc :checking?))))))

(defn open-show!
  "Open the show `guid` at `url` into the `store` from the feed that the
  cache keeps, or else fetch it as fetch-show! does with `preview?`. A kept
  feed past its time is checked once the page is open, i.e. unless every
  ask while the cache was read was a `preview?`."
  [store guid url preview?]
  (swap! store update-in [:shows guid] assoc
         :url url :status :opening :whole? (not preview?))
  (.then (app-cache/fetch! app-cache/shows (show/cache-key url))
         (fn [kept]
           (let [whole? (get-in @store [:shows guid :whole?])]
             (swap! store update-in [:shows guid] dissoc :whole?)
             (if-let [fetched (:value kept)]
               (let [fresh? (show/fresh? fetched (:at kept) (js/Date.))]
                 (page/canonicalize! (swap! store show/whole guid url fetched (:at kept) fresh?))
                 (when whole?
                   (check-show! store guid url)))
               (do (swap! store update-in [:shows guid] assoc :status nil)
                   (fetch-show! store guid url (not whole?))))))))

(defn load-show!
  "Load the show `guid` at `url` into the :shows of the `store`, unless the
  library keeps it: from the cache, or else the start of its feed and the
  whole feed too unless `preview?`, as open-show! has it.

  Without a URL, the cache or the Podcast Index finds the feed by the
  guid, when the app's server signs the requests to it."
  [store guid url preview?]
  (let [state       @store
        url         (or url (get-in state [:shows guid :url]))
        status      (get-in state [:shows guid :status])
        in-library? #(some-> (:library state) (show/library-key guid %))]
    (when (and (nil? url) (nil? status) (search/index-credentials state) (not (in-library? nil)))
      (look-up-show! store guid preview? false))
    (when (and url (not (in-library? url)))
      (case status
        nil      (open-show! store guid url preview?)
        :opening (when-not preview?
                   (swap! store assoc-in [:shows guid :whole?] true))
        :done    (when-not preview?
                   (check-show! store guid url))
        (fetch-show! store guid url preview?)))))

(defonce ^{:doc "The timeout of the prefetch that a pointer resting on a
  link to a show starts, which leaving the link cancels."}
  prefetch-timeout
  (atom nil))

(defn prefetch-soon!
  "Fetch the start of the feed of the show `guid` at `url` into the `store`
  in 65 ms, unless the pointer leaves the link to it first."
  [store guid url]
  (some-> @prefetch-timeout js/clearTimeout)
  (reset! prefetch-timeout (js/setTimeout #(load-show! store guid url true) 65)))

(defn prefetch-cancel!
  "Cancel the prefetch that prefetch-soon! started, when the pointer
  leaves the link."
  []
  (some-> @prefetch-timeout js/clearTimeout)
  (reset! prefetch-timeout nil))

(defn subscribe!
  "Subscribe the library of the `store` to the show `title` at `url`, and
  save the library. A feed that the Show page has whole isn't fetched
  again, and one that it hasn't is fetched in the worker."
  [store url title]
  (app-library/persist!)
  (swap! store subscription/subscribing url)
  (-> (if-let [fetched (show/whole-feed @store url)]
        (js/Promise.resolve fetched)
        (fetching/fetch! url fetch-opts))
      (.then (fn [fetched]
               (try
                 (app-library/change! store (subscription/subscribed url title fetched))
                 (catch :default e
                   (app-library/change! store (subscription/failed url title
                                                                   {:message (ex-message e)}))))
               @store))
      ;; a show subscribed to from its page loses the URL of its feed from
      ;; the address bar, since the library knows it now
      (.then #(do (page/canonicalize! %) %))
      (.then (fn [_] (app-library/save!)))
      (.then #(when (:error %)
                (js/console.error "The library wasn't saved" (clj->js %))))))

;; The page of the show stays, now as one outside the library, by the URL
;; of its feed, which it gets in its address too
(defn unsubscribe!
  "Unsubscribe the library of the `store` from the show `title` at `url`,
  and save the library."
  [store url title]
  (let [guid (get-in @store [:route :path-params :guid])]
    (app-library/change! store (subscription/unsubscribed @store url title))
    (app-library/save!)
    (apply rfe/replace-state (routes/show-route guid title url))))

(def linked-except
  "The parts of the view of a show that the fetch of the shows it links to
  leaves out."
  #{:notes :text :episodes :seasons :links})

(defn fetch-linked!
  "Fetch the shows that the show `guid`, whose parsed `feed` is at `url`,
  links to into the :linked of the `store`, once: from the cache, or else
  through the app's server, and from the Podcast Index where the server
  signs the requests to it, and keep them in the cache unless a feed
  failed, so that the next visit asks again."
  [store guid feed url]
  (when-not (get-in @store [:linked guid])
    (swap! store assoc-in [:linked guid] {:status :fetching})
    (-> (app-cache/fetch! app-cache/linked guid)
        (.then (fn [kept]
                 (or (:value kept)
                     (.then (app.show/fetch-linked-shows!
                             (app.show/view feed {:url url :except linked-except})
                             {:send  app-library/send
                              :index (search/index-credentials @store)})
                            (fn [fetched]
                              (let [shows (linked/kept guid fetched)]
                                (when-not (linked/failed? fetched)
                                  (app-cache/put! app-cache/linked guid shows))
                                shows))))))
        (.then #(swap! store assoc-in [:linked guid] (assoc % :status :done)))
        (.catch (fn [e]
                  (js/console.warn "The shows that a show links to weren't fetched" e)
                  (swap! store assoc-in [:linked guid :status] :failed))))))

(defn read-files!
  "Read the files of the episode `id`, as its `view` names them, into the
  :episodes of the `store`, as audio/read-files! does: its chapters file
  and its media file, for the chapters and the duration. The transcript
  waits for the listener."
  [store id view]
  (audio/read-files! store id #(episode/read-files! view {:send   app-library/send
                                                         :except #{:transcript}})))

(defn read-transcript!
  "Read the transcript of the episode `id`, as its `view` names it, into
  the :episodes of the `store`, once."
  [store id view]
  (when-not (get-in @store [:episodes id :transcript-status])
    (swap! store assoc-in [:episodes id :transcript-status] :reading)
    (-> (episode/read-files! view {:send app-library/send :except #{:chapters :media}})
        (.then #(swap! store update-in [:episodes id] assoc
                       :transcript-status :read :transcript (:transcript %)))
        (.catch #(swap! store update-in [:episodes id] assoc
                        :transcript-status :read
                        :transcript {:error {:message (ex-message %)}})))))
