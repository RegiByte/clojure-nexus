(ns nexus.router.web
  (:require
   [clojure.java.io :as io]
   [jsonista.core :as jsonista]))


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
   ;
   ]
  ; End Web Routes
  )