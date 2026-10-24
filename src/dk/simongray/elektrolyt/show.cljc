(ns dk.simongray.elektrolyt.show
  "What the Show page shows of a show, from the best source at hand: the
  feed in the library, the whole feed, the start of the feed, or a search
  result.

  The state holds the feeds of shows outside the library:

  - :feeds has each feed by URL, as a map of the :feed and whether it's
    :whole?, the :validators and :fetched-at of a whole fetch, with its
    :permanent-url, :new-feed-url and :websub for a subscription, whether
    this visit has :checked? it, the :error of a whole fetch that failed,
    and a :newer feed that waits for the listener.
  - :feed-urls has each show's feed URL by guid, as a map of the :url and
    where it came :from, :cache or :index, or else :not-found?.
  - :requests has the requests in flight: [:open url] while the cache is
    read, [:head url] or [:whole url] while the start or all of a feed is
    fetched, [:check url] while a cached feed is fetched again, and
    [:look-up guid] while the Podcast Index is asked."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.app.show :as show]
            [dk.simongray.podcast.feed :as feed]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.feed.schedule :as schedule]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.ui.links :as links]
            [dk.simongray.elektrolyt.ui.memo :as memo]))

(def page-size
  "How many episodes the page lists at first, and how many more each time
  the listener asks for more."
  20)

(def view-opts
  "The options of the show's view for the page, which leave out the parts
  that the page doesn't show, start the notes' headings at h2, and mark
  where each link leaves the app to, e.g. a new tab."
  {:except        #{:seasons :links :text}
   :heading-level 2
   :link-fn       links/leaving})

(defn episode-row
  "The episode `item` as a row in the list of episodes, with its own
  artwork if it has any."
  [item]
  {:guid          (guid/item-guid item)
   :type          (:type (resolve/numbering item))
   :title         (resolve/title item)
   :published     (resolve/published item)
   :duration      (resolve/duration item)
   :artwork       (resolve/artwork item)
   :artwork-sizes (resolve/artwork-sizes item)
   :supplement    (feed/supplement item)})

(def facts
  "The properties of a show's view that the page shows besides its title,
  artwork and notes, in the intro, the Details and the People."
  [:link :categories :language :copyright :update-frequency :complete?
   :serial? :medium :explicit? :ai-content? :locations :license :people
   :funding])

;; The latest episode anchors the cadence, so that the page needs no clock
;; and a show that stopped keeps the cadence it had
(defn cadence
  "How often a show with the `episodes` publishes, in milliseconds, as
  estimated up to `latest`, the date of the newest, or nil."
  [episodes latest]
  (when latest
    (schedule/cadence {:rss/items episodes} latest)))

(defn view->page
  "The page of the show `view`, from a feed or from the library."
  [view]
  (let [episodes (:episodes view)
        rows     (mapv episode-row episodes)
        latest   (some->> (seq (keep :published rows)) (apply max-key inst-ms))]
    (merge (select-keys view facts)
           {:title         (:title view)
            :author        (resolve/author view)
            :artwork       (:artwork view)
            :artwork-sizes (resolve/artwork-sizes view)
            :notes         (:notes view)
            :episodes      rows}
           (when latest
             {:latest  latest
              :cadence (cadence episodes latest)}))))

(defn feed->page
  "The page of the show from the parsed `feed` at `url`, whole or only its
  start."
  [url feed]
  (view->page (show/view feed (assoc view-opts :url url))))

(defn result->page
  "The page of the show in a search `result`, before its feed arrives:
  what the directory has, and no episodes yet."
  [{:keys [title author artwork artwork-sizes] :as result}]
  {:title         title
   :author        author
   :artwork       artwork
   :artwork-sizes artwork-sizes
   :episodes      []})

(defn library-key
  "The key of the show `guid` at `url` in the library `lib`, or nil unless
  the library has its feed for a subscription.

  The URL finds a show from a search, whose guid comes from its URL until
  the fetched feed gives one of its own. A show that the library holds
  only for an episode that the listener played gets the page of a show
  outside the library, since the library has no other episodes of it."
  [lib guid url]
  (let [feed? #(and (some? (get-in lib [:feeds % :feed]))
                    (profile/subscribed? (:profile lib) %))]
    (cond
      (feed? guid)
      guid

      url
      (let [k (get (profile/by-url (:profile lib)) (guid/normal-url url))]
        (when (and k (feed? k))
          k)))))

(defn subscription-of
  "The subscription to the show `guid` in the library `lib`, whether or not
  the library has its feed, e.g. one whose feed was never fetched, or nil."
  [lib guid]
  (get (profile/subscriptions (:profile lib)) guid))

