(ns dk.simongray.podcast-app.library
  "The listener's library on this device, kept in IndexedDB between visits.

  The library is podcast-clj's, and its requests go through the proxy of
  the app's server. As podcast-clj's guide has it, the id of the device
  lives in localStorage apart from the library, so that a save that fails
  makes no new device at the next visit, and the library has a database of
  its own, since a save deletes the documents of any other library there."
  (:require [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.podcast.app.library :as library]
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

(defn device-id!
  "The id of this browser, which its first visit makes and keeps."
  []
  (or (try (.getItem js/localStorage device-key) (catch :default _ nil))
      (let [id (str (random-uuid))]
        (try (.setItem js/localStorage device-key id) (catch :default _ nil))
        id)))

(defn restore!
  "A promise of the library of this browser as its last save left it, or a
  new one when nothing was saved."
  []
  (library/restore! (library/make (device-id!) {:config {:send send}}) store))

(defonce ^{:doc "The promise of the last save, which the next one waits for,
  so that two saves never write the documents at once."}
  saving
  (atom (js/Promise.resolve nil)))

(defn save!
  "Save the library `lib` once the saves before it are done, as a promise
  of what library/save! gives."
  [lib]
  (let [save #(library/save! lib store)]
    (swap! saving #(.then % save save))))

;; Storage Standard, sections 7.1 and 8: a browser can clear a site's
;; storage when the device runs low, unless the site asks for it to persist.
;; Firefox asks the user, so the request waits until the listener subscribes.
(defn persist!
  "Ask the browser to keep the site's storage when the device runs low."
  []
  (some-> js/navigator .-storage .persist))
