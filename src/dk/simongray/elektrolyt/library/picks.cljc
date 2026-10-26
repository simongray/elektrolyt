(ns dk.simongray.elektrolyt.library.picks
  "The picks: episodes in the listener's library like what they heard last,
  what they queued, an episode or a show, and the words they share with it.

  Picks are unplayed and not queued. Unless the listener chooses explicit
  picks in Settings, they're explicit only where they start from explicit
  episodes: under an explicit episode or show, and after a queue that has
  one. A show or an episode outside the library has picks too, from the
  feed that its page loaded."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.feed.search :as feed.search]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.elektrolyt.library.queue :as queue]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.ui.memo :as memo]))

;; Nielsen Norman Group: people expect only a handful of recommendations at
;; a time. One episode of each show, so that a list has as many shows as it
;; can. Home has as many as the start of the queue beside it.
;; https://www.nngroup.com/articles/recommendation-expectations/
(def places
  "The options of library/recommend at each place that shows picks. The
  end of the queue takes episodes of the queue's shows too, since another
  episode of a queued show is a natural one to queue next, as in Apple
  Podcasts' Continue Playing."
  {:home    {:limit 4 :max-per-show 1}
   :queue   {:limit 5 :max-per-show 1 :same-show? true}
   :episode {:limit 5 :max-per-show 1}
   :show    {:limit 5 :max-per-show 1}})

;; Feeds mark an episode explicit without saying why, e.g. for sex or for
;; swearing, so every explicit episode counts, and so does every episode of
;; an explicit show, which may mark some of them clean, as No Agenda does
(def clean
  "The criteria of episodes that are neither explicit nor of an explicit
  show."
  {:explicit? false :show-explicit? false})

(defn opts
  "The options of library/recommend at `place` in `state`: unplayed and
  unqueued picks, clean and from clean episodes unless the episodes they
  start from are `explicit?` or the listener chose explicit picks."
  [state place explicit?]
  (cond-> (assoc (places place) :where {:status :unplayed :queued? false})
    (not (or explicit? (settings/explicit-picks? state)))
    (-> (update :where merge clean)
        (assoc :from clean))))

(defn explicit-in?
  "Whether any of the episodes `ids` in the library `lib` is explicit or of
  an explicit show, with the `opts` of library/any-meets?, e.g. the :feeds
  of a show outside the library."
  ([lib ids]
   (explicit-in? lib ids {}))
  ([lib ids opts]
   (library/any-meets? lib ids {:not clean} opts)))

;; Nielsen Norman Group: say exactly what a recommendation is based on. The
;; query's words that no pick holds don't explain why the picks came up.
;; https://www.nngroup.com/articles/recommendation-guidelines/
(defn shared
  "The words that the `found` picks of library/recommend share with what
  they're like, the most telling first, by their weight in the query times
  how many picks hold them."
  [found]
  (let [weight (into {} (:terms (meta found)))]
    (->> (mapcat :terms found)
         frequencies
         (sort-by (fn [[t n]] [(- (* n (weight t 0))) t]))
         (mapv first))))

(defn picked
  "The picks in the library `lib` that library/recommend `found`, as a map
  of their listed :episodes and the :terms they share with what they're
  like, the most telling first."
  [lib found]
  {:episodes (into [] (keep #(library/listed lib (:id %))) found)
   :terms    (shared found)})

(defn recommended
  "The picks in the library `lib` like the episodes `ids`, or like those
  the listener heard last without `ids`, with the `opts` of
  library/recommend. It's nil when the library has no index."
  [lib ids opts]
  (some->> (library/recommend lib ids opts) (picked lib)))

;; The profile changes every few seconds while an episode plays, and
;; finding the picks takes tens of milliseconds, so they follow the queue
;; instead, which changes as the listener plays, adds or finishes an
;; episode
(defn library-key
  "What the picks in `state` change with, as values that are cheap to
  compare: the library's index and feeds, the queue, and whether the
  listener chose explicit picks."
  [{:keys [library] :as state}]
  [(:index library)
   (:feeds library)
   (some-> library :profile profile.queue/queue pr-str)
   (settings/explicit-picks? state)])

(defn home
  "Home's picks in `state`: like the episodes the listener heard last, and
  explicit only when the listener chose explicit picks."
  [{:keys [library] :as state}]
  (some-> library (recommended nil (opts state :home false))))

(defn after-queue
  "The picks at the end of the queue in `state`, like every episode in it,
  or nil when it's empty."
  [{:keys [library] :as state}]
  (when-let [ids (some->> library queue/view :episodes (mapv :id) not-empty)]
    (recommended library ids (opts state :queue (explicit-in? library ids)))))

(defn after-episode
  "The picks in `state` to follow the episode `id` once it ends with
  nothing queued: those the end of the queue would have with that episode
  alone in it."
  [{:keys [library] :as state} id]
  (some-> library (recommended [id] (opts state :queue (explicit-in? library [id])))))

(defn autoplayed
  "The id of the pick to play in `state` once the episode `id` ends with
  nothing else to play, if the listener chose autoplay, or nil."
  [state id]
  (when (settings/autoplay? state)
    (some-> (after-episode state id) :episodes first :id)))

;; The picks are made again at each change of the library, e.g. as an
;; episode plays, and the runs read the HTML of every episode's notes,
;; e.g. 0.5 s for 1,200 episodes, more than the rest of the picks take
(def repeated-runs
  "The runs of words that the notes of the parsed `feed` repeat, as
  feed.search/repeated-runs gives them, worked out again only for another
  feed. The last four are kept, e.g. for the start of a feed and the
  whole."
  (memo/remember-recent 4 feed.search/repeated-runs vector))

(defn outside
  "The `opts` of library/recommend with the feed `source` of a show added
  when it's outside the library, so that the picks can start from its
  episodes, with the runs of words that its notes repeat."
  [opts {:keys [key feed url] :as source}]
  (cond-> opts
    (not key) (assoc :feeds {(guid/feed-key feed url) {:feed     feed
                                                        :url      url
                                                        :repeated (repeated-runs feed)}})))

(defn like-episode
  "The picks under the episode `page` in `state`, like that episode."
  [{:keys [library] :as state} {:keys [id guid] :as page}]
  (let [source (show/source state guid)]
    (when (and library (:feed source))
      (recommended library [id]
                   (outside (opts state :episode (explicit-in? library [id] (outside {} source)))
                            source)))))

(def heard-count
  "The most episodes of a show that its picks start from."
  10)

(defn ranked
  "The `ids` with weights for the :weight-fn of library/recommend, the
  first weighing the most."
  [ids]
  (zipmap ids (range (count ids) 0 -1)))

(defn newest-in-feed
  "The ids of the newest episodes in the parsed `feed` at `url`, as many as
  heard-count, the newest first."
  [feed url]
  (->> (:rss/items feed)
       (sort-by #(or (some-> (resolve/published %) inst-ms) 0) >)
       (take heard-count)
       (mapv #(vector (guid/feed-key feed url) (guid/item-guid %)))))

(defn show-seeds
  "The episodes in `lib` that the picks of the show of the feed `source`
  start from, as a pair of their ids and weights: those the listener
  heard, the last weighing the most, or else the newest, with no weights.
  A show outside the library has only its newest."
  [lib {:keys [key feed url]}]
  (let [ids   (fn [pairs] (mapv (fn [[f item]] (library/episode-id lib f item)) pairs))
        heard (when key
                (ids (library/episodes lib {:feed-guids [key]
                                            :where      {:heard [nil nil]}
                                            :order      :recent
                                            :limit      heard-count})))]
    (cond
      (seq heard) [heard (ranked heard)]
      key         [(ids (library/episodes lib {:feed-guids [key]
                                               :order      :newest
                                               :limit      heard-count}))
                   nil]
      :else       [(newest-in-feed feed url) nil])))

(defn like-show
  "The picks under the Show page of the show `guid` in `state`, like its
  episodes that the listener heard, or else its newest. They may be
  explicit when the show or those episodes are. A show without episodes
  has no picks, and nil means they aren't known yet."
  [{:keys [library] :as state} guid]
  (let [{:keys [feed] :as source} (show/source state guid)]
    (when (and library feed)
      (let [[ids weights] (show-seeds library source)
            explicit?     (or (resolve/explicit? feed)
                              (explicit-in? library ids (outside {} source)))]
        (if (seq ids)
          (recommended library ids (cond-> (outside (opts state :show explicit?) source)
                                     weights (assoc :weight-fn weights)))
          {:episodes [] :terms []})))))

;; Finding the picks takes tens of milliseconds, more with a long history,
;; so they're found after the page paints and kept in :picks by place,
;; e.g. after a Play on Home, which moves the queue, rather than in the
;; render of the click
;; https://web.dev/articles/inp
(defn shown-places
  "The places of the picks that the page in `state` shows: [:home],
  [:queue] while the queue has episodes, and [:show guid] or [:episode id
  guid] while the listener has their picks open, where `episode` is the
  page of the episode that the page shows, if any. There are none while
  the library's search index is unloaded, which the picks need."
  [state episode]
  (let [library (:library state)
        route   (when (and library (not (library/index-unloaded? library)))
                  (get-in state [:route :name]))
        open? #(get-in state [:open (into [:picks] %)])]
    (case route
      :route/home    [[:home]]
      :route/queue   (when (seq (some-> (:library state) :profile profile.queue/queue))
                       [[:queue]])
      :route/show    (let [place [:show (get-in state [:route :path-params :guid])]]
                       (when (open? place) [place]))
      :route/episode (when-let [{:keys [id guid]} episode]
                       (let [place [:episode id guid]]
                         (when (open? place) [place])))
      nil)))

(defn place-key
  "What the picks at `place` in `state` change with, as library-key gives
  it, and the show's feed for a show's or an episode's."
  [state [kind a b :as place]]
  (case kind
    (:home :queue) (library-key state)
    :show          (conj (library-key state) a (:feed (show/source state a)))
    :episode       (conj (library-key state) (pr-str a) (:feed (show/source state b)))))

(defn found
  "The picks at `place` in `state`, found now."
  [state [kind a b :as place]]
  (case kind
    :home    (home state)
    :queue   (after-queue state)
    :show    (like-show state a)
    :episode (like-episode state {:id a :guid b})))

(defn same-key?
  "Whether the keys `a` and `b` of a place are the same, by memo/same? of
  their parts."
  [a b]
  (and (= (count a) (count b))
       (every? true? (map memo/same? a b))))

(defn kept
  "The picks at `place` in `state` as they were last found, which may be
  of an earlier key, or nil before they were found."
  [state place]
  (get-in state [:picks place]))

(defn fresh
  "The picks at `place` in `state` while they're those of its key, or nil."
  [state place]
  (let [{:keys [key picks]} (kept state place)]
    (when (same-key? key (place-key state place))
      picks)))

(defn stale?
  "Whether the picks at `place` in `state` are to be found again, for the
  key `k`."
  [state place k]
  (not (same-key? k (:key (kept state place)))))

(defn with-found
  "The `state` with the `picks` found at `place` for the key `k`. A show's
  or an episode's picks go once another's are found, so that only the
  last open section's are kept."
  [state place k picks]
  (update state :picks
          #(-> (into {} (filter (comp #{:home :queue} ffirst)) %)
               (assoc place {:key k :picks picks}))))

(defn filled
  "The `state` with the picks of the places that it shows found, with the
  page of the `episode` that it shows, if any, e.g. for a scene or a test."
  ([state]
   (filled state nil))
  ([state episode]
   (reduce (fn [s place]
             (with-found s place (place-key s place) (found s place)))
           state
           (shown-places state episode))))