(defn subscribed-key
  "The key of the subscription at `url` in the library `lib`, whether or
  not the library has its feed, or nil."
  [lib url]
  (let [profile (:profile lib)
        k       (get (profile/by-url profile) (guid/normal-url url))]
    (when (profile/subscribed? profile k)
      k)))

(def library-page
  "The page of the show under a key in a library, made again only when its
  feed or the profile changes, since sorting thousands of episodes takes a
  while."
  (memo/remember-last (fn [lib k] (view->page (library/show lib k view-opts)))
                      (fn [lib k] [(get-in lib [:feeds k]) (:profile lib) k])))

(defn requested?
  "Whether the `request` is in flight in the `state`."
  [state request]
  (contains? (:requests state) request))

(defn requested
  "The `state` with the `request` in flight, with the map `m` of its
  details, if any."
  ([state request]
   (requested state request {}))
  ([state request m]
   (assoc-in state [:requests request] m)))

(defn answered
  "The `state` once the `request` is no longer in flight."
  [state request]
  (update state :requests dissoc request))

(defn source
  "The feed of the show `guid` for its page in `state`, or nil when its URL
  is unknown.

  A feed in the library is a map of its :url, its :key in the library, the
  :feed and :whole?. Any other is the entry in :feeds at the URL from the
  link to the page, the cache, the Podcast Index or a subscription whose
  feed the library doesn't have, with the :url and, once it has arrived,
  the :feed."
  [state guid]
  (let [lib                                (:library state)
        {:keys [path-params query-params]} (:route state)
        url                                (or (when (= guid (:guid path-params))
                                                 (:url query-params))
                                               (get-in state [:feed-urls guid :url])
                                               (some-> lib (subscription-of guid) :url))
        k                                  (some-> lib (library-key guid url))]
    (cond
      k   {:url    (get-in lib [:feeds k :url])
           :key    k
           :feed   (get-in lib [:feeds k :feed])
           :whole? true}
      url (assoc (get-in state [:feeds url]) :url url))))

