(ns dk.simongray.podcast-app.audio
  "The audio element of the player, and what the app does to it as the
  effects of the views' actions, with play-nice: play an episode of the
  library, tell the library of each move with library/step, keep the lock
  screen in step, and save the library now and then.

  The element is the same for every episode. A phone's browser lets a page
  play an element that the listener started, so the next episode of the
  queue plays in it too, and the lock screen keeps its session."
  (:require [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.page.tabs :as tabs]
            [dk.simongray.play-nice.session :as session]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.app.media-session :as media-session]
            [dk.simongray.podcast.app.unsaved :as unsaved]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.podcast-app.episode :as episode]
            [dk.simongray.podcast-app.library :as app-library]
            [dk.simongray.podcast-app.picks :as picks]
            [dk.simongray.podcast-app.player :as player]))

(defn element
  "The audio element of the player, or nil before anything plays."
  []
  (js/document.querySelector "#player audio"))

(def volume-key
  "The key of the level of the volume slider in localStorage, since the
  volume belongs to this device rather than to the listener's profile."
  "dk.simongray.podcast-app.volume")

;; A save of the library to IndexedDB takes a few steps, which a page that
;; reloads or closes never finishes, so podcast-clj's app.unsaved keeps the
;; position and the episode that the listener played in localStorage too,
;; e.g. for a reload right after Play, which would otherwise bring the
;; episode before back
(def unsaved-prefix
  "The prefix of the keys in localStorage under which app.unsaved keeps
  the player's moves that the library's save may not have yet."
  "dk.simongray.podcast-app")

(defn save!
  "Save the library of the `store`, and note when."
  [store]
  (let [started (js/Date.now)]
    (swap! store assoc-in [:player :saved] started)
    (-> (app-library/save!)
        (.then #(if (:error %)
                  (js/console.error "The library wasn't saved" (clj->js %))
                  (unsaved/saved! unsaved-prefix started))))))

(defn playback
  "What library/playback gives for the episode `id` of the `state`, with
  what the Episode page read of its files, e.g. a chapters file, and the
  artwork of its chapters through the app's proxy."
  [{:keys [library episodes] :as state} id]
  (some-> (library/playback library id (if-let [read (get-in episodes [id :result])]
                                         {:read read}
                                         {}))
          (update :metadata player/with-proxied-chapters)))

(declare press! play-id!)

(defn apply-effects!
  "Do the `effects` of library/step in the player of the `store`."
  [store effects]
  (doseq [[kind id position] effects]
    (case kind
      :seek   (some-> (element) (media/seek! position))
      :stop   (some-> (element) media/pause!)
      :play   (play-id! store id nil)
      :played nil)))

(defn step!
  "Run the player `event` through library/step in the `store`, do its
  effects, and give them."
  [store event]
  (let [{:keys [effects] :as lib} (library/step (:library @store) event)]
    (swap! store assoc :library (dissoc lib :effects))
    (apply-effects! store effects)
    effects))

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
  (let [{:keys [library player]} @store
        {:keys [id playback]}    player]
    (when-let [el (and id (element))]
      (session/metadata! (:metadata playback))
      (session/actions! (select-keys (handlers store el)
                                     (concat [:play :pause :stop]
                                             (media-session/actions library id)))))))

