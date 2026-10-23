(ns dk.simongray.elektrolyt.ui.facts
  "The facts of an episode or a show in a line, e.g. its show, its date and
  its length, as the rows of the lists and the pages set them."
  (:require [clojure.string :as str]
            [dk.simongray.wary-fetch.url :as url]))

(defn source-host
  "The host of the feed `supplement` that an episode came from, as
  feed/supplement names it, without www., or nil when it names no URL."
  [supplement]
  (when (string? supplement)
    (some-> (url/host supplement) (str/replace #"^www\." "") not-empty)))

;; Apple Podcasts keeps the episodes that only subscribers get on the
;; show's one page, and names what's special about one in a label rather
;; than in a list of its own
;; https://podcasters.apple.com/support/set-up-your-show-for-a-subscription
(defn source
  "The fact that an episode came from the feed `supplement` rather than its
  show's own, e.g. \"From drpodcast.nu\", or nil when it didn't."
  [supplement]
  (when supplement
    (str "From " (or (source-host supplement) "another feed"))))

;; The middle dot is decoration, which a screen reader leaves out, so it
;; reads a comma in its place rather than run two facts together, e.g.
;; "Oct 2, 2026, 1 h 30 min" rather than "Oct 2, 2026 1 h 30 min"
(def separator
  "What stands between two facts in a line: a middle dot that shows, and a
  comma that only a screen reader reads."
  (list [:span {:aria-hidden "true"} " · "]
        [:span.visually-hidden ", "]))

(defn line
  "The `facts` in a line, e.g. Technology · Explicit, or nil when none of
  them has a value."
  [facts]
  (when-let [facts (seq (remove nil? facts))]
    (into [:p] (interpose separator facts))))
