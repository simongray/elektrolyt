(ns dk.simongray.podcast-app.colors-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast-app.colors :as colors]
            [dk.simongray.podcast-app.show :as show]))

(def playing
  "The playback of an episode whose artwork comes in two sizes."
  {:id            ["f" "e"]
   :artwork       "https://example.org/a.jpg"
   :artwork-sizes [{:href "https://example.org/a-600.jpg" :width 600}
                   {:href "https://example.org/a-100.jpg" :width 100}]})

(def state
  "The Queue page while the episode plays."
  {:route  {:name :route/queue}
   :player {:id (:id playing) :playback playing}})

(deftest the-image-to-work-the-colours-out-from
  (is (= "https://example.org/a-100.jpg" (colors/source playing)) "the smallest")
  (is (= "https://example.org/b.jpg" (colors/source {:artwork "https://example.org/b.jpg"}))))

(deftest the-artworks-that-a-page-wants
  (is (= [{:artwork (:artwork playing) :artwork-sizes (:artwork-sizes playing)}]
         (colors/wanted state))
      "the artwork of what plays, for the bars")
  (testing "once only"
    (is (empty? (colors/wanted (assoc-in state [:colors (:artwork playing)] {:status :loading}))))
    (is (empty? (colors/wanted (assoc-in state [:colors (:artwork playing)] {:seed nil})))))
  (is (empty? (colors/wanted {:route {:name :route/queue}})) "and none while nothing plays"))

(deftest the-seed-of-an-artwork
  (let [colored (assoc-in state [:colors (:artwork playing)] {:seed "#9e2a2b"})]
    (is (= "#9e2a2b" (colors/seed colored playing)))
    (is (= {:style {:--seed "#9e2a2b"}} (colors/attrs colored playing)))
    (is (= {} (colors/attrs state playing)) "none while it's unknown")
    (is (= {} (colors/attrs colored nil)) "or for no artwork")))

(deftest the-show-of-a-page
  (is (nil? (colors/page-show state)) "none on the Queue page"))

(deftest what-a-page-takes-its-colours-from
  (let [show {:artwork "https://example.org/show.jpg"}
        page (assoc state :route {:name :route/show :path-params {:guid "g"}})]
    (with-redefs [show/page (constantly show)]
      (is (= playing (colors/focus page)) "what plays, until the show's colours are known")
      (is (= show (colors/focus (assoc-in page [:colors (:artwork show)] {:seed "#24476b"})))
          "and then the show that the page is about"))
    (is (= playing (colors/focus state)) "and what plays on a page about no show")))
