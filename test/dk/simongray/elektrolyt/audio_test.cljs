(ns dk.simongray.elektrolyt.audio-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.podcast-app :as-alias app]
            [dk.simongray.elektrolyt.audio :as audio]
            [dk.simongray.elektrolyt.operations :as operations]
            [dk.simongray.elektrolyt.picks-test :as picks-test]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.settings :as settings]))

(def url "https://example.org/feed.xml")

(defn restored [state volume]
  (operations/changed state (audio/restored volume)))

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

(deftest the-player-as-the-page-loads
  (let [lib     (-> (library/make "test")
                    (library/add-episode url two-feed "one")
                    (library/add-episode url two-feed "two"))
        feed-id (first (keys (:feeds lib)))
        two     [feed-id "two"]
        played  (-> (:library (library/step lib [:play two]))
                    (library/update-profile profile/progress two 636 3600))]
    (is (= two (get-in (restored {:library played} nil) [:player :id]))
        "with the episode that the queue has at its front")
    (is (= 636 (:position (player/playback (restored {:library played} nil) two)))
        "where the listener left it")
    (is (= 0.4 (get-in (restored {:library played} 0.4) [:player :volume]))
        "at the volume that this browser keeps")
    (is (= 1 (get-in (restored {:library played} nil) [:player :volume]))
        "or all of it")))

(deftest the-moments-of-a-report
  (let [reports (atom [])
        store   (atom {:library (library/make "test") :player {:id ["feed" "one"]}})
        report  (audio/reporter store)]
    (with-redefs [audio/report! (fn [_ media] (swap! reports conj (:current-time media)))]
      (report {:paused false :current-time 10.4})
      (report {:paused false :current-time 11.2})
      (is (= [10] @reports) "as it starts, but not a second later")
      (report {:paused true :current-time 12.0})
      (is (= [10 12] @reports) "at a pause")
      (swap! store assoc-in [:player :id] ["feed" "two"])
      (report {:paused false :current-time 0.5})
      (is (= [10 12 0] @reports) "and as another episode starts"))))

(defn playing
  "A store of the player with the episode `id` of the picks' library,
  current and `position` seconds into its 1800, with the listener's
  preferences `prefs`."
  [id position prefs]
  (atom (-> {:library (-> (:library (library/step picks-test/lib [:play id]))
                          (library/update-profile profile/progress id position 1800))
             :player  {:id id}}
            (operations/changed (settings/preferred prefs)))))

(deftest autoplay-after-an-episode
  ;; Node has no audio element, no IndexedDB and no network
  (with-redefs [audio/element               (constantly nil)
                audio/save!                 (constantly nil)
                audio/read-library-files!   (constantly nil)]
    (let [grinders picks-test/grinders
          pick     (picks-test/id "Brew Lab" 0)
          now      #(some-> % :library :profile queue/current)]
      (testing "as the episode ends"
        (let [store (playing grinders 1790 {::app/autoplay? true})]
          (audio/ended! store)
          (is (= pick (get-in @store [:player :id])) "the first pick plays")
          (is (= pick (now @store)) "current, at the front of the queue"))
        (let [store (playing grinders 1790 {})]
          (audio/ended! store)
          (is (= grinders (get-in @store [:player :id])) "but only when the listener chose Autoplay")))
      (testing "as it ends in the outro that the listener skips"
        (let [outro #(update % :library library/update-profile profile/configure
                             (first grinders) {:skip-outro 60})
              store (atom (outro @(playing grinders 1700 {::app/autoplay? true})))]
          (audio/report! store {:current-time 1750 :duration 1800})
          (is (= pick (get-in @store [:player :id])))
          (is (= pick (now @store))))))))
