(ns dk.simongray.podcast-app.background
  "What the page asks the app's web worker, dk.simongray.podcast-app.worker,
  to do: refresh the library, and fetch a feed for the Show page.

  The worker starts with the first request, and answers in the codec of
  the library's config, Transit, since EDN reads slower than the feed
  parses."
  (:require [dk.simongray.podcast.app.worker :as worker]
            [dk.simongray.podcast-app.library :as app-library]))

(defonce ^{:doc "The client of the app's web worker, made at its first use."}
  client
  (delay (worker/client "/js/worker.js" app-library/config)))

(defn refresh!
  "A promise of the refresh of the library of `device` in the worker, with
  the `opts` of library/refresh! that are data, calling `on-progress` with
  the :done and :total of each fetch that ends, as worker/refresh! gives
  it."
  [device opts on-progress]
  (worker/refresh! @client device opts on-progress))

(defn fetch!
  "A promise of the feed at `url`, fetched and parsed in the worker with
  the `opts` of podcast/fetch! that are data, as podcast/fetch! gives it
  but without the :body."
  [url opts]
  (.then (worker/fetch! @client url opts)
         (fn [{:keys [failed] :as result}]
           (if failed
             (throw (js/Error. failed))
             result))))
