(ns dk.simongray.podcast-app.picks
  "The picks: episodes of the listener's library like what they heard last,
  what they queued, an episode or a show, as library/recommend finds them
  for each place that shows them, with the words that found them.

  The picks are unplayed, and never queued already. Unless the listener
  chooses otherwise in Settings, they're explicit only where they start
  from explicit episodes: under an explicit episode or show, and at the end
  of a queue that has one. Home's picks never are, and never start from
  one. A show or an episode outside the library has picks too, from the
  feed that its page loaded."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.feed.resolve :as resolve]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.memo :as memo]
            [dk.simongray.podcast-app.queue :as queue]
            [dk.simongray.podcast-app.settings :as settings]))

;; Nielsen Norman Group: people expect only a handful of recommendations at
;; a time. One episode of each show, so that a list has as many shows as it
;; can. Home has as many as the start of the queue beside it.
;; https://www.nngroup.com/articles/recommendation-expectations/
(def places
  "The options of library/recommend at each place that shows picks. The
  end of the queue takes episodes of the queue's shows too, since another
  episode of a show that the listener queued is a natural one to queue
  next, as Apple Podcasts' Continue Playing has it."
  {:home    {:limit 4 :max-per-show 1}
   :queue   {:limit 5 :max-per-show 1 :same-show? true}
   :episode {:limit 5 :max-per-show 1}
   :show    {:limit 5 :max-per-show 1}})

;; Feeds mark an episode explicit without saying why, e.g. for sex or for
;; swearing, so every explicit episode counts, and so does every episode of
;; an explicit show, which may mark some of them clean, as No Agenda does
(def clean
  "The criteria of the episodes that are neither explicit nor of an
  explicit show, which the picks keep to where they don't start from
  explicit ones."
  {:explicit? false :show-explicit? false})

(defn opts
  "The options of library/recommend at the `place` in the `state`: picks
  that are unplayed and not queued, and clean, started from clean
  episodes, unless the episodes that they start from aren't, `explicit?`,
  or the listener chose explicit picks."
  [state place explicit?]
  (cond-> (assoc (places place) :where {:status :unplayed :queued? false})
    (not (or explicit? (settings/explicit-picks? state)))
    (-> (update :where merge clean)
        (assoc :from clean))))

(defn explicit-in?
  "Whether any of the episodes `ids` of the library `lib` is explicit, or of
  an explicit show, with the `opts` of library/any-meets?, e.g. the :feeds
  of a show outside it, as outside gives them."
  ([lib ids]
   (explicit-in? lib ids {}))
  ([lib ids opts]
   (library/any-meets? lib ids {:not clean} opts)))

;; Nielsen Norman Group: say exactly what a recommendation is based on. The
;; words of the query that no pick holds say nothing of why they came up.
;; https://www.nngroup.com/articles/recommendation-guidelines/
(defn shared
  "The words that the picks that library/recommend `found` share with what
  they're like, the most telling first: by their weight in the query times
  how many of the picks hold them."
  [found]
  (let [weight (into {} (:terms (meta found)))]
    (->> (mapcat :terms found)
         frequencies
         (sort-by (fn [[t n]] [(- (* n (weight t 0))) t]))
         (mapv first))))

