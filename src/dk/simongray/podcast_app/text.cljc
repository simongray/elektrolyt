(ns dk.simongray.podcast-app.text
  "The words of the app and the numbers in them, in the page's language:
  counts of things, lists, the names of languages and the sizes of files.

  Where the browser knows the language's rules, its Intl formatters write
  them, e.g. Intl.ListFormat a list. On the JVM, plain English stands in."
  #?(:clj (:require [clojure.string :as str])))

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
