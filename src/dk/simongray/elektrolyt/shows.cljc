(ns dk.simongray.elektrolyt.shows
  "The Library page: the shows that the listener subscribes to, in the order
  and the layout they chose, and what a refresh of their feeds found.

  The state keeps the listener's choices for the page as :library-view, a
  map of its :sort, one of sorts, and its :layout, one of layouts, which
  this browser keeps, and the refresh under way or done as
  :refresh-status: a map of whether it's :running?, how many feeds are
  :done of the :total, and its :result, what library/refreshed gave beside
  the library, e.g. the counts and the episodes :added."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.memo :as memo]
            [dk.simongray.elektrolyt.text :as text]))

(def sorts
  "The orders of the shows that the listener can choose, by key, the first
  the default: the shows with the newest episodes first, as Apple Podcasts'
  Recently Updated has them, or by title."
  (array-map :newest "Newest episode"
             :title  "Title"))

(def layouts
  "The layouts of the shows that the listener can choose, by key, the first
  the default: a list on a phone and a grid of their artwork wider than
  that, or either at every width."
  (array-map :auto "Automatic"
             :grid "Grid"
             :list "List"))

(defn choice
  "The listener's choice of `k`, :sort or :layout, in the `state`, or its
  default."
  [state k]
  (let [options (case k :sort sorts :layout layouts)
        chosen  (get-in state [:library-view k])]
    (if (contains? options chosen) chosen (key (first options)))))

(def all-shows
  "The shows of a library from library/shows, made again only when its
  feeds or its profile change, since counting the episodes of every show
  takes a while."
  (memo/remember-last library/shows (juxt :feeds :profile)))

(defn sorted
  "The `shows` in the order `sort`, one of sorts."
  [shows sort]
  (case sort
    :title  (sort-by (comp str/lower-case str :title) shows)
    ;; a show whose feed has no dates yet comes last
    :newest (sort-by #(if-let [d (:newest %)] (- (inst-ms d)) ##Inf) shows)
    shows))

(defn counts-text
  "What the `counts` of a show say, e.g. \"12 unplayed, 3 new\", where the
  new ones are those in the inbox, or nil when there are none of either."
  [{:keys [unplayed inbox] :as counts}]
  (let [parts (cond-> []
                (pos? (or unplayed 0)) (conj (str unplayed " unplayed"))
                (pos? (or inbox 0))    (conj (str inbox " new")))]
    (when (seq parts)
      (str/join ", " parts))))

(defn updated-at
  "When the feeds of the library `lib` were last fetched, the latest of
  them, or nil."
  [lib]
  (some->> (seq (keep :fetched-at (vals (:feeds lib))))
           (apply max-key inst-ms)))

(defn summary
  "What a screen reader hears once a refresh is done, by its `result`: the
  new episodes and the shows that couldn't be checked. When the listener
  didn't ask for it, `asked?` false, nothing unless there's something new."
  [{:keys [counts added] :as result} asked?]
  (let [n      (count added)
        failed (:failed counts 0)
        found  (if (zero? n) "No new episodes" (text/counted n "new episode"))
        failed (when (pos? failed)
                 (str ", but " (text/counted failed "show") " couldn't be checked"))]
    (when (or asked? (pos? n))
      (str found failed))))
