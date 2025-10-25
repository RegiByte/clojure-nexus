(ns nexus.shared.maps
  (:require
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


(comment
  (def user #:users{:id 1,
                    :email "regi+2@test.com",
                    :first_name "Reginaldo",
                    :last_name "Junior",
                    :middle_name "Adriano",
                    :accounts #:accounts {
                                          :id 1
                                          :name "main account"
                    }
                    :created_at #inst "2025-10-25T15:08:05.428603000-00:00",
                    :updated_at #inst "2025-10-25T15:08:05.428603000-00:00"})
  
  user
  (unqualify-keys* user)
  
  ;
  )