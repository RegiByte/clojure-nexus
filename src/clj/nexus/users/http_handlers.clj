(ns nexus.users.http-handlers
  "HTTP handlers for user management endpoints"
  (:require
   [nexus.auth.middleware :as auth-middleware]
   [nexus.shared.maps :as maps]
   [nexus.users.service :as service]))

;; ============================================================================
;; Note: Response schemas are now defined in nexus.users.schemas.api
;; This keeps the HTTP layer focused on handlers and routing
;; ============================================================================


;; ============================================================================
;; Helper Functions
;; ============================================================================


(defn- sanitize-user
  "Remove sensitive fields and transform keys for API response.
   
   SECURITY: This function prevents password hash leakage!
   - Removes :password_hash / :users/password_hash
   - Even if service layer accidentally includes it, it won't reach client
   
   Transformations:
   1. Remove sensitive fields (password_hash)
   2. Unqualify keywords (:users/email → :email)
   3. Convert to camelCase for frontend (email → email, first_name → firstName)
   
   Why this matters:
   - Defense in depth: Multiple layers prevent password hash exposure
   - API consistency: Frontend always receives camelCase
   - Namespace cleanup: Qualified keywords are internal implementation detail"
  [user]
  (-> user
      (dissoc :users/password_hash :password_hash)
      (maps/unqualify-keys*)
      (maps/->camelCaseMap)))

;; ============================================================================
;; Public Handlers (No Authentication Required)
;; ============================================================================

(defn register-handler
  "Register a new user"
  [request]
  (let [context (:context request)
        domain-data (-> request :parameters :body maps/->kebab-map)
        created-user (service/register-user! context domain-data)
        sanitized (sanitize-user created-user)]
    {:status 201
     :body {:message "User registered successfully"
            :user sanitized}}))

(defn login-handler
  "Authenticate user and return JWT token"
  [request]
  (let [context (:context request)
        credentials (-> request :parameters :body)
        {:keys [token user]} (service/authenticate-user context credentials)
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
        users-list (service/list-users context params)]
    {:status 200
     :body (mapv sanitize-user users-list)}))

(defn get-user-handler
  "Get a user by ID"
  [request]
  (auth-middleware/ensure-authenticated! request)
  (let [context (:context request)
        user-id (-> request :parameters :path :id)
        user (service/find-by-id context user-id)]
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
        _ (println {:msg "Updating user" :data {:user-id user-id :updates updates}})
        updated-user (service/update-user! context user-id updates)]
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
        deleted-user (service/delete-user! context user-id)]
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
        {:keys [old-password new-password]} (-> request :parameters :body maps/->kebab-map)
        updated-user (service/change-password!
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
        results (service/search context search-term)]
    {:status 200
     :body (mapv sanitize-user results)}))
