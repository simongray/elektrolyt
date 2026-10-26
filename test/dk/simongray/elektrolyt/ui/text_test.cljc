(ns dk.simongray.elektrolyt.ui.text-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.ui.text :as text]))

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

(deftest the-lang-of-a-feeds-text
  (is (= ["da" "en-US" "en-GB" "de-CH"] (map text/lang ["da" "en-us" "EN-gb" "de_CH"]))
      "the tag in its canonical case, with a hyphen where some feeds write an underscore")
  (is (every? nil? (map text/lang [nil "" "English" "en--US"]))
      "none for a feed that names no language, or names it other than by its tag"))

(deftest sizes
  (is (= ["850 kB" "86 MB" "1.5 GB"] (map text/size-text [850000 86000000 1500000000])))
  (is (= ["1 MB" "1 GB"] (map text/size-text [999600 999700000])) "never 1,000 of a unit")
  (is (= "12.3 MB" (text/megabytes 12345678)))
  (is (= "0.0 MB" (text/megabytes 0))))

#?(:cljs
   (deftest one-formatter-of-each-kind-for-each-language
     (let [british (text/list-formatter)]
       (is (identical? british (text/list-formatter)) "made once")
       (with-redefs [text/language (constantly "da")]
         (is (not (identical? british (text/list-formatter))) "and again in another language")
         (is (= "fyyd, Apple Podcasts og gpodder.net"
                (text/listing ["fyyd" "Apple Podcasts" "gpodder.net"])))))))
