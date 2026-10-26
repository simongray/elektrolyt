(ns dk.simongray.elektrolyt.ui.memo
  "Remembering the last result of a slow function whose arguments seldom
  change between renders, e.g. a view of the library.")

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
