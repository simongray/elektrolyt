(ns dk.simongray.elektrolyt.feeds
  "The feeds of the shows of the Show and Episode pages, and the files of
  their episodes: opened from the cache, fetched in the worker, checked
  again once a visit, or found by guid in the Podcast Index, the
  subscriptions to them, and the shows that they link to.

  The namespace dk.simongray.elektrolyt.show describes what the state
  keeps of each show, and dk.simongray.elektrolyt.linked what it keeps of
  the shows that a show links to."
  (:require [dk.simongray.podcast.app.episode :as episode]
            [dk.simongray.podcast.app.show :as app.show]
            [dk.simongray.podcast.directory :as directory]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.elektrolyt.audio :as audio]
            [dk.simongray.elektrolyt.cache :as app-cache]
            [dk.simongray.elektrolyt.drpodcast :as drpodcast]
            [dk.simongray.elektrolyt.fetching :as fetching]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.linked :as linked]
            [dk.simongray.elektrolyt.page :as page]
            [dk.simongray.elektrolyt.routes :as routes]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.settings :as settings]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.subscription :as subscription]
            [reitit.frontend.easy :as rfe]))

(defn whole-opts
  "The options of the worker's fetch of a whole feed in the `state`: at
  most 20 pages of a paged feed, joined, and whether DR's own feeds take
  the episodes of drpodcast.nu."
  [state]
  {:max-pages          20
   :alternative-feeds? (settings/alternative-feeds? state)})

(defn keep-show!
  "Keep the whole feed at `url` that a fetch gave, `fetched`, in the cache."
  [url fetched]
  (app-cache/put! app-cache/shows (show/cache-key url) (show/kept fetched)))

(declare load-show!)

(defn look-up-show!
  "Find the feed of the show `guid` in the cache, or else in the Podcast
  Index through the app's server, and load the show into the `store` as
  load-show! does with `preview?`. A show that the index doesn't know is
  :not-found? in :feed-urls. When `fresh?`, only the index is asked, e.g.
  once the feed at the URL from the cache is gone."
  [store guid preview? fresh?]
  (let [found!     (fn [found]
                     (swap! store #(-> (show/answered % [:look-up guid])
                                       (assoc-in [:feed-urls guid] found)))
                     (when-let [url (:url found)]
                       (load-show! store guid url preview?)))
        ask!       #(-> (directory/call! :podcastindex :lookup {:id guid}
                                         {:send app-cache/send :credentials {}})
                        (.then (fn [[show]]
                                 (if-let [url (some-> show resolve/self-url)]
                                   (do (app-cache/put! app-cache/feed-urls guid url)
                                       (found! {:url url :from :index}))
                                   (found! {:not-found? true}))))
                        (.catch (fn [_] (found! {:not-found? true}))))]
    (swap! store show/requested [:look-up guid])
    (if fresh?
      (ask!)
      (.then (app-cache/fetch! app-cache/feed-urls guid)
             (fn [kept]
               (if-let [url (:value kept)]
                 (found! {:url url :from :cache})
                 (ask!)))))))

(defn show-failed!
  "Note in the `store` that the whole feed of the show `guid` at `url`
  failed with `error`, or look the guid up in the Podcast Index again when
  the feed is gone from the URL that the cache had for it."
  [store guid url error]
  (if (show/gone-from-cache? @store guid error)
    (do (swap! store #(-> (show/answered % [:whole url])
                          (update :feed-urls dissoc guid)))
        (look-up-show! store guid false true))
    (swap! store #(-> (show/answered % [:whole url])
                      (assoc-in [:feeds url :error] error)))))

(defn show-fetched!
  "Put the whole feed at `url` that the worker `fetched` into the :feeds
  of the `store`, and keep it in the cache."
  [store url fetched]
  (page/canonicalize! (swap! store #(-> (show/answered % [:whole url])
                                        (show/whole url fetched (js/Date.) true))))
  (keep-show! url fetched))

