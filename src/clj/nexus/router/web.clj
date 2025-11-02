(ns nexus.router.web
  (:require
   [clojure.java.io :as io]
   [nexus.auth.http-handlers :as auth-handlers]
   [nexus.users.schemas.api :as api-schemas]))

(defn auth-routes []
  ["/auth" {}
   [["/login" {:name :web-login
               :summary "Signs in a user through cookies"
               :post {:parameters {:body api-schemas/LoginCredentials}
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
  [(auth-routes)

   ["/" {:name :homepage
         :get {:handler ; Homepage route 
               serve-index-html}}]
   ;
   ])
