(ns dk.simongray.elektrolyt.player.ui
  "The views of the player, which stays as the pages change. The Episode
  page is the player's full view, with the notes, the chapters and the
  transcript."
  (:require [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.podcast.util.time :as time]
            [dk.simongray.elektrolyt.library.queue :as queue]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]
            [dk.simongray.elektrolyt.ui.colors :as colors]
            [dk.simongray.elektrolyt.ui.controls :as controls-ui]
            [dk.simongray.elektrolyt.ui.facts :as facts-ui]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.lists :as lists-ui]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

;; at the player's height on a desktop, 7rem (layout.css)
(defn artwork
  "The artwork of the `playback`, or the image of its chapter at `seconds`,
  as a link to its episode's page at `href`, with the `attrs` merged in,
  e.g. its colour while it loads."
  [{:keys [artwork title] :as playback} seconds href attrs]
  (let [image (or (some-> (:img (player/chapter-at playback seconds)) player/proxied)
                  artwork)]
    (when image
      (artwork/linked href title (artwork/img {:artwork image} 112 (merge {:class "player-artwork"} attrs))))))

;; Each line is cut short where it doesn't fit (layout.css), so each part
;; has its whole text as its tooltip. A screen reader doesn't read that
;; twice, since a link's title is its name and a span's isn't read at all
;; (in Chrome's accessibility tree).
(defn now-playing
  "The title and show of the `playback`, linked to its episode's page at
  `href` and its show's at `show-href`, with the title of its chapter at
  `seconds`."
  [{:keys [title show] :as playback} seconds href show-href]
  (let [chapter (:title (player/chapter-at playback seconds))
        label   (or title "Untitled episode")]
    [:div.player-title
     [:p [:a {:href href :title label} label]]
     (facts-ui/line [(when show [:a {:href show-href :title show} show])
                     (when chapter [:span {:title chapter} chapter])])]))

;; WAI-ARIA APG, button pattern: a button whose label changes from Play to
;; Pause has no aria-pressed. Play, Pause, Next and Previous are the IEC's
;; symbols for equipment, so they need no words.
;; https://en.wikipedia.org/wiki/Media_control_symbols
(defn play-button
  "The button that plays the episode that the `player` shows, or pauses
  it, wherever it plays. While it waits for data, Pause is marked
  data-waiting, which draws a ring around it (look.css)."
  [player]
  (if (player/playing player (player/shown-id player))
    [controls-ui/icon-button {:icon         icons/pause
                              :class        "player-play"
                              :data-waiting (player/waiting? player (player/shown-id player))
                              :on           {:click [[:player/pause]]}}
     "Pause"]
    [controls-ui/icon-button {:icon icons/play :class "player-play" :on {:click [[:player/resume]]}}
     "Play"]))

(defn fraction
  "The `seconds` as a fraction of the `duration`, or nil without a finite
  one, e.g. a live stream's. It's a string for a CSS property, since
  Replicant adds px to a number."
  [seconds duration]
  (when (and (time/finite? duration) (pos? duration))
    (str (double (/ (or seconds 0) duration)))))

;; The marks are for a pointer alone, since the chapters' buttons and the
;; Episode page's list of chapters serve a keyboard and a screen reader,
;; which WCAG allows of a small target with an equivalent. On a touch
;; screen they only show where the chapters are, like YouTube's on a
;; phone. A mark under the thumb shrinks away, so that it neither crosses
;; the thumb nor keeps it from being dragged (layout.css).
;; https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html
;; https://mobilesyrup.com/2020/05/28/youtube-video-chapters/
(defn chapter-marks
  "Marks on the seek bar where the `chapters` after the first begin, or
  nil while the `media` state has no duration. A click on a mark goes to
  its chapter, whose title is its tooltip."
  [{:keys [duration]} chapters]
  (when (fraction 0 duration)
    [:div.player-chapter-marks {:aria-hidden "true"}
     (for [{:keys [start-time title]} chapters
           :when (< 0 start-time duration)]
       [:span.player-chapter-mark
        {:replicant/key start-time
         :title         title
         :style         {:--at (fraction start-time duration)}
         :on            {:click [[:player/press {:action :seekto :seek-time start-time}]]}}])]))

;; A range input rather than a slider of our own, since a screen reader on
;; a phone can always adjust it with a swipe. Its value text names the
;; chapter too. The bar draws the part that has played (look.css), and
;; both times are as wide as the duration, so that the bar between them
;; never moves.
(defn seek-bar
  "The seek bar of the `playback` at the position in its `media` state, or
  at the `seeking` seconds while it's dragged, with its chapters' marks
  between the time played and the time left."
  [media seeking playback]
  (let [{:keys [current-time duration] :as shown} (cond-> media seeking (assoc :current-time seeking))
        attrs   (controls/range-attributes shown)
        chapter (:title (player/chapter-at playback current-time))
        played  (fraction current-time duration)
        width   (when played
                  (str (inc (count (time-ui/clock-text duration))) "ch"))]
    [:div.player-seek (when width {:style {:--clock width}})
     [:label.visually-hidden {:for "player-position"} "Position"]
     (time-ui/clock current-time)
     [:div.player-bar (when played {:style {:--played played}})
      ;; a drag that ends where it began fires no change
      [:input (cond-> (merge {:type "range"
                              :id   "player-position"
                              :on   {:input         [[:player/drag [:event/range-seek]]]
                                     :change        [[:player/press [:event/range-seek]]]
                                     :pointerup     [[:player/released]]
                                     :pointercancel [[:player/released]]}}
                             attrs)
                chapter (update :aria-valuetext str ", " chapter))]
      (chapter-marks shown (:chapters (player/chapter-doc playback)))]
     (update (time-ui/clock (player/remaining shown)) 2 #(str "−" %))]))

;; Play sits between back and forward, like in every podcast player, where
;; a thumb reaches it best on a phone. The speed comes before them and the
;; speaker after, like in Apple Podcasts on a Mac. The row follows the
;; episode, since it has the controls used most. A skip is a circular
;; arrow, as in other podcast players, but without their number of
;; seconds inside it, which the button's tooltip and name give. Rewind and
;; fast-forward would read as the next track, as in Apple Music.
;; https://support.apple.com/guide/podcasts/pod970198c2
;; https://www.uxmatters.com/mt/archives/2013/02/how-do-users-really-hold-mobile-devices.php
(defn transport
  "The row of the `player`'s play button between the buttons that skip
  `back` and `forward` seconds, with the `speed` before them and the
  `volume` after."
  [player back forward speed volume]
  [:div.player-transport
   speed
   [controls-ui/icon-button {:icon  icons/arrow-counter-clockwise
                             :class "player-back"
                             :on    {:click [[:player/press {:action :seekbackward}]]}}
    (str "Back " back " seconds")]
   (play-button player)
   [controls-ui/icon-button {:icon  icons/arrow-clockwise
                             :class "player-forward"
                             :on    {:click [[:player/press {:action :seekforward}]]}}
    (str "Forward " forward " seconds")]
   volume])

;; At the ends of the seek bar, since a chapter is a place on it, rather
;; than at the ends of the transport, where a music player skips a track
;; and the listener might take them for the next episode. Pocket Casts
;; keeps them by the chapter too.
;; https://support.pocketcasts.com/knowledge-base/chapters/
;; https://forum.antennapod.org/t/player-screen-ux-ui-work/6177?page=2
(defn chapter-button
  "The button that goes to the chapter of the `playback` in the
  `direction`, :previous or :next, or nil for a playback without
  chapters."
  [playback direction]
  (when (seq (:chapters (player/chapter-doc playback)))
    (let [[icon class words] (case direction
                               :previous [icons/skip-back "player-previous-chapter" "Previous chapter"]
                               :next     [icons/skip-forward "player-next-chapter" "Next chapter"])]
      [controls-ui/icon-button {:icon icon :class class :on {:click [[:player/chapter direction]]}}
       words])))

;; Its options, e.g. 1.5×, make clear what it is, so its title alone names
;; it and is its tooltip too: Chrome would read a title beside a label
;; again after the label. It's bare text with a caret of our own, since the
;; browser's arrow, beside room for the longest option, is too wide below
;; the artwork on a phone (layout.css).
;; https://www.w3.org/WAI/WCAG22/Techniques/html/H65
(defn speed
  "The choice of speed, at the rate in the `media` state, which sets the
  listener's preference."
  [{:keys [playback-rate] :as media}]
  [:div.player-speed
   (controls-ui/select "player-speed" (player/speed-options playback-rate) (or playback-rate 1)
                       [:player/rate [:event/target-number]]
                       {:title "Speed"})
   [controls-ui/icon {:icon icons/caret-down}]])

(defn volume-icon
  "The speaker icon for the volume `level`, struck out while it's `muted`
  or silent."
  [level muted]
  (cond
    (or muted (zero? level)) icons/speaker-slash
    (< level 0.5)            icons/speaker-low
    :else                    icons/speaker-high))

;; The speaker mutes on every device. Its label is what a press does, Mute
;; or Unmute, with no aria-pressed, by the APG's button pattern. Where a
;; mouse can rest on it, the slider rises from it on hover and on focus,
;; like Video.js's volume when it's not inline, so that Tab reaches the
;; slider (layout.css). An iPhone plays at the system's volume, which a
;; page can neither set nor read, so there's no slider there, and a touch
;; screen, whose buttons set the level, shows none. While muted, the
;; slider stands at silence, and moving it unmutes.
;; https://www.w3.org/WAI/ARIA/apg/patterns/button/
;; https://docs.videojs.com/control-bar_volume-panel.js.html
(defn volume-control
  "The mute button of the `media` state, with a slider at the volume
  `level` unless the device `locked?` the volume."
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
;; player. The episode's title is for a screen reader alone, and the link
;; has the colour that the player will take from its artwork.
(defn next-up
  "The episode that plays next in `state`, as a link to the queue with its
  artwork, or nil."
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

;; For a screen reader alone, like the page's announcer: the controls show
;; the player's state, and a line that came and went would move the player
;; each time
(defn status-line
  "The status of the `player` as a line that a screen reader reads as it
  changes."
  [player]
  [:p.visually-hidden {:role "status"} (player/status-text player)])

(defn content
  "The content of the player in `state`: the episode that plays, in this
  tab or another, and its controls."
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
     (transport player back forward
                (speed media)
                (volume-control media (or volume 1) volume-locked?))
     [:div.player-seek-row
      (chapter-button playback :previous)
      (seek-bar (player/with-duration media playback) seeking playback)
      (chapter-button playback :next)]
     (next-up state)
     (status-line player))))

;; WAI-ARIA APG, landmark regions: a labelled section is a region, the
;; landmark for content that no other landmark fits. It's left out without
;; an episode, so that the main content has the room while nothing plays.
(defn region
  "The player in `state` while it has an episode, in this tab or another,
  or else nil."
  [state]
  (when (player/shown-playback state)
    [:section#player {:aria-label "Player"}
     (content state)]))
