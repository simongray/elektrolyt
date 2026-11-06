(ns dk.simongray.elektrolyt.library.played-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.mind-meld :as crdt]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.elektrolyt.library.operations :as operations]
            [dk.simongray.elektrolyt.library.played :as played]))

(def url "https://example.org/feed.xml")

(def feed
  {:rss/title "A Serial"
   :rss/items (vec (for [i [1 2 3 4]]
                     {:rss/title       (str "Episode " i)
                      :rss/guid        {:content (str "ep" i)}
                      :rss/enclosure   {:url (str "https://example.org/" i ".mp3") :type "audio/mpeg"}
                      :itunes/duration 600}))})

(def lib
  (library/subscribe (library/make "test") url feed))

(def k
  (first (keys (:feeds lib))))

(defn id [i] [k (str "ep" i)])

(defn queued
  "The state with the episodes `is` in the queue, in order."
  [& is]
  {:library (library/update-profile lib profile.queue/enqueue-all (mapv id is))})

(defn changed
  "The `state` with the `change` made."
  [state change]
  (operations/changed state change))

(defn profile-of [state]
  (:profile (:library state)))

(defn played? [state i]
  (profile/played? (profile-of state) (id i)))

(defn in-queue [state]
  (profile.queue/queue (profile-of state)))

(deftest marking-one
  (let [state  (queued 1 2)
        marked (changed state (played/marked state (id 2) "Episode 2"))]
    (is (played? marked 2))
    (is (= [(id 1)] (in-queue marked)) "out of the queue")
    (is (= "Marked Episode 2 played" (:announcement marked)) "as a screen reader hears")
    (is (nil? (:played-undo marked)) "with no undo, since its button turns into Mark unplayed"))
  (let [state  (assoc (queued 1 2) :player {:id (id 1)})
        marked (changed state (played/marked state (id 1) "Episode 1"))]
    (is (played? marked 1))
    (is (= [(id 1) (id 2)] (in-queue marked)) "but the one that the player shows stays in it"))
  (let [begun    {:library (library/update-profile lib profile/progress (id 1) 100 600)}
        marked   (changed begun (played/marked begun (id 1) "Episode 1"))
        unmarked (changed marked (played/unmarked (id 1) "Episode 1"))]
    (is (not (played? unmarked 1)))
    (is (= 0 (:position (profile/episode-entry (profile-of unmarked) (id 1))))
        "and marking it unplayed starts it over")
    (is (= "Marked Episode 1 unplayed" (:announcement unmarked)))))

(deftest marking-up-to-an-episode-and-its-undo
  (let [state  (-> (queued 1 3 4)
                   (update :library library/update-profile profile/progress (id 2) 100 600)
                   (update :library library/update-profile
                           #(crdt/write %1 [:inbox (id 2)] (constantly {:removed? false}) %2)))
        marked (changed state (played/marked-up-to state (id 3) [(id 1) (id 2)]))]
    (is (every? #(played? marked %) [1 2 3]))
    (is (not (played? marked 4)))
    (is (= [(id 4)] (in-queue marked)) "which leave the queue")
    (is (not (profile.queue/in-inbox? (profile-of marked) (id 2))) "and the inbox")
    (is (= "Marked 3 episodes played" (:announcement marked)))
    (is (= "Undo marking 3 episodes played" (:label (played/offered marked (id 3)))))
    (is (nil? (played/offered marked (id 2))) "offered on the page of the episode alone")
    (let [undone (changed marked (played/undone marked))]
      (is (not-any? #(played? undone %) [1 2 3]) "whose undo takes the marks off")
      (is (= [(id 1) (id 3) (id 4)] (in-queue undone)) "puts them back in the queue, in their places")
      (is (profile.queue/in-inbox? (profile-of undone) (id 2)) "and in the inbox")
      (is (= 100 (:position (profile/episode-entry (profile-of undone) (id 2)))) "and keeps where it was")
      (is (= "Marked 3 episodes unplayed" (:announcement undone)))
      (is (nil? (played/offered undone (id 3))) "and is gone"))
    (let [elsewhere (update marked :library library/update-profile profile/mark-unplayed (id 1))
          undone    (changed elsewhere (played/undone elsewhere))]
      (is (= [(id 2) (id 3)] (:ids (played/offered elsewhere (id 3))))
          "only for those still played, should another tab have changed one")
      (is (= "Marked 2 episodes unplayed" (:announcement undone))))
    (let [next-mark (changed marked (played/marked marked (id 4) "Episode 4"))]
      (is (nil? (played/offered next-mark (id 3))) "and only until the next change of the marks"))))
