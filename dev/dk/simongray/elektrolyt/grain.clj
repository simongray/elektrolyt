(ns dk.simongray.elektrolyt.grain
  "Make the grain of the app's paper, public/images/grain.png, from a
  texture of paper.

  The texture is dev-resources/paper.png, the paper of simon.grays.blog
  (CC0). It's grey at 15% alpha, which the blog lays over its cream, so it
  darkens the whole page as well as giving it a grain. On white that veil
  would read as grey paper, so the grain keeps only the texture's
  variation. Each pixel darker than the texture's median grey becomes a
  black speck, and each lighter one a white speck, with an alpha in
  proportion to the difference. White paper shows only the black specks
  and dark paper only the white ones, while a colour shows both.

  The design, with a mock-up at each strength:
  https://claude.ai/artifact/MrZ7s4UY9fihNoS8hSpMfq"
  (:require [clojure.java.io :as io])
  (:import (java.awt.image BufferedImage)
           (javax.imageio ImageIO)))

(defn pixels
  "The ARGB ints of the pixels of `image`, row by row."
  [^BufferedImage image]
  (let [w (.getWidth image)
        h (.getHeight image)]
    (vec (.getRGB image 0 0 w h nil 0 w))))

(defn grey
  "The grey of the ARGB `pixel`, its red channel, since the texture is
  grey."
  [pixel]
  (bit-and (bit-shift-right pixel 16) 0xff))

(defn alpha
  "The alpha of the ARGB `pixel`, from 0 to 255."
  [pixel]
  (bit-and (unsigned-bit-shift-right pixel 24) 0xff))

(defn median
  "The median of the numbers `xs`."
  [xs]
  (nth (sort xs) (quot (count xs) 2)))

;; At strength 1 a speck varies the paper as much as the texture's own
;; layer varies the blog's cream: its alpha times its grey's distance
;; from the median.
(defn speck
  "The ARGB int of the speck of grain for the texture's `pixel` at
  `strength`, against the texture's `median` grey."
  [strength median pixel]
  (let [difference (- (grey pixel) median)
        opacity    (* strength (/ (alpha pixel) 255) (/ (abs difference) 255))
        a          (min 255 (Math/round (double (* 255 opacity))))
        rgb        (if (neg? difference) 0x000000 0xffffff)]
    (unchecked-int (bit-or (bit-shift-left a 24) rgb))))

(defn grain
  "The grain of the texture `image` at `strength`, an image of its size."
  [^BufferedImage image strength]
  (let [w      (.getWidth image)
        h      (.getHeight image)
        texels (pixels image)
        m      (median (map grey texels))
        out    (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)]
    (.setRGB out 0 0 w h (int-array (map #(speck strength m %) texels)) 0 w)
    out))

(defn write-grain!
  "Write the grain of the texture in the file `in` at `strength` as a PNG
  to the file `out`."
  [in out strength]
  (ImageIO/write ^BufferedImage (grain (ImageIO/read (io/file in)) strength)
                 "png"
                 (io/file out)))

(defn strongest
  "The alpha, from 0 to 1, of the strongest 1% of the black and the white
  specks of the grain in the file `f`, as {:black a :white a}, which the
  look test lays under text."
  [f]
  (let [specks (group-by #(if (zero? (bit-and % 0xffffff)) :black :white)
                         (pixels (ImageIO/read (io/file f))))]
    (update-vals specks (fn [xs]
                          (let [as (vec (sort (map alpha xs)))]
                            (/ (nth as (quot (* 99 (count as)) 100)) 255.0))))))

(comment
  ;; the strength that Simon chose on the artifact
  (write-grain! "dev-resources/paper.png" "public/images/grain.png" 2)
  (strongest "public/images/grain.png")
  ;; => {:black 0.0549 :white 0.0235}, which look_test.cljs has as specks
  #_.)
