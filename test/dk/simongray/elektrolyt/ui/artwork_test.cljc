(ns dk.simongray.elektrolyt.ui.artwork-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.elektrolyt.ui.artwork :as artwork]))

(deftest the-thumbnails-of-artwork
  (is (= "/artwork?url=https%3A%2F%2Fexample.org%2Fa%20b.jpg%3Fv%3D1&w=64"
         (artwork/thumbnail-url "/artwork" "https://example.org/a b.jpg?v=1" 64)))
  (is (= [{:href "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=64" :width 64}
          {:href "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=128" :width 128}
          {:href "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=256" :width 256}
          {:href "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=512" :width 512}]
         (artwork/thumbnails "https://example.org/a.jpg")))
  (is (nil? (artwork/thumbnails "data:image/png;base64,iVBORw0KGgo="))
      "none for artwork in a data URL, which no server fetches")
  (is (nil? (artwork/thumbnails nil))))

(deftest the-sizes-of-artwork
  (let [named [{:href "https://example.org/a-300.jpg" :width 300}]]
    (is (= named (artwork/sizes {:artwork "https://example.org/a.jpg" :artwork-sizes named}))
        "those that the feed names")
    (is (= (artwork/thumbnails "https://example.org/a.jpg")
           (artwork/sizes {:artwork "https://example.org/a.jpg" :artwork-sizes []}))
        "or else the server's thumbnails")))

(deftest the-smallest-artwork
  (is (= "/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=64"
         (artwork/smallest {:artwork "https://example.org/a.jpg"} 32))
      "the smallest of the server's thumbnails that is wide enough")
  (is (= "https://example.org/a-200.jpg"
         (artwork/smallest {:artwork       "https://example.org/a.jpg"
                            :artwork-sizes [{:href "https://example.org/a-600.jpg" :width 600}
                                            {:href "https://example.org/a-100.jpg" :width 100}
                                            {:href "https://example.org/a-200.jpg" :width 200}]}
                           150))
      "or of the sizes that the feed names")
  (is (= "data:image/png;base64,AA==" (artwork/smallest {:artwork "data:image/png;base64,AA=="} 32))
      "or else the artwork itself, e.g. a data URL, which the server can't fetch")
  (is (nil? (artwork/smallest {} 32)) "and nothing without artwork"))

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
  (testing "a large img over the small copy that a row loaded, in the colour behind it"
    (is (= {:background-color "#123456"
            :background-image (str "image-set(url(\"/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=64\") 1x, "
                                   "url(\"/artwork?url=https%3A%2F%2Fexample.org%2Fa.jpg&w=128\") 2x)")
            :background-size  "cover"}
           (:style (second (artwork/img {:artwork "https://example.org/a.jpg"} 256
                                        {:under true :style {:background-color "#123456"}})))))
    (is (not (contains? (second (artwork/img {:artwork "https://example.org/a.jpg"} 256 {:under true}))
                        :under))
        "with no attribute of that name")
    (is (nil? (:style (second (artwork/img {:artwork "data:image/png;base64,AA=="} 256 {:under true}))))
        "and none where there are no small sizes, rather than the large artwork twice"))
  (is (nil? (artwork/img {} 48 nil)) "no img without artwork"))

(deftest linked-artwork
  (is (= [:a.artwork-link {:href "/shows/g" :title "A Show" :tabindex -1 :aria-hidden "true"} [:img]]
         (artwork/linked "/shows/g" "A Show" [:img]))
      "for a pointer alone, its tooltip the title that links there too")
  (is (nil? (artwork/linked "/shows/g" "A Show" nil)) "and nothing without the img"))
