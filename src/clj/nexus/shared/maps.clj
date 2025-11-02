(ns nexus.shared.maps
  "Map key transformation utilities for API boundary conversions.
   
   Why we need this:
   
   1. Database layer: snake_case (SQL convention)
      {:first_name \"John\", :created_at #inst \"...\"}
   
   2. Service layer: kebab-case (Clojure convention)
      {:first-name \"John\", :created-at #inst \"...\"}
   
   3. API layer: camelCase (JavaScript/JSON convention)
      {:firstName \"John\", :createdAt \"...\"}
   
   Transformation flow:
   - Incoming request: camelCase → kebab-case (API → service)
   - Database writes: kebab-case → snake_case (service → DB)
   - Database reads: snake_case → kebab-case (automatic via jdbc options)
   - Outgoing response: kebab-case → camelCase (service → API)
   
   Note: unqualify-keys* is used to remove namespace qualifiers
   from qualified keywords (:users/email → :email) before sending
   to frontend."
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [clojure.walk :as walk]))

(defn unqualify-keys*
  "Recursively transforms all qualified keywords to unqualified.
   Useful for API responses while keeping internal data qualified."
  [data]
  (walk/postwalk
   (fn [x]
     (cond
       (map? x)
       (into {} (map (fn [[k v]]
                       [(if (qualified-keyword? k)
                          (keyword (name k))
                          k)
                        v])
                     x))

       :else x))
   data))

(defn ->kebab-map
  "Transforms keyword casing into kebab-case."
  [data]
  (cske/transform-keys csk/->kebab-case-keyword data))

(defn ->snake_map
  "Transforms keyword casing into snake_case."
  [data]
  (cske/transform-keys csk/->snake_case_keyword data))

(defn ->camelCaseMap
  "Transforms keyword casing into snake_case."
  [data]
  (cske/transform-keys csk/->camelCaseKeyword data))


(comment
  (def user #:users{:id 1,
                    :email "regi+2@test.com",
                    :first_name "Reginaldo",
                    :last_name "Junior",
                    :middle_name "Adriano",
                    :accounts #:accounts {:id 1
                                          :name "main account"}
                    :created_at #inst "2025-10-25T15:08:05.428603000-00:00",
                    :updated_at #inst "2025-10-25T15:08:05.428603000-00:00"})

  user
  (unqualify-keys* user)

  (->kebab-map {:foo_bar "broo"
                :this {:is_awesome :yes!
                       :how {:manyLevels
                             {:does_this {:goes :bro?}}}}})

  (->snake_map {:foo-bar "broo",
                :this {:is-awesome :yes!,
                       :how {:many-levels
                             {:does-this {:goes :bro?}}}}})

  (->camelCaseMap {:foo-bar "broo",
                   :this {:is_awesome :yes!,
                          :how {:many-levels
                                {:does-this {:goes_on :bro?}}}}})


  ;
  )