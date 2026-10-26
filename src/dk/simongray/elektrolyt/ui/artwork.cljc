(ns dk.simongray.elektrolyt.ui.artwork
  "The artwork of a show or an episode as an img, in the sizes that its
  feed names or else in the server's thumbnails of it."
  (:require [clojure.string :as str]
            [dk.simongray.wary-fetch.url :as url]))

(def thumbnails-path
  "The path of the server's thumbnails of artwork."
  "/artwork")

;; A few fixed widths, as Pocket Casts has 200, 480 and 960 and Next.js
;; allows only the widths in its config, since each width that a stranger
;; asks for would be another file in the server's cache
;; https://github.com/Automattic/pocket-casts-android/blob/cedff55/modules/services/repositories/src/main/java/au/com/shiftyjelly/pocketcasts/repositories/images/PodcastImage.kt
;; https://nextjs.org/docs/app/api-reference/components/image#imagesizes
(def widths
  "The widths of the thumbnails in pixels, for rows of 48 to 64 CSS pixels
  and artwork of 256 at twice the density. Each is half the next, so that
  scaling down by halves passes through each one."
  [64 128 256 512])

(defn thumbnail-url
  "The URL of the thumbnail of the artwork at `url` that's `width` pixels
  wide, from the server's handler at `path`."
  [path url width]
  (str path "?url=" (url/percent-encode url) "&w=" width))

(defn thumbnails
  "The server's thumbnails of the artwork at `url`, as maps of :href and
  :width, or nil for a URL that isn't http or https, e.g. a data URL.

  The server doesn't scale artwork up, so a thumbnail can be narrower than
  its :width."
  [url]
  (when (url/web-url? url)
    (mapv #(hash-map :href (thumbnail-url thumbnails-path url %) :width %)
          widths)))

;; Most feeds name a single artwork of 3000 pixels and no smaller sizes,
;; e.g. 1.4 MB for each episode of an Anchor feed
(defn sizes
  "The sizes of the artwork of `x`: the :artwork-sizes that its feed names,
  or else the server's thumbnails, or nil for artwork that the server
  can't fetch."
  [{:keys [artwork artwork-sizes] :as x}]
  (or (not-empty artwork-sizes)
      (thumbnails artwork)))

(defn smallest
  "The href of the smallest size of the artwork of `x` that's at least
  `width` pixels wide, or else its :artwork, or nil without artwork."
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

;; The artwork heads the Details column once the main content is 48 rem
;; wide, in a window about 63 em wide with the rail and the gutters. Rem
;; follows the reader's font size, as the layout does.
(def intro-sizes
  "The sizes attribute of the artwork in the header of a show or an
  episode, at the head of the Details column or else beside the title."
  "(min-width: 63em) 16rem, 10rem")

;; CSS Wizardry, the ultimate LQIP technique: a small copy that's likely in
;; the cache, e.g. the one that a row of 48 or a search result of 64 pixels
;; loaded at the screen's density, fills the box at once, and the large
;; artwork covers it once it arrives
;; https://csswizardry.com/2023/09/the-ultimate-lqip-lcp-technique/
(defn under
  "The style that shows a small copy of the artwork of `x` under its img
  while the img loads, or nil for artwork without small sizes."
  [x]
  (when (seq (sizes x))
    {:background-image (str "image-set(url(\"" (smallest x 64) "\") 1x, url(\""
                            (smallest x 128) "\") 2x)")
     :background-size  "cover"}))

;; HTML, section 4.8.4.3: with widths in the srcset and the img's size in
;; sizes, a browser loads the smallest artwork that fills it at the
;; screen's density. W3C, Images tutorial, decorative images: the title
;; beside the artwork names the show, so the alt is empty.
(defn img
  "The img of the artwork of `x`, a show or an episode, `size` CSS pixels
  square, with the `attrs` merged in, or nil without artwork. With :under
  in the `attrs`, a small copy lies under it while it loads."
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
                   (cond-> (dissoc attrs :under)
                     (:under attrs) (update :style merge (under x))))])))

;; WCAG 2.2, technique H2: an image and the text beside it that link to
;; the same page are one link, not two in turn. The title is that link, so
;; the artwork's link is for a pointer alone, out of the tab order and
;; hidden from a screen reader.
;; https://www.w3.org/WAI/WCAG22/Techniques/html/H2
(defn linked
  "The artwork `img` as a link to `href`, with the `title` beside it as its
  tooltip, or nil without an img."
  [href title img]
  (when img
    [:a.artwork-link {:href href :title title :tabindex -1 :aria-hidden "true"} img]))
