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
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.podcast-app.changes :as changes]
            [dk.simongray.podcast-app.memo :as memo]
            [dk.simongray.podcast-app.text :as text]))

(def view
  "The queue of a library from library/queue, made again only when its
  feeds or its profile change, since every page renders it."
  (memo/remember-last library/queue (juxt :feeds :profile)))

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

(defn index-of
  "The index of the episode `id` among the `episodes`, or nil."
  [episodes id]
  (first (keep-indexed #(when (= id (:id %2)) %1) episodes)))

(defn position
  "The position of the episode `id` in Up next of the `state`, from 1, or
  nil."
  [state id]
  (some-> (index-of (up-next state) id) inc))

(defn titled
  "The title of the episode `id` in the queue of the `state`."
  [state id]
  (some #(when (= id (:id %)) (:title %)) (:episodes (view (:library state)))))

(defn moved
  "The change of the `state` that moves the episode `id` to `to`, a
  position of Up next from 1, or nil when it's none."
  [state id to]
  (let [n (count (up-next state))]
    (when (and (position state id) (<= 1 to n))
      (changes/announced :queue-undo
                         (str "Moved " (titled state id) " to position " to " of " n)
                         nil
                         [:queue/move id (+ (dec to) (offset state))]))))

(defn removed
  "The change of the `state` that takes the episode `id` out of the queue,
  which an undo puts back in its place, or nil when it isn't queued."
  [state id]
  (let [index (index-of (:episodes (view (:library state))) id)
        title (titled state id)]
    (when index
      (changes/announced :queue-undo
                         (str "Removed " title " from the queue")
                         {:label   (str "Undo removing " title)
                          :restore [[id index]]
                          :done    (str "Put " title " back in the queue")}
                         [:queue/dequeue id]))))

(defn cleared
  "The change of the `state` that takes Up next out of the queue, which an
  undo puts back in order, or nil when it's empty."
  [state]
  (let [ids     (mapv :id (up-next state))
        n       (count ids)
        counted (text/counted n "episode")]
    (when (seq ids)
      (changes/announced :queue-undo
                         (str "Cleared " counted " from the queue")
                         {:label   "Undo clearing the queue"
                          :restore (mapv vector ids (range (offset state) (+ (offset state) n)))
                          :done    (str "Put " counted " back in the queue")}
                         [:queue/dequeue-all ids]))))

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
  "The change of the `state` that undoes the change that it can undo, or
  nil when there's none."
  [state]
  (when-let [{:keys [done] :as undo} (:queue-undo state)]
    (changes/announced :queue-undo done nil [:queue/restore (:restore undo)])))

(defn added
  "The change that puts the episode `id` titled `title` at the end of the
  queue, or after the current episode when `next?`."
  [id title next?]
  (changes/announced :queue-undo
                     (if next?
                       (str "Playing " title " next")
                       (str "Added " title " to the queue"))
                     nil
                     [(if next? :queue/play-next :queue/enqueue) id]))

(defn queued?
  "Whether the episode `id` is in the queue of the library of the `state`."
  [state id]
  (boolean (some-> (:library state) :profile (queue/queued? id))))

(defn place
  "Where the episode `id` is in the queue of the `state`: :current at its
  front, :queued after that, or nil."
  [state id]
  (cond
    (= id (:id (first (:episodes (some-> (:library state) view))))) :current
    (queued? state id)                                              :queued))
