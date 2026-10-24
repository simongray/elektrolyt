(ns dk.simongray.elektrolyt.phosphor
  "Writing the app's icons, ui/icons.cljc, from Phosphor's npm package,
  @phosphor-icons/core: a def for the path of each icon in one weight, and
  in other weights for the few that need them, e.g. a filled weight for an
  icon that fills in to show a state, so that a release build keeps only
  the icons that the views use."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]))

(def package
  "The folder of Phosphor's npm package, whose version package.json pins."
  "node_modules/@phosphor-icons/core")

(def target
  "The file of the app's namespace of icons."
  "src/dk/simongray/elektrolyt/ui/icons.cljc")

(defn version
  "The version of Phosphor in the npm package."
  []
  (second (re-find #"\"version\": \"([^\"]+)\""
                   (slurp (io/file package "package.json")))))

(defn icon-name
  "The name of the icon in the `file` of `weight`, e.g. play for
  play-light.svg, since the file names of every weight but Regular end in
  the weight's name."
  [weight file]
  (str/replace (.getName (io/file file))
               (re-pattern (str "(-" weight ")?\\.svg$"))
               ""))

;; A few icons of the Fill weight, e.g. stack-simple, are drawn in pieces,
;; a path each, which join into one path that draws the same
(defn path
  "The path in the text of an icon's `svg` file, which must hold paths on
  a grid of 256 by 256 and nothing else, since the app's icon alias draws
  one path on that grid."
  [svg]
  (if-let [[_ paths] (re-matches #"(?s)<svg [^>]*viewBox=\"0 0 256 256\"[^>]*>((?:<path d=\"[^\"]+\"/>)+)</svg>\s*"
                                 svg)]
    (str/join " " (map second (re-seq #"<path d=\"([^\"]+)\"/>" paths)))
    (throw (ex-info "Not an svg of paths on a grid of 256" {:svg svg}))))

(defn icons
  "The path of each icon of the `weight`, e.g. \"light\", by its name."
  [weight]
  (into (sorted-map)
        (for [file (.listFiles (io/file package "assets" weight))]
          [(icon-name weight file) (path (slurp file))])))

(defn defined-name
  "The name that the top-level `form` defines, e.g. \"list\" for (defn list
  ...), or nil."
  [form]
  (when (and (seq? form)
             (symbol? (first form))
             (str/starts-with? (name (first form)) "def")
             (symbol? (second form)))
    (name (second form))))

;; Only the names matter, so a tagged literal reads as its form, and a
;; keyword of an alias, e.g. ::ana/numeric, as the alias's own
(defn defined-names
  "The names that the source file `resource` on the classpath defines at
  its top level, reading the parts of the JVM's reader conditionals."
  [resource]
  (with-open [r (reader-types/indexing-push-back-reader
                 (io/reader (io/resource resource)))]
    (binding [reader/*default-data-reader-fn* (fn [_ form] form)
              reader/*alias-map*              identity]
      (->> (repeatedly #(reader/read {:eof ::eof :read-cond :allow} r))
           (take-while #(not= ::eof %))
           (keep defined-name)
           set))))

(defn cljs-core-names
  "The names of cljs.core's functions and macros, from the ClojureScript
  that shadow-cljs brings."
  []
  (into (defined-names "cljs/core.cljs") (defined-names "cljs/core.cljc")))

;; A def of a name that clojure.core or cljs.core has would replace the
;; core var, with a warning on each build
(defn clashes
  "The `names` that clojure.core or cljs.core has a var of."
  [names]
  (let [core (into (set (map name (keys (ns-publics 'clojure.core))))
                   (cljs-core-names))]
    (filter core names)))

(defn licence
  "Phosphor's licence from the npm package, as lines of comment."
  []
  (->> (str/split-lines (slurp (io/file package "LICENSE")))
       (map #(str/trimr (str ";; " %)))
       (str/join "\n")))

(defn weights
  "The names of the `others` weights in a sentence, e.g. \"the Fill and
  Thin weights\"."
  [others]
  (let [names (map str/capitalize others)]
    (str "the "
         (if (next names)
           (str (str/join ", " (butlast names)) " and " (last names) " weights")
           (str (first names) " weight")))))

(defn source
  "The source of the namespace of the `icons` in `weight`, and in the
  `others` weights under names that end in theirs, which excludes the
  names `excluded` from clojure.core."
  [weight others icons excluded]
  (let [weight-name (str/capitalize weight)]
    (str ";; Phosphor Icons " (version) ", the " weight-name " weight and " (weights others) ",\n"
         ";; from the npm package @phosphor-icons/core. dk.simongray.elektrolyt.phosphor\n"
         ";; writes this file, so change that rather than this.\n"
         ";;\n"
         (licence) "\n"
         "(ns dk.simongray.elektrolyt.ui.icons\n"
         "  \"Phosphor's icons in the " weight-name " weight, and in " (weights others) "\n"
         "  under names that end in theirs: a def for the path of each, named as\n"
         "  Phosphor names it, e.g. play, gear-six or bookmark-simple-" (first others) ".\n"
         "\n"
         "  The icon alias of ui.controls draws a path in the colour of the text\n"
         "  around it. A release build keeps only the icons that the views use.\n"
         "  Phosphor's site shows them all: https://phosphoricons.com\"\n"
         "  (:refer-clojure :exclude [" (str/join " " excluded) "]))\n"
         "\n"
         (str/join (for [[k d] icons]
                     (str "(def " k " " (pr-str d) ")\n"))))))

;; The icons of the other weights keep the names of their files, e.g.
;; star-fill, since Phosphor ends the file of every weight but Regular in
;; the weight's name
(defn write-icons!
  "Write the icons of the `weight`, e.g. \"light\", and of the `others`
  weights, e.g. \"fill\", under names that end in theirs, to the app's
  namespace of icons, and return how many there are."
  [weight & others]
  (let [icons (apply merge (icons weight)
                     (for [other others]
                       (update-keys (icons other) #(str % "-" other))))]
    (spit target (source weight others icons (clashes (keys icons))))
    (count icons)))

(comment
  ;; the weight that Simon chose over Regular, from a scene of both, Fill
  ;; for e.g. the bookmark of a subscription, and Thin for the bookmark of
  ;; a show to subscribe to
  (write-icons! "light" "fill" "thin")
  ;; => 4536
  (clashes (keys (icons "light")))
  #_.)
