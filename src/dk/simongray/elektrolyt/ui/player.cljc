(ns dk.simongray.elektrolyt.ui.player
  "The views of the player, which stays as the pages change.

  On a phone or a narrow window it's a row of the episode and what's
  next, above the seek bar between the speed and the speaker, and the
  transport below them. Where the title has room beside it, the transport
  is beside the episode, with the seek row below them. Every control is
  in view at every width, and its buttons are icons alone. The Episode
  page is the player's full view, with the notes, the chapters and the
  transcript."
  (:require [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.elektrolyt.colors :as colors]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.queue :as queue]
            [dk.simongray.elektrolyt.routes :as routes]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.controls :as controls-ui]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

;; at the size of the player on a desktop, whose height is 7rem (see
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
      (artwork/linked href (artwork/img {:artwork image} 112 (merge {:class "player-artwork"} attrs))))))

(defn now-playing
  "The title and the show of the `playback`, with its title of the chapter
  at `seconds`, whose title links to `href`, the page of its episode, and
  whose show to `show-href`, the page of its show."
  [{:keys [title show] :as playback} seconds href show-href]
  [:div.player-title
   [:p [:a {:href href} title]]
   (facts-ui/line [(when show [:a {:href show-href} show])
                   (:title (player/chapter-at playback seconds))])])

;; WAI-ARIA APG, button pattern: a button whose label changes, from Play to
;; Pause, has no aria-pressed. Play, Pause, Next and Previous are the IEC's
;; symbols for equipment, so they need no words to be understood.
;; https://en.wikipedia.org/wiki/Media_control_symbols
(defn play-button
  "The button that plays the episode that the `player` shows, or pauses
  it, wherever it plays."
  [player]
  (if (player/playing player (player/shown-id player))
    [controls-ui/icon-button {:icon icons/pause :class "player-play" :on {:click [[:player/pause]]}}
     "Pause"]
    [controls-ui/icon-button {:icon icons/play :class "player-play" :on {:click [[:player/resume]]}}
     "Play"]))

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

;; Phosphor has no skip by a number of seconds, and the listener chooses
;; theirs, so the number is text inside the arrow, in the font of the page,
;; as Apple's own symbols for it draw it, e.g. gobackward.15. Its size
;; leaves a stroke's width of air between it and the ring.
;; https://developer.apple.com/sf-symbols/
(defn skip-icon
  "The icon of a skip by `seconds` in the `direction`, :back or :forward,
  with the number inside the arrow."
  [direction seconds]
  (let [text (str seconds)]
    [controls-ui/icon {:icon (if (= :back direction)
                               icons/arrow-counter-clockwise
                               icons/arrow-clockwise)}
     [:text {:x           128
             :y           128
             :dy          "0.35em"
             :text-anchor "middle"
             :font-size   (if (< 2 (count text)) 80 108)
             :font-weight 700
             :fill        "currentColor"}
      text]]))

;; In the order of a CD player's buttons, in view at every width: the
;; previous chapter, back, Play, forward and the next chapter
(defn transport
  "The button that plays or pauses what the `player` shows, between those
  that skip back and forward by the seconds that the listener prefers,
  `back` and `forward`, and those that go to the previous and the next
  chapter of the `playback`, when it has chapters."
  [player playback back forward]
  (let [chapters? (seq (:chapters (player/chapter-doc playback)))]
    [:div#player-transport.player-transport
     (when chapters?
       [controls-ui/icon-button {:icon  icons/skip-back
                                 :class "player-previous-chapter"
                                 :on    {:click [[:player/chapter :previous]]}}
        "Previous chapter"])
     [controls-ui/icon-button {:icon  (skip-icon :back back)
                               :class "player-back"
                               :on    {:click [[:player/press {:action :seekbackward}]]}}
      (str "Back " back " seconds")]
     (play-button player)
     [controls-ui/icon-button {:icon  (skip-icon :forward forward)
                               :class "player-forward"
                               :on    {:click [[:player/press {:action :seekforward}]]}}
      (str "Forward " forward " seconds")]
     (when chapters?
       [controls-ui/icon-button {:icon  icons/skip-forward
                                 :class "player-next-chapter"
                                 :on    {:click [[:player/chapter :next]]}}
        "Next chapter"])]))

;; Its options, e.g. 1.5×, say what it is, so its label is for a screen
;; reader. It's bare text with a caret of our own, since the browser's
;; arrow, beside room for its longest option, is too wide below the
;; artwork on a phone (layout.css).
(defn speed
  "The choice of the speed of the `media` state, which sets the listener's
  preference."
  [{:keys [playback-rate] :as media}]
  [:div.player-speed
   [:label.visually-hidden {:for "player-speed"} "Speed"]
   (controls-ui/select "player-speed" player/speed-options (or playback-rate 1)
                       [:player/rate [:event/target-number]])
   [controls-ui/icon {:icon icons/caret-down}]])

(defn volume-icon
  "The icon of the sound at the `level` of the volume slider, struck out
  while it's `muted` or silent."
  [level muted]
  (cond
    (or muted (zero? level)) icons/speaker-slash
    (< level 0.5)            icons/speaker-low
    :else                    icons/speaker-high))

