(ns dk.simongray.podcast-app.opml
  "The import and export of the listener's subscriptions as OPML, the way
  podcast apps hand them to each other.

  An import subscribes to the shows of the file, with their folders and
  tags, refreshes the feeds, and then takes over the episodes that the
  file says were played or started, as Overcast's extended export writes
  them. An export downloads the subscriptions as a file, with those
  episodes, in the same form."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.format.opml :as format.opml]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.opml :as profile.opml]
            [dk.simongray.podcast-app.audio :as audio]
            [dk.simongray.podcast-app.refresh :as refresh]
            [dk.simongray.podcast-app.settings :as settings]))

(defn subscribed-count
  "How many shows the library of the `state` subscribes to."
  [state]
  (count (some-> state :library :profile profile/subscriptions)))

(defn imported
  "The `state` with the shows of the OPML subscription `list` subscribed
  to, and the page's status line saying how many of them are new."
  [state list]
  (let [before (subscribed-count state)
        after  (update state :library library/update-profile profile.opml/from-opml list)
        added  (- (subscribed-count after) before)]
    (assoc after :settings-status
           {:group :subscriptions
            :text  (if (pos? added)
                     (str "Subscribed to " (settings/shows-text added)
                          " from the file. Their episodes are on the way.")
                     "You subscribe to every show of the file already.")})))

;; WCAG 2.2, SC 3.3.3 Error Suggestion: what went wrong, and what to do
(def unreadable
  "The status of an import of a file that isn't OPML."
  {:group :subscriptions
   :text  (str "That file couldn't be read as OPML. Choose a file that a "
               "podcast app exported.")})

(defn import!
  "Import the OPML `file` that the listener chose into the library of the
  `store`, as the namespace describes."
  [store file]
  (-> (.text file)
      (.then (fn [text]
               (let [list (format.opml/parse text)]
                 (swap! store imported list)
                 (-> (audio/save! store)
                     (.then #(refresh/refresh! store false))
                     (.then (fn [_]
                              (swap! store update :library library/episodes-from-opml list)
                              (audio/save! store)))))))
      (.catch (fn [e]
                (js/console.error "The OPML file wasn't imported" e)
                (swap! store assoc :settings-status unreadable)))))

(defn export!
  "Download the subscriptions of the library of the `state` as an OPML
  file."
  [state]
  (let [text (format.opml/emit (library/to-opml (:library state)))
        url  (js/URL.createObjectURL (js/Blob. #js [text] #js {:type "text/x-opml"}))
        a    (js/document.createElement "a")]
    (set! (.-href a) url)
    (set! (.-download a) library/opml-name)
    (.click a)
    (js/setTimeout #(js/URL.revokeObjectURL url) 0)))
