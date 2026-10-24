(ns dk.simongray.elektrolyt.library.inbox
  "The inbox and the listener's changes to it, each announced to a screen
  reader.

  A dismissal can be undone until the next change of the inbox. The state
  keeps the undo as :inbox-undo, a map of:

  - :label, the text of its button
  - :ids, the episodes dismissed
  - :done, the announcement once it's undone

  An undo puts back only the episodes that are still as the dismissal left
  them, since another tab may have handled them in the meantime."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.elektrolyt.library.changes :as changes]
            [dk.simongray.elektrolyt.ui.memo :as memo]
            [dk.simongray.elektrolyt.ui.text :as text]))

(def view
  "The inbox of a library, made again only when its feeds or profile
  change, since every page counts it in the navigation."
  (memo/remember-last library/inbox (juxt :feeds :profile)))

(defn episodes
  "The episodes in the inbox of `state`, the newest first."
  [state]
  (or (some-> (:library state) view :episodes) []))

(defn titled
  "The title of the episode `id` in the inbox of `state`."
  [state id]
  (some #(when (= id (:id %)) (:title %)) (episodes state)))

(defn dismissed
  "The change of `state` that dismisses the episode `id` from the inbox,
  or nil when it isn't there. An undo puts it back."
  [state id]
  (when-let [title (titled state id)]
    (changes/announced :inbox-undo
                       (str "Dismissed " title)
                       {:label (str "Undo dismissing " title)
                        :ids   [id]
                        :done  (str "Put " title " back in the inbox")}
                       [:inbox/dismiss id])))

(defn all-dismissed
  "The change of `state` that dismisses every episode in the inbox, or nil
  when it's empty. An undo puts them back."
  [state]
  (let [ids     (mapv :id (episodes state))
        counted (text/counted (count ids) "episode")]
    (when (seq ids)
      (changes/announced :inbox-undo
                         (str "Dismissed " counted)
                         {:label "Undo dismissing all"
                          :ids   ids
                          :done  (str "Put " counted " back in the inbox")}
                         [:inbox/dismiss-all ids]))))

(defn all-queued
  "The change of `state` that puts every episode in the inbox at the end of
  the queue, the oldest first as a refresh queues them, or nil when the
  inbox is empty."
  [state]
  (let [ids (mapv :id (episodes state))]
    (when (seq ids)
      (changes/announced :inbox-undo
                         (str "Added " (text/counted (count ids) "episode") " to the queue")
                         nil
                         [:inbox/enqueue-all (vec (rseq ids))]))))

(defn restorable
  "The ids of the dismissal `undo` that are still as it left them in
  `profile`, in order, without those that are back in the inbox, queued or
  heard, since they were handled elsewhere."
  [profile {:keys [ids]}]
  (filterv #(not (or (queue/in-inbox? profile %)
                     (queue/queued? profile %)
                     (profile/played? profile %)))
           ids))

(defn offered
  "The undo in `state` with only the :ids that it would put back, or nil
  when there are none, e.g. once another tab queued them."
  [{:keys [library inbox-undo]}]
  (when-let [ids (some-> library :profile (restorable inbox-undo) not-empty)]
    (assoc inbox-undo :ids ids)))

(defn restore
  "The `profile` with the episodes of the dismissal `undo` back in the
  inbox at `at`, those that are still as it left them."
  [profile undo at]
  (queue/undismiss-all profile (restorable profile undo) at))

(defn undone
  "The change of `state` that puts back what its undo offers, or nil when
  there's nothing to put back."
  [{undo :inbox-undo :as state}]
  (when-let [{:keys [ids]} (offered state)]
    (changes/announced :inbox-undo
                       (if (= (count ids) (count (:ids undo)))
                         (:done undo)
                         (str "Put " (count ids) " of " (text/counted (count (:ids undo)) "episode")
                              " back in the inbox"))
                       nil
                       [:inbox/restore undo])))
