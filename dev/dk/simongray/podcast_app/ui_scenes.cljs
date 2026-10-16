(ns dk.simongray.podcast-app.ui-scenes
  "Scenes of the whole page: a main page, the settings, which the header
  links to, and a path that no page has."
  (:require [dk.simongray.podcast-app.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(defscene queue
  "A page of the main navigation, which marks its link as the current page."
  (ui/page {:route {:name :route/queue :path-params {}}}))

(defscene settings
  "The settings, whose link in the header is the current page instead."
  (ui/page {:route {:name :route/settings :path-params {}}}))

(defscene not-found
  "A path that no page has, which links back to the queue."
  (ui/page {:route nil}))
