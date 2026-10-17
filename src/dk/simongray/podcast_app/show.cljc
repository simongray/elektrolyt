(ns dk.simongray.podcast-app.show
  "What the Show page shows of a show, from the best source at hand: the
  feed that the library keeps, the whole feed, the start of the feed, or
  what a search found.

  The state keeps each show that isn't in the library under its guid in
  :shows, as a map of the :url of its feed, the :page that the views
  render, the :status of its load, the :head of the feed, i.e. its start,
  and the :feed once it's whole, which a subscription takes without a
  fetch of its own. The status is :previewed
  once the start of the feed is in, :loading while the whole feed is
  fetched, :done once it's in, and :failed with the :error of the fetch."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.app.show :as show]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast-app.links :as links]))

(def page-size
  "How many episodes the page lists at first, and how many more each time
  the listener asks for more."
  20)

(def view-opts
  "The options of app.show/view for the page, which leaves out what it
  doesn't show, puts the headings of the notes beside the page's own h2s,
  and opens their links to the web in a new tab."
  {:except        #{:seasons :links :text}
   :heading-level 2
   :link-fn       links/new-tab})

(defn episode-row
  "The episode `item` as a row of the list of episodes."
  [item]
  {:guid      (guid/item-guid item)
   :title     (resolve/title item)
   :published (resolve/published item)
   :duration  (resolve/duration item)})

(defn view->page
  "The page of the show `view`, as app.show/view or library/show gives it."
  [view]
  {:title         (:title view)
   :author        (resolve/author view)
   :artwork       (:artwork view)
   :artwork-sizes (resolve/artwork-sizes view)
   :notes         (:notes view)
   :episodes      (mapv episode-row (:episodes view))})

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

(defonce ^{:doc "The last page that library-page made, with what it made it
  from, since the page renders again at every change of the state."}
  last-library-page
  (atom nil))

(defn library-page
  "The page of the show that the library `lib` keeps under the key `k`.

  It's made again only when the feed or the profile has changed, since a
  show of thousands of episodes sorts them all."
  [lib k]
  (let [entry   (get-in lib [:feeds k])
        profile (:profile lib)
        [e p kk made] @last-library-page]
    (if (and (identical? e entry) (identical? p profile) (= kk k))
      made
      (let [made (view->page (library/show lib k view-opts))]
        (reset! last-library-page [entry profile k made])
        made))))

(defn feed-url
  "The URL of the feed of the show `guid` in the `state`: the one that the
  link to its page gives, the one it was loaded from, or the one that the
  library keeps, or nil."
  [state guid]
  (or (get-in state [:route :query-params :url])
      (get-in state [:shows guid :url])
      (when-let [lib (:library state)]
        (when-let [k (library-key lib guid nil)]
          (get-in lib [:feeds k :url])))))

(defn search-result
  "The result of a search in the `state` for the show `guid`, or nil."
  [state guid]
  (some (fn [{:keys [results]}]
          (some #(when (= guid (:guid %)) %) results))
        (vals (:searches state))))

(defn page
  "The page of the show `guid` in the `state`, from the library, its load,
  or a search, or nil when none of them has it."
  [state guid]
  (let [{:keys [page] :as loaded} (get-in state [:shows guid])
        lib                       (:library state)
        k                         (when lib (library-key lib guid (feed-url state guid)))]
    (cond
      k     (library-page lib k)
      page  page
      :else (some-> (search-result state guid) result->page))))

(defn with-sized-artwork
  "The `page` of a feed with the artwork of the page `known` before it, when
  the feed gives its artwork in one size alone, often 3000 pixels, and the
  page before had it in several, as a search at Apple has it."
  [page known]
  (if (and (empty? (:artwork-sizes page)) (seq (:artwork-sizes known)))
    (merge page (select-keys known [:artwork :artwork-sizes]))
    page))

(defn arrived
  "The show `guid` in the `state` once its `feed` at `url` has arrived, the
  start of it or, when `whole?`, all of it. A start that arrives after the
  whole feed changes nothing."
  [state guid url feed whole?]
  (let [shown (get-in state [:shows guid])
        known (or (:page shown) (some-> (search-result state guid) result->page))]
    (if (and (not whole?) (= :done (:status shown)))
      state
      (assoc-in state [:shows guid]
                (cond-> (assoc shown
                               :url    url
                               :page   (with-sized-artwork (feed->page url feed) known)
                               :status (cond
                                         whole?                          :done
                                         (= :loading (:status shown))   :loading
                                         :else                           :previewed))
                  whole?       (-> (assoc :feed feed) (dissoc :head))
                  (not whole?) (assoc :head feed))))))
