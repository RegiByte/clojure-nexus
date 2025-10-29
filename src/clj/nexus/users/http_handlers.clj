(ns nexus.users.http-handlers
  "HTTP handlers for user management endpoints"
  (:require
   [nexus.auth.middleware :as auth-middleware]
   [nexus.shared.maps :as maps]
   [nexus.users.schemas :as user-schemas]
   [nexus.users.service :as users]))

;; ============================================================================
;; Response Schemas for OpenAPI
;; ============================================================================

(def User
  "User response schema"
  [:map
   [:id :uuid]
   [:email :string]
   [:first-name :string]
   [:last-name :string]
   [:middle-name {:optional true} [:maybe :string]]
   [:created-at inst?]
   [:updated-at inst?]])

(def UserList
  "List of users response schema"
  [:sequential User])

(def AuthResponse
  "Authentication response schema"
  [:map
   [:message :string]
   [:token :string]
   [:user User]])

(def ErrorResponse
  "Error response schema"
  [:map
   [:error :string]
   [:details {:optional true} :any]])

(def SuccessResponse
  "Generic success response"
  [:map
   [:message :string]
   [:user {:optional true} User]])

(def ChangePasswordSuccessResponse
  "Generic success response"
  [:map
   [:message :string]
   [:user {:optional true} [:map
                            [:id user-schemas/UUIDSchema]
                            [:email user-schemas/EmailSchema]]]])


;; ============================================================================
;; Helper Functions
;; ============================================================================


(defn- sanitize-user
  "Remove sensitive fields and unqualify keys from user data"
  [user]
  (-> user
      (dissoc :users/password_hash :password_hash)
      (maps/unqualify-keys*)
      (maps/->kebab-map)))

;; ============================================================================
;; Public Handlers (No Authentication Required)
;; ============================================================================

(defn register-handler
  "Register a new user"
  [request]
  (let [context (:context request)
        user-data (-> request :parameters :body)
        created-user (users/register-user! context user-data)
        sanitized (sanitize-user created-user)]
    {:status 201
     :body {:message "User registered successfully"
            :user sanitized}}))

(defn login-handler
  "Authenticate user and return JWT token"
  [request]
  (let [context (:context request)
        credentials (-> request :parameters :body)
        {:keys [token user]} (users/authenticate-user context credentials)
        sanitized (sanitize-user user)]
    {:status 200
     :body {:message "Logged in successfully"
            :token token
            :user sanitized}}))

;; ============================================================================
;; Protected Handlers (Authentication Required)
;; ============================================================================

(defn list-users-handler
  "List users with pagination"
  [request]
  (auth-middleware/ensure-authenticated! request)
  (let [context (:context request)
        params (-> request :parameters :query (or {}))
        users-list (users/list-users context params)]
    {:status 200
     :body (mapv sanitize-user users-list)}))

(defn get-user-handler
  "Get a user by ID"
  [request]
  (auth-middleware/ensure-authenticated! request)
  (let [context (:context request)
        user-id (-> request :parameters :path :id)
        user (users/find-by-id context user-id)]
    (println "found user" {:user-id user-id
                           :user user})
    (if user
      {:status 200
       :body (sanitize-user user)}
      {:status 404
       :body {:error "User not found"}})))

(defn update-user-handler
  "Update user fields"
  [request]
  (auth-middleware/ensure-authenticated! request)
  (let [context (:context request)
        user-id (-> request :parameters :path :id)
        updates (-> request :parameters :body maps/->snake_map)
        updated-user (users/update-user! context user-id updates)]
    (if updated-user
      {:status 200
       :body {:message "User updated successfully"
              :user (sanitize-user updated-user)}}
      {:status 404
       :body {:error "User not found"}})))

(defn delete-user-handler
  "Soft delete a user"
  [request]
  (auth-middleware/ensure-authenticated! request)
  (let [context (:context request)
        user-id (-> request :parameters :path :id)
        deleted-user (users/delete-user! context user-id)]
    (if deleted-user
      {:status 200
       :body {:message "User deleted successfully"
              :user (sanitize-user deleted-user)}}
      {:status 404
       :body {:error "User not found"}})))

(defn change-password-handler
  "Change a user's password"
  [request]
  (auth-middleware/ensure-authenticated! request)
  (let [context (:context request)
        user-id (-> request :parameters :path :id)
        {:keys [old-password new-password]} (-> request :parameters :body)
        updated-user (users/change-password!
                      context
                      {:user-id user-id
                       :old-password old-password
                       :new-password new-password})]
    {:status 200
     :body {:message "Password changed successfully"
            :user (sanitize-user updated-user)}}))

(defn search-users-handler
  "Search users by name or email"
  [request]
  (auth-middleware/ensure-authenticated! request)
  (let [context (:context request)
        search-term (-> request :parameters :query :q)
        results (users/search context search-term)]
    {:status 200
     :body (mapv sanitize-user results)}))
