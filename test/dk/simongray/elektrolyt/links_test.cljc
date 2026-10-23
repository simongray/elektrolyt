(ns dk.simongray.elektrolyt.links-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.links :as links]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]))

(deftest links-that-leave-the-app
  (is (= [:a {:href "https://example.org/" :target "_blank" :aria-describedby "new-tab-note"}
          [:span "x"]
          [controls/icon {:icon icons/arrow-square-out :class "in-text" :title "Opens in a new tab"}]]
         (links/new-tab [:a {:href "https://example.org/"} "x"]))
      "in a new tab, which an icon after the text shows, and the note on the page says")
  (is (= [:a {:href "/shows/g"} "x"] (links/new-tab [:a {:href "/shows/g"} "x"]))
      "but not those of the app")
  (is (= [:a {:href "mailto:a@example.org"} "a"] (links/new-tab [:a {:href "mailto:a@example.org"} "a"]))
      "nor an email address"))
