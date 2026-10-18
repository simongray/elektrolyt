(ns dk.simongray.podcast-app.ui.settings
  "The views of the Settings page: how the listener plays, where new
  episodes go, the import and export of their subscriptions, and how this
  browser keeps the library.

  Each change applies at once, with no Save button, which NN/g, Microsoft
  and GNOME advise for settings, and which a field-by-field profile makes
  natural. The groups are fieldsets whose legends hold their headings, as
  GOV.UK nests them, so that a screen reader names the group of each
  control and lists the headings of the page.
  https://www.nngroup.com/articles/toggle-switch-guidelines/
  https://learn.microsoft.com/en-us/windows/apps/design/app-settings/guidelines-for-app-settings
  https://design-system.service.gov.uk/components/fieldset/"
  (:require [dk.simongray.podcast-app.player :as player]
            [dk.simongray.podcast-app.settings :as settings]
            [dk.simongray.podcast-app.ui.subscription :as subscription]))

;; WCAG 2.2, SC 4.1.3 Status Messages: the outcome of an action whose
;; effect the page doesn't show, e.g. how many shows an import added, in a
;; status line beside its button, which is there before it says anything
(defn status
  "The status line of the group `k` of settings, which says the status of
  the `state` when it's that group's."
  [state k]
  (let [{:keys [group text]} (:settings-status state)]
    [:p.settings-status {:role "status"} (when (= k group) text)]))

(defn group
  "A group of settings headed `heading`, with the `children`."
  [heading & children]
  [:fieldset.settings-group
   [:legend [:h2 heading]]
   children])

(defn option-value
  "The value of an option of a select for the value `v`, a number or a
  keyword."
  [v]
  (if (keyword? v) (name v) (str v)))

(defn choice
  "A select with the `id`, labelled `label`, of the `options`, pairs of a
  value and its text, with `chosen` selected, whose change dispatches the
  `action`, which holds the placeholder of the new value."
  [id label options chosen action]
  [:div.setting
   [:label {:for id} label]
   [:select {:id id :on {:change [action]}}
    (for [[v text] options]
      [:option {:value    (option-value v)
                :selected (= (option-value v) (option-value chosen))}
       text])]])

(defn playback
  "The settings of how the listener plays, by their preferences `prefs`."
  [{:keys [skip-back skip-forward playback-rate continuous? track-buttons] :as prefs}]
  (group "Playback"
         (choice "skip-back" "Skip back"
                 (for [s (settings/skip-options skip-back)] [s (str s " seconds")])
                 skip-back [:settings/prefer :skip-back [:event/target-number]])
         (choice "skip-forward" "Skip forward"
                 (for [s (settings/skip-options skip-forward)] [s (str s " seconds")])
                 skip-forward [:settings/prefer :skip-forward [:event/target-number]])
         (choice "speed" "Speed"
                 (for [s player/speeds] [s (str s "×")])
                 playback-rate [:settings/prefer :playback-rate [:event/target-number]])
         ;; WAI-ARIA APG, switch pattern: a checkbox with role="switch",
         ;; which every browser announces as a switch, and which Safari
         ;; also draws as one for the switch attribute. Its label stays the
         ;; same as it turns on and off.
         ;; https://www.w3.org/WAI/ARIA/apg/patterns/switch/
         ;; https://webkit.org/blog/15054/an-html-switch-control/
         ;; TODO: draw it as a switch in every browser, with appearance:
         ;; none, once the app has its look
         [:div.setting
          [:input {:type             "checkbox"
                   :switch           true
                   :role             "switch"
                   :id               "continuous"
                   :checked          (boolean continuous?)
                   :aria-describedby "continuous-hint"
                   :on               {:change [[:settings/prefer :continuous? [:event/target-checked]]]}}]
          [:label {:for "continuous"} "Continuous play"]
          [:p#continuous-hint
           "When an episode ends and the queue has no other, the next "
           "episode of its show plays."]]
         (choice "track-buttons" "Next and previous on the lock screen go to"
                 settings/track-buttons
                 track-buttons [:settings/prefer :track-buttons [:event/target-keyword]])))

(defn new-episodes
  "The default of where the new episodes of a new show go, in the `state`,
  and the button that applies it to every show."
  [state]
  (group "New episodes"
         (choice "default-new-episodes" "New episodes of a new show go to"
                 subscription/new-episodes-options
                 (settings/new-episodes state)
                 [:settings/new-episodes [:event/target-keyword]])
         [:p "A show has its own choice on its page, which this sets only for "
          "the shows that you subscribe to from now on."]
         [:button {:type "button" :on {:click [[:settings/apply-to-all]]}}
          "Apply to all shows"]
         (status state :new-episodes)))

;; AntennaPod, Pocket Casts and Castro all import and export OPML, the way
;; a listener brings their shows from one app to another
;; https://antennapod.org/documentation/
;; https://support.pocketcasts.com/article/opml-import/
;; https://castro.fm/support/import-subscriptions
(defn subscriptions
  "The import and export of the listener's subscriptions as OPML, in the
  `state`."
  [state]
  (group "Subscriptions"
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
  says."
  [{{:keys [supported? persisted? usage] :as storage} :storage :as state}]
  (group "This browser"
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
           [:p (str "The app uses " (settings/megabytes usage) " here.")])
         (status state :this-browser)))

(defn content
  "The content of the Settings page of the `state`, below its heading."
  [{:keys [library] :as state}]
  (if-not library
    [:p "The library couldn't be read."]
    (list
     (playback (settings/preferences state))
     (new-episodes state)
     (subscriptions state)
     (this-browser state))))
