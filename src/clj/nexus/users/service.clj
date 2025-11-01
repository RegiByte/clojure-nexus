(ns nexus.users.service
  "User service layer - orchestrates queries and schemas with validation"
  (:require
   [malli.core :as m]
   [malli.error :as me]
   [nexus.auth.hashing :as hashing]
   [nexus.db :as db]
   [nexus.errors :as errors]
   [nexus.shared.maps :as maps]
   [nexus.users.queries :as queries]
   [nexus.users.schemas :as schemas]
   [taoensso.telemere :as tel]))

;; ============================================================================
;; Validation
;; ============================================================================

(defn validate!
  "Validate data against a Malli schema, throw validation error if invalid"
  [schema data]
  (when-let [errors (m/explain schema data)]
    (throw (errors/validation-error
            "Validation failed"
            {:details (me/humanize errors)}))))


;; ============================================================================
;; Query Helpers
;; ============================================================================

(defn find-by-email
  "Find user by email address"
  [{:keys [db]} email]
  (db/exec-one! db (queries/find-by-email-query email)))

(defn find-by-id
  "Find user by ID"
  [{:keys [db]} id]
  (db/exec-one! db (queries/find-by-id-query id)))

;; ============================================================================
;; Service Functions
;; ============================================================================

(defn register-user!
  "Register a new user with hashed password"
  [{:keys [db]} {:keys [first-name last-name middle-name email password] :as data}]
  (validate! schemas/UserRegistration data)
  (tel/log! {:msg  "User registration - validation passed"
             :data (dissoc data :password)})

  (when (find-by-email {:db db} email)
    (throw (errors/conflict
            "Email already registered"
            {:email email})))

  (let [password-hash (hashing/hash-password password)
        query (queries/insert-user-query
               {:first-name    first-name
                :last-name     last-name
                :middle-name   middle-name
                :email         email
                :password-hash password-hash})]
    (db/exec-one! db query)))

(defn authenticate-user
  "Authenticate user and return JWT token"
  [{:keys [db jwt]} {:keys [email password] :as credentials}]
  (validate! schemas/LoginCredentials credentials)

  (if-let [user (db/exec-one! db (queries/find-by-email-query email))]
    (let [sanitized-user (-> user
                             (dissoc :users/password_hash)
                             (maps/unqualify-keys*))]
      (tel/log! {:msg   "User authentication attempt"
                 :email email})
      (if (hashing/verify-password password (:users/password_hash user))
        {:token ((:generate-token jwt)
                 sanitized-user
                 {:claims {:roles ["user"]}})
         ; return original user - non sanitized, http handler should do it
         :user user}
        (throw (errors/unauthorized "Invalid credentials" {:email email}))))
    (throw (errors/unauthorized "Invalid credentials" {:email email}))))


(defn change-password!
  "Change a user's password"
  [{:keys [db]} {:keys [user-id old-password new-password] :as data}]
  (validate! schemas/ChangePassword data)

  (if-let [user (find-by-id {:db db} user-id)]
    (if (hashing/verify-password old-password (:users/password_hash user))
      (let [new-hash (hashing/hash-password new-password)
            query (queries/update-password-query user-id new-hash)]
        (tel/log! {:msg     "Password changed successfully"
                   :data {:user-id user-id}})
        (db/exec-one! db query))
      (throw (errors/unauthorized "Invalid password" {:user-id user-id})))
    (throw (errors/unauthorized "User not found" {:user-id user-id}))))



(defn list-users
  "List users with pagination"
  [{:keys [db]} params]
  (validate! schemas/ListUsersParams params)
  (let [query (queries/list-users-query params)]
    (db/exec! db query)))

(defn update-user!
  "Update user fields"
  [{:keys [db]} id updates]
  (validate! schemas/UpdateUser {:id id :updates updates})
  (when (seq updates)
    (let [query (queries/update-user-query id updates)]
      (tel/log! {:msg     "User updated"
                 :data {:user-id id
                        :fields  (keys updates)}})
      (db/exec-one! db query))))

(defn delete-user!
  "Soft delete a user"
  [{:keys [db]} id]
  (validate! schemas/UserIdParam {:id id})
  (let [query (queries/soft-delete-user-query id)]
    (tel/log! {:msg     "User soft deleted"
               :data {:user-id id}})
    (db/exec-one! db query)))

(defn search
  "Search users by name or email"
  [{:keys [db]} q]
  (validate! schemas/SearchParams {:q q})
  (let [query (queries/search-users-query q)]
    (db/exec! db query)))
