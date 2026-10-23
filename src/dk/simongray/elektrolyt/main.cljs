(ns dk.simongray.elektrolyt.main
  "The app in the browser: the store of its state, the actions that the
  views dispatch, and the start of the render loop.

  The views dispatch actions as data, and the router dispatches one for
  each navigation. Nexus expands them into effects, which alone change the
  store, and each change of the store renders the page again. The effects
  are the functions of the namespaces of their subjects, e.g. the player's
  in dk.simongray.elektrolyt.audio, and this namespace registers them."
  (:require [cognitect.transit :as transit]
            [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.podcast-app :as-alias app]
            [dk.simongray.elektrolyt.artwork-colors :as artwork-colors]
            [dk.simongray.elektrolyt.audio :as audio]
            [dk.simongray.elektrolyt.cache :as app-cache]
            [dk.simongray.elektrolyt.episode :as episode]
            [dk.simongray.elektrolyt.feeds :as feeds]
            [dk.simongray.elektrolyt.inbox :as inbox]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.local :as local]
            [dk.simongray.elektrolyt.opml :as opml]
            [dk.simongray.elektrolyt.page :as page]
            [dk.simongray.elektrolyt.queue :as queue]
            [dk.simongray.elektrolyt.refresh :as refresh]
            [dk.simongray.elektrolyt.routes :as routes]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.searches :as searches]
            [dk.simongray.elektrolyt.settings :as settings]
            [dk.simongray.elektrolyt.show :as show]
            [nexus.registry :as nxr]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend.history :as rfh]
            [replicant.dom :as r]))

(defonce ^{:doc "The whole state of the app, which only effects change."}
  store
  (atom {}))

(defn changed!
  "Make the `change` of operations/changed to the `store`, e.g. of the
  listener's queue, and save the library, unless the `change` is nil."
  [store change]
  (when change
    (app-library/change! store change)
    (audio/save! store)))

;; The app's former name, since a browser keeps the listener's data under it
(def library-view-key
  "The key of the listener's choices for the Library page in localStorage,
  since the layout that suits a screen belongs to this device."
  "dk.simongray.podcast-app.library-view")

(nxr/register-system->state! deref)

;; The values of the event that an action names

(nxr/register-placeholder! :form/field
  (fn [{:replicant/keys [dom-event]} field-name]
    (some-> dom-event .-target (js/FormData.) (.get field-name))))

(nxr/register-placeholder! :event/target-number
  (fn [{:replicant/keys [dom-event]}]
    (some-> dom-event .-target .-value js/parseFloat)))

(nxr/register-placeholder! :event/target-keyword
  (fn [{:replicant/keys [dom-event]}]
    (some-> dom-event .-target .-value not-empty keyword)))

(nxr/register-placeholder! :event/target-checked
  (fn [{:replicant/keys [dom-event]}]
    (some-> dom-event .-target .-checked)))

(nxr/register-placeholder! :event/range-seek
  (fn [{:replicant/keys [dom-event]}]
    (some-> dom-event controls/range-seek)))

;; The actions that expand into others

(nxr/register-expansion! :route/navigated routes/navigated)

(nxr/register-expansion! :search/submit search/submitted)

(nxr/register-expansion! :show/more show/more-shown)

;; The store and the page

(nxr/register-effect! :store/assoc
  (fn [_ store k v]
    (swap! store assoc k v)))

(nxr/register-effect! :store/assoc-in
  (fn [_ store path v]
    (swap! store assoc-in path v)))

(nxr/register-effect! :event/prevent-default
  (fn [{:keys [dispatch-data]} _]
    (some-> (:replicant/dom-event dispatch-data) .preventDefault)))

(nxr/register-effect! :route/push
  (fn [_ _ route-name & [path-params query-params]]
    (rfe/push-state route-name path-params query-params)))

(nxr/register-effect! :page/focus-heading
  (fn [_ _]
    (page/focus-heading!)))

