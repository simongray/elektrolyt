(ns dk.simongray.podcast-app.worker
  "The web worker of the app, which refreshes the library and fetches the
  feeds of the Show page off the page, as podcast-clj's app.worker does
  it, so that parsing a feed never holds the page up.

  It writes the library as a replica of the device of its own, through the
  store and the config of the page. A web worker has no DOMParser, so it
  parses with that of @xmldom/xmldom."
  (:require ["@xmldom/xmldom" :as xmldom]
            [dk.simongray.podcast.app.worker :as worker]
            [dk.simongray.podcast-app.library :as app-library]))

(defn- parser
  "An xmldom DOMParser that throws on the errors that a browser's parser
  reports as a parsererror element."
  []
  (xmldom/DOMParser. #js {:onError (fn [level message _context]
                                     (when-not (= level "warning")
                                       (throw (js/Error. message))))}))

(defn ^:export init
  "Answer the page's requests to refresh the library and fetch feeds."
  []
  (worker/serve! {:store  app-library/store
                  :config app-library/config
                  :parser parser}))
