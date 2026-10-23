(ns dk.simongray.elektrolyt.links-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.links :as links]
            [dk.simongray.elektrolyt.ui.controls :as controls]
            [dk.simongray.elektrolyt.ui.icons :as icons]))

(deftest links-that-leave-the-app
  (is (= [:a {:href "https://example.org/" :target "_blank" :aria-describedby "new-tab-note"}
          [:span "x"]
          (controls/in-text icons/arrow-square-out "Opens in a new tab")]
         (links/leaving [:a {:href "https://example.org/"} "x"]))
      "in a new tab, which an icon after the text shows, and the note on the page says")
  (is (= [:a {:href "MAILTO:a@example.org" :aria-describedby "email-note"}
          [:span "a@example.org"]
          (controls/in-text icons/envelope-simple "Opens your email app")]
         (links/leaving [:a {:href "MAILTO:a@example.org"} "a@example.org"]))
      "or in the email app, which an envelope shows")
  (is (= [:a {:href "/shows/g"} "x"] (links/leaving [:a {:href "/shows/g"} "x"]))
      "but not those of the app"))