(defn press!
  "Do what the Media Session action of `details` asks for in the player of
  the `store`, e.g. {:action :seekforward}, through the library: report
  where the element is, and then move."
  [store details]
  (let [{:keys [library player]} @store
        id                       (:id player)]
    (when-let [el (and id (element))]
      (swap! store assoc-in [:player :seeking] nil)
      (run! #(step! store %) (media-session/events library id (.-currentTime el) details)))))

(defn media-of
  "The position and the duration of the audio `element`, under the keys of
  its media state."
  [^js element]
  {:current-time (.-currentTime element) :duration (.-duration element)})

(defn report!
  "Tell the library of the `store` where the element is, by its `media`
  state."
  [store {:keys [current-time duration] :as media}]
  (when-let [id (and (number? current-time) (get-in @store [:player :id]))]
    (let [duration (when (and (number? duration) (js/Number.isFinite duration))
                     duration)]
      (unsaved/keep-position! unsaved-prefix id current-time duration)
      (swap! store assoc-in [:player :reported] current-time)
      (step! store [:progress id current-time duration]))))

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
  the whole second, and tell the library and save it when it's time, as
  long as no other tab has the newest playback."
  [store media]
  (let [{old :media :keys [reported saved] :as p} (:player @store)
        media                                      (player/whole-seconds media)]
    (when (not= media old)
      (swap! store assoc-in [:player :media] media))
    (when (and (player/writes? p) (player/report? old media reported))
      (report! store media))
    (when (player/save? old media saved (js/Date.now))
      (save! store))
    (buffering! store media)))

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
  chapters of a chapters file or the media file, and show them in the
  player and on the lock screen while the player has the episode."
  [store id read]
  (when-not (get-in @store [:episodes id :status])
    (swap! store assoc-in [:episodes id :status] :reading)
    (-> (read)
        (.then (fn [result]
                 (swap! store update-in [:episodes id] assoc :status :read :result result)
                 (when (= id (get-in @store [:player :id]))
                   (swap! store assoc-in [:player :playback] (playback @store id))
                   (lock-screen! store))))
        (.catch #(swap! store update-in [:episodes id] assoc :status :read
                        :error {:message (ex-message %)})))))

(defn read-library-files!
  "Read the files of the episode `id` of the library of the `store`, as
  read-files! does, unless the Episode page has read them. The transcript
  waits for the listener."
  [store id]
  (read-files! store id #(library/read-files! (:library @store) id {:except #{:transcript}})))

(defn load!
  "Load the episode `id` of the library into the player of the `store`, at
  `t` seconds when it's given, or else where the listener left it, and
  return the element."
  [store id t]
  (let [pb (playback @store id)]
    (swap! store update :player assoc
           :id id :playback pb :answer nil :reported nil :seeking nil)
    (read-library-files! store id)
    ;; the render of the swap is done, so the element is in the page
    (when-let [el (element)]
      (media/source! el (cond-> pb t (assoc :position t)))
      (lock-screen! store)
      el)))

(defn play-id!
  "Play the episode `id` of the library in the player of the `store`, from
  `t` seconds when it's given, or else from where the player is in it, or
  where the listener left it."
  [store id t]
  (let [same? (= id (get-in @store [:player :id]))
        el    (if same? (element) (load! store id t))]
    (when-not same?
      (unsaved/keep-played! unsaved-prefix id))
    (when (and same? el (number? t))
      (media/seek! el t))
    (some->> el (play-element! store))))

(defn added
  "The library of the `state` with the episode of the `playable` of an
  Episode page, as episode/playable gives it, unless it has it already."
  [{:keys [library] :as state} {:keys [id guid url item-guid]}]
  (if (library/playback library id)
    library
    (library/add-episode library url (:feed (episode/source state guid)) item-guid)))

(defn play!
  "Play the `playable` episode, e.g. of an Episode page or a pick, in the
  player of the `store`, from `t` seconds when it's given: first add it to
  the library, unless it's there, so that its progress is kept, and make
  it the current episode, at the front of the queue."
  [store {:keys [id] :as playable} t]
  (try
    (swap! store assoc :library (added @store playable))
    (step! store [:play id])
    (play-id! store id t)
    (save! store)
    (catch :default e
      (js/console.error "The episode didn't play" e))))

(defn resume!
  "Play the episode in the player of the `store` from where it is."
  [store]
  (some->> (element) (play-element! store)))

;; The library's step plays the next of the queue, or of the show with
;; Continuous play, and stops at the end of a sleep timer, so a pick plays
;; only when the step does none of them
(defn ended!
  "Tell the library that the episode of the player of the `store` ended,
  which plays the next one of the queue, or else the first pick after it
  when the listener chose Autoplay, and save it."
  [store]
  (when-let [id (get-in @store [:player :id])]
    (if-let [pick (and (empty? (step! store [:ended id]))
                       (picks/autoplayed @store id))]
      (play! store {:id pick} nil)
      (save! store))))

(defn chapter!
  "Go to the chapter in `direction`, :previous or :next, of the episode of
  the player of the `store`."
  [store direction]
  (let [{:keys [id playback]} (:player @store)
        el                    (element)]
    (when-let [doc (and id el (player/chapter-doc playback))]
      (report! store (media-of el))
      (step! store [:chapter id direction doc]))))

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
    (some-> (element) (media/rate! rate))
    (swap! store update :library library/update-profile profile/prefer {:playback-rate rate})
    (save! store)))

(defn volume!
  "Set the volume slider of the player of the `store` to `level`, and keep
  it in this browser."
  [store level]
  (when-let [el (element)]
    (media/volume! el level {:curve :cubic})
    (swap! store assoc-in [:player :volume] level)
    (media/keep-level! volume-key level)))

(defn mute!
  "Mute the player, or unmute it where `muted?` is false."
  [muted?]
  (some-> (element) (media/muted! muted?)))

(defn shared-handlers
  "The handlers of the actions that the other tabs send to the player of the
  `store` while its `element` plays: those of the lock screen and the
  keys, and those of the player's own controls."
  [store element]
  (merge (handlers store element)
         {:drag    #(drag! store %)
          :chapter #(chapter! store (:direction %))
          :rate    #(rate! store (:rate %))
          :volume  #(volume! store (:volume %))
          :muted   #(mute! (:muted %))}))

;; W3C Remote Playback API: while the media plays elsewhere, the local
;; player shows the remote state, and its commands go there
;; https://www.w3.org/TR/remote-playback/
(defn act!
  "Do the action of `details` to the playback that the player of the
  `store` shows: in the tab that plays it, where that's another, and else
  here with `local!`, from where the playback is, telling the other tabs
  of a change of the paused element."
  [store details local!]
  (if (player/playing-elsewhere? (:player @store))
    (do (swap! store assoc-in [:player :seeking] nil)
        (tabs/press! player/element-name details))
    (let [el (element)]
      (when (and el (.-paused el))
        (tabs/align! el))
      (local!)
      (when (and el (.-paused el))
        (tabs/share! el)))))

(defn pause-shown!
  "Pause the playback that the player of the `store` shows, where it
  plays."
  [store]
  (if (player/playing-elsewhere? (:player @store))
    (tabs/press! player/element-name {:action :pause})
    (some-> (element) media/pause!)))

(defn resume-shown!
  "Play the playback that the player of the `store` shows, here: the
  episode of another tab's newest playback, where it's another than this
  one's, or else this one, from where the playback is."
  [store]
  (let [{:keys [id] :as p} (:player @store)
        other              (:id (player/elsewhere p))]
    (if (and other (not= other id))
      (play-id! store other nil)
      (resume! store))))

(defn drag-shown!
  "Show the seek bar at the seek of `details` while it's dragged, and seek
  near there, in the tab that plays."
  [store {:keys [seek-time] :as details}]
  (if (player/playing-elsewhere? (:player @store))
    (do (swap! store assoc-in [:player :seeking] seek-time)
        (tabs/press! player/element-name (assoc details :action :drag)))
    (drag! store details)))

(defn volume-shown!
  "Set the volume of the player of the `store` to `level`, and of the tab
  that plays, whose sound the listener hears."
  [store level]
  (volume! store level)
  (when (player/playing-elsewhere? (:player @store))
    (tabs/press! player/element-name {:action :volume :volume level})))

(defn mute-shown!
  "Mute the player of the `store`, or unmute it where `muted?` is false,
  and the tab that plays."
  [store muted?]
  (mute! muted?)
  (when (player/playing-elsewhere? (:player @store))
    (tabs/press! player/element-name {:action :muted :muted muted?})))

(defonce ^{:doc "The interval that moves the playback of another tab on in
  the player while it plays there."}
  ticker
  (atom nil))

(defn shared-moved
  "The `state` with the player's :shared-position, where the newest
  playback across the tabs is by now, to the whole second, and the
  :shared-playback of its episode, as library/playback gives it."
  [state]
  (let [{:keys [shared shared-playback]} (:player state)
        id                               (:id shared)]
    (update state :player assoc
            :shared-position (some-> shared (tabs/position (js/Date.now)) js/Math.floor)
            :shared-playback (when id
                               (if (= id (:id shared-playback))
                                 shared-playback
                                 (playback state id))))))

(defn shared!
  "Keep the newest playback across the tabs, `shared`, as tabs/watch!
  gives it, in the player of the `store`, moved on every second while
  another tab plays it."
  [store shared]
  (swap! store #(shared-moved (assoc-in % [:player :shared] shared)))
  (let [moving? (player/playing-elsewhere? (:player @store))]
    (cond
      (and moving? (not @ticker))
      (reset! ticker (js/setInterval #(swap! store shared-moved) 1000))

      (and (not moving?) @ticker)
      (do (js/clearInterval @ticker)
          (reset! ticker nil)))))

(defonce ^{:doc "The function that stops following the element."}
  detach!
  (atom (constantly nil)))

(defn mounted!
  "Follow the audio `element` that the player of the `store` mounted: keep
  its state, put it on the lock screen, share its playback with the other
  tabs, let it pause when another tab plays, and follow the keys pressed
  in the player. Load the episode of a player that the page restored,
  where nothing loaded it yet."
  [store ^js element]
  (let [{:keys [id playback volume]} (:player @store)]
    (when (and id (not (.getAttribute element "src")))
      (media/source! element playback)
      (read-library-files! store id))
    (media/volume! element (or volume 1) {:curve :cubic})
    (reset! detach!
            (juxt (media/watch! element #(changed! store %)
                                {:events (conj media/events :timeupdate)})
                  (session/attach! element)
                  (tabs/attach! element player/element-name
                                {:id-fn    #(get-in @store [:player :id])
                                 :handlers (shared-handlers store element)
                                 :align?   true})
                  (controls/listen! (.closest element "#player") element
                                    (handlers store element))))
    (lock-screen! store)))

(defn restored
  "The `state` as the page loads: its library told by unsaved/restored of
  the episode that this browser `played` last and of the position that it
  `kept`, and its player with the episode that the queue has at its
  front, paused where the listener left it, at the `volume` that this
  browser keeps, or all of it."
  [state played kept volume]
  (let [state (update state :library unsaved/restored played kept)
        id    (some-> state :library :profile queue/current)
        pb    (when id (playback state id))]
    (cond-> (assoc-in state [:player :volume] (or volume 1))
      pb (update :player assoc :id id :playback pb))))

(defn restored!
  "Restore the player of the `store` with restored as the page loads,
  from what this browser keeps."
  [store]
  (swap! store restored
         (unsaved/kept-played unsaved-prefix)
         (unsaved/kept-position unsaved-prefix)
         (media/kept-level volume-key)))

;; Page Lifecycle API: hidden is the last state that a page can count on
;; seeing, e.g. before a phone discards it, so the player reports and saves
;; there, and never at unload
;; https://developer.chrome.com/docs/web-platform/page-lifecycle-api
(defn hidden!
  "Tell the library where the player of the `store` is, and save it, as
  the page is hidden, unless another tab has the newest playback."
  [store]
  (when-let [el (and (get-in @store [:player :id])
                     (player/writes? (:player @store))
                     (element))]
    (report! store (media-of el))
    (save! store)))

(defn volume-locked!
  "Note in the player of the `store` whether the device locks the volume."
  [store]
  (.then (media/volume-locked!) #(swap! store assoc-in [:player :volume-locked?] (boolean %))))
