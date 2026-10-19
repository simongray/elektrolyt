(ns dk.simongray.podcast-app.look-test
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
  "The colour of each page of the navigation."
  (into {} (for [p ["queue" "inbox" "library" "search" "settings"]]
             [p (get root (str "--" p))])))

(deftest the-stylesheets-have-the-tokens
  (is (= {:space :oklch :l 0.33 :c 0.1 :turn 180} (first (tokens shell "--bar"))))
  (is (= {:space :lch :l 42 :c 50 :turn 0}
         (first (tokens (declarations look ".shell > nav li" 2) "--label"))))
  (is (= 5 (count (filter some? (vals pages)))))
  (is (= "oklch(20% 0 0)" (get root "--app")))
  (is (= ["oklch(100% 0 0)" "oklch(17.5% 0.003 80)"] paper)))

(deftest the-bars-contrast-at-every-hue
  (doseq [[k on wants] [["--on-bar" "--bar" 4.5]
                        ["--on-bar-2" "--bar" 4.5]
                        ["--bar-rule" "--bar" 3]
                        ["--on-bar-fill" "--bar-fill" 4.5]]]
    (let [[a] (tokens shell k)
          [b] (tokens shell on)]
      (is (<= wants (least-ratio a b seeds)) (str k " on " on)))))

(deftest the-links-and-buttons-on-the-paper-contrast-at-every-hue
  (doseq [[i mode] [[0 "light"] [1 "dark"]]
          :let [accent (nth (tokens main "--accent") i)]]
    (testing mode
      (is (<= 4.5 (least-ratio accent (nth paper i) seeds)) "links")
      (is (<= 4.5 (least-ratio (nth (tokens main "--accent-ink") i) accent seeds)) "a filled button's label")
      (is (<= 4.5 (least-ratio accent (nth (tokens main "--accent-soft") i) seeds)) "links on a soft fill"))))

(defn label-ratios
  "The least contrast ratio of the label `label` of each page with the
  `ground`, a token or a colour, over the `xs` seeds of the ground."
  [label ground xs]
  (into {} (for [[p seed] pages]
             [p (apply min (for [clip? [false true]]
                             (least-ratio (colour label seed clip?) ground xs)))])))

(deftest the-labels-contrast-with-their-ground
  (let [[on-bar] (tokens (declarations look ".shell > nav li" 0) "--label")
        [bar]    (tokens shell "--bar")
        rail     (tokens (declarations look ".shell > nav li" 2) "--label")]
    (is (every? #(<= 4.5 %) (vals (label-ratios on-bar bar seeds)))
        "on the bar of every artwork")
    (is (every? #(<= 4.5 %) (vals (label-ratios on-bar bar [(get root "--app")])))
        "and of the app's own near-black")
    (doseq [i [0 1]]
      (is (every? #(<= 4.5 %) (vals (label-ratios (nth rail i) (nth paper i) [nil])))
          (str "on the paper, " (["light" "dark"] i))))))

(def color-blind
  "The labels for colour blindness of each page, on a bar and on the
  paper in light and dark."
  (let [rule #(str ".shell[data-palette=\"color-blind\"] > nav li[data-page=\"" % "\"]")]
    (into {} (for [p (keys pages)]
               [p {:bar   (get (declarations look (rule p) 0) "--label")
                   :paper (light-dark (get (declarations look (rule p) 2) "--label"))}]))))

;; a ΔEOK of 0.02 is about the least that shows, as vision/least-difference
;; has it, and these are meant to look different, not just to differ
(deftest the-labels-for-colour-blindness-stay-apart
  (let [bars   (map :bar (vals color-blind))
        lights (map (comp first :paper) (vals color-blind))
        darks  (map (comp second :paper) (vals color-blind))
        [bar]  (tokens shell "--bar")]
    (is (every? some? (concat bars lights darks)) "as look.css has them")
    (doseq [[ground cs] [["on the bar" bars] ["on the light paper" lights] ["on the dark paper" darks]]]
      (is (every? #(<= 0.05 %) (vals (vision/least-difference cs)))
          (str ground ", for every colour vision")))
    (is (every? #(<= 4.5 (least-ratio % bar seeds)) bars) "with contrast on the bar of every artwork")
    (is (every? #(<= 4.5 (contrast/ratio % (first paper))) lights) "and on the light paper")
    (is (every? #(<= 4.5 (contrast/ratio % (second paper))) darks) "and on the dark paper")))

(deftest more-contrast-meets-the-enhanced-level
  (let [more  (declarations look ":root[data-contrast=\"more\"] .shell" 0)
        links (declarations look ":root[data-contrast=\"more\"] .shell > main" 0)
        rail  (tokens (declarations look ":root[data-contrast=\"more\"] .shell > nav li" 2) "--label")
        [bar] (tokens shell "--bar")]
    (is (<= 7 (least-ratio (first (tokens more "--on-bar-2")) bar seeds)) "secondary text on the bars")
    (is (<= 4.5 (least-ratio (first (tokens more "--bar-rule")) bar seeds)) "the edges of controls on the bars")
    (doseq [i [0 1]]
      (is (<= 7 (least-ratio (nth (tokens links "--accent") i) (nth paper i) seeds)) "links")
      (is (every? #(<= 7 %) (vals (label-ratios (nth rail i) (nth paper i) [nil]))) "labels on the paper"))))
