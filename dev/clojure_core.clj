(ns clojure-core)

;; Testing clojure core functions

(comment
  ; Vectors
  [1 2 3 "hi" :asdf [:a 1]]
  ; Sets
  #{1 2 3}
  ; Maps
  {"key" "value"
   :a 1
   [1 2 3] 2}

  ; Lists
  '(1 2 3)
  (list 1 2 3 4)
  ; Sequences
  (seq [1 2 3 4])
  (type (seq [1 2 3 4]))
  (seq nil)
  (seq [])

  (when (seq [1 2 3])
    :not-empty)
  (when (not (seq []))
    :empty)

  ; Get
  (get [1 2 3] 0)
  (get [1 2 3] 4 :default)
  (get {:a 1} :a)
  (get {:a 1} :b :default)

  ;; Get in
  (get-in {:a {:b {:c 1}}} [:a :b :c])
  (get-in {:a {:b {:c 1}}} [:a :b :c :d] :default)

  ;; Assoc
  (assoc {:a 1} :b 2)
  (assoc {:a 1} :b 2 :c 3)

  ;; Merge
  ; Works on top-level, 
  (merge {:a 1}
         {:b 2}
         {:c 3})
  ; merges left-to-right
  (merge {:a 1}
         {:b 2}
         {:c 3}
         {:a 5})
  ; only merges maps at the top level
  (merge {:a 1 :d {:x 1}}
         {:b 2}
         {:c 3}
         {:a 5 :d {:y 2}})
  (merge-with merge
              {:d {:x 1}}
              {:b 2}
              {:c 3}
              {:a 5 :d {:y 2}})

  ; Sum
  (+ 1 2 3 4)
  (apply + [1 2 3])
  ; First 
  (first [1 2 3])
  ; Second
  (second [1 2 3])
  ; Nth
  (nth [1 2 3] 2)
  ; Rest
  (rest [1 2 3])
  ; Last
  (last [1 2 3])
  ; Butlast
  (butlast [1 2 3])
  ; Take
  (take 2 [1 2 3 4])
  ; Drop
  (drop 2 [1 2 3 4 5])
  (drop-last 2 [1 2 3 4 5])
  ;; Flatten
  (flatten [1 2 3 [4 5 [6 [7 [8]]]]])
  ;; Map
  (map #(+ 2 %) [1 2 3])
  (map + [1 2 3] [4 5 6])
  ;; Map indexed
  (map-indexed
   (fn [index item] {:index index :value item})
   [1 2 3])
  ;; Mapv returns vector
  (mapv + [1 2 3] [4 5 6])
  ;; Mapcat
  (map (fn [value] [value value value]) [1 2 3])
  (mapcat (fn [value] [value (+ value 1) value]) [1 2 3])
  ;; Filter
  (filter pos? [1 -1 0 3 -5])
  (filterv pos? [1 -1 0 3 -5])
  ;; Remove
  (remove pos? [1 -1 0 3 -5])

  ;; Keep (remove nils)
  (->> [1 2 3]
       (map (fn [value]
              (if (= value 2) nil value)))
       (remove nil?))
  (->> [1 2 3]
       (keep (fn [value]
               (if (= value 2) nil value))))
  (->> [1 2 3]
       (keep-indexed (fn [index value]
                       (if (= value 2) nil {:index index :value value}))))

  ;; Into
  (into #{} [1 2 3 4 1])
  (into {} [[:a 1] [:b 2]])

  ;; Group by
  (group-by pos? [1 2 3 -1 0 -3])
  (group-by (fn [value]
              (cond
                (zero? value) :neutral
                (pos? value) :positive
                (neg? value) :negative))
            [1 2 3 -1 0 -3 5 -8 0.00 2])

  (group-by :type [{:type 1 :value 1}
                   {:type 2 :value 2}
                   {:type 1 :value "some other"}])

  ;; Frequencies (count how many times each item appears, return map with key->n-appearences)
  (frequencies [1 2 3 4 1 2 2 2 5 6 4])
  (frequencies [{:value 1} {:value 1} {:value 2} {:value 2} {:value 5}])
  (get-in
   (frequencies [{:value 1} {:value 1} {:value 2} {:value 2} {:value 5}])
   [{:value 5}] ; using map as key
   )

  ;; Partition
  (partition 2 [1 2 3 4])
  (partition 2 1 [1 2 3 4 5 6])
  ;; Partition all
  (partition-all 2 [1 2 3 4 5])
  ;; Shuffle
  (shuffle [1 2 3 4 5 6])

  ;; Reduce
  (reduce + [1 2 3])
  (reduce (fn [result item]
            (+ result item)) [1 2 3])

  ;; Update values
  (update-vals {:a 1
                :b 2
                :c 2} inc)
  ;; Update keys
  (update-keys {"s" 1
                "t" 2
                "e" 3} keyword)

  ;; Sort
  (sort [1 2 5 -2 8 10 85 20])
  (reverse (sort [1 2 5 -2 8 10 85 20]))
  (reverse [1 2 5 -2 8 10 85 20])

  ;; Sort-by
  (sort-by :id [{:id 2}
                {:id 1}
                {:id 20}
                {:id 3}
                {:id -20}])

  ;; Interleave
  (interleave [1 2 3] ["a" "b" "c"] [:a :b :c])
  ;; Interpose
  (interpose "sep" [1 2 3])
  (repeat 10 2)
  (repeatedly 10 (fn [] "something"))
  ;; Range
  (take 10 (range))
  (range 0 50)
  ;; Cycle
  (take 50 (cycle [1 2 3 :pause]))
  ;; Distinct
  (distinct [1 2 3 4 1 2 4 5 6 2 3])
  ;; Select keys
  (select-keys {:a 1
                :b 2
                :c 3} [:a :c])

  ;; Juxt
  ((juxt #(->> (:a %)
               (apply max))
         #(->> (:a %)
               (apply min)) 
         #(->> (:b %)
               (apply max))
         #(->> (:b %)
               (apply min))) {:a [1 2 3]
                                 :b [4 5 6]})



  (apply max [1 2 3])
  ; 



  ;
  )