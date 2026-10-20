(ns dk.simongray.podcast-app.audio-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.podcast-app.audio :as audio]
            [dk.simongray.podcast-app.picks-test :as picks-test]
            [dk.simongray.podcast-app.player :as player]
            [dk.simongray.podcast-app.settings :as settings]))

(def url "https://example.org/feed.xml")

(def feed
  {:rss/title "A Show"
   :rss/items [{:rss/title     "One"
                :rss/guid      {:content "one"}
                :rss/enclosure {:url "https://example.org/1.mp3" :type "audio/mpeg"}}]})

(def two-feed
  "A feed of two episodes."
  (update feed :rss/items conj {:rss/title     "Two"
                                :rss/guid      {:content "two"}
                                :rss/enclosure {:url "https://example.org/2.mp3" :type "audio/mpeg"}}))

(deftest the-episode-played-last-through-a-reload
  (let [lib     (-> (library/make "test")
                    (library/add-episode url two-feed "one")
                    (library/add-episode url two-feed "two"))
        feed-id (first (keys (:feeds lib)))
        one     [feed-id "one"]
        two     [feed-id "two"]
        played  (dissoc (library/step lib [:play one]) :effects)
        current #(some-> % :library :profile queue/current)
        now     (js/Date.now)]
    (is (= one (current {:library played})))
    (is (= two (current (audio/restored {:library played} {:id two :at now} nil nil)))
        "the episode that this browser played last, which no save had")
    (is (= one (current (audio/restored {:library played} nil nil nil))) "nothing kept")
    (is (= two (get-in (audio/restored {:library played} {:id two :at now} nil nil) [:player :id]))
        "which the player has")))

(deftest the-player-as-the-page-loads
  (let [lib (library/add-episode (library/make "test") url feed "one")
        id  [(first (keys (:feeds lib))) "one"]
        now (js/Date.now)
        lib (library/update-profile lib queue/enqueue id)]
    (is (= 636 (:position (player/entry (:library (audio/restored {:library lib} nil
                                                                  {:id id :position 636 :duration 3600 :at now}
                                                                  nil))
                                        id)))
        "the library told of the position that this browser kept")
    (is (= 0.4 (get-in (audio/restored {:library lib} nil nil 0.4) [:player :volume]))
        "at the volume that this browser keeps")
    (is (= 1 (get-in (audio/restored {:library lib} nil nil nil) [:player :volume]))
        "or all of it")))

(defn playing
  "A store of the player with the episode `id` of the picks' library,
  current and `position` seconds into its 1800, with the listener's
  preferences `prefs`."
  [id position prefs]
  (atom (-> {:library (-> picks-test/lib
                          (library/step [:play id])
                          (dissoc :effects)
                          (library/update-profile profile/progress id position 1800))
             :player  {:id id}}
            (settings/preferred prefs))))

(deftest autoplay-after-an-episode
  ;; Node has no audio element, no IndexedDB and no network
  (with-redefs [audio/element               (constantly nil)
                audio/save!                 (constantly nil)
                audio/read-library-files!   (constantly nil)]
    (let [grinders picks-test/grinders
          pick     (picks-test/id "Brew Lab" 0)
          now      #(some-> % :library :profile queue/current)]
      (testing "as the episode ends"
        (let [store (playing grinders 1790 {:autoplay? true})]
          (audio/ended! store)
          (is (= pick (get-in @store [:player :id])) "the first pick plays")
          (is (= pick (now @store)) "current, at the front of the queue"))
        (let [store (playing grinders 1790 {})]
          (audio/ended! store)
          (is (= grinders (get-in @store [:player :id])) "but only when the listener chose Autoplay")))
      (testing "as it ends in the outro that the listener skips"
        (let [outro #(update % :library library/update-profile profile/configure
                             (first grinders) {:skip-outro 60})
              store (atom (outro @(playing grinders 1700 {:autoplay? true})))]
          (audio/report! store {:current-time 1750 :duration 1800})
          (is (= pick (get-in @store [:player :id])))
          (is (= pick (now @store))))))))
