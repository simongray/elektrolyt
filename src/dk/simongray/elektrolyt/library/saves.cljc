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

(defn text
  "What the page says of the save `status`, with the time of day of its
  :since as `time`, e.g. \"14:32\", or without a time when it's nil."
  [{:keys [cause]} time]
  (let [changes (str "Your changes" (when time (str " since " time)))]
    (case cause
      :full         (str changes " aren't saved, because the browser has no room left.")
      :newer-format (str changes " can't be saved, because another tab has a newer "
                         "version of Elektrolyt.")
      (str changes " aren't saved yet."))))

;; WCAG 2.2, SC 4.1.3 Status Messages: announced as it comes, changes its
;; cause and goes, without taking the focus, since it isn't about what the
;; listener does
(defn noted
  "The `state` with the save `status`, or without one once the changes are
  saved, announced as the page starts telling of it or of another cause,
  and as it stops."
  [state status]
  (let [before (:save-status state)
        was    (shown? before)
        now    (shown? status)]
    (cond-> (if status
              (assoc state :save-status status)
              (dissoc state :save-status))
      (and now (or (not was) (not= (:cause before) (:cause status))))
      (assoc :announcement (text status nil))

      (and was (not now))
      (assoc :announcement "Your changes are saved"))))
