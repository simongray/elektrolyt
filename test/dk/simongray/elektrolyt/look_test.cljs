(ns dk.simongray.elektrolyt.look-test
  "The contrast of the look's colours, read from tokens.css and look.css as
  they are: each pair that WCAG 2.2 asks to contrast, from seeds of every
  hue and chroma, both as colour-coded maps the colours into sRGB and as a
  browser clips them.
  https://www.w3.org/WAI/WCAG22/Understanding/contrast-minimum.html
  https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dk.simongray.colour-coded.contrast :as contrast]
            [dk.simongray.colour-coded.gamut :as gamut]
            [dk.simongray.colour-coded.mix :as mix]
            [dk.simongray.colour-coded.tone :as tone]
            [dk.simongray.colour-coded.vision :as vision]))

(defn stylesheet
  "The text of the stylesheet `file` of public/css."
  [file]
  (.readFileSync (js/require "fs") (str "public/css/" file) "utf8"))

(defn escaped
  "The text `s` with the characters that a regex reads escaped."
  [s]
  (str/replace s #"[.*+?^${}()|\[\]\\]" "\\$&"))

(defn declarations
  "The custom properties of the rules of the `css` whose selector is
  `selector`, indented by `indent` spaces, e.g. 2 inside a media query, as a
  map of each name to its value."
  [css selector indent]
  (let [rule (js/RegExp. (str "^" (str/join (repeat indent " ")) (escaped selector) " \\{([^}]*)\\}") "m")]
    (into {}
          (for [[_ body] (re-seq rule css)
                [_ k v]  (re-seq #"(--[\w-]+):\s*([^;]+);" body)]
            [k (str/trim v)]))))

(defn top-level-comma
  "The index of the first comma of `s` outside its parentheses."
  [s]
  (loop [i 0 depth 0]
    (case (nth s i)
      "(" (recur (inc i) (inc depth))
      ")" (recur (inc i) (dec depth))
      "," (if (zero? depth) i (recur (inc i) depth))
      (recur (inc i) depth))))

(defn light-dark
  "The light and the dark value of the CSS `value`, the arguments of its
  light-dark(), or else the value itself for both."
  [value]
  (if-let [[_ args] (re-find #"^light-dark\((.*)\)$" value)]
    (let [i (top-level-comma args)]
      [(str/trim (subs args 0 i)) (str/trim (subs args (inc i)))])
    [value value]))

(defn token
  "The token of tone of the CSS `value`, a relative colour from a custom
  property, e.g. oklch(from var(--seed) 33% min(c, 0.1) calc(h + 180)), or
  else the value itself, a colour."
  [value]
  (if-let [[_ space l c turn] (re-find #"^(oklch|lch)\(from var\(--[\w-]+\) ([\d.]+)%? min\(c, ([\d.]+)\) (?:h|calc\(h \+ ([\d.]+)\))\)$" value)]
    {:space (keyword space)
     :l     (cond-> (js/parseFloat l) (= "oklch" space) (/ 100))
     :c     (js/parseFloat c)
     :turn  (if turn (js/parseFloat turn) 0)}
    value))

(defn tokens
  "The light and the dark token of the custom property `k` of the
  declarations `m`."
  [m k]
  (mapv token (light-dark (get m k))))

(def seeds
  "Seeds of every third hue, from grey to vivid, as tone/worst-contrast
  sweeps them."
  (for [h (range 0 360 3)
        c (map #(* 0.04 %) (range 11))]
    {:space :oklch :coords [0.5 c h] :alpha 1.0}))

(defn colour
  "The colour of `x` from the `seed`, as tone maps it into sRGB, or as a
  browser clips it when `clip?`, where `x` is a token, or else a colour."
  [x seed clip?]
  (cond
    (not (contains? x :l)) x
    clip?                  (gamut/clip (tone/unmapped x seed))
    :else                  (tone/tone x seed)))

(defn least-ratio
  "The least contrast ratio of `a` and `b`, tokens or colours, over every
  seed of `xs`, mapped and clipped."
  [a b xs]
  (apply min (for [s xs clip? [false true]]
               (contrast/ratio (colour a s clip?) (colour b s clip?)))))

(def root (declarations (stylesheet "tokens.css") ":root" 0))
(def look (stylesheet "look.css"))
(def shell (declarations look ".shell" 0))
(def main (declarations look ".shell > main" 0))

(def paper
  "The paper in light and dark."
  (light-dark (get root "--paper")))

(def pages
  "The colour of each page of the navigation that has one."
  (into {} (for [p ["home" "queue" "library" "search" "settings"]]
             [p (get root (str "--" p))])))

(deftest the-stylesheets-have-the-tokens
  (is (= {:space :oklch :l 0.33 :c 0.1 :turn 180} (first (tokens shell "--bar"))))
  (is (= {:space :lch :l 42 :c 50 :turn 0}
         (first (tokens (declarations look ".shell > nav li" 0) "--label"))))
  (is (= 5 (count (filter some? (vals pages)))))
  (is (= "oklch(20% 0 0)" (get root "--app")))
  (is (= ["oklch(100% 0 0)" "oklch(17.5% 0.003 80)"] paper)))

(deftest the-bar-contrasts-at-every-hue
  (doseq [[k on wants] [["--on-bar" "--bar" 4.5]
                        ["--on-bar-2" "--bar" 4.5]
                        ["--bar-rule" "--bar" 3]
                        ["--on-bar-fill" "--bar-fill" 4.5]]]
    (let [[a] (tokens shell k)
          [b] (tokens shell on)]
      (is (<= wants (least-ratio a b seeds)) (str k " on " on)))))

(deftest the-links-and-buttons-on-the-paper-contrast-at-every-hue
  (doseq [[i mode] [[0 "light"] [1 "dark"]]
          :let [accent (nth (tokens shell "--seed-accent") i)]]
    (testing mode
      (is (<= 4.5 (least-ratio accent (nth paper i) seeds)) "links")
      (is (<= 4.5 (least-ratio (nth (tokens main "--accent-ink") i) accent seeds)) "a filled button's label")
      (is (<= 4.5 (least-ratio accent (nth (tokens main "--accent-soft") i) seeds)) "links on a soft fill"))))

;; The strongest 1% of the black and the white specks of
;; public/images/grain.png, which strongest in
;; dev/dk/simongray/elektrolyt/grain.clj reports. Remake them with the
;; grain.
(def specks
  {:black 0.0549 :white 0.0235})

(defn specked
  "The `ground` under the strongest speck of the grain that brings it
  closer to the `text`, white under lighter text and black under darker,
  laid over it in sRGB, which is where a browser lays it."
  [ground text]
  (let [k (if (> (contrast/luminance text) (contrast/luminance ground)) :white :black)]
    (mix/mix ground (name k) (specks k) :srgb)))

(defn least-specked-ratio
  "The least contrast ratio of the `text` on the `ground`, tokens or
  colours, under the grain's strongest specks, over every seed of `xs`,
  mapped and clipped."
  [text ground xs]
  (apply min (for [s xs clip? [false true]
                   :let [t (colour text s clip?)]]
               (contrast/ratio t (specked (colour ground s clip?) t)))))

(deftest the-text-contrasts-on-the-grain
  (testing "on the bar"
    (doseq [[k on wants] [["--on-bar" "--bar" 4.5]
                          ["--on-bar-2" "--bar" 4.5]
                          ["--bar-rule" "--bar" 3]
                          ["--on-bar-fill" "--bar-fill" 4.5]]]
      (let [[a] (tokens shell k)
            [b] (tokens shell on)]
        (is (<= wants (least-specked-ratio a b seeds)) (str k " on " on)))))
  (doseq [[i mode] [[0 "light"] [1 "dark"]]
          :let [accent (nth (tokens shell "--seed-accent") i)
                ground (nth paper i)]]
    (testing (str "on the " mode " paper")
      (is (<= 4.5 (least-specked-ratio (nth (light-dark (get root "--ink-2")) i) ground [nil])) "secondary text")
      (is (<= 4.5 (least-specked-ratio accent ground seeds)) "links")
      (is (<= 4.5 (least-specked-ratio (nth (tokens main "--accent-ink") i) accent seeds)) "a filled button's label")
      (is (<= 4.5 (least-specked-ratio accent (nth (tokens main "--accent-soft") i) seeds)) "links on a soft fill"))))

(def rail-tone
  "How much of the colour of the label of the navigation's page tones its
  paper."
  (-> #"color-mix\(in oklab, var\(--label, var\(--seed-accent\)\) ([\d.]+)%, var\(--paper\)\)"
      (re-find look)
      second
      js/parseFloat
      (/ 100)))

(defn shades
  "The colours of `x`, a token or a colour, from each seed of `xs`, mapped
  and clipped."
  [x xs]
  (for [s xs clip? [false true]]
    (colour x s clip?)))

(defn least-on-rail
  "The least contrast ratio of the colours `texts` on the rail's paper in
  the mode `i`, 0 for light and 1 for dark, toned with each of the colours
  `tones`."
  [texts tones i]
  (apply min (for [ground (map #(mix/mix (nth paper i) % rail-tone) tones)
                   text   texts]
               (contrast/ratio text ground))))

(defn accents
  "The accents of every seed in the mode `i` of the `shell`'s declarations,
  which tone the rail of a page with no label of its own, e.g. a show's."
  [shell i]
  (shades (nth (tokens shell "--seed-accent") i) seeds))

(deftest the-labels-contrast-with-their-ground
  (let [rail (tokens (declarations look ".shell > nav li" 0) "--label")]
    (doseq [i [0 1]
            :let [labels (shades (nth rail i) (vals pages))]]
      (is (<= 4.5 (least-on-rail labels (concat labels (accents shell i)) i))
          (str "on the navigation's paper, " (["light" "dark"] i))))))

(def player
  "The rule of the labels in the player's colour."
  ".shell[data-palette=\"player\"] > nav li")

(deftest the-labels-in-the-player's-colour-contrast-at-every-hue
  (let [rail (tokens (declarations look player 0) "--label")]
    (doseq [i [0 1]]
      (is (<= 4.5 (apply min (for [c (shades (nth rail i) seeds)]
                               (least-on-rail [c] [c] i))))
          (str "on the navigation's paper, toned with them, " (["light" "dark"] i))))))

(def color-blind
  "The labels for colour blindness of each page, on the paper in light and
  dark."
  (let [rule #(str ".shell[data-palette=\"color-blind\"] [data-page=\"" % "\"]")]
    (into {} (for [p (keys pages)]
               [p (light-dark (get (declarations look (rule p) 0) "--label"))]))))

;; a ΔEOK of 0.02 is about the least that shows, as vision/least-difference
;; has it, and these are meant to look different, not just to differ
(deftest the-labels-for-colour-blindness-stay-apart
  (let [lights (map first (vals color-blind))
        darks  (map second (vals color-blind))]
    (is (every? some? (concat lights darks)) "as look.css has them")
    (doseq [[ground cs] [["on the light paper" lights] ["on the dark paper" darks]]]
      (is (every? #(<= 0.05 %) (vals (vision/least-difference cs)))
          (str ground ", for every colour vision")))
    (is (<= 4.5 (least-on-rail lights (concat lights (accents shell 0)) 0)) "with contrast on the light paper")
    (is (<= 4.5 (least-on-rail darks (concat darks (accents shell 1)) 1)) "and on the dark paper")))

(deftest more-contrast-meets-the-enhanced-level
  (let [more   (declarations look ":root[data-contrast=\"more\"] .shell" 0)
        rail   (tokens (declarations look ":root[data-contrast=\"more\"] .shell[data-palette=\"pages\"] > nav li" 0) "--label")
        player (tokens (declarations look (str ":root[data-contrast=\"more\"] " player) 0) "--label")
        [bar]  (tokens shell "--bar")]
    (is (<= 7 (least-ratio (first (tokens more "--on-bar-2")) bar seeds)) "secondary text on the bar")
    (is (<= 4.5 (least-ratio (first (tokens more "--bar-rule")) bar seeds)) "the edges of controls on the bar")
    (doseq [i [0 1]
            :let [labels (shades (nth rail i) (vals pages))]]
      (is (<= 7 (least-ratio (nth (tokens more "--seed-accent") i) (nth paper i) seeds)) "links")
      (is (<= 7 (least-on-rail labels (concat labels (accents more i)) i)) "labels on the navigation's paper")
      (is (<= 7 (apply min (for [c (shades (nth player i) seeds)]
                             (least-on-rail [c] [c] i))))
          "labels in the player's colour on the navigation's paper"))))
