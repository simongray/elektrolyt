(ns dk.simongray.podcast-app.episode
  "What the Episode page shows of an episode, from the feed of its show that
  the library keeps, or that the Show page loaded, whole or its start.

  The page names the episode by the short hash of its guid and the slug of
  its title, as dk.simongray.podcast-app.routes has it. The state keeps
  what the files of an episode say under its id in :episodes, as a map of
  the :status of their read, :reading or :read, its :result, and the
  :transcript-status and :transcript once the listener asks for the
  transcript."
  (:require [dk.simongray.podcast.app.episode :as episode]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.format.chapters :as chapters]
            [dk.simongray.podcast-app.links :as links]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.show :as show]))

(def view-opts
  "The options of app.episode/view for the page: the headings of the show
  notes below the page's own h2s, their links to the web in a new tab, and
  neither the notes as text nor links to other apps, which the page
  doesn't show."
  {:heading-level 3
   :link-fn       links/new-tab
   :except        #{:text :links}})

(defn item-of
  "The item of `items` that the short `hash` of its guid names, and of two
  such items the one whose title gives the `slug`, or nil."
  [items hash slug]
  (let [matches (guid/matching-items items hash)]
    (or (some #(when (= slug (routes/slug (resolve/title %))) %) matches)
        (first matches))))

(defn source
  "The feed of the show `guid` in the `state`, as a map of the :feed, its
  :url, the :key that the library keeps it under, when it does, and
  whether it's :whole?, or nil when the state has none of it yet."
  [state guid]
  (let [lib                           (:library state)
        url                           (show/feed-url state guid)
        k                             (some-> lib (show/library-key guid url))
        {:keys [feed head] :as shown} (get-in state [:shows guid])]
    (cond
      k    {:feed   (get-in lib [:feeds k :feed])
            :url    (get-in lib [:feeds k :url])
            :key    k
            :whole? true}
      feed {:feed feed :url (:url shown) :whole? true}
      head {:feed head :url (:url shown) :whole? false})))

(defn page-of
  "The page of the `item` of the show `guid` in the feed `source`, as source
  gives it. Each time in the show notes is what `time-fn` gives for the
  page, the seconds and the text of the time."
  [guid {:keys [feed url key]} item time-fn]
  (let [item-guid (guid/item-guid item)
        title     (resolve/title item)
        guid      (or key guid)
        base      {:id        [(or key (guid/feed-key feed url)) item-guid]
                   :guid      guid
                   :item-guid item-guid
                   :title     title
                   :published (resolve/published item)
                   :show      {:title (resolve/title feed)
                               :href  (routes/show-href guid (resolve/title feed)
                                                        (when-not key url))}
                   :url       (when-not key url)
                   :href      (routes/episode-href guid item-guid title
                                                   (when-not key url))
                   :src       (:url (resolve/enclosure item))}
        view      (episode/view feed item
                                (assoc view-opts
                                       :url url
                                       :timestamp-fn #(time-fn base %1 %2)))]
    (assoc base
           :duration      (resolve/duration item)
           :artwork       (:artwork view)
           :artwork-sizes (resolve/artwork-sizes view)
           :notes         (:notes view)
           :view          view)))

(defn playable
  "What the player needs of the episode `page` to play it: its :id, and for
  an episode that the library doesn't have yet, the :guid of the page of
  its show, the :url of its feed and its :item-guid, to add it."
  [page]
  (select-keys page [:id :guid :url :item-guid]))

(defonce ^{:doc "The last page that page made, with what it made it from,
  since the page renders again at every change of the state, and hashing
  the guids of thousands of items takes a while."}
  last-page
  (atom nil))

(defn page
  "The page of the episode `hash` of the show `guid` in the `state`, which
  the link to it gives the `slug` of, or nil when the feed at hand doesn't
  have it. The `time-fn` makes the times of the show notes, as page-of has
  it."
  [state guid hash slug time-fn]
  (when-let [{:keys [feed url key] :as src} (source state guid)]
    (let [made-from   [feed url key guid hash slug time-fn]
          [from made] @last-page]
      (if (and from
               (identical? feed (first from))
               (= (rest made-from) (rest from)))
        made
        (let [made (when-let [item (item-of (:rss/items feed) hash slug)]
                     (page-of guid src item time-fn))]
          (reset! last-page [made-from made])
          made)))))

(defn status
  "Where the Episode page of the show `guid` in the `state` stands while it
  has no episode to show: :looking-up its show, :loading its feed, :failed
  to, the episode :not-found in it, or :no-show at all."
  [state guid]
  (let [src              (source state guid)
        {:keys [status]} (get-in state [:shows guid])]
    (cond
      (= :looking-up status)                      :looking-up
      (= :failed status)                          :failed
      (:whole? src)                               :not-found
      (#{:previewing :previewed :loading} status) :loading
      :else                                       :no-show)))

(defn shown-chapters
  "The chapters to list for the `page` of an episode, with what its `files`
  say: those of the chapters file or the media file once they're read, else
  those of the feed. Chapters that the show notes imply are left out, since
  the notes show their times already."
  [page files]
  (let [{:keys [source document]} (or (get-in files [:result :chapters])
                                      (get-in page [:view :chapters]))]
    (when (and document (not= :inferred source))
      (seq (chapters/visible document)))))
