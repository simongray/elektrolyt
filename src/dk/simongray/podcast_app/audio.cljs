(ns dk.simongray.podcast-app.audio
  "The audio element of the player, and what the app does to it as the
  effects of the views' actions, with play-nice: play an episode of the
  library, tell the library of each move with library/step, keep the lock
  screen in step, and save the library now and then.

  The element is the same for every episode. A phone's browser lets a page
  play an element that the listener started, so the next episode of the
  queue plays in it too, and the lock screen keeps its session."
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
            [dk.simongray.podcast-app.player :as player]))

(defn element
  "The audio element of the player, or nil before anything plays."
  []
  (js/document.querySelector "#player audio"))

(def volume-key
  "The key of the level of the volume slider in localStorage, since the
  volume belongs to this device rather than to the listener's profile."
  "dk.simongray.podcast-app.volume")

(defn saved-volume
  "The level of the volume slider that this browser keeps, or nil."
  []
  (try
    (some-> (.getItem js/localStorage volume-key) js/parseFloat
            (#(when (js/Number.isFinite %) %)))
    (catch :default _ nil)))

;; A save of the library to IndexedDB takes a few steps, which a page that
;; reloads or closes never finishes, so the position also goes to
;; localStorage, which writes at once. The next start gives it to the
;; library with the time it was kept at, so the profile still decides.
(def position-key
  "The key of the last position of the player in localStorage."
  "dk.simongray.podcast-app.position")

(defn keep-position!
  "Keep the `position` and the `duration` of the episode `id` in this
  browser at once, with the time."
  [id position duration]
  (try
    (.setItem js/localStorage position-key
              (pr-str {:id id :position position :duration duration :at (js/Date.now)}))
    (catch :default _ nil)))

(defn kept-position
  "The last position of the player that this browser kept, as a map of the
  episode's :id, the :position and :duration in seconds, and the :at in
  milliseconds, or nil."
  []
  (try
    (some-> (.getItem js/localStorage position-key) reader/read-string)
    (catch :default _ nil)))

(defn save!
  "Save the library of the `store`, and note when."
  [store]
  (swap! store assoc-in [:player :saved] (js/Date.now))
  (-> (app-library/save! (:library @store))
      (.then #(when (:error %)
                (js/console.error "The library wasn't saved" (clj->js %))))))

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
  "Run the player `event` through library/step in the `store`, and do its
  effects."
  [store event]
  (let [lib (library/step (:library @store) event)]
    (swap! store assoc :library (dissoc lib :effects))
    (apply-effects! store (:effects lib))))

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

