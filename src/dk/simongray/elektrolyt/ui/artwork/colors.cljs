(ns dk.simongray.elektrolyt.ui.artwork.colors
  "Working out the colours of artwork with colour-coded, from the cache or
  else from the image, a few artworks at a time."
  (:require [clojure.string :as str]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.image :as image]
            [dk.simongray.elektrolyt.library.options :as library-options]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.runtime.cache :as app-cache]
            [dk.simongray.elektrolyt.ui.colors :as colors]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [reitit.core :as reitit]
            [taoensso.telemere :as tel]))

(defn cached-colors
  "The colours in the cache entry `kept`, or nil when there are none or
  they were kept before colours had a :dominant one."
  [kept]
  (when (contains? (:value kept) :dominant)
    (:value kept)))

(defn image-colors
  "A promise of the :seed and :dominant colour of the image in the bytes
  `b`, as hex, or of nil when the image can't be decoded."
  [b]
  (image/then (image/colours b)
              (fn [{:keys [seed background]}]
                {:seed     (some-> seed cc/hex)
                 :dominant (some-> background cc/hex)})))

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
  else from the image, whose colours are then cached. An image that fails
  to load leaves the artwork without colours until the page loads again."
  [store {:keys [artwork] :as x}]
  (-> (app-cache/fetch! app-cache/colors artwork)
      (.then (fn [kept]
               (or (cached-colors kept)
                   (-> (image-bytes x)
                       (.then image-colors)
                       (.then (fn [v]
                                (when v
                                  (app-cache/put! app-cache/colors artwork v))
                                v))))))
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
