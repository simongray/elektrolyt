(ns dk.simongray.podcast-app.audio-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.audio :as audio]
            [dk.simongray.podcast-app.player :as player]))

(def url "https://example.org/feed.xml")

(def feed
  {:rss/title "A Show"
   :rss/items [{:rss/title     "One"
                :rss/guid      {:content "one"}
                :rss/enclosure {:url "https://example.org/1.mp3" :type "audio/mpeg"}}]})

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
