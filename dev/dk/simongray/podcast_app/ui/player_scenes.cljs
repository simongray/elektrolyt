(ns dk.simongray.podcast-app.ui.player-scenes
  "Scenes of the player: nothing playing, an episode that plays with its
  chapters, its controls on a phone, an episode that plays in another tab,
  and the ways it can fail to play."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.ui :as ui]
            [dk.simongray.podcast-app.ui.search-scenes :as search-scenes]
            [portfolio.replicant :refer [defscene]]))

(def playback
  "What library/playback gives for an episode with chapters."
  {:id       ["917393e3-1b1e-5cef-ace4-edaa54e1f810" "PC20-273"]
   :src      "https://example.org/273.mp3"
   :title    "Episode 273: \"Advertising is the Horse and Buggies of Podcasting\""
   :show     "Podcasting 2.0"
   :artwork  search-scenes/artwork
   :chapters {:source   :file
              :document {:chapters [{:start-time 0 :title "Show Open"}
                                    {:start-time 81 :title "A sales meeting with the Christian broadcasters"}
                                    {:start-time 666 :title "Show and tell: PodcastIndexRobot.com"}]}}})

(def media
  "The state of the audio element, 10 minutes and 30 seconds in."
  {:status        :ready
   :paused        false
   :current-time  630
   :duration      5385
   :playback-rate 1
   :volume        1
   :muted         false})

(defn playing
  "The state of the queue with the `player` merged into one that plays the
  episode of the scenes."
  [player]
  {:route   {:name :route/queue}
   :library (library/make "scenes")
   :player  (merge {:id       (:id playback)
                    :playback playback
                    :media    media
                    :volume   1}
                   player)})

(defscene nothing-playing
  "Before the listener plays anything, when the page has no player."
  (ui/page {:route {:name :route/queue} :library (library/make "scenes")}))

(defscene an-episode-that-plays
  "The episode, its chapter and its controls, all in view wider than a
  phone, and behind Controls on one."
  (ui/page (playing {})))

(defscene the-controls-on-a-phone
  "The controls of the player once the listener opens them on a phone."
  (ui/page (playing {:expanded? true})))

(defscene paused-with-the-volume-locked
  "A paused episode on an iPhone, whose volume slider stays, disabled, since
  only the device's buttons set the volume."
  (ui/page (playing {:media (assoc media :paused true) :volume-locked? true})))

(defscene while-it-waits-for-data
  "The player once it has waited half a second for data, which its status
  line tells a screen reader."
  (ui/page (playing {:media (assoc media :status :buffering) :buffering? true})))

(defscene a-file-that-wont-play
  "A file that the server or the network failed to give, or that the
  browser can't play."
  (ui/page (playing {:media (assoc media :paused true :status :error
                                   :error {:code :src-not-supported})})))

(defscene playing-in-another-tab
  "The playback of another tab, 15 minutes in, which this tab's player
  shows and controls, with its own element paused where it loaded."
  (ui/page (playing {:media           (assoc media :paused true)
                     :shared          (assoc media :id (:id playback) :at 0 :changed 0
                                             :current-time 900)
                     :shared-position 900
                     :shared-playback playback})))

(defscene a-play-that-the-browser-refused
  "A play before the page may play, e.g. after a reload, which the
  listener's own press starts."
  (ui/page (playing {:media (assoc media :paused true) :answer {:refused :not-allowed}})))
