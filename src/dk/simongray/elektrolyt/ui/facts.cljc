(ns dk.simongray.elektrolyt.ui.facts
  "The facts of an episode or a show in a line, e.g. its show, its date and
  its length, as the rows of the lists and the pages set them.")

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
