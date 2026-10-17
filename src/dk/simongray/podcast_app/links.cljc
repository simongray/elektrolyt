(ns dk.simongray.podcast-app.links
  "Links that leave the app, which open in a new tab, so that following one
  doesn't stop what plays."
  (:require [dk.simongray.wary-fetch.url :as url]))

(def note-id
  "The id of the note on every page that says a link opens in a new tab."
  "new-tab-note")

;; WCAG 2.2, technique G201: a warning that a link opens a new window, for
;; a screen reader in its description, so that the text of the link, and
;; of the show notes that hold it, stays as it was. Browsers open a link of
;; target=_blank without an opener, so it needs no rel=noopener.
;; https://www.w3.org/WAI/WCAG22/Techniques/general/G201
(defn new-tab
  "The link `a`, as Hiccup, made to open in a new tab when it goes to the
  web, with the note that says so as its description."
  [[tag attrs & children :as a]]
  (if (url/web-url? (:href attrs))
    (into [tag (assoc attrs :target "_blank" :aria-describedby note-id)] children)
    a))
