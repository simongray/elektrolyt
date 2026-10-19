(ns dk.simongray.podcast-app.library
  "The listener's library on this device, kept in IndexedDB between visits
  and shared by the tabs and the workers of the app, and the app's web
  worker.

  The library is podcast-clj's, and its requests go through the proxy of
  the app's server. As podcast-clj's guide has it, the id of the device
  lives in localStorage apart from the library, so that a save that fails
  makes no new device at the next visit, and the library has a database of
  its own, since a save deletes the documents of any other library there.

  Each tab and worker writes the library as a replica of the device, as
  like-minded's replica has it with the library's kind, so that none of
  them loses what another writes. The documents are in Transit, which
  reads about 30 times faster than EDN."
  (:require [dk.simongray.like-minded.device :as device]
            [dk.simongray.like-minded.replica :as replica]
            [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.app.worker :as worker]
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
  "The config of the library in every tab and worker of the app."
  {:send  send
   :codec transit/codec})

(defonce ^{:doc "The replica of the library in this tab, once open! has
  opened it."}
  the-replica
  (atom nil))

(defn open!
  "A promise of the library of this browser, as its last save left it or a
  new one, in the :library of the app `state`, an atom, as the replica
  that this tab leases."
  [state]
  (.then (replica/open! {:state  state
                         :path   [:library]
                         :store  store
                         :device (device/id! device-key)
                         :kind   (library/replica-kind config)})
         #(reset! the-replica %)))

(defn save!
  "A promise of the save of the library of this tab, once the saves before
  it are done, as library/save! gives it."
  []
  (if-let [r @the-replica]
    (replica/save! r)
    (js/Promise.resolve nil)))

(defonce ^{:doc "The client of the app's web worker, which refreshes the
  library and fetches feeds, made at its first use."}
  web-worker
  (delay (worker/client "/js/worker.js" config)))

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
