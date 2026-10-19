(ns dk.simongray.podcast-app.settings
  "The listener's settings, as the Settings page shows and changes them:
  the preferences of the profile, among them where the new episodes of a
  new show go, and what the page says of how this browser keeps the
  library.

  The preferences live in the profile, so that they sync with it, among
  them the directories that a search asks, in the order of their lists.
  The state keeps the last outcome that the page tells of as
  :settings-status, a map of the :group of settings that it's about and
  its :text, and what the browser said of its storage as :storage, a map
  of :supported?, :persisted? and the :usage in bytes."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]))

;; Apple Podcasts offers skips of 10 to 60 seconds, and AntennaPod a list
;; of them too, rather than a number to type, which can't be wrong
;; https://support.apple.com/guide/podcasts/pod4130f48/mac
(def skip-seconds
  "The seconds that a skip can jump, as the Settings page offers them."
  [5 10 15 30 45 60])

(defn skip-options
  "The seconds that a skip can jump, with the listener's `current` among
  them, e.g. one that another app set."
  [current]
  (sort (distinct (cond-> skip-seconds (pos-int? current) (conj current)))))

(def track-buttons
  "What the next and previous buttons of a lock screen or headphones can
  go to, as profile/default-preferences names it, with the text of each."
  [[:chapters "Chapters"]
   [:queue "The episodes of the queue"]
   [:chapters-first "Chapters, then the queue"]])

;; web.dev, prefers-color-scheme: follow the device by default, and let the
;; listener choose, since some want a dark device but light pages
;; https://web.dev/articles/prefers-color-scheme
(def appearances
  "The colours that the app can have, as profile/default-preferences names
  them for :appearance, with the text of each."
  [[:auto "Automatic, as the device"]
   [:light "Light"]
   [:dark "Dark"]])

;; Media Queries 5, prefers-contrast: follow the device by default, as with
;; light and dark, and let the listener ask for more, or for the standard
;; on a device that asks for more
;; https://drafts.csswg.org/mediaqueries-5/#prefers-contrast
(def contrasts
  "The contrasts that the app can have, as profile/default-preferences
  names them for :contrast, with the text of each."
  [[:auto "Automatic, as the device"]
   [:standard "Standard"]
   [:more "More"]])

(defn preferences
  "The listener's preferences in the library of the `state`, or nil before
  it's restored."
  [state]
  (some-> (:library state) :profile profile/preferences))

(defn theme
  "The colours that the listener chose in the `state`, \"light\" or
  \"dark\", or nil for the device's."
  [state]
  (some-> (#{:light :dark} (:appearance (preferences state))) name))

(defn contrast
  "The contrast that the listener chose in the `state`, \"standard\" or
  \"more\", or nil for the device's."
  [state]
  (some-> (#{:standard :more} (:contrast (preferences state))) name))

(defn new-episodes
  "Where the new episodes of a new show go by the preferences of the
  `state`, as profile/new-episodes names it."
  [state]
  (profile/new-episodes (merge profile/default-settings
                               (:subscription-defaults (preferences state)))))

(defn preferred
  "The `state` with the listener's preferences `prefs` changed."
  [state prefs]
  (update state :library library/update-profile profile/prefer prefs))

(defn defaulted
  "The `state` with the new episodes of a new show going to `choice`, as
  profile/new-episodes names it."
  [state choice]
  (preferred state {:subscription-defaults
                    (merge (:subscription-defaults (preferences state))
                           (profile/new-episodes-settings choice))}))

(def directories
  "The directories that the Settings page offers, in the order in which it
  lists those that a search leaves out."
  [:podcastindex :apple :fyyd :gpodder])

(defn searched
  "The directories that a search asks by the preferences of the `state`, in
  the listener's order."
  [state]
  (:search-sources (or (preferences state) profile/default-preferences)))

(defn directory-rows
  "The directories as the Settings page lists them for the `state`, each a
  pair of its id and whether a search asks it: those that it asks, in
  their order, and then the others."
  [state]
  (let [on (searched state)]
    (concat (map #(vector % true) on)
            (for [d directories :when (not (some #{d} on))]
              [d false]))))

(defn turned
  "The `state` with a search asking the directory `source`, at the end of
  the order, where `on?`, or leaving it out."
  [state source on?]
  (let [others (filterv #(not= source %) (searched state))]
    (preferred state {:search-sources (if on? (conj others source) others)})))

(defn moved-source
  "The `state` with the directory `source` at the position `to`, from 1, in
  the order of those that a search asks."
  [state source to]
  (let [others (filterv #(not= source %) (searched state))
        i      (max 0 (min (count others) (dec to)))]
    (preferred state {:search-sources (into (conj (subvec others 0 i) source)
                                            (subvec others i))})))

(defn shows-text
  "The count `n` of shows in words, e.g. \"3 shows\"."
  [n]
  (str n (if (= 1 n) " show" " shows")))

;; Pocket Casts asks whether a changed default should apply to the shows
;; that the listener has, as "Apply to existing podcasts?"
;; https://github.com/Automattic/pocket-casts-android/blob/main/modules/services/localization/src/main/res/values/strings.xml
(defn applied-to-all
  "The `state` with every show's new episodes going where those of a new
  show go, and the page's status line saying so."
  [state]
  (let [ks       (keys (profile/subscriptions (:profile (:library state))))
        settings (profile/new-episodes-settings (new-episodes state))]
    (-> state
        (update :library library/update-profile
                (fn [p at] (reduce #(profile/configure %1 %2 settings at) p ks)))
        (assoc :settings-status {:group :new-episodes
                                 :text  (str "Applied to " (shows-text (count ks)))}))))

(defn megabytes
  "The `bytes` in megabytes to a tenth, e.g. \"12.3 MB\"."
  [bytes]
  (let [tenths (quot (+ (long bytes) 50000) 100000)]
    (str (quot tenths 10) "." (rem tenths 10) " MB")))
