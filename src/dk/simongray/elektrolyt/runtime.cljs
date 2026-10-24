(ns ^:runtime dk.simongray.elektrolyt.runtime
  "Every listener, timer, lock and channel of the app in a tab, so that
  what reaches into the page from outside the store is in one file. The
  rest of the app asks for them here:

  - timers by name, e.g. :refresh, which replace the timer of the same
    name, and which timers holds while they wait
  - the BroadcastChannels by name, on which the tabs of the app post EDN
  - the listeners on the document, the window, the history and the store
  - the audio element of the player, which play-nice follows, with the
    play lock across the tabs, and the lock screen

  The web worker, its messages and its Web Lock are in
  dk.simongray.elektrolyt.worker.fetching, which is runtime too, and
  localStorage is in dk.simongray.elektrolyt.runtime.local. What each of
  them holds and does is in STATE.md."
  (:require [cljs.reader :as reader]
            [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.page.tabs :as tabs]
            [dk.simongray.play-nice.session :as session]
            [reitit.frontend.easy :as rfe]))

;; Timers

(defonce ^{:doc "The timeouts and intervals that wait, by name, as maps of
  the :id that the browser gave and whether it's an :interval?."}
  timers
  (atom {}))

(defn cancel!
  "Cancel the timer `k`, if it waits."
  [k]
  (when-let [{:keys [id interval?]} (get @timers k)]
    (if interval? (js/clearInterval id) (js/clearTimeout id))
    (swap! timers dissoc k)))

(defn later!
  "Call `f` in `ms` milliseconds as the timer `k`, in place of the timer of
  that name, if one waits."
  [k ms f]
  (cancel! k)
  (let [id (js/setTimeout #(do (swap! timers dissoc k) (f)) ms)]
    (swap! timers assoc k {:id id})))

(defn every!
  "Call `f` every `ms` milliseconds as the timer `k`, in place of the timer
  of that name, until it's cancelled."
  [k ms f]
  (cancel! k)
  (swap! timers assoc k {:id (js/setInterval f ms) :interval? true}))

(defn waiting?
  "Whether the timer `k` waits."
  [k]
  (contains? @timers k))

(defn soon!
  "Call `f` once the browser has done what it does now."
  [f]
  (js/setTimeout f 0))

;; requestIdleCallback, where the browser has it, with a timeout so that a
;; busy page gets to it too
(defn idle!
  "Call `f` once the page is idle, or after `ms` milliseconds at the
  latest, or soon! where the browser can't say when it's idle."
  [ms f]
  (if (exists? js/requestIdleCallback)
    (js/requestIdleCallback f #js {:timeout ms})
    (soon! f)))

;; Channels

(defonce ^{:doc "The BroadcastChannels of the app by name, each opened at
  its first use."}
  channels
  (atom {}))

(defn channel
  "The BroadcastChannel `name`, opened at its first use, or nil where the
  browser has none."
  [name]
  (when (exists? js/BroadcastChannel)
    (or (get @channels name)
        (let [ch (js/BroadcastChannel. name)]
          (swap! channels assoc name ch)
          ch))))

(defn post!
  "Send the `message` to the other tabs on the channel `name`, as EDN."
  [name message]
  (some-> ^js (channel name) (.postMessage (pr-str message))))

(defn on-message!
  "Call `f` with each message that another tab sends on the channel
  `name`, read as EDN, unless it isn't a map, e.g. a message of another
  version of the app."
  [name f]
  (some-> ^js (channel name)
          (.addEventListener "message"
                             (fn [^js e]
                               (let [message (try (reader/read-string (.-data e))
                                                  (catch :default _ nil))]
                                 (when (map? message)
                                   (f message)))))))

;; The page

;; Page Lifecycle API: hidden is the last state that a page can count on
;; seeing, and a page that the back/forward cache restores is shown again
;; without loading
;; https://developer.chrome.com/docs/web-platform/page-lifecycle-api
(defn on-hidden!
  "Call `f` whenever the page is hidden."
  [f]
  (.addEventListener js/document "visibilitychange"
                     #(when (= "hidden" js/document.visibilityState) (f))))

(defn on-visible!
  "Call `f` whenever the page is visible again."
  [f]
  (.addEventListener js/document "visibilitychange"
                     #(when (= "visible" js/document.visibilityState) (f))))

(defn visible?
  "Whether the page is visible now."
  []
  (= "visible" js/document.visibilityState))

(defn on-pagehide!
  "Call `f` as the page goes, e.g. to another site or into the
  back/forward cache."
  [f]
  (.addEventListener js/window "pagehide" (fn [_] (f))))

(defn on-pageshow!
  "Call `f` whenever the back/forward cache shows the page again."
  [f]
  (.addEventListener js/window "pageshow" #(when (.-persisted %) (f))))

(defn on-transitionend!
  "Call `f` with the element whose CSS transition ended, of any element
  of the document."
  [f]
  (.addEventListener js/document "transitionend" #(f (.-target %))))

(defn follow-history!
  "Follow the paths of the window's history with the reitit `router`, which
  calls `on-navigate` with each match and with the current one at once, as
  rfe/start! does with the `opts`."
  [router on-navigate opts]
  (rfe/start! router on-navigate opts))

(defn watch!
  "Call `f` with the old and the new state at each change of the atom
  `store`, as the watch `k`."
  [store k f]
  (add-watch store k (fn [_ _ old new] (f old new))))

(defn unwatch!
  "Stop the watch `k` of the atom `store`."
  [store k]
  (remove-watch store k))

;; The player

(defonce ^{:doc "The function that stops following the audio element of
  the player, or nil."}
  detach
  (atom nil))

(defn detach-player!
  "Stop following the audio element of the player, and clear the buttons
  of the lock screen."
  []
  (when-let [f @detach]
    (reset! detach nil)
    (f)))

(defn attach-player!
  "Follow the audio `element` of the player, in place of any element before
  it, with the `opts` below, as play-nice does it: keep its media state,
  put it on the lock screen, let it pause when another tab takes the play
  lock, and follow the keys pressed in the player.

  - :on-media and :on-report, functions called with the media state of
    the element at each of its events
  - :lock, the name of the play lock across the tabs
  - :keys-element, the element of the player whose keys control it, and
    :handlers, the functions of the keys, as controls/listen! takes them"
  [element {:keys [on-media on-report lock keys-element handlers]}]
  (detach-player!)
  (let [events {:events (conj media/events :timeupdate)}]
    (reset! detach
            (juxt (media/watch! element on-media events)
                  (media/watch! element on-report events)
                  (session/attach! element)
                  ;; the buttons that lock-screen! set, which attach!
                  ;; leaves without its own :actions
                  #(session/actions! {})
                  (tabs/attach! element lock)
                  (controls/listen! keys-element element handlers)))))

(defn lock-screen!
  "Show the `metadata` of the episode on the lock screen, with the
  `actions`, the handlers of its buttons, as session/actions! takes them."
  [metadata actions]
  (session/metadata! metadata)
  (session/actions! actions))