(nxr/register-effect! :page/focus
  (fn [_ _ id]
    (page/focus! id)))

(nxr/register-effect! :page/show
  (fn [_ _ id]
    (page/show! id)))

(nxr/register-effect! :page/scroll-to
  (fn [_ _ id]
    (page/scroll-to! id)))

(nxr/register-effect! :dialog/open
  (fn [_ _ id]
    (page/open-dialog! id)))

;; Searches and shows

(nxr/register-effect! :search/run
  (fn [{:keys [dispatch]} store q]
    (searches/search! dispatch q (search/sources @store) (search/index-credentials @store))))

(nxr/register-effect! :search/retry
  (fn [{:keys [dispatch]} store q source]
    (searches/search-directory! dispatch q source (search/index-credentials @store) true)))

(nxr/register-effect! :show/load
  (fn [_ store guid url preview?]
    (feeds/load-show! store guid url preview?)
    (page/canonicalize! @store)))

(nxr/register-effect! :show/prefetch-soon
  (fn [_ store guid url]
    (feeds/prefetch-soon! store guid url)))

(nxr/register-effect! :show/prefetch-cancel
  (fn [_ _]
    (feeds/prefetch-cancel!)))

;; the button goes as the list takes the new episodes, so the focus moves
;; to the heading of the list
(nxr/register-effect! :show/newer
  (fn [_ store guid]
    (swap! store show/newer-shown guid)
    (page/focus! "episodes")))

