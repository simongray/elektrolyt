(ns dk.simongray.elektrolyt.ui-scenes
  "Scenes of the whole page: a main page, the settings, which the
  navigation links to, the navigation in the colours that the listener can
  choose, and a path that no page has."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast-app :as-alias app]
            [dk.simongray.elektrolyt.operations :as operations]
            [dk.simongray.elektrolyt.settings :as settings]
            [dk.simongray.elektrolyt.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(defscene queue
  "A page of the main navigation, which marks its link as the current page."
  (ui/page {:route {:name :route/queue :path-params {}}}))

(defscene settings
  "The settings, whose link in the navigation is the current page instead."
  (ui/page {:route {:name :route/settings :path-params {}}}))

(defn palette
  "The state of the Queue page of a listener who chose the navigation's
  colours `palette`, one of settings/navigation-palettes."
  [palette]
  (operations/changed {:route   {:name :route/queue :path-params {}}
                       :library (library/make "scenes")}
                      (settings/preferred {::app/navigation-palette palette})))

(defscene colours-for-colour-blindness
  "The navigation in the colours that keep its pages apart for colour
  blindness, which the listener can choose in Settings."
  (ui/page (palette :color-blind)))

(defscene no-colours
  "The navigation without a colour for each page, which the listener can
  choose in Settings."
  (ui/page (palette :none)))

(defscene not-found
  "A path that no page has, which links back to the queue."
  (ui/page {:route nil}))
