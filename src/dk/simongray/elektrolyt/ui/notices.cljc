(ns dk.simongray.elektrolyt.ui.notices
  "Notices of one line: something that went wrong or needs the listener,
  and what they can do about it.

  A notice is only a view: whether it shows, and what dismissing it does,
  belong to the caller's state. A notice appears along with its words, so
  it's no live region, which a screen reader announces only when its
  content changes after it's in the page. Whatever puts the message in the
  state announces it through the page's announcer instead.
  https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Guides/Live_regions"
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
  - :icon, warning-circle by default
  - :dismiss, the actions of a Dismiss button, e.g. ones that take the
    message out of the state, or nil for none
  - :refocus, where the focus goes as the notice goes with its button, a
    selector, main h1 by default"
  [{:keys [label icon dismiss refocus]
    :or   {icon icons/warning-circle refocus "main h1"}
    :as   opts}
   text actions]
  (conj (if label [:section.notice {:aria-label label}] [:div.notice])
        [:p [controls/icon {:icon icon}] text]
        actions
        (when dismiss
          [controls/icon-button {:icon         icons/x
                                 :class        "notice-dismiss"
                                 :data-refocus refocus
                                 :on           {:click dismiss}}
           "Dismiss"])))
