(ns dk.simongray.podcast-app.text-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.text :as text]))

(deftest counts-in-words
  (is (= ["0 episodes" "1 episode" "3 episodes"] (map #(text/counted % "episode") [0 1 3])))
  (is (= "new episodes" (text/plural 2 "new episode"))))

(deftest lists-in-british-english
  (is (= "Apple Podcasts, fyyd and gpodder.net" (text/listing ["Apple Podcasts" "fyyd" "gpodder.net"]))
      "with no comma before the and")
  (is (= "fyyd" (text/listing ["fyyd"]))))

(deftest lists-in-parts
  (is (= [[:element "relays"] [:literal ", "] [:element "zaps"] [:literal " and "] [:element "Clojure"]]
         (text/listing-parts ["relays" "zaps" "Clojure"]))
      "each string apart from the words between them")
  (is (= [[:element "relays"]] (text/listing-parts ["relays"]))))

(deftest sizes
  (is (= ["850 kB" "86 MB" "1.5 GB"] (map text/size-text [850000 86000000 1500000000])))
  (is (= "12.3 MB" (text/megabytes 12345678)))
  (is (= "0.0 MB" (text/megabytes 0))))
