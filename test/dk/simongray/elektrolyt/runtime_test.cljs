(ns dk.simongray.elektrolyt.runtime-test
  "The player's audio element, as runtime follows it, with stand-ins for
  the browser's Media Session and for what play-nice listens to, which
  Node has none of. runtime_test.clj checks which namespaces are runtime."
  (:require [clojure.test :refer [deftest is]]
            [dk.simongray.play-nice.controls :as controls]
            [dk.simongray.play-nice.media :as media]
            [dk.simongray.play-nice.page.tabs :as tabs]
            [dk.simongray.elektrolyt.runtime :as runtime]))

(defn stand-in-session
  "A stand-in for the browser's Media Session, which keeps the handler of
  each action in the atom `handlers`, or nil once it's cleared."
  [handlers]
  #js {:setActionHandler (fn [action f] (swap! handlers assoc action f))})

(defn set-actions
  "The actions that have a handler in `handlers`."
  [handlers]
  (set (keep (fn [[action f]] (when f action)) handlers)))

;; with the arities of the calls, since a call of a function of several
;; arities goes to the arity itself
(defn followed
  "A stand-in for a function of play-nice that follows an element, which
  returns a function that stops nothing."
  ([_ _] (fn []))
  ([_ _ _] (fn [])))

(deftest the-lock-screen-of-a-player-that-unmounted
  (let [handlers (atom {})]
    (set! (.-mediaSession js/navigator) (stand-in-session handlers))
    (try
      (with-redefs [media/watch!     followed
                    tabs/attach!     followed
                    controls/listen! followed]
        (runtime/attach-player! #js {} {})
        (runtime/lock-screen! {:title "One"} {:play identity :nexttrack identity})
        (is (= #{"play" "nexttrack"} (set-actions @handlers)))
        (runtime/detach-player!)
        (is (empty? (set-actions @handlers)) "has no buttons left"))
      (finally
        (js-delete js/navigator "mediaSession")))))
