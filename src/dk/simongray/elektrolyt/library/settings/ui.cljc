(ns dk.simongray.elektrolyt.library.settings.ui
  "The views of the Settings page.

  Each change applies at once with no Save button, following NN/g,
  Microsoft and GNOME.
  https://www.nngroup.com/articles/toggle-switch-guidelines/
  https://learn.microsoft.com/en-us/windows/apps/design/app-settings/guidelines-for-app-settings"
  (:require [dk.simongray.elektrolyt.library.saves.ui :as saves-ui]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.ui.controls :as controls-ui]
            [dk.simongray.elektrolyt.ui.icons :as icons]
            [dk.simongray.elektrolyt.ui.notices :as notices]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.rows :as rows]
            [dk.simongray.elektrolyt.ui.setting :as setting]
            [dk.simongray.elektrolyt.ui.text :as text]))

;; WCAG 2.2, SC 4.1.3 Status Messages: the outcome of an action that the
;; page doesn't otherwise show, in a status line under the group's rows.
;; The line is there before it has any text. A failure is a notice that the
;; listener can dismiss beside it, announced by what failed.
(defn status
  "The status line of the settings group `k`, with the status in `state`
  if it's that group's, or the notice of the group's failure."
  [state k]
  (let [{:keys [group text error?]} (:settings-status state)
        ours?                       (= k group)]
    (list
     [:p.settings-status {:role "status"} (when (and ours? (not error?)) text)]
     (when (and ours? error?)
       (notices/line {:dismiss [[:store/assoc :settings-status nil]]
                      :refocus (str "#" (name k))}
                     text
                     nil)))))

(defn group-href
  "The path of the group of settings named `k`, e.g. \"/settings#search\"."
  [k]
  (routes/href :route/settings nil nil (name k)))

(defn switch
  "A switch as setting/switch makes it with the `id`, `label`, `hint` and
  `on?`, whose change sets the listener's preference `k`."
  [id label hint on? k]
  (setting/switch id label hint on? [:settings/prefer k [:event/target-checked]]))

(defn appearance
  "The settings of the app's colours in `state`: light or dark, the
  contrast, colours from artwork and colours in the navigation."
  [state]
  (setting/group
   :appearance "Appearance"
   (setting/rows
    (setting/choice "light-or-dark" "Light or dark" settings/appearances
                    (settings/preference state ::settings/appearance)
                    [:settings/prefer ::settings/appearance [:event/target-keyword]])
    (setting/choice "contrast" "Contrast" settings/contrasts
                    (settings/preference state ::settings/contrast)
                    [:settings/prefer ::settings/contrast [:event/target-keyword]])
    (switch "artwork-colours" "Colours from the artwork"
            (str "The player takes its colours from the artwork of what "
                 "plays, and a show's page from the show's artwork. "
                 "Otherwise the player keeps the app's own dark grey, and a "
                 "show's page the app's own colours.")
            (settings/artwork-colors? state) ::settings/artwork-colors?)
    (setting/choice "navigation-colours" "Colours in the navigation" settings/navigation-palettes
                    (settings/navigation-palette state)
                    [:settings/prefer ::settings/navigation-palette [:event/target-keyword]]))))

(defn playback
  "The playback settings in `state`."
  [state]
  (let [{:keys [skip-back skip-forward playback-rate continuous? track-buttons]}
        (settings/preferences state)]
    (setting/group
     :playback "Playback"
     (setting/rows
      (setting/choice "skip-back" "Skip back"
                      (for [s (settings/skip-options skip-back)] [s (str s " seconds")])
                      skip-back [:settings/prefer :skip-back [:event/target-number]])
      (setting/choice "skip-forward" "Skip forward"
                      (for [s (settings/skip-options skip-forward)] [s (str s " seconds")])
                      skip-forward [:settings/prefer :skip-forward [:event/target-number]])
      (setting/choice "speed" "Speed" (player/speed-options playback-rate)
                      playback-rate [:settings/prefer :playback-rate [:event/target-number]])
      (switch "continuous" "Continuous play"
              (str "When an episode ends and the queue has no other, the next "
                   "episode of its show plays.")
              continuous? :continuous?)
      (switch "autoplay" "Autoplay"
              (str "When an episode ends and neither the queue nor Continuous "
                   "play has another, the first of More like your queue plays.")
              (settings/autoplay? state) ::settings/autoplay?)
      (setting/choice "track-buttons" "Next and previous on the lock screen go to"
                      settings/track-buttons
                      track-buttons [:settings/prefer :track-buttons [:event/target-keyword]])))))

(defn recommendations
  "The settings of the recommendations in `state`."
  [state]
  (setting/group
   :recommendations "Recommendations"
   (setting/rows
    (switch "explicit-picks" "Explicit episodes"
            (str "Unless this is on, recommendations are explicit only under "
                 "an explicit episode or show, and at the end of a queue that "
                 "has one.")
            (settings/explicit-picks? state) ::settings/explicit-picks?))))

