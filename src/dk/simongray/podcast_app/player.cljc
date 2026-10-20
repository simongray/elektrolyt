(ns dk.simongray.podcast-app.player
  "The player that stays as the pages change: what it plays, and when it
  tells the library how far it has come.

  The state keeps it as :player, a map of these keys:

  - :id, the episode that plays, [feed-guid item-guid]
  - :playback, what library/playback gives for it
  - :media, the state of the audio element, as play-nice's media/watch!
    gives it, to the whole second
  - :reported, the position last reported to the library, and :saved, when
    the library was last saved, in milliseconds
  - :seeking, the seconds that the seek bar is dragged to, while it is
  - :expanded?, whether the controls show on a phone
  - :volume and :volume-locked?, the level of the volume slider, and
    whether the device locks the volume, as an iPhone does
  - :answer, the browser's last refusal to play, e.g. {:refused
    :not-allowed}
  - :buffering?, whether it has waited for data for half a second
  - :shared, the newest playback across the tabs, as play-nice's
    tabs/watch! gives it, with :shared-position, where it is by now, and
    :shared-playback, what library/playback gives for its episode

  Every move goes through library/step, as podcast-clj's inspector does it,
  so that the profile keeps the position and the played marks, and syncs
  them. The tabs share one playback, which every tab's player shows, and
  only the tab whose playback is the newest tells the library where it
  is: the one that plays, or the one where the listener last changed it
  while nothing played."
  (:require [dk.simongray.podcast.format.chapters :as chapters]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.util.time :as time]
            [dk.simongray.wary-fetch.proxy :as proxy]))

(def element-name
  "The name of the lock that keeps one player playing across the tabs."
  "dk.simongray.podcast-app.player")

(def speeds
  "The speeds that the player offers, in quarters, as YouTube and Plyr do."
  [0.5 0.75 1 1.25 1.5 1.75 2 2.25 2.5 2.75 3])

(def speed-options
  "The speeds that the player offers, with the text of each, e.g. 1.5×."
  (for [s speeds] [s (str s "×")]))

(def report-seconds
  "How many seconds of playing pass between two reports of the position to
  the library, as in podcast-clj's inspector and AntennaPod."
  5)

(def save-ms
  "How many milliseconds of playing pass between two saves of the library."
  30000)

(defn report?
  "Whether the player tells the library where it is, as the audio element
  goes from the state `old` to `new`, having last reported the position
  `reported`: at a pause, after a seek, and every report-seconds of
  playing."
  [old {:keys [paused seeking current-time] :as new} reported]
  (boolean
   (or (and paused (false? (:paused old)))
       (and (:seeking old) (not seeking))
       (and (not paused)
            (number? current-time)
            (>= (abs (- current-time (or reported -10))) report-seconds)))))

(defn save?
  "Whether the player saves the library, as the audio element goes from the
  state `old` to `new` at `now`, having last saved it at `saved`, both in
  milliseconds: at a pause, and every save-ms of playing."
  [old {:keys [paused] :as new} saved now]
  (boolean
   (or (and paused (false? (:paused old)))
       (and (not paused) (>= (- now (or saved 0)) save-ms)))))

(defn elsewhere
  "The newest playback across the tabs, in the :shared of the `player`,
  when it's another tab's, or nil."
  [{:keys [shared] :as player}]
  (when (and shared (not (:here? shared)))
    shared))

(defn playing-elsewhere?
  "Whether another tab plays the playback that the `player` shows."
  [player]
  (false? (:paused (elsewhere player))))

;; Only one writer of the position at a time, which the play lock settles
;; while an episode plays. Another tab's write of an older position would
;; take the newer one's place in the profile, by its newer stamp, and its
;; reports would count time as listened that no one heard.
(defn writes?
  "Whether the `player` of this tab tells the library where it is: unless
  another tab's playback is the newest."
  [player]
  (nil? (elsewhere player)))

