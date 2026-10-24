(ns dk.simongray.elektrolyt.player.audio-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as queue]
            [dk.simongray.podcast-app :as-alias app]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.library.operations :as operations]
            [dk.simongray.elektrolyt.library.picks-test :as picks-test]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.player-test :as player-test]
            [dk.simongray.elektrolyt.player.audio :as audio]
            [dk.simongray.elektrolyt.runtime :as runtime]))

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
                app-library/save!           (constantly nil)
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

(deftest a-press-in-the-outro
  (let [one ["feed" "one"]
        two ["feed" "two"]]
    (testing "stops at the step that ends the episode"
      (let [ran (atom [])]
        (with-redefs [audio/step! (fn [_ [kind :as event]]
                                    (swap! ran conj event)
                                    (case kind
                                      :progress [[:stop one] [:played one] [:play two]]
                                      []))]
          (audio/steps! (atom {}) [[:progress one 3585 nil] [:skip one 0]])
          (is (= [[:progress one 3585 nil]] @ran) "so the skip meant for it doesn't run"))))
    (testing "and seeks only the player's episode"
      (let [el    #js {:currentTime 0}
            store (atom {:player {:id two}})]
        (with-redefs [audio/element (constantly el)]
          (audio/apply-effects! store [[:seek one 3585]])
          (is (zero? (.-currentTime el)) "not the next one, which a step before it loaded")
          (audio/apply-effects! store [[:seek two 30]])
          (is (= 30 (.-currentTime el))))))))

(deftest the-chapters-read-of-the-files
  (let [id    player-test/id
        doc   (get-in player-test/episodes [id :result :chapters :document])
        store (atom (player-test/state {:media {:paused false :current-time 30}}))]
    (with-redefs [audio/element (constantly #js {:currentTime 30 :paused false})]
      (testing "on the lock screen"
        (let [actions (atom nil)]
          (with-redefs [runtime/lock-screen! (fn [_ handlers] (reset! actions (set (keys handlers))))]
            (audio/lock-screen! store)
            (is (contains? @actions :nexttrack) "whose next button goes to the next chapter"))))
      (testing "at a press of the next button, e.g. on headphones"
        (let [events (atom [])]
          (with-redefs [audio/step!  (fn [_ event] (swap! events conj event) [])
                        audio/moved! (constantly nil)]
            (audio/press! store {:action :nexttrack})
            (is (= [[:progress id 30 nil] [:chapter id :next doc]] @events))))))))

(deftest an-episode-that-the-library-doesnt-have
  (let [lib   (library/add-episode (library/make "test") url feed "one")
        one   [(first (keys (:feeds lib))) "one"]
        gone  ["elsewhere" "gone"]
        store (atom {:library lib :player {:id one}})]
    (audio/read-library-files! store gone)
    (is (nil? (get-in @store [:episodes gone])) "has no files to read, so no read waits")
    (with-redefs [audio/element (constantly nil)]
      (is (nil? (audio/load! store gone nil)) "and doesn't load")
      (audio/follow! store {:id gone :tab "there" :paused true :current-time 100})
      (is (= one (get-in @store [:player :id]))
          "even as another tab stops it, so the player keeps its own"))))

(deftest playing-another-episode-while-one-plays
  (let [lib       (-> (library/make "test")
                      (library/add-episode url two-feed "one")
                      (library/add-episode url two-feed "two"))
        feed-id   (first (keys (:feeds lib)))
        [one two] (map #(vector feed-id %) ["one" "two"])
        store     (atom {:library lib :player {:id one}})
        reports   (atom [])]
    (with-redefs [audio/element             (constantly nil)
                  audio/read-library-files! (constantly nil)
                  audio/report!             (fn [s media]
                                              (swap! reports conj [(get-in @s [:player :id]) media]))]
      (with-redefs [audio/playing-element (constantly #js {:currentTime 754.2 :duration 3600})]
        (audio/load! store two nil))
      (is (= [[one {:current-time 754.2 :duration 3600}]] @reports)
          "reports where the one that played got to")
      (is (= two (get-in @store [:player :id])) "before it loads the other")
      (audio/load! store one nil)
      (is (= 1 (count @reports)) "but not where a paused element is, which may be stale"))))

(deftest the-keys-of-the-player
  (let [posts   (atom [])
        here    {:id [:feed "here"] :media {:paused true :current-time 30}}
        press   (fn [player action]
                  ((get (audio/key-handlers (atom {:player player})) action) {:action action}))]
    (with-redefs [audio/post!   #(swap! posts conj %)
                  audio/element (constantly nil)]
      (let [there (assoc here :elsewhere player-test/playing-there :now 12000)]
        (press there :play)
        (press there :seekforward)
        (is (= [{:type :command :command [:pause]}
                {:type :command :command [:press {:action :seekforward}]}]
               @posts)
            "act on the playback in another tab, as the player's buttons do"))
      (reset! posts [])
      (press here :play)
      (press here :seekforward)
      (is (empty? @posts) "or else on this tab's own"))))

(deftest a-link-past-the-end
  (let [store (atom (player-test/state {:media {:paused true :current-time 75}}))
        cued  (atom [])]
    (with-redefs [audio/cue! (fn [_ _ t _] (swap! cued conj t) nil)]
      (audio/play-id! store player-test/id 754)
      (audio/play-id! store player-test/id 3600)
      (is (= [754 nil] @cued)
          "starts where the listener left it, since the end would mark it played"))))
