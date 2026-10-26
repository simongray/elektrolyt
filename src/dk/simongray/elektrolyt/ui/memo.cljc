(ns dk.simongray.elektrolyt.ui.memo
  "Remembering the results of slow functions whose arguments seldom change
  between renders, e.g. a view of the library, or the thumbnails of a
  row's artwork.")

(defn same?
  "Whether the arguments `a` and `b` count as the same: identical
  collections, which are cheap to check, or equal values of another kind."
  [a b]
  (or (identical? a b)
      (and (not (coll? a)) (= a b))))

(defn same-entries?
  "Whether the maps `a` and `b` have the same keys, each with a value that
  counts as the same by same?, so that a map made anew of the same values
  counts as the same."
  [a b]
  (and (= (count a) (count b))
       (every? (fn [[k v]] (and (contains? b k) (same? v (get b k)))) a)))

(defn remember-last
  "A version of `f` that returns its last result again while `key-fn`
  returns the same vector for the arguments as last time, compared value
  by value, or by `same-key?` of the new key and the last. Without
  `key-fn`, the arguments themselves count."
  ([f]
   (remember-last f vector))
  ([f key-fn]
   (remember-last f key-fn #(and (= (count %1) (count %2))
                                 (every? true? (map same? %1 %2)))))
  ([f key-fn same-key?]
   (let [remembered (atom nil)]
     (fn [& args]
       (let [k             (apply key-fn args)
             [last-k made] @remembered]
         (if (and last-k (same-key? k last-k))
           made
           (let [made (apply f args)]
             (reset! remembered [k made])
             made)))))))

(defn remember-recent
  "A version of `f` that keeps its results for the last `n` keys that
  `key-fn` gives for the arguments, compared as remember-last compares
  them, e.g. for the pages that the listener goes back and forth between.
  The one used longest ago goes first."
  [n f key-fn]
  (let [remembered (atom [])]
    (fn [& args]
      (let [k     (apply key-fn args)
            hit?  (fn [[kept-k]] (and (= (count k) (count kept-k))
                                      (every? true? (map same? k kept-k))))
            kept  @remembered
            entry (some #(when (hit? %) %) kept)]
        (if entry
          (do (when-not (identical? entry (peek kept))
                (swap! remembered #(conj (filterv (complement #{entry}) %) entry)))
              (second entry))
          ;; a copy rather than a subvec, which is a view of the whole
          ;; vector, so that conj onto it would keep every result
          (let [made (apply f args)]
            (swap! remembered #(conj (cond->> % (<= n (count %)) (into [] (drop 1)))
                                     [k made]))
            made))))))

;; A JS Map finds a string by its value, but a ClojureScript collection
;; only by its identity, so the keys are strings
(defn remember-each
  "A version of `f`, a function of a string, that keeps its result for
  each of the last `n` strings, 2,000 unless given, the first one kept
  going first, e.g. for what each row of a list derives from a URL or a
  guid at every render."
  ([f]
   (remember-each f 2000))
  ([f n]
   #?(:clj  (let [^java.util.Map kept (proxy [java.util.LinkedHashMap] []
                                        (removeEldestEntry [_]
                                          (< n (.size ^java.util.Map this))))]
              (fn [s]
                (locking kept
                  (if (.containsKey kept s)
                    (.get kept s)
                    (let [made (f s)]
                      (.put kept s made)
                      made)))))
      :cljs (let [kept (js/Map.)]
              (fn [s]
                (if (.has kept s)
                  (.get kept s)
                  (let [made (f s)]
                    (.set kept s made)
                    (when (< n (.-size kept))
                      (.delete kept (.-value (.next (.keys kept)))))
                    made)))))))
