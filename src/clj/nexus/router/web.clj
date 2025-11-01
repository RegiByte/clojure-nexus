(ns nexus.router.web
  (:require
   [clojure.java.io :as io]
   [jsonista.core :as jsonista]
   [nexus.auth.http-handlers :as auth-handlers]
   [nexus.users.schemas :as user-schemas]))

(defn auth-routes []
  ["/auth" {}
   [["/login" {:name :web-login
               :summary "Signs in a user through cookies"
               :post {:parameters {:body user-schemas/LoginCredentials}
                      :responses {200 {:body auth-handlers/WebAuthResponse}
                                  401 {:body auth-handlers/ErrorResponse}}
                      :handler auth-handlers/login-handler}}]
    ["/logout" {:name :web-logout
                :summary "Removes auth cookie"
                :post {:responses {200 {:body auth-handlers/WebLogoutResponse}}
                       :handler auth-handlers/logout-handler}}]

    ["/me" {:name :web-me
            :summary "Shows info about current logged in user"
            :get {:responses {200 {:body auth-handlers/WebMeResponse}
                              401 {:body auth-handlers/ErrorResponse}}
                  :handler auth-handlers/me-handler}}]]])


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
