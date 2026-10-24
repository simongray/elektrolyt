(ns dk.simongray.elektrolyt.ui.links
  "Links that leave the app: those to the web, which open in a new tab, so
  that following one doesn't stop what plays, and those to an email
  address, which open the listener's email app. An icon after the text of
  each says where it goes, and so does a note on every page, as its
  description."
  (:require [clojure.string :as str]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.wary-fetch.url :as url]))

(def notes
  "The notes on every page that say where a link goes, by its kind, each
  with its id and its text, which the icon of the link has as its
  tooltip."
  {:new-tab {:id "new-tab-note" :text "Opens in a new tab"}
   :email   {:id "email-note" :text "Opens your email app"}})

(def new-tab-icon
  "The icon after the text of a link that opens in a new tab, unless the
  link has one of its own, e.g. a feed's."
  icons/arrow-square-out)

(defn marked
  "The link `a`, as Hiccup, with the `icon` after its text, and the note of
  its `kind` as its description. The text is in a span of its own, so that
  the icon keeps to the line of its last word (layout.css)."
  [[tag attrs & children] kind icon]
  (let [{:keys [id text]} (notes kind)]
    [tag (assoc attrs :aria-describedby id)
     (into [:span] children)
     (controls/in-text icon text)]))

;; WCAG 2.2, technique G201: a warning that a link opens a new window, which
;; shows and which a screen reader reads, as in its example 2: an icon
;; after the text, and the note as the link's description, so that the
;; text of the link, and of the show notes that hold it, stays as it was.
;; Browsers open a link of target=_blank without an opener, so it needs no
;; rel=noopener.
;; https://www.w3.org/WAI/WCAG22/Techniques/general/G201
(defn new-tab
  "The link `a`, as Hiccup, made to open in a new tab when it goes to the
  web, and marked with the `icon`, or else new-tab-icon."
  ([a]
   (new-tab a new-tab-icon))
  ([[_ attrs :as a] icon]
   (if (url/web-url? (:href attrs))
     (marked (assoc-in a [1 :target] "_blank") :new-tab icon)
     a)))

;; Opquast, rule 144: a link that opens other software says so, as one to
;; an email address opens the email app. JAWS says that a link is one to
;; an email address, but NVDA and VoiceOver on iOS don't, so it has an
;; envelope and a note, as a link to the web has its arrow.
;; https://checklists.opquast.com/en/digital-quality/144
;; https://lists.w3.org/Archives/Public/w3c-wai-ig/2021JulSep/0089.html
(defn email
  "The link `a`, as Hiccup, marked with an envelope when it goes to an
  email address."
  [[_ attrs :as a]]
  (if (some-> (:href attrs) str/lower-case (str/starts-with? "mailto:"))
    (marked a :email icons/envelope-simple)
    a))

(defn leaving
  "The link `a` of show notes, as Hiccup, made to say where it leaves the
  app to, as new-tab and email do."
  [a]
  (-> a new-tab email))
