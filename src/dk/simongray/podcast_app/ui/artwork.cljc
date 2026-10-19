(ns dk.simongray.podcast-app.ui.artwork
  "The artwork of a show or an episode as an img, in the size that fits,
  and as a link where its title beside it is one."
  (:require [clojure.string :as str]
            [dk.simongray.podcast.artwork :as thumbnails]))

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
  [{:keys [artwork artwork-sizes]}]
  (or (not-empty artwork-sizes)
      (thumbnails/sizes thumbnails-path artwork)))

(defn srcset
  "The srcset of the artwork `sizes`, maps of an :href and a :width."
  [sizes]
  (str/join ", " (map #(str (:href %) " " (:width %) "w") sizes)))

;; The main content is 48 rem wide, the width at which the artwork heads
;; the column of the Details, in a window about 63 em wide, with the rail
;; and the gutters
(def intro-sizes
  "The sizes of the artwork of the header of a show or an episode: 256 px
  at the head of the column of the Details, and 160 px at most beside the
  title."
  "(min-width: 63em) 256px, 160px")

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
;; a screen reader.
;; https://www.w3.org/WAI/WCAG22/Techniques/html/H2
(defn linked
  "The artwork `img` as a link to `href`, where the title beside it links
  too, or nil without the img."
  [href img]
  (when img
    [:a.artwork-link {:href href :tabindex -1 :aria-hidden "true"} img]))
