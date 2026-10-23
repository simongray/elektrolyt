(ns dk.simongray.elektrolyt.ui.facts
  "The facts of an episode or a show in a line, e.g. its show, its date and
  its length, as the rows of the lists and the pages set them."
  (:require [clojure.string :as str]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.wary-fetch.url :as url]))

(defn source-host
  "The host of the feed `supplement` that an episode came from, as
  feed/supplement names it, without www., or nil when it names no URL."
  [supplement]
  (when (string? supplement)
    (some-> (url/host supplement) (str/replace #"^www\." "") not-empty)))

(defn source-text
  "The words that say an episode came from the feed `supplement` rather
  than its show's own, e.g. \"From drpodcast.nu\", or nil when it didn't."
  [supplement]
  (when supplement
    (str "From " (or (source-host supplement) "another feed"))))

;; Apple Podcasts keeps the episodes that only subscribers get on the
;; show's one page, and marks what's special about one in its row rather
;; than in a list of its own. The mark is Phosphor's puzzle piece, for an
;; episode from elsewhere that fills a gap in its show's own feed. In a
;; row, it's the icon alone, whose words are its tooltip and what a screen
;; reader reads, as an icon button's are. The episode's own page has room
;; for the words.
;; https://podcasters.apple.com/support/set-up-your-show-for-a-subscription
(defn source
  "The mark of an episode that came from the feed `supplement`, for its row
  in a list: a puzzle piece whose tooltip is source-text, or nil."
  [supplement]
  (when-let [text (source-text supplement)]
    [:span
     (controls/in-text icons/puzzle-piece text)
     [:span.visually-hidden text]]))

(defn labelled-source
  "The mark of an episode that came from the feed `supplement`, for its own
  page: a puzzle piece before source-text, or nil."
  [supplement]
  (when-let [text (source-text supplement)]
    [:span.source (controls/in-text icons/puzzle-piece) text]))

;; The middle dot is decoration, which a screen reader leaves out, so it
;; reads a comma in its place rather than run two facts together, e.g.
;; "Oct 2, 2026, 1 h 30 min" rather than "Oct 2, 2026 1 h 30 min"
(def separator
  "What stands between two facts in a line: a middle dot that shows, and a
  comma that only a screen reader reads."
  (list [:span.separator {:aria-hidden "true"} " · "]
        [:span.visually-hidden ", "]))

(defn line
  "The `facts` in a line, e.g. Technology · Explicit, or nil when none of
  them has a value."
  [facts]
  (when-let [facts (seq (remove nil? facts))]
    (into [:p] (interpose separator facts))))
