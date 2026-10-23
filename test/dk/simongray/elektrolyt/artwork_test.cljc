(ns dk.simongray.elektrolyt.artwork-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.artwork :as artwork]))

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
