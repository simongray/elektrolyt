(ns dk.simongray.elektrolyt.ui.details-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.details :as details]
            [dk.simongray.elektrolyt.ui-test :refer [text-of]]))

(deftest licenses-by-name
  (is (= "my-podcast-license-v1"
         (text-of (details/license {:content "my-podcast-license-v1" :url "https://example.org/terms.pdf"}))))
  (is (= "custom" (details/license {:content " custom "})) "without terms on the web, as text")
  (is (nil? (details/license nil))))

(deftest details-only-with-values
  (is (nil? (details/aside [["Website" [nil]] ["Language" []]] nil)))
  (is (= [:div {:replicant/key "Categories"} [:dt "Categories"] [:dd "News"] [:dd "Technology"]]
         (first (second (nth (details/aside [["Website" [nil]] ["Categories" ["News" "Technology"]]] nil) 3))))
      "one dt for a name with several values, and a dd for each")
  (is (some? (details/aside [] [{:name "Adam"}])) "or with people alone")
  (is (nil? (details/aside [] [{:role "host"}])) "but not people without a name"))