(defn shown-media
  "The media state that the `player` shows: another tab's newest playback,
  at the position it has reached by now, or else its own element's."
  [{:keys [media shared-position] :as player}]
  (if-let [shared (elsewhere player)]
    (assoc (select-keys shared [:paused :ended :duration :playback-rate :volume :muted :status])
           :current-time shared-position)
    media))

;; The one question of every view that offers Play or Pause, so that a
;; page's button and the player's agree while another tab plays
(defn playing
  "The id of the episode that plays, as the `player` shows it: another
  tab's, where its playback is the newest, or else this tab's, or nil
  while it's paused."
  [player]
  (when (false? (:paused (shown-media player)))
    (if-let [shared (elsewhere player)]
      (:id shared)
      (:id player))))

(defn shown-playback
  "What library/playback gives for the episode that the `player` shows:
  another tab's, where its newest playback is of another, or else its
  own."
  [{:keys [playback shared-playback] :as player}]
  (if (and (elsewhere player) shared-playback)
    shared-playback
    playback))

(defn whole-seconds
  "The `media` state of the audio element with its position to the whole
  second, so that the page renders once a second while it plays, not at
  every timeupdate."
  [media]
  (update media :current-time #(when (number? %) (Math/floor %))))

(defn proxied
  "The URL `url` through the app's proxy, e.g. for the image of a chapter,
  which would otherwise tell its host who looks at the page."
  [url]
  (:url (proxy/proxied "/proxy" {:url url})))

;; The Podcasting 2.0 JSON chapters spec asks apps to be careful with what
;; a chapters file links to, since it can track the listener
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/examples/chapters/jsonChapters.md
(defn with-proxied-chapters
  "The `metadata` of the Media Session with the artwork of its chapters
  through the app's proxy."
  [metadata]
  (update metadata :chapter-info
          (fn [chapters]
            (mapv (fn [chapter]
                    (update chapter :artwork #(mapv (fn [image] (update image :src proxied)) %)))
                  chapters))))

(defn chapter-doc
  "The chapters document of the `playback`, or nil."
  [playback]
  (:document (:chapters playback)))

(defn chapter-at
  "The chapter of the `playback` at `seconds`, or nil."
  [playback seconds]
  (when-let [doc (chapter-doc playback)]
    (when (number? seconds)
      (chapters/at doc seconds))))

(defn skips
  "The seconds of the skip back and the skip forward that the listener
  prefers in the `profile`, as a pair."
  [profile]
  ((juxt :skip-back :skip-forward) (profile/preferences profile)))

(defn with-duration
  "The `media` state of the audio element with the duration of the
  `playback`, as the library knows it, until the element knows its own,
  e.g. after a reload, where it loads its metadata only later."
  [media playback]
  (cond-> media
    (and (not (number? (:duration media))) (number? (:duration playback)))
    (assoc :duration (:duration playback))))

(defn remaining
  "The seconds left of the `media` state of the audio element, or nil while
  its duration is unknown."
  [{:keys [current-time duration]}]
  (when (and (number? duration) (number? current-time) (time/finite? duration))
    (max 0 (- duration current-time))))

;; W3C Remote Playback API: the player shows where the media plays, while
;; it plays elsewhere
;; https://www.w3.org/TR/remote-playback/
(defn status-text
  "What the status line of the `player` says: that another tab plays, or
  why it doesn't play, or that it waits for data, or nil."
  [{:keys [media answer buffering?] :as player}]
  (cond
    (playing-elsewhere? player)
    "Playing in another tab."

    (:error media)
    ;; HTML, §4.8.11.5: a dead file and a format that the browser can't
    ;; play both end in MEDIA_ERR_SRC_NOT_SUPPORTED
    "This episode couldn't be played. The server or the network failed, or the browser can't play its format."

    (= :not-allowed (:refused answer))
    "Press Play to start."

    (= :not-supported (:refused answer))
    "This episode couldn't be played. The browser can't play its format."

    buffering?
    "Loading…"))

(defn entry
  "The entry of the episode `id` in the profile of the library `lib`, with
  its :position and whether it's :played?, or nil."
  [lib id]
  (when (and lib id)
    (profile/episode-entry (:profile lib) id)))
