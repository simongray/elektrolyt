(ns dk.simongray.elektrolyt.links
  "Links that leave the app, which open in a new tab, so that following one
  doesn't stop what plays."
  (:require [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.wary-fetch.url :as url]))

(def note-id
  "The id of the note on every page that says a link opens in a new tab."
  "new-tab-note")

(def note
  "What the note says, which the icon of each such link has as its tooltip."
  "Opens in a new tab")

(def new-tab-icon
  "The icon after the text of a link that opens in a new tab, unless the
  link has one of its own, e.g. a feed's."
  icons/arrow-square-out)

;; WCAG 2.2, technique G201: a warning that a link opens a new window, which
;; shows and which a screen reader reads, as in its example 2: an icon
;; after the text, and the note as the link's description. The text of the
;; link, and of the show notes that hold it, stays as it was, in a span
;; that keeps the icon to the line of its last word (layout.css). Browsers
;; open a link of target=_blank without an opener, so it needs no
;; rel=noopener.
;; https://www.w3.org/WAI/WCAG22/Techniques/general/G201
(defn new-tab
  "The link `a`, as Hiccup, made to open in a new tab when it goes to the
  web, with the `icon` after its text, or else new-tab-icon, and the note
  as its description."
  ([a]
   (new-tab a new-tab-icon))
  ([[tag attrs & children :as a] icon]
   (if (url/web-url? (:href attrs))
     [tag (assoc attrs :target "_blank" :aria-describedby note-id)
      (into [:span] children)
      [controls/icon {:icon icon :class "in-text" :title note}]]
     a)))
