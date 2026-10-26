(ns dk.simongray.elektrolyt.show.feeds
  "The effects of the Show and Episode pages: loading their feeds, the
  files of their episodes and the shows that a show links to, and
  subscribing.

  A feed opens from the cache, or else the web worker fetches it. A cached
  feed past its time is checked again once a visit. When the link doesn't
  give a show's feed URL, the cache or the Podcast Index finds it by guid."
  (:require [dk.simongray.podcast.app.episode :as episode]
            [dk.simongray.podcast.app.feeds :as feeds]
            [dk.simongray.podcast.app.show :as app.show]
            [dk.simongray.podcast.directory :as directory]
            [dk.simongray.podcast.feed.discovery :as discovery]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.player.audio :as audio]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.runtime.cache :as app-cache]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.search.drpodcast :as drpodcast]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.show.linked :as linked]
            [dk.simongray.elektrolyt.show.subscription :as subscription]
            [dk.simongray.elektrolyt.ui.announcement :as announcement]
            [dk.simongray.elektrolyt.ui.document :as document]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.worker.fetching :as fetching]
            [reitit.frontend.easy :as rfe]
            [taoensso.telemere :as tel]))

(defn whole-opts
  "The options of the worker's fetch of a whole feed in `state`: a limit
  on the pages of a paged feed, and whether to add the episodes of
  drpodcast.nu to DR's own feeds."
  [state]
  {:max-pages          20
   :alternative-feeds? (settings/alternative-feeds? state)})

(defn keep-show!
  "Cache the whole feed `fetched` from `url`."
  [url fetched]
  (app-cache/put! app-cache/shows (show/cache-key url) (show/kept fetched)))

(defn index-lookup!
  "A promise of the feed URL of the show `guid` from the Podcast Index,
  through the app's server: a map of the :url, which is then cached, and
  where it came :from, of :not-found? when the index doesn't have it, or
  nil when the lookup fails, e.g. offline."
  [guid]
  (-> (directory/call! :podcastindex :lookup {:id guid}
                       {:send app-cache/send :credentials {}})
      (.then (fn [[show]]
               (if-let [url (some-> show resolve/self-url)]
                 (do (app-cache/put! app-cache/feed-urls guid url)
                     {:url url :from :index})
                 {:not-found? true})))
      (.catch (constantly nil))))

(defn feed-url!
  "A promise of the feed URL of the show `guid`, from the cache or else the
  Podcast Index, which alone is asked when `fresh?`."
  [guid fresh?]
  (if fresh?
    (index-lookup! guid)
    (.then (app-cache/fetch! app-cache/feed-urls guid)
           (fn [kept]
             (if-let [url (:value kept)]
               {:url url :from :cache}
               (index-lookup! guid))))))

(declare load-show!)

(defn look-up-show!
  "Find the feed URL of the show `guid` in the cache, or else the Podcast
  Index, and load the show into `store`, only the start of its feed when
  `preview?`. When `fresh?`, only the index is asked, e.g. once the feed
  is gone from the cached URL. A lookup that fails leaves nothing, so
  that the next visit asks again."
  [store guid preview? fresh?]
  (swap! store show/requested [:look-up guid])
  (.then (feed-url! guid fresh?)
         (fn [found]
           (swap! store #(cond-> (show/answered % [:look-up guid])
                           found (assoc-in [:feed-urls guid] found)))
           (when-let [url (:url found)]
             (load-show! store guid url preview?)))))

(defn show-failed!
  "Note in `store` that the whole feed of the show `guid` at `url` failed
  with `error`, and announce it, or look the show up in the Podcast Index
  again when the feed is gone from the URL that the cache had, and fetch
  it at the URL that the index gives."
  [store guid url error]
  (if (show/gone-from-cache? @store guid error)
    (do (swap! store #(-> (show/answered % [:whole url])
                          (update :feed-urls dissoc guid)
                          ;; the index can give the same URL, where a feed
                          ;; checked on this visit would stay as it was
                          (show/retried url)))
        (look-up-show! store guid false true))
    (swap! store #(-> (show/answered % [:whole url])
                      (assoc-in [:feeds url :error] error)
                      (announcement/announced "The feed couldn't be fetched")))))