(defn report!
  "Tell the library of the `store` where the element is, by its `media`
  state."
  [store {:keys [current-time duration] :as media}]
  (when-let [id (and (number? current-time) (get-in @store [:player :id]))]
    (let [duration (when (and (number? duration) (js/Number.isFinite duration))
                     duration)]
      (keep-position! id current-time duration)
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
  the whole second, and tell the library and save it when it's time."
  [store media]
  (let [{old :media :keys [reported saved]} (:player @store)
        media                               (player/whole-seconds media)]
    (when (not= media old)
      (swap! store assoc-in [:player :media] media))
    (when (player/report? old media reported)
      (report! store media))
    (when (player/save? old media saved (js/Date.now))
      (save! store))
    (buffering! store media)))

(defn answered!
  "Keep the browser's `answer` to a play in the player of the `store`, when
  it refused."
  [store answer]
  (swap! store assoc-in [:player :answer] (when (:refused answer) answer)))

(defn read-files!
  "Read the files of the episode `id` of the library of the `store`, unless
  the Episode page has, for the chapters of a chapters file or the media
  file, and show them in the player and on the lock screen."
  [store id]
  (when-not (get-in @store [:episodes id :status])
    (swap! store assoc-in [:episodes id :status] :reading)
    (-> (library/read-files! (:library @store) id {:except #{:transcript}})
        (.then (fn [result]
                 (swap! store update-in [:episodes id] assoc :status :read :result result)
                 (when (= id (get-in @store [:player :id]))
                   (swap! store assoc-in [:player :playback] (playback @store id))
                   (lock-screen! store))))
        (.catch #(swap! store update-in [:episodes id] assoc :status :read
                        :error {:message (ex-message %)})))))

(defn load!
  "Load the episode `id` of the library into the player of the `store`, at
  `t` seconds when it's given, or else where the listener left it, and
  return the element."
  [store id t]
  (let [pb (playback @store id)]
    (swap! store update :player assoc
           :id id :playback pb :answer nil :reported nil :seeking nil)
    (read-files! store id)
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
    (when (and same? el (number? t))
      (media/seek! el t))
    (when el
      (.then (media/play! el) #(answered! store %)))))

(defn added
  "The library of the `state` with the episode of the `playable` of an
  Episode page, as episode/playable gives it, unless it has it already."
  [{:keys [library] :as state} {:keys [id guid url item-guid]}]
  (if (library/playback library id)
    library
    (library/add-episode library url (:feed (episode/source state guid)) item-guid)))

(defn play!
  "Play the `playable` episode of an Episode page in the player of the
  `store`, from `t` seconds when it's given: first add it to the library,
  unless it's there, so that its progress is kept, and make it the current
  episode, at the front of the queue."
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
  (when-let [el (element)]
    (.then (media/play! el) #(answered! store %))))

(defn ended!
  "Tell the library that the episode of the player of the `store` ended,
  which plays the next one of the queue, and save it."
  [store]
  (when-let [id (get-in @store [:player :id])]
    (step! store [:ended id])
    (save! store)))

(defn chapter!
  "Go to the chapter in `direction`, :previous or :next, of the episode of
  the player of the `store`."
  [store direction]
  (let [{:keys [id playback]} (:player @store)
        el                    (element)]
    (when-let [doc (and id el (player/chapter-doc playback))]
      (report! store {:current-time (.-currentTime el) :duration (.-duration el)})
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
    (try (.setItem js/localStorage volume-key (str level)) (catch :default _ nil))))

(defn mute!
  "Mute the player, or unmute it where `muted?` is false."
  [muted?]
  (some-> (element) (media/muted! muted?)))

(defonce ^{:doc "The function that stops following the element."}
  detach!
  (atom (constantly nil)))

(defn mounted!
  "Follow the audio `element` that the player of the `store` mounted: keep
  its state, put it on the lock screen, let it pause when another tab
  plays, and follow the keys pressed in the player. Load the episode of a
  player that the page restored, where nothing loaded it yet."
  [store ^js element]
  (let [{:keys [id playback volume]} (:player @store)]
    (when (and id (not (.getAttribute element "src")))
      (media/source! element playback)
      (read-files! store id))
    (media/volume! element (or volume 1) {:curve :cubic})
    (reset! detach!
            (juxt (media/watch! element #(changed! store %)
                                {:events (conj media/events :timeupdate)})
                  (session/attach! element)
                  (tabs/attach! element player/element-name)
                  (controls/listen! (.closest element "#player") element
                                    (handlers store element))))
    (lock-screen! store)))

(defn with-kept-position
  "The `library` told of the position that this browser `kept`, as
  kept-position gives it, at the time it was kept, unless it hasn't that
  episode, or its profile has a later position, e.g. of another device.

  The profile orders a write of this device after every write that it has
  seen, whatever its time, so the times are compared here."
  [library {:keys [id position duration at] :as kept}]
  (let [updated (:updated-at (player/entry library id))]
    (if (and library id (number? position) (number? at)
             (library/playback library id)
             (or (not (inst? updated)) (> at (inst-ms updated))))
      (dissoc (library/step library [:progress id position duration] {:at (js/Date. at)})
              :effects)
      library)))

(defn restored
  "The `state` as the page loads: its library told of the position that
  this browser `kept`, and its player with the episode that the queue has
  at its front, paused where the listener left it."
  [state kept]
  (let [state (update state :library with-kept-position kept)
        id    (some-> state :library :profile queue/current)
        pb    (when id (playback state id))]
    (cond-> (update state :player assoc
                    :volume (or (saved-volume) 1))
      pb (update :player assoc :id id :playback pb))))

;; Page Lifecycle API: hidden is the last state that a page can count on
;; seeing, e.g. before a phone discards it, so the player reports and saves
;; there, and never at unload
;; https://developer.chrome.com/docs/web-platform/page-lifecycle-api
(defn hidden!
  "Tell the library where the player of the `store` is, and save it, as
  the page is hidden."
  [store]
  (when-let [el (and (get-in @store [:player :id]) (element))]
    (report! store {:current-time (.-currentTime el) :duration (.-duration el)})
    (save! store)))

(defn volume-locked!
  "Note in the player of the `store` whether the device locks the volume."
  [store]
  (.then (media/volume-locked!) #(swap! store assoc-in [:player :volume-locked?] (boolean %))))
