(ns dk.simongray.podcast-app.artwork-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.artwork :as artwork]))

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
