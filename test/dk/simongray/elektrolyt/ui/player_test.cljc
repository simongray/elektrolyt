(ns dk.simongray.elektrolyt.ui.player-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.player-test :refer [id lib state]]
            [dk.simongray.elektrolyt.queue-test :as queue-test]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]
            [dk.simongray.elektrolyt.ui.player :as player-ui]
            [dk.simongray.elektrolyt.ui-test :refer [elements nodes text-of]]))

(defn buttons
  "The buttons of the Hiccup `x`, with or without a class, and the buttons
  of ui.controls' aliases."
  [x]
  (filter #(and (vector? %) (keyword? (first %)) (re-find #"^(icon-)?button\b" (name (first %))))
          (nodes x)))

(defn attributes-of
  "The attributes of the element with the `id` in the Hiccup `x`."
  [id x]
  (some #(when (= id (:id (second %))) (second %))
        (filter #(map? (second %)) (concat (elements :input x) (elements :select x) (elements :div x)))))

(deftest the-main-action
  (is (= [[controls/icon-button icons/play "Play"] [controls/icon-button icons/pause "Pause"]]
         (map (comp (juxt first (comp :icon second) last) player-ui/play-button)
              [{:id [:feed "ep"] :media {:paused true}} {:id [:feed "ep"] :media {:paused false}}]))
      "Play and Pause, as their icons alone, named by their words"))

(deftest nothing-playing
  (is (nil? (player-ui/region {:library (library/make "test")}))
      "no player, so that the main content has the room"))

(deftest an-episode-that-plays
  (let [x (player-ui/region (state {}))]
    (is (re-find #"Episode 7" (text-of (elements :div.player-title x))))
    (is (re-find #"News" (text-of (elements :div.player-title x))) "with its chapter")
    (is (= [(lists-ui/episode-href lib id "Episode 7") (lists-ui/show-href lib (first id) "A Show")]
           (map (comp :href second) (elements :a (elements :div.player-title x))))
        "the episode's title and the show's name, links to their pages")
    (is (= ["Episode 7" "A Show" "News"]
           (keep (comp :title second) (concat (elements :a (elements :div.player-title x))
                                              (elements :span (elements :div.player-title x)))))
        "each with its whole text as its tooltip, where the line cuts it short")
    (is (= ["Back 15 seconds" "Pause" "Forward 30 seconds" "Mute" "Previous chapter" "Next chapter"]
           (map text-of (buttons x)))
        "Play between back and forward and the speaker after them, then the chapters' buttons, every one in view")
    (testing "the seek bar, which a screen reader reads in words"
      (is (= {:min 0 :max 3600 :step 1 :value 75 :aria-valuetext "1 minute, 15 seconds, News"}
             (select-keys (attributes-of "player-position" x) [:min :max :step :value :aria-valuetext])))
      (is (= 600 (:value (attributes-of "player-position" (player-ui/region (state {:seeking 600})))))
          "where it's dragged to, while it is")
      (is (= {:--played (str (double (/ 75 3600)))}
             (:style (second (first (elements :div.player-bar x)))))
          "which draws how much has played")
      (is (= {:--clock "8ch"} (:style (second (first (elements :div.player-seek x)))))
          "with times as wide as the duration, 1:00:00, and its minus"))
    (is (= "1.5" (some #(when (:selected (second %)) (:value (second %))) (elements :option x)))
        "the speed that it plays at")
    (is (= "Speed" (:title (attributes-of "player-speed" x))) "named by its title, its tooltip")))

(deftest the-marks-of-chapters
  (let [chapters [{:start-time 0 :title "Intro"} {:start-time 60 :title "News"} {:start-time 3600 :title "After"}]
        x        (player-ui/chapter-marks {:duration 3600} chapters)]
    (is (= {:aria-hidden "true"} (second x)) "for a pointer alone")
    (is (= [{:title "News"
             :style {:--at (str (double (/ 60 3600)))}
             :on    {:click [[:player/press {:action :seekto :seek-time 60}]]}}]
           (map #(select-keys (second %) [:title :style :on]) (elements :span.player-chapter-mark x)))
        "where each chapter after the first begins in the episode, a press to its start")
    (is (nil? (player-ui/chapter-marks {} chapters)) "and none before the duration is known")))

(deftest what-plays-next
  (let [x (player-ui/next-up (queue-test/state true 1 2))]
    (is (= "/queue" (:href (second x))) "a link to the queue")
    (is (= "Next: Episode 2" (text-of x)) "named by what plays next"))
  (is (nil? (player-ui/next-up (queue-test/state true 1))) "and nothing after the last"))

(deftest the-status-line
  (is (= [:p.visually-hidden {:role "status"} "Loading…"]
         (first (elements :p.visually-hidden (player-ui/region (state {:buffering? true})))))
      "which only a screen reader reads, so that the player doesn't move as it changes"))

(deftest the-volume
  (let [mute   #(some (fn [b] (when (#{"Mute" "Unmute"} (text-of b)) [(text-of b) (:on (second b))]))
                      (buttons %))
        slider #(select-keys (attributes-of "player-volume" %) [:value :aria-valuetext :on])]
    (let [x (player-ui/region (state {:volume 0.5}))]
      (is (= ["Mute" {:click [[:player/mute true]]}] (mute x)) "a speaker that mutes")
      (is (= {:value 0.5 :aria-valuetext "50%" :on {:input [[:player/volume [:event/target-number]]]}}
             (slider x))
          "and the slider that rises from it, whose level a screen reader reads"))
    (let [x (player-ui/region (state {:volume 0.5
                                      :media  {:paused false :current-time 75 :duration 3600 :muted true}}))]
      (is (= ["Unmute" {:click [[:player/mute false]]}] (mute x)))
      (is (= {:value 0 :aria-valuetext "0%" :on {:input [[:player/volume [:event/target-number]]
                                                         [:player/mute false]]}}
             (slider x))
          "at silence while muted, which moving it ends"))
    (let [x (player-ui/region (state {:volume 0.5 :volume-locked? true}))]
      (is (= "Mute" (first (mute x))))
      (is (empty? (slider x))
          "a mute button alone where the device locks the volume, rather than a slider at a false level")))
  (is (= [icons/speaker-slash icons/speaker-slash icons/speaker-low icons/speaker-high]
         (map #(apply player-ui/volume-icon %) [[1 true] [0 false] [0.3 false] [0.8 false]]))
      "the icon shows the level, struck out while muted or silent"))
