(ns dk.simongray.elektrolyt.library.operations
  "The operations on the listener's library, as data, and the functions
  that apply them: every change that the app makes to the library.

  An operation is a vector of its kind and its arguments, e.g. [:queue/move
  id 3]. A change is a map of an :operation and a :ui, a function of the
  state with the changed library that changes what the page shows, e.g.
  its announcement. Either can be missing.

  An operation is applied at the time of the listener's action, as :at,
  so that it stamps the same time however often it's applied."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.opml :as profile.opml]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.podcast.util.time :as time]
            [dk.simongray.elektrolyt.library.inbox :as inbox]
            [dk.simongray.elektrolyt.library.queue :as queue]))

(defn- profile-changed
  "The `library` with its profile changed by `f`, called with the profile,
  the `args` and then the instant `at`."
  [library at f & args]
  (update library :profile #(apply f % (concat args [at]))))

(defn- given
  "The result `x` of a library function as a map of the :library and
  anything else it returns, e.g. the :effects of a step."
  [x]
  (if (contains? x :library) x {:library x}))

(defn applied
  "The result of the `operation` on the `library` with the `opts`, as a
  map of the new :library and anything else it returns, e.g. the :effects
  of a step, or nil without a library.

  It's applied at the :at of the `opts`, the time of the listener's
  action, or else now. The other `opts` go to the library's functions,
  e.g. its :codec."
  [library [kind & args :as operation] opts]
  (when library
    (let [at      (or (:at opts) (time/now))
          changed #(apply profile-changed library at %1 args)]
      (given
       (case kind
         :episode/add         (let [[id url feed item-guid] args]
                                (if (library/playback library id)
                                  library
                                  (library/add-episode library url feed item-guid
                                                       {:at at})))
         :queue/enqueue       (changed profile.queue/enqueue)
         :queue/play-next     (changed profile.queue/play-next)
         :queue/move          (changed profile.queue/move)
         :queue/dequeue       (changed profile.queue/dequeue)
         :queue/dequeue-all   (changed profile.queue/dequeue-all)
         :queue/restore       (changed queue/restore)
         :inbox/dismiss       (changed profile.queue/dismiss)
         :inbox/dismiss-all   (changed profile.queue/dismiss-all)
         :inbox/enqueue-all   (changed profile.queue/enqueue-all)
         :inbox/restore       (changed inbox/restore)
         :settings/prefer     (changed profile/prefer)
         :opml/subscribe      (changed profile.opml/from-opml)
         :opml/mark-played    (let [[list] args]
                                (library/episodes-from-opml library list {:at at}))
         :library/subscribe   (let [[url fetched] args]
                                (library/subscribe library url fetched {:at at}))
         :library/unsubscribe (let [[k] args]
                                (library/unsubscribe library k {:at at}))
         ;; what the worker found, applied at the time of its refresh
         :library/refreshed   (let [[found] args]
                                (library/refreshed library found (dissoc opts :at)))
         ;; the feeds with only the notes that the app reads, as the worker
         ;; fetches them, so that a feed kept from before is written so
         ;; once, a one-off that app.library/leaned! explains
         :library/leaned      (library/leaned library at)
         :player/step         (let [[event] args]
                                (library/step library event (assoc opts :at at))))))))

(defn changed
  "The `state` with the `change` made to it: its operation applied to the
  library with the `opts`, and then its :ui function."
  ([state change]
   (changed state change {}))
  ([state {:keys [operation ui] :or {ui identity}} opts]
   (ui (cond-> state
         operation (update :library #(:library (applied % operation opts)))))))

(comment
  (applied nil [:queue/move ["feed" "item"] 2] {})
  ;; => nil
  #_.)
