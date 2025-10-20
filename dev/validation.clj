(ns validation
  (:require
   [clojure.walk :as walk]
   [malli.core :as m]
   [malli.error :as me]
   [malli.json-schema :as mjs]
   [malli.transform :as mt]))

;; Define schema

(def User
  [:map
   [:name [:string {:min 3 :max 50}]]
   [:email [:re {:error/message "Invalid email format"}
            #"^[^\s@]+@[^\s@]+\.[^\s@]+$"]]
   [:age [:int {:min 18 :max 120
                :error/message "Age must be between 18 and 120"}]]])

(def UserWithPermissions
  [:multi {:dispatch :type}
   ["user" [:map
            [:type [:= "user"]]
            [:name {:error/message "name is required"} :string]]]
   ["admin" [:map
             [:type [:= "admin"]]
             [:name {:error/message "the name is required."} :string]
             [:permissions [:vector :string]]]]])

;; Validate and get user-friendly errors

(defn validate-user [data]
  (if (m/validate User data)
    {:valid true :data data}
    {:valid false
     :errors (-> (m/explain User data)
                 (me/humanize))}))

(defn format-collection-errors
  "Formats collection errors to show only invalid items with their indices"
  [errors]
  (walk/postwalk
   (fn [x]
     (if (and (vector? x) (some nil? x))
       ;; Convert [nil nil ["error"] nil ["error"]] 
       ;; to [{:index 2 :errors ["error"]} {:index 4 :errors ["error"]}]
       (vec (keep-indexed
             (fn [idx item]
               (when (some? item)
                 {:index idx :errors item}))
             x))
       x))
   errors))

(defn validate-user-with-permissions [data]
  (if (m/validate UserWithPermissions data)
    {:valid true :data data}
    {:valid false
     :errors (-> (m/explain UserWithPermissions data)
                 me/humanize
                 format-collection-errors
                 )}))

(defn ->json-schema [schema]
  (mjs/transform schema))

(comment
  (validate-user {:name "Jo" :email "Invalid" :age 15})
  (validate-user {:name "Jo" :email "valid.user@email.com" :age 15})
  (validate-user {:name "The" :email "valid.user@email.com" :age 15})
  (validate-user {:name "The" :email "valid.user@email.com" :age 18})

  (->json-schema User)
  (->json-schema UserWithPermissions)

  (validate-user-with-permissions {:type "user"})
  (validate-user-with-permissions {:type "user"
                                   :name "Some name"})

  (validate-user-with-permissions
   {:type "admin"
    :name "Some name"
    :permissions ["user:write"
                  "user:read"
                  1
                  "something:else"
                  2]})
  
  (m/encode :int 42 mt/string-transformer)
  (m/decode :int "42" mt/string-transformer)
  (m/decode :int "invalid" mt/string-transformer)

  ;
  )



;; ========== ZOD EXPERIMENTS =============

;; Zod (TS)
;; z.object({ name: z.string(), age: z.number().min(0) })

;; Malli
[:map
 [:name string?]
 [:age [:int {:min 0}]]]

;; Basic types
[:string] ;; any string
[:int {:min 0 :max 100}] ;; bounded integer
[:enum "active" "inactive"] ;; union/enum
[:maybe :string] ;; string or nil (like z.string().optional())

;; Objects/maps
[:map
 [:id :uuid]
 [:email [:string {:min 1}]]
 [:age [:int {:min 0}]]
 [:status {:optional true} [:enum "active" "inactive"]]]

[:vector :string] ;; string[]
[:sequential :int] ;; any sequential collection of ints
[:set :keyword] ;; Set<keyword>

;; Nested objects
[:map
 [:user [:map
         [:name :string]
         [:email :string]]
  [:posts [:vector [:map
                    [:title :string]
                    [:content :string]]]]]]

;; Union types (like z.union())
[:or :string :int]
[:or
 [:map
  [:type [:= "user"]]
  [:name :string]]
 [:map
  [:type [:= "admin"]]
  [:name :string]
  [:permissions [:vector :string]]]]

;; ========== ZOD EXPERIMENTS =============