(defn fetch-show!
  "Fetch the show `guid` at `url` into the :feeds of the `store`: the start
  of its feed, and the whole feed too unless `preview?`. Each is fetched
  once, however often it's asked for, but a whole feed whose fetch failed
  is fetched again."
  [store guid url preview?]
  (let [state                       @store
        {:keys [feed whole? error]} (get-in state [:feeds url])
        asked?                      #(show/requested? state [% url])]
    (when-not (or feed error (asked? :head) (asked? :whole))
      (swap! store show/requested [:head url])
      (-> (discovery/fetch-head! url {:items show/page-size :send app-library/send})
          (.then (fn [{:keys [feed]}]
                   (page/canonicalize! (swap! store #(cond-> (show/answered % [:head url])
                                                       feed (show/arrived url feed))))))
          (.catch #(swap! store show/answered [:head url]))))
    (when-not (or preview? whole? (asked? :whole) (asked? :open))
      (swap! store show/requested [:whole url])
      ;; in the worker, since a feed of 20 MB takes a third of a second or
      ;; more to parse; the start of the feed already shows the first
      ;; episodes, parsed here, since it's small
      (-> (fetching/fetch! url (whole-opts state))
          (.then (fn [{:keys [feed error] :as fetched}]
                   (if feed
                     (show-fetched! store url fetched)
                     (show-failed! store guid url error))))
          (.catch #(show-failed! store guid url {:message (ex-message %)}))))))

;; RFC 9110, sections 13.1.1 and 13.1.3: the validators of the kept feed
;; ask whether it changed, and a 304 says it didn't
(defn check-show!
  "Fetch the feed of the show `guid` at `url` in the `store` again, with
  the validators of the one from the cache, once a visit and only when
  it's past its time. A 304 keeps it for another while, and a new feed
  goes into the cache and onto the page, or waits when it brings new
  episodes."
  [store guid url]
  (let [{:keys [feed whole? validators checked?]} (get-in @store [:feeds url])]
    (when (and whole? (not checked?))
      (swap! store #(-> (assoc-in % [:feeds url :checked?] true)
                        (show/requested [:check url])))
      (-> (fetching/fetch! url (merge (whole-opts @store) (select-keys validators [:etag :last-modified])))
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
          (.finally #(swap! store show/answered [:check url]))))))

(defn open-show!
  "Open the show `guid` at `url` into the `store` from the feed that the
  cache keeps, or else fetch it as fetch-show! does with `preview?`. A kept
  feed past its time is checked once the page is open, i.e. unless every
  ask while the cache was read was a `preview?`."
  [store guid url preview?]
  (swap! store show/requested [:open url] {:whole? (not preview?)})
  (.then (app-cache/fetch! app-cache/shows (show/cache-key url))
         (fn [kept]
           (let [whole? (get-in @store [:requests [:open url] :whole?])]
             (if-let [fetched (:value kept)]
               ;; a feed of DR's own that the cache kept from before the
               ;; listener turned drpodcast.nu's episodes on or off is old
               (let [fresh? (and (show/fresh? fetched (:at kept) (js/Date.))
                                 (drpodcast/fetched-as? (:feed fetched) url
                                                        (settings/alternative-feeds? @store)))]
                 (page/canonicalize! (swap! store #(-> (show/answered % [:open url])
                                                       (show/whole url fetched (:at kept) fresh?))))
                 (when whole?
                   (check-show! store guid url)))
               (do (swap! store show/answered [:open url])
                   (fetch-show! store guid url (not whole?))))))))

(defn load-show!
  "Load the show `guid` at `url` into the :feeds of the `store`, unless the
  library keeps it: from the cache, or else the start of its feed and the
  whole feed too unless `preview?`, as open-show! has it.

  Without a URL, the cache or the Podcast Index finds the feed by the
  guid, when the app's server signs the requests to it."
  [store guid url preview?]
  (let [state       @store
        url         (or url (:url (show/source state guid)))
        known       (get-in state [:feeds url])
        asked?      #(show/requested? state [% url])
        in-library? #(some-> (:library state) (show/library-key guid %))]
    (when (and (nil? url)
               (nil? (get-in state [:feed-urls guid]))
               (not (show/requested? state [:look-up guid]))
               (search/index-credentials state)
               (not (in-library? nil)))
      (look-up-show! store guid preview? false))
    (when (and url (not (in-library? url)))
      (cond
        (asked? :open)                             (when-not preview?
                                                     (swap! store assoc-in [:requests [:open url] :whole?] true))
        (:whole? known)                            (when-not preview?
                                                     (check-show! store guid url))
        (or known (asked? :head) (asked? :whole)) (fetch-show! store guid url preview?)
        :else                                      (open-show! store guid url preview?)))))

(defn prefetch-soon!
  "Fetch the start of the feed of the show `guid` at `url` into the `store`
  in 65 ms, unless the pointer leaves the link to it first."
  [store guid url]
  (runtime/later! ::prefetch 65 #(load-show! store guid url true)))

(defn prefetch-cancel!
  "Cancel the prefetch that prefetch-soon! started, when the pointer
  leaves the link."
  []
  (runtime/cancel! ::prefetch))

(defn subscribe!
  "Subscribe the library of the `store` to the show `title` at `url`, and
  save the library. A feed that the Show page has whole isn't fetched
  again, and one that it hasn't is fetched in the worker. A fetch that
  fails, e.g. with the worker, fails the subscription."
  [store url title]
  (app-library/persist!)
  (swap! store subscription/subscribing url)
  (-> (if-let [fetched (show/whole-feed @store url)]
        (js/Promise.resolve fetched)
        (.catch (fetching/fetch! url (whole-opts @store))
                (fn [e] {:error {:message (ex-message e)}})))
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
      (.then (fn [_] (app-library/save! store)))
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
    (app-library/save! store)
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
