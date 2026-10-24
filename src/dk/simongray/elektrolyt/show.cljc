(ns dk.simongray.elektrolyt.show
  "What the Show page shows of a show, from the best source at hand: the
  feed that the library keeps, the whole feed, the start of the feed, or
  what a search found. The function source picks the feed, and page makes
  the page of it.

  The state keeps what it knows of the feeds of shows outside the library
  apart from the requests for them:

  - :feeds keeps each feed by its URL, as a map of the :feed, whole or its
    start as :whole? says, the :validators and :fetched-at of the fetch of
    a whole feed, from the cache or the network, whether this visit has
    :checked? it, i.e. fetched it or found it fresh, the :error of a whole
    fetch that failed, and a :newer feed that waits. A fetch that brings
    new episodes while the page lists the episodes waits as :newer, with
    its :count of them, so that the list on screen doesn't change until
    the listener asks.
  - :feed-urls keeps where the cache or the Podcast Index says the feed of
    a show is, by its guid: the :url and where it came :from, :cache or
    :index, or :not-found? when the index doesn't know it.
  - :requests keeps the requests in flight, by request: [:open url] while
    the cache is read, with :whole? once a page asks for the whole feed,
    [:head url] and [:whole url] while the start or all of a feed is
    fetched, [:check url] while a feed from the cache is fetched again,
    and [:look-up guid] while the Podcast Index is asked.

  The state keeps how many episodes the page lists in :episodes-shown, by
  the guid of the show."
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
  "The options of app.show/view for the page, which leaves out what it
  doesn't show, puts the headings of the notes beside the page's own h2s,
  and marks where their links leave the app to, e.g. a new tab."
  {:except        #{:seasons :links :text}
   :heading-level 2
   :link-fn       links/leaving})

(defn episode-row
  "The episode `item` as a row of the list of episodes, with its own
  artwork, if it has any."
  [item]
  {:guid          (guid/item-guid item)
   :title         (resolve/title item)
   :published     (resolve/published item)
   :duration      (resolve/duration item)
   :artwork       (resolve/artwork item)
   :artwork-sizes (resolve/artwork-sizes item)
   :supplement    (feed/supplement item)})

(def facts
  "The properties of a show view that its page shows besides the title,
  the artwork and the notes: in its intro, in its Details and as its
  People."
  [:link :categories :language :copyright :update-frequency :complete?
   :serial? :medium :explicit? :ai-content? :locations :license :people
   :funding])

;; The latest episode anchors the cadence, so that the page needs no clock
;; and the cadence of a show that stopped stays what it was
(defn cadence
  "How often the show of the `episodes`, the items of its view, publishes,
  in milliseconds, as schedule/cadence estimates it up to `latest`, the
  date of the newest, or nil."
  [episodes latest]
  (when latest
    (schedule/cadence {:rss/items episodes} latest)))

(defn view->page
  "The page of the show `view`, as app.show/view or library/show gives it."
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
  "The page of the show of the parsed `feed` at `url`, whole or its start."
  [url feed]
  (view->page (show/view feed (assoc view-opts :url url))))

(defn result->page
  "The page of a show that a search found, the `result`, before its feed
  has arrived: what the directory knows, and no episodes yet."
  [{:keys [title author artwork artwork-sizes] :as result}]
  {:title         title
   :author        author
   :artwork       artwork
   :artwork-sizes artwork-sizes
   :episodes      []})

