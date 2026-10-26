(ns dk.simongray.elektrolyt.player.audio
  "The player's effects on its audio element, with play-nice: playing an
  episode, reporting each move to the library, keeping the lock screen in
  step and sharing the playback with the other tabs.

  The element is the same for every episode, since a phone's browser lets
  a page play an element that the listener started. That way the next
  episode in the queue plays in it too, and the lock screen keeps its
  session.

  One tab's element plays at a time. That tab posts its media state to the
  other tabs and runs the commands they send. When it pauses, the others
  move their paused elements to where it stopped, so that Play in any tab
  goes on from there."
  (:require [clojure.string :as str]
            [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.page.tabs.playback :as playback]
            [dk.simongray.play-nice.session :as session]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.app.media-session :as media-session]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.library.options :as library-options]
            [dk.simongray.elektrolyt.library.picks :as picks]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.runtime.local :as local]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.ui.memo :as memo]
            [taoensso.telemere :as tel]))

(defn element
  "The audio element of the player, or nil while the player isn't in the
  page."
  []
  (js/document.querySelector "#player audio"))

(defn playing-element
  "The audio element of the player while it plays, or nil."
  []
  (let [el (element)]
    (when (and el (not (.-paused el)))
      el)))

(def volume-key
  "The localStorage key of the volume slider's level, since the volume
  belongs to the device rather than the listener's profile."
  "dk.simongray.elektrolyt.volume")

(defonce ^{:doc "This tab's id on the player's channel."}
  tab
  (str (random-uuid)))

(defn post!
  "Send the `message` to the other tabs on the player's channel."
  [message]
  (runtime/post! player/channel-name message))

(defn tell!
  "Post the `media` state of this tab's element to the other tabs, with
  the episode of the player in `store`."
  [store media]
  (post! {:type  :told
          :state (player/told media (get-in @store [:player :id]) tab (js/Date.now))}))

(declare act! autoplay! press! play-id! resume!)

;; An earlier step can load another episode, e.g. at the outro. A seek
;; meant for the episode before mustn't move it.
(defn apply-effects!
  "Carry out the `effects` of a library step on the player in `store`."
  [store effects]
  (doseq [[kind id position] effects]
    (case kind
      :seek         (when (= id (get-in @store [:player :id]))
                      (some-> (element) (media/seek! position)))
      :stop         (some-> (element) media/pause!)
      :play         (play-id! store id nil)
      :played       nil
      :nothing-next (autoplay! store id))))

(defn step!
  "Run the player `event` through a step of the library in `store`, save
  the library, and carry out and return the step's effects."
  [store event]
  (let [{:keys [effects]} (app-library/change! store {:operation [:player/step event]})]
    (app-library/save! store)
    (apply-effects! store effects)
    effects))

(defn steps!
  "Run the player `events` through steps of the library in `store` in
  turn. Stop once a step stops the episode or plays another, since the
  rest were meant for the episode that played."
  [store events]
  (reduce (fn [_ event]
            (let [effects (step! store event)]
              (cond-> effects (player/moved-on? effects) reduced)))
          nil
          events))

;; A library step plays what's next in the queue, or in the show with
;; Continuous play, and stops at the end of a sleep timer, so a pick plays
;; only when the step has nothing to play next
(defn autoplay!
  "Play the first pick after the episode `id` in the player in `store`, if
  the listener turned Autoplay on."
  [store id]
  (when-let [pick (picks/autoplayed @store id)]
    (step! store [:play pick])
    (play-id! store pick nil)))

(def moves
  "The actions of the lock screen and the keys that move within the
  episode or to another. They go through the library."
  [:seekbackward :seekforward :seekto :previoustrack :nexttrack])

