(ns dk.simongray.elektrolyt.ui.links
  "Links that leave the app, to the web or to an email address, marked
  with an icon and a note that say where they go. A link to the web opens
  in a new tab, so that following it doesn't stop what plays."
  (:require [clojure.string :as str]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.wary-fetch.url :as url]))

(def notes
  "The notes on every page that say where a link goes, by kind, each with
  its id and its text, which is also the tooltip of the link's icon."
  {:new-tab {:id "new-tab-note" :text "Opens in a new tab"}
   :email   {:id "email-note" :text "Opens your email app"}})

(def new-tab-icon
  "The icon after the text of a link that opens in a new tab, unless the
  link has one of its own, e.g. a feed's."
  icons/arrow-square-out)

(defn marked
  "The link `a`, as Hiccup, with the `icon` after its text and the note of
  its `kind` as its description. The text gets a span of its own, so that
  the icon stays on the line of its last word."
  [[tag attrs & children] kind icon]
  (let [{:keys [id text]} (notes kind)]
    [tag (assoc attrs :aria-describedby id)
     (into [:span] children)
     (controls/in-text icon text)]))

;; WCAG 2.2, technique G201, example 2: a visible warning that a link opens
;; a new window, as an icon after the text and a note as its description,
;; so that the text of the link and of the show notes around it stays as it
;; was. Browsers open a target=_blank link without an opener, so it needs
;; no rel=noopener.
;; https://www.w3.org/WAI/WCAG22/Techniques/general/G201
(defn new-tab
  "The link `a`, made to open in a new tab when it goes to the web, and
  marked with the `icon`, by default the new-tab one."
  ([a]
   (new-tab a new-tab-icon))
  ([[_ attrs :as a] icon]
   (if (url/web-url? (:href attrs))
     (marked (assoc-in a [1 :target] "_blank") :new-tab icon)
     a)))

;; Opquast, rule 144: a link that opens other software says so, as one to
;; an email address opens the email app. JAWS announces a link to an email
;; address as one, but NVDA and VoiceOver on iOS don't, so it gets an
;; envelope and a note.
;; https://checklists.opquast.com/en/digital-quality/144
;; https://lists.w3.org/Archives/Public/w3c-wai-ig/2021JulSep/0089.html
(defn email
  "The link `a`, marked with an envelope when it goes to an email address."
  [[_ attrs :as a]]
  (if (some-> (:href attrs) str/lower-case (str/starts-with? "mailto:"))
    (marked a :email icons/envelope-simple)
    a))

(defn leaving
  "The link `a` from show notes, marked with where it leaves the app to, if
  it does."
  [a]
  (-> a new-tab email))
