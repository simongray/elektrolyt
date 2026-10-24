(ns dk.simongray.elektrolyt.player
  "The player that stays as the pages change: what it plays or shows from
  another tab, and when it reports its position to the library.

  The player is a map of:

  - :id, the episode, as [feed-guid item-guid]
  - :media, the audio element's media state, to the whole second
  - :seeking, the seconds that the seek bar is dragged to, while it's
    dragged
  - :volume, the level of the volume slider, and :volume-locked?, whether
    the device locks the volume, e.g. on an iPhone
  - :answer, the browser's last refusal to play, e.g. {:refused
    :not-allowed}
  - :buffering?, whether it has waited for data for half a second
  - :elsewhere, the media state that the playing tab posted last, and
    :now, the time in milliseconds of the clock that moves its position on"
  (:require [dk.simongray.play-nice.page.tabs.playback :as playback]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.format.chapters :as chapters]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.util.time :as time]
            [dk.simongray.elektrolyt.ui.memo :as memo]
            [dk.simongray.wary-fetch.proxy :as proxy]
            [dk.simongray.wary-fetch.url :as url]))

(def element-name
  "The name of the lock that lets one tab's player play at a time."
  "dk.simongray.elektrolyt.player")

(def channel-name
  "The name of the BroadcastChannel on which the playing tab posts its
  media state and the other tabs send their commands."
  "dk.simongray.elektrolyt.player")

(def speeds
  "The speeds that the player offers, in quarter steps like YouTube's and
  Plyr's."
  [0.5 0.75 1 1.25 1.5 1.75 2 2.25 2.5 2.75 3])

(defn speed-options
  "The speeds that the player offers, each with its label, e.g. 1.5×, with
  the listener's `current` one among them, e.g. one that another app set."
  [current]
  (for [s (sort (distinct (cond-> speeds (and (number? current) (pos? current)) (conj current))))]
    [s (str s "×")]))

;; The profile can count a gap of more than 30 seconds between two reports
;; as a pause, and each report writes the library, so the player reports
;; at half that gap rather than every few seconds
(def report-seconds
  "How many seconds of playing pass between two reports of the position to
  the library."
  15)

;; One tab writes the position at a time. The play lock lets one element
;; play and pauses the one it takes over from, so a tab reports on its own
;; only while its element plays or as it pauses. A paused tab would report
;; the position it loaded, and its newer stamp would replace the playing
;; tab's position and count time that nobody heard as listened. The
;; episode ends at its outro only when a report reaches it, so reaching
;; the outro is a moment of its own.
(defn report?
  "Whether the player reports its position as the media state goes from
  `old` to `new` at `now`, given its `last` report, both in milliseconds,
  and the start of the `outro` in seconds, if any.

  It reports at a pause and, while the element plays, after a seek, on
  reaching the outro and every report-seconds, or at once when the clock
  went back past the last report."
  [old {:keys [paused seeking current-time] :as new} last now outro]
  (let [since (- now (or last 0))]
    (boolean
     (and (number? current-time)
          (or (and paused (false? (:paused old)))
              (and (not paused)
                   (or (and (:seeking old) (not seeking))
                       (and (number? outro)
                            (number? (:current-time old))
                            (< (:current-time old) outro)
                            (<= outro current-time))
                       (neg? since)
                       (>= since (* 1000 report-seconds)))))))))

(def told-keys
  "The keys of an element's media state that the playing tab posts to the
  other tabs."
  [:paused :ended :current-time :duration :playback-rate :volume :muted :status])

;; HTML leaves an element that failed unpaused, e.g. at a dead file or a
;; network failure, but it doesn't play
(defn told
  "The media state that the tab `tab` posts to the others: the told-keys
  of its element's `media` state, with the episode `id`, the tab, and
  `now` in milliseconds as both :at and :changed. An element that failed
  counts as paused."
  [media id tab now]
  (cond-> (assoc (select-keys media told-keys) :id id :tab tab :at now :changed now)
    (:error media) (assoc :paused true)))

(defn heard
  "The newer at `now` of the media state `kept` from another tab and the
  one just `told`. That's the told one, unless the kept one is from a
  different tab and counts as newer, e.g. one that plays while the told
  one paused because its tab lost the play lock."
  [kept told now]
  (if (and kept
           (not= (:tab kept) (:tab told))
           (= (:tab kept) (:tab (playback/newest [told kept] now))))
    kept
    told))

(defn elsewhere
  "The other tab's media state in the `player` while that tab still plays
  at the player's :now, or else nil, e.g. once that tab falls silent. It's
  nil while this tab's element plays too, since the tab it took the play
  lock from may still post its last state."
  [{:keys [elsewhere media now] :as player}]
  (when-not (false? (:paused media))
    (let [there (some-> elsewhere vector (playback/newest now))]
      (when (false? (:paused there))
        there))))