(defn picked
  "The picks of the library `lib` that library/recommend `found`, as a map
  of their :episodes, each as library/listed gives it, and the :terms that
  they share with what they're like, the most telling first."
  [lib found]
  {:episodes (into [] (keep #(library/listed lib (:id %))) found)
   :terms    (shared found)})

(defn recommended
  "The picks of the library `lib` like the episodes `ids`, or like those
  that the listener heard last without them, with the `opts` of
  library/recommend, or nil when the library keeps no index."
  [lib ids opts]
  (some->> (library/recommend lib ids opts) (picked lib)))

;; The profile changes every few seconds while an episode plays, as the
;; player reports where it is, and finding the picks takes tens of
;; milliseconds, so they're found again only as the queue changes, which
;; it does as the listener plays, adds or finishes an episode
(defn library-key
  "What the picks of the `state` change with, as values that are cheap to
  compare: the library's index and feeds, the queue, and whether the
  listener chose explicit picks."
  [{:keys [library] :as state}]
  [(:index library)
   (:feeds library)
   (some-> library :profile profile.queue/queue pr-str)
   (settings/explicit-picks? state)])

(def home
  "The picks of Home in the `state`: like the episodes that the listener
  heard last, and explicit ones only where the listener chose explicit
  picks."
  (memo/remember-last
   (fn [{:keys [library] :as state}]
     (some-> library (recommended nil (opts state :home false))))
   library-key))

(def after-queue
  "The picks at the end of the queue of the `state`: like the episode that
  plays and those up next, or nil when it's empty."
  (memo/remember-last
   (fn [{:keys [library] :as state}]
     (when-let [ids (some->> library queue/view :episodes (mapv :id) not-empty)]
       (recommended library ids (opts state :queue (explicit-in? library ids)))))
   library-key))

(defn after-episode
  "The picks of the `state` that could follow the episode `id` once it has
  ended and nothing is queued, which are those that the end of the queue
  had with that episode alone in it."
  [{:keys [library] :as state} id]
  (some-> library (recommended [id] (opts state :queue (explicit-in? library [id])))))

(defn autoplayed
  "The id of the pick that plays in the `state` after the episode `id`
  ends with nothing else to play, when the listener chose so, or nil."
  [state id]
  (when (settings/autoplay? state)
    (some-> (after-episode state id) :episodes first :id)))

(defn outside
  "The `opts` of library/recommend with the feed `source` of a show, as
  episode/source gives it, when the library doesn't keep it, so that the
  picks can start from its episodes."
  [opts {:keys [key feed url] :as source}]
  (cond-> opts
    (not key) (assoc :feeds {(guid/feed-key feed url) {:feed feed :url url}})))

(def like-episode
  "The picks under the episode `page` of the `state`, as episode/page gives
  it: like that episode."
  (memo/remember-last
   (fn [{:keys [library] :as state} {:keys [id guid] :as page}]
     (when-let [source (and library (episode/source state guid))]
       (recommended library [id]
                    (outside (opts state :episode (explicit-in? library [id] (outside {} source)))
                             source))))
   (fn [state {:keys [id guid]}]
     (conj (library-key state) (pr-str id) (:feed (episode/source state guid))))))

(def heard-count
  "The most episodes of a show that its picks start from."
  10)

(defn ranked
  "The `ids` with their weights, the first weighing the most, as the
  :weight-fn of library/recommend takes them."
  [ids]
  (zipmap ids (range (count ids) 0 -1)))

(defn show-seeds
  "The ids of the episodes of `lib` of the show of the feed `source`, as
  episode/source gives it, that its picks start from, and their weights,
  as a pair: those that the listener heard, the last weighing the most, or
  else its newest. A show outside the library has only its newest."
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
      :else       [(->> (:rss/items feed)
                        (sort-by #(or (some-> (resolve/published %) inst-ms) 0) >)
                        (take heard-count)
                        (mapv #(vector (guid/feed-key feed url) (guid/item-guid %))))
                   nil])))

(def like-show
  "The picks under the Show page of the show `guid` in the `state`: like
  its episodes that the listener heard, or else its newest, and explicit
  too where the show is, or those episodes are. A show without episodes
  has none, and nil means that they aren't known yet."
  (memo/remember-last
   (fn [{:keys [library] :as state} guid]
     (when-let [{:keys [feed] :as source} (and library (episode/source state guid))]
       (let [[ids weights] (show-seeds library source)
             explicit?     (or (resolve/explicit? feed)
                               (explicit-in? library ids (outside {} source)))]
         (if (seq ids)
           (recommended library ids (cond-> (outside (opts state :show explicit?) source)
                                      weights (assoc :weight-fn weights)))
           {:episodes [] :terms []}))))
   (fn [state guid]
     (conj (library-key state) guid (:feed (episode/source state guid))))))
