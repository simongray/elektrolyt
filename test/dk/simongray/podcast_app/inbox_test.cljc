(ns dk.simongray.podcast-app.inbox-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.mind-meld :as crdt]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.podcast-app.inbox :as inbox]))

(def url "https://example.org/feed.xml")

(def dates
  {1 #inst "2026-10-01T06:00:00Z"
   2 #inst "2026-10-02T06:00:00Z"
   3 #inst "2026-10-03T06:00:00Z"})

(def feed
  {:rss/title "A Show"
   :rss/items (vec (for [i [3 2 1]]
                     {:rss/title       (str "Episode " i)
                      :rss/guid        {:content (str "ep" i)}
                      :rss/pub-date    (dates i)
                      :rss/enclosure   {:url (str "https://example.org/" i ".mp3") :type "audio/mpeg"}
                      :itunes/duration 600}))})

(def lib
  (library/subscribe (library/make "test") url feed))

(def k
  (first (keys (:feeds lib))))

(defn id [i] [k (str "ep" i)])

(defn arrived
  "The library `lib` with the episode `i` in its inbox, as a refresh puts
  it there."
  [lib i]
  (library/update-profile lib #(crdt/write %1 [:inbox (id i)] (constantly {:removed? false}) %2)))

(defn state
  "The state with the episodes `is` in the inbox, in the order they
  arrived."
  [& is]
  {:library (reduce arrived lib is)})

(defn titles [state]
  (map :title (inbox/episodes state)))

(deftest the-inbox
  (is (= ["Episode 3" "Episode 2" "Episode 1"] (titles (state 1 3 2))) "the newest first")
  (is (= [] (inbox/episodes {})) "and none before the library is restored"))

(deftest a-dismissal-and-its-undo
  (let [dismissed (inbox/dismissed (state 1 2 3) (id 2))]
    (is (= ["Episode 3" "Episode 1"] (titles dismissed)))
    (is (= "Dismissed Episode 2" (:announcement dismissed)))
    (is (= "Undo dismissing Episode 2" (get-in dismissed [:inbox-undo :label])))
    (let [undone (inbox/undone dismissed)]
      (is (= ["Episode 3" "Episode 2" "Episode 1"] (titles undone)) "back in its place")
      (is (= "Put Episode 2 back in the inbox" (:announcement undone)))
      (is (nil? (:inbox-undo undone))))))

(deftest all-at-once
  (let [dismissed (inbox/all-dismissed (state 1 2 3))]
    (is (= [] (titles dismissed)))
    (is (= "Dismissed 3 episodes" (:announcement dismissed)))
    (is (= ["Episode 3" "Episode 2" "Episode 1"] (titles (inbox/undone dismissed)))))
  (let [queued (inbox/all-queued (state 1 2 3))]
    (is (= [] (titles queued)))
    (is (= [(id 1) (id 2) (id 3)] (profile.queue/queue (:profile (:library queued))))
        "the oldest first, as a refresh queues them")
    (is (= "Added 3 episodes to the queue" (:announcement queued)))))

(defn new-episodes
  "Where the new episodes of the show of the `state` go, and those of a
  new show."
  [state]
  (let [p (:profile (:library state))]
    [(profile/new-episodes (profile/settings p k))
     (profile/new-episodes (merge profile/default-settings
                                  (:subscription-defaults (profile/preferences p))))]))

(deftest every-new-episode-arrives-in-the-inbox
  (let [state {:library lib}]
    (is (identical? state (inbox/inbox-only state)) "as they do at first")
    (is (identical? {} (inbox/inbox-only {})) "and before the library is restored"))
  (let [elsewhere {:library (-> lib
                                (library/update-profile profile/configure k
                                                        (profile/new-episodes-settings :queue))
                                (library/update-profile profile/prefer
                                                        {:subscription-defaults
                                                         (profile/new-episodes-settings :none)}))}]
    (is (= [:queue :none] (new-episodes elsewhere)))
    (is (= [:inbox :inbox] (new-episodes (inbox/inbox-only elsewhere)))
        "though an earlier version of the app sent them elsewhere")))
