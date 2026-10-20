(ns dk.simongray.podcast-app.audio
  "The audio element of the player, and what the app does to it as the
  effects of the views' actions, with play-nice: play an episode of the
  library, tell the library of each move with library/step and write it,
  and keep the lock screen in step.

  The element says where playback is now, and the library where the
  listener left it. So the player tells the library where the element is
  at the moments that player/report? names and as the page is hidden. A
  move of the listener's goes through the library too, and each change is
  written at once.

  The element is the same for every episode. A phone's browser lets a page
  play an element that the listener started, so the next episode of the
  queue plays in it too, and the lock screen keeps its session.

  One tab's element plays at a time, by play-nice's play lock. As its
  media state changes, that tab posts it to the other tabs on a
  BroadcastChannel of the app's own, and it runs the commands that they
  send it. When it pauses, the other tabs move their paused elements to
  where it stopped, so that Play in any tab goes on from there."
  (:require [cljs.reader :as reader]
            [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.page.tabs :as tabs]
            [dk.simongray.play-nice.session :as session]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.app.media-session :as media-session]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.library :as app-library]
            [dk.simongray.podcast-app.local :as local]
            [dk.simongray.podcast-app.picks :as picks]
            [dk.simongray.podcast-app.player :as player]
            [dk.simongray.podcast-app.settings :as settings]))

(defn element
  "The audio element of the player, or nil before anything plays."
  []
  (js/document.querySelector "#player audio"))

(defn playing-element
  "The audio element of the player while it plays, or nil."
  []
  (let [el (element)]
    (when (and el (not (.-paused el)))
      el)))

(def volume-key
  "The key of the level of the volume slider in localStorage, since the
  volume belongs to this device rather than to the listener's profile."
  "dk.simongray.podcast-app.volume")

(defn save!
  "A promise of the write of the changes to the library of the `store`
  that wait, which logs a write that failed."
  [store]
  (.then (app-library/save! store)
         #(when (:error %)
            (js/console.error "The library wasn't saved" (clj->js %)))))

