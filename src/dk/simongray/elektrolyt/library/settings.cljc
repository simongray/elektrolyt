(ns dk.simongray.elektrolyt.library.settings
  "The listener's settings, as the Settings page shows and changes them.
  They're preferences in the profile, so that they sync with it."
  (:require [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.ui.announcement :as announcement]))

;; Apple Podcasts offers skips of 10 to 60 seconds and AntennaPod a list
;; too, rather than a number to type, so that a skip can't be wrong
;; https://support.apple.com/guide/podcasts/pod4130f48/mac
(def skip-seconds
  "The skips that the listener can choose, in seconds."
  [5 10 15 30 45 60])

(defn skip-options
  "The skips to choose from, in seconds, with the listener's `current` one
  among them, e.g. one that another app set."
  [current]
  (sort (distinct (cond-> skip-seconds (pos-int? current) (conj current)))))

(def track-buttons
  "What the next and previous buttons on a lock screen or headphones can
  go to, with the text of each."
  [[:chapters "Chapters"]
   [:queue "The episodes of the queue"]
   [:chapters-first "Chapters, then the queue"]])

;; web.dev, prefers-color-scheme: follow the device by default, and let the
;; listener choose, since some want a dark device but light pages
;; https://web.dev/articles/prefers-color-scheme
(def appearances
  "The colour schemes that the listener can choose, with the text of each."
  [[:auto "Automatic, as the device"]
   [:light "Light"]
   [:dark "Dark"]])

;; Media Queries 5, prefers-contrast: follow the device by default, and let
;; the listener ask for more contrast, or for the standard one on a device
;; that asks for more
;; https://drafts.csswg.org/mediaqueries-5/#prefers-contrast
(def contrasts
  "The contrasts that the listener can choose, with the text of each."
  [[:auto "Automatic, as the device"]
   [:standard "Standard"]
   [:more "More"]])

;; Preferences that only this app reads sync under keys of this namespace,
;; which other apps leave alone
(def defaults
  "The app's own preferences and their defaults, by their keys in the
  profile."
  {::appearance         :auto
   ::contrast           :auto
   ::artwork-colors?    true
   ::navigation-palette :pages
   ::explicit-picks?    false
   ::autoplay?          false
   ::alternative-feeds? true})

(defn preferences
  "The listener's preferences in `state`, or nil without a library."
  [state]
  (some-> (:library state) :profile profile/preferences))

(defn preference
  "The app's own preference `k` in `state`: the listener's choice, or else
  its default."
  [state k]
  (get (preferences state) k (defaults k)))

(defn theme
  "The colour scheme that the listener chose in `state`, \"light\" or
  \"dark\", or nil to follow the device."
  [state]
  (some-> (#{:light :dark} (preference state ::appearance)) name))

(defn contrast
  "The contrast that the listener chose in `state`, \"standard\" or
  \"more\", or nil to follow the device."
  [state]
  (some-> (#{:standard :more} (preference state ::contrast)) name))

(defn look
  "The listener's colour scheme and contrast in `state`, as a map of the
  :theme and the :contrast, or nil when there are no preferences to read,
  e.g. before the library opens."
  [state]
  (when (preferences state)
    {:theme (theme state) :contrast (contrast state)}))

(defn artwork-colors?
  "Whether the pages in `state` take their colours from artwork rather
  than the app's own."
  [state]
  (preference state ::artwork-colors?))

;; The colours for colour blindness keep the pages apart for protans,
;; deutans and tritans, but are closer to each other than the pages' own
;; colours, so they're a choice rather than the default
(def navigation-palettes
  "The colourings of the navigation's links that the listener can choose,
  with the text of each."
  [[:pages "A colour for each page"]
   [:player "The player's colour"]
   [:color-blind "Colours for colour blindness"]
   [:none "None"]])

(defn navigation-palette
  "The colouring of the navigation's links that the listener chose in
  `state`."
  [state]
  (preference state ::navigation-palette))

(defn explicit-picks?
  "Whether the picks in `state` may be explicit episodes anywhere."
  [state]
  (preference state ::explicit-picks?))

;; Pocket Casts' Autoplay is off until the listener turns it on
;; https://blog.pocketcasts.com/2023/07/25/introducing-autoplay/
(defn autoplay?
  "Whether autoplay is on in `state`, so that a pick plays when an episode
  ends and nothing else would."
  [state]
  (preference state ::autoplay?))

(defn alternative-feeds?
  "Whether the episodes from drpodcast.nu are on in `state`: DR's own feeds
  take those of their siblings there, and a search asks drpodcast.nu for
  the series that DR publishes no podcast of."
  [state]
  (preference state ::alternative-feeds?))

(defn preferred
  "The change that sets the listener's preferences `prefs`."
  [prefs]
  {:operation [:settings/prefer prefs]})

(def directories
  "The directories that a search can ask, in the order in which those that
  it leaves out are listed."
  [:podcastindex :apple :fyyd :gpodder])

(defn searched
  "The directories that a search asks in `state`, in the listener's order."
  [state]
  (:search-sources (or (preferences state) profile/default-preferences)))

(defn directory-rows
  "The rows of the directories in `state`, each a pair of a directory and
  whether a search asks it: those that it asks, in order, then the others."
  [state]
  (let [on (searched state)]
    (concat (map #(vector % true) on)
            (for [d directories :when (not (some #{d} on))]
              [d false]))))

(defn turned
  "The change of `state` that adds the directory `source` to the end of
  those that a search asks if `on?`, or else removes it."
  [state source on?]
  (let [others (filterv #(not= source %) (searched state))]
    (preferred {:search-sources (if on? (conj others source) others)})))

(defn moved-source
  "The change of `state` that moves the directory `source` to the position
  `to`, counted from 1, among those that a search asks."
  [state source to]
  (let [others (filterv #(not= source %) (searched state))
        i      (max 0 (min (count others) (dec to)))]
    (preferred {:search-sources (into (conj (subvec others 0 i) source)
                                      (subvec others i))})))

(defn cleared
  "The `state` once clearing the cache has succeeded, or failed with the
  `error`, with a status of how it went. A success also drops what the
  cache held from the state, so that Back doesn't bring it back and the
  colours are worked out again."
  [state error]
  (if error
    (let [text "The searches and shows couldn't be cleared."]
      (-> (assoc state :settings-status {:group :this-browser :error? true :text text})
          (announcement/announced text)))
    (-> (dissoc state :searches :feeds :feed-urls :linked :colors)
        (assoc :settings-status {:group :this-browser
                                 :text  "Cleared the searches and shows."}))))
