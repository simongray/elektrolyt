(ns dk.simongray.elektrolyt.library
  "The listener's library on this device, kept in IndexedDB between visits
  and shared by the tabs.

  The library has a database of its own, since a write deletes the
  documents that it no longer needs. The device's id lives in localStorage
  instead, so that a save that fails makes no new device at the next visit.

  A tab applies each change to its own library at once. A save later
  writes the waiting operations if the stored version is still the one
  that the tab knows, or else applies them again to the stored library.
  Then it tells the other tabs the new version on a BroadcastChannel, so
  that they read it. The documents are in Transit, which reads about 30
  times faster than EDN."
  (:require [dk.simongray.like-minded.device :as device]
            [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.library.operations :as operations]
            [dk.simongray.elektrolyt.library.saves :as saves]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.wary-fetch.proxy :as proxy]
            [taoensso.telemere :as tel]))

(def send
  "The function that sends the library's requests through the proxy on the
  app's server."
  (proxy/sender "/proxy"))

;; The app's former name, which existing data is stored under
(def device-key
  "The localStorage key of this browser's device id."
  "dk.simongray.podcast-app.device")

(def store
  "The library's IndexedDB database."
  (indexeddb/store "dk.simongray.podcast-app.library"))

;; The app offers no choice of where new episodes go, so they all go to the
;; inbox. A show's own choice from an earlier version or another app stays
;; in its settings, for the apps that offer one.
(def config
  "The library's config, the same in every tab and the worker."
  {:new-episodes :inbox})

(def options
  "The options of every call to the library: its requests go through the
  proxy and its documents are in Transit. They're functions, so they stay
  out of its config."
  {:send  send
   :codec transit/codec})

(def document-name
  "The name that podcast-clj gives the library's own document in the store."
  (str "library" (:extension transit/codec)))

(def channel-name
  "The name of the BroadcastChannel that carries each version of the
  library that a tab writes."
  "dk.simongray.elektrolyt.library")

