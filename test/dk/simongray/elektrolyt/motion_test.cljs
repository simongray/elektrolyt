(ns dk.simongray.elektrolyt.motion-test
  "That every motion of the stylesheets waits for a listener who doesn't
  ask for less: each transition, animation and smooth scroll that takes
  any time lies in a block of prefers-reduced-motion: no-preference.
  https://www.w3.org/WAI/WCAG22/Techniques/css/C39"
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dk.simongray.elektrolyt.look-test :as look-test]))

(defn declarations
  "The declarations of the stylesheet `css` as maps of their :property,
  their :value and the preludes of the blocks :within which they lie, the
  outermost first. It reads blocks by the depth of their braces, past
  comments and strings."
  [css]
  (let [n     (count css)
        found (fn [out stack text]
                (let [text (str/trim text)
                      i    (str/index-of text ":")]
                  (if (and i (not (str/starts-with? text "@")))
                    (conj out {:property (str/trim (subs text 0 i))
                               :value    (str/trim (subs text (inc i)))
                               :within   stack})
                    out)))]
    (loop [i 0 stack [] start 0 out []]
      (if (<= n i)
        out
        (let [c (nth css i)]
          (cond
            (str/starts-with? (subs css i) "/*")
            (let [end (or (str/index-of css "*/" (+ i 2)) n)]
              (recur (+ end 2) stack (if (= start i) (+ end 2) start) out))

            (#{"\"" "'"} c)
            (recur (inc (or (str/index-of css c (inc i)) n)) stack start out)

            (= "{" c)
            (recur (inc i) (conj stack (str/trim (subs css start i))) (inc i) out)

            (= "}" c)
            (recur (inc i) (pop stack) (inc i) (found out stack (subs css start i)))

            (= ";" c)
            (recur (inc i) stack (inc i) (found out stack (subs css start i)))

            :else
            (recur (inc i) stack start out)))))))

(defn seconds
  "The first time of the CSS `value`, e.g. 0.6 for \"--seed 0.6s ease\", in
  seconds, or 0 without one."
  [value]
  (if-let [[_ number unit] (re-find #"(?:^|[\s(])(\d*\.?\d+)(ms|s)\b" value)]
    (cond-> (js/parseFloat number) (= "ms" unit) (/ 1000))
    0))

(defn moves?
  "Whether the `declaration` moves anything for a while: a transition or
  an animation of which a part takes any time, or a smooth scroll."
  [{:keys [property value]}]
  (case property
    ("transition" "transition-duration" "animation" "animation-duration")
    (boolean (some #(pos? (seconds %)) (str/split value #",")))

    "scroll-behavior" (= "smooth" value)
    false))

(defn gated?
  "Whether the `declaration` lies in a block for a listener who doesn't ask
  for less motion."
  [{:keys [within]}]
  (boolean (some #(re-find #"prefers-reduced-motion:\s*no-preference" %) within)))

(deftest the-declarations-of-a-stylesheet
  (let [css (str "/* a comment { */ a { color: red; transition: color 0s }\n"
                 "@media (prefers-reduced-motion: no-preference) {\n"
                 "  a { transition: color 0.15s, outline 0s; }\n"
                 "  b::after { content: \"{;}\"; animation: turn 1s linear infinite }\n"
                 "}\n"
                 "c { animation: appear 0s 1s both; scroll-behavior: smooth }")
        found (declarations css)]
    (is (= ["color" "transition" "transition" "content" "animation" "animation" "scroll-behavior"]
           (map :property found))
        "past comments and strings")
    (is (= [false false true false true false true] (map moves? found))
        "of which those that take any time move, though a delay doesn't")
    (is (= [false false true true true false false] (map gated? found)))))

(deftest every-motion-waits-for-no-preference
  (is (seq (filter moves? (declarations (look-test/stylesheet "look.css"))))
      "of which look.css has some")
  (doseq [file ["tokens.css" "layout.css" "look.css" "app.css"]
          :let [moving (filter moves? (declarations (look-test/stylesheet file)))]]
    (testing file
      (is (empty? (remove gated? moving))
          "outside a block of prefers-reduced-motion: no-preference"))))
