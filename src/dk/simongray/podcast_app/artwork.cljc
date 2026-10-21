(ns dk.simongray.podcast-app.artwork
  "The sizes of the artwork of a show or an episode: those that its feed
  names, or else the thumbnails that the app's server makes of it, for
  the views' srcsets and for the colours of the artwork.

  The server answers a GET of its thumbnails-path with url and w
  parameters, e.g. /artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=128,
  with a thumbnail of the artwork at that width. The widths are few and
  fixed, as Pocket Casts has 200, 480 and 960, and Next.js allows only the
  widths that its config lists. Any width that a stranger asks for would be
  another file in the server's cache."
  (:require [dk.simongray.wary-fetch.url :as url]))

(def thumbnails-path
  "The path of the server's thumbnails of artwork."
  "/artwork")

;; Pocket Casts, PodcastImage.kt; Next.js, the Image component, imageSizes
;; https://github.com/Automattic/pocket-casts-android/blob/cedff55/modules/services/repositories/src/main/java/au/com/shiftyjelly/pocketcasts/repositories/images/PodcastImage.kt
;; https://nextjs.org/docs/app/api-reference/components/image#imagesizes
(def widths
  "The widths of the thumbnails in pixels. Each is half the next, so the
  halving that format.image/thumbnails does makes each one on its way to
  the next, and they cover rows of 48 to 64 pixels and artwork of 256 at
  two pixels to the CSS pixel."
  [64 128 256 512])

(defn thumbnail-url
  "The URL of the thumbnail `width` pixels wide of the artwork at `url`,
  from the server's handler at `path`, e.g. /artwork."
  [path url width]
  (str path "?url=" (url/percent-encode url) "&w=" width))

(defn thumbnails
  "The server's thumbnails of the artwork at `url`, at the widths, as maps
  of :href and :width, as resolve/artwork-sizes gives the sizes that a
  feed names. They're nil for a URL that isn't http or https, e.g. a data
  URL, which the server can't fetch.

  The server doesn't scale an artwork up, so a thumbnail can be narrower
  than its :width: the widths above the artwork's own lead to one
  thumbnail at its own width, under the smallest of them."
  [url]
  (when (url/web-url? url)
    (mapv #(hash-map :href (thumbnail-url thumbnails-path url %) :width %)
          widths)))

;; Most feeds name one artwork of 3000 pixels and no sizes, e.g. 1.4 MB
;; for each episode of a feed of Anchor's, so a row of 48 pixels takes the
;; server's thumbnails, as Pocket Casts' lists take its own of 200 pixels
(defn sizes
  "The sizes of the artwork of `x`: the :artwork-sizes that its feed names,
  or else the server's thumbnails of its :artwork, or nil for artwork that
  the server can't fetch, e.g. a data URL."
  [{:keys [artwork artwork-sizes] :as x}]
  (or (not-empty artwork-sizes)
      (thumbnails artwork)))
