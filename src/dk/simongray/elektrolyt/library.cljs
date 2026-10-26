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
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.library.operations :as operations]
            [dk.simongray.elektrolyt.library.options :as library-options]
            [dk.simongray.elektrolyt.library.saves :as saves]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.runtime.perf :as perf]
            [taoensso.telemere :as tel]))

(def device-key
  "The localStorage key of this browser's device id."
  "dk.simongray.elektrolyt.device")

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
  (operations/applied library operation (merge library-options/options opts)))

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
        (perf/measured!
         "library/write" {:operations (count ops)}
         #(-> (library/durable-swap! library-options/store k
                                     (fn [stored]
                                       (if (identical? stored library)
                                         shown
                                         (applied-all stored ops)))
                                     library-options/options)
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
                        {:error {:id :library/save-failed :reason (ex-message e)}}))))))))

;; A write can fail for a while, e.g. while the device is out of room, and
;; the operations that wait would be lost with the tab
(defn retry-ms
  "How long to wait before a save is tried again after `n` writes in a row
  failed: five seconds after the first, twice as long after each one more,
  and five minutes at most."
  [n]
  (min 300000 (* 5000 (js/Math.pow 2 (dec n)))))

(declare save!)

(defn retried!
  "Note the result `written` of a write in the save status of the `state`
  atom, and return it. A write that failed is logged and tried again
  later, unless a newer version saved the library, and while the page
  tells of the failure, the browser asks before the page goes."
  [state written]
  (let [before (:save-status @state)
        status (when-let [error (:error written)]
                 (saves/failed before error (or (-> @waiting first :opts :at) (js/Date.))))]
    (when status
      (tel/log! {:level :error :id :library/save-failed :data written}
                "The library wasn't saved"))
    (if (saves/retry? status)
      (runtime/later! ::retry (retry-ms (:failures status)) #(save! state))
      (runtime/cancel! ::retry))
    (when (not= before status)
      (swap! state saves/noted status)
      (runtime/warn-before-leaving! (saves/shown? status))))
  written)

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
     (perf/measured!
      "library/change" {:operation (first operation)}
      (fn []
        (let [before (:library @state)
              opts   (merge {:at (js/Date.)} opts)
              given  (when operation
                       (applied before operation opts))]
          (when (and operation before)
            (swap! waiting conj {:operation operation :opts opts}))
          (swap! state #(ui (cond-> %
                              operation (assoc :library (:library given)))))
          given))))))

;; A one-off for the feeds kept from before 2026-10-26, when the worker
;; began to lean each feed that it fetches. Those hold their notes in two
;; or three fields, and a feed whose server answers 304 keeps its document
;; as it was, e.g. 12,984 of the 27,833 episodes of the in-app browser's
;; library of 54 shows. That held the notes twice in memory: a library of
;; 53 such shows took 268 MB of heap after the start, and 173 MB once lean.
;;
;; It's an operation, saved as any other, rather than a part of the read,
;; since a save deletes only the documents that the library it came from
;; names. A feed renamed as it's read would leave its old document in the
;; store for good, while one leaned by an operation gets a new document in
;; place of the old, as podcast-clj's library/leaned explains.
;;
;; A lean library stays the same, so the check at each start writes
;; nothing, and it takes about 20 ms of idle time for 27,833 episodes in
;; the dev build. It can go once every library from before 2026-10-26 has
;; opened once since.
(defn leaned!
  "Drop the notes that the app doesn't read from the feeds of the library
  in the `state` atom, and save it, which writes those feeds' documents
  anew, once. A library whose feeds are lean already stays as it is."
  [state]
  (when-let [library (:library @state)]
    (when-not (identical? library (library/leaned library (js/Date.)))
      (change! state {:operation [:library/leaned]})
      (save! state))))

(defn stored-version
  "A promise of the stored version of the library's document, taken from
  the store's listing so that no document is read."
  []
  (.then ((:list library-options/store))
         (fn [listed]
           (some #(when (= library-options/document-name (:name %)) (:version %))
                 listed))))

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
                  (perf/measured!
                   "library/catch-up" {}
                   #(.then (library/read! library library-options/store
                                          library-options/options)
                           (fn [read]
                             (reset! known read)
                             (swap! state assoc :library
                                    (applied-all (:library read) @waiting))
                             read))))))))))

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

(defn noting-fetches
  "The `store` with the times of performance.now when its first fetch
  began and its last ended kept in the atom `times`, as :first and :last."
  [store times]
  (update store :fetch
          (fn [fetch]
            (fn [& args]
              (swap! times update :first #(or % (js/performance.now)))
              (.then (js/Promise.resolve (apply fetch args))
                     (fn [document]
                       (swap! times assoc :last (js/performance.now))
                       document))))))

(defn open!
  "A promise of reading this browser's library, or a new one, into the
  :library of the `state` atom, and of keeping it up to date with what the
  other tabs write. If the library can't be read, the promise fails and
  `state` gets no library.

  The time from its first read of IndexedDB to its last goes on the page's
  timeline as startup/library-reading and startup/library-read, and the
  rest of the read is decoding."
  [state]
  ;; listening first, a write that another tab announces during the read
  ;; is read after it
  (listen! state)
  (in-turn!
   (fn []
     (let [times (atom {})]
       (perf/measured!
        "library/open" {}
        #(.then (library/read! (library/make (device/id! device-key)
                                             {:config library-options/config
                                              :codec  transit/codec})
                               (noting-fetches library-options/store times)
                               library-options/options)
                (fn [{:keys [library] :as read}]
                  (when-let [{began :first ended :last} (not-empty @times)]
                    (perf/mark! "startup/library-reading" began)
                    (perf/mark! "startup/library-read" ended))
                  (reset! known read)
                  (swap! state assoc :library (applied-all library @waiting))
                  read)))))))

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
