(ns dk.simongray.elektrolyt
  "The app in the browser: its store, its Nexus actions and effects, and
  its start.

  The views and the router dispatch actions as data. Nexus expands them
  into effects, which alone change the store, and each change of the
  store renders the page again."
  (:require [cognitect.transit :as transit]
            [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.elektrolyt.episode :as episode]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.library.inbox :as inbox]
            [dk.simongray.elektrolyt.library.opml :as opml]
            [dk.simongray.elektrolyt.library.picks.finding :as picks-finding]
            [dk.simongray.elektrolyt.library.queue :as queue]
            [dk.simongray.elektrolyt.library.refresh :as refresh]
            [dk.simongray.elektrolyt.library.saves :as saves]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.player.audio :as audio]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.runtime.cache :as app-cache]
            [dk.simongray.elektrolyt.runtime.local :as local]
            [dk.simongray.elektrolyt.runtime.log :as log]
            [dk.simongray.elektrolyt.runtime.perf :as perf]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.search.directories :as directories]
            [dk.simongray.elektrolyt.show :as show]
            [dk.simongray.elektrolyt.show.feeds :as feeds]
            [dk.simongray.elektrolyt.ui :as ui]
            [dk.simongray.elektrolyt.ui.artwork.colors :as artwork-colors]
            [dk.simongray.elektrolyt.ui.controls :as controls-ui]
            [dk.simongray.elektrolyt.ui.document :as document]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [nexus.registry :as nxr]
            [reitit.frontend.easy :as rfe]
            [reitit.frontend.history :as rfh]
            [replicant.dom :as r]
            [taoensso.telemere :as tel]))

(defonce ^{:doc "The whole state of the app, which only effects change."}
  store
  (atom {}))

(defn changed!
  "Make the `change` to `store` and save the library, unless `change` is
  nil."
  [store change]
  (when change
    (app-library/change! store change)
    (app-library/save! store)))

(def library-view-key
  "The localStorage key of the Library page's order and layout, kept on
  the device since a layout suits its screen."
  "dk.simongray.elektrolyt.library-view")

(nxr/register-system->state! deref)

;; Placeholders for values of the DOM event

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

(nxr/register-placeholder! :event/click-count
  (fn [{:replicant/keys [dom-event]}]
    (some-> dom-event .-detail)))

;; The actions that expand into others

(nxr/register-expansion! :route/navigated routes/navigated)

(nxr/register-expansion! :search/submit search/submitted)

(nxr/register-expansion! :show/more show/more-shown)

(nxr/register-expansion! :event/single-click controls-ui/unless-repeated)

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
    (document/focus-heading!)))

(nxr/register-effect! :page/focus
  (fn [_ _ id]
    (document/focus! id)))

(nxr/register-effect! :page/show
  (fn [_ _ id]
    (document/show! id)))

(nxr/register-effect! :page/scroll-to
  (fn [_ _ id]
    (document/scroll-to! id)))

(nxr/register-effect! :page/restore-scroll
  (fn [_ _ px]
    (document/restore-scroll! px)))

(nxr/register-effect! :dialog/open
  (fn [_ _ id]
    (document/open-dialog! id)))

(nxr/register-effect! :hover/dismiss-on-escape
  (fn [{:keys [dispatch-data]} _]
    (document/dismiss-on-escape! (:replicant/dom-event dispatch-data))))

(nxr/register-effect! :hover/restore-on-leave
  (fn [{:keys [dispatch-data]} _]
    (document/restore-on-leave! (:replicant/dom-event dispatch-data))))

;; Searches and shows

(nxr/register-effect! :search/run
  (fn [{:keys [dispatch]} store q]
    (directories/search! dispatch q (search/sources @store) (search/index-credentials @store))))

(nxr/register-effect! :search/retry
  (fn [{:keys [dispatch]} store q source]
    (directories/search-directory! dispatch q source (search/index-credentials @store) true)))

