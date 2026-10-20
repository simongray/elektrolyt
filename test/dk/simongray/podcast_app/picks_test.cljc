(ns dk.simongray.podcast-app.picks-test
  (:require [clojure.test :refer [deftest is testing]]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.podcast-app :as-alias app]
            [dk.simongray.podcast-app.operations :as operations]
            [dk.simongray.podcast-app.picks :as picks]
            [dk.simongray.podcast-app.settings :as settings]))

(defn show
  "A feed titled `title` in English of the `episodes`, pairs of a title and
  notes, explicit when `explicit?`, in the RSS `categories`."
  [title explicit? categories episodes]
  (cond-> {:rss/title    title
           :rss/language "en"
           :rss/items    (vec (map-indexed (fn [i [t notes]]
                                             {:rss/title       t
                                              :rss/description notes
                                              :rss/guid        {:content (str i)}
                                              :rss/pub-date    (nth [#inst "2026-06-01" #inst "2026-06-02"] i)
                                              :rss/enclosure   {:url  (str "https://example.org/" title i ".mp3")
                                                                :type "audio/mpeg"}
                                              :itunes/duration 1800})
                                           episodes))}
    explicit?        (assoc :itunes/explicit true)
    (seq categories) (assoc :rss/categories (mapv #(hash-map :content %) categories))))

(def shows
  "Shows about coffee, three of them explicit: one about sex, one for its
  swearing, and one that marks its episode clean. And others, enough of
  them that the words about coffee are of a few shows."
  [(show "Coffee Talk" false [] [["Espresso grinders" "Burr grinders for espresso, and how fine to grind the beans."]
                                 ["Roasting at home" "Roasting espresso beans at home, for burr grinders."]])
   (show "Brew Lab" false [] [["Grinders compared" "Conical and flat burr grinders for espresso beans."]])
   (show "After Dark" true ["Sexuality"] [["Espresso grinders in bed" "Burr grinders, espresso beans and more."]])
   (show "Swear Jar" true ["Comedy"] [["Damn good espresso" "Espresso from burr grinders, and swearing."]])
   (assoc-in (show "Late Night" true [] [["Grinders for espresso at night" "Late espresso from burr grinders."]])
             [:rss/items 0 :itunes/explicit] false)
   (show "Sailing" false [] [["Knots" "Knots for a dinghy."]])
   (show "Chess" false [] [["Openings" "The Sicilian defence."]])
   (show "Bikes" false [] [["Gears" "The gears of a bicycle."]])
   (show "Gardens" false [] [["Roses" "Pruning roses in the spring."]])
   (show "History" false [] [["Rome" "The fall of the Roman republic."]])
   (show "Music" false [] [["Chords" "Minor chords on a guitar."]])
   (show "Stars" false [] [["Comets" "Comets and their tails."]])
   (show "Weather" false [] [["Fog" "Fog over the harbour."]])])

(def lib
  (reduce #(library/subscribe %1 (str "https://example.org/" (:rss/title %2) ".xml") %2)
          (library/make "test")
          shows))

(def keys-by-title
  (into {} (map (fn [[k {:keys [feed]}]] [(:rss/title feed) k])) (:feeds lib)))

(defn id [title i]
  [(keys-by-title title) (str i)])

(def grinders (id "Coffee Talk" 0))

(defn titles [picked]
  (mapv :title (:episodes picked)))

(defn preferring [state prefs]
  (operations/changed state (settings/preferred prefs)))

(deftest the-options-of-each-place
  (let [state {:library lib}]
    (is (= {:status :unplayed :queued? false :explicit? false :show-explicit? false}
           (:where (picks/opts state :episode false)))
        "unplayed, not queued, and neither explicit nor of an explicit show")
    (is (= picks/clean (:from (picks/opts state :home false)))
        "nor started from an explicit episode")
    (is (= [{:status :unplayed :queued? false} nil]
           ((juxt :where :from) (picks/opts state :episode true)))
        "unless the episodes that they start from are explicit")
    (is (true? (:same-show? (picks/opts state :queue false)))
        "the end of the queue takes the shows of the queue too")
    (is (= [{:status :unplayed :queued? false} nil]
           ((juxt :where :from) (picks/opts (preferring state {::app/explicit-picks? true}) :home false)))
        "or the listener chose explicit picks")))

(deftest explicit-episodes
  (is (picks/explicit-in? lib [grinders (id "Swear Jar" 0)]) "for swearing too")
  (is (picks/explicit-in? lib [(id "Late Night" 0)]) "and of an explicit show, though it says it's clean")
  (is (not (picks/explicit-in? lib [grinders])))
  (let [outside (show "Outside" true [] [["Swearing" "Damn."]])
        feeds   {:feeds {"outside" {:feed outside :url "https://example.org/outside.xml"}}}]
    (is (not (picks/explicit-in? lib [["outside" "0"]])))
    (is (picks/explicit-in? lib [["outside" "0"]] feeds) "and in the feed of a show outside the library")))

(deftest picks-of-a-show-outside-the-library
  (let [url       "https://example.org/outside.xml"
        clean     (show "Outside" false [] [["Burr grinders" "Burr grinders for espresso beans."]])
        marked    (assoc-in clean [:rss/items 0 :itunes/explicit] true)
        k         (guid/feed-key clean url)
        state     (fn [feed] {:library lib :shows {k {:feed feed :url url}}})
        page      {:id [k "0"] :guid k}
        explicit? (fn [picked]
                    (boolean (some #{"Espresso grinders in bed" "Damn good espresso"
                                     "Grinders for espresso at night"}
                                   (titles picked))))]
    (testing "like its episode, explicit ones only when the episode is"
      (is (seq (titles (picks/like-episode (state clean) page))))
      (is (not (explicit? (picks/like-episode (state clean) page))))
      (is (explicit? (picks/like-episode (state marked) page))))
    (testing "like the show, explicit ones only when an episode of it is"
      (is (seq (titles (picks/like-show (state clean) k))))
      (is (not (explicit? (picks/like-show (state clean) k))))
      (is (explicit? (picks/like-show (state marked) k))))
    (testing "remembered while what they change with stays the same"
      (let [s (state clean)]
        (is (identical? (picks/like-episode s page) (picks/like-episode s page)))
        (is (identical? (picks/like-show s k) (picks/like-show s k)))
        (is (explicit? (picks/like-episode (preferring s {::app/explicit-picks? true}) page))
            "but found again once the listener chose explicit picks")
        (is (explicit? (picks/like-show (preferring s {::app/explicit-picks? true}) k)))
        (is (not= (titles (picks/like-episode s page))
                  (titles (picks/like-episode s (assoc page :id grinders))))
            "or for another episode")))))

(deftest picks-like-an-episode
  (let [state {:library lib}
        like  #(titles (picks/recommended lib [%] (picks/opts state :episode (picks/explicit-in? lib [%]))))]
    (is (= ["Grinders compared"] (like grinders)) "of other shows, but not the explicit ones")
    (is (= #{"Espresso grinders in bed" "Grinders for espresso at night" "Grinders compared" "Damn good espresso"}
           (set (titles (picks/recommended lib [grinders]
                                      (picks/opts (preferring state {::app/explicit-picks? true}) :episode false)))))
        "which the listener can choose")
    (is (seq (:terms (picks/recommended lib [grinders] (picks/opts state :episode false))))
        "with the words that found them")
    (is (some #{"Damn good espresso"} (like (id "After Dark" 0)))
        "and explicit ones too, like an explicit episode")))

(deftest picks-leave-out-what-is-queued
  (let [queued (library/update-profile lib profile.queue/enqueue (id "Brew Lab" 0))]
    (is (= [] (titles (picks/recommended queued [grinders] (picks/opts {:library queued} :episode false)))))))

(deftest the-picks-of-each-place
  (testing "Home, like what the listener heard last"
    (is (= [] (titles (picks/home {:library lib}))) "nothing before the listener has heard anything")
    (is (= [] (:terms (picks/home {:library lib}))))
    ;; as the player has it, which puts what plays at the front of the queue
    (let [heard (-> lib
                    (library/step [:play grinders])
                    (library/update-profile profile/progress grinders 60))]
      (is (= ["Grinders compared"] (titles (picks/home {:library heard})))
          "but no episode of an explicit show, though it says it's clean")
      (is (some #{"Grinders for espresso at night"}
                (titles (picks/home (preferring {:library heard} {::app/explicit-picks? true}))))
          "which the listener can choose")))
  (testing "the end of the queue, like what's in it, of its shows too"
    (is (nil? (picks/after-queue {:library lib})) "none for an empty queue")
    (let [queued (library/update-profile lib profile.queue/enqueue grinders)]
      (is (= ["Grinders compared" "Roasting at home"]
             (titles (picks/after-queue {:library queued}))))
      (is (some #{"Espresso grinders in bed"}
                (titles (picks/after-queue {:library (library/update-profile queued profile.queue/enqueue
                                                                             (id "Swear Jar" 0))})))
          "and explicit ones too, once the queue has one"))))

(deftest autoplay
  (let [state {:library lib}]
    (is (nil? (picks/autoplayed state grinders)) "not until the listener chose it")
    (is (= (id "Brew Lab" 0)
           (picks/autoplayed (preferring state {::app/autoplay? true}) grinders))
        "and then the first pick after the episode that ended")))

(deftest the-episodes-that-the-picks-of-a-show-start-from
  (let [k      (keys-by-title "Coffee Talk")
        source {:key k :feed (get-in lib [:feeds k :feed]) :url (get-in lib [:feeds k :url])}]
    (is (= [[(id "Coffee Talk" 1) (id "Coffee Talk" 0)] nil]
           (picks/show-seeds lib source))
        "its newest")
    (let [heard (-> lib
                    (library/update-profile profile/progress grinders 60))]
      (is (= [[grinders] {grinders 1}] (picks/show-seeds heard source))
          "or those that the listener heard"))
    (let [outside (dissoc source :key)
          [ids]   (picks/show-seeds lib outside)]
      (is (= ["1" "0"] (map second ids)) "and of a show outside the library, its newest")
      (is (= #{(first (first ids))} (set (keys (:feeds (picks/outside {} outside)))))
          "whose feed recommend is given"))))
