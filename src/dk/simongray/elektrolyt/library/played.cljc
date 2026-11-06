(ns dk.simongray.elektrolyt.library.played
  "Marking episodes played or unplayed, each change announced to a screen
  reader.

  Marking an episode played takes it out of the queue too, unless the player
  shows it. Marking several at once can be undone until the next change
  here. The state keeps the undo as :played-undo, a map of:

  - :episode, the episode whose page offers it
  - :label, what its button undoes, for a screen reader
  - :ids, the episodes marked
  - :queue, the undo of taking them out of the queue, as library.queue has it
  - :inbox, those that were in the inbox

  An undo takes the mark off only the episodes that are still played, since
  another tab may have changed them in the meantime."
  (:require [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.elektrolyt.library.changes :as changes]
            [dk.simongray.elektrolyt.library.inbox :as inbox]
            [dk.simongray.elektrolyt.library.queue :as queue]
            [dk.simongray.elektrolyt.ui.text :as text]))

;; Apple's Mark as Played takes an episode off Up Next, and AntennaPod's
;; listeners ask for the same, since finishing an episode does it too.
;; https://ios.gadgethacks.com/how-to/mark-episodes-as-played-apple-podcasts-remove-them-from-your-up-next-queue-0199670/
;; https://forum.antennapod.org/t/toggling-played-state-optionally-resets-progress-and-removes-from-queue/2982
(defn leaving-queue
  "Those of the episodes `ids` that marking them played takes out of the
  queue in `state`: all that are queued but the one the player shows."
  [{:keys [library] :as state} ids]
  (let [shown (:id (queue/current state))]
    (filterv #(and (profile.queue/queued? (:profile library) %) (not= shown %)) ids)))

(defn mark
  "The `profile` with the episodes `ids` played at `at`, and those of them
  in `leaving` out of the queue."
  [profile ids leaving at]
  (-> profile
      (profile/mark-all-played ids at)
      (profile.queue/dequeue-all leaving at)))

(defn marked
  "The change of `state` that marks the episode `id` titled `title` played."
  [state id title]
  (changes/announced :played-undo
                     (str "Marked " title " played")
                     nil
                     [:played/mark [id] (leaving-queue state [id])]))

(defn unmarked
  "The change that marks the episode `id` titled `title` unplayed, which
  starts it over."
  [id title]
  (changes/announced :played-undo
                     (str "Marked " title " unplayed")
                     nil
                     [:played/unmark [id]]))

;; AntennaPod's listeners have asked for this since 2021, to catch up with a
;; show that they heard elsewhere.
;; https://forum.antennapod.org/t/the-ability-to-mark-episodes-older-than-selected-as-played/1449
(defn marked-up-to
  "The change of `state` that marks the episode `id` and those `earlier`
  played, which an undo takes back."
  [{:keys [library] :as state} id earlier]
  (let [ids     (conj (vec earlier) id)
        counted (text/counted (count ids) "episode")
        leaving (leaving-queue state ids)]
    (changes/announced :played-undo
                       (str "Marked " counted " played")
                       {:episode id
                        :label   (str "marking " counted " played")
                        :ids     ids
                        :queue   (queue/undo state leaving nil nil)
                        :inbox   (filterv #(profile.queue/in-inbox? (:profile library) %) ids)}
                       [:played/mark ids leaving])))

(defn restorable
  "The ids of the `undo` that are still played in `profile`, in order."
  [profile {:keys [ids]}]
  (filterv #(profile/played? profile %) ids))

(defn offered
  "The undo in `state` on the page of the episode `id`, with only the :ids
  that it would take the mark off, or nil when it has none."
  [{:keys [library played-undo]} id]
  (when (= id (:episode played-undo))
    (when-let [ids (not-empty (restorable (:profile library) played-undo))]
      (assoc played-undo :ids ids))))

(defn restore
  "The `profile` with the marks of the `undo` taken off at `at`, and its
  episodes back in the inbox and the queue."
  [profile {:keys [inbox] queue-undo :queue :as undo} at]
  (-> profile
      (profile/unmark-all-played (restorable profile undo) at)
      (inbox/restore {:ids inbox} at)
      (queue/restore queue-undo at)))

(defn undone
  "The change of `state` that takes off the marks that its undo offers, or
  nil when there are none."
  [{:keys [played-undo] :as state}]
  (when-let [{:keys [ids] :as kept} (offered state (:episode played-undo))]
    (changes/announced :played-undo
                       (str "Marked " (text/counted (count ids) "episode") " unplayed")
                       nil
                       [:played/restore kept])))