(defn search-result
  "The result of a search in the `state` for the show `guid`, or nil."
  [state guid]
  (some (fn [search]
          (some #(when (= guid (:guid %)) %) (search/results-of search)))
        (vals (:searches state))))

(defn with-sized-artwork
  "The `page` of a feed, with the artwork of the earlier page `known` when
  the feed has a single size, often of 3000 pixels, and `known` has
  several, e.g. from a search at Apple."
  [page known]
  (if (and (empty? (:artwork-sizes page)) (seq (:artwork-sizes known)))
    (merge page (select-keys known [:artwork :artwork-sizes]))
    page))

(def feed-page
  "The page of the show from the parsed `feed` at `url`, with the artwork
  of the search `result` that found it, made again only when one of them
  changes, since a feed of thousands of episodes takes a while."
  (memo/remember-last (fn [url feed result]
                        (with-sized-artwork (feed->page url feed)
                          (some-> result result->page)))))

(defn page
  "The page of the show `guid` in `state`, from its feed or else from a
  search result, or nil when there's neither."
  [state guid]
  (let [{:keys [url key feed]} (source state guid)
        result                 (search-result state guid)]
    (cond
      key    (library-page (:library state) key)
      feed   (feed-page url feed result)
      result (result->page result))))

(defn status
  "Where the feed of the show `guid` in `state` stands: :looking-up its
  URL, :loading, :checking it again, :done or :failed, or nil when no load
  has begun."
  [state guid]
  (let [{:keys [url key feed whole? error]} (source state guid)]
    (cond
      (requested? state [:look-up guid])                    :looking-up
      (or key whole?)                                       (if (requested? state [:check url])
                                                              :checking
                                                              :done)
      (some #(requested? state [% url]) [:open :head :whole]) :loading
      error                                                 :failed
      ;; only the start of the feed, e.g. from a prefetch
      feed                                                  :loading)))

(defn arrived
  "The `state` once `feed`, the start of the feed at `url`, has arrived,
  unless the whole feed came first."
  [state url feed]
  (if (get-in state [:feeds url :whole?])
    state
    (update-in state [:feeds url] assoc :feed feed :whole? false)))

(defn cache-key
  "The cache key of the feed at `url`, its normalized URL."
  [url]
  (guid/normal-url url))

(def validator-keys
  "The keys of a fetch's validators, and of the time until which the feed
  is fresh."
  [:etag :last-modified :not-before])

(def subscription-keys
  "The keys of a whole fetch that a subscription takes besides the feed and
  the validators: where the feed moved to for good, and its WebSub hubs."
  [:permanent-url :new-feed-url :websub])

(defn kept
  "What the cache keeps of the whole feed `fetched`: its URL, the feed, the
  fetch's validators, and where the feed moved to and its hubs."
  [fetched]
  (select-keys fetched (concat [:url :feed] validator-keys subscription-keys)))

(defn validators
  "The validators of the fetch `fetched`, for a subscription or the next
  fetch of the feed."
  [fetched]
  (into {}
        (remove (comp nil? val))
        (select-keys fetched validator-keys)))

(defn fresh?
  "Whether the whole feed `fetched` at the instant `fetched-at` is fresh at
  the instant `at`: until the :not-before from its server's max-age, or
  else for an hour."
  [fetched fetched-at at]
  (< (inst-ms at)
     (or (some-> (:not-before fetched) inst-ms)
         (+ (inst-ms fetched-at) (* 60 60 1000)))))

(defn whole
  "The `state` with the whole feed at `url`, `fetched` at the instant
  `fetched-at`, replacing any earlier entry. The feed is `checked?` when
  it came from the network or was fresh in the cache."
  [state url fetched fetched-at checked?]
  (assoc-in state [:feeds url] (merge {:feed       (:feed fetched)
                                       :whole?     true
                                       :validators (validators fetched)
                                       :fetched-at fetched-at
                                       :checked?   checked?}
                                      (select-keys fetched subscription-keys))))

;; RFC 9110, section 15.5.5 and 15.5.11: a feed that's gone from a URL
(defn gone?
  "Whether a fetch failed with `error` because the feed is gone from its
  URL, a 404 or a 410."
  [error]
  (contains? #{404 410} (:status error)))

(defn gone-from-cache?
  "Whether the whole feed of the show `guid` in `state` failed with `error`
  because it's gone from the URL that the cache had."
  [state guid error]
  (and (= :cache (get-in state [:feed-urls guid :from]))
       (gone? error)))

(defn new-episodes
  "How many episodes the feed `fresh` has that the feed `old` hasn't."
  [old fresh]
  (let [known (set (map guid/item-guid (:rss/items old)))]
    (count (remove known (map guid/item-guid (:rss/items fresh))))))

;; podcast-clj's refresh counts such a feed as a failure too, with
;; :fetch/empty-feed
(defn emptied?
  "Whether the feed `fresh` has no items where the feed `old` had some, as
  some hosts answer during an outage."
  [old fresh]
  (boolean (and (seq (:rss/items old)) (empty? (:rss/items fresh)))))

(defn listed?
  "Whether the page in `state` is the Show page of the show `guid`, which
  lists its episodes."
  [{{:keys [name path-params]} :route :as state} guid]
  (and (= :route/show name) (= guid (:guid path-params))))

;; web.dev, CLS: a shift that follows the listener's own tap within 500 ms
;; doesn't count, but one that comes on its own does
;; https://web.dev/articles/cls#user-initiated_layout_shifts
(defn refetched
  "The `state` with the feed of the show `guid` at `url`, `fetched` again
  at the instant `at`, in place of the old one. While the episodes are
  listed, a feed with new episodes waits as :newer until the listener
  asks for it."
  [state guid url fetched at]
  (let [n (new-episodes (get-in state [:feeds url :feed]) (:feed fetched))]
    (if (and (pos? n) (listed? state guid))
      (assoc-in state [:feeds url :newer]
                {:fetched fetched :fetched-at at :count n})
      (whole state url fetched at true))))

(defn more-shown
  "The actions that list another page of the `total` episodes of the show
  `guid` in `state`, and announce how many are listed."
  [state guid total]
  (let [shown (min total (+ (get-in state [:episodes-shown guid] page-size)
                            page-size))]
    [[:store/assoc-in [:episodes-shown guid] shown]
     [:store/assoc :announcement (str "Showing " shown " of " total " episodes")]]))

(defn whole-feed
  "The whole feed at `url` in `state`, as a fetch returns it with its
  validators, where it moved to and its hubs, or nil. A :newer feed that
  waits comes first, since a subscription should start from the newest."
  [state url]
  (let [{:keys [feed whole? validators newer] :as entry} (get-in state [:feeds url])]
    (when whole?
      (or (:fetched newer)
          (merge validators (select-keys entry subscription-keys) {:feed feed})))))

(defn newer-shown
  "The `state` with the :newer feed of the show `guid` in place of the one
  on the page."
  [state guid]
  (let [url             (:url (source state guid))
        {:keys [newer]} (get-in state [:feeds url])]
    (if newer
      (whole state url (:fetched newer) (:fetched-at newer) true)
      state)))
