(ns dk.simongray.podcast-app.links-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.links :as links]))

(deftest links-that-leave-the-app
  (is (= [:a {:href "https://example.org/" :target "_blank" :aria-describedby "new-tab-note"} "x"]
         (links/new-tab [:a {:href "https://example.org/"} "x"]))
      "in a new tab, which the note on the page says")
  (is (= [:a {:href "/shows/g"} "x"] (links/new-tab [:a {:href "/shows/g"} "x"]))
      "but not those of the app")
  (is (= [:a {:href "mailto:a@example.org"} "a"] (links/new-tab [:a {:href "mailto:a@example.org"} "a"]))
      "nor an email address"))
