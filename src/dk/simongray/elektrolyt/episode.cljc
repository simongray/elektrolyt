(ns dk.simongray.elektrolyt.episode
  "What the Episode page shows of an episode, from its show's feed in the
  library or the one that the Show page loaded, whole or just its start.

  The page names the episode by a short hash of its guid and the slug of
  its title. What's read of an episode's files is kept by its id, as a map
  of:

  - :status, :reading or :read, and :at, when the read began, in
    milliseconds
  - :result, or :error when the read failed
  - :transcript-status and :transcript, once the listener asks for the
    transcript"
  (:require [dk.simongray.podcast.app.episode :as episode]
            [dk.simongray.podcast.feed :as feed]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.format.chapters :as chapters]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.ui.links :as links]
            [dk.simongray.elektrolyt.ui.memo :as memo]
            [dk.simongray.elektrolyt.ui.routes :as routes]))

(def view-opts
  "The options of the episode's view for the page: the show notes'
  headings below the page's h2s, their links marked where they leave the
  app, and neither the notes as text nor links to other apps, which the
  page doesn't show."
  {:heading-level 3
   :link-fn       links/leaving
   :except        #{:text :links}})

(defn item-of
  "The item in `items` whose guid has the short `hash`, and of several such
  items the one whose title gives the `slug`, or nil. Of items that share
  a guid, it's the first, the one that the library plays."
  [items hash slug]
  (let [matches (resolve/distinct-items (guid/matching-items items hash))]
    (or (some #(when (= slug (routes/slug (resolve/title %))) %) matches)
        (first matches))))

;; Apple, A Podcaster's Guide to RSS: with only one season in the feed,
;; Apple Podcasts doesn't display a season number
(defn one-season?
  "Whether the items of `feed` are in one season at most."
  [feed]
  (>= 1 (count (into #{} (keep (comp :season resolve/numbering)) (:rss/items feed)))))

(defn page-of
  "The page of the `item` of the show `guid` in the show's source, a map of
  its :feed, :url and library :key, if any. Each time in the show notes is
  what `time-fn` returns for the page, the seconds and the text of the
  time.

  The link, locations and license are the item's own, since the page's
  Details show what's particular to the episode. The people and funding
  are the show's when the item has none, and the language is the show's,
  since an RSS item names none."
  [guid {:keys [feed url key]} item time-fn]
  (let [item-guid   (guid/item-guid item)
        title       (resolve/title item)
        guid        (or key guid)
        outside-url (when-not key url)
        show-title  (resolve/title feed)
        base        {:id        [(or key (guid/feed-key feed url)) item-guid]
                     :guid      guid
                     :item-guid item-guid
                     :title     title
                     :published (resolve/published item)
                     :language  (resolve/language feed)
                     :show      {:title show-title
                                 :href  (routes/show-href guid show-title outside-url)}
                     :url       outside-url
                     :href      (routes/episode-href guid item-guid title
                                                     outside-url)
                     :src       (:url (resolve/enclosure item))}
        view        (episode/view feed item
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
  "What playing the episode `page` takes: its :id, and the :guid of its
  show, the :url of its feed and its :item-guid, which add it to the
  library if it isn't there yet."
  [page]
  (select-keys page [:id :guid :url :item-guid]))

(def page-in
  "The page of an episode in the feed of a source, made again only when the
  feed or the link changes, since the page renders at every change of the
  state and hashing thousands of guids takes a while. The last four are
  kept, for going back and forth between episodes."
  (memo/remember-recent 4
                        (fn [guid src hash slug time-fn]
                          (when-let [item (item-of (:rss/items (:feed src)) hash slug)]
                            (page-of guid src item time-fn)))
                        (fn [guid {:keys [feed url key]} hash slug time-fn]
                          [feed url key guid hash slug time-fn])))

(defn page
  "The page of an episode of the show `guid` in `state`, by the short
  `hash` and the `slug` of its link, or nil when the feed at hand doesn't
  have it. The `time-fn` makes the times in the show notes."
  [state guid hash slug time-fn]
  (let [src (show/source state guid)]
    (when (:feed src)
      (page-in guid src hash slug time-fn))))

(defn status
  "Where the Episode page of the show `guid` in `state` stands without an
  episode to show: :looking-up the show, :loading its feed or checking a
  cached one again, :failed, the episode :not-found in the feed, or
  :no-show at all."
  [state guid]
  (case (show/status state guid)
    :looking-up          :looking-up
    :failed              :failed
    (:loading :checking) :loading
    :done                :not-found
    :no-show))

;; Otherwise a tab that plays for days would keep every transcript that the
;; listener opened, and an hour's is ten thousand words. The count matches
;; the cache of shows outside the library.
(def kept-count
  "How many episodes' reads the state keeps besides the one that plays,
  those that began last."
  20)

(defn kept
  "The `episodes` with only the `n` whose reads began last and the one
  `playing`."
  [episodes playing n]
  (->> (dissoc episodes playing)
       (sort-by (comp #(or (:at %) 0) val) >)
       (drop n)
       (map key)
       (apply dissoc episodes)))

(defn shown-chapters
  "The chapters to list for the episode `page` and its `files`: those read
  from the chapters file or the media file, or else the feed's. Chapters
  inferred from the show notes are left out, since the notes show their
  times already."
  [page files]
  (let [{:keys [source document]} (or (get-in files [:result :chapters])
                                      (get-in page [:view :chapters]))]
    (when (and document (not= :inferred source))
      (seq (chapters/visible document)))))
