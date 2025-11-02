(ns nexus.router.api
  (:require
   [clojure.java.io :as io]
   [nexus.auth.middleware :as auth-middleware]
   [nexus.users.http-handlers :as user-handlers]
   [nexus.users.schemas.api :as api-schemas]
   [reitit.openapi :as openapi]
   [reitit.ring.malli :as malli]))

(defn auth-routes []
  ["/auth"
   {:tags #{"auth"}}
   [["/identity" {:name :api-auth-identity
                  :summary "Gets auth info from Authorization header JWT"
                  :get {:responses {200 {:body [:map [:identity [:map {:closed false}]]]}
                                    401 {:body [:map [:error :string]]}}
                        :openapi {:security [{"bearerAuth" {}}]}
                        :handler (fn [request]
                                   (auth-middleware/ensure-authenticated! request)
                                   {:status 200
                                    :body {:identity (auth-middleware/req-identity request)}})}}]]])

(defn math-routes []
  ["/math"
   {:tags #{"math"}}

   ["/plus"
    {:get {:summary "plus with malli query parameters"
           :parameters {:query [:map
                                [:x
                                 {:title "X parameter"
                                  :description "Description for X parameter"
                                  :json-schema/default 42}
                                 int?]
                                [:y {:title "Y paramater"
                                     :description "These attributes are not required"} int?]]}
           :responses {200 {:body [:map [:total int?]]}}
           :handler (fn [{{{:keys [x y]} :query} :parameters}]
                      {:status 200
                       :body {:total (+ x y)}})}

     :post {:summary "plus with malli body parameters"
            :parameters {:body [:map
                                [:x
                                 {:title "X parameter"
                                  :description "Description for X parameter"
                                  :json-schema/default 42}
                                 int?]
                                [:y int?]]}
            :responses {200 {:body [:map [:total int?]]}}
            :handler (fn [{{{:keys [x y]} :body} :parameters}]
                       {:status 200
                        :body {:total (+ x y)}})}}]])

(defn file-routes []
  ["/files"
   {:tags #{"files"}}

   ["/upload"
    {:post {:summary "upload a file"
            :parameters {:multipart [:map [:file malli/temp-file-part]]}
            :responses {200 {:body [:map [:name string?] [:size int?]]}}
            :handler (fn [{{{:keys [file]} :multipart} :parameters :as request}]
                       (tap> request)
                       ;; Create uploads directory if it doesn't exist
                       (let [upload-dir (io/file "uploads")
                             _ (.mkdirs upload-dir)
                             unique-id (str (java.util.UUID/randomUUID))
                             original-filename (:filename file)
                             ;; create destination file
                             dest-file (io/file upload-dir (str unique-id "--" original-filename))
                             ;; Copy the temp file to the destination
                             _ (io/copy (:tempfile file) dest-file)]
                         {:status 200
                          :body {:name (:filename file)
                                 :size (:size file)}}))}}]

   ["/download"
    {:get {:summary "downloads a file"
           :swagger {:produces ["image/png"]}
           :responses {200 {:description "an image"
                            :content {"image/png" {:schema string?}}}}
           :handler (fn [_]
                      {:status 200
                       :headers {"Content-Type" "image/png"}
                       :body (-> "public/favicon-32x32.png"
                                 (io/resource)
                                 (io/input-stream))})}}]])

(defn user-routes []
  ["/users"
   {:tags #{"users"}}

   ;; Public endpoints
   ["/register"
    {:conflicting true
     :name :api/users-register
     :post {:summary "Register a new user"
            :description "Creates a new user account with the provided information"
            :parameters {:body api-schemas/UserRegistration}
            :responses {201 {:body api-schemas/UserResponse
                             :description "User successfully registered"}
                        400 {:body api-schemas/ErrorResponse
                             :description "Validation error"}
                        409 {:body api-schemas/ErrorResponse
                             :description "Email already registered"}}
            :handler user-handlers/register-handler}}]

   ["/login"
    {:conflicting true
     :name :api/users-login
     :post {:summary "Login with email and password"
            :description "Authenticates a user and returns a JWT token"
            :parameters {:body api-schemas/LoginCredentials}
            :responses {200 {:body api-schemas/AuthResponse
                             :description "Successfully authenticated"}
                        401 {:body api-schemas/ErrorResponse
                             :description "Invalid credentials"}}
            :handler user-handlers/login-handler}}]

   ;; Protected endpoints (require authentication)
   [""
    {:openapi {:security [{"bearerAuth" {}}]}}

    [""
     {:name :api/users-list
      :get {:summary "List all users"
            :description "Returns a paginated list of users"
            :parameters {:query api-schemas/ListUsersParams}
            :responses {200 {:body api-schemas/UserList
                             :description "List of users"}
                        401 {:body api-schemas/ErrorResponse
                             :description "Unauthorized"}}
            :handler user-handlers/list-users-handler}}]

    ["/search"
     {:conflicting true
      :name :api/users-search
      :get {:summary "Search users"
            :description "Search users by name or email"
            :parameters {:query api-schemas/SearchParams}
            :responses {200 {:body api-schemas/UserList
                             :description "Search results"}
                        401 {:body api-schemas/ErrorResponse
                             :description "Unauthorized"}}
            :handler user-handlers/search-users-handler}}]

    ["/:id"
     {:conflicting true
      :parameters {:path api-schemas/UserIdParam}}

     [""
      {:get {:name :api/users-get
             :summary "Get user by ID"
             :description "Returns a single user by their ID"
             :responses {200 {:body api-schemas/User
                              :description "User found"}
                         401 {:body api-schemas/ErrorResponse
                              :description "Unauthorized"}
                         404 {:body api-schemas/ErrorResponse
                              :description "User not found"}}
             :handler user-handlers/get-user-handler}
       :patch {:name :api/users-update
               :summary "Update user"
               :description "Updates user information"
               :parameters {:body api-schemas/UpdateUserRequestBody
                            :path api-schemas/UpdateUserRequestParameters}
               :responses {200 {:body api-schemas/UserResponse
                                :description "User updated"}
                           401 {:body api-schemas/ErrorResponse
                                :description "Unauthorized"}
                           404 {:body api-schemas/ErrorResponse
                                :description "User not found"}}
               :handler user-handlers/update-user-handler}
       :delete {:name :api/users-delete
                :summary "Delete user"
                :description "Soft deletes a user"
                :responses {200 {:body api-schemas/UserResponse
                                 :description "User deleted"}
                            401 {:body api-schemas/ErrorResponse
                                 :description "Unauthorized"}
                            404 {:body api-schemas/ErrorResponse
                                 :description "User not found"}}
                :handler user-handlers/delete-user-handler}}]

     ["/change-password"
      {:name :api/users-change-password
       :post {:summary "Change user password"
              :description "Changes the password for a user"
              :parameters {:body api-schemas/ChangePasswordRequest}
              :responses {200 {:body api-schemas/ChangePasswordResponse
                               :description "Password changed"}
                          401 {:body api-schemas/ErrorResponse
                               :description "Unauthorized or invalid password"}
                          404 {:body api-schemas/ErrorResponse
                               :description "User not found"}}
              :handler user-handlers/change-password-handler}}]]]])

(defn example-routes []
  [(math-routes)
   (file-routes)])

(defn routes
  "Main delegator for API routes"
  []
  [["/health" {:name :api/health
               :get {:summary "Pings the server and check if everything is okay"
                     :responses {200 {:body [:map [:message string?] [:status string?]]}}
                     :handler (fn [_request]
                                {:status 200
                                 :body {:message "Healthy!"
                                        :status "ok"}})}}]
   ["/crash" {:get {:no-doc true
                    :handler (fn [_request]
                               (throw (ex-info "Purposely crashed from route" {:exception-info :nothing?}))
                               {:status 200
                                :body {:message "Should crash bro"}})}}]

   ["/openapi.json", {:get
                      {:no-doc true
                       :openapi {:info {:title "Nexus API"
                                        :description "A showcase of how to build a production-ready web server in clojure"
                                        :version "0.0.1"}
                                 :components {:securitySchemes {"bearerAuth" {:type :http
                                                                              :scheme "bearer"
                                                                              :bearerFormat "JWT"}}}}
                       :handler (openapi/create-openapi-handler)}}]

   ["/examples", (example-routes)]

   (auth-routes)

   (user-routes)

   ;
   ])
