(ns dk.simongray.elektrolyt.ui.settings
  "The views of the Settings page: the app's colours, how the listener
  plays, what the recommendations leave out, the directories that a
  search asks, whether DR's podcasts take episodes from drpodcast.nu, the
  import and export of their subscriptions, and how this browser keeps the
  library.

  Each change applies at once, with no Save button, which NN/g, Microsoft
  and GNOME advise for settings, and which a field-by-field profile makes
  natural. The groups are fieldsets whose legends hold their headings, as
  GOV.UK nests them, so that a screen reader names the group of each
  control and lists the headings of the page.
  https://www.nngroup.com/articles/toggle-switch-guidelines/
  https://learn.microsoft.com/en-us/windows/apps/design/app-settings/guidelines-for-app-settings
  https://design-system.service.gov.uk/components/fieldset/"
  (:require [dk.simongray.podcast-app :as-alias app]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.routes :as routes]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.settings :as settings]
            [dk.simongray.elektrolyt.text :as text]
            [dk.simongray.elektrolyt.ui.controls :as controls-ui]
            [dk.simongray.elektrolyt.ui.rows :as rows]))

;; WCAG 2.2, SC 4.1.3 Status Messages: the outcome of an action whose
;; effect the page doesn't show, e.g. how many shows an import added, in a
;; status line beside its button, which is there before it says anything
(defn status
  "The status line of the group `k` of settings, which says the status of
  the `state` when it's that group's."
  [state k]
  (let [{:keys [group text]} (:settings-status state)]
    [:p.settings-status {:role "status"} (when (= k group) text)]))

;; Wikipedia's and GOV.UK's contents link to the headings of a page by
;; their ids, as a link from another page does here, e.g. from the Search
;; page to its directories. The heading takes the focus, as a page's does.
(defn group
  "A group of settings named `k`, headed `heading`, with the `children`,
  whose heading has the id that a link to the group names."
  [k heading & children]
  [:fieldset.settings-group
   [:legend [:h2 {:id (name k) :tabindex -1} heading]]
   children])

(defn group-href
  "The path of the group of settings named `k`, e.g. \"/settings#search\"."
  [k]
  (routes/href :route/settings nil nil (name k)))

(defn choice
  "A select of controls-ui/select with the `id`, labelled `label`, of the
  `options`, with `chosen` selected, whose change dispatches the
  `action`, in a row of the settings."
  [id label options chosen action]
  [:div.setting
   [:label {:for id} label]
   (controls-ui/select id options chosen action)])

;; WAI-ARIA APG, switch pattern: a checkbox with role="switch", which every
;; browser announces as a switch, and which look.css draws as one in every
;; browser. Its label stays the same as it turns on and off.
;; https://www.w3.org/WAI/ARIA/apg/patterns/switch/
;; https://webkit.org/blog/15054/an-html-switch-control/
(defn switch
  "A switch with the `id`, labelled `label` and described by the `hint`,
  on when `on?`, whose change sets the listener's preference `k`."
  [id label hint on? k]
  [:div.setting
   [:input {:type             "checkbox"
            :switch           true
            :role             "switch"
            :id               id
            :checked          (boolean on?)
            :aria-describedby (str id "-hint")
            :on               {:change [[:settings/prefer k [:event/target-checked]]]}}]
   [:label {:for id} label]
   [:p {:id (str id "-hint")} hint]])

(defn appearance
  "The choice of the app's colours in the `state`: light or dark, the
  contrast, whether the artwork colours the app, whether each page's link
  in the navigation has a colour of its own, and the size of the player,
  which sync with the profile as the other preferences do."
  [state]
  (group :appearance "Appearance"
         (choice "light-or-dark" "Light or dark" settings/appearances
                 (settings/preference state ::app/appearance)
                 [:settings/prefer ::app/appearance [:event/target-keyword]])
         (choice "contrast" "Contrast" settings/contrasts
                 (settings/preference state ::app/contrast)
                 [:settings/prefer ::app/contrast [:event/target-keyword]])
         (switch "artwork-colours" "Colours from the artwork"
                 (str "The player takes its colours from the artwork of what "
                      "plays, and a show's page from the show's artwork. "
                      "Otherwise the player keeps the app's own dark grey, and a "
                      "show's page the app's own colours.")
                 (settings/artwork-colors? state) ::app/artwork-colors?)
         (choice "navigation-colours" "Colours in the navigation" settings/navigation-palettes
                 (settings/navigation-palette state)
                 [:settings/prefer ::app/navigation-palette [:event/target-keyword]])))

(defn playback
  "The settings of how the listener plays, by their preferences in the
  `state`."
  [state]
  (let [{:keys [skip-back skip-forward playback-rate continuous? track-buttons]}
        (settings/preferences state)]
    (group :playback "Playback"
           (choice "skip-back" "Skip back"
                   (for [s (settings/skip-options skip-back)] [s (str s " seconds")])
                   skip-back [:settings/prefer :skip-back [:event/target-number]])
           (choice "skip-forward" "Skip forward"
                   (for [s (settings/skip-options skip-forward)] [s (str s " seconds")])
                   skip-forward [:settings/prefer :skip-forward [:event/target-number]])
           (choice "speed" "Speed" player/speed-options
                   playback-rate [:settings/prefer :playback-rate [:event/target-number]])
           (switch "continuous" "Continuous play"
                   (str "When an episode ends and the queue has no other, the next "
                        "episode of its show plays.")
                   continuous? :continuous?)
           (switch "autoplay" "Autoplay"
                   (str "When an episode ends and neither the queue nor Continuous "
                        "play has another, the first of More like your queue plays.")
                   (settings/autoplay? state) ::app/autoplay?)
           (choice "track-buttons" "Next and previous on the lock screen go to"
                   settings/track-buttons
                   track-buttons [:settings/prefer :track-buttons [:event/target-keyword]]))))

