(ns nexus.users.service
  (:require
   [integrant.core :as ig]
   [malli.core :as m]
   [malli.error :as me]
   [nexus.db :as db]
   [nexus.service :as service]
   [taoensso.telemere :as tel]
   [nexus.auth.hashing :as hashing]
   [nexus.errors :as errors]
   [nexus.shared.maps :as maps]))

(def EmailSchema [:re #"^[^\s@]+@[^\s@]+\.[^\s@]+$"])

(def UserRegistration
  [:map
   [:first-name [:string {:min 1 :max 100}]]
   [:last-name  [:string {:min 1 :max 100}]]
   [:middle-name {:optional true} [:maybe :string]]
   [:email EmailSchema]
   [:password [:string {:min 8 :max 100}]]])

(def LoginCredentials
  [:map
   [:email {:json-schema/default "test@example.com"} EmailSchema]
   [:password {:json-schema/default "supersecretpassword"} [:string {:min 1}]]])

(defn validate! [schema data]
  (when-let [errors (m/explain schema data)]
    (throw (errors/validation-error
            "Validation failed"
            {:details (me/humanize errors)}))))


(defn find-by-email [{:keys [db]} email]
  (db/exec-one! db {:select [:*] :from :nexus.users :where [:= :email email]}))

(defn register-user!
  "Register a new user with hashed password"
  [{:keys [db]} {:keys [first-name last-name middle-name email password] :as data}]
  (validate! UserRegistration data)
  (tel/log! {:msg "Validation passed"
             :data data})
  (when (find-by-email {:db db} email)
    (throw (errors/conflict
            "Email already registered"
            {:email email})))
  (let [password-hash (hashing/hash-password password)]
    (db/exec-one!
     db
     {:insert-into [:nexus.users]
      :values [{:first_name first-name
                :last_name last-name
                :middle_name middle-name
                :email email
                :password_hash password-hash}]
      :returning [:id :first_name :last_name :middle_name :email :created_at]})))

(defn authenticate-user
  "Authenticate user and return JWT token"
  [{:keys [db jwt]} {:keys [email password] :as credentials}]
  (validate! LoginCredentials credentials)
  (if-let [user (db/exec-one!
                 db
                 {:select [:*]
                  :from :nexus.users
                  :where [:= :email email]})]
    (let [sanitized-user (-> user
                             (dissoc :users/password_hash)
                             (maps/unqualify-keys*))]
      (println {:user sanitized-user})
      (if (hashing/verify-password password (:users/password_hash user))
        {:token ((:generate-token jwt) sanitized-user {:claims {:roles ["admin" "user"]}})
         :user sanitized-user}
        (throw (errors/unauthorized "Invalid credentials" {:email email}))))
    (throw (errors/unauthorized "Invalid credentials" {:email email}))))


(defn find-by-id [{:keys [db]} id]
  (db/exec-one! db {:select [:*] :from :nexus.users :where [:= :id id]}))

(defn change-password!
  "Change a user's password"
  [{:keys [db]} {:keys [user-id old-password new-password]}]
  (when (< (count new-password) 8)
    (throw (errors/validation-error "New password too short"
                                    {:new-password new-password
                                     :min-length 8})))
  (if-let [user (find-by-id {:db db} user-id)]
    (if (hashing/verify-password old-password (:users/password_hash user))
      (let [new-hash (hashing/hash-password new-password)]
        (db/exec-one! db {:update :nexus.users
                          :set {:password_hash new-hash}
                          :where [:= :id user-id]
                          :returning [:id :email]}))
      (throw (errors/unauthorized "Invalid password" {:user-id user-id})))
    (throw (errors/unauthorized "User not found" {:user-id user-id}))))



(defn list-users [{:keys [db]} {:keys [limit offset] :or {limit 50 offset 0}}]
  (db/exec! db {:select [:id
                         :email
                         :first_name
                         :last_name
                         :middle_name
                         :created_at
                         :updated_at] :from :nexus.users :order-by [[:created_at :desc]]
                :limit limit :offset offset}))

(defn update-user! [{:keys [db]} id updates]
  (when (seq updates)
    (db/exec-one! db {:update :nexus.users
                      :set (into {} (map (fn [[k v]] [(keyword (name k)) v]) updates))
                      :where [:= :id id]
                      :returning [:*]})))

(defn delete-user! [{:keys [db]} id]
  (db/exec-one! db {:update :nexus.users
                    :set {:deleted_at [:now]}
                    :where [:= :id id]
                    :returning [:*]}))

(defn search [{:keys [db]} q]
  (db/exec! db {:select [:*] :from :nexus.users
                :where [:or
                        [:ilike :first_name (str "%" q "%")]
                        [:ilike :last_name  (str "%" q "%")]
                        [:ilike :email      (str "%" q "%")]]}))

(def ops
  ;; Service operations, unresolved
  {:register-user! register-user!
   :authenticate-user authenticate-user
   :change-password! change-password!
   :find-by-id   find-by-id
   :find-by-email find-by-email
   :list-users   list-users
   :update-user! update-user!
   :delete-user! delete-user!
   :search       search})


(defmethod ig/init-key :nexus.users/service [_ {:keys [deps]}]
  ;; Builds the user service by providing the necessary deps 
  (let [{:keys [db jwt]} deps]
    (service/build {:db db
                    :jwt jwt} ops)))
