(ns dk.simongray.elektrolyt.player
  "The player that stays as the pages change: what it plays, and when it
  tells the library how far it has come.

  The state keeps it as :player, a map of these keys:

  - :id, the episode of this tab's player, [feed-guid item-guid]
  - :media, the state of the audio element, as play-nice's media/watch!
    gives it, to the whole second
  - :seeking, the seconds that the seek bar is dragged to, while it is
  - :volume and :volume-locked?, the level of the volume slider, and
    whether the device locks the volume, as an iPhone does
  - :answer, the browser's last refusal to play, e.g. {:refused
    :not-allowed}
  - :buffering?, whether it has waited for data for half a second
  - :elsewhere, the media state that the tab that plays posted last, as
    told makes it, and :now, the time in milliseconds of the clock that
    moves its position on

  Every move goes through library/step, as podcast-clj's inspector does it,
  so that the profile keeps the position and the played marks, and syncs
  them. The audio element says where playback is now, and the profile
  where the listener left it, so the player tells the library at the
  moments that report? names, and the library writes it at once. One tab
  plays at a time, by play-nice's play lock, and only that tab tells the
  library where it is on its own. It also posts its media state to the
  other tabs. Their players show its playback and send it their commands,
  and their paused elements move to where it pauses."
  (:require [dk.simongray.play-nice.page.tabs :as tabs]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.format.chapters :as chapters]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.util.time :as time]
            [dk.simongray.elektrolyt.ui.memo :as memo]
            [dk.simongray.wary-fetch.proxy :as proxy]))

(def element-name
  "The name of the lock that keeps one player playing across the tabs."
  "dk.simongray.elektrolyt.player")

(def channel-name
  "The name of the BroadcastChannel on which the playing tab posts its
  media state, and the other tabs send it their commands."
  "dk.simongray.elektrolyt.player")

(def speeds
  "The speeds that the player offers, in quarters, as YouTube and Plyr do."
  [0.5 0.75 1 1.25 1.5 1.75 2 2.25 2.5 2.75 3])

(def speed-options
  "The speeds that the player offers, with the text of each, e.g. 1.5×."
  (for [s speeds] [s (str s "×")]))

;; podcast-clj's profile/listened asks for a report at least every 30
;; seconds, since its count of listening time takes a longer gap for a
;; pause. Each report writes the library, so the player reports at half
;; that gap rather than every few seconds. A report has to reach the outro
;; for the episode to skip it, so reaching it is a moment of its own.
(def report-seconds
  "How many seconds of playing pass between two reports of the position to
  the library."
  15)

;; One writer of the position at a time. The play lock lets one tab's
;; element play at a time, and pauses the one it takes over from, so a tab
;; reports on its own only while its element plays, or as it pauses. A
;; paused tab would report the position it loaded, whose newer stamp would
;; replace the position of the tab that plays, and count time as listened
;; that no one heard. The listener's own moves in a paused tab reach the
;; library through their actions, e.g. audio/press!.
(defn report?
  "Whether the player tells the library where it is, as the audio element
  goes from the state `old` to `new` at `now`, having last reported at
  `last`, both in milliseconds, in an episode whose outro starts at
  `outro` seconds, or nil: at a pause, and while it plays, after a seek,
  as it reaches the outro and every report-seconds."
  [old {:keys [paused seeking current-time] :as new} last now outro]
  (boolean
   (and (number? current-time)
        (or (and paused (false? (:paused old)))
            (and (not paused)
                 (or (and (:seeking old) (not seeking))
                     (and (number? outro)
                          (number? (:current-time old))
                          (< (:current-time old) outro)
                          (<= outro current-time))
                     (>= (- now (or last 0)) (* 1000 report-seconds))))))))

(def told-keys
  "The keys of an element's media state that the playing tab posts to the
  other tabs."
  [:paused :ended :current-time :duration :playback-rate :volume :muted :status])

(defn told
  "The media state that the tab `tab` posts to the others: the told-keys
  of its element's `media` state, the episode `id`, the :tab, and `now`
  in milliseconds, as the :at that tabs/position moves on from and the
  :changed that tabs/newest compares."
  [media id tab now]
  (assoc (select-keys media told-keys) :id id :tab tab :at now :changed now))

(defn heard
  "The newer at `now` of the media state `kept` of another tab and the one
  that a tab just `told` of: the told one, unless the kept one comes from
  another tab and tabs/newest prefers it, e.g. one that plays while the
  told one paused as its tab lost the play lock."
  [kept told now]
  (if (and kept
           (not= (:tab kept) (:tab told))
           (= (:tab kept) (:tab (tabs/newest [told kept] now))))
    kept
    told))

(defn elsewhere
  "The media state that the `player` keeps as :elsewhere, while that tab
  still plays at the :now of its clock, or else nil. tabs/newest counts a
  tab as gone once it's been silent for three of play-nice's heartbeats.
  It's nil while this tab's element plays too, since the tab it took the
  play lock from may still post its last state."
  [{:keys [elsewhere media now] :as player}]
  (when-not (false? (:paused media))
    (let [there (some-> elsewhere vector (tabs/newest now))]
      (when (false? (:paused there))
        there))))

(defn shown-id
  "The episode that the `player` shows: the one that another tab plays,
  or else its own."
  [player]
  (:id (or (elsewhere player) player)))

(defn shown-media
  "The media state that the `player` shows: that of the other tab while it
  plays, at the position it has reached by the :now of its clock to the
  whole second, or else this tab's element's."
  [{:keys [media now] :as player}]
  (if-let [there (elsewhere player)]
    (assoc (select-keys there told-keys)
           :current-time (some-> (tabs/position there now) Math/floor))
    media))

;; The one question of every view that offers Play or Pause, so that a
;; page's button, the player's and its status line agree while another tab
;; plays
(defn playing
  "Where the episode `id` plays, as the `player` knows it: :here,
  :elsewhere in another tab, or nil while it doesn't play."
  [{:keys [media] :as player} id]
  (when (some? id)
    (cond
      (= id (:id (elsewhere player)))
      :elsewhere

      (and (= id (:id player)) (false? (:paused media)))
      :here)))

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

(defn playback
  "What library/playback gives for the episode `id` of the `state`, with
  what the Episode page read of its files, e.g. a chapters file, and the
  artwork of its chapters through the app's proxy, or nil."
  [{:keys [library episodes] :as state} id]
  (when (and library id)
    (some-> (library/playback library id (if-let [read (get-in episodes [id :result])]
                                           {:read read}
                                           {}))
            (update :metadata with-proxied-chapters))))

(def shown-playback
  "The playback of the episode that the player of a state shows. It's made
  again only when the library's feeds or profile, the files read of the
  episode, or the episode itself change, since the page renders every
  second while it plays."
  (memo/remember-last
   (fn [state]
     (playback state (shown-id (:player state))))
   (fn [{:keys [library episodes player] :as state}]
     (let [id (shown-id player)]
       (into [(:feeds library) (:profile library) (get-in episodes [id :result])] id)))))

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
    (= :elsewhere (playing player (shown-id player)))
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