(nxr/register-effect! :search/listed
  (fn [_ store q source run l]
    (swap! store search/listed q source run l)))

(nxr/register-effect! :search/found
  (fn [_ store q run feeds]
    (swap! store search/found q run feeds)))

(nxr/register-effect! :show/load
  (fn [_ store guid url preview?]
    (feeds/load-show! store guid url preview?)
    (document/canonicalize! @store)))

(nxr/register-effect! :show/retry
  (fn [_ store guid url]
    (swap! store show/retried url)
    (feeds/load-show! store guid url false)))

(nxr/register-effect! :show/prefetch-soon
  (fn [_ store guid url & [ms]]
    (feeds/prefetch-soon! store guid url ms)))

(nxr/register-effect! :show/prefetch-cancel
  (fn [_ _]
    (feeds/prefetch-cancel!)))

(nxr/register-effect! :show/newer
  (fn [_ store guid]
    (swap! store show/newer-shown guid)))

;; Dispatched as the linked shows mount, so the store changes after the
;; render
(nxr/register-effect! :show/fetch-linked
  (fn [_ store guid feed url]
    (js/queueMicrotask #(feeds/fetch-linked! store guid feed url))))

;; Dispatched as the episode's page mounts, so the store changes after the
;; render
(nxr/register-effect! :episode/read-files
  (fn [_ store id view]
    (js/queueMicrotask #(feeds/read-files! store id view))))

;; Dispatched as the open transcript mounts, so the store changes after
;; the render
(nxr/register-effect! :episode/read-transcript
  (fn [_ store id view]
    (js/queueMicrotask #(feeds/read-transcript! store id view))))

;; Only past the limit, since each change of the store renders the page
(nxr/register-effect! :episodes/forget
  (fn [_ store]
    (when (< (inc episode/kept-count) (count (:episodes @store)))
      (swap! store update :episodes episode/kept (get-in @store [:player :id]) episode/kept-count))))

;; The player

;; Dispatched as the player's element mounts, so the store changes after
;; the render
(nxr/register-effect! :player/mounted
  (fn [{:keys [dispatch-data]} store]
    (let [node (:replicant/node dispatch-data)]
      (js/queueMicrotask #(audio/mounted! store node)))))

;; Acted on at once, though the report changes the store during the render,
;; which then renders again at the next frame: the browser pauses a removed
;; element at its next stable state, after which nothing would be reported
;; https://html.spec.whatwg.org/multipage/media.html#playing-the-media-resource
(nxr/register-effect! :player/unmounted
  (fn [{:keys [dispatch-data]} store]
    (audio/unmounted! store (:replicant/node dispatch-data))))

(nxr/register-effect! :player/play
  (fn [_ store playable t]
    (audio/play! store playable t)))

(nxr/register-effect! :player/resume
  (fn [_ store]
    (audio/resume! store)))

;; The controls act on the playback that the player shows, in whichever
;; tab it plays
(nxr/register-effect! :player/pause
  (fn [_ store]
    (audio/act! store [:pause])))

(nxr/register-effect! :player/press
  (fn [_ store details]
    (audio/act! store [:press details])))

(nxr/register-effect! :player/drag
  (fn [_ store details]
    (audio/act! store [:drag details])))

(nxr/register-effect! :player/released
  (fn [_ store]
    (audio/released! store)))

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

;; The library

(nxr/register-effect! :library/subscribe
  (fn [_ store url title]
    (feeds/subscribe! store url title)))

(nxr/register-effect! :library/unsubscribe
  (fn [_ store url title]
    (feeds/unsubscribe! store url title)))

;; the tiles' artwork moves to its new place (look.css)
(nxr/register-effect! :library/view
  (fn [_ store k v]
    (runtime/transition!
     #(local/store-edn! library-view-key
                        (:library-view (swap! store assoc-in [:library-view k] v))))))

(nxr/register-effect! :library/refresh
  (fn [_ store]
    (refresh/refresh! store true)))

;; The listener's Try again, which says so when it fails again
(nxr/register-effect! :library/save
  (fn [_ store]
    (let [before (:save-status @store)]
      (.then (app-library/save! store)
             #(when (:error %)
                (swap! store saves/tried-again before))))))

(nxr/register-effect! :page/reload
  (fn [_ _]
    (runtime/reload!)))

(nxr/register-effect! :queue/add
  (fn [_ store {:keys [id] :as playable} title next?]
    (try
      (app-library/change! store (audio/added @store playable))
      (changed! store (queue/added id title next?))
      (catch :default e
        (tel/log! {:level :error :id :queue/add-failed :error e}
                  "The episode couldn't be queued")))))

(nxr/register-effect! :queue/move
  (fn [_ store id to]
    (changed! store (queue/moved @store id to))))

(nxr/register-effect! :queue/remove
  (fn [_ store id]
    (changed! store (queue/removed @store id))))

(nxr/register-effect! :queue/undo
  (fn [_ store]
    (changed! store (queue/undone @store))))

;; Once the dialog has closed, since the rest of the page is inert while
;; it's open
(nxr/register-effect! :queue/clear-closed
  (fn [{:keys [dispatch-data]} store focus-id]
    (when-let [dialog (some-> (:replicant/dom-event dispatch-data) .-target)]
      (when (= "clear" (.-returnValue dialog))
        (set! (.-returnValue dialog) "")
        (changed! store (queue/cleared @store))
        (document/focus! focus-id)))))

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

;; a change of the colours cross-fades the page, rather than flip it
(nxr/register-effect! :settings/prefer
  (fn [_ store k v]
    (when (or (boolean? v) (keyword? v) (js/Number.isFinite v))
      (if (#{::settings/appearance ::settings/contrast} k)
        (runtime/transition! #(changed! store (settings/preferred {k v})))
        (changed! store (settings/preferred {k v})))
      ;; DR's own feeds are stale now, so a refresh fetches them at once
      (when (= ::settings/alternative-feeds? k)
        (refresh/refresh-if-due! store)))))

(nxr/register-effect! :settings/search-source
  (fn [_ store source on?]
    (changed! store (settings/turned @store source (boolean on?)))))

(nxr/register-effect! :settings/move-source
  (fn [_ store source to]
    (changed! store (settings/moved-source @store source to))))

;; Emptying the input lets the listener import the same file again
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
                  (tel/log! {:level :error :id :cache/clear-failed :error e}
                            "The cache wasn't cleared")
                  (swap! store settings/cleared e)))
        (.then #(app-library/check-storage! store))
        ;; a save that had no room may go through now
        (.then #(app-library/save! store)))))

;; The start

(defn routed-click?
  "Whether the `router` takes over the `click` on the `anchor` to `uri`:
  when reitit would, unless a handler already prevented the click's
  default action, e.g. the skip link's."
  [router ^goog.events.BrowserEvent click anchor uri]
  (and (not (.. click getBrowserEvent -defaultPrevented))
       (rfh/ignore-anchor-click? router click anchor uri)))

(defn start-router!
  "Follow the window's history and dispatch a navigation to each path,
  starting with the current one."
  []
  (document/decodable!)
  (runtime/keep-scroll-positions! "main")
  (runtime/follow-history! routes/router
                           (fn [match _]
                             (nxr/dispatch store {} [[:route/navigated
                                                      (routes/match->route match)
                                                      (runtime/scroll-position)]]))
                           {:use-fragment         false
                            :ignore-anchor-click? routed-click?}))

(defn restore-library!
  "Read this browser's saved library into the store and follow what the
  other tabs write. A library that fails to read stays out of the store,
  rather than be replaced by a new one that the next save would write
  over the old."
  []
  (-> (app-library/open! store)
      ;; the path can now carry the slug of a show that only the library
      ;; has
      (.then #(do (document/canonicalize! @store)
                  (opml/resume! store)))
      (.catch #(tel/log! {:level :error :id :library/restore-failed :error %}
                         "The library didn't restore"))))

(defn load-server-info!
  "Read what the app's server offers into :server in the store, e.g.
  whether it signs requests to the Podcast Index. The promise that it
  returns settles either way."
  []
  (-> (js/fetch "/api/server")
      (.then #(.text %))
      (.then (fn [text]
               (nxr/dispatch store {} [[:store/assoc :server
                                        (transit/read (transit/reader :json) text)]])))
      (.catch #(tel/log! {:level :error :id :server/info-failed :error %}
                         "The server didn't say what it offers"))))

(defn first-refresh!
  "Refresh the library if feeds are due, and mark the refresh's start and
  end on the page's timeline."
  []
  (perf/mark! "startup/refresh")
  (let [refreshed (refresh/refresh-if-due! store)]
    (if (instance? js/Promise refreshed)
      (.finally refreshed #(perf/mark! "startup/refreshed"))
      refreshed)))

(defn ^:dev/after-load reload!
  "Start the router again once shadow-cljs reloads the code, so that the
  page renders with the new routes and views."
  []
  (start-router!))

(defn ^:export init
  "Start the app in the page.

  The page's frame renders at once: the navigation, and the page's heading
  over a skeleton. The page itself waits for the library and for what the
  server offers, since it can depend on both, e.g. a search includes the
  Podcast Index only when the server signs for it. It waits for the cached
  colours of the page's artwork too, so that the page opens in them rather
  than turns to them."
  []
  (perf/mark! "startup/init")
  (log/start! js/window)
  (when ^boolean perf/on?
    (nxr/register-interceptor! perf/dispatch-interceptor))
  (r/set-dispatch! #(nxr/dispatch store %1 %2))
  ;; the page's frame at once, while the library is read
  (document/decodable!)
  (document/render! (ui/starting @store js/location.pathname))
  (perf/mark! "startup/frame")
  (runtime/hold-links! #(contains? @store :route)
                       #(document/render! (ui/starting @store %)))
  (audio/listen! store)
  (runtime/on-hidden! #(do (audio/hidden! store)
                           (app-library/save! store)))
  (runtime/on-visible! #(refresh/refresh-if-due! store))
  (runtime/on-pagehide! #(do (audio/hidden! store)
                             (audio/gone! store)))
  ;; the theme-color follows the bar's colour once its transition ends
  (runtime/on-transitionend! #(when (.. % -classList (contains "shell"))
                                (document/theme-colored!)))
  (audio/volume-locked! store)
  (swap! store assoc :library-view (local/stored-edn library-view-key))
  (-> (js/Promise.all #js [(.finally (restore-library!) #(perf/mark! "startup/library"))
                           (.finally (load-server-info!) #(perf/mark! "startup/server"))])
      (.finally #(audio/restored! store))
      (.then #(artwork-colors/kept-colors! store) #(artwork-colors/kept-colors! store))
      (.finally (fn []
                  (perf/mark! "startup/colors")
                  (runtime/watch! store ::render (perf/timed-render document/rendered!))
                  (runtime/watch! store ::colors
                                  (fn [_ state]
                                    (perf/measured! "colors" {:route (get-in state [:route :name])}
                                                    #(artwork-colors/colored! store state))))
                  (runtime/watch! store ::picks (fn [_ state] (picks-finding/found! store state)))
                  (runtime/watch! store ::lock-screen (audio/lock-screen-watch store))
                  (start-router!)
                  (perf/mark! "startup/page")
                  ;; after the first page paints, which a refresh's status
                  ;; would else render again first
                  (.then (runtime/painted!)
                         (fn []
                           (perf/mark! "startup/painted")
                           (perf/started! (count (get-in @store [:library :feeds])))
                           (runtime/idle! 2000 #(first-refresh!))))))))
