(ns dk.simongray.elektrolyt.ui.artwork-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]))

(deftest the-img-of-artwork
  (testing "artwork without sizes, e.g. of 3000 pixels, takes the server's thumbnails"
    (let [[_ attrs] (artwork/img {:artwork "https://example.org/a.jpg"} 48 {:loading "lazy"})]
      (is (= {:src      "https://example.org/a.jpg"
              :srcset   (str "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=64 64w, "
                             "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=128 128w, "
                             "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=256 256w, "
                             "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=512 512w")
              :sizes    "48px"
              :alt      ""
              :width    48
              :height   48
              :decoding "async"
              :loading  "lazy"}
             attrs)
          "with the artwork itself as the src")))
  (testing "the sizes that a feed names come first"
    (is (= "https://example.org/a-100.jpg 100w"
           (:srcset (second (artwork/img {:artwork       "https://example.org/a.jpg"
                                          :artwork-sizes [{:href "https://example.org/a-100.jpg" :width 100}]}
                                         48 nil))))))
  (testing "artwork in a data URL, which the server can't fetch, stays as it is"
    (is (nil? (:srcset (second (artwork/img {:artwork "data:image/png;base64,AA=="} 48 nil))))))
  (is (nil? (artwork/img {} 48 nil)) "no img without artwork"))

(deftest linked-artwork
  (is (= [:a.artwork-link {:href "/shows/g" :title "A Show" :tabindex -1 :aria-hidden "true"} [:img]]
         (artwork/linked "/shows/g" "A Show" [:img]))
      "for a pointer alone, its tooltip the title that links there too")
  (is (nil? (artwork/linked "/shows/g" "A Show" nil)) "and nothing without the img"))