(defn answered!
  "Note in `store` that the `request` for a feed is answered, apply `f` to
  the state, and put the page's canonical path in the address bar, since
  the feed can change it, e.g. with the show's title."
  [store request f]
  (document/canonicalize! (swap! store #(f (show/answered % request)))))

(defn show-fetched!
  "Put the whole feed `fetched` from `url` into the :feeds of `store`, and
  cache it."
  [store url fetched]
  (answered! store [:whole url] #(show/whole % url fetched (js/Date.) true))
  (keep-show! url fetched))

(defn fetch-start!
  "Fetch the start of the feed at `url` into the :feeds of `store`, for
  the first episodes, and parse it in the page, since it's small."
  [store url]
  (swap! store show/requested [:head url])
  (-> (discovery/fetch-head! url {:items show/page-size :send app-library/send})
      (.then (fn [{:keys [feed]}]
               (answered! store [:head url]
                          #(cond-> % feed (show/arrived url feed)))))
      (.catch #(swap! store show/answered [:head url]))))

(defn fetch-whole!
  "Fetch the whole feed of the show `guid` at `url` into the :feeds of
  `store` and cache it, or note why it failed. The web worker parses it,
  since a feed of 20 MB takes a third of a second or more."
  [store guid url]
  (swap! store show/requested [:whole url])
  (-> (fetching/fetch! url (whole-opts @store))
      (.then (fn [{:keys [feed error] :as fetched}]
               (if feed
                 (show-fetched! store url fetched)
                 (show-failed! store guid url error))))
      (.catch #(show-failed! store guid url {:message (ex-message %)}))))

(defn fetch-show!
  "Fetch the show `guid` at `url` into the :feeds of `store`: the start of
  its feed, and the whole feed too unless `preview?`. Neither is fetched
  twice, except a whole feed whose fetch failed."
  [store guid url preview?]
  (let [state                       @store
        {:keys [feed whole? error]} (get-in state [:feeds url])
        asked?                      #(show/requested? state [% url])]
    (when-not (or feed error (asked? :head) (asked? :whole))
      (fetch-start! store url))
    (when-not (or preview? whole? (asked? :whole) (asked? :open))
      (fetch-whole! store guid url))))

;; RFC 9110, sections 13.1.2 and 13.1.3: If-None-Match and
;; If-Modified-Since, which a 304 answers when the feed is unchanged
(defn check-show!
  "Fetch the cached feed of the show `guid` at `url` in `store` again with
  its validators, once a visit and only when it's past its time. A 304
  renews the cached copy, and a new feed replaces it in the cache and on
  the page, unless it lost every item."
  [store guid url]
  (let [{:keys [feed whole? validators checked?] :as entry} (get-in @store [:feeds url])]
    (when (and whole? (not checked?))
      (swap! store #(-> (assoc-in % [:feeds url :checked?] true)
                        (show/requested [:check url])))
      (-> (fetching/fetch! url (merge (whole-opts @store)
                                      (select-keys validators [:etag :last-modified])))
          (.then (fn [{:keys [status error] :as fetched}]
                   (cond
                     (show/gone? error)
                     (show-failed! store guid url error)

                     ;; a feed that lost every item, e.g. during a host's outage,
                     ;; stays as it was, as a refresh of the library keeps it
                     (and (:feed fetched) (not (feeds/emptied? feed (:feed fetched))))
                     (do (swap! store show/refetched guid url fetched (js/Date.))
                         (keep-show! url fetched))

                     ;; the old :not-before has passed, so only the 304's
                     ;; counts
                     (= 304 status)
                     (keep-show! url (merge {:url url :feed feed}
                                            (select-keys entry show/location-keys)
                                            (dissoc validators :not-before)
                                            (show/validators fetched))))))
          (.catch #(tel/log! {:level :warn :id :show/check-failed
                              :data  {:url url} :error %}
                             "The show wasn't checked"))
          (.finally #(swap! store show/answered [:check url]))))))

(defn kept-fresh?
  "Whether the cache entry `kept` of the feed at `url` is fresh in `state`
  at `now`: within its time, and not a feed of DR's own from before the
  listener turned drpodcast.nu's episodes on or off."
  [state url {:keys [value at]} now]
  (and (show/fresh? value at now)
       (drpodcast/fetched-as? (:feed value) url
                              (settings/alternative-feeds? state))))

(defn open-show!
  "Open the show `guid` at `url` into `store` from the cache, or else fetch
  it, only the start of its feed when `preview?`. A cached feed past its
  time is checked again, unless every request while the cache was read
  was a preview."
  [store guid url preview?]
  (swap! store show/requested [:open url] {:whole? (not preview?)})
  (.then (app-cache/fetch! app-cache/shows (show/cache-key url))
         (fn [kept]
           (let [whole? (get-in @store [:requests [:open url] :whole?])]
             (if-let [fetched (:value kept)]
               (let [fresh? (kept-fresh? @store url kept (js/Date.))]
                 (answered! store [:open url]
                            #(show/whole % url fetched (:at kept) fresh?))
                 (when whole?
                   (check-show! store guid url)))
               (do (swap! store show/answered [:open url])
                   (fetch-show! store guid url (not whole?))))))))

(defn in-library?
  "Whether the library in `state` has the feed of the show `guid` at `url`
  for a subscription."
  [state guid url]
  (some? (some-> (:library state) (show/library-key guid url))))

(defn look-up?
  "Whether to look up the show `guid` without a `url` in `state`: when its
  feed URL is neither known nor being looked up, the app's server signs
  requests to the Podcast Index, and the library doesn't have the show."
  [state guid url]
  (and (nil? url)
       (nil? (get-in state [:feed-urls guid]))
       (not (show/requested? state [:look-up guid]))
       (some? (search/index-credentials state))
       (not (in-library? state guid nil))))

(defn load-show!
  "Load the show `guid` at `url` into the :feeds of `store`, unless the
  library has it: from the cache, or else the start of its feed and the
  whole feed too unless `preview?`.

  Without a URL, the cache or the Podcast Index finds the feed by its
  guid, when the app's server signs requests to the index."
  [store guid url preview?]
  (let [state  @store
        url    (or url (:url (show/source state guid)))
        known  (get-in state [:feeds url])
        asked? #(show/requested? state [% url])]
    (when (look-up? state guid url)
      (look-up-show! store guid preview? false))
    (when (and url (not (in-library? state guid url)))
      (cond
        (asked? :open)
        (when-not preview?
          (swap! store assoc-in [:requests [:open url] :whole?] true))

        (:whole? known)
        (when-not preview?
          (check-show! store guid url))

        (or known (asked? :head) (asked? :whole))
        (fetch-show! store guid url preview?)

        :else
        (open-show! store guid url preview?)))))

(defn prefetch-soon!
  "Fetch the start of the feed of the show `guid` at `url` into `store` in
  `ms` milliseconds, or 65 without them, unless the prefetch is cancelled
  first."
  [store guid url ms]
  (runtime/later! ::prefetch (or ms 65) #(load-show! store guid url true)))

(defn prefetch-cancel!
  "Cancel a prefetch that hasn't started yet."
  []
  (runtime/cancel! ::prefetch))

(defn whole-feed!
  "A promise of the whole feed at `url`, from the Show page in `store` or
  else fetched in the worker, or of the :error of a fetch that failed."
  [store url]
  (if-let [fetched (show/whole-feed @store url)]
    (js/Promise.resolve fetched)
    (.catch (fetching/fetch! url (whole-opts @store))
            (fn [e] {:error {:message (ex-message e)}}))))

(defn with-search-documents!
  "A promise of the whole feed `fetched` from `url` with the search
  documents that the web worker built of it, or of `fetched` as it is when
  it has no feed or the worker fails, so that the library builds them."
  [fetched url]
  (if (:feed fetched)
    (-> (fetching/search-documents! fetched url)
        (.then #(assoc fetched :search-documents %))
        (.catch (constantly fetched)))
    (js/Promise.resolve fetched)))

(defn subscribe!
  "Subscribe the library in `store` to the show `title` at `url` with its
  whole feed, and save the library. A failed fetch fails the
  subscription. The web worker builds the feed's search documents, which
  would hold the page up for a while."
  [store url title]
  (app-library/persist!)
  (swap! store subscription/subscribing url)
  ;; the bookmark fills in before the feed is encoded for the worker and the
  ;; page indexes the documents, while a fetch of the feed starts at once;
  ;; a feed that the page has resolves within the click's task
  (-> (js/Promise.all #js [(whole-feed! store url) (runtime/painted!)])
      (.then (fn [[fetched]] (with-search-documents! fetched url)))
      (.then (fn [fetched]
               (try
                 (app-library/change! store (subscription/subscribed url title fetched))
                 (catch :default e
                   (app-library/change! store (subscription/failed url title
                                                                   {:message (ex-message e)}))))
               @store))
      ;; the page's address drops the feed URL, since the library has it
      ;; now
      (.then #(do (document/canonicalize! %) %))
      (.then (fn [_] (app-library/save! store)))))

;; The bookmark empties before the library lets the show go, which for a
;; long show takes a while, e.g. to take its episodes out of the search
;; index. The page stays, now for a show outside the library, with its
;; feed URL in the address, which checks the library's copy of the feed.
(defn unsubscribe!
  "Unsubscribe the library in `store` from the show `title` at `url` once
  the page shows that it's unsubscribing, and save the library."
  [store url title]
  (let [guid (get-in @store [:route :path-params :guid])]
    (swap! store subscription/unsubscribing url)
    (.then (runtime/painted!)
           (fn [_]
             (app-library/change! store (subscription/unsubscribed @store url title))
             (app-library/save! store)
             (when (= guid (get-in @store [:route :path-params :guid]))
               (apply rfe/replace-state (routes/show-route guid title url)))))))

(def linked-except
  "The parts of a show's view that fetching its linked shows doesn't
  need."
  #{:notes :text :episodes :seasons :links})

(defn linked-shows!
  "A promise of the shows that the show `guid` links to in its parsed
  `feed` at `url`, fetched through the app's proxy, and from the Podcast
  Index too with the `index` credentials. They're cached unless a feed
  failed, so that the next visit tries again."
  [guid feed url index]
  (.then (app.show/fetch-linked-shows!
          (app.show/view feed {:url url :except linked-except})
          {:send app-library/send :index index})
         (fn [fetched]
           ;; the page's guid can come from the feed's URL, while the
           ;; publisher lists the show by the guid that its feed declares
           (let [shows (linked/kept #{guid (guid/feed-guid feed url)} fetched)]
             (when-not (linked/failed? fetched)
               (app-cache/put! app-cache/linked guid shows))
             shows))))

(defn fetch-linked!
  "Fetch the shows that the show `guid` links to in its parsed `feed` at
  `url` into the :linked of `store`, once: from the cache, or else from
  the network, with the Podcast Index when the app's server signs
  requests to it."
  [store guid feed url]
  (when-not (get-in @store [:linked guid])
    (swap! store assoc-in [:linked guid] {:status :fetching})
    (-> (app-cache/fetch! app-cache/linked guid)
        (.then #(or (:value %)
                    (linked-shows! guid feed url (search/index-credentials @store))))
        (.then #(swap! store assoc-in [:linked guid] (assoc % :status :done)))
        (.catch (fn [e]
                  (tel/log! {:level :warn :id :show/linked-failed
                             :data  {:guid guid} :error e}
                            "The shows that a show links to weren't fetched")
                  (swap! store assoc-in [:linked guid :status] :failed))))))

(defn read-files!
  "Read the chapters and duration of the episode `id` from the files that
  its `view` names into the :episodes of `store`. The transcript waits
  for the listener."
  [store id view]
  (audio/read-files! store id #(episode/read-files! view {:send   app-library/send
                                                         :except #{:transcript}})))

(defn read-transcript!
  "Read the transcript that the `view` of the episode `id` names into the
  :episodes of `store`, once."
  [store id view]
  (when-not (get-in @store [:episodes id :transcript-status])
    (swap! store assoc-in [:episodes id :transcript-status] :reading)
    (let [read! #(swap! store update-in [:episodes id] assoc
                        :transcript-status :read :transcript %)]
      (-> (episode/read-files! view {:send app-library/send :except #{:chapters :media}})
          (.then (comp read! :transcript))
          (.catch #(read! {:error {:message (ex-message %)}}))))))