(defn lock-screen-handlers
  "The handlers of the lock screen's actions for `store`: play, pause and
  stop on the `element` itself, and the moves through the library."
  [store element]
  (merge (select-keys (session/element-actions element) [:play :pause :stop])
         (zipmap moves (repeat #(press! store %)))))

;; A play/pause key asks for play while this tab's element is paused, as
;; it is while another tab plays. In that case it pauses the other tab,
;; like the player's button.
(defn play-key!
  "Pause the playback that the player in `store` shows in another tab, or
  else resume it here."
  [store]
  (if (player/elsewhere (:player @store))
    (act! store [:pause])
    (resume! store)))

(defn key-handlers
  "The handlers of the keys pressed in the player in `store`, which act on
  the playback that it shows, in this tab or another, as its buttons do."
  [store]
  (merge {:play  (fn [_] (play-key! store))
          :pause (fn [_] (act! store [:pause]))
          :stop  (fn [_] (act! store [:pause]))}
         (zipmap moves (repeat #(act! store [:press %])))))

(defn lock-screen!
  "Show the episode of the player in `store` on the lock screen, with the
  buttons that apply to it."
  [store]
  (let [{:keys [library player] :as state} @store
        id                                 (:id player)]
    (when-let [el (and id (element))]
      (runtime/lock-screen! (:metadata (player/playback state id))
                            (select-keys (lock-screen-handlers store el)
                                         (concat [:play :pause :stop]
                                                 (media-session/actions library id
                                                                        (player/playback-opts state id))))))))

(def track-buttons
  "The listener's choice of what the lock screen's Next and Previous do in
  `profile`, worked out again only when the profile changes."
  (memo/remember-last #(:track-buttons (profile/preferences %))))

(defn lock-screen-key
  "What the lock screen shows of the player in `state` comes from: its
  episode, the listener's choice of track buttons, what the episode's files
  say, and the entry of its show's feed in the library."
  [{:keys [player library episodes]}]
  (let [id (:id player)]
    [id
     (some-> (:profile library) track-buttons)
     (get-in episodes [id :result])
     (get-in library [:feeds (first id)])]))

;; Media Session, setActionHandler: each call sets a handler again, which
;; can redraw a platform's media notification, so the lock screen is set
;; only when what it shows changes, e.g. the listener's choice of track
;; buttons in this tab or another
;; https://w3c.github.io/mediasession/#dom-mediasession-setactionhandler
(defn lock-screen-watch
  "A watch of the state in `store` that shows its player's episode on the
  lock screen again when what it comes from changes."
  [store]
  (let [shown (atom nil)]
    (fn [_ state]
      (let [k (lock-screen-key state)]
        (when-not (and @shown (every? true? (map memo/same? k @shown)))
          (reset! shown k)
          (lock-screen! store))))))

;; User Timing, so that DevTools shows each stage of a start of playback in
;; its Performance panel
;; https://w3c.github.io/user-timing/
(defonce ^{:doc "The start of playback that the listener asked for last,
  until the element plays: a map of its :trigger, e.g. :play, and the time of
  each of its stages by name, from performance.now, or nil."}
  start
  (atom nil))

(defn asked!
  "Note that the listener asked for playback with the `trigger`: :play for
  an episode's Play, :resume for the player's, :ended for what's next and
  :press for the lock screen's next or previous."
  [trigger]
  (js/performance.mark "player/asked")
  (reset! start {:trigger trigger :marks {:asked (js/performance.now)}}))

(defn marked!
  "Note the `stage` of the start of playback under way, unless there's
  none or it has that stage already."
  [stage]
  (when (and @start (nil? (get-in @start [:marks stage])))
    (js/performance.mark (str "player/" (name stage)))
    (swap! start assoc-in [:marks stage] (js/performance.now))))

;; HTML, §4.8.11.7: loadedmetadata, canplay and playing fire as the ready
;; state reaches theirs
(defn stages
  "The stages of a start of playback that the element's `media` state
  shows: loadstart while it loads, loadedmetadata and canplay by its ready
  state, and playing once it plays with the data to go on. The stages of
  loading count only once the element has a new `source?`."
  [{:keys [network-state ready-state paused]} source?]
  (let [ready (.indexOf media/ready-states ready-state)]
    (cond-> []
      (and source? (= :loading network-state)) (conj :loadstart)
      (and source? (<= 1 ready))               (conj :loadedmetadata)
      (and source? (<= 3 ready))               (conj :canplay)
      (and (false? paused) (<= 3 ready))       (conj :playing))))

(defn proxied-bytes
  "The bytes that the reads through the proxy transferred from `since`, in
  the time of performance.now."
  [since]
  (->> (js/performance.getEntriesByType "resource")
       (filter #(and (<= since (.-startTime %))
                     (str/includes? (.-name %) "/proxy?")))
       (map #(.-transferSize %))
       (reduce + 0)))

(defn played!
  "Log how long the start of playback took to each stage, and forget it."
  []
  (let [{:keys [trigger marks]} @start
        asked                   (:asked marks)]
    (reset! start nil)
    (tel/log! {:level :info :id :player/started
               :data  {:trigger       trigger
                       :ms            (update-vals (dissoc marks :asked)
                                                   #(js/Math.round (- % asked)))
                       :visibility    (some-> (.-document js/globalThis)
                                              .-visibilityState)
                       :proxied-bytes (proxied-bytes asked)}}
              "The episode started playing")))

(defn staged!
  "Mark the stages of the start of playback under way that the element's
  `media` state shows, and log the start once the element plays."
  [media]
  (when-let [{:keys [marks]} @start]
    (run! marked! (stages media (contains? marks :source)))
    (when (get-in @start [:marks :playing])
      (played!))))

(defn moved!
  "Post the state of the paused `element` and the episode in `store` to
  the other tabs after the listener moves it, since a paused element's
  changes aren't posted otherwise."
  [store ^js element]
  (when (.-paused element)
    (tell! store (media/state element))))

(defn press!
  "Carry out the Media Session action in `details`, e.g. {:action
  :seekforward}, on the player in `store` through the library: report
  where the element is, then move, unless the report ended the episode,
  e.g. in its outro."
  [store details]
  (let [{:keys [library player] :as state} @store
        id                                 (:id player)]
    (when-let [el (and id (element))]
      (when (#{:previoustrack :nexttrack} (:action details))
        (asked! :press))
      (swap! store assoc-in [:player :seeking] nil)
      (steps! store (media-session/events library id (.-currentTime el) details
                                          (player/playback-opts state id)))
      (moved! store el))))

(defn media-of
  "The position and duration of the audio `element`, keyed as in its media
  state."
  [^js element]
  {:current-time (.-currentTime element) :duration (.-duration element)})

(defn report!
  "Report the position in the element's `media` state to the library in
  `store`, and save it."
  [store {:keys [current-time duration] :as media}]
  (when-let [id (and (number? current-time) (get-in @store [:player :id]))]
    (let [duration (when (js/Number.isFinite duration)
                     duration)]
      (step! store [:progress id current-time duration]))))

;; The next file starts through its host's tracking prefixes, and curl took
;; 0.2-0.7 s less to its first byte over open connections, e.g. 0.70 s
;; rather than 1.38 for Omny's
(defn warm-next!
  "Open the connections to the file of the episode that plays after the
  episode `id` in `store`, when it's on the web."
  [store id]
  (let [state @store]
    (some->> (picks/played-next state id)
             (player/playback state)
             player/warm-url
             runtime/warm-chain!)))

(defn reporter
  "A function that takes each media state of the audio element and reports
  the position to the library in `store` when a report is due, and opens
  the connections to the next episode's file near the end. It keeps the
  last state, the time and episode of its last report, and the episode
  that it warmed the next of, to itself."
  [store]
  (let [seen (atom {})]
    (fn [media]
      (let [shown                       (player/whole-seconds media)
            now                         (js/Date.now)
            {:keys [library player]}    @store
            id                          (:id player)
            {old :media last :at :as s} @seen
            outro                       (some-> (:profile library)
                                                (profile/outro-start (first id) id))]
        (when (and id (not= id (:warmed s)) (player/warm? shown outro))
          (swap! seen assoc :warmed id)
          (warm-next! store id))
        (if (player/report? old shown (when (= id (:id s)) last) now outro)
          (do (swap! seen assoc :media shown :at now :id id)
              (report! store shown))
          (swap! seen assoc :media shown))))))

(defn buffering!
  "Show that the player in `store` waits for data once its `media` state
  has been buffering for half a second during playback, so that a short
  wait shows nothing. Media Chrome waits as long."
  [store {:keys [status paused] :as media}]
  (if (and (= :buffering status) (not paused))
    (when-not (runtime/waiting? ::buffering)
      (runtime/later! ::buffering 500
                      #(swap! store assoc-in [:player :buffering?] true)))
    (do (runtime/cancel! ::buffering)
        (when (get-in @store [:player :buffering?])
          (swap! store assoc-in [:player :buffering?] false)))))

(declare read-library-files!)

(defn changed!
  "Keep the element's `media` state in the player in `store`, to the whole
  second, and show any wait for data. While the element plays, and as it
  pauses, post each change to the other tabs. Once it plays with the data
  to go on, read the episode's other files, if that's due."
  [store media]
  (let [old   (get-in @store [:player :media])
        shown (player/whole-seconds media)]
    (staged! media)
    (when (not= shown old)
      (swap! store assoc-in [:player :media] shown)
      (when (or (false? (:paused shown)) (false? (:paused old)))
        (tell! store media)))
    (buffering! store shown)
    (when (player/steady? (:player @store))
      (read-library-files! store (get-in @store [:player :id])))))

(defn answered!
  "Keep the browser's refusal to play in the player in `store`, or clear it
  when the `answer` isn't one."
  [store answer]
  (swap! store assoc-in [:player :answer] (when (:refused answer) answer)))

(defn reload!
  "Load the file of the audio `element` again where it is, at its rate."
  [^js element]
  (media/source! element {:src           (.-src element)
                          :position      (.-currentTime element)
                          :playback-rate (.-playbackRate element)}))

;; HTML, §4.8.11.8: play doesn't load a file that failed again. It even
;; refuses a file that the browser couldn't play, which includes a dead one.
(defn play-element!
  "Play the audio `element`, and keep the browser's answer in the player in
  `store`. After a failure, e.g. of the network, it loads the file again
  first."
  [store ^js element]
  (when (.-error element)
    (reload! element))
  (.then (media/play! element) #(answered! store %)))

(def reread-ms
  "How long a read of an episode's files that failed stands before the
  files are read again, e.g. once the device is back online."
  60000)

(defn due?
  "Whether to read an episode's files at `now` by its `episode` entry: when
  they were never read, or when a read that failed, wholly or in part,
  began at least reread-ms before."
  [{:keys [status at error result] :as episode} now]
  (boolean (or (nil? status)
               (and (= :read status)
                    (or error (some :error (vals result)))
                    (<= reread-ms (- now at))))))

(defn read-files!
  "Read the files of the episode `id` into the :episodes of `store` when
  that's due, with `read`, a function that returns a promise of what they
  hold, e.g. chapters, or nil when there's nothing to read. Update the
  lock screen if the player has the episode."
  [store id read]
  (when-let [reading (and (due? (get-in @store [:episodes id]) (js/Date.now)) (read))]
    (swap! store update-in [:episodes id] assoc :status :reading :at (js/Date.now))
    (-> reading
        (.then (fn [result]
                 (swap! store update-in [:episodes id]
                        #(-> % (assoc :status :read :result result) (dissoc :error)))))
        (.catch #(swap! store update-in [:episodes id] assoc :status :read
                        :error {:message (ex-message %)})))))

(defn read-library-files!
  "Read the files of the library's episode `id` in `store`, except the
  transcript, which waits for the listener."
  [store id]
  (read-files! store id #(library/read-files! (:library @store) id
                                              (assoc library-options/options :except #{:transcript}))))

;; An MP3's ID3 tag can hold megabytes, e.g. a cover or a PRIV frame, which
;; the element reads too as it opens the file, so the read of the tag waits
;; until the episode plays with the data to go on (changed!), or this long
;; when it doesn't play
(def files-wait-ms
  "How long after an episode is loaded its files are read at the latest,
  once the page is idle, when it doesn't play meanwhile."
  5000)

(defn read-soon!
  "Read the files of the library's episode `id` in `store` once the page
  is idle, files-wait-ms from now, unless playing it reads them first."
  [store id]
  (runtime/later! ::files files-wait-ms #(runtime/idle! 2000 (fn [] (read-library-files! store id)))))

;; A new source pauses the element without a pause event, so the reporter
;; misses the last position. A paused element has reported its position
;; already, or holds a stale one, e.g. while another tab plays.
(defn load!
  "Load the library's episode `id` into the player in `store`, at `t`
  seconds if given or else where the listener left it, and return the
  element, or nil when the library has no such episode, e.g. one that
  another tab plays and this tab's library doesn't have yet. First it
  reports where the element got to in the episode that plays."
  [store id t]
  (when-let [pb (player/playback @store id)]
    (some->> (playing-element) media-of (report! store))
    (swap! store update :player assoc :id id :answer nil :seeking nil)
    (read-soon! store id)
    ;; the swap has rendered by now, so the element is in the page
    (when-let [el (element)]
      (media/source! el (cond-> pb t (assoc :position t)))
      (marked! :source)
      (lock-screen! store)
      el)))

(defn cue!
  "Load the library's episode `id` into the player in `store` unless it
  has it already, and return the element. Move it to `t` seconds and set
  its `rate`, if given."
  [store id t rate]
  (let [el (if (= id (get-in @store [:player :id]))
             (let [el (element)]
               (when (and el (number? t))
                 (media/seek! el t))
               el)
             (load! store id t))]
    (when (and el (number? rate))
      (media/rate! el rate))
    el))

(defn play-id!
  "Play the library's episode `id` in the player in `store`, from `t`
  seconds if given and before its end. Otherwise it goes on from where
  another tab plays it, at that tab's rate, or from where the player or
  the listener left it."
  [store id t]
  (let [{p :player :as state} @store
        there                 (let [there (player/elsewhere p)]
                                (when (= id (:id there))
                                  there))
        el                    (cue! store id
                                    (or (some->> t (player/start-time (:duration (player/playback state id))))
                                        (some-> there (playback/position (js/Date.now))))
                                    (:playback-rate there))]
    (some->> el (play-element! store))))

;; library.change! does nothing with nil, rather than queue an operation
;; that changes nothing and render the page again in the click
(defn added
  "The change of `state` that adds the `playable` episode to the library,
  or nil when it's there already."
  [{:keys [library] :as state} {:keys [id guid url item-guid]}]
  (when-not (some-> library (library/playback id))
    {:operation [:episode/add id url (:feed (show/source state guid)) item-guid]}))

(defn play!
  "Play the `playable` episode in the player in `store`, from `t` seconds
  if given. First add it to the library, if needed, so that its progress
  is kept, and make it the current episode at the front of the queue."
  [store {:keys [id] :as playable} t]
  (asked! :play)
  (try
    (app-library/change! store (added @store playable))
    (step! store [:play id])
    (play-id! store id t)
    (catch :default e
      (tel/log! {:level :error :id :player/play-failed :data {:id id} :error e}
                "The episode didn't play"))))

(defn resume!
  "Resume the player in `store` from where it is, or try its file again
  where it failed, and its other files if their read failed."
  [store]
  (asked! :resume)
  (some->> (get-in @store [:player :id]) (read-library-files! store))
  (some->> (element) (play-element! store)))

(defn ended!
  "Report the end of the player's episode to the library in `store`, and
  play what's next, e.g. the next episode in the queue."
  [store]
  (when-let [id (get-in @store [:player :id])]
    (asked! :ended)
    (step! store [:ended id])))

(defn chapter!
  "Move the player in `store` to the chapter in `direction`, :previous or
  :next, unless the report of where it is ends the episode, e.g. in its
  outro."
  [store direction]
  (let [{:keys [player] :as state} @store
        id                         (:id player)
        el                         (element)]
    (when-let [doc (and id el (player/chapter-doc (player/playback state id)))]
      (when-not (player/moved-on? (report! store (media-of el)))
        (step! store [:chapter id direction doc]))
      (moved! store el))))

(defn drag!
  "Show the seek bar of the player in `store` at the seek time in
  `details` while it's dragged, and seek near there at once."
  [store {:keys [seek-time] :as details}]
  (swap! store assoc-in [:player :seeking] seek-time)
  (some-> (element) (media/seek! seek-time {:fast true})))

(defn released!
  "Show the seek bar of the player in `store` at the element's position
  again as a drag ends, e.g. one that ends where it began and so moves
  nothing."
  [store]
  (swap! store assoc-in [:player :seeking] nil))

(defn rate!
  "Set the player in `store` to play at `rate`, and keep it as the
  listener's preference."
  [store rate]
  (when (js/Number.isFinite rate)
    (let [el (element)]
      (some-> el (media/rate! rate))
      (app-library/change! store (settings/preferred {:playback-rate rate}))
      (app-library/save! store)
      (some->> el (moved! store)))))

(defn volume!
  "Set the volume of the player in `store` to `level`, and keep it in this
  browser."
  [store level]
  (when-let [el (element)]
    (media/volume! el level {:curve :cubic})
    (swap! store assoc-in [:player :volume] level)
    (local/store! volume-key (str level))))

(defn mute!
  "Mute the player, or unmute it when `muted?` is false."
  [muted?]
  (some-> (element) (media/muted! muted?)))

(defn command!
  "Carry out the `command` in the player in `store`: [:pause], [:press
  details], [:drag details], [:chapter direction], [:rate rate], [:volume
  level] or [:mute muted?]."
  [store [kind x]]
  (case kind
    :pause   (some-> (element) media/pause!)
    :press   (press! store x)
    :drag    (drag! store x)
    :chapter (chapter! store x)
    :rate    (rate! store x)
    :volume  (volume! store x)
    :mute    (mute! x)
    nil))

;; W3C Remote Playback API: while the media plays elsewhere, the local
;; player shows the remote state, and its commands go there
;; https://www.w3.org/TR/remote-playback/
(defn act!
  "Carry out the `command` on the playback that the player in `store`
  shows: in the other tab while it plays there, or else here. The volume
  and mute change here as well, since this tab's slider shows the level
  that the browser keeps."
  [store [kind details :as command]]
  (if (player/elsewhere (:player @store))
    (do (swap! store assoc-in [:player :seeking] (when (= :drag kind) (:seek-time details)))
        (post! {:type :command :command command})
        (when (#{:volume :mute} kind)
          (command! store command)))
    (command! store command)))

(declare ticked!)

(defn clock!
  "Move the clock of the player in `store` on once a second while another
  tab plays, or else stop it."
  [store]
  (let [moving? (some? (player/elsewhere (:player @store)))]
    (cond
      (and moving? (not (runtime/waiting? ::ticker)))
      (runtime/every! ::ticker 1000 #(ticked! store))

      (not moving?)
      (runtime/cancel! ::ticker))))

(defn follow!
  "Move the paused element of the player in `store` to where another tab's
  media `state` stopped, so that Play here goes on from there, and forget
  that tab's playback."
  [store {:keys [id current-time playback-rate] :as state}]
  (when-not (playing-element)
    (cue! store id current-time playback-rate))
  (swap! store update :player dissoc :elsewhere)
  (clock! store))

(defn ticked!
  "Move the clock of the player in `store` on to now. Once the tab that
  played falls silent, e.g. because it crashed, follow its playback to
  where it last was."
  [store]
  (let [{:keys [elsewhere] :as p} (:player (swap! store assoc-in [:player :now] (js/Date.now)))]
    (if (and elsewhere (not (player/elsewhere p)))
      (follow! store elsewhere)
      (clock! store))))

(defn told!
  "Take the media `state` that another tab posted into the player in
  `store` if it's newer: keep it while that tab plays, or else follow it
  to where it stopped."
  [store state]
  (let [now                 (js/Date.now)
        {:keys [elsewhere]} (:player @store)
        newer               (player/heard elsewhere state now)]
    (cond
      (identical? newer elsewhere)
      nil

      (false? (:paused newer))
      (do (swap! store update :player assoc :elsewhere newer :now now)
          (clock! store))

      :else
      (follow! store newer))))

(defn heard!
  "Handle the `message` from another tab in the player in `store`: its
  media state, a new tab's question, which the playing tab answers, or a
  command for the playing tab."
  [store {:keys [type] :as message}]
  (case type
    :told    (told! store (:state message))
    :ask     (some->> (playing-element) media/state (tell! store))
    :command (when (playing-element)
               (command! store (:command message)))
    nil))

;; HTML Living Standard, 9.5: a page in the back/forward cache gets no
;; messages, so it missed what the other tabs played meanwhile
;; https://html.spec.whatwg.org/multipage/web-messaging.html#broadcasting-to-other-browsing-contexts
(defn left-at
  "The seconds where the library in `state` says the listener left the
  player's episode, or nil while another tab plays it."
  [{:keys [player] :as state}]
  (when-not (player/elsewhere player)
    (some->> (:id player) (player/playback state) :position)))

(defn shown-again!
  "Ask the playing tab, if any, for its media state as the back/forward
  cache shows the page of the player in `store` again. Move this tab's
  paused element to where the listener left its episode, once the
  library has caught up, since another tab may have played it. That's
  also where a reload would start it, gone back by the listener's rewind
  after a pause."
  [store]
  (post! {:type :ask})
  (-> (app-library/caught-up! store)
      (.catch (constantly nil))
      (.then (fn [_]
               (let [el (element)
                     t  (left-at @store)]
                 (when (and el t (.-paused el))
                   (media/seek! el t)))))))

(defn listen!
  "Handle the other tabs' messages in the player in `store`, and ask the
  playing tab, if any, for its media state, also after the back/forward
  cache shows the page again."
  [store]
  (runtime/on-message! player/channel-name #(heard! store %))
  (runtime/on-pageshow! #(shown-again! store))
  (post! {:type :ask}))

(defn mounted!
  "Follow the audio `element` as the player in `store` mounts it: keep its
  state, report its position, show it on the lock screen, pause it when
  another tab plays and handle the keys pressed in the player. Load the
  restored player's episode if nothing has loaded it yet."
  [store ^js element]
  (let [{:keys [player] :as state} @store
        {:keys [id volume]}        player]
    (when (and id (not (.getAttribute element "src")))
      (media/source! element (player/playback state id))
      ;; after the start, and still before a Play, so that the chapters'
      ;; buttons are there for it
      (runtime/idle! 2000 #(read-library-files! store id)))
    (media/volume! element (or volume 1) {:curve :cubic})
    (runtime/attach-player! element {:on-media     #(changed! store %)
                                     :on-report    (reporter store)
                                     :lock         player/element-name
                                     :keys-element (.closest element "#player")
                                     :handlers     (key-handlers store)})
    (lock-screen! store)))

(defn restored
  "The change that restores the player as the page loads, with the episode
  at the front of the queue and the `volume` that this browser keeps, or
  else full volume."
  [volume]
  {:ui (fn [state]
         (let [id (some-> state :library :profile queue/current)]
           (cond-> (assoc-in state [:player :volume] (or volume 1))
             (player/playback state id) (assoc-in [:player :id] id))))})

(defn restored!
  "Restore the player in `store` at the volume that this browser keeps."
  [store]
  (app-library/change! store (restored (local/stored-number volume-key))))

;; Page Lifecycle API: hidden is the last state that a page can count on,
;; e.g. before a phone discards it, so the player reports there rather
;; than at unload
;; https://developer.chrome.com/docs/web-platform/page-lifecycle-api
(defn hidden!
  "Report the position of the player in `store` as the page is hidden,
  while its element plays."
  [store]
  (some->> (playing-element) media-of (report! store)))

(defn gone!
  "Tell the other tabs that the player in `store` stops where it is as the
  page goes, if its element plays."
  [store]
  (when-let [el (playing-element)]
    (tell! store (assoc (media/state el) :paused true))))

;; The player goes when the library loses its episode's show, e.g. as the
;; listener leaves it, and the browser pauses an element that leaves the
;; page with no event that a report could follow
;; https://html.spec.whatwg.org/multipage/media.html#playing-the-media-resource
(defn unmounted!
  "Stop following the audio `element` as the player in `store` unmounts
  it, once its position is reported and the other tabs know it stopped,
  if it played."
  [store ^js element]
  (when (and element (not (.-paused element)))
    (report! store (media-of element))
    (tell! store (assoc (media/state element) :paused true)))
  (runtime/detach-player!))

(defn volume-locked!
  "Note in the player in `store` whether the device locks the volume."
  [store]
  (.then (media/volume-locked!) #(swap! store assoc-in [:player :volume-locked?] (boolean %))))
