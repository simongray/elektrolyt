(ns dk.simongray.podcast-app.opml
  "The import and export of the listener's subscriptions as OPML, the way
  podcast apps hand them to each other.

  An import subscribes to the shows of the file, with their folders and
  tags, refreshes the feeds, and once a refresh has fetched them, takes
  over the episodes that the file says were played or started, as
  Overcast's extended export writes them. An export downloads the subscriptions as a file, with those
  episodes, in the same form."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.format.opml :as format.opml]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast-app.audio :as audio]
            [dk.simongray.podcast-app.library :as app-library]
            [dk.simongray.podcast-app.refresh :as refresh]
            [dk.simongray.podcast-app.runtime :as runtime]
            [dk.simongray.podcast-app.text :as text]))

(defn subscribed-count
  "How many shows the library of the `state` subscribes to."
  [state]
  (count (some-> state :library :profile profile/subscriptions)))

(defn imported
  "The change of the `state` that subscribes to the shows of the OPML
  subscription `list`, with the page's status line saying how many of
  them are new."
  [state list]
  (let [before (subscribed-count state)]
    {:operation [:opml/subscribe list]
     :ui        (fn [after]
                  (let [added (- (subscribed-count after) before)]
                    (assoc after :settings-status
                           {:group :subscriptions
                            :text  (if (pos? added)
                                     (str "Subscribed to " (text/counted added "show")
                                          " from the file. Their episodes are on the way.")
                                     "You subscribe to every show of the file already.")})))}))

;; WCAG 2.2, SC 3.3.3 Error Suggestion: what went wrong, and what to do
(def unreadable
  "The status of an import of a file that isn't OPML."
  {:group :subscriptions
   :text  (str "That file couldn't be read as OPML. Choose a file that a "
               "podcast app exported.")})

;; The marks need the feeds, but refresh! starts nothing while a refresh is
;; under way, which began before the import and so lacks its feeds, and
;; finds nothing to fetch while another tab's worker refreshes. The next
;; refresh comes at once, since the new feeds are due.
(defn waited
  "The phase of an import's wait for its feeds, from `phase` as the state
  of its store goes from `old` to `new`:

  - :waiting until a refresh starts after the import
  - :started while that one runs
  - :fetched once it's done, or :waiting again where it found another
    tab's worker refreshing, or failed"
  [phase old new]
  (let [running? #(boolean (get-in % [:refresh-status :running?]))]
    (case phase
      :waiting (if (and (running? new) (not (running? old))) :started :waiting)
      :started (cond
                 (running? new)                         :started
                 (get-in new [:refresh-status :result]) :fetched
                 :else                                  :waiting)
      phase)))

(defn mark-when-fetched!
  "Take over the episodes that the OPML `list` says were played or started
  into the library of the `store` once a refresh has fetched its feeds, as
  waited tells, and save it."
  [store list]
  (let [k     (gensym "opml-import")
        phase (atom :waiting)]
    (runtime/watch! store k
                    (fn [old new]
                      (when (= :fetched (swap! phase waited old new))
                        (runtime/unwatch! store k)
                        (app-library/change! store {:operation [:opml/mark-played list]})
                        (audio/save! store))))))

(defn import!
  "Import the OPML `file` that the listener chose into the library of the
  `store`, as the namespace describes."
  [store file]
  (-> (.text file)
      (.then (fn [text]
               (let [list (format.opml/parse text)]
                 (app-library/change! store (imported @store list))
                 (mark-when-fetched! store list)
                 (-> (audio/save! store)
                     (.then #(refresh/refresh! store false))))))
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
    (runtime/soon! #(js/URL.revokeObjectURL url))))