(defonce ^{:doc "The BroadcastChannel of the player across the tabs, opened
  at its first use, or nil where the browser has none."}
  channel
  (delay (when (exists? js/BroadcastChannel)
           (js/BroadcastChannel. player/channel-name))))

(defonce ^{:doc "The id of this tab on the channel."}
  tab
  (str (random-uuid)))

(defn post!
  "Send the `message` to the other tabs on the channel, as EDN."
  [message]
  (some-> ^js @channel (.postMessage (pr-str message))))

(defn tell!
  "Post the `media` state of this tab's element, as media/state returns
  it, to the other tabs, with the episode of the player of the `store`."
  [store media]
  (post! {:type  :told
          :state (player/told media (get-in @store [:player :id]) tab (js/Date.now))}))

(declare autoplay! press! play-id!)

(defn apply-effects!
  "Do the `effects` of library/step in the player of the `store`."
  [store effects]
  (doseq [[kind id position] effects]
    (case kind
      :seek         (some-> (element) (media/seek! position))
      :stop         (some-> (element) media/pause!)
      :play         (play-id! store id nil)
      :played       nil
      :nothing-next (autoplay! store id))))

(defn step!
  "Run the player `event` through library/step in the `store`, write the
  library, do the effects of the step, and give them."
  [store event]
  (let [{:keys [effects]} (app-library/change! store {:operation [:player/step event]})]
    (save! store)
    (apply-effects! store effects)
    effects))

;; The library's step plays the next of the queue, or of the show with
;; Continuous play, and stops at the end of a sleep timer, so a pick plays
;; only when the step says that nothing plays next, which it says of an
;; episode that ends in its outro too
(defn autoplay!
  "Play the first pick after the episode `id` in the player of the `store`,
  as Play does, when the listener chose Autoplay."
  [store id]
  (when-let [pick (picks/autoplayed @store id)]
    (step! store [:play pick])
    (play-id! store pick nil)))

(defn handlers
  "The handlers of the actions of the lock screen and the keys for the
  `store`: play and pause on the `element` itself, and the rest through
  the library."
  [store element]
  (merge (select-keys (session/element-actions element) [:play :pause :stop])
         (zipmap [:seekbackward :seekforward :seekto :previoustrack :nexttrack]
                 (repeat #(press! store %)))))

(defn lock-screen!
  "Show the episode of the player of the `store` on the lock screen, with
  the buttons that do something for it."
  [store]
  (let [{:keys [library player] :as state} @store
        id                                 (:id player)]
    (when-let [el (and id (element))]
      (session/metadata! (:metadata (player/playback state id)))
      (session/actions! (select-keys (handlers store el)
                                     (concat [:play :pause :stop]
                                             (media-session/actions library id)))))))

(defn moved!
  "Post the state of the `element` of the player of the `store` to the
  other tabs after the listener moved it while it's paused, since no pause
  will."
  [store ^js element]
  (when (.-paused element)
    (tell! store (media/state element))))

(defn press!
  "Do what the Media Session action of `details` asks for in the player of
  the `store`, e.g. {:action :seekforward}, through the library: report
  where the element is, and then move."
  [store details]
  (let [{:keys [library player]} @store
        id                       (:id player)]
    (when-let [el (and id (element))]
      (swap! store assoc-in [:player :seeking] nil)
      (run! #(step! store %) (media-session/events library id (.-currentTime el) details))
      (moved! store el))))

(defn media-of
  "The position and the duration of the audio `element`, under the keys of
  its media state."
  [^js element]
  {:current-time (.-currentTime element) :duration (.-duration element)})

(defn report!
  "Tell the library of the `store` where the element is, by its `media`
  state, and write it."
  [store {:keys [current-time duration] :as media}]
  (when-let [id (and (number? current-time) (get-in @store [:player :id]))]
    (let [duration (when (and (number? duration) (js/Number.isFinite duration))
                     duration)]
      (step! store [:progress id current-time duration]))))

(defn reporter
  "A function that tells the library of the `store` where the audio
  element is, by each media state of the element that media/watch! gives
  it, at the moments that player/report? names. It keeps the last state,
  and when it last reported in the episode of the player, to itself."
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
        (if (player/report? old shown (when (= id (:id s)) last) now outro)
          (do (reset! seen {:media shown :at now :id id})
              (report! store shown))
          (swap! seen assoc :media shown))))))

(defonce ^{:doc "The timeout that shows that the player waits for data,
  after half a second, as Media Chrome does, so that a short wait says
  nothing."}
  buffering-timeout
  (atom nil))

(defn buffering!
  "Show that the player of the `store` waits for data, by its `media`
  state, once it has waited half a second while it plays."
  [store {:keys [status paused] :as media}]
  (if (and (= :buffering status) (not paused))
    (when-not @buffering-timeout
      (reset! buffering-timeout
              (js/setTimeout #(do (reset! buffering-timeout nil)
                                  (swap! store assoc-in [:player :buffering?] true))
                             500)))
    (do (some-> @buffering-timeout js/clearTimeout)
        (reset! buffering-timeout nil)
        (when (get-in @store [:player :buffering?])
          (swap! store assoc-in [:player :buffering?] false)))))

(defn changed!
  "Keep the `media` state of the element in the player of the `store`, to
  the whole second. While the element plays, and as it pauses, post each
  change to the other tabs."
  [store media]
  (let [old   (get-in @store [:player :media])
        shown (player/whole-seconds media)]
    (when (not= shown old)
      (swap! store assoc-in [:player :media] shown)
      (when (or (false? (:paused shown)) (false? (:paused old)))
        (tell! store media)))
    (buffering! store shown)))

(defn answered!
  "Keep the browser's `answer` to a play in the player of the `store`, when
  it refused."
  [store answer]
  (swap! store assoc-in [:player :answer] (when (:refused answer) answer)))

(defn play-element!
  "Play the audio `element` of the player of the `store`, and keep the
  browser's answer."
  [store element]
  (.then (media/play! element) #(answered! store %)))

(defn read-files!
  "Read the files of the episode `id` into the :episodes of the `store`
  once, with `read`, a function that gives a promise of them, e.g. for the
  chapters of a chapters file or the media file, and show them on the lock
  screen while the player has the episode."
  [store id read]
  (when-not (get-in @store [:episodes id :status])
    (swap! store assoc-in [:episodes id :status] :reading)
    (-> (read)
        (.then (fn [result]
                 (swap! store update-in [:episodes id] assoc :status :read :result result)
                 (when (= id (get-in @store [:player :id]))
                   (lock-screen! store))))
        (.catch #(swap! store update-in [:episodes id] assoc :status :read
                        :error {:message (ex-message %)})))))

(defn read-library-files!
  "Read the files of the episode `id` of the library of the `store`, as
  read-files! does, unless the Episode page has read them. The transcript
  waits for the listener."
  [store id]
  (read-files! store id #(library/read-files! (:library @store) id
                                              (assoc app-library/options :except #{:transcript}))))

(defn load!
  "Load the episode `id` of the library into the player of the `store`, at
  `t` seconds when they're given, or else where the listener left it, and
  return the element."
  [store id t]
  (let [pb (player/playback @store id)]
    (swap! store update :player assoc :id id :answer nil :seeking nil)
    (read-library-files! store id)
    ;; the render of the swap is done, so the element is in the page
    (when-let [el (element)]
      (media/source! el (cond-> pb t (assoc :position t)))
      (lock-screen! store)
      el)))

(defn cue!
  "Load the episode `id` of the library into the player of the `store`,
  unless it has it already, and return the element. Move it to `t`
  seconds and set its `rate` when they're given."
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
  "Play the episode `id` of the library in the player of the `store`: from
  `t` seconds when they're given, or else from where another tab plays it
  (at that tab's rate), from where the player is in it, or from where the
  listener left it."
  [store id t]
  (let [p     (:player @store)
        there (let [there (player/elsewhere p)]
                (when (= id (:id there))
                  there))
        el    (cue! store id
                    (or t (some-> there (tabs/position (js/Date.now))))
                    (:playback-rate there))]
    (some->> el (play-element! store))))

(defn added
  "The change of the `state` that adds the episode of the `playable` of an
  Episode page, as episode/playable gives it, to the library, unless it
  has it already."
  [state {:keys [id guid url item-guid]}]
  {:operation [:episode/add id url (:feed (episode/source state guid)) item-guid]})

(defn play!
  "Play the `playable` episode, e.g. of an Episode page or a pick, in the
  player of the `store`, from `t` seconds when it's given: first add it to
  the library, unless it's there, so that its progress is kept, and make
  it the current episode, at the front of the queue."
  [store {:keys [id] :as playable} t]
  (try
    (app-library/change! store (added @store playable))
    (step! store [:play id])
    (play-id! store id t)
    (catch :default e
      (js/console.error "The episode didn't play" e))))

(defn resume!
  "Play the episode in the player of the `store` from where it is."
  [store]
  (some->> (element) (play-element! store)))

(defn ended!
  "Tell the library that the episode of the player of the `store` ended,
  which plays the next one of the queue, or else the first pick after it
  when the listener chose Autoplay."
  [store]
  (when-let [id (get-in @store [:player :id])]
    (step! store [:ended id])))

(defn chapter!
  "Go to the chapter in `direction`, :previous or :next, of the episode of
  the player of the `store`."
  [store direction]
  (let [{:keys [player] :as state} @store
        id                         (:id player)
        el                         (element)]
    (when-let [doc (and id el (player/chapter-doc (player/playback state id)))]
      (report! store (media-of el))
      (step! store [:chapter id direction doc])
      (moved! store el))))

(defn drag!
  "Show the seek bar at the seek of `details` while it's dragged, and seek
  near there at once."
  [store {:keys [seek-time] :as details}]
  (swap! store assoc-in [:player :seeking] seek-time)
  (some-> (element) (media/seek! seek-time {:fast true})))

(defn rate!
  "Play at `rate`, and keep it as the listener's preference."
  [store rate]
  (when (js/Number.isFinite rate)
    (let [el (element)]
      (some-> el (media/rate! rate))
      (app-library/change! store (settings/preferred {:playback-rate rate}))
      (save! store)
      (some->> el (moved! store)))))

(defn volume!
  "Set the volume slider of the player of the `store` to `level`, and keep
  it in this browser."
  [store level]
  (when-let [el (element)]
    (media/volume! el level {:curve :cubic})
    (swap! store assoc-in [:player :volume] level)
    (local/store! volume-key (str level))))

(defn mute!
  "Mute the player, or unmute it where `muted?` is false."
  [muted?]
  (some-> (element) (media/muted! muted?)))

(defn command!
  "Do the `command` in the player of the `store`, as its controls or
  another tab ask: [:pause], [:press details], [:drag details], [:chapter
  direction], [:rate rate], [:volume level] or [:mute muted?]."
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
  "Do the `command`, as for command!, to the playback that the player of
  the `store` shows: in the tab that plays it, when that's another tab, or
  else here. The volume and the mute change here as well, since this
  tab's slider shows the level that the browser keeps."
  [store [kind details :as command]]
  (if (player/elsewhere (:player @store))
    (do (swap! store assoc-in [:player :seeking] (when (= :drag kind) (:seek-time details)))
        (post! {:type :command :command command})
        (when (#{:volume :mute} kind)
          (command! store command)))
    (command! store command)))

(defonce ^{:doc "The interval that moves the clock of the player on while
  another tab plays."}
  ticker
  (atom nil))

(declare ticked!)

(defn clock!
  "Move the clock of the player of the `store` on once a second while
  another tab plays, and stop it otherwise."
  [store]
  (let [moving? (some? (player/elsewhere (:player @store)))]
    (cond
      (and moving? (not @ticker))
      (reset! ticker (js/setInterval #(ticked! store) 1000))

      (and (not moving?) @ticker)
      (do (js/clearInterval @ticker)
          (reset! ticker nil)))))

(defn follow!
  "Move the paused element of the player of the `store` to where another
  tab's media `state` stopped, so that Play here goes on from there, and
  forget that tab's playback."
  [store {:keys [id current-time playback-rate] :as state}]
  (when-not (playing-element)
    (cue! store id current-time playback-rate))
  (swap! store update :player dissoc :elsewhere)
  (clock! store))

(defn ticked!
  "Move the clock of the player of the `store` on to now. Once the tab that
  played has gone silent, e.g. because it crashed, follow its playback to
  where it last was."
  [store]
  (let [{:keys [elsewhere] :as p} (:player (swap! store assoc-in [:player :now] (js/Date.now)))]
    (if (and elsewhere (not (player/elsewhere p)))
      (follow! store elsewhere)
      (clock! store))))

(defn told!
  "Take the media `state` that another tab posted into the player of the
  `store`, if it's the newer one: keep it while that tab plays, or else
  follow it to where it stopped."
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
  "Handle the `message` that another tab sent on the channel, in the
  player of the `store`: its media state, the question of a tab that just
  opened, which the playing tab answers, or a command for the playing
  tab."
  [store {:keys [type] :as message}]
  (case type
    :told    (told! store (:state message))
    :ask     (some->> (playing-element) media/state (tell! store))
    :command (when (playing-element)
               (command! store (:command message)))
    nil))

(defn listen!
  "Follow the messages that the other tabs send on the channel, in the
  player of the `store`, and ask the tab that plays, if any, for its media
  state."
  [store]
  (when-let [^js ch @channel]
    (.addEventListener ch "message"
                       (fn [^js e]
                         (let [message (try (reader/read-string (.-data e)) (catch :default _ nil))]
                           (when (map? message)
                             (heard! store message)))))
    (post! {:type :ask})))

(defonce ^{:doc "The function that stops following the element."}
  detach!
  (atom (constantly nil)))

(defn mounted!
  "Follow the audio `element` that the player of the `store` mounted: keep
  its state, tell the library where it is, put it on the lock screen, let
  it pause when another tab plays, and follow the keys pressed in the
  player. Load the episode of a player that the page restored, where
  nothing loaded it yet."
  [store ^js element]
  (let [{:keys [player] :as state} @store
        {:keys [id volume]}        player
        events                     {:events (conj media/events :timeupdate)}]
    (when (and id (not (.getAttribute element "src")))
      (media/source! element (player/playback state id))
      (read-library-files! store id))
    (media/volume! element (or volume 1) {:curve :cubic})
    (reset! detach!
            (juxt (media/watch! element #(changed! store %) events)
                  (media/watch! element (reporter store) events)
                  (session/attach! element)
                  (tabs/attach! element player/element-name)
                  (controls/listen! (.closest element "#player") element
                                    (handlers store element))))
    (lock-screen! store)))

(defn restored
  "The change as the page loads: the player with the episode that the
  queue has at its front, paused where the listener left it, at the
  `volume` that this browser keeps, or all of it."
  [volume]
  {:ui (fn [state]
         (let [id (some-> state :library :profile queue/current)]
           (cond-> (assoc-in state [:player :volume] (or volume 1))
             (player/playback state id) (assoc-in [:player :id] id))))})

(defn restored!
  "Restore the player of the `store` with restored as the page loads, at
  the volume that this browser keeps."
  [store]
  (app-library/change! store (restored (local/stored-number volume-key))))

;; Page Lifecycle API: hidden is the last state that a page can count on
;; seeing, e.g. before a phone discards it, so the player reports there,
;; and never at unload
;; https://developer.chrome.com/docs/web-platform/page-lifecycle-api
(defn hidden!
  "Tell the library where the player of the `store` is, and write it, as
  the page is hidden, while its element plays. The play lock makes it the
  only element across the tabs that does."
  [store]
  (when-let [el (and (get-in @store [:player :id]) (playing-element))]
    (report! store (media-of el))))

(defn gone!
  "While the element of the player of the `store` plays, tell the other
  tabs that its playback stops where it is, as the page goes."
  [store]
  (when-let [el (playing-element)]
    (tell! store (assoc (media/state el) :paused true))))

(defn volume-locked!
  "Note in the player of the `store` whether the device locks the volume."
  [store]
  (.then (media/volume-locked!) #(swap! store assoc-in [:player :volume-locked?] (boolean %))))
