(ns dk.simongray.elektrolyt.ui.artwork
  "The artwork of a show or an episode: its sizes, for the srcsets of its
  img and for its colours, and the img itself, in the size that fits, and
  as a link where its title beside it is one.

  The sizes are those that its feed names, or else the thumbnails that the
  app's server makes of it. The server answers a GET of its
  thumbnails-path with url and w parameters, e.g.
  /artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=128, with a thumbnail
  of the artwork at that width. The widths are few and fixed, as Pocket
  Casts has 200, 480 and 960, and Next.js allows only the widths that its
  config lists. Any width that a stranger asks for would be another file
  in the server's cache."
  (:require [clojure.string :as str]
            [dk.simongray.wary-fetch.url :as url]))

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

(defn smallest
  "The href of the smallest size of the artwork of `x` that is at least
  `width` pixels wide, or else its :artwork itself, e.g. a data URL, or
  nil without artwork."
  [x width]
  (or (->> (sizes x)
           (filter #(<= width (:width %)))
           (sort-by :width)
           first
           :href)
      (:artwork x)))

(defn srcset
  "The srcset of the artwork `sizes`, maps of an :href and a :width."
  [sizes]
  (str/join ", " (map #(str (:href %) " " (:width %) "w") sizes)))

;; The main content is 48 rem wide, the width at which the artwork heads
;; the column of the Details (see layout.css), in a window about 63 em
;; wide, with the rail and the gutters. The sizes are in rem, as the
;; column and the artwork beside the title are, so that they follow the
;; reader's font size.
(def intro-sizes
  "The sizes of the artwork of the header of a show or an episode: 16 rem
  at the head of the column of the Details, and 10 rem at most beside the
  title."
  "(min-width: 63em) 16rem, 10rem")

;; HTML, section 4.8.4.3: with the widths of the sizes in a srcset, and the
;; size of the img in its sizes, a browser loads the smallest artwork that
;; fills it at the screen's density. W3C, Images tutorial, decorative
;; images: the title next to the artwork names the show, so its alt is
;; empty. The artwork itself stays the src, which a browser without srcset
;; loads.
(defn img
  "The img of the :artwork of a show or an episode `x`, at `size` CSS
  pixels square, with its sizes as its srcset and the `attrs` merged in,
  e.g. :loading, or nil without artwork."
  [{:keys [artwork] :as x} size attrs]
  (when artwork
    (let [candidates (sizes x)]
      [:img (merge {:src      artwork
                    :alt      ""
                    :width    size
                    :height   size
                    :decoding "async"}
                   (when (seq candidates)
                     {:srcset (srcset candidates)
                      :sizes  (str size "px")})
                   attrs)])))

;; WCAG 2.2, technique H2: an image and a text beside it that link to the
;; same page are one link, rather than two that a keyboard and a screen
;; reader meet in turn. The title is that link, so the artwork's link is
;; for a pointer alone, out of the order of the keyboard and hidden from
;; a screen reader. Its tooltip is that title, which a screen reader
;; doesn't read either.
;; https://www.w3.org/WAI/WCAG22/Techniques/html/H2
(defn linked
  "The artwork `img` as a link to `href`, where the `title` beside it links
  too, which is its tooltip, or nil without the img."
  [href title img]
  (when img
    [:a.artwork-link {:href href :title title :tabindex -1 :aria-hidden "true"} img]))
