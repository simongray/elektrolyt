(ns dk.simongray.elektrolyt.ui.artwork.colors
  "Working out the colours of artwork with colour-coded, from the cache or
  else from the image, a few artworks at a time."
  (:require [clojure.string :as str]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.image :as image]
            [dk.simongray.colour-coded.quantise :as quantise]
            [dk.simongray.elektrolyt.library.options :as library-options]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.runtime.cache :as app-cache]
            [dk.simongray.elektrolyt.ui.colors :as colors]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [reitit.core :as reitit]
            [taoensso.telemere :as tel]))

;; The cache keeps the palette and not what the page takes from it, so
;; that a new rule for the seed needs no artwork fetched again
(defn cached-colors
  "The colours of the palette in the cache entry `kept`, or nil when it
  has none, e.g. an entry from before the cache kept palettes."
  [kept]
  (some-> (:palette (:value kept)) colors/from-palette))

;; Celebi (2011): k-means puts a colour where a cluster of pixels is, where
;; buckets of sRGB split a cluster that straddles two. Eight colours leave
;; room for a vivid detail beside the shades of a ground.
;; https://arxiv.org/abs/1101.0395
(defn image-palette
  "A promise of the palette of the image in the bytes `b`, eight colours
  as pairs of a hex colour and its share of the image, the commonest
  first, or of nil when the image can't be decoded."
  [b]
  (image/then (image/palette b 8 quantise/k-means)
              (fn [palette]
                (mapv (fn [{:keys [colour share]}]
                        [(cc/hex colour) (/ (js/Math.round (* 1000 share)) 1000)])
                      palette))))

(defn- proxied-bytes
  "A promise of the bytes of the image at `url`, through the proxy, since
  most sites that serve artwork don't let another site's page read it."
  [url]
  (.then (library-options/send {:url url :as :bytes}) :body))

;; Fetch, redirect mode manual: fetch doesn't follow a redirect to the
;; artwork's own site, which a page can't read without CORS
;; https://fetch.spec.whatwg.org/#concept-request-redirect-mode
(defn image-bytes
  "A promise of the bytes of the image to take the colours of the artwork
  of `x` from: the server's thumbnail, on the app's own origin, or else
  the artwork through the proxy. When the server redirects a thumbnail to
  the artwork, e.g. for WebP, the artwork comes through the proxy too."
  [{:keys [artwork] :as x}]
  (let [url (colors/source x)]
    (if (str/starts-with? url "/")
      (-> (js/fetch url #js {:redirect "manual"})
          (.then (fn [response]
                   (if (.-ok response)
                     (.then (.arrayBuffer response) #(js/Uint8Array. %))
                     (proxied-bytes artwork)))))
      (proxied-bytes url))))

(def colors-at-once
  "How many artworks' colours are worked out at a time, so that a long
  list doesn't send all its images through the proxy at once."
  4)

(defn next-wanted
  "The artworks on the page in `state` whose colours to work out next, as
  many as colors-at-once allows beside those under way."
  [state]
  (let [loading (count (filter #(= :loading (:status %)) (vals (:colors state))))]
    (take (- colors-at-once loading) (colors/wanted state))))

(defn loading
  "The `state` with the colours of the artworks `xs` marked as under way."
  [state xs]
  (update state :colors merge (zipmap (map :artwork xs) (repeat {:status :loading}))))

(declare load-colors!)

(defn took-arrived!
  "Put the colours of the artworks `found` into `store` in one change,
  with the next artworks marked as under way, and start on those."
  [store found]
  (let [xs (volatile! nil)]
    (swap! store (fn [state]
                   (let [state (update state :colors merge found)]
                     (vreset! xs (next-wanted state))
                     (loading state @xs))))
    (run! #(load-colors! store %) @xs)))

;; Each colour went into the store on its own, and each made a render of
;; the page, as did marking the next artwork as under way: 97 renders as
;; the Library's 50 colours arrived
(defonce ^{:doc "The colours of artworks worked out since the store last
  took them, by artwork, which it takes together once the page paints, or
  nil when none wait."}
  arrived
  (atom nil))

(defn arrived!
  "Keep the colours `v` of the `artwork` for `store` to take with the
  others that arrive before the page paints."
  [store artwork v]
  (when (nil? @arrived)
    (.then (runtime/painted!)
           #(let [found @arrived]
              (reset! arrived nil)
              (took-arrived! store found))))
  (swap! arrived assoc artwork v))

(defn load-colors!
  "Work out the colours of the artwork `x` for `store`, from the cache or
  else from the image, whose palette is then cached. An image that fails
  to load leaves the artwork without colours until the page loads again."
  [store {:keys [artwork] :as x}]
  (-> (app-cache/fetch! app-cache/colors artwork)
      (.then (fn [kept]
               (or (cached-colors kept)
                   (-> (image-bytes x)
                       (.then image-palette)
                       (.then (fn [palette]
                                (when palette
                                  (app-cache/put! app-cache/colors artwork {:palette palette})
                                  (colors/from-palette palette))))))))
      (.catch (fn [e]
                (tel/log! {:level :warn :id :artwork/colors-failed
                           :data  {:artwork artwork} :error e}
                          "The colours of the artwork weren't worked out")
                nil))
      (.then #(arrived! store artwork (or % {:seed nil})))))

(defn colored!
  "Work out the missing colours of the artworks on the page in `state`
  into `store`, a few at a time and in order."
  [store state]
  (let [xs (next-wanted state)]
    (when (seq xs)
      (swap! store loading xs)
      (run! #(load-colors! store %) xs))))

(defn kept-colors!
  "A promise of loading the cached colours of the artwork on the page at
  the window's location into the :colors of `store`, so that the first
  render has them."
  [store]
  (let [route  (routes/match->route (reitit/match-by-path routes/router js/location.pathname))
        wanted (colors/wanted (assoc @store :route route))]
    (-> (app-cache/fetch-keys! app-cache/colors (map :artwork wanted))
        (.then (fn [kept]
                 (swap! store update :colors merge
                        (into {} (keep (fn [[artwork k]] (some->> (cached-colors k) (vector artwork))))
                              kept)))))))