(defn directory-row
  "The row of the directory `source` at index `i`, `on?` or off, with the
  buttons that move it among the `n` that are on. Unless `index?`, the
  Podcast Index's row notes that the server doesn't sign its requests."
  [n index? i [source on?]]
  (let [title (search/source-name source)
        id    (str "directory-" (name source))
        hint? (and (= :podcastindex source) (not index?))]
    [:li.setting {:replicant/key source}
     [:label {:for id} title]
     (when hint?
       [:p {:id (setting/hint-id id)}
        "This server doesn't sign the requests to it, so a search here leaves it out."])
     [:div.setting-control
      (when on?
        (rows/move-buttons title (inc i) n
                           #(str id "-" (name %))
                           #(vector :settings/move-source source %)))
      ;; the key keeps the same switch as the arrows come and go, so that its
      ;; knob slides
      (setting/switch-input
       id on?
       (cond-> {:replicant/key "switch"
                :on            {:change [[:settings/search-source source [:event/target-checked]]
                                         [:page/focus id]]}}
         hint? (assoc :aria-describedby (setting/hint-id id))))]]))

;; Kagi's lenses: only those turned on in the settings appear while
;; searching
;; https://help.kagi.com/kagi/settings/lenses.html
(defn search-directories
  "The directories in `state` that the listener turns on, off and
  rearranges: those that a search asks, in the order of their results,
  and then the others."
  [state]
  (let [listed (settings/directory-rows state)
        n      (count (filter second listed))
        index? (get-in state [:server :podcastindex?])]
    (setting/group
     :search "Search"
     [:p "A search asks each directory that's on, and shows what each "
      "found in a list of its own, in this order."]
     [:ol.setting-rows {:role "list"}
      (map-indexed (partial directory-row n index?) listed)])))

(defn special
  "The settings in `state` for particular shows: whether DR's podcasts take
  episodes from drpodcast.nu, and a search finds DR's programmes there that
  have no podcast."
  [state]
  (setting/group
   :special "Special settings"
   (setting/rows
    (switch "alternative-feeds" "Episodes from drpodcast.nu"
            (str "DR's podcasts also get the episodes that drpodcast.nu "
                 "republishes from DR's radio, which DR's own feeds leave out "
                 "or have later. A search finds the programmes that DR "
                 "publishes no podcast of there too.")
            (settings/alternative-feeds? state) ::settings/alternative-feeds?))))

;; AntennaPod, Pocket Casts and Castro all import and export OPML
;; https://antennapod.org/documentation/
;; https://support.pocketcasts.com/article/opml-import/
;; https://castro.fm/support/import-subscriptions
(defn subscriptions
  "The import and export of the listener's subscriptions as OPML, with the
  status in `state`."
  [state]
  (setting/group
   :subscriptions "Subscriptions"
   (setting/rows
    (setting/file "opml-import" "Import from an OPML file"
                  (str "The shows of the file join your library, e.g. from the "
                       "export of another podcast app.")
                  {:accept ".opml,.xml,text/x-opml,application/xml,text/xml"
                   :on     {:change [[:opml/import]]}})
    (setting/action "opml-export" "Export as an OPML file"
                    "A file of the shows in your library, for another podcast app."
                    [controls-ui/button {:icon             icons/download-simple
                                         :aria-describedby (setting/hint-id "opml-export")
                                         :on               {:click [[:opml/export]]}}
                     "Export" [:span.visually-hidden " as OPML"]]))
   (status state :subscriptions)))

;; Storage Standard, sections 7.1 and 8: a browser can clear a site's
;; storage when the device runs low, unless the site asks it to persist
;; https://storage.spec.whatwg.org/#persistence
;; https://developer.mozilla.org/en-US/docs/Web/API/StorageManager/estimate
(defn keeping
  "The row of how this browser keeps the library, from its `storage`, with
  the button that asks it to keep the library when it may not."
  [{:keys [supported? persisted?] :as storage}]
  (let [row (partial setting/action "keeping-the-library" "Keeping the library")]
    (cond
      (nil? storage)   (row "Asking the browser…")
      (not supported?) (row "This browser doesn't say how it keeps the library.")
      persisted?       (row "This browser keeps the library until you clear the site's data.")
      :else            (row "This browser may clear the library when the device runs low on space."
                            [:button {:type             "button"
                                      :aria-describedby (setting/hint-id "keeping-the-library")
                                      :on               {:click [[:storage/persist]]}}
                             "Ask the browser to keep it"]))))

(defn this-browser
  "How this browser keeps the library, from the :storage in `state`, and
  the button that clears the cache."
  [{{:keys [usage] :as storage} :storage :as state}]
  (setting/group
   :this-browser "This browser"
   [:p "Unlike the rest, this belongs to this browser alone."]
   (when-let [unsaved (saves-ui/status-text state)]
     [:p unsaved])
   (setting/rows
    (keeping storage)
    (when usage
      (setting/fact "Space used" (text/megabytes usage)))
    (setting/action "searches-and-shows" "Searches and shows"
                    (str "Searches stay for a month, and the shows you opened and "
                         "those they recommend for a week, so that they show at once.")
                    [controls-ui/button {:icon             icons/trash
                                         :aria-describedby (setting/hint-id "searches-and-shows")
                                         :on               {:click [[:cache/clear]]}}
                     "Clear" [:span.visually-hidden " the searches and shows"]]))
   (status state :this-browser)))

;; The groups keep to their columns rather than flow between them, so that
;; none jumps to the other column when something in one changes its height.
;; How the app looks and plays is on the left, and where shows come from
;; and where they're kept on the right.
(defn content
  "The content of the Settings page for `state`, below its heading."
  [state]
  [:div.columns
   [:div
    (appearance state)
    (playback state)
    (recommendations state)]
   [:div
    (search-directories state)
    (special state)
    (subscriptions state)
    (this-browser state)]])
