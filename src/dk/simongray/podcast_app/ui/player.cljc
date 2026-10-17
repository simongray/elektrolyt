(ns dk.simongray.podcast-app.ui.player
  "The views of the player, which stays as the pages change.

  On a phone it's one row of the episode and the button that plays it,
  with a disclosure of the other controls. Wider than that, they're all in
  view. The Episode page is the player's full view, with the notes, the
  chapters and the transcript."
  (:require [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.podcast-app.player :as player]
            [dk.simongray.podcast-app.queue :as queue]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.ui.artwork :as artwork]))

(defn now-playing
  "The artwork, the title and the show of the `playback`, with its title of
  the chapter at `seconds`, whose image takes the place of the artwork."
  [{:keys [id title show artwork] :as playback} seconds]
  (let [chapter (player/chapter-at playback seconds)
        image   (or (some-> (:img chapter) player/proxied) artwork)]
    (list
     (when image
       (artwork/img {:artwork image} 48 {}))
     [:div.player-title
      [:p [:a {:href (routes/episode-href (first id) (second id) title nil)} title]]
      [:p show
       (when-let [t (:title chapter)]
         (list [:span {:aria-hidden "true"} " · "] t))]])))

;; WAI-ARIA APG, button pattern: a button whose label changes, from Play to
;; Pause, has no aria-pressed
(defn play-button
  "The button that plays the episode of the `media` state, or pauses it."
  [{:keys [paused] :as media}]
  (if (false? paused)
    [:button {:type "button" :on {:click [[:player/pause]]}} "Pause"]
    [:button {:type "button" :on {:click [[:player/resume]]}} "Play"]))

;; A range input, rather than a slider of our own, since a screen reader on
;; a phone adjusts it with a swipe, which it always follows
(defn seek-bar
  "The seek bar of the `media` state, at the `seeking` seconds while it's
  dragged, with the time so far and the time left."
  [media seeking]
  (let [shown (cond-> media seeking (assoc :current-time seeking))]
    [:div.player-seek
     [:label.visually-hidden {:for "player-position"} "Position"]
     [:time {:datetime (str "PT" (long (or (:current-time shown) 0)) "S")}
      (player/clock (:current-time shown))]
     [:input (merge {:type "range"
                     :id   "player-position"
                     :on   {:input  [[:player/drag [:event/range-seek]]]
                            :change [[:player/press [:event/range-seek]]]}}
                    (controls/range-attributes shown))]
     (let [left (player/remaining shown)]
       [:time {:datetime (str "PT" (long (or left 0)) "S")}
        "−" (player/clock left)])]))

(defn skip-buttons
  "The buttons that skip back and forward by the seconds that the
  listener prefers, `back` and `forward`."
  [back forward]
  (list
   [:button {:type "button" :on {:click [[:player/press {:action :seekbackward}]]}}
    (str "Back " back " s")]
   [:button {:type "button" :on {:click [[:player/press {:action :seekforward}]]}}
    (str "Forward " forward " s")]))

(defn chapter-buttons
  "The buttons that go to the previous and the next chapter of the
  `playback`, when it has chapters."
  [playback]
  (when (seq (:chapters (player/chapter-doc playback)))
    (list
     [:button {:type "button" :on {:click [[:player/chapter :previous]]}} "Previous chapter"]
     [:button {:type "button" :on {:click [[:player/chapter :next]]}} "Next chapter"])))

;; Adrian Roselli, Be Careful Using 'menu': a select is the control for
;; choosing a value, where a menu is for the commands of an application
;; https://adrianroselli.com/2023/05/be-careful-using-menu.html
(defn speed
  "The choice of the speed of the `media` state, which sets the listener's
  preference."
  [{:keys [playback-rate] :as media}]
  (let [rate (or playback-rate 1)]
    (list
     [:label {:for "player-speed"} "Speed"]
     [:select {:id "player-speed"
               :on {:change [[:player/rate [:event/target-number]]]}}
      (for [s player/speeds]
        [:option {:value s :selected (== s rate)} (str s "×")])])))

;; An iPhone plays at the system's volume, which a page can neither set nor
;; read, so there the slider stays, disabled, for a layout that doesn't
;; shift, and says so to a screen reader rather than a false 100%
(defn volume-controls
  "The mute button and the volume slider at the `level` of the `media`
  state, which is disabled where the device `locked?` the volume."
  [{:keys [muted] :as media} level locked?]
  (list
   [:button {:type "button" :on {:click [[:player/mute (not muted)]]}}
    (if muted "Unmute" "Mute")]
   [:label.visually-hidden {:for "player-volume"} "Volume"]
   [:input {:type           "range"
            :id             "player-volume"
            :min            0
            :max            1
            :step           0.05
            :value          (if locked? 1 level)
            :disabled       (boolean locked?)
            :aria-valuetext (if locked?
                              "Set with the device's buttons"
                              (str (Math/round (* 100 level)) "%"))
            :on             {:input [[:player/volume [:event/target-number]]]}}]))

;; Pocket Casts, Apple Podcasts and Spotify all reach the queue from the
;; player, which here is a page of the app
(defn next-up
  "The episode that plays after the one of the player of the `state`, as a
  link to the queue, or nil."
  [state]
  (when-let [{:keys [title]} (first (queue/up-next state))]
    [:p.player-next [:a {:href (routes/href :route/queue)} "Next: " title]]))

(defn status-line
  "The line that says why the `player` doesn't play, or that it waits for
  data, which a screen reader reads as it changes."
  [player]
  [:p.player-status {:role "status"} (player/status-text player)])

;; WAI-ARIA APG, disclosure pattern: a button with aria-expanded that shows
;; and hides the controls below it, which leaves the history and the focus
;; as they are
(defn content
  "The content of the player of the `state`: the episode that plays, and
  its controls."
  [{:keys [player library] :as state}]
  (let [{:keys [id playback media seeking expanded? volume volume-locked?]} player
        [back forward] (player/skips (:profile library))]
    (if-not (and id playback)
      [:p "Nothing is playing."]
      (list
       [:audio {:replicant/key        "player"
                :preload              "metadata"
                :replicant/on-mount   [[:player/mounted]]
                :replicant/on-unmount [[:player/unmounted]]
                :on                   {:ended [[:player/ended]]}}]
       [:div.player-now
        (now-playing playback (:current-time media))
        (play-button media)
        ;; as a string, since Replicant leaves out an attribute of false
        [:button.player-more {:type          "button"
                              :aria-expanded (str (boolean expanded?))
                              :aria-controls "player-controls"
                              :on            {:click [[:player/expand (not expanded?)]]}}
         "Controls"]]
       [:div {:id    "player-controls"
              :class (cond-> ["player-controls"] (not expanded?) (conj "collapsed"))}
        (seek-bar (player/with-duration media playback) seeking)
        (skip-buttons back forward)
        (chapter-buttons playback)
        (speed media)
        (volume-controls media (or volume 1) volume-locked?)
        (next-up state)]
       (status-line player)))))

;; WAI-ARIA APG, landmark regions: a section with a label is a region, the
;; landmark for content that none of the others describes
(defn region
  "The player of the `state`, which stays as the pages change."
  [state]
  [:section#player {:aria-label "Player"}
   (content state)])