(defn recommendations
  "The settings of the picks in the `state`: whether they may be explicit
  episodes anywhere."
  [state]
  (group :recommendations "Recommendations"
         (switch "explicit-picks" "Explicit episodes"
                 (str "Unless this is on, recommendations are explicit only under "
                      "an explicit episode or show, and at the end of a queue that "
                      "has one.")
                 (settings/explicit-picks? state) ::app/explicit-picks?)))

;; Kagi's lenses: only those turned on in the settings appear while
;; searching. The order of the rows is the order of the lists of the
;; Search page, which the buttons of the queue rearrange.
;; https://help.kagi.com/kagi/settings/lenses.html
(defn search-directories
  "The directories that a search asks in the `state`, in the order of
  their lists, which the listener turns on and off and rearranges, and
  those that it leaves out."
  [state]
  (let [listed (settings/directory-rows state)
        n      (count (filter second listed))
        index? (get-in state [:server :podcastindex?])]
    (group :search "Search"
           [:p "A search asks each directory that's on, and shows what each "
            "found in a list of its own, in this order."]
           [:ol.settings-directories {:role "list"}
            (for [[i [source on?]] (map-indexed vector listed)]
              (let [title (search/source-name source)
                    id    (str "directory-" (name source))
                    hint? (and (= :podcastindex source) (not index?))]
                [:li.setting {:replicant/key source}
                 [:input (cond-> {:type    "checkbox"
                                  :id      id
                                  :checked (boolean on?)
                                  :on      {:change [[:settings/search-source source [:event/target-checked]]
                                                     [:page/focus id]]}}
                           hint? (assoc :aria-describedby (str id "-hint")))]
                 [:label {:for id} title]
                 (when on?
                   (rows/move-buttons title (inc i) n
                                      #(str id "-" (name %))
                                      #(vector :settings/move-source source %)))
                 (when hint?
                   [:p {:id (str id "-hint")}
                    "This server doesn't sign the requests to it, so a search here leaves it out."])]))])))

(defn dr
  "Whether DR's podcasts take more episodes from drpodcast.nu in the
  `state`, and a search finds the programmes that have no podcast there."
  [state]
  (group :dr "DR"
         (switch "alternative-feeds" "Episodes from drpodcast.nu"
                 (str "DR's podcasts also get the episodes that drpodcast.nu "
                      "republishes from DR's radio, which DR's own feeds leave out "
                      "or have later. A search finds the programmes that DR "
                      "publishes no podcast of there too.")
                 (settings/alternative-feeds? state) ::app/alternative-feeds?)))

;; AntennaPod, Pocket Casts and Castro all import and export OPML, the way
;; a listener brings their shows from one app to another
;; https://antennapod.org/documentation/
;; https://support.pocketcasts.com/article/opml-import/
;; https://castro.fm/support/import-subscriptions
(defn subscriptions
  "The import and export of the listener's subscriptions as OPML, in the
  `state`."
  [state]
  (group :subscriptions "Subscriptions"
         [:div.setting
          [:label {:for "opml-import"} "Import from an OPML file"]
          [:input {:type             "file"
                   :id               "opml-import"
                   :accept           ".opml,.xml,text/x-opml,application/xml,text/xml"
                   :aria-describedby "opml-import-hint"
                   :on               {:change [[:opml/import]]}}]
          [:p#opml-import-hint
           "The shows of the file join your library, e.g. from the export of "
           "another podcast app."]]
         [:button {:type "button" :on {:click [[:opml/export]]}}
          "Export as OPML"]
         (status state :subscriptions)))

;; Storage Standard, sections 7.1 and 8: a browser can clear a site's
;; storage when the device runs low, unless the site asks it to persist
;; https://storage.spec.whatwg.org/#persistence
;; https://developer.mozilla.org/en-US/docs/Web/API/StorageManager/estimate
(defn this-browser
  "How this browser keeps the library, as the :storage of the `state`
  says, and the button that clears what the cache keeps."
  [{{:keys [supported? persisted? usage] :as storage} :storage :as state}]
  (group :this-browser "This browser"
         [:p "Unlike the rest, this belongs to this browser alone."]
         (cond
           (nil? storage)   [:p "Asking the browser…"]
           (not supported?) [:p "This browser doesn't say how it keeps the library."]
           persisted?       [:p "This browser keeps the library until you clear the site's data."]
           :else            (list
                             [:p "This browser may clear the library when the device "
                              "runs low on space."]
                             [:button {:type "button" :on {:click [[:storage/persist]]}}
                              "Ask the browser to keep it"]))
         (when usage
           [:p (str "The app uses " (text/megabytes usage) " here.")])
         [:p "It also keeps your searches for a month, and the shows you "
          "opened and those they recommend for a week, so that they show at once."]
         [:button {:type "button" :on {:click [[:cache/clear]]}}
          "Clear the searches and shows"]
         (status state :this-browser)))

(defn content
  "The content of the Settings page of the `state`, below its heading."
  [state]
  (list
   (appearance state)
   (playback state)
   (recommendations state)
   (search-directories state)
   (dr state)
   (subscriptions state)
   (this-browser state)))
