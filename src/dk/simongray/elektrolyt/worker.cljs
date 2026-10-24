(ns dk.simongray.elektrolyt.worker
  "The web worker of the app, which fetches the feeds that a refresh is
  due to fetch and the feed of a Show page off the page, as
  dk.simongray.elektrolyt.worker.fetching has it, so that parsing a feed
  never holds the page up.

  A web worker has no DOMParser, so it parses with that of
  @xmldom/xmldom."
  (:require ["@xmldom/xmldom" :as xmldom]
            [dk.simongray.elektrolyt.runtime.log :as log]
            [dk.simongray.elektrolyt.worker.fetching :as fetching]))

(defn throwing
  "A function that makes a parser of the class `DOMParser`, e.g. that of
  xmldom, which throws on the errors that a browser's parser reports as a
  parsererror element."
  [DOMParser]
  (fn []
    (DOMParser. #js {:onError (fn [level message _context]
                                (when-not (= level "warning")
                                  (throw (js/Error. message))))})))

(defn ^:export init
  "Start the worker's logs, and answer the page's requests to fetch feeds."
  []
  (log/start! js/self)
  (when-not (exists? js/DOMParser)
    (set! (.-DOMParser js/globalThis) (throwing xmldom/DOMParser)))
  (fetching/serve!))
