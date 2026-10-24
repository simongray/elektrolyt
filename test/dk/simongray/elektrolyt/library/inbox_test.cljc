(ns dk.simongray.elektrolyt.library.inbox-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.mind-meld :as crdt]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.elektrolyt.library.inbox :as inbox]
            [dk.simongray.elektrolyt.library.operations :as operations]))

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

(defn changed
  "The `state` with the change that `f` gives for it and the `args` made."
  [f state & args]
  (operations/changed state (apply f state args)))

(deftest the-inbox
  (is (= ["Episode 3" "Episode 2" "Episode 1"] (titles (state 1 3 2))) "the newest first")
  (is (= [] (inbox/episodes {})) "and none before the library is restored"))

(deftest a-dismissal-and-its-undo
  (let [dismissed (changed inbox/dismissed (state 1 2 3) (id 2))]
    (is (= ["Episode 3" "Episode 1"] (titles dismissed)))
    (is (= "Dismissed Episode 2" (:announcement dismissed)))
    (is (= "Undo dismissing Episode 2" (get-in dismissed [:inbox-undo :label])))
    (let [undone (changed inbox/undone dismissed)]
      (is (= ["Episode 3" "Episode 2" "Episode 1"] (titles undone)) "back in its place")
      (is (= "Put Episode 2 back in the inbox" (:announcement undone)))
      (is (nil? (:inbox-undo undone))))))

(deftest an-undo-after-another-tab-changed-the-inbox
  (let [dismissed (changed inbox/all-dismissed (state 1 2 3))
        elsewhere (fn [f i] (update dismissed :library library/update-profile f (id i)))
        queued    (elsewhere profile.queue/enqueue 2)]
    (is (= [(id 3) (id 1)] (:ids (inbox/offered queued))))
    (is (= ["Episode 3" "Episode 1"] (titles (changed inbox/undone queued)))
        "puts back only what's as it left it, and not an episode queued since")
    (is (= "Put 2 of 3 episodes back in the inbox" (:announcement (changed inbox/undone queued))))
    (is (= [(id 3) (id 1)] (:ids (inbox/offered (elsewhere profile/mark-played 2))))
        "nor one heard since"))
  (let [dismissed (changed inbox/dismissed (state 1 2) (id 2))
        queued    (update dismissed :library library/update-profile profile.queue/enqueue (id 2))]
    (is (nil? (inbox/offered queued)))
    (is (nil? (inbox/undone queued)) "and nothing when there's nothing to put back"))
  (let [dismissed (changed inbox/dismissed (state 1 2) (id 2))]
    (is (nil? (inbox/offered (update dismissed :library library/unsubscribe k)))
        "nor of a show that the listener left")))

(deftest all-at-once
  (let [dismissed (changed inbox/all-dismissed (state 1 2 3))]
    (is (= [] (titles dismissed)))
    (is (= "Dismissed 3 episodes" (:announcement dismissed)))
    (is (= ["Episode 3" "Episode 2" "Episode 1"] (titles (changed inbox/undone dismissed)))))
  (let [queued (changed inbox/all-queued (state 1 2 3))]
    (is (= [] (titles queued)))
    (is (= [(id 1) (id 2) (id 3)] (profile.queue/queue (:profile (:library queued))))
        "the oldest first, as a refresh queues them")
    (is (= "Added 3 episodes to the queue" (:announcement queued)))))
