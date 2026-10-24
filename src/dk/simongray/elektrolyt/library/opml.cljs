(ns dk.simongray.elektrolyt.library.opml
  "The import and export of the listener's subscriptions as OPML.

  An import subscribes to the file's shows, with their folders and tags.
  Once a refresh has fetched their feeds, it marks the episodes that the
  file lists as played or started, in the form of Overcast's extended
  export. An export downloads the subscriptions as a file in that form."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.format.opml :as format.opml]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.elektrolyt.library :as app-library]
            [dk.simongray.elektrolyt.library.refresh :as refresh]
            [dk.simongray.elektrolyt.runtime :as runtime]
            [dk.simongray.elektrolyt.ui.text :as text]
            [taoensso.telemere :as tel]))

(defn subscribed-count
  "How many shows the library in `state` subscribes to."
  [state]
  (count (some-> state :library :profile profile/subscriptions)))

(defn imported
  "The change of `state` that subscribes to the shows in the OPML
  subscription `list`, with a status of how many of them are new."
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

;; The marks need the import's feeds. A refresh that was under way before
;; the import doesn't fetch them, and neither does one that finds another
;; tab's worker refreshing, but the next comes at once, since the new feeds
;; are due.
(defn waited
  "The next phase of an import's wait for its feeds after `phase`, as the
  state goes from `old` to `new`:

  - :waiting until a refresh starts after the import
  - :started while that one runs
  - :fetched once it's done, or :waiting again if it failed or found
    another tab's worker refreshing"
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
  "Once a refresh has fetched the feeds of the OPML `list`, mark its played
  and started episodes in the library in `store`, and save it."
  [store list]
  (let [k     (gensym "opml-import")
        phase (atom :waiting)]
    (runtime/watch! store k
                    (fn [old new]
                      (when (= :fetched (swap! phase waited old new))
                        (runtime/unwatch! store k)
                        (app-library/change! store {:operation [:opml/mark-played list]})
                        (app-library/save! store))))))

(defn import!
  "Import the OPML `file` into the library in `store` and refresh it, or
  set a status if the file can't be read."
  [store file]
  (-> (.text file)
      (.then (fn [text]
               (let [list (format.opml/parse text)]
                 (app-library/change! store (imported @store list))
                 (mark-when-fetched! store list)
                 (-> (app-library/save! store)
                     (.then #(refresh/refresh! store false))))))
      (.catch (fn [e]
                (tel/log! {:level :error :id :opml/import-failed :error e}
                          "The OPML file wasn't imported")
                (swap! store assoc :settings-status unreadable)))))

(defn export!
  "Download the subscriptions in `state` as an OPML file."
  [state]
  (let [text (format.opml/emit (library/to-opml (:library state)))
        url  (js/URL.createObjectURL (js/Blob. #js [text] #js {:type "text/x-opml"}))
        a    (js/document.createElement "a")]
    (set! (.-href a) url)
    (set! (.-download a) library/opml-name)
    (.click a)
    (runtime/soon! #(js/URL.revokeObjectURL url))))
