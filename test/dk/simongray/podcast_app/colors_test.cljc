(ns dk.simongray.podcast-app.colors-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast-app.colors :as colors]
            [dk.simongray.podcast-app.inbox-test :as inbox-test]
            [dk.simongray.podcast-app.settings :as settings]
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
  (is (empty? (colors/wanted {:route {:name :route/queue}})) "and none while nothing plays")
  (with-redefs [show/page (constantly (select-keys playing [:artwork]))]
    (is (= [(:artwork playing)]
           (map :artwork (colors/wanted (assoc state :route {:name :route/show :path-params {:guid "g"}}))))
        "each artwork once, though what plays and the page's show share it")))

(def chaptered
  "The state of the Queue page while the episode plays at 75 seconds, in
  a chapter with an image of its own."
  (-> state
      (assoc-in [:player :playback :chapters :document :chapters]
                [{:start-time 0 :title "A"} {:start-time 60 :title "B" :img "https://example.org/b.png"}])
      (assoc-in [:player :media :current-time] 75)))

(deftest the-image-of-a-chapter-colours-the-bars
  (is (= {:artwork "https://example.org/b.png"} (colors/playing chaptered)))
  (is (= "https://example.org/a.jpg"
         (:artwork (colors/playing (assoc-in chaptered [:player :media :current-time] 30))))
      "and the artwork in a chapter without one"))

(deftest the-seed-of-an-artwork
  (let [colored (assoc-in state [:colors (:artwork playing)] {:seed "#9e2a2b"})]
    (is (= "#9e2a2b" (colors/seed colored playing)))
    (is (= {:style {:--bar-seed "#9e2a2b"}} (colors/attrs colored))
        "for the bars alone, on a page about no show, whose paper is the app's")
    (is (= {} (colors/attrs state)) "none while it's unknown")
    (is (= {} (colors/attrs {:route {:name :route/queue}})) "or while nothing plays")))

(deftest the-player-takes-the-colours-of-what-plays-alone
  (let [show  {:artwork "https://example.org/show.jpg"}
        page  (-> state
                  (assoc :route {:name :route/show :path-params {:guid "g"}})
                  (assoc :colors {(:artwork show)    {:seed "#24476b"}
                                  (:artwork playing) {:seed "#9e2a2b"}}))]
    (with-redefs [show/page (constantly show)]
      (is (= {:style {:--seed "#24476b" :--bar-seed "#9e2a2b"}} (colors/attrs page))
          "while the paper takes those of the show that the page is about"))))

(deftest an-artwork-waits-in-its-dominant-colour
  (is (= {:style {:background-color "#0a0a0a"}}
         (colors/placeholder (assoc-in state [:colors (:artwork playing)] {:seed nil :dominant "#0a0a0a"})
                             playing)))
  (is (nil? (colors/placeholder state playing)) "once it's known"))

(deftest the-show-of-a-page
  (is (nil? (colors/page-show state)) "none on the Queue page"))

(deftest what-a-page-takes-its-colours-from
  (let [show {:artwork "https://example.org/show.jpg"}
        page (assoc state :route {:name :route/show :path-params {:guid "g"}})]
    (with-redefs [show/page (constantly show)]
      (is (nil? (colors/focus page)) "the app's own, until the show's colours are known")
      (is (= show (colors/focus (assoc-in page [:colors (:artwork show)] {:seed "#24476b"})))
          "and then the show that the page is about")
      (is (nil? (colors/focus (-> page
                                  (assoc-in [:colors (:artwork show)] {:seed "#24476b"})
                                  (assoc :library inbox-test/lib)
                                  (settings/preferred {:artwork-colors? false}))))
          "or the app's own, when the listener prefers them"))
    (is (nil? (colors/focus state)) "and never what plays, which colours the player alone")))
