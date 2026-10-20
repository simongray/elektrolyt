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

(deftest the-time-left
  (is (= 4755 (player/remaining {:current-time 630 :duration 5385})))
  (is (nil? (player/remaining {:current-time 630 :duration nil}))))

(def playing-there
  "The newest playback across the tabs, of another tab, which plays."
  {:id [:feed "ep"] :paused false :status :ready :current-time 100 :duration 600 :playback-rate 1})

(deftest one-playback-across-the-tabs
  (let [here  {:media {:paused true :current-time 30} :shared (assoc playing-there :here? true)}
        there {:media {:paused true :current-time 30} :shared playing-there :shared-position 112}]
    (is (player/writes? here) "this tab tells the library where it is, when its playback is the newest")
    (is (player/writes? {:media {:paused true}}) "and before any tab has played")
    (is (not (player/writes? there)) "but not while another tab's is")
    (is (not (player/writes? (assoc-in there [:shared :paused] true)))
        "nor after another tab paused it, where this tab's position is older")
    (is (player/playing-elsewhere? there))
    (is (= [:feed "ep"] (player/playing (assoc there :id [:feed "here"])))
        "the episode that plays is another tab's, where its playback is the newest")
    (is (nil? (player/playing (assoc-in there [:shared :paused] true))) "none once it's paused")
    (is (= [:feed "here"] (player/playing {:id [:feed "here"] :media {:paused false}}))
        "or this tab's")
    (is (nil? (player/playing (assoc here :id [:feed "here"]))) "unless it's paused")
    (is (= [false 112 600] ((juxt :paused :current-time :duration) (player/shown-media there)))
        "the player shows another tab's playback, where it has come to by now")
    (is (= {:paused true :current-time 30} (player/shown-media here)) "or its own")
    (is (= {:title "There"} (player/shown-playback (assoc there :playback {:title "Here"}
                                                          :shared-playback {:title "There"}))))))

(deftest what-the-status-line-says
  (is (nil? (player/status-text {:media {:paused false}})))
  (is (= "Playing in another tab." (player/status-text {:shared playing-there})))
  (is (re-find #"couldn't be played" (player/status-text {:media {:error {:code :src-not-supported}}})))
  (is (= "Press Play to start." (player/status-text {:answer {:refused :not-allowed}})))
  (is (= "Loading…" (player/status-text {:buffering? true}))))