(defn library-key
  "The key that the library `lib` keeps the show `guid` at `url` under, or
  nil when it keeps no feed of it, or keeps it for no subscription.

  A show from a search has the guid that its URL gives, which the feed can
  replace with one of its own when it's fetched, so the URL finds it too.
  A show that the library keeps for an episode that the listener played
  alone has only that episode, so its page is that of a show outside it."
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

(def library-page
  "The page of the show that a library keeps under a key, made again only
  when its feed or the profile changes, since a show of thousands of
  episodes sorts them all."
  (memo/remember-last (fn [lib k] (view->page (library/show lib k view-opts)))
                      (fn [lib k] [(get-in lib [:feeds k]) (:profile lib) k])))

(defn requested?
  "Whether the `request` is in flight in the `state`."
  [state request]
  (contains? (:requests state) request))

(defn requested
  "The `state` with the `request` in flight, with the map `m` of what it
  needs to know about it, if any."
  ([state request]
   (requested state request {}))
  ([state request m]
   (assoc-in state [:requests request] m)))

(defn answered
  "The `state` once the `request` is no longer in flight."
  [state request]
  (update state :requests dissoc request))

(defn source
  "The feed of the show `guid` that its page in the `state` shows, or nil
  when nothing says where it is.

  It's the feed that the library keeps, as a map of its :url, the :key
  that the library keeps it under and the :feed, which is :whole?. Or
  else it's what :feeds keeps at the URL that the link to the page gives,
  or that the cache or the Podcast Index found, with the :url, which has
  the :feed once it has arrived."
  [state guid]
  (let [lib                                (:library state)
        {:keys [path-params query-params]} (:route state)
        url                                (or (when (= guid (:guid path-params))
                                                 (:url query-params))
                                               (get-in state [:feed-urls guid :url]))
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
  "The `page` of a feed with the artwork of the page `known` before it, when
  the feed gives its artwork in one size alone, often 3000 pixels, and the
  page before had it in several, as a search at Apple has it."
  [page known]
  (if (and (empty? (:artwork-sizes page)) (seq (:artwork-sizes known)))
    (merge page (select-keys known [:artwork :artwork-sizes]))
    page))

(def feed-page
  "The page of the show of the parsed `feed` at `url`, with the artwork of
  the search `result` that found it, made again only when one of them
  changes, since a feed of thousands of episodes takes a while."
  (memo/remember-last (fn [url feed result]
                        (with-sized-artwork (feed->page url feed)
                          (some-> result result->page)))))

(defn page
  "The page of the show `guid` in the `state`, from the feed that source
  picks, or from a search before the feed has arrived, or nil when none
  of them has it."
  [state guid]
  (let [{:keys [url key feed]} (source state guid)
        result                 (search-result state guid)]
    (cond
      key    (library-page (:library state) key)
      feed   (feed-page url feed result)
      result (result->page result))))

(defn status
  "Where the feed of the show `guid` in the `state` stands: :looking-up
  where it is, :done once it's whole, while it's :checking again, :loading
  it, or :failed, or nil when nothing loads it."
  [state guid]
  (let [{:keys [url key feed whole? error]} (source state guid)]
    (cond
      (requested? state [:look-up guid])                    :looking-up
      (or key whole?)                                       (if (requested? state [:check url])
                                                              :checking
                                                              :done)
      (some #(requested? state [% url]) [:open :head :whole]) :loading
      error                                                 :failed
      ;; the start of a feed whose whole fetch hasn't begun, e.g. after a
      ;; pointer on a link asked for it
      feed                                                  :loading)))

(defn arrived
  "The `state` once the start of the feed at `url` has arrived, `feed`. A
  start that arrives after the whole feed changes nothing."
  [state url feed]
  (if (get-in state [:feeds url :whole?])
    state
    (update-in state [:feeds url] assoc :feed feed :whole? false)))

(defn cache-key
  "The key in the cache of the feed at `url`, as podcast-clj normalizes the
  URL."
  [url]
  (guid/normal-url url))

(def validator-keys
  "The keys of the validators of a fetch, and of the time until which the
  feed is fresh."
  [:etag :last-modified :not-before])

(defn kept
  "What the cache keeps of the whole feed that a fetch gave, `fetched`: the
  feed and the validators of the fetch."
  [fetched]
  (select-keys fetched (into [:url :feed] validator-keys)))

(defn validators
  "The validators of the fetch `fetched`, which a subscription and the next
  fetch of the feed take."
  [fetched]
  (into {}
        (remove (comp nil? val))
        (select-keys fetched validator-keys)))

;; podcast/fetch! reads the max-age of the feed's server into :not-before,
;; an hour at most
(defn fresh?
  "Whether the whole feed that a fetch gave, `fetched` at the instant
  `fetched-at`, is fresh at the instant `at`: until the time that its
  server names runs out, or for an hour when it names none."
  [fetched fetched-at at]
  (< (inst-ms at)
     (or (some-> (:not-before fetched) inst-ms)
         (+ (inst-ms fetched-at) (* 60 60 1000)))))

(defn whole
  "The `state` with the whole feed at `url` that a fetch gave, `fetched` at
  the instant `fetched-at`, which this visit has `checked?` when it came
  from the network or was fresh in the cache, in place of what it knew of
  the feed."
  [state url fetched fetched-at checked?]
  (assoc-in state [:feeds url] {:feed       (:feed fetched)
                                :whole?     true
                                :validators (validators fetched)
                                :fetched-at fetched-at
                                :checked?   checked?}))

;; RFC 9110, section 15.5.5 and 15.5.11: a feed that's gone from a URL
(defn gone-from-cache?
  "Whether the whole feed of the show `guid` in the `state` failed with
  `error` because it's gone from the URL that the cache had for it, a 404
  or a 410, so that the Podcast Index is asked again."
  [state guid error]
  (and (= :cache (get-in state [:feed-urls guid :from]))
       (contains? #{404 410} (:status error))))

(defn new-episodes
  "How many episodes the feed `fresh` has that the feed `old` hasn't."
  [old fresh]
  (let [known (set (map guid/item-guid (:rss/items old)))]
    (count (remove known (map guid/item-guid (:rss/items fresh))))))

;; web.dev, CLS: a shift that follows the listener's own tap within 500 ms
;; doesn't count, but one that comes on its own does
;; https://web.dev/articles/cls#user-initiated_layout_shifts
(defn listed?
  "Whether the `state` shows the list of episodes of the show `guid`, i.e.
  its Show page."
  [{{:keys [name path-params]} :route :as state} guid]
  (and (= :route/show name) (= guid (:guid path-params))))

(defn refetched
  "The `state` once the feed at `url` of the show `guid` was fetched again,
  `fetched` at the instant `at`: in place of the feed that it had, unless
  it brings new episodes while its list shows. Those wait as :newer until
  the listener asks for them."
  [state guid url fetched at]
  (let [n (new-episodes (get-in state [:feeds url :feed]) (:feed fetched))]
    (if (and (pos? n) (listed? state guid))
      (assoc-in state [:feeds url :newer]
                {:fetched fetched :fetched-at at :count n})
      (whole state url fetched at true))))

(defn more-shown
  "The actions of the listener's ask for more of the `total` episodes of the
  show `guid` in the `state`: list another page of them, and say how many
  are listed now."
  [state guid total]
  (let [shown (min total (+ (get-in state [:episodes-shown guid] page-size)
                            page-size))]
    [[:store/assoc-in [:episodes-shown guid] shown]
     [:store/assoc :announcement (str "Showing " shown " of " total " episodes")]]))

(defn whole-feed
  "The whole feed at `url` that the Show page loaded into the `state`, as a
  fetch gives it, with its validators, or nil. A newer feed that waits for
  the listener comes first, since a subscription should have the newest."
  [state url]
  (let [{:keys [feed whole? validators newer]} (get-in state [:feeds url])]
    (when whole?
      (or (:fetched newer) (assoc validators :feed feed)))))

(defn newer-shown
  "The `state` with the :newer feed of the show `guid` that waited, in
  place of the one on the page."
  [state guid]
  (let [url             (:url (source state guid))
        {:keys [newer]} (get-in state [:feeds url])]
    (if newer
      (whole state url (:fetched newer) (:fetched-at newer) true)
      state)))
