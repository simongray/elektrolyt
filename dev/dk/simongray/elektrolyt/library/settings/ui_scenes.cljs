(ns dk.simongray.elektrolyt.library.settings.ui-scenes
  "Scenes of the Settings page: the defaults, a browser that may clear the
  library, and the status line after an action."
  (:require [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.scenes :refer [lib]]
            [dk.simongray.elektrolyt.ui :as ui]
            [portfolio.replicant :refer [defscene]]))

(defn settings-page
  "The state of the Settings page, with the `more` state merged in."
  [more]
  (merge {:route   {:name :route/settings}
          :library lib}
         more))

(defscene the-defaults
  "Every setting as it is until the listener changes it, in a browser that
  keeps the library."
  (ui/page (settings-page {:storage {:supported? true :persisted? true :usage 12345678}})))

(defscene a-browser-that-may-clear-it
  "A browser that may clear the library, with the button that asks it to
  keep the library."
  (ui/page (settings-page {:storage {:supported? true :persisted? false :usage 2345678}})))

(defscene after-clearing-the-searches
  "The status line once the cache is cleared."
  (ui/page (settings/cleared
            (settings-page {:storage {:supported? true :persisted? true :usage 12345678}})
            nil)))
