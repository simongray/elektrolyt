(ns dk.simongray.elektrolyt.ui.controls-scenes
  "Scenes of the buttons with icons, on the paper and on the player's bar,
  in the app's own colours and in an artwork's."
  (:require [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [portfolio.replicant :refer [defscene]]))

;; The seconds sit in the arrow, in the font of the page, since Phosphor
;; has no skip by a number of seconds
(defn skip
  "The icon of a skip by `seconds` around the arrow of the `path`."
  [path seconds]
  [controls/icon {:icon path}
   [:text {:x           128
           :y           128
           :dy          "0.35em"
           :text-anchor "middle"
           :font-size   96
           :font-weight 700
           :fill        "currentColor"}
    (str seconds)]])

(def row
  "The style of a row of controls in the scenes."
  {:display     "flex"
   :flex-wrap   "wrap"
   :align-items "center"
   :gap         "0.5em"
   :margin      "0 0 1rem"})

(def paper
  "The buttons on the paper, with words and with an icon alone."
  (list
   [:div {:style row}
    [controls/button {:icon icons/play :class "primary"} "Play"]
    [controls/button {:icon icons/queue} "Add to queue"]
    [controls/button {:icon icons/x} "Dismiss all"]
    [controls/button {:icon icons/upload-simple} "Import"]]
   [:div {:style row}
    [controls/icon-button {:icon icons/play :class "primary"} "Play"]
    [controls/icon-button {:icon icons/pause} "Pause"]
    [controls/icon-button {:icon (skip icons/arrow-counter-clockwise 15)} "Back 15 seconds"]
    [controls/icon-button {:icon (skip icons/arrow-clockwise 30)} "Forward 30 seconds"]
    [controls/icon-button {:icon icons/skip-back} "Previous chapter"]
    [controls/icon-button {:icon icons/skip-forward} "Next chapter"]
    [controls/icon-button {:icon icons/dots-three} "More"]
    [controls/icon-button {:icon icons/trash} "Remove"]]))

;; The player's own grid would place each button in a cell of its own,
;; so the bar lays them out in a row instead
(def bar
  "The buttons on the player's bar."
  [:section#player {:aria-label "Player"
                    :style      {:display "flex"
                                 :padding "1rem var(--gutter) 0"}}
   [:div {:style row}
    [controls/icon-button {:icon icons/play :class "primary"} "Play"]
    [controls/icon-button {:icon (skip icons/arrow-counter-clockwise 15)} "Back 15 seconds"]
    [controls/icon-button {:icon (skip icons/arrow-clockwise 30)} "Forward 30 seconds"]
    [controls/icon-button {:icon icons/skip-back} "Previous chapter"]
    [controls/icon-button {:icon icons/skip-forward} "Next chapter"]
    [controls/icon-button {:icon icons/speaker-high} "Mute"]
    [controls/button {:icon icons/sliders-horizontal} "Controls"]]])

(defn page
  "A page with the buttons on the paper and on the bar, whose shell has the
  `attrs`, e.g. the seeds of an artwork."
  [attrs]
  [:div.shell attrs
   [:main#main
    [:h1 "Buttons with icons"]
    paper]
   bar])

(defscene buttons-with-icons
  "The buttons in the app's own colours."
  (page {}))

(defscene buttons-with-icons-in-colour
  "The buttons in the colours of an artwork, on the paper of a show's page
  and on the bar of the player."
  (page {:style {:--seed "#2f6f8f" :--bar-seed "#c0562a"}}))
