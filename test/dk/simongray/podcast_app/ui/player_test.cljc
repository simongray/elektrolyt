(ns dk.simongray.podcast-app.ui.player-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.ui.player :as player-ui]
            [dk.simongray.podcast-app.ui.show-test :refer [text-of]]
            [dk.simongray.podcast-app.ui-test :refer [elements]]))

(def playback
  {:id       ["f" "e"]
   :title    "Episode 7"
   :show     "A Show"
   :chapters {:document {:chapters [{:start-time 0 :title "Intro"} {:start-time 60 :title "News"}]}}})

(defn state
  "The state of a page whose player has the episode of the test, with the
  `player` merged in."
  [player]
  {:library (library/make "test")
   :player  (merge {:id       ["f" "e"]
                    :playback playback
                    :media    {:paused false :current-time 75 :duration 3600 :playback-rate 1.5}}
                   player)})

(defn buttons
  "The buttons of the Hiccup `x`, with or without a class."
  [x]
  (->> (tree-seq (some-fn vector? seq?) #(if (vector? %) (rest %) %) x)
       (filter #(and (vector? %) (keyword? (first %)) (re-find #"^button\b" (name (first %)))))))

(defn attributes-of
  "The attributes of the element with the `id` in the Hiccup `x`."
  [id x]
  (some #(when (= id (:id (second %))) (second %))
        (filter #(map? (second %)) (concat (elements :input x) (elements :select x) (elements :div x)))))

(deftest the-main-action
  (is (= [:button.primary :button.primary]
         (map (comp first player-ui/play-button) [{:paused true} {:paused false}]))
      "Play and Pause, which the look fills"))

(deftest nothing-playing
  (is (nil? (player-ui/region {:library (library/make "test")}))
      "no player, so that the main content has the room"))

(deftest an-episode-that-plays
  (let [x (player-ui/region (state {}))]
    (is (= "medium" (:data-size (second x))) "in the size that the listener prefers, medium at first")
    (is (re-find #"Episode 7" (text-of (elements :div.player-title x))))
    (is (re-find #"News" (text-of (elements :div.player-title x))) "with its chapter")
    (is (= ["Pause" "Back 15 s" "Forward 30 s" "Controls" "Previous chapter" "Next chapter" "Mute"]
           (map text-of (buttons x))))
    (testing "the seek bar, which a screen reader reads in words"
      (is (= {:min 0 :max 3600 :step 1 :value 75 :aria-valuetext "1 minute, 15 seconds"}
             (select-keys (attributes-of "player-position" x) [:min :max :step :value :aria-valuetext])))
      (is (= 600 (:value (attributes-of "player-position" (player-ui/region (state {:seeking 600})))))
          "where it's dragged to, while it is"))
    (is (= 1.5 (some #(when (:selected (second %)) (:value (second %))) (elements :option x)))
        "the speed that it plays at")))

(deftest the-status-line
  (is (= [:p.visually-hidden {:role "status"} "Loading…"]
         (first (elements :p.visually-hidden (player-ui/region (state {:buffering? true})))))
      "which only a screen reader reads, so that the player doesn't move as it changes"))

(deftest the-controls-on-a-phone
  (let [button #(some (fn [b] (when (= "Controls" (text-of b)) (second b))) (buttons %))
        shown  #(:class (attributes-of "player-controls" %))]
    (is (= "false" (:aria-expanded (button (player-ui/region (state {}))))))
    (is (= ["player-controls" "collapsed"] (shown (player-ui/region (state {})))))
    (is (= "true" (:aria-expanded (button (player-ui/region (state {:expanded? true}))))))
    (is (= ["player-controls"] (shown (player-ui/region (state {:expanded? true})))))
    (is (= ["group" "Controls"]
           ((juxt :role :aria-label) (attributes-of "player-controls" (player-ui/region (state {})))))
        "a group named as the button")))

(deftest the-volume
  (is (= {:disabled false :aria-valuetext "50%"}
         (select-keys (attributes-of "player-volume" (player-ui/region (state {:volume 0.5})))
                      [:disabled :aria-valuetext])))
  (is (= {:disabled true :value 1 :aria-valuetext "Set with the device's buttons"}
         (select-keys (attributes-of "player-volume" (player-ui/region (state {:volume 0.5 :volume-locked? true})))
                      [:disabled :value :aria-valuetext]))
      "disabled where the device locks it, rather than a false level"))
