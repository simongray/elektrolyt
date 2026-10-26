(ns dk.simongray.elektrolyt.worker-test
  "That the web worker's namespaces leave out the page's: the app's library
  and runtime, reitit's frontend and play-nice's namespaces of the page,
  whose top-level code every start of the worker would run, where there's
  no window or history. The check reads the ns forms of the namespaces
  that the worker's requires reach, from the classpath."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(def page-pattern
  "The names of the page's namespaces."
  (re-pattern (str/join "|" ["^dk\\.simongray\\.elektrolyt\\.(library|runtime)$"
                             "^reitit\\.frontend"
                             "^dk\\.simongray\\.play-nice\\.(page|session|controls|media)"])))

(defn ns-form
  "The ns form of the ClojureScript namespace `sym` on the classpath, as
  ClojureScript reads it, or nil when there's no source, e.g. for the
  Closure Library's."
  [sym]
  (let [path (-> (name sym) (str/replace "-" "_") (str/replace "." "/"))]
    (when-let [resource (some #(io/resource (str path %)) [".cljs" ".cljc"])]
      (with-open [r (java.io.PushbackReader. (io/reader resource))]
        (binding [*default-data-reader-fn* (fn [_ form] form)]
          (read {:read-cond :allow :features #{:cljs} :eof nil} r))))))

(defn requires
  "The namespaces that the :require of the ns `form` names, without npm's
  packages, which it names as strings."
  [form]
  (for [clause (drop 2 form)
        :when  (and (seq? clause) (= :require (first clause)))
        spec   (rest clause)
        :let   [sym (if (sequential? spec) (first spec) spec)]
        :when  (symbol? sym)]
    sym))

(defn reachable
  "The namespaces that the requires of the namespace `root` reach, `root`
  included."
  [root]
  (loop [todo [root] seen #{}]
    (if-let [[sym & more] (seq todo)]
      (if (seen sym)
        (recur more seen)
        (recur (into (vec more) (requires (ns-form sym))) (conj seen sym)))
      seen)))

(deftest the-check-reads-requires
  (is (= '[a.b c.d] (requires '(ns x "Doc." (:require [a.b :as b] c.d ["npm" :as npm])))))
  (is (contains? (reachable 'dk.simongray.elektrolyt.library)
                 'dk.simongray.elektrolyt.runtime)
      "the page's library, which reaches the page's runtime"))

(deftest the-worker-leaves-out-the-page
  (let [reached (reachable 'dk.simongray.elektrolyt.worker)]
    (is (contains? reached 'dk.simongray.elektrolyt.worker.fetching))
    (is (empty? (sort (filter #(re-find page-pattern (name %)) reached))))))
