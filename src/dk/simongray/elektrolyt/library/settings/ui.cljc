(ns dk.simongray.elektrolyt.library.settings.ui
  "The views of the Settings page.

  Each change applies at once with no Save button, following NN/g,
  Microsoft and GNOME. The groups are fieldsets with their headings in the
  legends, following GOV.UK, so that a screen reader names each control's
  group and lists the page's headings.
  https://www.nngroup.com/articles/toggle-switch-guidelines/
  https://learn.microsoft.com/en-us/windows/apps/design/app-settings/guidelines-for-app-settings
  https://design-system.service.gov.uk/components/fieldset/"
  (:require [dk.simongray.podcast-app :as-alias app]
            [dk.simongray.elektrolyt.library.settings :as settings]
            [dk.simongray.elektrolyt.player :as player]
            [dk.simongray.elektrolyt.search :as search]
            [dk.simongray.elektrolyt.ui.controls :as controls-ui]
            [dk.simongray.elektrolyt.ui.routes :as routes]
            [dk.simongray.elektrolyt.ui.rows :as rows]
            [dk.simongray.elektrolyt.ui.text :as text]))

;; WCAG 2.2, SC 4.1.3 Status Messages: the outcome of an action that the
;; page doesn't otherwise show, in a status line beside its button. The
;; line is there before it has any text.
(defn status
  "The status line of the settings group `k`, with the status in `state`
  if it's that group's."
  [state k]
  (let [{:keys [group text]} (:settings-status state)]
    [:p.settings-status {:role "status"} (when (= k group) text)]))

;; Wikipedia's and GOV.UK's contents link to a page's headings by their
;; ids, and so do the links from other pages here. The heading takes the
;; focus like a page's main heading.
(defn group
  "A group of settings named `k`, headed `heading`, with the `children`,
  whose heading has `k` as its id for links to the group."
  [k heading & children]
  [:fieldset.settings-group
   [:legend [:h2 {:id (name k) :tabindex -1} heading]]
   children])

(defn group-href
  "The path of the group of settings named `k`, e.g. \"/settings#search\"."
  [k]
  (routes/href :route/settings nil nil (name k)))

(defn choice
  "A settings row with a select of the `options`, labelled `label`, with
  the `id`, the `chosen` option selected and the `action` on a change."
  [id label options chosen action]
  [:div.setting
   [:label {:for id} label]
   (controls-ui/select id options chosen action)])

;; WAI-ARIA APG, switch pattern: a checkbox with role="switch", which every
;; browser announces as a switch and the CSS draws as one. Its label stays
;; the same as it turns on and off.
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
  "The settings of the app's colours in `state`: light or dark, the
  contrast, colours from artwork and colours in the navigation."
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
  "The playback settings in `state`."
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
  "The settings of the recommendations in `state`."
  [state]
  (group :recommendations "Recommendations"
         (switch "explicit-picks" "Explicit episodes"
                 (str "Unless this is on, recommendations are explicit only under "
                      "an explicit episode or show, and at the end of a queue that "
                      "has one.")
                 (settings/explicit-picks? state) ::app/explicit-picks?)))

(defn directory-row
  "The row of the directory `source` at index `i`, `on?` or off, with the
  buttons that move it among the `n` that are on. Unless `index?`, the
  Podcast Index's row notes that the server doesn't sign its requests."
  [n index? i [source on?]]
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
        "This server doesn't sign the requests to it, so a search here leaves it out."])]))

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
    (group :search "Search"
           [:p "A search asks each directory that's on, and shows what each "
            "found in a list of its own, in this order."]
           [:ol.settings-directories {:role "list"}
            (map-indexed (partial directory-row n index?) listed)])))

(defn dr
  "The DR setting in `state`: whether DR's podcasts take episodes from
  drpodcast.nu, and a search finds DR's programmes there that have no
  podcast."
  [state]
  (group :dr "DR"
         (switch "alternative-feeds" "Episodes from drpodcast.nu"
                 (str "DR's podcasts also get the episodes that drpodcast.nu "
                      "republishes from DR's radio, which DR's own feeds leave out "
                      "or have later. A search finds the programmes that DR "
                      "publishes no podcast of there too.")
                 (settings/alternative-feeds? state) ::app/alternative-feeds?)))

;; AntennaPod, Pocket Casts and Castro all import and export OPML
;; https://antennapod.org/documentation/
;; https://support.pocketcasts.com/article/opml-import/
;; https://castro.fm/support/import-subscriptions
(defn subscriptions
  "The import and export of the listener's subscriptions as OPML, with the
  status in `state`."
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
  "How this browser keeps the library, from the :storage in `state`, and
  the button that clears the cache."
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
  "The content of the Settings page for `state`, below its heading."
  [state]
  (list
   (appearance state)
   (playback state)
   (recommendations state)
   (search-directories state)
   (dr state)
   (subscriptions state)
   (this-browser state)))
