(ns ^:runtime dk.simongray.elektrolyt.runtime
  "The app's listeners, timers, locks and channels in a tab, gathered in
  one place since they reach outside the store."
  (:require [cljs.reader :as reader]
            [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.page.tabs :as tabs]
            [dk.simongray.play-nice.session :as session]
            [reitit.frontend.easy :as rfe]))

;; Timers

(defonce ^{:doc "The waiting timeouts and intervals by name, each a map of
  the browser's :id and whether it's an :interval?."}
  timers
  (atom {}))

(defn cancel!
  "Cancel the timer `k`, if it's waiting."
  [k]
  (when-let [{:keys [id interval?]} (get @timers k)]
    (if interval? (js/clearInterval id) (js/clearTimeout id))
    (swap! timers dissoc k)))

(defn later!
  "Call `f` in `ms` milliseconds as the timer `k`, replacing any timer of
  that name."
  [k ms f]
  (cancel! k)
  (let [id (js/setTimeout #(do (swap! timers dissoc k) (f)) ms)]
    (swap! timers assoc k {:id id})))

(defn every!
  "Call `f` every `ms` milliseconds as the timer `k`, replacing any timer
  of that name, until it's cancelled."
  [k ms f]
  (cancel! k)
  (swap! timers assoc k {:id (js/setInterval f ms) :interval? true}))

(defn waiting?
  "Whether the timer `k` is waiting."
  [k]
  (contains? @timers k))

(defn soon!
  "Call `f` after the current task."
  [f]
  (js/setTimeout f 0))

;; HTML Living Standard, the event loop's processing model: the browser
;; paints between tasks, right after the callbacks of requestAnimationFrame,
;; so a task queued from one runs once the frame is painted. A hidden page
;; gets no frames, so a timeout stands in for them.
;; https://html.spec.whatwg.org/multipage/webappapis.html#event-loop-processing-model
;; https://developer.mozilla.org/en-US/docs/Web/API/Window/requestAnimationFrame
;; https://web.dev/articles/optimize-long-tasks
(defn painted!
  "A promise that resolves once the browser has painted the page as it is,
  e.g. a button that changed when it was pressed, before a long task, or
  after the current task where nothing paints."
  []
  (js/Promise.
   (fn [resolve]
     (if (exists? js/requestAnimationFrame)
       (do (js/requestAnimationFrame #(soon! resolve))
           (js/setTimeout resolve 100))
       (soon! resolve)))))

;; Safari has requestIdleCallback only behind a setting, and there work
;; after the current task would run before the page paints what changed,
;; e.g. a feed encoded for the cache before the Show page's list shows
;; https://caniuse.com/requestidlecallback
(defn idle!
  "Call `f` once the page is idle, or after `ms` milliseconds at the
  latest, or once the page has painted in a browser without
  requestIdleCallback."
  [ms f]
  (if (exists? js/requestIdleCallback)
    (js/requestIdleCallback f #js {:timeout ms})
    (.then (painted!) (fn [_] (f)))))

;; Prioritized Task Scheduling, scheduler.yield(): the rest of a long piece
;; of work goes on ahead of other tasks, once the input that waits is
;; handled. Safari has no scheduler, so a message on a channel of its own
;; stands in, which waits behind the other tasks, but not for a timer's
;; clamp as setTimeout does.
;; https://developer.chrome.com/blog/use-scheduler-yield
;; https://developer.mozilla.org/en-US/docs/Web/API/Scheduler/yield
(defn yielded!
  "A promise that resolves once the browser has handled what waits, e.g.
  a click, between parts of a long piece of work."
  []
  (if (some-> js/globalThis .-scheduler .-yield)
    (.yield js/scheduler)
    (js/Promise.
     (fn [resolve]
       (let [channel (js/MessageChannel.)]
         (set! (.. channel -port1 -onmessage) (fn [_] (resolve)))
         (.postMessage (.-port2 channel) nil))))))

;; Channels

(defonce ^{:doc "The BroadcastChannels of the app by name, each opened at
  its first use."}
  channels
  (atom {}))

(defn channel
  "The BroadcastChannel `name`, opened at its first use, or nil in a
  browser without them."
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
  "Call `f` with each map that another tab sends as EDN on the channel
  `name`, ignoring anything else, e.g. a message from another version of
  the app."
  [name f]
  (some-> ^js (channel name)
          (.addEventListener "message"
                             (fn [^js e]
                               (let [message (try (reader/read-string (.-data e))
                                                  (catch :default _ nil))]
                                 (when (map? message)
                                   (f message)))))))

;; The page

;; Page Lifecycle API: the change to hidden is the last event that's sure
;; to fire, and the back/forward cache shows a page again without loading
;; it
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

;; for a motion that CSS can't gate, e.g. a scroll that a script makes,
;; which would else smooth every scroll of its box
(defn motion?
  "Whether the listener allows motion, as prefers-reduced-motion says."
  []
  (.-matches (js/matchMedia "(prefers-reduced-motion: no-preference)")))

(defn on-pagehide!
  "Call `f` whenever the page unloads or goes into the back/forward
  cache."
  [f]
  (.addEventListener js/window "pagehide" (fn [_] (f))))

(defn on-pageshow!
  "Call `f` whenever the back/forward cache shows the page again."
  [f]
  (.addEventListener js/window "pageshow" #(when (.-persisted %) (f))))

(defn leaving
  "Ask the browser to confirm, with its own words, that the page goes, by
  the `event` of beforeunload."
  [^js event]
  (.preventDefault event)
  ;; for the browsers before Chrome 119
  (set! (.-returnValue event) true))

;; MDN, beforeunload: listen only while there's something to lose, since
;; Firefox keeps no page with the listener in its back/forward cache
;; https://developer.mozilla.org/en-US/docs/Web/API/Window/beforeunload_event
(defn warn-before-leaving!
  "Have the browser ask before the page goes, e.g. as its tab closes, when
  `on?`, and not otherwise."
  [on?]
  (if on?
    (.addEventListener js/window "beforeunload" leaving)
    (.removeEventListener js/window "beforeunload" leaving)))

(defn reload!
  "Load the page again. While changes aren't saved, the browser asks
  first, e.g. at the Reload of a newer version, since a reload loses
  them."
  []
  (.reload js/location))

(defn on-transitionend!
  "Call `f` with each element of the document whose CSS transition ends."
  [f]
  (.addEventListener js/document "transitionend" #(f (.-target %))))

(defn follow-history!
  "Follow the window's history with the reitit `router` and `opts`,
  calling `on-navigate` with each match, the current one first."
  [router on-navigate opts]
  (rfe/start! router on-navigate opts))

;; Before the router starts, a link of the frame would load the page again
;; and start the wait over
(defn hold-links!
  "Until `routed?` gives true, keep a plain click on a link of the
  navigation from loading its page: push its path to the history instead,
  and call `f` with the path, e.g. to render the frame there."
  [routed? f]
  (letfn [(held [^js e]
            (if (routed?)
              (.removeEventListener js/document "click" held)
              (when-let [^js a (some-> ^js (.-target e) (.closest "nav a[href]"))]
                (when-not (or (pos? (.-button e)) (.-metaKey e) (.-ctrlKey e)
                              (.-shiftKey e) (.-altKey e))
                  (.preventDefault e)
                  (.pushState js/history nil "" (.-href a))
                  (f (.-pathname a))))))]
    (.addEventListener js/document "click" held)))

(defonce ^{:doc "The scroll position of the main content as each history
  entry was left, by the entry's key."}
  scroll-positions
  (atom {}))

;; HTML, the Navigation API: navigate fires at a push, a replace or a
;; traversal before the URL changes, while currentEntry is still the entry
;; that's left. The browser's own restore of an inner scroller comes during
;; a traversal, while the page that's left is still rendered, so the app
;; keeps the positions itself. Without the API, e.g. in Safari before 26.2,
;; Back goes to the top.
;; https://html.spec.whatwg.org/multipage/nav-history-apis.html#navigation-api
(defn keep-scroll-positions!
  "Keep the scroll position of the element `id` for each history entry as
  it's left, where the browser has the Navigation API."
  [id]
  (when-let [^js navigation (.-navigation js/globalThis)]
    (.addEventListener navigation "navigate"
                       (fn [_]
                         (when-let [element (js/document.getElementById id)]
                           (swap! scroll-positions assoc
                                  (.. navigation -currentEntry -key)
                                  (.-scrollTop element)))))))

(defn scroll-position
  "The scroll position kept for the current history entry, or nil."
  []
  (when-let [k (some-> js/globalThis .-navigation .-currentEntry .-key)]
    (get @scroll-positions k)))

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

(defonce ^{:doc "The function that stops following the player's audio
  element, or nil."}
  detach
  (atom nil))

(defn detach-player!
  "Stop following the player's audio element and clear the lock screen's
  buttons."
  []
  (when-let [f @detach]
    (reset! detach nil)
    (f)))

(defn attach-player!
  "Follow the player's audio `element` with play-nice, in place of any
  element before it: report its media state, put it on the lock screen,
  pause it when another tab takes the play lock, and follow the keys
  pressed in the player. It takes these options:

  - :on-media and :on-report, functions called with the element's media
    state at each of its events
  - :lock, the name of the play lock across the tabs
  - :keys-element, the element whose keys control the player, and
    :handlers, the functions of the keys' actions"
  [element {:keys [on-media on-report lock keys-element handlers]}]
  (detach-player!)
  (let [events {:events (conj media/events :timeupdate)}]
    (reset! detach
            (juxt (media/watch! element on-media events)
                  (media/watch! element on-report events)
                  (session/attach! element)
                  ;; clears the buttons that lock-screen! set, since
                  ;; session/attach! clears only the :actions it's given
                  #(session/actions! {})
                  (tabs/attach! element lock)
                  (controls/listen! keys-element element handlers)))))

(defn lock-screen!
  "Show the episode's `metadata` on the lock screen, with `actions` as the
  handlers of its buttons."
  [metadata actions]
  (session/metadata! metadata)
  (session/actions! actions))
