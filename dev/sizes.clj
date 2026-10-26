(ns sizes
  "The sizes of the app's release bundles, against the budgets in
  dev/sizes.edn.

  Both functions build a release of the :app build into a snapshot of its
  own, which leaves public/js and target/release alone, and write
  shadow-cljs's report of what's in each module to
  target/build-report.html. Locally, run them in the nREPL of the JVM that
  runs the watch, never in a shadow-cljs CLI beside it. CI runs
  clojure -M:cljs clj-run sizes/check!"
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [shadow.cljs.build-report :as build-report]
            [shadow.cljs.devtools.api :as shadow]))

(def budgets-file
  "Each module's budget in bytes, before and after gzip."
  "dev/sizes.edn")

(defn report!
  "Build a release of :app, write its report and return each module's size
  in bytes, as a map of module id to its :js and :gzip sizes."
  []
  (build-report/generate (shadow/get-build-config :app)
                         {:report-file "target/build-report.html"
                          :data-file   "target/build-report.edn"
                          :print-table true})
  (->> (slurp "target/build-report.edn")
       (edn/read-string {:default tagged-literal})
       :build-modules
       (into (sorted-map)
             (map (fn [{:keys [module-id js-size gzip-size]}]
                    [module-id {:js js-size :gzip gzip-size}])))))

(defn budgets
  "Budgets for the `sizes` that report! returns, 5% above them."
  [sizes]
  (update-vals sizes #(update-vals % (fn [n] (long (Math/ceil (* n 1.05)))))))

(defn over-budget
  "The `sizes` above their `budgets`, or without one, each as a vector of
  module id, key, size and budget."
  [budgets sizes]
  (for [[module m] sizes
        [k n]      m
        :let       [budget (get-in budgets [module k])]
        :when      (not (and budget (<= n budget)))]
    [module k n budget]))

(defn check!
  "Report the sizes of the release bundles, and throw where one is over its
  budget."
  []
  (let [sizes (report!)
        over  (over-budget (edn/read-string (slurp budgets-file)) sizes)]
    (pprint/pprint sizes)
    (when (seq over)
      (throw (ex-info (str "Over budget: " (pr-str over)) {:over over})))))

(comment
  (check!)
  (budgets (report!))
  #_.)
