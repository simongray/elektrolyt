(ns dk.simongray.elektrolyt.ui.time
  "Dates, durations and clock times as text in the page's language, and as
  time elements (HTML, section 4.5.14)."
  (:require #?(:clj [clojure.string :as str])
            [dk.simongray.podcast.util.time :as time]
            #?(:cljs [dk.simongray.elektrolyt.ui.text :as text])))

(defn date-text
  "The date of the instant `inst` in the language of the page, e.g. \"16
  Oct 2026\"."
  [inst]
  #?(:clj  (str inst)
     :cljs (.toLocaleDateString inst (text/language) #js {:dateStyle "medium"})))

(defn date-attribute
  "The instant `inst` as the datetime of a time element."
  [inst]
  #?(:clj  (str (.toInstant ^java.util.Date inst))
     :cljs (.toISOString inst)))

(defn date
  "The date of the instant `inst` as a time element."
  [inst]
  [:time {:datetime (date-attribute inst)} (date-text inst)])

(defn time-text
  "The time of day of the instant `inst` in the language of the page, e.g.
  \"14:32\"."
  [inst]
  #?(:clj  (str inst)
     :cljs (.toLocaleTimeString inst (text/language) #js {:timeStyle "short"})))

(defn duration-parts
  "The `seconds` of an episode to the minute, as hours and minutes, or as
  seconds when it's shorter than half a minute."
  [seconds]
  (let [minutes (quot (+ (long seconds) 30) 60)]
    (if (zero? minutes)
      {:seconds (long seconds)}
      {:hours (quot minutes 60) :minutes (rem minutes 60)})))

;; ECMA-402, Intl.DurationFormat, in its narrow style, e.g. "1h 23m", which
;; leaves out the units of zero. Pocket Casts writes a length the same way.
;; https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/DurationFormat
;; https://github.com/Automattic/pocket-casts-android/blob/main/modules/services/localization/src/main/res/values/strings.xml#L486-L489
;; It writes the seconds even when they're 0, which it would leave out, and
;; nothing for a number too large to be a duration, which it would throw on
(defn duration-text
  "The `seconds` of an episode in words, e.g. \"1h 23m\", or nil when
  they're too many to count."
  [seconds]
  (when (time/countable? seconds)
    (let [parts (duration-parts seconds)]
      #?(:clj  (or (not-empty (str/join " " (for [[k unit] [[:hours "h"] [:minutes "m"] [:seconds "s"]]
                                                   :when (pos? (k parts 0))]
                                               (str (k parts) unit))))
                   "0s")
         :cljs (.format (js/Intl.DurationFormat. (text/language)
                                                 #js {:style          "narrow"
                                                      :secondsDisplay (if (:seconds parts) "always" "auto")})
                        (clj->js parts))))))

(defn duration-attribute
  "The `seconds` as the datetime of a time element, e.g. \"PT5025S\"."
  [seconds]
  (str "PT" (long seconds) "S"))

(defn duration
  "The `seconds` of an episode as a time element, e.g. 1h 23m, or nil when
  they're too many to count."
  [seconds]
  (when-let [text (duration-text seconds)]
    [:time {:datetime (duration-attribute seconds)} text]))

(defn clock-text
  "The `seconds` as a clock shows them, e.g. \"12:34\", or \"–:––\" while
  they're unknown."
  [seconds]
  (or (time/format-clock seconds) "–:––"))

(defn clock
  "The `seconds` into an episode as a time element, e.g. 12:34, whose
  datetime is the duration since its start."
  [seconds]
  [:time {:datetime (duration-attribute (or seconds 0))} (clock-text seconds)])
