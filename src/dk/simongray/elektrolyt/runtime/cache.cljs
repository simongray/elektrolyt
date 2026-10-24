(ns dk.simongray.elektrolyt.runtime.cache
  "What this browser keeps between visits besides the library: the lists
  of the directories, the URLs of the feeds that the Podcast Index found by
  guid, the feeds of shows outside the library, the shows that a show
  links to, and the colours of artwork.

  They're caches of like-minded in an IndexedDB database of their own, so
  that the button in Settings empties them without touching the library.
  The requests to the directories skip the browser's HTTP cache, so that
  no other copy of a search stays behind. A cache that the browser can't
  read or write, e.g. in a private window, keeps nothing."
  (:require [dk.simongray.like-minded.cache :as cache]
            [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [taoensso.telemere :as tel]))

;; The app's former name, since a browser keeps the listener's data under it
(def store
  "The IndexedDB database of the caches, apart from the library's."
  (indexeddb/store "dk.simongray.podcast-app.cache"))

(defn config
  "The config of a cache of like-minded in the store, whose keys begin
  with `prefix`, which keeps `max-count` values at most, each for `days`."
  [prefix max-count days]
  {:store       store
   :prefix      prefix
   :max-count   max-count
   :max-seconds (* days 24 60 60)
   :codec       transit/codec})

(def searches
  "The lists of the directories by directory and query, at most 100
  searches and for 30 days."
  (config "search." 100 30))

(def feed-urls
  "The URLs of the feeds that the Podcast Index found by guid, at most 500
  and for 30 days."
  (config "feed-url." 500 30))

;; Pocket Casts keeps a show that the listener only looked at for 7 days
;; https://github.com/Automattic/pocket-casts-android/blob/cb128be/modules/services/repositories/src/main/java/au/com/shiftyjelly/pocketcasts/repositories/podcast/PodcastManagerImpl.kt#L218-L235
(def shows
  "The feeds of shows outside the library, by the URL of each, at most 20
  and for 7 days."
  (config "show." 20 7))

;; A podroll changes about as seldom as a show's artwork, but a week keeps
;; it with the shows that the listener opened
(def linked
  "The shows that a show links to, as linked/kept gives them, by the guid
  of the show, at most 100 and for 7 days."
  (config "linked." 100 7))

;; An artwork whose image changes usually changes its URL too, so its
;; colours keep for long
(def colors
  "The colours of artwork by the URL of each, as colors/wanted names
  them, at most 500 and for 90 days."
  (config "colors." 500 90))

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

(defn write!
  "A promise of keeping the value `v` under the key `k` in the cache `c`
  now, which logs a write that failed."
  [c k v]
  (-> (js/Promise.resolve nil)
      (.then #(cache/put! c k v))
      (.catch #(tel/log! {:level :warn :id :cache/write-failed
                          :data  {:key k} :error %}
                         "The cache wasn't written"))))

;; A large feed takes a moment to encode, which waits until the page is
;; idle
(defn put!
  "Keep the value `v` under the key `k` in the cache `c`, once the page is
  idle."
  [c k v]
  (runtime/idle! 2000 #(write! c k v)))

(defn clear!
  "A promise that every cache is empty."
  []
  (js/Promise.all (into-array (map cache/clear! [searches feed-urls shows linked colors]))))
