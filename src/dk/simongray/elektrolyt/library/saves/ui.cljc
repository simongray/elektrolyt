(ns dk.simongray.elektrolyt.library.saves.ui
  "The banner that tells the listener that their changes aren't saved."
  (:require [dk.simongray.elektrolyt.library.saves :as saves]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn status-text
  "What the page says of the save status in `state`, or nil while it says
  nothing."
  [{status :save-status}]
  (when (saves/shown? status)
    (saves/text status (time-ui/time-text (:since status)))))

;; The banner goes with its buttons once a write goes through, so the focus
;; moves on to the page's heading
(defn actions
  "The buttons for the save `status`: Try again now, the fix for its
  cause, or both."
  [{:keys [cause] :as status}]
  (let [retry [:button {:type "button" :data-refocus "main h1" :on {:click [[:library/save]]}}
               "Try again now"]]
    (case cause
      :full         (list [:button {:type "button" :data-refocus "main h1" :on {:click [[:cache/clear]]}}
                           "Clear the searches and shows"]
                          retry)
      :newer-format [:button {:type "button" :on {:click [[:page/reload]]}} "Reload"]
      retry)))

;; GOV.UK Design System, notification banner: before the page's heading,
;; a region labelled by its own heading, one at most, and with nothing to
;; dismiss it, since it goes once the changes are saved
;; https://design-system.service.gov.uk/components/notification-banner/
(defn banner
  "The banner in `state` while the page tells that the changes aren't
  saved, or nil."
  [state]
  (when-let [text (status-text state)]
    [:section.save-banner {:aria-labelledby "save-banner"}
     [:h2#save-banner "Not saved"]
     [:p text]
     [:div.save-banner-actions (actions (:save-status state))]]))
