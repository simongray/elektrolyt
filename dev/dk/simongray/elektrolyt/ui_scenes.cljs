(ns dk.simongray.elektrolyt.ui-scenes
  "Scenes of the whole page: its navigation, in each palette that the
  listener can choose, a path that no page has, and the frame of a page
  while the app starts."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.elektrolyt.library.operations :as operations]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(defscene queue
  "A page of the main navigation, which marks its link as the current page."
  (ui/page {:route {:name :route/queue :path-params {}}}))

(defscene settings
  "The settings, whose link in the navigation is the current page instead."
  (ui/page {:route {:name :route/settings :path-params {}}}))

(defn palette
  "The state of the Queue page for a listener who chose the navigation
  `palette`, e.g. :none."
  [palette]
  (operations/changed {:route   {:name :route/queue :path-params {}}
                       :library (library/make "scenes")}
                      (settings/preferred {::settings/navigation-palette palette})))

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

(defscene while-the-app-starts
  "The frame of the Library page while the library is read: the
  navigation, and the page's heading over a skeleton, which shows after a
  second."
  (ui/page (ui/starting {} "/library")))