;; dispatched as the shows that a show links to mount, so it changes the
;; store once the render is done
(nxr/register-effect! :show/fetch-linked
  (fn [_ store guid feed url]
    (js/queueMicrotask #(feeds/fetch-linked! store guid feed url))))

;; dispatched as the page of the episode mounts, so it changes the store
;; once the render is done
(nxr/register-effect! :episode/read-files
  (fn [_ store id view]
    (js/queueMicrotask #(feeds/read-files! store id view))))

;; dispatched as the open transcript mounts, as the files are
(nxr/register-effect! :episode/read-transcript
  (fn [_ store id view]
    (js/queueMicrotask #(feeds/read-transcript! store id view))))

;; only past the limit, since each change of the store renders the page
(nxr/register-effect! :episodes/forget
  (fn [_ store]
    (when (< (inc episode/kept-count) (count (:episodes @store)))
      (swap! store update :episodes episode/kept (get-in @store [:player :id]) episode/kept-count))))

;; The player

;; dispatched as the player's element mounts, so it changes the store once
;; the render is done
(nxr/register-effect! :player/mounted
  (fn [{:keys [dispatch-data]} store]
    (let [node (:replicant/node dispatch-data)]
      (js/queueMicrotask #(audio/mounted! store node)))))

(nxr/register-effect! :player/unmounted
  (fn [_ _]
    (runtime/detach-player!)))

(nxr/register-effect! :player/play
  (fn [_ store playable t]
    (audio/play! store playable t)))

(nxr/register-effect! :player/resume
  (fn [_ store]
    (audio/resume! store)))

;; the controls act on the playback that the player shows, wherever it
;; plays (see audio/act!)
(nxr/register-effect! :player/pause
  (fn [_ store]
    (audio/act! store [:pause])))

(nxr/register-effect! :player/press
  (fn [_ store details]
    (audio/act! store [:press details])))

(nxr/register-effect! :player/drag
  (fn [_ store details]
    (audio/act! store [:drag details])))

(nxr/register-effect! :player/chapter
  (fn [_ store direction]
    (audio/act! store [:chapter direction])))

(nxr/register-effect! :player/ended
  (fn [_ store]
    (audio/ended! store)))

(nxr/register-effect! :player/rate
  (fn [_ store rate]
    (audio/act! store [:rate rate])))

(nxr/register-effect! :player/volume
  (fn [_ store level]
    (audio/act! store [:volume level])))

(nxr/register-effect! :player/mute
  (fn [_ store muted?]
    (audio/act! store [:mute muted?])))

(nxr/register-effect! :player/expand
  (fn [_ store expanded?]
    (swap! store assoc-in [:player :expanded?] expanded?)))

;; The library

(nxr/register-effect! :library/subscribe
  (fn [_ store url title]
    (feeds/subscribe! store url title)))

(nxr/register-effect! :library/unsubscribe
  (fn [_ store url title]
    (feeds/unsubscribe! store url title)))

(nxr/register-effect! :library/view
  (fn [_ store k v]
    (local/store-edn! library-view-key (:library-view (swap! store assoc-in [:library-view k] v)))))

(nxr/register-effect! :library/refresh
  (fn [_ store]
    (refresh/refresh! store true)))

(nxr/register-effect! :queue/add
  (fn [_ store {:keys [id] :as playable} title next?]
    (try
      (app-library/change! store (audio/added @store playable))
      (changed! store (queue/added id title next?))
      (catch :default e
        (js/console.error "The episode couldn't be queued" e)))))

(nxr/register-effect! :queue/move
  (fn [_ store id to]
    (changed! store (queue/moved @store id to))))

(nxr/register-effect! :queue/remove
  (fn [_ store id]
    (changed! store (queue/removed @store id))))

(nxr/register-effect! :queue/undo
  (fn [_ store]
    (changed! store (queue/undone @store))))

;; once the dialog has closed, since the page is inert while it's open
(nxr/register-effect! :queue/clear-closed
  (fn [{:keys [dispatch-data]} store focus-id]
    (when-let [dialog (some-> (:replicant/dom-event dispatch-data) .-target)]
      (when (= "clear" (.-returnValue dialog))
        (set! (.-returnValue dialog) "")
        (changed! store (queue/cleared @store))
        (page/focus! focus-id)))))

(nxr/register-effect! :inbox/dismiss
  (fn [_ store id]
    (changed! store (inbox/dismissed @store id))))

(nxr/register-effect! :inbox/dismiss-all
  (fn [_ store]
    (changed! store (inbox/all-dismissed @store))))

(nxr/register-effect! :inbox/queue-all
  (fn [_ store]
    (changed! store (inbox/all-queued @store))))

(nxr/register-effect! :inbox/undo
  (fn [_ store]
    (changed! store (inbox/undone @store))))

;; The settings

(nxr/register-effect! :settings/prefer
  (fn [_ store k v]
    (when (or (boolean? v) (keyword? v) (js/Number.isFinite v))
      (changed! store (settings/preferred {k v}))
      ;; DR's own feeds are fetched again at once, with or without the
      ;; episodes of drpodcast.nu
      (when (= ::app/alternative-feeds? k)
        (refresh/refresh-if-due! store)))))

(nxr/register-effect! :settings/search-source
  (fn [_ store source on?]
    (changed! store (settings/turned @store source (boolean on?)))))

(nxr/register-effect! :settings/move-source
  (fn [_ store source to]
    (changed! store (settings/moved-source @store source to))))

;; the input is emptied, so that choosing the same file again imports it
;; again
(nxr/register-effect! :opml/import
  (fn [{:keys [dispatch-data]} store]
    (when-let [input (some-> (:replicant/dom-event dispatch-data) .-target)]
      (when-let [file (some-> input .-files (aget 0))]
        (opml/import! store file)
        (set! (.-value input) "")))))

(nxr/register-effect! :opml/export
  (fn [_ store]
    (opml/export! @store)))

(nxr/register-effect! :storage/check
  (fn [_ store]
    (app-library/check-storage! store)))

(nxr/register-effect! :storage/persist
  (fn [_ store]
    (.then (or (app-library/persist!) (js/Promise.resolve false))
           (fn [granted?]
             (when-not granted?
               (swap! store assoc :settings-status
                      {:group :this-browser
                       :text  "The browser didn't agree to keep it."}))
             (app-library/check-storage! store)))))

(nxr/register-effect! :cache/clear
  (fn [_ store]
    (-> (app-cache/clear!)
        (.then #(swap! store settings/cleared nil))
        (.catch (fn [e]
                  (js/console.error "The cache wasn't cleared" e)
                  (swap! store settings/cleared e)))
        (.then #(app-library/check-storage! store)))))

;; The start

(defn routed-click?
  "Whether the `router` takes over the `click` on the `anchor` to `uri`: when
  reitit would, unless a handler of the app has already prevented the
  click's default action, as the skip link does."
  [router ^goog.events.BrowserEvent click anchor uri]
  (and (not (.. click getBrowserEvent -defaultPrevented))
       (rfh/ignore-anchor-click? router click anchor uri)))

(defn start-router!
  "Follow the paths of the window's history, which dispatches the navigation
  to the current one at once."
  []
  (runtime/follow-history! routes/router
                           (fn [match _]
                             (nxr/dispatch store {} [[:route/navigated
                                                      (routes/match->route match)]]))
                           {:use-fragment         false
                            :ignore-anchor-click? routed-click?}))

(defn restore-library!
  "Read the library that this browser wrote into the store, which then
  reads what the other tabs write, as app-library/open! has it.

  A library that doesn't restore stays out of the store rather than be
  replaced by a new one, which the next save would write over the old."
  []
  (-> (app-library/open! store)
      ;; the title of a show that only the library keeps is known now
      (.then #(page/canonicalize! @store))
      (.catch #(js/console.error "The library didn't restore" %))))

(defn load-server-info!
  "Read what the app's server offers from /api/server into the :server of
  the store, e.g. whether it signs for the Podcast Index, as a promise that
  settles either way."
  []
  (-> (js/fetch "/api/server")
      (.then #(.text %))
      (.then (fn [text]
               (nxr/dispatch store {} [[:store/assoc :server
                                        (transit/read (transit/reader :json) text)]])))
      (.catch #(js/console.error "The server didn't say what it offers" %))))

(defn ^:dev/after-load reload!
  "Start the router again with the routes and views that shadow-cljs just
  reloaded, which renders the page."
  []
  (start-router!))

(defn ^:export init
  "Start the app: send the actions of the views to Nexus, follow the
  playback of the other tabs, restore the library, and once the server
  has said what it offers, put the current episode in the player, render
  the page at each change of the store and follow the window's history.
  Tell the library where the player is, and save it, when the page is
  hidden while it plays, and the other tabs that it stops as it goes.

  The first page waits for both, since what it shows can depend on them,
  e.g. a search asks the Podcast Index too when the server signs for it,
  only the Podcast Index finds a show by its guid alone, and a show that
  the library keeps needs neither the index nor a fetch. It waits for the
  colours that the cache keeps of its artwork too, so that it opens in
  them rather than turns to them."
  []
  (r/set-dispatch! #(nxr/dispatch store %1 %2))
  (audio/listen! store)
  (runtime/on-hidden! #(audio/hidden! store))
  (runtime/on-visible! #(refresh/refresh-if-due! store))
  (runtime/on-pagehide! #(do (audio/hidden! store)
                             (audio/gone! store)))
  ;; the bars' colour once it has moved to the next one
  (runtime/on-transitionend! #(when (.. % -classList (contains "shell"))
                                (page/theme-colored!)))
  (audio/volume-locked! store)
  (swap! store assoc :library-view (local/stored-edn library-view-key))
  (-> (js/Promise.all #js [(restore-library!) (load-server-info!)])
      (.finally #(audio/restored! store))
      (.then #(artwork-colors/kept-colors! store) #(artwork-colors/kept-colors! store))
      (.finally (fn []
                  (runtime/watch! store ::render (fn [_ state] (page/render! state)))
                  (runtime/watch! store ::colors (fn [_ state] (artwork-colors/colored! store state)))
                  (start-router!)
                  (refresh/refresh-if-due! store)))))
