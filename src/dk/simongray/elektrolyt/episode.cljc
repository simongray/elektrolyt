(ns dk.simongray.elektrolyt.episode
  "What the Episode page shows of an episode, from the feed of its show that
  the library keeps, or that the Show page loaded, whole or its start.

  The page names the episode by the short hash of its guid and the slug of
  its title, as dk.simongray.elektrolyt.routes has it. The state keeps
  what the files of an episode say under its id in :episodes, as a map of
  the :status of their read, :reading or :read, the time in milliseconds
  when it began, :at, its :result, and the :transcript-status and
  :transcript once the listener asks for the transcript."
  (:require [dk.simongray.podcast.app.episode :as episode]
            [dk.simongray.podcast.feed :as feed]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.format.chapters :as chapters]
            [dk.simongray.elektrolyt.memo :as memo]
            [dk.simongray.elektrolyt.routes :as routes]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.ui.links :as links]))

(def view-opts
  "The options of app.episode/view for the page: the headings of the show
  notes below the page's own h2s, their links marked for where they leave
  the app to, e.g. a new tab, and neither the notes as text nor links to
  other apps, which the page doesn't show."
  {:heading-level 3
   :link-fn       links/leaving
   :except        #{:text :links}})

(defn item-of
  "The item of `items` that the short `hash` of its guid names, and of two
  such items the one whose title gives the `slug`, or nil."
  [items hash slug]
  (let [matches (guid/matching-items items hash)]
    (or (some #(when (= slug (routes/slug (resolve/title %))) %) matches)
        (first matches))))

;; Apple, A Podcaster's Guide to RSS: with only one season in the feed,
;; Apple Podcasts doesn't display a season number
(defn one-season?
  "Whether the items of `feed` are in one season at most."
  [feed]
  (>= 1 (count (into #{} (keep (comp :season resolve/numbering)) (:rss/items feed)))))

(defn page-of
  "The page of the `item` of the show `guid` in the feed `source`, as
  show/source gives it. Each time in the show notes is what `time-fn` gives for the
  page, the seconds and the text of the time.

  The page's link, locations and license are the item's own, since its
  Details show what's particular to it, and its people and funding are
  its show's when it has none of its own. Its language is its show's,
  since an item of RSS names none."
  [guid {:keys [feed url key]} item time-fn]
  (let [item-guid (guid/item-guid item)
        title     (resolve/title item)
        guid      (or key guid)
        base      {:id        [(or key (guid/feed-key feed url)) item-guid]
                   :guid      guid
                   :item-guid item-guid
                   :title     title
                   :published (resolve/published item)
                   :language  (resolve/language feed)
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
    (merge base
           (select-keys view [:explicit? :ai-content? :people :funding
                              :content-links :enclosure])
           {:duration      (resolve/duration item)
            :artwork       (:artwork view)
            :artwork-sizes (resolve/artwork-sizes view)
            :notes         (:notes view)
            :numbering     (cond-> (:numbering view)
                             (one-season? feed) (dissoc :season :season-name))
            :link          (resolve/link item)
            :locations     (resolve/locations item)
            :license       (resolve/license item)
            :supplement    (feed/supplement item)
            :view          view})))

(defn playable
  "What the player needs of the episode `page` to play it: its :id, and for
  an episode that the library doesn't have yet, the :guid of the page of
  its show, the :url of its feed and its :item-guid, to add it."
  [page]
  (select-keys page [:id :guid :url :item-guid]))

(def page-in
  "The page of an episode in the feed of a source, for page, made again
  only when the feed or the link changes, since the page renders at every
  change of the state, and hashing the guids of thousands of items takes
  a while."
  (memo/remember-last (fn [guid src hash slug time-fn]
                        (when-let [item (item-of (:rss/items (:feed src)) hash slug)]
                          (page-of guid src item time-fn)))
                      (fn [guid {:keys [feed url key]} hash slug time-fn]
                        [feed url key guid hash slug time-fn])))

(defn page
  "The page of the episode `hash` of the show `guid` in the `state`, which
  the link to it gives the `slug` of, or nil when the feed at hand doesn't
  have it. The `time-fn` makes the times of the show notes, as page-of has
  it."
  [state guid hash slug time-fn]
  (let [src (show/source state guid)]
    (when (:feed src)
      (page-in guid src hash slug time-fn))))

(defn status
  "Where the Episode page of the show `guid` in the `state` stands while it
  has no episode to show: :looking-up its show, :loading its feed, or
  checking the one from the cache again, :failed to, the episode
  :not-found in it, or :no-show at all."
  [state guid]
  (case (show/status state guid)
    :looking-up          :looking-up
    :failed              :failed
    (:loading :checking) :loading
    :done                :not-found
    :no-show))

;; A tab that plays for days would otherwise keep each transcript that the
;; listener opened, and an hour's is ten thousand words. The number is
;; that of the shows outside the library that the cache keeps.
(def kept-count
  "How many episodes' reads the state keeps, besides the one that plays:
  those that began last."
  20)

(defn kept
  "The `episodes` whose reads the state keeps, with only the `n` that began
  last and the episode `playing`."
  [episodes playing n]
  (->> (dissoc episodes playing)
       (sort-by (comp #(or (:at %) 0) val) >)
       (drop n)
       (map key)
       (apply dissoc episodes)))

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
