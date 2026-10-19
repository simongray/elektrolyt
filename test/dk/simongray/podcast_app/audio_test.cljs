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
    (is (= two (current (audio/restored {:library played} {:id two :at now} nil nil)))
        "the episode that this browser played last, which no save had")
    (is (= one (current (audio/restored {:library played} nil nil nil))) "nothing kept")
    (is (= two (get-in (audio/restored {:library played} {:id two :at now} nil nil) [:player :id]))
        "which the player has")))

(deftest the-player-as-the-page-loads
  (let [lib (library/add-episode (library/make "test") url feed "one")
        id  [(first (keys (:feeds lib))) "one"]
        now (js/Date.now)
        lib (library/update-profile lib queue/enqueue id)]
    (is (= 636 (:position (player/entry (:library (audio/restored {:library lib} nil
                                                                  {:id id :position 636 :duration 3600 :at now}
                                                                  nil))
                                        id)))
        "the library told of the position that this browser kept")
    (is (= 0.4 (get-in (audio/restored {:library lib} nil nil 0.4) [:player :volume]))
        "at the volume that this browser keeps")
    (is (= 1 (get-in (audio/restored {:library lib} nil nil nil) [:player :volume]))
        "or all of it")))
