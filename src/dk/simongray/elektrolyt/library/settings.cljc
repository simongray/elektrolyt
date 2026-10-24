(ns dk.simongray.elektrolyt.library.settings
  "The listener's settings, as the Settings page shows and changes them:
  the preferences of the profile, and what the page says of how this
  browser keeps the library.

  The preferences live in the profile, so that they sync with it, among
  them the directories that a search asks, in the order of their lists.
  The state keeps the last outcome that the page tells of as
  :settings-status, a map of the :group of settings that it's about and
  its :text, and what the browser said of its storage as :storage, a map
  of :supported?, :persisted? and the :usage in bytes."
  (:require [dk.simongray.podcast.profile :as profile]
            ;; the app's former name, since the listener's profile keeps
            ;; the preferences under keys of it
            [dk.simongray.podcast-app :as-alias app]))

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
  "The colours that the app can have, as the app keeps them, with the text
  of each."
  [[:auto "Automatic, as the device"]
   [:light "Light"]
   [:dark "Dark"]])

;; Media Queries 5, prefers-contrast: follow the device by default, as with
;; light and dark, and let the listener ask for more, or for the standard
;; on a device that asks for more
;; https://drafts.csswg.org/mediaqueries-5/#prefers-contrast
(def contrasts
  "The contrasts that the app can have, as the app keeps them, with the
  text of each."
  [[:auto "Automatic, as the device"]
   [:standard "Standard"]
   [:more "More"]])

;; podcast-clj's profile/prefer: preferences that only this app reads sync
;; with the others under keys of its own namespace, which other apps leave
;; alone. Until October 2026, the app kept them under the same keys
;; without a namespace, which a profile may still hold.
(def defaults
  "The preferences that the app keeps of its own, by their keys in the
  profile, with their values until the listener chooses others."
  {::app/appearance         :auto
   ::app/contrast           :auto
   ::app/artwork-colors?    true
   ::app/navigation-palette :pages
   ::app/explicit-picks?    false
   ::app/autoplay?          false
   ::app/alternative-feeds? true})

(defn preferences
  "The listener's preferences in the library of the `state`, or nil before
  it's restored."
  [state]
  (some-> (:library state) :profile profile/preferences))

(defn preference
  "The app's own preference `k` in the `state`, one of the keys of
  defaults: the listener's choice, under `k` or the key without its
  namespace that the app kept before, or else the default."
  [state k]
  (let [prefs (preferences state)]
    (get prefs k (get prefs (keyword (name k)) (defaults k)))))

(defn theme
  "The colours that the listener chose in the `state`, \"light\" or
  \"dark\", or nil for the device's."
  [state]
  (some-> (#{:light :dark} (preference state ::app/appearance)) name))

(defn contrast
  "The contrast that the listener chose in the `state`, \"standard\" or
  \"more\", or nil for the device's."
  [state]
  (some-> (#{:standard :more} (preference state ::app/contrast)) name))

(defn look
  "The colours and the contrast that the listener chose in the `state`, as
  a map of the :theme and the :contrast, each nil for the device's, or nil
  where there are no preferences to read, e.g. before the library is
  restored or when it failed to."
  [state]
  (when (preferences state)
    {:theme (theme state) :contrast (contrast state)}))

(defn artwork-colors?
  "Whether the pages take colours from artwork in the `state`, which they
  do until the listener prefers the app's own."
  [state]
  (preference state ::app/artwork-colors?))

;; The colours that colour-coded's vision namespace found for colour
;; blindness keep the pages apart for protans, deutans and tritans, but are
;; closer to each other than the pages' own colours, so they're a choice
(def navigation-palettes
  "The colours that the links of the navigation can have, as the app keeps
  them, with the text of each."
  [[:pages "A colour for each page"]
   [:player "The player's colour"]
   [:color-blind "Colours for colour blindness"]
   [:none "None"]])

(defn navigation-palette
  "The colours of the links of the navigation that the listener chose in
  the `state`, a colour for each page until they choose others."
  [state]
  (preference state ::app/navigation-palette))

(defn explicit-picks?
  "Whether the picks of the `state` may be explicit episodes anywhere, as
  they may not until the listener chooses so."
  [state]
  (preference state ::app/explicit-picks?))

;; Pocket Casts' Autoplay is off until the listener turns it on
;; https://blog.pocketcasts.com/2023/07/25/introducing-autoplay/
(defn autoplay?
  "Whether a pick plays when nothing else would after an episode ends, in
  the `state`, which it doesn't until the listener chooses so."
  [state]
  (preference state ::app/autoplay?))

(defn alternative-feeds?
  "Whether DR's own feeds take the episodes of their siblings on
  drpodcast.nu in the `state`, and a search asks drpodcast.nu for the
  series that DR publishes no podcast of, as they do until the listener
  turns it off."
  [state]
  (preference state ::app/alternative-feeds?))

(defn preferred
  "The change that sets the listener's preferences `prefs`."
  [prefs]
  {:operation [:settings/prefer prefs]})

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
  "The change of the `state` that has a search ask the directory `source`,
  at the end of the order, where `on?`, or leave it out."
  [state source on?]
  (let [others (filterv #(not= source %) (searched state))]
    (preferred {:search-sources (if on? (conj others source) others)})))

(defn moved-source
  "The change of the `state` that puts the directory `source` at the
  position `to`, from 1, in the order of those that a search asks."
  [state source to]
  (let [others (filterv #(not= source %) (searched state))
        i      (max 0 (min (count others) (dec to)))]
    (preferred {:search-sources (into (conj (subvec others 0 i) source)
                                      (subvec others i))})))

(defn cleared
  "The `state` once the cache is empty, or not, by `error`, with a status
  that says how it went. A cleared tab forgets what the cache kept too: the
  searches, the feeds of shows and where they are, the shows they link to
  and the colours of artwork. So Back doesn't bring them back, and the page
  works out its colours again."
  [state error]
  (if error
    (assoc state :settings-status
           {:group :this-browser
            :text  "The searches and shows couldn't be cleared."})
    (-> (dissoc state :searches :feeds :feed-urls :linked :colors)
        (assoc :settings-status {:group :this-browser
                                 :text  "Cleared the searches and shows."}))))
