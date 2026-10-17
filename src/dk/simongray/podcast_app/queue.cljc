(ns dk.simongray.podcast-app.queue
  "The queue: what the Queue page and the player show of it, and the changes
  that the listener makes to it, each with what a screen reader hears of
  it, and the last one that can be undone.

  The queue is the profile's, which podcast-clj keeps with the current
  episode first. The page shows that episode on its own while the player
  has it, and the rest as Up next, whose positions count from 1. The state
  keeps the change that can be undone as :queue-undo, until the next
  change: a map of the :label of its button, the :restore pairs of the ids
  that it took out and their places in the queue, and what a screen reader
  hears once it's undone, the :done."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile.queue :as queue]))

(defonce ^{:doc "The last view that view made, with the feeds and the
  profile that it made it from, since every page renders it."}
  last-view
  (atom nil))

(defn view
  "The queue of the library `lib`, as library/queue gives it."
  [lib]
  (let [[feeds profile made] @last-view]
    (if (and made (identical? feeds (:feeds lib)) (identical? profile (:profile lib)))
      made
      (let [made (library/queue lib)]
        (reset! last-view [(:feeds lib) (:profile lib) made])
        made))))

(defn current
  "The episode of the queue of the `state` that the player has, or nil."
  [{:keys [library player] :as state}]
  (let [[first-one] (:episodes (view library))]
    (when (and first-one (= (:id first-one) (:id player)))
      first-one)))

(defn up-next
  "The episodes of the queue of the `state` after the one that the player
  has, or all of them while it has none of them."
  [state]
  (let [episodes (:episodes (view (:library state)))]
    (vec (if (current state) (rest episodes) episodes))))

(defn offset
  "How many places of the queue of the `state` come before Up next: one
  while the player has its first episode."
  [state]
  (if (current state) 1 0))

(defn position
  "The position of the episode `id` in Up next of the `state`, from 1, or
  nil."
  [state id]
  (some (fn [[i e]] (when (= id (:id e)) (inc i)))
        (map-indexed vector (up-next state))))

(defn titled
  "The title of the episode `id` in the queue of the `state`."
  [state id]
  (some #(when (= id (:id %)) (:title %)) (:episodes (view (:library state)))))

(defn changed
  "The `state` with its library changed by the function of the profile
  `f` and its `args`, as library/update-profile takes them, what a screen
  reader hears of it, the `announcement`, and the `undo` of it, or none."
  [state announcement undo f & args]
  (-> (apply update state :library library/update-profile f args)
      (assoc :announcement announcement :queue-undo undo)))

(defn moved
  "The `state` with the episode `id` moved to `to`, a position of Up next
  from 1, if it's one."
  [state id to]
  (let [n (count (up-next state))]
    (if (and (position state id) (<= 1 to n))
      (changed state
               (str "Moved " (titled state id) " to position " to " of " n)
               nil
               queue/move id (+ (dec to) (offset state)))
      state)))

(defn removed
  "The `state` with the episode `id` taken out of the queue, which an undo
  puts back in its place."
  [state id]
  (let [index (some (fn [[i e]] (when (= id (:id e)) i))
                    (map-indexed vector (:episodes (view (:library state)))))
        title (titled state id)]
    (if index
      (changed state
               (str "Removed " title " from the queue")
               {:label   (str "Undo removing " title)
                :restore [[id index]]
                :done    (str "Put " title " back in the queue")}
               queue/dequeue id)
      state)))

(defn cleared
  "The `state` with Up next taken out of the queue, which an undo puts back
  in order."
  [state]
  (let [ids    (mapv :id (up-next state))
        n      (count ids)
        counted (str n (if (= 1 n) " episode" " episodes"))]
    (if (seq ids)
      (changed state
               (str "Cleared " counted " from the queue")
               {:label   "Undo clearing the queue"
                :restore (mapv vector ids (range (offset state) (+ (offset state) n)))
                :done    (str "Put " counted " back in the queue")}
               queue/dequeue-all ids)
      state)))

(defn restore
  "The `profile` with the episodes of the `pairs` of an undo back in the
  queue, each at its place, at `at`."
  [profile pairs at]
  (reduce (fn [p [id index]]
            (-> p
                (queue/enqueue id at)
                (queue/move id index at)))
          profile
          pairs))

(defn undone
  "The `state` with the change that it can undo undone."
  [state]
  (if-let [{:keys [done] :as undo} (:queue-undo state)]
    (changed state done nil restore (:restore undo))
    state))

(defn added
  "The `state` with the episode `id` titled `title` at the end of the
  queue, or after the current episode when `next?`."
  [state id title next?]
  (changed state
           (if next?
             (str "Playing " title " next")
             (str "Added " title " to the queue"))
           nil
           (if next? queue/play-next queue/enqueue)
           id))

(defn queued?
  "Whether the episode `id` is in the queue of the library of the `state`."
  [state id]
  (boolean (some-> (:library state) :profile (queue/queued? id))))
