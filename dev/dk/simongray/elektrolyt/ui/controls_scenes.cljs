(ns dk.simongray.elektrolyt.ui.controls-scenes
  "Scenes of the buttons with icons on the paper, in the app's own colours
  and in an artwork's. The player's scenes show them on its bar."
  (:require [dk.simongray.elektrolyt.player.ui :as player-ui]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [portfolio.replicant :refer [defscene]]))

(def row
  "The style of a row of controls in the scenes."
  {:display     "flex"
   :flex-wrap   "wrap"
   :align-items "center"
   :gap         "0.5em"
   :margin      "0 0 1rem"})

(def buttons
  "The buttons, with words and with an icon alone."
  (list
   [:div {:style row}
    [controls/button {:icon icons/play :class "primary"} "Play"]
    [controls/button {:icon icons/rows-plus-bottom} "Play last"]
    [controls/button {:icon icons/x} "Dismiss all"]
    [controls/button {:icon icons/upload-simple} "Import"]]
   [:div {:style row}
    [controls/icon-button {:icon icons/play :class "primary"} "Play"]
    [controls/icon-button {:icon icons/pause} "Pause"]
    [controls/icon-button {:icon (player-ui/skip-icon :back 15)} "Back 15 seconds"]
    [controls/icon-button {:icon (player-ui/skip-icon :forward 30)} "Forward 30 seconds"]
    [controls/icon-button {:icon icons/skip-back} "Previous chapter"]
    [controls/icon-button {:icon icons/skip-forward} "Next chapter"]
    [controls/icon-button {:icon icons/dots-three} "More"]
    [controls/icon-button {:icon icons/trash} "Remove"]]))

(defn page
  "A page with the buttons on the paper, whose shell has the `attrs`, e.g.
  the seed of an artwork."
  [attrs]
  [:div.shell attrs
   [:main#main
    [:h1 "Buttons with icons"]
    buttons]])

(defscene buttons-with-icons
  "The buttons in the app's own colours."
  (page {}))

(defscene buttons-with-icons-in-colour
  "The buttons in the colours of an artwork, on the paper of a show's
  page."
  (page {:style {:--seed "#2f6f8f"}}))
