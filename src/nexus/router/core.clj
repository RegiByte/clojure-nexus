(ns nexus.router.core
  (:require
   [jsonista.core :as jsonista]))

(defn reply [msg]
  (fn [_request]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body msg}))

(defn api-routes []
  [["/health" {:get {:handler (reply "Healthy bro!!!")}}]
   ["/hello" {:get {:handler (reply "Hello from API")}}]
   ["/crash" {:get {:handler (fn [_request]
                               (throw (ex-info "Purposely crashed from route" {:exception-info :nothing?}))
                               {:status 200
                                :body {:message "Should crash bro"}})}}]
   ;
   ])

(defn web-routes
  " Returns the application web routes, thing acessible to users and such "
  []
  [["/" {:name :homepage
         :get {:handler ; Homepage route 
               (reply " Hello From homepage.")}}]
   ["/hello/:name"
    {:name :hello
     :get {:handler (fn [request]
                      (tap> request)
                      {:status 200
                       :headers {"Content-Type" "text/html"
                                 :x-original-params (jsonista/write-value-as-string (:query-params request))}
                       :body (str "Hey " (-> request :path-params :name) "! how are you?")})}}]]
  ; End Web Routes
  )


(defn routes [_deps]
  [["", (web-routes)]
   ["/api", (api-routes)]
   ;
   ])