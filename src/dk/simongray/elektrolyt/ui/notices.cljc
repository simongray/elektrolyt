(ns dk.simongray.elektrolyt.ui.notices
  "Notices of one line: something that went wrong or needs the listener,
  and what they can do about it."
  (:require [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]))

;; GOV.UK Design System, notification banner: a region that a screen
;; reader lists by its name, for a notice about the whole page. The icon
;; only marks it, since the words say it all.
;; https://design-system.service.gov.uk/components/notification-banner/
(defn line
  "A notice of one line: its `text` after an icon, then the `actions`,
  e.g. buttons and links, in a row that wraps on a phone. The `opts` are:

  - :label, the name of the region of the page that it is, for a notice
    about the whole page, or nil for one among other content
  - :icon, warning-circle by default"
  [{:keys [label icon] :or {icon icons/warning-circle} :as opts} text actions]
  (conj (if label [:section.notice {:aria-label label}] [:div.notice])
        [:p [controls/icon {:icon icon}] text]
        actions))
