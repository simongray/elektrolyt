(ns dk.simongray.elektrolyt.library.saves.ui
  "The notice that tells the listener that their changes aren't saved."
  (:require [dk.simongray.elektrolyt.library.saves :as saves]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.notices :as notices]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.time :as time-ui]))

(defn status-text
  "What the page says of the save status in `state`, or nil while it says
  nothing."
  [{status :save-status}]
  (when (saves/shown? status)
    (saves/text status (time-ui/time-text (:since status)))))

;; The notice goes with its button once a write goes through, so the focus
;; moves on to the page's heading. The names say what each does among the
;; page's other buttons and links, e.g. Search's Try again of a directory.
(defn actions
  "What the listener can do about the save `status`: try again, after
  making room in Settings when the browser has none, or reload when
  another tab has a newer version."
  [{:keys [cause] :as status}]
  (let [retry [controls/button {:icon         icons/arrows-clockwise
                                :data-refocus "main h1"
                                :on           {:click [[:library/save]]}}
               "Try again" [:span.visually-hidden " to save your changes"]]]
    (case cause
      :full         (list [:a {:href (routes/href :route/settings nil nil "this-browser")}
                           "Make room" [:span.visually-hidden " in Settings"]]
                          retry)
      :newer-format [controls/button {:icon icons/arrows-clockwise :on {:click [[:page/reload]]}}
                     "Reload" [:span.visually-hidden " the page"]]
      retry)))

(defn notice
  "The notice in `state` above the page's heading while the changes
  aren't saved, or nil."
  [state]
  (when-let [text (status-text state)]
    (notices/line {:label "Not saved"} text (actions (:save-status state)))))
