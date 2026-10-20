(ns dk.simongray.podcast-app.library
  "The listener's library on this device, kept in IndexedDB between visits
  and shared by the tabs of the app.

  The library is podcast-clj's, and its requests go through the proxy of
  the app's server. As podcast-clj's guide has it, the id of the device
  lives in localStorage apart from the library, so that a save that fails
  makes no new device at the next visit, and the library has a database of
  its own, since a write deletes the documents of the library before it.

  The library's document in IndexedDB is the one library of the device,
  which every tab changes as it would an atom. A change is an operation of
  dk.simongray.podcast-app.operations, which the tab applies to its own
  library at once, for the page. A save writes the operations that wait
  with durable-swap!, on condition of the version of the document that the
  tab knows, and again onto the stored library when another tab wrote
  first. Then it tells the other tabs the new version on a
  BroadcastChannel, so that they read it, as a page does when it's shown
  again. The app's web worker writes only the documents of the feeds that
  it fetched, as dk.simongray.podcast-app.fetching has it. The documents
  are in Transit, which reads about 30 times faster than EDN."
  (:require [cljs.reader :as reader]
            [dk.simongray.like-minded.device :as device]
            [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app.operations :as operations]
            [dk.simongray.wary-fetch.proxy :as proxy]))

(def send
  "The function that sends the requests of the library, through the proxy
  of the app's server."
  (proxy/sender "/proxy"))

(def device-key
  "The key of the id of this browser in localStorage."
  "dk.simongray.podcast-app.device")

(def store
  "The IndexedDB database of the library, which opens at its first use."
  (indexeddb/store "dk.simongray.podcast-app.library"))

(def config
  "The config of the library in every tab and the worker of the app."
  {:send  send
   :codec transit/codec})

(def document-name
  "The name of the library's own document in the store, as podcast-clj
  names it in the codec of the config."
  (str "library" (:extension transit/codec)))

(def channel-name
  "The name of the BroadcastChannel on which the tabs tell each other the
  version of the library that they wrote."
  "dk.simongray.podcast-app.library")

