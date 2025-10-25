(ns nexus.router.api
  (:require
   [clojure.java.io :as io]
   [nexus.auth.middleware :as auth-middleware]
   [nexus.shared.maps :as maps]
   [nexus.users.service :as users-service]
   [reitit.openapi :as openapi]
   [reitit.ring.malli :as malli]
   [nexus.errors :as errors]))

(defn secure-route []
  ["/secure"
   {:tags #{"secure"}
    :openapi {:security [{"auth" []}]}
    :swagger {:security [{"auth" []}]}}

   ["/get"
    {:get {:summary "endpoint authenticated with a header"
           :responses {200 {:body [:map [:secret :string]]}
                       401 {:body [:map [:error :string]]}}
           :handler (fn [request]
                      ;; In a real app authentication would be handled by middleware
                      (if (= "secret" (get-in request [:headers "x-api-key"]))
                        {:status 200
                         :body {:secret "I am a marmot"}}
                        {:status 401
                         :body {:error "unauthorized"}}))}}]])

(defn auth-routes []
  ["/auth"
   {:tags #{"auth"}}
   [["/login" {:name :api-login
               :summary "Signs in a user and returns a JWT token"
               :post {:parameters {:body users-service/LoginCredentials}
                      :responses {200 {:body [:map
                                              [:message :string]
                                              [:token :string]
                                              [:user [:map {:closed false}]]]}
                                  401 {:body [:map [:error :string]]}}
                      :handler (fn [request]
                                 (try
                                   (let [users-service (-> request :context :users-service)
                                         {:keys [email password]} (-> request :parameters :body)
                                         {:keys [token user]}
                                         ((:authenticate-user users-service)
                                          {:email email
                                           :password password})]
                                     {:status 200
                                      :body {:message "Logged in successfully"
                                             :token token
                                             :user (maps/unqualify-keys* user)}})
                                   (catch Exception e
                                     {:status 401
                                      :body {:error (ex-message e)}})))}}]

    ["/identity" {:name :api-auth-identity
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
                       :headers {"Content-Type" "application/json"}
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

(defn example-routes []
  [(secure-route)
   (math-routes)
   (file-routes)])

(defn routes
  "Main delegator for API routes"
  []
  [["/health" {:name :api/health
               :get {:summary "Pings the server and check if everything is okay"
                     :handler (fn [_request]
                                (tap> _request)
                                {:status 200
                                 :body {:message "Healthy!"}})}}]
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

   ;
   ])
