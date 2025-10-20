(ns nexus.router.core
  (:require
   [jsonista.core :as jsonista]
   [clojure.java.io :as io]
   [nexus.http.request :as req]))


(defn reply [msg]
  (fn [_request]
    (tap> _request)
    (let [url (req/named-url _request :hello {:name "param"} {:query "params"
                                                              :right "here"})]
      {:status 200
       :headers {"Content-Type" "text/html"}
       :body (str msg "<br/>" (or url "no url"))})))

(defn api-routes []
  [["/health" {:name :api/health
               :get {:handler (reply "Healthy bro!!!")}}]
   ["/hello" {:get {:handler (reply "Hello from API")}}]
   ["/crash" {:get {:handler (fn [_request]
                               (throw (ex-info "Purposely crashed from route" {:exception-info :nothing?}))
                               {:status 200
                                :body {:message "Should crash bro"}})}}]
   ;
   ])

(defn serve-index-html [_request]
  (println "Serving index page")
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (slurp (io/resource "public/index.html"))})

(defn web-routes
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


(defn routes [_deps]
  [["/api", (api-routes)]
   ["", (web-routes)]
   ;
   ])