(defonce ^{:doc "The library that this tab last read or wrote, and the
  version of its document, as a map of the :library and the :version, or
  nil before the first read."}
  known
  (atom nil))

(defonce ^{:doc "The operations that this tab has applied to its library
  but not written yet, in order, each a map of the :operation and the
  :opts it was applied with."}
  waiting
  (atom []))

(defonce ^{:doc "The promise of this tab's last read or write, and of the
  save that waits its turn after it, if any."}
  turns
  (atom {:last (js/Promise.resolve nil) :next nil}))

(defn applied
  "The result of the `operation` on the `library`, applied with the
  library's options and the `opts`."
  [library operation opts]
  (operations/applied library operation (merge options opts)))

(defn applied-all
  "The `library` with the `ops` applied in order, each a map of an
  :operation and its :opts. An operation that throws, e.g. a subscription
  that another tab made first, is logged and left out."
  [library ops]
  (reduce (fn [lib {:keys [operation opts]}]
            (try
              (:library (applied lib operation opts))
              (catch :default e
                (tel/log! {:level :warn :id :library/operation-failed
                           :data  {:operation operation} :error e}
                          "An operation didn't apply to the stored library")
                lib)))
          library
          ops))

(defn in-turn!
  "A promise of what `f` returns, called once this tab's earlier reads and
  writes are done."
  [f]
  (let [p (.then (:last @turns) (fn [_] (f)))]
    (swap! turns assoc :last (.catch p (constantly nil)))
    p))

(defn announce!
  "Tell the other tabs that this one wrote the `version` of the library."
  [version]
  (runtime/post! channel-name {:version version}))

(defn written!
  "A promise of writing this tab's waiting operations to the store, from
  the library in the `state` atom, or of nil when none wait.

  When another tab wrote first, the operations are applied again to the
  stored library, which then goes into `state` with any newer operations
  on top. A write that fails keeps its operations waiting."
  [state]
  (let [ops @waiting
        {:keys [library] :as k} @known]
    (if (or (empty? ops) (nil? k))
      (js/Promise.resolve nil)
      (let [shown (:library @state)
            kept! #(swap! waiting (fn [later] (into ops later)))]
        (reset! waiting [])
        (-> (library/durable-swap! store k
                                   #(if (identical? % library) shown (applied-all % ops))
                                   options)
            (.then (fn [{:keys [error] :as written}]
                     (if error
                       (kept!)
                       (do (reset! known written)
                           (when-not (identical? (:library written) shown)
                             (swap! state assoc :library
                                    (applied-all (:library written) @waiting)))
                           (announce! (:version written))))
                     written))
            (.catch (fn [e]
                      (kept!)
                      {:error {:id :library/save-failed :reason (ex-message e)}})))))))

(defn retry-ms
  "How long to wait before a save is tried again after `n` writes in a row
  failed: five seconds after the first, twice as long after each one more,
  and five minutes at most."
  [n]
  (min 300000 (* 5000 (js/Math.pow 2 (dec n)))))

(declare save!)

(defn retried!
  "The result `written` of a write. If it failed, it's logged, the save
  status of the `state` atom notes it, and the atom is saved again later.
  Otherwise the save status goes. While the page tells of the status, the
  browser asks before the page goes."
  [state written]
  (let [before (:save-status @state)
        status (when-let [error (:error written)]
                 (saves/failed before error (or (-> @waiting first :opts :at) (js/Date.))))]
    (if status
      (do (tel/log! {:level :error :id :library/save-failed :data written}
                    "The library wasn't saved")
          (runtime/later! ::retry (retry-ms (:failures status)) #(save! state)))
      (runtime/cancel! ::retry))
    (when (not= before status)
      (swap! state saves/noted status)
      (runtime/warn-before-leaving! (saves/shown? status))))
  written)

;; A write can fail for a while, e.g. while the device is out of room, and
;; the operations that wait would be lost with the tab
(defn save!
  "A promise of writing this tab's waiting operations from the `state`
  atom once its earlier reads and writes are done. A write that fails is
  logged and tried again later. A save asked for while another waits its
  turn joins that one."
  [state]
  (or (:next @turns)
      (let [p (in-turn! #(do (swap! turns assoc :next nil)
                             (.then (written! state) (partial retried! state))))]
        (swap! turns assoc :next p)
        p)))

(defn change!
  "Make the `change` to the `state` atom with the `opts`, and return what
  its operation returned, e.g. the :effects of a step beside the library.
  It's applied at the current time unless the `opts` give an :at, and its
  operation waits for the next save.

  An operation that throws changes nothing, and its error reaches the
  caller."
  ([state change]
   (change! state change {}))
  ([state {:keys [operation ui] :or {ui identity} :as change} opts]
   (when change
     (let [before (:library @state)
           opts   (merge {:at (js/Date.)} opts)
           given  (when operation
                    (applied before operation opts))]
       (when (and operation before)
         (swap! waiting conj {:operation operation :opts opts}))
       (swap! state #(ui (cond-> %
                           operation (assoc :library (:library given)))))
       given))))

(defn stored-version
  "A promise of the stored version of the library's document, taken from
  the store's listing so that no document is read."
  []
  (.then ((:list store))
         (fn [listed]
           (some #(when (= document-name (:name %)) (:version %)) listed))))

(defn caught-up!
  "A promise of reading the library that another tab wrote into the
  `state` atom, with the waiting operations applied, when the stored
  version isn't the one this tab knows. It waits for this tab's earlier
  reads and writes, and reads only what changed."
  [state]
  (in-turn!
   (fn []
     (when-let [{:keys [library version]} @known]
       (.then (stored-version)
              (fn [stored]
                (when (not= stored version)
                  (.then (library/read! library store options)
                         (fn [read]
                           (reset! known read)
                           (swap! state assoc :library
                                  (applied-all (:library read) @waiting))
                           read)))))))))

;; HTML Living Standard, 9.5 Broadcasting to other browsing contexts, and
;; Page Lifecycle: a hidden or frozen page, or one in the back/forward
;; cache, gets no messages, so it reads what it missed when it's shown again
;; https://developer.chrome.com/docs/web-platform/page-lifecycle-api
(defn listen!
  "Read what the other tabs write into the `state` atom whenever they
  announce it, and when the page is visible again or comes back from the
  back/forward cache."
  [state]
  (runtime/on-message! channel-name (fn [_] (caught-up! state)))
  (runtime/on-visible! #(caught-up! state))
  (runtime/on-pageshow! #(caught-up! state)))

(defn open!
  "A promise of reading this browser's library, or a new one, into the
  :library of the `state` atom, and of keeping it up to date with what the
  other tabs write. If the library can't be read, the promise fails and
  `state` gets no library."
  [state]
  ;; listening first, a write that another tab announces during the read
  ;; is read after it
  (listen! state)
  (in-turn!
   #(.then (library/read! (library/make (device/id! device-key) {:config config :codec transit/codec})
                          store
                          options)
           (fn [{:keys [library] :as read}]
             (reset! known read)
             (swap! state assoc :library (applied-all library @waiting))
             read))))

;; Storage Standard, sections 7.1 and 8. Firefox asks the user first, so the
;; request waits until the listener subscribes.
(defn persist!
  "Ask the browser to keep the site's storage when the device runs low."
  []
  (some-> js/navigator .-storage .persist))

(defn storage!
  "A promise of how the browser keeps the site's storage: a map of
  :supported?, :persisted? and the :usage in bytes."
  []
  (if-let [^js s (some-> js/navigator .-storage)]
    (.then (js/Promise.all #js [(.persisted s) (.estimate s)])
           (fn [[persisted estimate]]
             {:supported? true
              :persisted? persisted
              :usage      (.-usage estimate)}))
    (js/Promise.resolve {:supported? false})))

(defn check-storage!
  "A promise of updating the :storage of the `state` atom with how the
  browser keeps the site's storage now."
  [state]
  (.then (storage!) #(swap! state assoc :storage %)))
