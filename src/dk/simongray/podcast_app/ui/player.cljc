(ns dk.simongray.podcast-app.ui.player
  "The views of the player, which stays as the pages change.

  On a phone it's one row of the episode and the button that plays it,
  with a disclosure of the other controls. Wider than that, they're all in
  view. The Episode page is the player's full view, with the notes, the
  chapters and the transcript."
  (:require [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.podcast-app.colors :as colors]
            [dk.simongray.podcast-app.player :as player]
            [dk.simongray.podcast-app.queue :as queue]
            [dk.simongray.podcast-app.routes :as routes]
            [dk.simongray.podcast-app.settings :as settings]
            [dk.simongray.podcast-app.ui.artwork :as artwork]
            [dk.simongray.podcast-app.ui.controls :as controls-ui]
            [dk.simongray.podcast-app.ui.facts :as facts-ui]
            [dk.simongray.podcast-app.ui.lists :as lists-ui]
            [dk.simongray.podcast-app.ui.time :as time-ui]))

;; at the size of the largest player, whose rows are 10rem high (see
;; layout.css)
(defn artwork
  "The artwork of the `playback`, or the image of its chapter at `seconds`,
  which takes its place, at an edge of the player, with the `attrs` merged
  in, e.g. its colour while it loads, as a link to `href`, the page of its
  episode, as its title is."
  [{:keys [artwork] :as playback} seconds href attrs]
  (let [image (or (some-> (:img (player/chapter-at playback seconds)) player/proxied)
                  artwork)]
    (when image
      (artwork/linked href (artwork/img {:artwork image} 160 (merge {:class "player-artwork"} attrs))))))

(defn now-playing
  "The title and the show of the `playback`, with its title of the chapter
  at `seconds`, whose title links to `href`, the page of its episode."
  [{:keys [title show] :as playback} seconds href]
  [:div.player-title
   [:p [:a {:href href} title]]
   (facts-ui/line [show (:title (player/chapter-at playback seconds))])])

;; WAI-ARIA APG, button pattern: a button whose label changes, from Play to
;; Pause, has no aria-pressed
(defn play-button
  "The button that plays the episode that the `player` shows, or pauses
  it, wherever it plays."
  [player]
  (if (player/playing player)
    [:button.primary {:type "button" :on {:click [[:player/pause]]}} "Pause"]
    [:button.primary {:type "button" :on {:click [[:player/resume]]}} "Play"]))

;; A range input, rather than a slider of our own, since a screen reader on
;; a phone adjusts it with a swipe, which it always follows
(defn seek-bar
  "The seek bar of the `media` state, at the `seeking` seconds while it's
  dragged, with the time so far and the time left."
  [media seeking]
  (let [shown (cond-> media seeking (assoc :current-time seeking))]
    [:div#player-seek.player-seek
     [:label.visually-hidden {:for "player-position"} "Position"]
     (time-ui/clock (:current-time shown))
     [:input (merge {:type "range"
                     :id   "player-position"
                     :on   {:input  [[:player/drag [:event/range-seek]]]
                            :change [[:player/press [:event/range-seek]]]}}
                    (controls/range-attributes shown))]
     (update (time-ui/clock (player/remaining shown)) 2 #(str "−" %))]))

(defn skip-buttons
  "The buttons that skip back and forward by the seconds that the
  listener prefers, `back` and `forward`."
  [back forward]
  [:div#player-skips.player-skips
   [:button {:type "button" :on {:click [[:player/press {:action :seekbackward}]]}}
    (str "Back " back " s")]
   [:button {:type "button" :on {:click [[:player/press {:action :seekforward}]]}}
    (str "Forward " forward " s")]])

(defn chapter-buttons
  "The buttons that go to the previous and the next chapter of the
  `playback`, when it has chapters."
  [playback]
  (when (seq (:chapters (player/chapter-doc playback)))
    (list
     [:button {:type "button" :on {:click [[:player/chapter :previous]]}} "Previous chapter"]
     [:button {:type "button" :on {:click [[:player/chapter :next]]}} "Next chapter"])))

(defn speed
  "The choice of the speed of the `media` state, which sets the listener's
  preference."
  [{:keys [playback-rate] :as media}]
  (list
   [:label {:for "player-speed"} "Speed"]
   (controls-ui/select "player-speed" player/speed-options (or playback-rate 1)
                       [:player/rate [:event/target-number]])))

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

;; Only a screen reader reads it, as it does the page's announcer: a
;; sighted listener sees the player's state in its controls, and a line
;; that came and went would move the player each time
(defn status-line
  "The line that says why the `player` doesn't play, or that it waits for
  data, which a screen reader reads as it changes."
  [player]
  [:p.visually-hidden {:role "status"} (player/status-text player)])

;; WAI-ARIA APG, disclosure pattern: a button with aria-expanded that shows
;; and hides the controls below it, which leaves the history and the focus
;; as they are. On a phone it shows the seek bar and the skips too, which a
;; desktop always shows, so it names them all.
(defn content
  "The content of the player of the `state`: the episode that plays, in
  this tab or another, and its controls, each a part of the player's grid
  in layout.css."
  [{:keys [player library] :as state}]
  (let [{:keys [seeking expanded? volume volume-locked?]} player
        playback       (player/shown-playback player)
        media          (player/shown-media player)
        [back forward] (player/skips (:profile library))
        href           (when-let [id (:id playback)]
                         (lists-ui/episode-href library id (:title playback)))]
    (list
     [:audio {:replicant/key        "player"
              :preload              "metadata"
              :replicant/on-mount   [[:player/mounted]]
              :replicant/on-unmount [[:player/unmounted]]
              :on                   {:ended [[:player/ended]]}}]
     (artwork playback (:current-time media) href
              (colors/placeholder state (colors/playing state)))
     (now-playing playback (:current-time media) href)
     (play-button player)
     (skip-buttons back forward)
     ;; as a string, since Replicant leaves out an attribute of false
     [:button.player-more {:type          "button"
                           :aria-expanded (str (boolean expanded?))
                           :aria-controls "player-seek player-skips player-controls"
                           :on            {:click [[:player/expand (not expanded?)]]}}
      "Controls"]
     (seek-bar (player/with-duration media playback) seeking)
     ;; a group named as the button that shows it, which a screen reader
     ;; names as it enters the controls
     [:div {:id         "player-controls"
            :role       "group"
            :aria-label "Controls"
            :class      (cond-> ["player-controls"] (not expanded?) (conj "collapsed"))}
      (chapter-buttons playback)
      (speed media)
      (volume-controls media (or volume 1) volume-locked?)
      (next-up state)]
     (status-line player))))

;; WAI-ARIA APG, landmark regions: a section with a label is a region, the
;; landmark for content that none of the others describes. It comes with an
;; episode, rather than stand empty, so that the main content has the room
;; while nothing plays.
(defn region
  "The player of the `state`, which stays as the pages change, while it
  has an episode, in this tab or another, or else nil."
  [{:keys [player] :as state}]
  (when (and (:id player) (player/shown-playback player))
    [:section#player {:aria-label "Player"
                      :data-size  (name (settings/player-size state))}
     (content state)]))
