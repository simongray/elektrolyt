(ns dk.simongray.elektrolyt.library.shows
  "The shows on the Library page, in the order and layout that the listener
  chose, and what a refresh of their feeds found."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.ui.memo :as memo]
            [dk.simongray.elektrolyt.ui.text :as text]))

(def sorts
  "The orders that the listener can choose for the shows, by key, with the
  default first. Newest episode first follows Apple Podcasts' Recently
  Updated."
  (array-map :newest "Newest episode"
             :title  "Title"))

(def layouts
  "The layouts that the listener can choose for the shows, by key, with the
  default first: a list on a phone and a grid on wider screens, or either
  one at every width."
  (array-map :auto "Automatic"
             :grid "Grid"
             :list "List"))

(defn choice
  "The listener's choice of `k`, :sort or :layout, in `state`, or else its
  default."
  [state k]
  (let [options (case k :sort sorts :layout layouts)
        chosen  (get-in state [:library-view k])]
    (if (contains? options chosen) chosen (key (first options)))))

(def all-shows
  "The shows in a library, worked out again only when its feeds or profile
  change, since counting every show's episodes takes a while."
  (memo/remember-last library/shows (juxt :feeds :profile)))

(defn sorted
  "The `shows` in the order `sort`."
  [shows sort]
  (case sort
    :title  (sort-by (comp str/lower-case str :title) shows)
    ;; a show whose feed has no dates yet comes last
    :newest (sort-by #(if-let [d (:newest %)] (- (inst-ms d)) ##Inf) shows)
    shows))

(defn counts-text
  "The text of a show's `counts`, e.g. \"12 unplayed, 3 new\", where new
  means in the inbox, or nil when there are none of either."
  [{:keys [unplayed inbox] :as counts}]
  (let [parts (cond-> []
                (pos? (or unplayed 0)) (conj (str unplayed " unplayed"))
                (pos? (or inbox 0))    (conj (str inbox " new")))]
    (when (seq parts)
      (str/join ", " parts))))

(defn updated-at
  "The last time that any feed in the library `lib` was fetched, or nil."
  [lib]
  (some->> (seq (keep :fetched-at (vals (:feeds lib))))
           (apply max-key inst-ms)))

(defn summary
  "The announcement of a refresh's `result`: how many episodes are new and
  how many shows couldn't be checked. Unless the listener `asked?` for the
  refresh, it's nil when nothing is new."
  [{:keys [counts added] :as result} asked?]
  (let [n      (count added)
        failed (:failed counts 0)
        found  (if (zero? n) "No new episodes" (text/counted n "new episode"))
        failed (when (pos? failed)
                 (str ", but " (text/counted failed "show") " couldn't be checked"))]
    (when (or asked? (pos? n))
      (str found failed))))
