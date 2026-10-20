(ns dk.simongray.podcast-app.player-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.player :as player]))

(def feed
  "A feed of an episode of an hour, in the show's artwork."
  {:rss/title    "A Show"
   :itunes/image {:href "https://example.org/a.jpg"}
   :rss/items    [{:rss/title       "Episode 7"
                   :rss/guid        {:content "e7"}
                   :rss/enclosure   {:url "https://example.org/7.mp3" :type "audio/mpeg"}
                   :itunes/duration 3600}]})

(def lib
  (library/subscribe (library/make "test") "https://example.org/feed.xml" feed))

(def id
  [(first (keys (:feeds lib))) "e7"])

(def episodes
  "What the app read of the files of the episode: its chapters."
  {id {:status :read
       :result {:chapters {:document {:chapters [{:start-time 0 :title "Intro"}
                                                 {:start-time 60 :title "News"}]}}}}})

(defn state
  "The state of a page whose player has the episode, 75 seconds in as it
  plays, with the `player` merged in."
  [player]
  {:library  lib
   :episodes episodes
   :player   (merge {:id    id
                     :media {:paused false :current-time 75 :duration 3600 :playback-rate 1.5}}
                    player)})

(deftest when-the-library-hears-of-it
  (let [playing {:paused false :current-time 100}]
    (is (player/report? playing {:paused true :current-time 101} 100) "at a pause")
    (is (player/report? {:paused false :seeking true} {:paused false :seeking false :current-time 600} 100)
        "after a seek")
    (is (player/report? playing {:paused false :current-time 105} 100) "every five seconds")
    (is (not (player/report? playing {:paused false :current-time 103} 100)) "but not sooner")
    (is (not (player/report? {:paused true} {:paused true :current-time 100} 50)) "nor while paused")
    (is (not (player/report? {:paused true :seeking true} {:paused true :seeking false :current-time 600} 100))
        "nor after the seek of a paused element, e.g. as it loads an episode that another tab plays")))

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
  "What another tab told of an episode as it plays."
  {:id [:feed "ep"] :tab "there" :paused false :status :ready :current-time 100 :duration 600
   :playback-rate 1 :at 0 :changed 0})

(deftest what-a-tab-tells
  (is (= {:paused false :current-time 12.5 :id [:feed "ep"] :tab "t" :at 1000 :changed 1000}
         (player/told {:paused false :seeking false :current-time 12.5 :buffered [[0 20]]}
                      [:feed "ep"] "t" 1000))
      "its told-keys, and when, for tabs/position and tabs/newest"))

(deftest the-newest-that-a-tab-told-of
  (let [paused (assoc playing-there :paused true :at 500 :changed 500)
        other  (assoc playing-there :tab "other" :at 300 :changed 300)]
    (is (= playing-there (player/heard nil playing-there 600)))
    (is (= paused (player/heard playing-there paused 600)) "a tab's newer state in place of its older")
    (is (= other (player/heard playing-there other 600)) "another tab that plays in place of one that played")
    (is (= other (player/heard other paused 600))
        "but not a pause of a tab after the one that plays took the play lock from it")))

(deftest one-player-across-the-tabs
  (let [here  {:id [:feed "here"] :media {:paused true :current-time 30}}
        there (assoc here :elsewhere playing-there :now 12000)]
    (is (= playing-there (player/elsewhere there)) "another tab plays")
    (is (nil? (player/elsewhere (assoc-in there [:media :paused] false)))
        "but not while this tab's element does, as it took the play lock")
    (is (nil? (player/elsewhere (assoc there :now 16000)))
        "nor once it hasn't told of it for three heartbeats")
    (is (= [:feed "ep"] (player/shown-id there)))
    (is (= [false 112 600] ((juxt :paused :current-time :duration) (player/shown-media there)))
        "the player shows it where it has come to by now")
    (is (= :elsewhere (player/playing there [:feed "ep"])))
    (is (nil? (player/playing there [:feed "here"])))
    (is (= [:feed "here"] (player/shown-id here)))
    (is (= {:paused true :current-time 30} (player/shown-media here)) "or else its own")
    (is (= :here (player/playing (assoc-in here [:media :paused] false) [:feed "here"])))
    (is (nil? (player/playing here [:feed "here"])) "unless it's paused")
    (is (nil? (player/playing here nil)))))

(deftest the-playback-that-the-player-shows
  (let [pb (player/shown-playback (state {}))]
    (is (= ["Episode 7" "A Show" "https://example.org/a.jpg"] ((juxt :title :show :artwork) pb)))
    (is (= "News" (:title (player/chapter-at pb 75))) "with the chapters read of its files")
    (is (identical? pb (player/shown-playback (state {:media {:paused false :current-time 76}})))
        "made again only when what it's made of changes")
    (is (nil? (player/shown-playback (state {:id nil}))) "and none without an episode")))

(deftest what-the-status-line-says
  (is (nil? (player/status-text {:media {:paused false}})))
  (is (= "Playing in another tab." (player/status-text {:media {:paused true} :elsewhere playing-there :now 0})))
  (is (re-find #"couldn't be played" (player/status-text {:media {:error {:code :src-not-supported}}})))
  (is (= "Press Play to start." (player/status-text {:answer {:refused :not-allowed}})))
  (is (= "Loading…" (player/status-text {:buffering? true}))))
