(ns dk.simongray.elektrolyt.runtime.cache
  "The caches of what the app fetches, kept between visits.

  They live in an IndexedDB database of their own, so that clearing them
  in Settings leaves the library alone. A cache that the browser can't
  open, e.g. in a private window, holds nothing."
  (:require [dk.simongray.like-minded.cache :as cache]
            [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [taoensso.telemere :as tel]))

(def store
  "The IndexedDB database of the caches."
  (indexeddb/store "dk.simongray.elektrolyt.cache"))

(defn config
  "The config of a like-minded cache whose keys start with `prefix`,
  keeping at most `max-count` values for `days` each."
  [prefix max-count days]
  {:store       store
   :prefix      prefix
   :max-count   max-count
   :max-seconds (* days 24 60 60)
   :codec       transit/codec})

(def searches
  "The results of the directories, by directory and query."
  (config "search." 100 30))

(def feed-urls
  "The feed URLs that the Podcast Index found, by guid."
  (config "feed-url." 500 30))

;; Pocket Casts keeps a show that the listener only looked at for 7 days
;; https://github.com/Automattic/pocket-casts-android/blob/cb128be/modules/services/repositories/src/main/java/au/com/shiftyjelly/pocketcasts/repositories/podcast/PodcastManagerImpl.kt#L218-L235
(def shows
  "The feeds of shows outside the library, by URL."
  (config "show." 20 7))

;; the codec of the library, in which the web worker sends a feed, so that
;; the page doesn't encode a large feed a second time
(def encoded-shows
  "The cache of shows, for a feed that's encoded already, as a string in
  the codec of the cache and the library."
  (assoc-in shows [:codec :encode] identity))

;; A podroll changes about as seldom as artwork, but it's kept as long as
;; the shows
(def linked
  "The shows that each show links to, by the show's guid."
  (config "linked." 100 7))

;; An artwork's URL usually changes along with its image, so its colours
;; keep for months
(def colors
  "The colours of artwork, by URL."
  (config "colors." 500 90))

;; DR can publish a series that it didn't, so the answer keeps a day
(def dr-feeds
  "Whether DR publishes a podcast of each series of drpodcast.nu, by its
  slug: :published or :unpublished."
  (config "dr-feed." 2000 1))

;; Fetch, request cache mode no-store
;; https://fetch.spec.whatwg.org/#concept-request-cache-mode
(defn send
  "Send the `request` through the app's proxy, past the browser's HTTP
  cache, so that no other copy of a search stays behind."
  [request]
  (app-library/send (assoc request :cache :no-store)))

(defn fetch!
  "A promise of the entry under `k` in the cache `c`, a map of its :value
  and the :at when it was kept, or of nil."
  [c k]
  (-> (js/Promise.resolve nil)
      (.then #(cache/fetch! c k))
      (.catch (constantly nil))))

(defn fetch-all!
  "A promise of the entries under the keys `ks` in the cache `c`, as a map
  of each key that has one to a map of its :value and the :at when it was
  kept, or of nil, from one listing of the cache's database."
  [c ks]
  (-> (js/Promise.resolve nil)
      (.then #(cache/fetch-all! c ks))
      (.catch (constantly nil))))

(defn write!
  "A promise of keeping the value `v` under `k` in the cache `c`, which
  logs a write that fails."
  [c k v]
  (-> (js/Promise.resolve nil)
      (.then #(cache/put! c k v))
      (.catch #(tel/log! {:level :warn :id :cache/write-failed
                          :data  {:key k} :error %}
                         "The cache wasn't written"))))

(defn put!
  "Keep the value `v` under `k` in the cache `c` once the page is idle,
  since a large feed takes a moment to encode."
  [c k v]
  (runtime/idle! 2000 #(write! c k v)))

(defn clear!
  "A promise of emptying every cache."
  []
  (js/Promise.all (into-array (map cache/clear! [searches feed-urls shows linked colors dr-feeds]))))
