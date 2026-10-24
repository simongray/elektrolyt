(ns dk.simongray.elektrolyt.library.saves.ui
  "The line that tells the listener that their changes aren't saved."
  (:require [dk.simongray.elektrolyt.library.saves :as saves]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn status-text
  "What the page says of the save status in `state`, or nil while it says
  nothing."
  [{status :save-status}]
  (when (saves/shown? status)
    (saves/text status (time-ui/time-text (:since status)))))

;; The line goes with its button once a write goes through, so the focus
;; moves on to the page's heading
(defn actions
  "What the listener can do about the save `status`: try again, after
  making room in Settings when the browser has none, or reload when
  another tab has a newer version."
  [{:keys [cause] :as status}]
  (let [retry [controls/button {:icon         icons/arrows-clockwise
                                :data-refocus "main h1"
                                :on           {:click [[:library/save]]}}
               "Try again"]]
    (case cause
      :full         (list [:a {:href (routes/href :route/settings nil nil "this-browser")}
                           "Make room"]
                          retry)
      :newer-format [controls/button {:icon icons/arrows-clockwise :on {:click [[:page/reload]]}}
                     "Reload"]
      retry)))

;; GOV.UK Design System, notification banner: before the page's heading,
;; a region of its own, one at most, and with nothing to dismiss it, since
;; it goes once the changes are saved. Here it's a single line.
;; https://design-system.service.gov.uk/components/notification-banner/
(defn banner
  "The line in `state` while the page tells that the changes aren't saved,
  or nil."
  [state]
  (when-let [text (status-text state)]
    [:section.save-banner {:aria-label "Not saved"}
     [:p [controls/icon {:icon icons/warning-circle}] text]
     (actions (:save-status state))]))
