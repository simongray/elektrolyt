(ns dk.simongray.elektrolyt.ui.facts
  "The facts of an episode or a show in a line, e.g. its show, date and
  length."
  (:require [clojure.string :as str]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.wary-fetch.url :as url]))

(defn source-host
  "The domain of the feed `supplement` that an episode came from, without
  www., or nil when it isn't a URL."
  [supplement]
  (when (string? supplement)
    (some-> (url/host supplement) (str/replace #"^www\." "") not-empty)))

(defn source-text
  "The words that say an episode came from the feed `supplement` rather
  than its show's own, e.g. \"From drpodcast.nu\", or nil when it didn't."
  [supplement]
  (when supplement
    (str "From " (or (source-host supplement) "another feed"))))

;; Apple Podcasts keeps subscriber-only episodes on the show's one page and
;; marks them in their rows rather than listing them apart. The mark here
;; is a puzzle piece, for an episode that fills a gap in its show's own
;; feed.
;; https://podcasters.apple.com/support/set-up-your-show-for-a-subscription
(defn source
  "The mark for the row of an episode that came from the feed `supplement`:
  a puzzle piece whose tooltip and hidden text say where from, or nil."
  [supplement]
  (when-let [text (source-text supplement)]
    [:span
     (controls/in-text icons/puzzle-piece text)
     [:span.visually-hidden text]]))

(defn labelled-source
  "The mark for the page of an episode that came from the feed
  `supplement`: a puzzle piece and the words that say where from, or nil."
  [supplement]
  (when-let [text (source-text supplement)]
    [:span.source (controls/in-text icons/puzzle-piece) text]))

(def separator
  "What stands between two facts in a line: a middle dot that shows, and a
  comma for a screen reader, so that it doesn't run the facts together."
  (list [:span.separator {:aria-hidden "true"} " · "]
        [:span.visually-hidden ", "]))

(defn line
  "The `facts` in a line, e.g. Technology · Explicit, or nil when none of
  them has a value."
  [facts]
  (when-let [facts (seq (remove nil? facts))]
    (into [:p] (interpose separator facts))))
