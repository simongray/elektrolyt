(ns dk.simongray.podcast-app.settings-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast-app.inbox-test :as inbox-test]
            [dk.simongray.podcast-app.settings :as settings]))

(def state
  {:library inbox-test/lib})

(defn new-episodes-of-the-show
  "Where the new episodes of the show of the `state` go."
  [state]
  (profile/new-episodes (profile/settings (:profile (:library state)) inbox-test/k)))

(deftest skips
  (is (= [5 10 15 30 45 60] (settings/skip-options 15)))
  (is (= [5 10 15 20 30 45 60] (settings/skip-options 20)) "with one that another app set"))

(deftest preferences
  (is (= 30 (:skip-forward (settings/preferences state))) "the defaults, at first")
  (is (= 45 (:skip-forward (settings/preferences (settings/preferred state {:skip-forward 45})))))
  (is (nil? (settings/preferences {})) "and none before the library is restored"))

(deftest the-colours-of-the-app
  (is (nil? (settings/theme state)) "the device's, at first")
  (is (= "dark" (settings/theme (settings/preferred state {:appearance :dark}))))
  (is (= "light" (settings/theme (settings/preferred state {:appearance :light}))))
  (is (nil? (settings/theme (settings/preferred state {:appearance :auto}))))
  (is (= "more" (settings/contrast (settings/preferred state {:contrast :more}))))
  (is (nil? (settings/contrast (settings/preferred state {:contrast :auto}))) "or the device's contrast")
  (is (nil? (settings/theme {})) "and before the library is restored"))

(deftest new-episodes-of-a-new-show
  (is (= :inbox (settings/new-episodes state)))
  (let [s (settings/defaulted state :next)]
    (is (= :next (settings/new-episodes s)))
    (is (= :inbox (new-episodes-of-the-show s)) "the shows that the listener has keep theirs")
    (let [applied (settings/applied-to-all s)]
      (is (= :next (new-episodes-of-the-show applied)) "until it's applied to all of them")
      (is (= {:group :new-episodes :text "Applied to 1 show"} (:settings-status applied))))))

(deftest the-directories-of-a-search
  (is (= [[:podcastindex true] [:apple true] [:fyyd true] [:gpodder false]]
         (settings/directory-rows state))
      "the Podcast Index, Apple Podcasts and fyyd at first, and gpodder.net left out")
  (is (= [:podcastindex :apple :fyyd :gpodder] (settings/searched (settings/turned state :gpodder true)))
      "turned on at the end")
  (is (= [:apple :fyyd] (settings/searched (settings/turned state :podcastindex false))))
  (is (= [:fyyd :podcastindex :apple] (settings/searched (settings/moved-source state :fyyd 1))))
  (is (= [:podcastindex :fyyd :apple] (settings/searched (settings/moved-source state :apple 9)))
      "no further than the end"))

(deftest sizes
  (is (= "12.3 MB" (settings/megabytes 12345678)))
  (is (= "0.0 MB" (settings/megabytes 0))))