;; The speaker mutes on every device, and its name says what a press does,
;; Mute or Unmute, with no aria-pressed, as the APG's button pattern has
;; it. Where a mouse can rest on it, the slider rises from it, as Video.js
;; draws its volume when it's not inline: on hover, and on the keyboard's
;; focus, so that Tab reaches the slider (layout.css). An iPhone plays at
;; the system's volume, which a page can neither set nor read, so there's
;; no slider there, and on a touch screen, whose buttons set the level,
;; none shows. While muted, the slider stands at silence, at the bottom,
;; and moving it unmutes.
;; https://www.w3.org/WAI/ARIA/apg/patterns/button/
;; https://docs.videojs.com/control-bar_volume-panel.js.html
(defn volume-control
  "The mute button of the `media` state, with the slider at its `level`
  that rises from it, unless the device `locked?` the volume."
  [{:keys [muted] :as media} level locked?]
  (let [shown (if muted 0 level)]
    [:div (cond-> {:class "player-volume"}
            (not locked?) (assoc :on {:keydown      [[:hover/dismiss-on-escape]]
                                      :pointerleave [[:hover/restore-on-leave]]
                                      :focusout     [[:hover/restore-on-leave]]}))
     [controls-ui/icon-button {:icon (volume-icon (if locked? 1 level) muted)
                               :on   {:click [[:player/mute (not muted)]]}}
      (if muted "Unmute" "Mute")]
     (when-not locked?
       [:div.player-volume-slider
        [:label.visually-hidden {:for "player-volume"} "Volume"]
        [:input {:type           "range"
                 :id             "player-volume"
                 :min            0
                 :max            1
                 :step           0.05
                 :value          shown
                 :aria-valuetext (str (Math/round (* 100 shown)) "%")
                 :on             {:input (cond-> [[:player/volume [:event/target-number]]]
                                           muted (conj [:player/mute false]))}}]])]))

;; Pocket Casts, Apple Podcasts and Spotify all reach the queue from the
;; player, which here is a page of the app. At every width it's a square at
;; the player's right edge, as the artwork is at its left (layout.css):
;; "Next" above the episode's artwork, whose title only a screen reader
;; reads, in the colour that the player will take from that artwork.
(defn next-up
  "The episode that plays after the one of the player of the `state`, as a
  link to the queue with its artwork, or nil."
  [state]
  (when-let [{:keys [title] :as episode} (first (queue/up-next state))]
    (let [seed (colors/bar-seed state episode)]
      [:a.player-next (cond-> {:href (routes/href :route/queue)}
                        seed (assoc :style {:--bar-seed seed}))
       [:span.player-next-label "Next" [:span.player-next-colon ":"]]
       [:span.player-next-title " " title]
       ;; at its size on a desktop, inside the mat (layout.css)
       (artwork/img episode 68 (merge {:class "player-next-artwork"}
                                      (colors/placeholder state episode)))])))

;; Only a screen reader reads it, as it does the page's announcer: a
;; sighted listener sees the player's state in its controls, and a line
;; that came and went would move the player each time
(defn status-line
  "The line that says why the `player` doesn't play, or that it waits for
  data, which a screen reader reads as it changes."
  [player]
  [:p.visually-hidden {:role "status"} (player/status-text player)])

(defn content
  "The content of the player of the `state`: the episode that plays, in
  this tab or another, and its controls, each a part of the player's grid
  in layout.css."
  [{:keys [player library] :as state}]
  (let [{:keys [seeking volume volume-locked?]} player
        playback       (player/shown-playback state)
        media          (player/shown-media player)
        [back forward] (player/skips (:profile library))
        href           (when-let [id (:id playback)]
                         (lists-ui/episode-href library id (:title playback)))
        show-href      (when-let [[feed-guid] (:id playback)]
                         (lists-ui/show-href library feed-guid (:show playback)))]
    (list
     [:audio {:replicant/key        "player"
              :preload              "metadata"
              :replicant/on-mount   [[:player/mounted]]
              :replicant/on-unmount [[:player/unmounted]]
              :on                   {:ended [[:player/ended]]}}]
     (artwork playback (:current-time media) href
              (colors/placeholder state (colors/playing state)))
     (now-playing playback (:current-time media) href show-href)
     (transport player playback back forward)
     [:div.player-seek-row
      (speed media)
      (seek-bar (player/with-duration media playback) seeking)
      (volume-control media (or volume 1) volume-locked?)]
     (next-up state)
     (status-line player))))

;; WAI-ARIA APG, landmark regions: a section with a label is a region, the
;; landmark for content that none of the others describes. It comes with an
;; episode, rather than stand empty, so that the main content has the room
;; while nothing plays.
(defn region
  "The player of the `state`, which stays as the pages change, while it
  has an episode, in this tab or another, or else nil."
  [state]
  (when (player/shown-playback state)
    [:section#player {:aria-label "Player"}
     (content state)]))
