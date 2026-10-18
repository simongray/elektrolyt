(ns dk.simongray.podcast-app.cache
  "What this browser keeps between visits besides the library: the lists
  of the directories, the URLs of the feeds that the Podcast Index found by
  guid, and the feeds of shows outside the library.

  They're caches of like-minded in an IndexedDB database of their own, so
  that the button in Settings empties them without touching the library.
  The requests to the directories skip the browser's HTTP cache, so that
  no other copy of a search stays behind. A cache that the browser can't
  read or write, e.g. in a private window, keeps nothing."
  (:require [dk.simongray.like-minded.cache :as cache]
            [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.podcast-app.library :as app-library]))

(def store
  "The IndexedDB database of the caches, apart from the library's."
  (indexeddb/store "dk.simongray.podcast-app.cache"))

(def searches
  "The lists of the directories by directory and query, at most 100
  searches and for 30 days."
  {:store       store
   :prefix      "search."
   :max-count   100
   :max-seconds (* 30 24 60 60)
   :codec       transit/codec})

(def feed-urls
  "The URLs of the feeds that the Podcast Index found by guid, for 30
  days."
  {:store       store
   :prefix      "feed-url."
   :max-count   500
   :max-seconds (* 30 24 60 60)
   :codec       transit/codec})

;; Pocket Casts keeps a show that the listener only looked at for 7 days
;; https://github.com/Automattic/pocket-casts-android/blob/cb128be/modules/services/repositories/src/main/java/au/com/shiftyjelly/pocketcasts/repositories/podcast/PodcastManagerImpl.kt#L218-L235
(def shows
  "The feeds of shows outside the library, by the URL of each, at most 20
  and for 7 days."
  {:store       store
   :prefix      "show."
   :max-count   20
   :max-seconds (* 7 24 60 60)
   :codec       transit/codec})

;; Fetch, request cache mode: no-store neither reads nor writes the HTTP
;; cache
;; https://fetch.spec.whatwg.org/#concept-request-cache-mode
(defn send
  "Send the `request` through the proxy of the app's server, past the
  browser's HTTP cache, as the requests to the directories go."
  [request]
  (app-library/send (assoc request :cache :no-store)))

(defn fetch!
  "A promise of what the cache `c` keeps under the key `k`, a map of the
  :value and the :at it was kept, or of nil."
  [c k]
  (-> (js/Promise.resolve nil)
      (.then #(cache/fetch! c k))
      (.catch (constantly nil))))

;; requestIdleCallback, where the browser has it: a large feed takes a
;; moment to encode, which waits until the page is idle
(defn put!
  "Keep the value `v` under the key `k` in the cache `c`, once the page is
  idle."
  [c k v]
  (let [put #(-> (js/Promise.resolve nil)
                 (.then (fn [_] (cache/put! c k v)))
                 (.catch (fn [e] (js/console.warn "The cache wasn't written" e))))]
    (if (exists? js/requestIdleCallback)
      (js/requestIdleCallback put #js {:timeout 2000})
      (js/setTimeout put 0))))

(defn clear!
  "A promise that every cache is empty."
  []
  (js/Promise.all (into-array (map cache/clear! [searches feed-urls shows]))))
