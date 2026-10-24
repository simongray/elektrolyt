(ns dk.simongray.elektrolyt.library.settings-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.elektrolyt.library.inbox-test :as inbox-test]
            [dk.simongray.elektrolyt.library.operations :as operations]
            [dk.simongray.elektrolyt.library.settings :as settings]))

(def state
  {:library inbox-test/lib})

(defn preferred [state prefs]
  (operations/changed state (settings/preferred prefs)))

(defn changed
  "The `state` with the change that `f` gives for it and the `args` made."
  [f state & args]
  (operations/changed state (apply f state args)))

(deftest skips
  (is (= [5 10 15 30 45 60] (settings/skip-options 15)))
  (is (= [5 10 15 20 30 45 60] (settings/skip-options 20)) "with one that another app set"))

(deftest preferences
  (is (= 30 (:skip-forward (settings/preferences state))) "the defaults, at first")
  (is (= 45 (:skip-forward (settings/preferences (preferred state {:skip-forward 45})))))
  (is (nil? (settings/preferences {})) "and none before the library is restored"))

(defn own
  "The app's own preferences in the `state`, by the names of their keys."
  [state]
  (into {} (for [k (keys settings/defaults)]
             [(keyword (name k)) (settings/preference state k)])))

(deftest the-apps-own-preferences
  (let [defaults {:appearance :auto :contrast :auto :artwork-colors? true :navigation-palette :pages
                  :explicit-picks? false :autoplay? false :alternative-feeds? true}]
    (is (= defaults (own state)) "the defaults, at first")
    (is (= defaults (own {})) "and before the library is restored")
    (is (= (assoc defaults :appearance :light :autoplay? true)
           (own (preferred state {::settings/appearance :light ::settings/autoplay? true})))
        "or what the listener chose")))

(deftest the-colours-of-the-app
  (is (nil? (settings/theme state)) "the device's, at first")
  (is (= "dark" (settings/theme (preferred state {::settings/appearance :dark}))))
  (is (= "light" (settings/theme (preferred state {::settings/appearance :light}))))
  (is (nil? (settings/theme (preferred state {::settings/appearance :auto}))))
  (is (= "more" (settings/contrast (preferred state {::settings/contrast :more}))))
  (is (nil? (settings/contrast (preferred state {::settings/contrast :auto}))) "or the device's contrast")
  (is (nil? (settings/theme {})) "and before the library is restored"))

(deftest the-look-to-keep
  (is (= {:theme "dark" :contrast "more"}
         (settings/look (preferred state {::settings/appearance :dark ::settings/contrast :more}))))
  (is (= {:theme nil :contrast nil} (settings/look state))
      "the device's, which forgets the copies of an earlier choice")
  (is (nil? (settings/look {}))
      "but nothing to go by without the library, which keeps them"))

(deftest the-directories-of-a-search
  (is (= [[:podcastindex true] [:apple true] [:fyyd true] [:gpodder false]]
         (settings/directory-rows state))
      "the Podcast Index, Apple Podcasts and fyyd at first, and gpodder.net left out")
  (is (= [:podcastindex :apple :fyyd :gpodder] (settings/searched (changed settings/turned state :gpodder true)))
      "turned on at the end")
  (is (= [:apple :fyyd] (settings/searched (changed settings/turned state :podcastindex false))))
  (is (= [:fyyd :podcastindex :apple] (settings/searched (changed settings/moved-source state :fyyd 1))))
  (is (= [:podcastindex :fyyd :apple] (settings/searched (changed settings/moved-source state :apple 9)))
      "no further than the end"))

(deftest clearing-the-cache
  (let [state {:searches  {"pc20" {}}
               :feeds     {"https://example.org/feed.xml" {}}
               :feed-urls {"g" {:url "https://example.org/feed.xml" :from :index}}
               :linked    {"g" {:podroll [] :status :done}}
               :colors    {"https://example.org/art.jpg" {:seed "#3a6ea5" :dominant "#203040"}}
               :route     {:name :route/settings}}]
    (is (= {:route           {:name :route/settings}
            :settings-status {:group :this-browser
                              :text  "Cleared the searches and shows."}}
           (settings/cleared state nil))
        "the tab forgets them too")
    (is (= {"pc20" {}} (:searches (settings/cleared state (ex-info "no" {}))))
        "and keeps them when the cache couldn't be cleared")
    (is (= [true "The searches and shows couldn't be cleared."]
           ((juxt (comp :error? :settings-status) :announcement) (settings/cleared state (ex-info "no" {}))))
        "which it announces as a failure")))
