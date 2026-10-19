(ns dk.simongray.podcast-app.ui.artwork
  "The artwork of a show as an img, in the size that fits."
  (:require [clojure.string :as str]))

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
;; empty.
(defn img
  "The img of the :artwork of a show `x`, at `size` CSS pixels square, with
  the :artwork-sizes of `x` as its srcset and the `attrs` merged in, e.g.
  :loading, or nil without artwork."
  [{:keys [artwork artwork-sizes] :as x} size attrs]
  (when artwork
    [:img (merge {:src      artwork
                  :alt      ""
                  :width    size
                  :height   size
                  :decoding "async"}
                 (when (seq artwork-sizes)
                   {:srcset (srcset artwork-sizes)
                    :sizes  (str size "px")})
                 attrs)]))
