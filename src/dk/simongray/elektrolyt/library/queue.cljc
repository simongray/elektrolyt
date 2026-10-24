(ns dk.simongray.elektrolyt.library.queue
  "The queue: what the Queue page and the player show of it, and the changes
  that the listener makes to it, each with what a screen reader hears of
  it, and the last one that can be undone.

  The queue is the profile's, which podcast-clj keeps with the current
  episode first. The page shows that episode on its own while the player
  has it, and the rest as Up next, whose positions count from 1. The state
  keeps the change that can be undone as :queue-undo, until the next
  change: a map of the :label of its button, the :ids that it took out, in
  order, the queue as it was :before, the ids that were :heard already,
  and what a screen reader hears once it's undone, the :done. The undo
  puts back only what's as the change left it, since another tab may have
  changed the queue since."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.elektrolyt.library.changes :as changes]
            [dk.simongray.elektrolyt.ui.memo :as memo]
            [dk.simongray.elektrolyt.ui.text :as text]))

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

(defn undo
  "The undo of the change that takes the episodes `ids` out of the queue
  of the `state`, with the `label` of its button and what a screen reader
  hears once it's undone, `done`."
  [state ids label done]
  (let [p (:profile (:library state))]
    {:label  label
     :ids    ids
     :before (queue/queue p)
     :heard  (into #{} (filter #(profile/played? p %)) ids)
     :done   done}))

(defn removed
  "The change of the `state` that takes the episode `id` out of the queue,
  which an undo puts back in its place, or nil when it isn't queued."
  [state id]
  (when (index-of (:episodes (view (:library state))) id)
    (let [title (titled state id)]
      (changes/announced :queue-undo
                         (str "Removed " title " from the queue")
                         (undo state [id] (str "Undo removing " title)
                               (str "Put " title " back in the queue"))
                         [:queue/dequeue id]))))

(defn cleared
  "The change of the `state` that takes Up next out of the queue, which an
  undo puts back in order, or nil when it's empty."
  [state]
  (let [ids     (mapv :id (up-next state))
        counted (text/counted (count ids) "episode")]
    (when (seq ids)
      (changes/announced :queue-undo
                         (str "Cleared " counted " from the queue")
                         (undo state ids "Undo clearing the queue"
                               (str "Put " counted " back in the queue"))
                         [:queue/dequeue-all ids]))))

(defn restorable
  "The episodes of the `undo` that are as its change left them in the
  `profile`, in order: out of the queue, and not heard since."
  [profile {:keys [ids heard]}]
  (filterv #(not (or (queue/queued? profile %)
                     (and (profile/played? profile %) (not (contains? heard %)))))
           ids))

(defn offered
  "The undo of the `state`, with only the :ids that it would put back, or
  nil when there are none, e.g. once another tab queued them again."
  [{:keys [library queue-undo]}]
  (when-let [ids (some-> library :profile (restorable queue-undo) not-empty)]
    (assoc queue-undo :ids ids)))

(defn- index-in
  "The index of `x` in the vector `xs`, or nil."
  [xs x]
  (first (keep-indexed #(when (= x %2) %1) xs)))

;; Selective undo, as collaborative editors have it: the undo reverses this
;; tab's change alone, applied to the queue as it is then, which another
;; tab or device may have changed since. So an episode goes back after its
;; neighbour rather than at a number. (Prakash and Knister, A Framework
;; for Undoing Actions in Collaborative Systems, ACM TOCHI 1(4), 1994.)
;; https://doi.org/10.1145/198425.198427
(defn restore
  "The `profile` with the episodes of the `undo` that are as its change
  left them back in the queue at `at`, each after the nearest of the
  episodes before it that's queued now, or else at its old place."
  [profile {:keys [before] :as undo} at]
  (reduce (fn [p id]
            (let [p     (queue/enqueue p id at)
                  now   (queue/queue p)
                  prior (some (set now) (reverse (take-while #(not= id %) before)))]
              (queue/move p id (if prior (inc (index-in now prior)) (index-in before id)) at)))
          profile
          (restorable profile undo)))

(defn undone
  "The change of the `state` that undoes the change that it can undo, as
  far as offered has it, or nil when there's nothing to put back."
  [{undo :queue-undo :as state}]
  (when-let [{:keys [ids]} (offered state)]
    (changes/announced :queue-undo
                       (if (= (count ids) (count (:ids undo)))
                         (:done undo)
                         (str "Put " (count ids) " of " (text/counted (count (:ids undo)) "episode")
                              " back in the queue"))
                       nil
                       [:queue/restore undo])))

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
