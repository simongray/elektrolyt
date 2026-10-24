(ns dk.simongray.elektrolyt.worker
  "The app's web worker, which fetches feeds so that parsing them never
  holds the page up. A web worker has no DOMParser, so it parses with
  @xmldom/xmldom's."
  (:require ["@xmldom/xmldom" :as xmldom]
            [dk.simongray.elektrolyt.runtime.log :as log]
            [dk.simongray.elektrolyt.worker.fetching :as fetching]))

(defn throwing
  "A function that makes a parser of the class `DOMParser`, e.g. xmldom's,
  that throws on the errors which a browser's parser reports as a
  parsererror element."
  [DOMParser]
  (fn []
    (DOMParser. #js {:onError (fn [level message _context]
                                (when-not (= level "warning")
                                  (throw (js/Error. message))))})))

(defn ^:export init
  "Start logging in the worker and answer the page's requests to fetch
  feeds."
  []
  (log/start! js/self)
  (when-not (exists? js/DOMParser)
    (set! (.-DOMParser js/globalThis) (throwing xmldom/DOMParser)))
  (fetching/serve!))