(defn shown-id
  "The id of the episode that the `player` shows: the one that another tab
  plays, or else its own."
  [player]
  (:id (or (elsewhere player) player)))

(defn shown-media
  "The media state that the `player` shows: the other tab's while it
  plays, moved on to the player's :now and to the whole second, or else
  this tab's element's."
  [{:keys [media now] :as player}]
  (if-let [there (elsewhere player)]
    (assoc (select-keys there told-keys)
           :current-time (some-> (playback/position there now) Math/floor))
    media))

;; Every Play or Pause button uses this, so that a page's button, the
;; player's and its status line agree while another tab plays. An element
;; that failed doesn't play, though HTML leaves it unpaused.
(defn playing
  "Where the `player` has the episode `id` playing: :here, :elsewhere in
  another tab, or nil."
  [{:keys [media] :as player} id]
  (when (some? id)
    (cond
      (= id (:id (elsewhere player)))
      :elsewhere

      (and (= id (:id player)) (false? (:paused media)) (not (:error media)))
      :here)))

(defn whole-seconds
  "The `media` state with its position to the whole second, so that the
  page renders once a second while it plays rather than at every
  timeupdate."
  [media]
  (update media :current-time #(when (number? %) (Math/floor %))))

(defn proxied
  "The `url` through the app's proxy, e.g. for a chapter's image, so that
  its site can't see who looks at the page. A data URL needs no fetch, so
  it stays as it is. For any other URL that isn't http or https, it
  returns nil."
  [url]
  (cond
    (url/web-url? url)          (:url (proxy/proxied "/proxy" {:url url}))
    (= "data" (url/scheme url)) url))

;; Podcasting 2.0 JSON chapters: what a chapters file links to can track
;; the listener
;; https://github.com/Podcastindex-org/podcast-namespace/blob/main/docs/examples/chapters/jsonChapters.md
(defn with-proxied-chapters
  "The Media Session `metadata` with its chapters' artwork through the
  app's proxy."
  [metadata]
  (update metadata :chapter-info
          (fn [chapters]
            (mapv (fn [chapter]
                    (update chapter :artwork #(mapv (fn [image] (update image :src proxied)) %)))
                  chapters))))

(defn playback-opts
  "The options that library/playback takes for the episode `id` in
  `state`: what's been read of its files, e.g. a chapters file, or nil."
  [{:keys [episodes] :as state} id]
  {:read (get-in episodes [id :result])})

(defn playback
  "The playback of the episode `id` in `state`, with what's been read of
  its files, e.g. a chapters file, and its chapters' artwork through the
  app's proxy, or nil."
  [{:keys [library] :as state} id]
  (when (and library id)
    (some-> (library/playback library id (playback-opts state id))
            (update :metadata with-proxied-chapters))))

;; A start at the end or past it, e.g. from a link's t, would only end the
;; episode and mark it played
(defn start-time
  "The seconds `t` to start an episode of `duration` seconds at, or nil
  when it's at or past the duration, if that's known."
  [duration t]
  (when-not (and (number? t) (number? duration) (<= duration t))
    t))

(defn moved-on?
  "Whether the `effects` of a library step stop the episode that played or
  play another."
  [effects]
  (boolean (some (comp #{:stop :play} first) effects)))

(def shown-playback
  "The playback of the episode that the player in a state shows. It's made
  again only when the episode, what's read of its files, or the library's
  feeds or profile change, since the page renders every second while it
  plays."
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
  "The seconds to skip back and forward, as a pair, by the listener's
  preferences in `profile`."
  [profile]
  ((juxt :skip-back :skip-forward) (profile/preferences profile)))

(defn with-duration
  "The `media` state with the duration of the `playback` until the element
  has its own, e.g. after a reload, before it loads its metadata."
  [media playback]
  (cond-> media
    (and (not (number? (:duration media))) (number? (:duration playback)))
    (assoc :duration (:duration playback))))

(defn remaining
  "The seconds left in the `media` state, or nil while the duration is
  unknown."
  [{:keys [current-time duration]}]
  (when (and (time/finite? duration) (number? current-time))
    (max 0 (- duration current-time))))

;; W3C Remote Playback API: while the media plays elsewhere, the player
;; shows where
;; https://www.w3.org/TR/remote-playback/
(defn status-text
  "The text of the status line of the `player`: that another tab plays,
  why it doesn't play or that it waits for data, or nil."
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
  its :position and :played?, or nil."
  [lib id]
  (when (and lib id)
    (profile/episode-entry (:profile lib) id)))
