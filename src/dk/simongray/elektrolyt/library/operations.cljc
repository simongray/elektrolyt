(ns dk.simongray.elektrolyt.library.operations
  "The operations on the listener's library, as data, and the functions
  that apply them: every way in which the app changes the library.

  An operation is a vector of its kind and its arguments, e.g. [:queue/move
  id 3], as the namespace of its subject gives it. A change of the state
  is a map of the :operation and the :ui, a function of the state with the
  changed library that changes what the page shows, e.g. its
  announcement. Either can be missing.

  An operation is applied with the time of the listener's action, as :at,
  so that it stamps the same time whenever it's applied."
  (:require [dk.simongray.podcast.app.library :as library]
            [dk.simongray.podcast.profile :as profile]
            [dk.simongray.podcast.profile.opml :as profile.opml]
            [dk.simongray.podcast.profile.queue :as profile.queue]
            [dk.simongray.podcast.util.time :as time]
            [dk.simongray.elektrolyt.inbox :as inbox]
            [dk.simongray.elektrolyt.queue :as queue]))

(defn- profile-changed
  "The `library` with its profile changed by `f` of it, the `args` and the
  instant `at`, in that order, as the profile's functions take them."
  [library at f & args]
  (update library :profile #(apply f % (concat args [at]))))

(defn- given
  "What a function of podcast-clj's library gave, `x`, as a map of the
  :library and what else it tells, e.g. the :effects of a step."
  [x]
  (if (contains? x :library) x {:library x}))

(defn applied
  "What the `operation` gives of the `library`, applied at the :at of the
  `opts`, the instant of the listener's action, or now: a map of the new
  :library and what else it tells, as podcast-clj's library returns them,
  e.g. the :effects of a step, or nil for a nil `library`. The other
  `opts` go to the library's calls, e.g. its :codec."
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
         :player/step         (let [[event] args]
                                (library/step library event (assoc opts :at at))))))))

(defn changed
  "The `state` with the `change` made to it, with the `opts` of applied:
  its operation applied to the library, and then its change to the UI."
  ([state change]
   (changed state change {}))
  ([state {:keys [operation ui] :or {ui identity}} opts]
   (ui (cond-> state
         operation (update :library #(:library (applied % operation opts)))))))

(comment
  (applied nil [:queue/move ["feed" "item"] 2] {})
  ;; => nil
  #_.)
