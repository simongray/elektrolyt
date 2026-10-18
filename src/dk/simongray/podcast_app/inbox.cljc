(ns dk.simongray.podcast-app.inbox
  "The inbox: what the Inbox page and the main navigation show of it, and
  the changes that the listener makes to it, each with what a screen
  reader hears of it, and the last dismissal, which can be undone.

  The inbox is the profile's, as library/inbox lists it, the newest
  episode first. The state keeps the dismissal that can be undone as
  :inbox-undo, until the next change of the inbox: a map of the :label of
  its button, the :ids that it dismissed, and what a screen reader hears
  once it's undone, the :done."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile.queue :as profile.queue]))

(defonce ^{:doc "The last view that view made, with the feeds and the
  profile that it made it from, since every page counts the inbox in its
  navigation."}
  last-view
  (atom nil))

(defn view
  "The inbox of the library `lib`, as library/inbox gives it."
  [lib]
  (let [[feeds profile made] @last-view]
    (if (and made (identical? feeds (:feeds lib)) (identical? profile (:profile lib)))
      made
      (let [made (library/inbox lib)]
        (reset! last-view [(:feeds lib) (:profile lib) made])
        made))))

(defn episodes
  "The episodes in the inbox of the `state`, the newest first."
  [state]
  (or (some-> (:library state) view :episodes) []))

(defn counted
  "The count `n` of episodes in words, e.g. \"3 episodes\"."
  [n]
  (str n (if (= 1 n) " episode" " episodes")))

(defn titled
  "The title of the episode `id` in the inbox of the `state`."
  [state id]
  (some #(when (= id (:id %)) (:title %)) (episodes state)))

(defn changed
  "The `state` with its library changed by the function of the profile
  `f` and its `args`, as library/update-profile takes them, what a screen
  reader hears of it, the `announcement`, and the `undo` of it, or none."
  [state announcement undo f & args]
  (-> (apply update state :library library/update-profile f args)
      (assoc :announcement announcement :inbox-undo undo)))

(defn dismissed
  "The `state` with the episode `id` dismissed from the inbox, which an
  undo puts back."
  [state id]
  (if-let [title (titled state id)]
    (changed state
             (str "Dismissed " title)
             {:label (str "Undo dismissing " title)
              :ids   [id]
              :done  (str "Put " title " back in the inbox")}
             profile.queue/dismiss id)
    state))

(defn all-dismissed
  "The `state` with every episode of the inbox dismissed, which an undo
  puts back."
  [state]
  (let [ids (mapv :id (episodes state))]
    (if (seq ids)
      (changed state
               (str "Dismissed " (counted (count ids)))
               {:label "Undo dismissing all"
                :ids   ids
                :done  (str "Put " (counted (count ids)) " back in the inbox")}
               profile.queue/dismiss-all ids)
      state)))

(defn all-queued
  "The `state` with every episode of the inbox at the end of the queue, the
  oldest first, as a refresh queues new episodes."
  [state]
  (let [ids (mapv :id (episodes state))]
    (if (seq ids)
      (changed state
               (str "Added " (counted (count ids)) " to the queue")
               nil
               profile.queue/enqueue-all (rseq ids))
      state)))

(defn undone
  "The `state` with the dismissal that it can undo undone."
  [state]
  (if-let [{:keys [done ids]} (:inbox-undo state)]
    (changed state done nil profile.queue/undismiss-all ids)
    state))
