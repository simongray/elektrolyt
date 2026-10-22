(ns dk.simongray.elektrolyt.ui.player-scenes
  "Scenes of the player: nothing playing, an episode that plays with its
  chapters, its controls on a phone, an episode that plays in another tab,
  the ways it can fail to play, and its colours."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.scenes :refer [artwork feed url]]
            [dk.simongray.elektrolyt.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(def lib
  "A library subscribed to the scenes' show, in its artwork."
  (library/subscribe (library/make "scenes") url (assoc feed :itunes/image {:href artwork})))

(def id
  "The episode of the scenes, which has chapters."
  [(first (keys (:feeds lib))) "PC20-273"])

(def episodes
  "What the app read of the episode's files: its chapters file."
  {id {:status :read
       :result {:chapters {:source   :file
                           :document {:chapters [{:start-time 0 :title "Show Open"}
                                                 {:start-time 81 :title "A sales meeting with the Christian broadcasters"}
                                                 {:start-time 666 :title "Show and tell: PodcastIndexRobot.com"}]}}}}})

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
  {:route    {:name :route/queue}
   :library  lib
   :episodes episodes
   :player   (merge {:id     id
                     :media  media
                     :volume 1}
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
  (ui/page (playing {:media     (assoc media :paused true)
                     :elsewhere (assoc media :id id :tab "other" :at 0 :changed 0
                                       :current-time 900)
                     :now       0})))

(defscene a-play-that-the-browser-refused
  "A play before the page may play, e.g. after a reload, which the
  listener's own press starts."
  (ui/page (playing {:media (assoc media :paused true) :answer {:refused :not-allowed}})))

(defscene in-the-colours-of-its-artwork
  "The player in the colours of the artwork of what plays, once they're
  worked out, here of a deep red."
  (ui/page (assoc (playing {}) :colors {artwork {:seed "#9e2a2b" :dominant "#888888"}})))
