(ns dk.simongray.elektrolyt.library.queue-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.elektrolyt.library.operations :as operations]
            [dk.simongray.elektrolyt.library.queue :as queue]))

(def url "https://example.org/feed.xml")

(def feed
  {:rss/title "A Show"
   :rss/items (vec (for [i (range 1 5)]
                     {:rss/title       (str "Episode " i)
                      :rss/guid        {:content (str "ep" i)}
                      :rss/enclosure   {:url (str "https://example.org/" i ".mp3") :type "audio/mpeg"}
                      :itunes/duration 600}))})

(def lib
  (library/subscribe (library/make "test") url feed))

(def k
  (first (keys (:feeds lib))))

(defn id [i] [k (str "ep" i)])

(defn state
  "The state with the episodes `is` queued in order, and the first of them
  in the player when `playing?`."
  [playing? & is]
  (cond-> {:library (reduce #(library/update-profile %1 profile.queue/enqueue (id %2)) lib is)}
    playing? (assoc :player {:id (id (first is))})))

(defn titles [state]
  (map :title (queue/up-next state)))

(defn changed
  "The `state` with the change that `f` gives for it and the `args` made."
  [f state & args]
  (operations/changed state (apply f state args)))

(defn added [state id title next?]
  (operations/changed state (queue/added id title next?)))

(deftest the-episode-that-plays-and-up-next
  (is (= "Episode 1" (:title (queue/current (state true 1 2 3)))))
  (is (= ["Episode 2" "Episode 3"] (titles (state true 1 2 3))))
  (is (= ["Episode 1" "Episode 2" "Episode 3"] (titles (state false 1 2 3)))
      "all of them while the player has none of them")
  (is (= 2 (queue/position (state true 1 2 3) (id 3)))))

(deftest moves-in-up-next
  (let [moved (changed queue/moved (state true 1 2 3 4) (id 4) 1)]
    (is (= ["Episode 4" "Episode 2" "Episode 3"] (titles moved)) "after the episode that plays")
    (is (= "Moved Episode 4 to position 1 of 3" (:announcement moved))))
  (is (= ["Episode 2" "Episode 3"] (titles (changed queue/moved (state true 1 2 3) (id 2) 3)))
      "never past the end"))

(deftest a-removal-and-its-undo
  (let [removed (changed queue/removed (state true 1 2 3) (id 2))]
    (is (= ["Episode 3"] (titles removed)))
    (is (= "Removed Episode 2 from the queue" (:announcement removed)))
    (is (= "Undo removing Episode 2" (get-in removed [:queue-undo :label])))
    (let [undone (changed queue/undone removed)]
      (is (= ["Episode 2" "Episode 3"] (titles undone)) "back in its place")
      (is (= "Put Episode 2 back in the queue" (:announcement undone)))
      (is (nil? (:queue-undo undone))))))

(deftest clearing-and-its-undo
  (let [cleared (changed queue/cleared (state true 1 2 3))]
    (is (= [] (titles cleared)))
    (is (= "Episode 1" (:title (queue/current cleared))) "the episode that plays stays")
    (is (= "Cleared 2 episodes from the queue" (:announcement cleared)))
    (is (= ["Episode 2" "Episode 3"] (titles (changed queue/undone cleared))) "back in order")))

(defn elsewhere
  "The `state` with the profile changed by `f` and the `args`, as another
  tab changes it."
  [state f & args]
  (update state :library #(apply library/update-profile % f args)))

(deftest an-undo-after-another-tab-changed-the-queue
  (let [cleared (changed queue/cleared (state true 1 2 3 4))]
    (testing "puts back only what's as it left it"
      (let [requeued (elsewhere cleared profile.queue/enqueue (id 3))
            heard    (elsewhere cleared profile/mark-played (id 2))]
        (is (= [(id 2) (id 4)] (:ids (queue/offered requeued))))
        (is (= ["Episode 2" "Episode 3" "Episode 4"] (titles (changed queue/undone requeued)))
            "each after its neighbour, where the other one stays")
        (is (= "Put 2 of 3 episodes back in the queue" (:announcement (changed queue/undone requeued))))
        (is (= ["Episode 3" "Episode 4"] (titles (changed queue/undone heard)))
            "and not an episode heard since")))
    (is (nil? (queue/offered (reduce #(elsewhere %1 profile.queue/enqueue (id %2)) cleared [2 3 4]))))
    (is (nil? (queue/undone (reduce #(elsewhere %1 profile.queue/enqueue (id %2)) cleared [2 3 4])))
        "and nothing when there's nothing to put back"))
  (let [removed (changed queue/removed (state true 1 2 3 4) (id 3))]
    (is (= ["Episode 3" "Episode 4"] (titles (changed queue/undone (elsewhere removed profile.queue/dequeue (id 2)))))
        "after the nearest neighbour that's still queued, rather than at its old number"))
  (let [heard-first (elsewhere (state true 1 2 3) profile/mark-played (id 2))]
    (is (= ["Episode 2" "Episode 3"]
           (titles (changed queue/undone (changed queue/removed heard-first (id 2)))))
        "but one that was heard before the change comes back")))

(deftest adding
  (testing "at the end, or after the episode that plays"
    (is (= ["Episode 2" "Episode 3"] (titles (added (state true 1 2) (id 3) "Episode 3" false))))
    (is (= ["Episode 3" "Episode 2"] (titles (added (state true 1 2) (id 3) "Episode 3" true)))))
  (is (= ["Episode 3" "Episode 2"] (titles (added (state true 1 2 3) (id 3) "Episode 3" true)))
      "play next moves an episode that's queued")
  (is (= "Playing Episode 3 next" (:announcement (added (state true 1 2) (id 3) "Episode 3" true))))
  (is (nil? (:queue-undo (added (changed queue/removed (state true 1 2) (id 2)) (id 3) "Episode 3" false)))
      "a change ends the undo of the one before"))

(deftest the-operation-of-a-change
  (is (= [:queue/move (id 4) 1] (:operation (queue/moved (state true 1 2 3 4) (id 4) 1)))
      "a position of the queue, counting the episode that plays")
  (is (nil? (queue/moved (state true 1 2 3) (id 2) 3)) "and none past the end"))

(deftest whats-queued
  (is (queue/queued? (state true 1 2) (id 2)))
  (is (not (queue/queued? (state true 1 2) (id 3)))))