(defonce ^{:doc "The library as this tab last read it from the store or
  wrote it there, and the version of its document, as library/read! gives
  them, once open! has read it."}
  known
  (atom nil))

(defonce ^{:doc "The operations that this tab applied to its library and
  hasn't written yet, in order, each a map of the :operation and the
  :opts that it was applied with."}
  waiting
  (atom []))

(defonce ^{:doc "The promise of the last write or read of this tab, and
  the save that waits its turn after it, if any."}
  turns
  (atom {:last (js/Promise.resolve nil) :next nil}))

(defonce ^{:doc "The BroadcastChannel of the versions that the tabs write,
  opened at its first use, or nil where the browser has none."}
  channel
  (delay (when (exists? js/BroadcastChannel)
           (js/BroadcastChannel. channel-name))))

(defn applied-all
  "The `library` with the `ops`, maps of the :operation and its :opts as
  waiting holds them, applied in order, without the effects of a step. An
  operation that throws on this library, e.g. a subscription that another
  tab made first, is left out."
  [library ops]
  (reduce (fn [lib {:keys [operation opts]}]
            (try
              (dissoc (operations/applied lib operation opts) :effects)
              (catch :default e
                (js/console.warn "An operation didn't apply to the stored library"
                                 (clj->js operation) e)
                lib)))
          library
          ops))

(defn durable-swap!
  "A promise of `f` of the library that `known` holds, as library/read!
  gives it, written to `store` on condition of the version that `known`
  holds, and of `f` of the stored library again while another tab wrote
  first, as swap! applies a function to an atom. It gives the library and
  the version written, as read! gives them, or the :error of the write."
  [store {:keys [library version] :as known} f]
  (let [changed (f library)]
    (.then (library/write! changed store {:if-version version :before library})
           (fn [{:keys [conflict? error] :as written}]
             (cond
               error     written
               conflict? (.then (library/read! library store) #(durable-swap! store % f))
               :else     {:library changed :version (:version written)})))))

(defn in-turn!
  "A promise of what `f` gives, called once the writes and reads of this
  tab before it are done."
  [f]
  (let [p (.then (:last @turns) (fn [_] (f)))]
    (swap! turns assoc :last (.catch p (constantly nil)))
    p))

(defn announce!
  "Tell the other tabs that this one wrote the `version` of the library."
  [version]
  (some-> ^js @channel (.postMessage (pr-str {:version version}))))

(defn written!
  "A promise of the write of the operations that wait in the app `state`,
  an atom, with durable-swap!, or of nil when none wait. The tab's library
  is the one that it writes, unless another tab wrote first, when it's the
  stored library with the operations applied again, and those that came
  meanwhile on top. A write that fails keeps the operations waiting for
  the next."
  [state]
  (let [ops @waiting
        {:keys [library] :as k} @known]
    (if (or (empty? ops) (nil? k))
      (js/Promise.resolve nil)
      (let [shown (:library @state)
            kept! #(swap! waiting (fn [later] (into ops later)))]
        (reset! waiting [])
        (-> (durable-swap! store k #(if (identical? % library) shown (applied-all % ops)))
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

(defn save!
  "A promise of the write of the operations that wait in the app `state`,
  an atom, once the writes and reads before it are done, as written!
  gives it. A save asked for while another waits its turn is that one."
  [state]
  (or (:next @turns)
      (let [p (in-turn! #(do (swap! turns assoc :next nil)
                             (written! state)))]
        (swap! turns assoc :next p)
        p)))

(defn change!
  "Make the `change` of operations/changed to the app `state`, an atom,
  with the `opts` of operations/applied, and give the library as its
  operation left it, with the :effects of a step. The time of the change
  is now, unless the `opts` give its :at. The operation waits for the next
  save!.

  An operation that throws changes nothing, and the throw goes on to the
  caller."
  ([state change]
   (change! state change {}))
  ([state {:keys [operation ui] :or {ui identity} :as change} opts]
   (when change
     (let [before (:library @state)
           opts   (merge {:at (js/Date.)} opts)
           lib    (if operation
                    (operations/applied before operation opts)
                    before)]
       (when (and operation before)
         (swap! waiting conj {:operation operation :opts opts}))
       (swap! state #(ui (cond-> %
                           operation (assoc :library (some-> lib (dissoc :effects))))))
       lib))))

(defn stored-version
  "A promise of the version of the library's document in the store, from
  its listing, which reads no bodies."
  []
  (.then ((:list store))
         (fn [listed]
           (some #(when (= document-name (:name %)) (:version %)) listed))))

(defn caught-up!
  "A promise of the library that another tab wrote, read into the app
  `state`, an atom, with the operations that wait applied to it, once the
  writes and reads before are done, when the store holds another version
  than this tab knows. It reads only what changed."
  [state]
  (in-turn!
   (fn []
     (when-let [{:keys [library version]} @known]
       (.then (stored-version)
              (fn [stored]
                (when (not= stored version)
                  (.then (library/read! library store)
                         (fn [read]
                           (reset! known read)
                           (swap! state assoc :library
                                  (applied-all (:library read) @waiting))
                           read)))))))))

;; HTML Living Standard, 9.5 Broadcasting to other browsing contexts, and
;; Page Lifecycle: a hidden or frozen page, or one in the back/forward
;; cache, gets no message, so it reads what it missed when it's shown again
;; https://developer.chrome.com/docs/web-platform/page-lifecycle-api
(defn listen!
  "Read what the other tabs write into the app `state`, an atom, as they
  tell it, and when the page is visible again or shown from the
  back/forward cache."
  [state]
  (some-> ^js @channel
          (.addEventListener "message"
                             (fn [^js e]
                               (when (map? (try (reader/read-string (.-data e))
                                                (catch :default _ nil)))
                                 (caught-up! state)))))
  (.addEventListener js/document "visibilitychange"
                     #(when (= "visible" js/document.visibilityState)
                        (caught-up! state)))
  (.addEventListener js/window "pageshow"
                     #(when (.-persisted %)
                        (caught-up! state))))

(defn open!
  "A promise of the library of this browser, as the last write left it or
  a new one, in the :library of the app `state`, an atom, which then reads
  what the other tabs write. A library that doesn't read leaves the state
  without one, and the promise fails."
  [state]
  (.then (library/read! (library/make (device/id! device-key) {:config config}) store)
         (fn [{:keys [library] :as read}]
           (reset! known read)
           (swap! state assoc :library (applied-all library @waiting))
           (listen! state)
           read)))

;; Storage Standard, sections 7.1 and 8: a browser can clear a site's
;; storage when the device runs low, unless the site asks for it to persist.
;; Firefox asks the user, so the request waits until the listener subscribes.
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
  "A promise that the :storage of the app `state`, an atom, says how the
  browser keeps the site's storage now, from storage!."
  [state]
  (.then (storage!) #(swap! state assoc :storage %)))
