(ns dk.simongray.elektrolyt.library.saves
  "Whether the listener's changes are saved, and what the page says while
  they aren't.

  The state keeps :save-status while writes fail, a map of:

  - :since, when the oldest change that waits was made
  - :failures, how many writes in a row failed
  - :cause, :full when the browser has no room left for the site,
    :newer-format when a newer version of the app saved the library, or
    else :other"
  (:require [dk.simongray.like-minded.store.indexeddb :as-alias indexeddb]))

(defn cause-of
  "The cause of a write that failed with the `error` of its save: :full,
  :newer-format or :other."
  [{:keys [id cause] :as error}]
  (cond
    (= :library/newer-format id) :newer-format
    (= ::indexeddb/full cause)   :full
    :else                        :other))

(defn failed
  "The save `status` once one more write has failed with the `error`,
  while the oldest change that waits was made at the instant `since`."
  [status error since]
  {:since    since
   :failures (inc (:failures status 0))
   :cause    (cause-of error)})

;; A write that fails once usually goes through at the next try, 5 seconds
;; later, but one that has no room, or a newer version in its way, waits
;; for the listener
(defn shown?
  "Whether the page tells of the save `status`: after two failed writes in
  a row, or at once when only the listener can fix the cause."
  [{:keys [failures cause] :as status}]
  (boolean (and status
                (or (<= 2 failures) (contains? #{:full :newer-format} cause)))))

;; WCAG 2.2, SC 4.1.3 Status Messages: announced as it comes and goes,
;; without taking the focus, since it isn't about what the listener does
(defn noted
  "The `state` with the save `status`, or without one once the changes are
  saved, announced as the page starts or stops telling of it."
  [state status]
  (let [was (shown? (:save-status state))
        now (shown? status)]
    (cond-> (if status
              (assoc state :save-status status)
              (dissoc state :save-status))
      (and now (not was)) (assoc :announcement "Your changes aren't saved")
      (and was (not now)) (assoc :announcement "Your changes are saved"))))

(defn text
  "What the page says of the save `status`, with the time of day of its
  :since as `time`, e.g. \"14:32\"."
  [{:keys [cause]} time]
  (case cause
    :full         (str "Your changes since " time " aren't saved, because the "
                       "browser has no room left.")
    :newer-format (str "Your changes since " time " can't be saved, because "
                       "another tab has a newer version of Elektrolyt.")
    (str "Your changes since " time " aren't saved yet.")))
