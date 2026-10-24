(ns dk.simongray.elektrolyt.ui.announcement
  "The status message that the page reads out to a screen reader, which
  any part of the app puts into the state with announced.

  A screen reader reads a status line out as it changes, so a message
  that repeats the last one, e.g. after a Try again that fails again,
  wouldn't be heard. So each message is counted, and the page's status
  line shows it as a new element by its count.
  https://www.w3.org/TR/wai-aria-1.2/#aria-live")

(defn announced
  "The `state` with the status `message` for a screen reader, counted, so
  that the page reads it out even when it repeats the last one."
  [state message]
  (-> state
      (assoc :announcement message)
      (update :announcements (fnil inc 0))))
