(ns dk.simongray.elektrolyt.library.opml
  "The import and export of the listener's subscriptions as OPML.

  An import subscribes to the file's shows, with their folders and tags.
  As refreshes fetch their feeds, it marks the episodes that the file
  lists as played or started, in the form of Overcast's extended export.
  An export downloads the subscriptions as a file in that form."
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

(defn fetched-count
  "How many of the shows that the library in `state` subscribes to have
  their feeds."
  [state]
  (let [lib (:library state)]
    (count (filter #(library/loaded? lib %)
                   (keys (some-> lib :profile profile/subscriptions))))))

;; The marks need the import's feeds, which arrive with a refresh in this
;; tab or another, and a feed that failed with a later one. A mark made
;; already stays as it is when the marks are made again.
(defn arrived?
  "Whether more of the subscribed shows have their feeds as the state goes
  from `old` to `new`."
  [old new]
  (and (not (identical? (get-in old [:library :feeds]) (get-in new [:library :feeds])))
       (> (fetched-count new) (fetched-count old))))

(defn mark-when-fetched!
  "Each time more feeds have arrived, mark the played and started episodes
  of the OPML `list` in the library in `store`, and save it, until every
  show has its feed."
  [store list]
  (let [k (gensym "opml-import")]
    (runtime/watch! store k
                    (fn [old new]
                      (when (arrived? old new)
                        (when (= (fetched-count new) (subscribed-count new))
                          (runtime/unwatch! store k))
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
