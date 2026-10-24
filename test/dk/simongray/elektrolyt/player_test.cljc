(ns dk.simongray.elektrolyt.player-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.player :as player]))

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
  (let [playing {:paused false :current-time 100}
        now     1000000
        last    (- now 3000)]
    (is (player/report? playing {:paused true :current-time 101} last now nil) "at a pause")
    (is (player/report? {:paused false :seeking true} {:paused false :seeking false :current-time 600} last now nil)
        "after a seek")
    (is (player/report? {:paused false :current-time 1739} {:paused false :current-time 1740} last now 1740)
        "as it reaches the outro")
    (is (not (player/report? {:paused false :current-time 1741} {:paused false :current-time 1742} last now 1740))
        "but not again in it")
    (is (player/report? playing {:paused false :current-time 101} (- now 15000) now nil) "every fifteen seconds")
    (is (player/report? playing {:paused false :current-time 101} nil now nil) "and as it starts")
    (is (not (player/report? playing {:paused false :current-time 103} last now nil)) "but not sooner")
    (is (player/report? playing {:paused false :current-time 103} (+ now 60000) now nil)
        "unless the clock went back past the last report")
    (is (not (player/report? {:paused true} {:paused true :current-time 100} nil now nil)) "nor while paused")
    (is (not (player/report? {:paused true :seeking true} {:paused true :seeking false :current-time 600} nil now nil))
        "nor after the seek of a paused element, e.g. as it loads an episode that another tab plays")))

(deftest a-render-a-second
  (is (= {:current-time 12.0 :paused false} (player/whole-seconds {:current-time 12.7 :paused false})))
  (is (= {:current-time nil} (player/whole-seconds {:current-time nil}))))

(deftest the-images-of-chapters-through-the-proxy
  (is (= {:title        "E"
          :chapter-info [{:title "Intro" :start-time 0
                          :artwork [{:src "/proxy?url=https%3A%2F%2Fexample.org%2F1.jpg"}]}
                         {:title "Two" :start-time 60 :artwork []}
                         {:title "Three" :start-time 120 :artwork [{:src "data:image/png;base64,iVBORw0K"}]}]}
         (player/with-proxied-chapters
          {:title        "E"
           :chapter-info [{:title "Intro" :start-time 0 :artwork [{:src "https://example.org/1.jpg"}]}
                          {:title "Two" :start-time 60}
                          {:title "Three" :start-time 120 :artwork [{:src "data:image/png;base64,iVBORw0K"}]}]})))
  (is (= "data:image/jpeg;base64,/9j/" (player/proxied "data:image/jpeg;base64,/9j/"))
      "but not the image of an ID3 chapter, which the media file holds")
  (is (nil? (player/proxied "//example.org/1.jpg")) "nor a URL that isn't http or https")
  (is (nil? (player/proxied nil))))

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
      "its told-keys, and when, for playback/position and playback/newest")
  (is (true? (:paused (player/told {:paused false :status :error :error {:code :network}}
                                   [:feed "ep"] "t" 1000)))
      "and paused once its element failed, which HTML leaves unpaused"))

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
    (is (nil? (player/playing (update here :media assoc :paused false :error {:code :src-not-supported})
                              [:feed "here"]))
        "or failed, e.g. at a dead file")
    (is (nil? (player/playing here nil)))))

(deftest a-start-before-the-end
  (is (= 754 (player/start-time 3600 754)))
  (is (nil? (player/start-time 3600 3600)) "not at the end, which would only mark it played")
  (is (nil? (player/start-time 3600 5000)) "nor past it")
  (is (= 5000 (player/start-time nil 5000)) "unless the duration is unknown")
  (is (nil? (player/start-time 3600 nil))))

(deftest a-step-that-moves-on
  (is (player/moved-on? [[:stop [:feed "ep"]] [:played [:feed "ep"]] [:play [:feed "next"]]])
      "stops the episode in its outro and plays the next")
  (is (player/moved-on? [[:play [:feed "next"]]]) "or skips to the next")
  (is (not (player/moved-on? [[:seek [:feed "ep"] 600] [:played [:feed "ep"]]])) "but a seek doesn't")
  (is (not (player/moved-on? []))))

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
