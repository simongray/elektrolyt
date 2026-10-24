(ns dk.simongray.elektrolyt.library.saves-test
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.like-minded.store.indexeddb :as-alias indexeddb]
            [dk.simongray.elektrolyt.library.saves :as saves]))

(def since #inst "2026-10-24T14:32:00Z")

(deftest why-a-save-failed
  (is (= :full (saves/cause-of {:id :library/save-failed :cause ::indexeddb/full})))
  (is (= :newer-format (saves/cause-of {:id :library/newer-format})))
  (is (= :other (saves/cause-of {:id :library/save-failed :reason "Aborted"}))))

(deftest when-the-page-tells-of-it
  (let [once  (saves/failed nil {:id :library/save-failed} since)
        twice (saves/failed once {:id :library/save-failed} #inst "2026-10-24T14:40:00Z")]
    (is (= {:since since :failures 1 :cause :other} once))
    (is (not (saves/shown? once)) "not after one failure, which the next try usually fixes")
    (is (saves/shown? twice) "but after two in a row")
    (is (saves/shown? (saves/failed nil {:id :library/newer-format} since))
        "or at once when only the listener can fix it")
    (is (saves/shown? (saves/failed nil {:cause ::indexeddb/full} since)))
    (is (not (saves/shown? nil)))))

(deftest when-a-write-is-tried-again
  (is (saves/retry? (saves/failed nil {:id :library/save-failed} since)))
  (is (saves/retry? (saves/failed nil {:cause ::indexeddb/full} since)) "e.g. once there's room")
  (is (not (saves/retry? (saves/failed nil {:id :library/newer-format} since)))
      "but not over a library that a newer version saved")
  (is (not (saves/retry? nil)) "nor after a write that went through"))

(deftest announced-as-it-comes-and-goes
  (let [twice (saves/failed (saves/failed nil {} since) {} since)
        shown (saves/noted {} twice)
        again (saves/noted (dissoc shown :announcement) (saves/failed twice {} since))
        full  (saves/noted (dissoc shown :announcement)
                           (saves/failed twice {:cause ::indexeddb/full} since))
        saved (saves/noted shown nil)]
    (is (= "Your changes aren't saved yet." (:announcement shown)) "with its reason")
    (is (nil? (:announcement again)) "once")
    (is (= "Your changes aren't saved, because the browser has no room left." (:announcement full))
        "and again for another reason")
    (is (= "Your changes are saved" (:announcement saved)))
    (is (not (contains? saved :save-status)))
    (is (nil? (:announcement (saves/noted {} (saves/failed nil {} since))))
        "and not for a failure that it doesn't tell of")))

(deftest what-the-page-says
  (is (= "Your changes since 14:32 aren't saved yet." (saves/text {:cause :other} "14:32")))
  (is (= "Your changes since 14:32 aren't saved, because the browser has no room left."
         (saves/text {:cause :full} "14:32")))
  (is (re-find #"another tab has a newer version of Elektrolyt\.$"
               (saves/text {:cause :newer-format} "14:32")))
  (is (= "Your changes aren't saved yet." (saves/text {:cause :other} nil)) "or without the time"))
