(ns dk.simongray.podcast-app.text
  "The words of the app and the numbers in them, in the page's language:
  counts of things, lists, the names of languages and the sizes of files.
  Text from a feed is in the feed's language instead, which lang names.

  Where the browser knows the language's rules, its Intl formatters write
  them, e.g. Intl.ListFormat a list. On the JVM, plain English stands in."
  (:require [clojure.string :as str]
            [dk.simongray.langtag :as langtag]))

(def default-language
  "The language of the page where no document names it, e.g. in the tests:
  British English, the language of the app's words, which the lang of
  index.html names too."
  "en-GB")

(defn language
  "The language of the page, which its words and numbers are written in."
  []
  #?(:clj  default-language
     :cljs (or (some-> js/globalThis .-document .-documentElement .-lang not-empty)
               default-language)))

;; The app's words are English, so Intl.PluralRules, which only names the
;; category of a count, would add nothing until they're translated
(defn plural
  "The `noun` for a count of `n`, e.g. \"episodes\" for 3."
  [n noun]
  (if (= 1 n) noun (str noun "s")))

(defn counted
  "The count `n` of the `noun` in words, e.g. \"3 episodes\"."
  [n noun]
  (str n " " (plural n noun)))

;; ECMA-402, Intl.ListFormat: the conjunction of the page's language,
;; which in British English has no comma before the "and"
;; https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/ListFormat
(defn listing
  "The strings `xs` as a list in words, e.g. \"a, b and c\"."
  [xs]
  #?(:clj  (if (next xs)
             (str (str/join ", " (butlast xs)) " and " (last xs))
             (str (first xs)))
     :cljs (.format (js/Intl.ListFormat. (language) #js {:type "conjunction"})
                    (into-array xs))))

;; ECMA-402, Intl.ListFormat.prototype.formatToParts: the same list as
;; listing, with the strings apart from the words between them
;; https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/ListFormat/formatToParts
(defn listing-parts
  "The strings `xs` as a list in words, in parts, each a pair of :element
  and one of `xs` or of :literal and the words between them, e.g.
  [[:element \"a\"] [:literal \" and \"] [:element \"b\"]], so that each
  string can be marked up."
  [xs]
  #?(:clj  (let [n (count xs)]
             (->> (map-indexed (fn [i x]
                                 (cond-> [[:element x]]
                                   (< i (- n 2)) (conj [:literal ", "])
                                   (= i (- n 2)) (conj [:literal " and "])))
                               xs)
                  (apply concat)
                  vec))
     :cljs (mapv (fn [^js part] [(keyword (.-type part)) (.-value part)])
                 (.formatToParts (js/Intl.ListFormat. (language) #js {:type "conjunction"})
                                 (into-array xs)))))

;; ECMA-402, Intl.DisplayNames: the name of a language in the language of
;; the page, which gives back the tag itself when it doesn't know it
(defn language-name
  "The name of the language `tag` in the language of the page, e.g.
  \"English\" for en, or the tag when the browser can't name it."
  [tag]
  #?(:clj  tag
     :cljs (try
             (.of (js/Intl.DisplayNames. #js [(language)] #js {:type "language"}) tag)
             (catch :default _ tag))))

;; HTML, the lang attribute: an element without one is in the language of
;; its parent, here the page's en-GB. Browsers hyphenate by that language
;; (MDN, hyphens), and screen readers pronounce by it, so text from a feed
;; takes the feed's language (WCAG 2.2, SC 3.1.2 Language of Parts,
;; technique H58). The value must be a BCP 47 tag, so a feed that names its
;; language English gets none: the browser would take it for an unknown
;; language and hyphenate nothing. The en_US of some feeds means en-US.
;; https://html.spec.whatwg.org/multipage/dom.html#the-lang-and-xml:lang-attributes
;; https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/hyphens
;; https://www.w3.org/WAI/WCAG22/Understanding/language-of-parts.html
;; https://www.w3.org/WAI/WCAG22/Techniques/html/H58
(defn lang
  "The lang attribute of text in the language `tag` as a feed writes it,
  e.g. \"en-US\" for en_us, or nil when it isn't a language tag with a
  language of two or three letters, as ISO 639 has them."
  [tag]
  (when-let [{:keys [language] :as m} (some-> tag (str/replace "_" "-") langtag/parse)]
    (when (<= 2 (count language) 3)
      (langtag/tag m))))

(def unit-symbols
  "The symbols of the units of a quantity, where Intl doesn't write them."
  {"kilobyte" "kB" "megabyte" "MB" "gigabyte" "GB"})

;; ECMA-402, Intl.NumberFormat: a number and the symbol of its unit in the
;; page's language, e.g. "86 MB"
;; https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/NumberFormat/NumberFormat#style
(defn quantity
  "The number `n` of the `unit`, e.g. \"megabyte\", with between
  `min-digits` and `max-digits` decimals, e.g. \"12.3 MB\"."
  [n unit min-digits max-digits]
  #?(:clj  (let [s (String/format java.util.Locale/ROOT (str "%." max-digits "f")
                                  (to-array [(double n)]))]
             (str (cond-> s (< min-digits max-digits) (str/replace #"\.?0+$" ""))
                  " " (unit-symbols unit)))
     :cljs (.format (js/Intl.NumberFormat. (language) #js {:style                 "unit"
                                                          :unit                  unit
                                                          :unitDisplay           "short"
                                                          :minimumFractionDigits min-digits
                                                          :maximumFractionDigits max-digits})
                    n)))

(defn size-text
  "The size of a file of `bytes`, e.g. \"86 MB\", to the kilobyte or the
  megabyte, or to a tenth of a gigabyte."
  [bytes]
  (cond
    (< bytes 1e6) (quantity (/ bytes 1e3) "kilobyte" 0 0)
    (< bytes 1e9) (quantity (/ bytes 1e6) "megabyte" 0 0)
    :else         (quantity (/ bytes 1e9) "gigabyte" 0 1)))

(defn megabytes
  "The `bytes` in megabytes to a tenth, e.g. \"12.3 MB\"."
  [bytes]
  (quantity (/ bytes 1e6) "megabyte" 1 1))
