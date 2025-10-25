(ns nexus.router.web
  (:require
   [clojure.java.io :as io]
   [jsonista.core :as jsonista]
   [nexus.auth.middleware :as auth-middleware]
   [nexus.shared.maps :as maps]
   [nexus.users.service :as users-service]))

(defn auth-routes []
  ["/auth" {}
   [["/login" {:name :web-login
               :summary "Signs in a user through cookies"
               :post {:parameters {:body users-service/LoginCredentials}
                      :responses {200 {:body [:map
                                              [:message :string]
                                              [:user [:map {:closed false}]]]}}
                      :handler (fn [request]
                                 (try
                                   (let [users-service (-> request :context :users-service)
                                         {:keys [email password]} (-> request :parameters :body)
                                         {:keys [token user]}
                                         ((:authenticate-user users-service)
                                          {:email email
                                           :password password})]
                                     {:status 200
                                      :cookies {"auth-token" {:value token
                                                               :http-only true
                                                               :secure false
                                                               :same-site :lax
                                                               :max-age (* 24 60 60)
                                                               :path "/"}}
                                      :body {:message "Logged in successfully"
                                             :user (maps/unqualify-keys* user)}})
                                   (catch Exception e
                                     ;; Handle auth error
                                     {:status 401
                                      :body {:error (ex-message e)}})))}}]
    ["/logout" {:name :web-logout
                :summary "Removes auth cookie"
                :post {:responses {200 {:body [:map [:message :string]]}}
                       :handler (fn [request]
                                  {:status 200
                                   :cookies {"auth-token" {:value ""
                                                           :max-age 0
                                                           :path "/"}}
                                   :body {:message "Logged out successfully"}})}}]

    ["/me" {:name :web-me
            :summary "Shows info about current logged in user"
            :get {:responses {200 {:body [:map [:user [:map {:closed false}]]]}
                              401 {:body [:map [:error :string]]}}
                  :handler (fn [request]
                             (tap> request)

                             (if (auth-middleware/authenticated? request)
                               {:status 200
                                :body {:user (auth-middleware/req-identity request)}}
                               {:status 401
                                :body {:error "Not authenticated"}}))}}]]])


(defn serve-index-html [_request]
  (println "Serving index page")
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (slurp (io/resource "public/index.html"))})


;; Web specific routes
(defn routes
  " Returns the application web routes, thing acessible to users and such "
  []
  [["/" {:name :homepage
         :get {:handler ; Homepage route 
               serve-index-html}}]
   ["/hello/:name"
    {:name :hello
     :get {:handler (fn [request]
                      (tap> request)
                      {:status 200
                       :headers {"Content-Type" "text/html"
                                 "x-original-params" (jsonista/write-value-as-string (:query-params request))}
                       :body (str "Hey " (-> request :path-params :name) "! how are you?")})}}]


   ["/app/*path" {:get {:handler ; Homepage route 
                        serve-index-html}}]

   (auth-routes)
   ;
   ]
  ; End Web Routes
  )

(comment

  ;
  )