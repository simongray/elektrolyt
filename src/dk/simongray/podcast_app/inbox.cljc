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
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.podcast-app.changes :as changes]
            [dk.simongray.podcast-app.memo :as memo]
            [dk.simongray.podcast-app.text :as text]))

(def view
  "The inbox of a library from library/inbox, made again only when its
  feeds or its profile change, since every page counts the inbox in its
  navigation."
  (memo/remember-last library/inbox (juxt :feeds :profile)))

(def to-inbox
  "The settings that send the new episodes of a show to the inbox."
  (profile/new-episodes-settings :inbox))

(defn to-inbox?
  "Whether the `settings` of a show send its new episodes to the inbox."
  [settings]
  (= :inbox (profile/new-episodes (merge profile/default-settings settings))))

;; The app offers no choice of where new episodes go, so every one of them
;; arrives in the inbox, though an earlier version of the app, or another
;; app, chose the queue or nowhere for a show, or for every new show
(defn inbox-only
  "The `state` with the new episodes of each show, and of a new show, going
  to the inbox, or the `state` itself where they all do already. That
  includes the shows that the listener unsubscribed from, whose settings
  hold again when they subscribe again."
  [state]
  (let [p        (some-> (:library state) :profile)
        ks       (remove #(to-inbox? (profile/settings p %))
                         (keys (:subscriptions p)))
        defaults (:subscription-defaults (profile/preferences p))]
    (cond-> state
      (seq ks)
      (update :library library/update-profile
              (fn [p at] (reduce #(profile/configure %1 %2 to-inbox at) p ks)))

      (and p (not (to-inbox? defaults)))
      (update :library library/update-profile profile/prefer
              {:subscription-defaults (merge defaults to-inbox)}))))

(defn episodes
  "The episodes in the inbox of the `state`, the newest first."
  [state]
  (or (some-> (:library state) view :episodes) []))

(defn titled
  "The title of the episode `id` in the inbox of the `state`."
  [state id]
  (some #(when (= id (:id %)) (:title %)) (episodes state)))

(defn dismissed
  "The `state` with the episode `id` dismissed from the inbox, which an
  undo puts back."
  [state id]
  (if-let [title (titled state id)]
    (changes/changed state :inbox-undo
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
  (let [ids     (mapv :id (episodes state))
        counted (text/counted (count ids) "episode")]
    (if (seq ids)
      (changes/changed state :inbox-undo
                       (str "Dismissed " counted)
                       {:label "Undo dismissing all"
                        :ids   ids
                        :done  (str "Put " counted " back in the inbox")}
                       profile.queue/dismiss-all ids)
      state)))

(defn all-queued
  "The `state` with every episode of the inbox at the end of the queue, the
  oldest first, as a refresh queues new episodes."
  [state]
  (let [ids (mapv :id (episodes state))]
    (if (seq ids)
      (changes/changed state :inbox-undo
                       (str "Added " (text/counted (count ids) "episode") " to the queue")
                       nil
                       profile.queue/enqueue-all (rseq ids))
      state)))

(defn undone
  "The `state` with the dismissal that it can undo undone."
  [state]
  (if-let [{:keys [done ids]} (:inbox-undo state)]
    (changes/changed state :inbox-undo done nil profile.queue/undismiss-all ids)
    state))
