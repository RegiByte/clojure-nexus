(ns nexus.auth.http-handlers
  "HTTP handlers for web authentication endpoints (cookie-based)"
  (:require
   [nexus.auth.middleware :as auth-middleware]
   [nexus.shared.maps :as maps]
   [nexus.shared.strings :refer [str->uuid]]
   [nexus.users.service :as users]))

;; ============================================================================
;; Response Schemas for OpenAPI
;; ============================================================================

(def WebAuthResponse
  "Web authentication response schema (cookie-based)"
  [:map
   [:message :string]
   [:user [:map {:closed false}]]])

(def WebLogoutResponse
  "Web logout response schema"
  [:map
   [:message :string]])

(def WebMeResponse
  "Current user info response schema"
  [:map
   [:user [:map {:closed false}]]])

(def ErrorResponse
  "Error response schema"
  [:map
   [:error :string]])

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn- sanitize-user
  "Remove sensitive fields and unqualify keys from user data"
  [user]
  (-> user
      (dissoc :users/password_hash :password_hash)
      (dissoc :users/deleted_at)
      (maps/unqualify-keys*)))

;; ============================================================================
;; Web Auth Handlers (Cookie-based authentication)
;; ============================================================================

(defn login-handler
  "Authenticate user and set HTTP-only auth cookie"
  [request]
  (let [context (:context request)
        {:keys [email password]} (-> request :parameters :body)
        {:keys [token user]} (users/authenticate-user
                              context
                              {:email email
                               :password password})
        sanitized (sanitize-user user)]
    {:status 200
     :cookies {"auth-token" {:value token
                             :http-only true
                             :secure false ; Set to true in production with HTTPS
                             :same-site :lax
                             :max-age (* 24 60 60) ; 24 hours
                             :path "/"}}
     :body {:message "Logged in successfully"
            :user sanitized}}))

(defn logout-handler
  "Clear auth cookie to log user out"
  [_request]
  {:status 200
   :cookies {"auth-token" {:value ""
                           :max-age 0
                           :path "/"}}
   :body {:message "Logged out successfully"}})

(defn me-handler
  "Get current authenticated user info from cookie"
  [request]
  (if-let [identity (auth-middleware/req-identity request)]
    (if-let [user-id (str->uuid (:user-id identity))]
      (let [db (-> request :context :db) 
            user (users/find-by-id {:db db} user-id)
            sanitized (sanitize-user user)]
        {:status 200
         :body {:user sanitized}})
      {:status 401
       :body {:error "Authenticated entity must be a user"}})
    {:status 401
     :body {:error "Not authenticated"}}))
