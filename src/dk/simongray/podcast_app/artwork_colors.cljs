(ns dk.simongray.podcast-app.artwork-colors
  "Working out the colours of artwork with colour-coded, a few artworks at
  once, into the :colors of the state, from the cache, or else from the
  image itself, whose colours the cache keeps then.

  The namespace dk.simongray.podcast-app.colors describes what the colours
  are for and how the state keeps them."
  (:require [clojure.string :as str]
            [dk.simongray.colour-coded :as cc]
            [dk.simongray.colour-coded.image :as image]
            [dk.simongray.podcast-app.cache :as app-cache]
            [dk.simongray.podcast-app.colors :as colors]
            [dk.simongray.podcast-app.library :as app-library]
            [dk.simongray.podcast-app.routes :as routes]
            [reitit.core :as reitit]))

(defn cached-colors
  "The colours of an artwork in the entry `kept` of the cache, or nil for
  none, or for colours kept before they had a :dominant colour."
  [kept]
  (when (contains? (:value kept) :dominant)
    (:value kept)))

(defn image-colors
  "A promise of the colours of the image in the bytes `b` that colors.cljc
  keeps: its :seed and its :dominant colour, as hex, or nil where it can't
  be decoded."
  [b]
  (image/then (image/colours b)
              (fn [{:keys [seed background]}]
                {:seed     (some-> seed cc/hex)
                 :dominant (some-> background cc/hex)})))

(defn- proxied-bytes
  "A promise of the bytes of the image at `url`, through the proxy, since
  most hosts of artwork don't let a page of another site read it."
  [url]
  (.then (app-library/send {:url url :as :bytes}) :body))

;; Fetch, section 4.3: with the redirect mode manual, fetch doesn't follow
;; the server's redirect to the artwork's own host, whose answer a page
;; can't read without CORS
;; https://fetch.spec.whatwg.org/#concept-request-redirect-mode
(defn image-bytes
  "A promise of the bytes of the image to work out the colours of the
  artwork of `x` from: its source, which the server's thumbnails serve
  from the app's own origin, or else through the proxy. A thumbnail that
  the server redirects to the artwork itself, e.g. of WebP, comes through
  the proxy from the artwork."
  [{:keys [artwork] :as x}]
  (let [url (colors/source x)]
    (if (str/starts-with? url "/")
      (-> (js/fetch url #js {:redirect "manual"})
          (.then (fn [response]
                   (if (.-ok response)
                     (.then (.arrayBuffer response) #(js/Uint8Array. %))
                     (proxied-bytes artwork)))))
      (proxied-bytes url))))

(defn load-colors!
  "Work out the colours of the artwork `x`, as colors/wanted names it, into
  the :colors of the `store`: from the cache, or else from the image,
  whose colours the cache keeps then. An image that doesn't come leaves
  the artwork without colours until the page loads again."
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
                (js/console.warn "The colours of the artwork weren't worked out" e)
                nil))
      (.then #(swap! store assoc-in [:colors artwork] (or % {:seed nil})))))

(def colors-at-once
  "How many artworks' colours are worked out at once, so that a long list,
  e.g. the Library, doesn't send all its images through the proxy at once."
  4)

(defn colored!
  "Work out the colours of the artworks that the page of the `state` wants
  and doesn't have yet, into the `store`, a few at once, the first first."
  [store state]
  (let [loading (count (filter #(= :loading (:status %)) (vals (:colors state))))
        xs      (take (- colors-at-once loading) (colors/wanted state))]
    (when (seq xs)
      (swap! store update :colors merge (zipmap (map :artwork xs) (repeat {:status :loading})))
      (doseq [x xs]
        (load-colors! store x)))))

(defn kept-colors!
  "A promise that the :colors of the `store` have what the cache keeps of
  the artwork that the page at the window's location wants, before it
  renders, which comes to nothing for artwork that it doesn't keep."
  [store]
  (let [route  (routes/match->route (reitit/match-by-path routes/router js/location.pathname))
        wanted (colors/wanted (assoc @store :route route))]
    (-> (js/Promise.all (into-array (map #(app-cache/fetch! app-cache/colors (:artwork %)) wanted)))
        (.then (fn [kept]
                 (swap! store update :colors merge
                        (into {} (keep (fn [[x k]] (some->> (cached-colors k) (vector (:artwork x)))))
                              (map vector wanted kept))))))))
