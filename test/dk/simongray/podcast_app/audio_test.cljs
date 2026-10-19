(ns dk.simongray.podcast-app.audio-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.podcast-app.audio :as audio]
            [dk.simongray.podcast-app.player :as player]))

(def url "https://example.org/feed.xml")

(def feed
  {:rss/title "A Show"
   :rss/items [{:rss/title     "One"
                :rss/guid      {:content "one"}
                :rss/enclosure {:url "https://example.org/1.mp3" :type "audio/mpeg"}}]})

(def two-feed
  "A feed of two episodes."
  (update feed :rss/items conj {:rss/title     "Two"
                                :rss/guid      {:content "two"}
                                :rss/enclosure {:url "https://example.org/2.mp3" :type "audio/mpeg"}}))

(deftest the-episode-played-last-through-a-reload
  (let [lib     (-> (library/make "test")
                    (library/add-episode url two-feed "one")
                    (library/add-episode url two-feed "two"))
        feed-id (first (keys (:feeds lib)))
        one     [feed-id "one"]
        two     [feed-id "two"]
        played  (dissoc (library/step lib [:play one]) :effects)
        current #(some-> % :library :profile queue/current)
        now     (js/Date.now)]
    (is (= one (current {:library played})))
    (is (= two (current (audio/restored {:library played} nil {:id two :at now})))
        "the episode that this browser played last, which no save had")
    (is (= one (current (audio/restored {:library played} nil nil))) "nothing kept")
    (is (= one (current (audio/restored {:library played} nil {:id [feed-id "three"] :at now})))
        "nor an episode that the library hasn't")
    (is (= two (get-in (audio/restored {:library played} nil {:id two :at now}) [:player :id]))
        "which the player has")))

(deftest the-position-through-a-reload
  (let [lib (library/add-episode (library/make "test") url feed "one")
        id  [(first (keys (:feeds lib))) "one"]
        now (js/Date.now)]
    (is (= 636 (:position (player/entry (audio/with-kept-position lib {:id id :position 636 :duration 3600 :at now}) id)))
        "the position that this browser kept as the page went")
    (is (= lib (audio/with-kept-position lib nil)) "nothing kept")
    (is (= lib (audio/with-kept-position lib {:id ["other" "x"] :position 5 :at now}))
        "nor an episode that the library hasn't")
    (let [later (audio/with-kept-position lib {:id id :position 900 :duration 3600 :at (+ now 1000)})]
      (is (= 900 (:position (player/entry (audio/with-kept-position later {:id id :position 636 :at now}) id)))
          "a later position stays"))))
