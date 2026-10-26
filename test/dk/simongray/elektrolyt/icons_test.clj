(ns dk.simongray.elektrolyt.icons-test
  "That the icons' namespace, which dk.simongray.elektrolyt.phosphor writes,
  holds the icons that the code uses and no others. An icon that it lacks
  fails clj-kondo's lint too."
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.test :refer [deftest is]]))

(defn defined
  "The names of the icons that the icons' namespace defines."
  []
  (->> (slurp "src/dk/simongray/elektrolyt/ui/icons.cljc")
       (re-seq #"(?m)^\(def ([a-z0-9-]+) ")
       (map second)
       set))

(defn used
  "The names of the icons that the code in src, dev and test uses."
  []
  (set (for [folder ["src" "dev" "test"]
             file   (file-seq (io/file folder))
             :when  (re-find #"\.clj[cs]?$" (.getName ^java.io.File file))
             [_ k]  (re-seq #"(?<![\w@/-])icons/([a-z0-9-]+)" (slurp file))]
         k)))

(deftest only-the-icons-that-the-code-uses
  (let [defined (defined)
        used    (used)]
    (is (seq defined))
    (is (empty? (set/difference defined used))
        "an icon that the code no longer uses, which write-icons! drops")
    (is (empty? (set/difference used defined))
        "an icon that the code uses, which write-icons! adds")))
