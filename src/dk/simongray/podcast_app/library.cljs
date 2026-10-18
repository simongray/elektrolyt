(ns dk.simongray.podcast-app.library
  "The listener's library on this device, kept in IndexedDB between visits
  and shared by the tabs and the workers of the app.

  The library is podcast-clj's, and its requests go through the proxy of
  the app's server. As podcast-clj's guide has it, the id of the device
  lives in localStorage apart from the library, so that a save that fails
  makes no new device at the next visit, and the library has a database of
  its own, since a save deletes the documents of any other library there.

  Each tab and worker writes the library as a replica of the device, as
  podcast-clj's app.replica has it, so that none of them loses what
  another writes. The documents are in Transit, which reads about 30 times
  faster than EDN."
  (:require [dk.simongray.like-minded.replica :as like-minded.replica]
            [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.podcast.app.replica :as replica]
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

(defonce ^{:doc "The channel on which the tabs and workers of the app tell
  each other that they saved the library."}
  channel
  (like-minded.replica/channel "dk.simongray.podcast-app.library"))

(def config
  "The config of the library in every tab and worker of the app."
  {:send    send
   :codec   transit/codec
   :channel channel})

(defn device-id!
  "The id of this browser, which its first visit makes and keeps."
  []
  (or (try (.getItem js/localStorage device-key) (catch :default _ nil))
      (let [id (str (random-uuid))]
        (try (.setItem js/localStorage device-key id) (catch :default _ nil))
        id)))

(defn restore!
  "A promise of the library of this browser as its last save left it, or a
  new one when nothing was saved, as the replica that this tab leases."
  []
  (replica/open! store (device-id!) config))

(defn save!
  "Save the library of the app `state`, an atom, once the saves before it
  are done, as a promise of what library/save! gives."
  [state]
  (replica/save! state [:library] store))

(defn catch-up!
  "Catch the library of the app `state`, an atom, up with what another tab
  or worker saved, as a promise."
  [state]
  (replica/catch-up! state [:library] store))

(defn listen!
  "Catch the library of the app `state` up whenever another tab or worker
  says that it saved, and give a function that stops it."
  [state]
  (replica/listen! state [:library] store channel))

;; Storage Standard, sections 7.1 and 8: a browser can clear a site's
;; storage when the device runs low, unless the site asks for it to persist.
;; Firefox asks the user, so the request waits until the listener subscribes.
(defn persist!
  "Ask the browser to keep the site's storage when the device runs low."
  []
  (some-> js/navigator .-storage .persist))
