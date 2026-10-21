(ns dk.simongray.elektrolyt.library
  "The listener's library on this device, kept in IndexedDB between visits
  and shared by the tabs of the app.

  The library is podcast-clj's, and its requests go through the proxy of
  the app's server. As podcast-clj's guide has it, the id of the device
  lives in localStorage apart from the library, so that a save that fails
  makes no new device at the next visit, and the library has a database of
  its own, since a write deletes the documents of the library before it.

  The library's document in IndexedDB is the one library of the device,
  which every tab changes as it would an atom. A change is an operation of
  dk.simongray.elektrolyt.operations, which the tab applies to its own
  library at once, for the page. A save writes the operations that wait
  with library/durable-swap!, on condition of the version of the document
  that the tab knows, and again onto the stored library when another tab
  wrote first. Then it tells the other tabs the new version on a
  BroadcastChannel, so that they read it, as a page does when it's shown
  again. The app's web worker writes only the documents of the feeds that
  it fetched, as dk.simongray.elektrolyt.fetching has it. The documents
  are in Transit, which reads about 30 times faster than EDN."
  (:require [dk.simongray.like-minded.device :as device]
            [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.operations :as operations]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.wary-fetch.proxy :as proxy]))

(def send
  "The function that sends the requests of the library, through the proxy
  of the app's server."
  (proxy/sender "/proxy"))

;; The app's former name, since a browser keeps the listener's data under
;; them
(def device-key
  "The key of the id of this browser in localStorage."
  "dk.simongray.podcast-app.device")

(def store
  "The IndexedDB database of the library, which opens at its first use."
  (indexeddb/store "dk.simongray.podcast-app.library"))

;; The app offers no choice of where new episodes go, so every one of them
;; arrives in the inbox, though an earlier version of the app, or another
;; app, chose the queue or nowhere for a show. That choice stays in the
;; show's settings, for the apps that offer it.
(def config
  "The config of the library in every tab and the worker of the app."
  {:new-episodes :inbox})

(def options
  "The options of every call to the library in the tabs and the worker of
  the app, which are functions and so stay out of its config: its requests
  go through the proxy, and its documents are in Transit."
  {:send  send
   :codec transit/codec})

(def document-name
  "The name of the library's own document in the store, as podcast-clj
  names it in the codec of the options."
  (str "library" (:extension transit/codec)))

(def channel-name
  "The name of the BroadcastChannel on which the tabs tell each other the
  version of the library that they wrote."
  "dk.simongray.elektrolyt.library")

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

(defn applied
  "What the `operation` gives of the `library`, applied with the `opts` of
  operations/applied and the options of the library's calls."
  [library operation opts]
  (operations/applied library operation (merge options opts)))

(defn applied-all
  "The `library` with the `ops`, maps of the :operation and its :opts as
  waiting holds them, applied in order. An operation that throws on this
  library, e.g. a subscription that another tab made first, is left out."
  [library ops]
  (reduce (fn [lib {:keys [operation opts]}]
            (try
              (:library (applied lib operation opts))
              (catch :default e
                (js/console.warn "An operation didn't apply to the stored library"
                                 (clj->js operation) e)
                lib)))
          library
          ops))

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
  (runtime/post! channel-name {:version version}))

(defn written!
  "A promise of the write of the operations that wait in the app `state`,
  an atom, with library/durable-swap!, or of nil when none wait. The tab's
  library is the one that it writes, unless another tab wrote first, when
  it's the stored library with the operations applied again, and those
  that came meanwhile on top. A write that fails keeps the operations
  waiting for the next."
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
  with the `opts` of operations/applied, and give what its operation gave,
  as applied gives it, e.g. the :effects of a step beside the library. The
  time of the change is now, unless the `opts` give its :at. The operation
  waits for the next save!.

  An operation that throws changes nothing, and the throw goes on to the
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
                  (.then (library/read! library store options)
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
  (runtime/on-message! channel-name (fn [_] (caught-up! state)))
  (runtime/on-visible! #(caught-up! state))
  (runtime/on-pageshow! #(caught-up! state)))

(defn open!
  "A promise of the library of this browser, as the last write left it or
  a new one, in the :library of the app `state`, an atom, which then reads
  what the other tabs write. A library that doesn't read leaves the state
  without one, and the promise fails."
  [state]
  (.then (library/read! (library/make (device/id! device-key) {:config config :codec transit/codec})
                        store
                        options)
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
