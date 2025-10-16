(ns nexus.server
  (:require
   [integrant.core :as ig]
   [reitit.ring :as ring]
   [ring.adapter.jetty :as jetty]
   [taoensso.telemere :as tel]))

(defn routes [deps]
  [["/" {:get {:handler (fn [request]
                          (tap> request)
                          {:status 200
                           :headers {"Content-Type" "text/html"}
                           :body "Hello, World From homepage!!!!"})}} ; Homepage route
    ]
   ["/hello/:name" {:get {:handler (fn [request]
                                     (tap> request)
                                     {:status 200
                                      :headers {"Content-Type" "text/html"}
                                      :body (str "Hey " (-> request :path-params :name) "! how are you?")})}}]
   ;
   ])



(defn not-found-handler [_request]
  {:status 404 :body "Not Found bro!"})

(defn create-handler
  [routes]
  (ring/ring-handler
   (ring/router routes {})
   (ring/routes
    (ring/redirect-trailing-slash-handler)
    (ring/create-resource-handler {:path "/"})
    (ring/create-default-handler {:not-found #'not-found-handler}))))

(defn create-root-handler
  [deps options]
  (if (:hot-reload? options)
    ;; Dev: recompile router on each request for hot reloading
    (fn dev-handler [request]
      (let [handler (create-handler (#'routes deps))]
        (handler request)))
    ;; Prod: compile once for performance
    (create-handler (routes deps))))

(defmethod ig/init-key ::handler [_ {:keys [options deps]}]
  (tel/log! :info "initializing server handler")
  (create-root-handler deps options))

(defmethod ig/init-key ::server [_ {:keys [options deps] :as context}]
  (tel/log! {:data {:context context}
             :level :info} "initializing server")
  (jetty/run-jetty (:handler deps) {:join? false
                                    :port (:port options)}))

(defmethod ig/halt-key! ::server [_ server]
  (tel/log! :info "stopping server")
  (.stop server))