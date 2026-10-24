(ns dk.simongray.elektrolyt.library.opml
  "The import and export of the listener's subscriptions as OPML.

  An import subscribes to the file's shows, with their folders and tags.
  As refreshes fetch their feeds, it marks the episodes that the file
  lists as played or started, in the form of Overcast's extended export.
  The marks that wait for their feeds stay in IndexedDB, so that the next
  page load goes on with them. An export downloads the subscriptions as a
  file in that form."
  (:require [dk.simongray.like-minded.store.indexeddb :as indexeddb]
            [dk.simongray.like-minded.transit :as transit]
            [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.feed.guid :as guid]
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
  {:group  :subscriptions
   :error? true
   :text   (str "That file couldn't be read as OPML. Choose a file that a "
                "podcast app exported.")})

(defn marked
  "The shows of the OPML subscription `list` that mark episodes, with their
  feed URLs and episodes alone, as a list of their own, or nil when none
  does."
  [list]
  (when-let [shows (seq (filter (comp seq :episodes) (:subscriptions list)))]
    {:subscriptions (mapv #(select-keys % [:xml-url :episodes]) shows)}))

;; A database of its own, since podcast-clj reads every other document in
;; the library's as part of its search index
(def marks-db
  "The IndexedDB database of the marks of an import that wait for their
  feeds."
  (indexeddb/store "dk.simongray.podcast-app.opml"))

;; A document per import, so that a second import leaves the marks of the
;; first alone
(defn marks-name
  "The name of the document of the marks of the import `id` that wait."
  [id]
  (str "marks-" id (:extension transit/codec)))

(defn kept!
  "A promise of keeping the marks of the OPML `list` in the document `name`
  until their feeds arrive."
  [name list]
  ((:put marks-db) name ((:encode transit/codec) list) {}))

(defn forgot!
  "A promise of forgetting the marks in the document `name`, once none
  waits."
  [name]
  ((:delete marks-db) name))

;; The marks need the import's feeds, which arrive with a refresh in this
;; tab or another, and a feed that failed with a later one. A mark made
;; already stays as it is when the marks are made again.
(defn waiting
  "The marks of the OPML `list` whose shows the library in `state`
  subscribes to without their feeds, or nil when none does."
  [state list]
  (let [lib     (:library state)
        profile (:profile lib)
        by-url  (profile/by-url profile)
        waits?  (fn [{:keys [xml-url]}]
                  (let [k (by-url (guid/normal-url xml-url))]
                    (and (profile/subscribed? profile k)
                         (not (library/loaded? lib k)))))]
    (when-let [shows (seq (filter waits? (:subscriptions list)))]
      {:subscriptions (vec shows)})))

(declare mark-when-fetched!)

(defn mark!
  "Mark the played and started episodes of the OPML `list` whose feeds the
  library in `store` has, and save it, then keep the marks of the other
  shows in the document `name` until their feeds arrive."
  [store name list]
  (app-library/change! store {:operation [:opml/mark-played list]})
  (app-library/save! store)
  (if-let [left (waiting @store list)]
    (do (kept! name left)
        (mark-when-fetched! store name left))
    (forgot! name)))

(defn mark-when-fetched!
  "Mark the episodes of the OPML `list` in the library in `store` once one
  of its shows has its feed, or the listener left one, and keep the rest
  in the document `name`."
  [store name list]
  (let [k (gensym "opml-import")]
    (runtime/watch! store k
                    (fn [old new]
                      ;; not at each step of the player, which changes only the profile
                      (when (and (not (identical? (get-in old [:library :feeds])
                                                  (get-in new [:library :feeds])))
                                 (not= list (waiting new list)))
                        (runtime/unwatch! store k)
                        (mark! store name list))))))

(defn resume-import!
  "Go on with the marks of an earlier import in the document `name` that
  wait for their feeds, in the library in `store`. Nothing is written
  while none of those feeds has arrived, e.g. one that's gone for good."
  [store name]
  (-> ((:fetch marks-db) name {})
      (.then (fn [doc]
               (when-let [list (some-> doc :body ((:decode transit/codec)))]
                 (if (= list (waiting @store list))
                   (mark-when-fetched! store name list)
                   (mark! store name list)))))
      (.catch (fn [e]
                (tel/log! {:level :warn :id :opml/marks-unread
                           :data  {:name name} :error e}
                          "The marks of an earlier import weren't read")
                (forgot! name)))))

(defn resume!
  "Go on with the marks of the earlier imports that wait for their feeds,
  e.g. after a reload, in the library in `store`."
  [store]
  (when (:library @store)
    (-> ((:list marks-db))
        (.then #(run! (fn [{:keys [name]}] (resume-import! store name)) %))
        (.catch #(tel/log! {:level :warn :id :opml/marks-unread :error %}
                           "The marks of earlier imports weren't listed")))))

(defn import!
  "Import the OPML `file` into the library in `store` and refresh it, or
  set a status if the file can't be read."
  [store file]
  (-> (.text file)
      (.then (fn [text]
               (let [list (format.opml/parse text)]
                 (app-library/change! store (imported @store list))
                 (some->> (marked list) (mark! store (marks-name (random-uuid))))
                 (-> (app-library/save! store)
                     (.then #(refresh/refresh! store false))))))
      (.catch (fn [e]
                (tel/log! {:level :error :id :opml/import-failed :error e}
                          "The OPML file wasn't imported")
                (swap! store assoc :settings-status unreadable :announcement (:text unreadable))))))

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
