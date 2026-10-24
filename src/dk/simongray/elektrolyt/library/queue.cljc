(ns dk.simongray.elektrolyt.library.queue
  "The queue and the listener's changes to it, each announced to a screen
  reader.

  Taking episodes out of the queue can be undone until the next change of
  the queue. The state keeps the undo as :queue-undo, a map of:

  - :label, the text of its button
  - :ids, the episodes taken out, in order
  - :before, the queue as it was
  - :heard, those of the ids that were heard already
  - :done, the announcement once it's undone

  An undo puts back only the episodes that are still as the change left
  them, since another tab may have changed the queue in the meantime."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.elektrolyt.library.changes :as changes]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.ui.memo :as memo]
            [dk.simongray.elektrolyt.ui.text :as text]))

(def view
  "The queue of a library, the current episode first, made again only when
  its feeds or profile change, since every page renders it."
  (memo/remember-last library/queue (juxt :feeds :profile)))

(defn current
  "The first episode of the queue in `state` while the player shows it,
  here or in another tab, or nil."
  [{:keys [library player] :as state}]
  (let [[first-one] (:episodes (view library))]
    (when (and first-one (= (:id first-one) (player/shown-id player)))
      first-one)))

(defn up-next
  "The episodes up next in `state`: the queue after its first episode while
  the player shows that one, or else the whole queue."
  [state]
  (let [episodes (:episodes (view (:library state)))]
    (vec (if (current state) (rest episodes) episodes))))

(defn index-of
  "The index of the episode `id` among the `episodes`, or nil."
  [episodes id]
  (first (keep-indexed #(when (= id (:id %2)) %1) episodes)))

(defn- index-in
  "The index of `x` in the vector `xs`, or nil."
  [xs x]
  (first (keep-indexed #(when (= x %2) %1) xs)))

(defn position
  "The position of the episode `id` among the episodes up next in `state`,
  counting from 1, or nil."
  [state id]
  (some-> (index-of (up-next state) id) inc))

(defn titled
  "The title of the queued episode `id` in `state`."
  [state id]
  (some #(when (= id (:id %)) (:title %)) (:episodes (view (:library state)))))

(defn moved
  "The change of `state` that moves the episode `id` to the position `to`
  of Up next, counting from 1, or nil when either isn't in Up next."
  [state id to]
  (let [episodes (up-next state)
        n        (count episodes)]
    (when (and (position state id) (<= 1 to n))
      (changes/announced :queue-undo
                         (str "Moved " (titled state id) " to position " to " of " n)
                         nil
                         ;; the place of the episode there among every queued
                         ;; id, which can include episodes whose feed has gone
                         [:queue/move id (index-in (queue/queue (:profile (:library state)))
                                                   (:id (get episodes (dec to))))]))))

(defn undo
  "The undo of taking the episodes `ids` out of the queue in `state`, with
  the `label` of its button and the announcement `done` once it's undone."
  [state ids label done]
  (let [p (:profile (:library state))]
    {:label  label
     :ids    ids
     :before (queue/queue p)
     :heard  (into #{} (filter #(profile/played? p %)) ids)
     :done   done}))

(defn removed
  "The change of `state` that takes the episode `id` out of the queue, or
  nil when it isn't queued. An undo puts it back in its place."
  [state id]
  (when (index-of (:episodes (view (:library state))) id)
    (let [title (titled state id)]
      (changes/announced :queue-undo
                         (str "Removed " title " from the queue")
                         (undo state [id] (str "Undo removing " title)
                               (str "Put " title " back in the queue"))
                         [:queue/dequeue id]))))

(defn cleared
  "The change of `state` that takes Up next out of the queue, or nil when
  it's empty. An undo puts the episodes back in order."
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
  "The ids of `undo` that are still as its change left them in `profile`,
  in order: out of the queue, and not heard since."
  [profile {:keys [ids heard]}]
  (filterv #(not (or (queue/queued? profile %)
                     (and (profile/played? profile %) (not (contains? heard %)))))
           ids))

(defn offered
  "The undo in `state` with only the :ids that it would put back, or nil
  when there are none, e.g. once another tab queued them again."
  [{:keys [library queue-undo]}]
  (when-let [ids (some-> library :profile (restorable queue-undo) not-empty)]
    (assoc queue-undo :ids ids)))

;; Selective undo, as in collaborative editors: the undo reverses this
;; tab's change alone, on the queue as it is by then, which another tab or
;; device may have changed. So an episode goes back after its neighbour
;; rather than at a number. (Prakash and Knister, A Framework for Undoing
;; Actions in Collaborative Systems, ACM TOCHI 1(4), 1994.)
;; https://doi.org/10.1145/198425.198427
(defn restore
  "The `profile` with the episodes of `undo` back in the queue at `at`,
  those that are still as its change left them. Each goes after the
  nearest episode before it that's still queued, or else at its old place."
  [profile {:keys [before] :as undo} at]
  (reduce (fn [p id]
            (let [p     (queue/enqueue p id at)
                  now   (queue/queue p)
                  prior (some (set now) (reverse (take-while #(not= id %) before)))]
              (queue/move p id (if prior (inc (index-in now prior)) (index-in before id)) at)))
          profile
          (restorable profile undo)))

(defn undone
  "The change of `state` that puts back what its undo offers, or nil when
  there's nothing to put back."
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
  "Whether the episode `id` is in the queue in `state`."
  [state id]
  (boolean (some-> (:library state) :profile (queue/queued? id))))

(defn place
  "Where the episode `id` is in the queue in `state`: :current at the
  front while the player shows it, :queued in Up next, or nil."
  [state id]
  (cond
    (= id (:id (current state))) :current
    (queued? state id)           :queued))
