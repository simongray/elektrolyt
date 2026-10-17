(ns dk.simongray.podcast-app.player-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.player :as player]))

(deftest when-the-library-hears-of-it
  (let [playing {:paused false :current-time 100}]
    (is (player/report? playing {:paused true :current-time 101} 100) "at a pause")
    (is (player/report? {:paused false :seeking true} {:paused false :seeking false :current-time 600} 100)
        "after a seek")
    (is (player/report? playing {:paused false :current-time 105} 100) "every five seconds")
    (is (not (player/report? playing {:paused false :current-time 103} 100)) "but not sooner")
    (is (not (player/report? {:paused true} {:paused true :current-time 100} 50)) "nor while paused")))

(deftest when-the-library-is-saved
  (is (player/save? {:paused false} {:paused true} 0 1000) "at a pause")
  (is (player/save? {:paused false} {:paused false} 1000 31000) "every 30 seconds of playing")
  (is (not (player/save? {:paused false} {:paused false} 1000 20000)) "but not sooner")
  (is (not (player/save? {:paused true} {:paused true} 0 99999)) "nor while paused"))

(deftest a-render-a-second
  (is (= {:current-time 12.0 :paused false} (player/whole-seconds {:current-time 12.7 :paused false})))
  (is (= {:current-time nil} (player/whole-seconds {:current-time nil}))))

(deftest the-images-of-chapters-through-the-proxy
  (is (= {:title        "E"
          :chapter-info [{:title "Intro" :start-time 0
                          :artwork [{:src "/proxy?url=https%3A%2F%2Fexample.org%2F1.jpg"}]}
                         {:title "Two" :start-time 60 :artwork []}]}
         (player/with-proxied-chapters
          {:title        "E"
           :chapter-info [{:title "Intro" :start-time 0 :artwork [{:src "https://example.org/1.jpg"}]}
                          {:title "Two" :start-time 60}]}))))

(deftest the-chapter-at-a-time
  (let [pb {:chapters {:document {:chapters [{:start-time 0 :title "A"} {:start-time 60 :title "B"}]}}}]
    (is (= "B" (:title (player/chapter-at pb 75))))
    (is (nil? (player/chapter-at pb nil)))
    (is (nil? (player/chapter-at {} 75)) "none for an episode without chapters")))

(deftest the-duration-before-the-metadata
  (is (= 5385 (:duration (player/with-duration {:current-time 630 :duration nil} {:duration 5385})))
      "the library's, until the element knows its own")
  (is (= 5973 (:duration (player/with-duration {:duration 5973} {:duration 5385})))
      "which goes first")
  (is (nil? (:duration (player/with-duration {:duration nil} {})))))

(deftest the-times-of-the-seek-bar
  (is (= "10:30" (player/clock 630)))
  (is (= "–:––" (player/clock nil)) "before the metadata")
  (is (= 4755 (player/remaining {:current-time 630 :duration 5385})))
  (is (nil? (player/remaining {:current-time 630 :duration nil}))))

(deftest what-the-status-line-says
  (is (nil? (player/status-text {:media {:paused false}})))
  (is (re-find #"couldn't be played" (player/status-text {:media {:error {:code :src-not-supported}}})))
  (is (= "Press Play to start." (player/status-text {:answer {:refused :not-allowed}})))
  (is (= "Loading…" (player/status-text {:buffering? true}))))
