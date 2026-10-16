(ns dk.simongray.podcast-app.ui-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast-app.ui :as ui]))

(defn elements
  "The elements of the Hiccup `x` with the `tag`, at any depth."
  [tag x]
  (->> (tree-seq (some-fn vector? seq?) #(if (vector? %) (rest %) %) x)
       (filter #(and (vector? %) (= tag (first %))))))

(defn current-links
  "The labels of the links that the Hiccup `x` marks as the current page."
  [x]
  (->> (elements :a x)
       (filter #(= "page" (:aria-current (second %))))
       (map last)))

(deftest the-current-page-is-marked-once
  (is (= ["Inbox"]
         (current-links (ui/page {:route {:name :route/inbox}}))))
  (is (= ["Settings"]
         (current-links (ui/page {:route {:name :route/settings}}))))
  (is (empty? (current-links (ui/page {:route nil})))))

(deftest the-window-title-starts-with-the-page
  (is (= "Library – Podcasts"
         (ui/document-title {:route {:name :route/library}})))
  (is (= "Page not found – Podcasts"
         (ui/document-title {:route nil}))))

(deftest the-landmarks-keep-their-order
  (is (= [:a.skip-link :header :nav :main#main :section]
         (map first (rest (ui/page {:route {:name :route/queue}}))))))
