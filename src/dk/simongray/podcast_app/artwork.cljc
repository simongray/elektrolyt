(ns dk.simongray.podcast-app.artwork
  "The sizes of the artwork of a show or an episode: those that its feed
  names, or else the thumbnails that the app's server makes of it, for
  the views' srcsets and for the colours of the artwork."
  (:require [dk.simongray.podcast.artwork :as thumbnails]))

(def thumbnails-path
  "The path of the server's thumbnails of artwork."
  "/artwork")

;; Most feeds name one artwork of 3000 pixels and no sizes, e.g. 1.4 MB
;; for each episode of a feed of Anchor's, so a row of 48 pixels takes the
;; server's thumbnails, as Pocket Casts' lists take its own of 200 pixels
(defn sizes
  "The sizes of the artwork of `x`: the :artwork-sizes that its feed names,
  or else the server's thumbnails of its :artwork, or nil for artwork that
  the server can't fetch, e.g. a data URL."
  [{:keys [artwork artwork-sizes] :as x}]
  (or (not-empty artwork-sizes)
      (thumbnails/sizes thumbnails-path artwork)))
