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
  (is (= [:a.skip-link :nav :main#main :section#player]
         (map first (drop 2 (ui/page {:route {:name :route/queue}}))))
      "after the shell's attributes"))

(deftest the-colours-of-the-artwork
  (let [playing "https://example.org/playing.jpg"
        state   {:route  {:name :route/queue}
                 :player {:id ["f" "e"] :playback {:id ["f" "e"] :artwork playing}}}]
    (is (= {} (second (ui/page state))) "the app's own colours, while the artwork's are unknown")
    (is (= {:style {:--seed "#9e2a2b"}}
           (second (ui/page (assoc state :colors {playing {:seed "#9e2a2b"}}))))
        "and the bars in the seed of the artwork of what plays, once it's known")
    (is (= {} (second (ui/page (assoc state :colors {playing {:seed nil}}))))
        "or the app's own, for artwork without a hue")))
