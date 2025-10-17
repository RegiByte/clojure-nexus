(ns nexus.server
  (:require
   [integrant.core :as ig]
   [muuntaja.core :as muuntaja-core]
   [nexus.router.core :refer [routes]]
   [reitit.coercion.malli :as malli-coercion]
   [reitit.ring :as ring]
   [reitit.dev.pretty :as pretty]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [jsonista.core :as jsonista]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.cors :as cors]
   [taoensso.telemere :as tel]))

(defn exception-handler [message]
  (fn [exception _request]
    (tel/error! exception message)
    {:status 500
     :body ()}))

(defn not-found-handler [_request]
  {:status 404
   :headers {"Content-Type" "application/json"}
   :body (jsonista/write-value-as-string {:error "Not Found"})})

(defn global-middlewares []
  [;; query-params & form-params
   parameters/parameters-middleware
   ;; content-negotiation
   muuntaja/format-negotiate-middleware
   ;; encoding response body
   muuntaja/format-response-middleware
   ;; error handling
   (exception/create-exception-middleware
    {::exception/default (exception-handler "Unhandled exception")})
   ;; decoding request body
   muuntaja/format-request-middleware
   ;
   ])

(defn cors-config [mode]
  (case mode
    :dev {:origins [#".*"]
          :allow-credentials true}
    :prod {:origins [#"http://example.prod.domain"] ;; Set prod domains
           :allow-credentials true}))

(defn create-handler
  [routes cors-mode]
  (let
   [cors-cfg (cors-config cors-mode)
    router (ring/router routes {:exception pretty/exception
                                :data {:muuntaja muuntaja-core/instance
                                       :coercion malli-coercion/coercion
                                       :middleware (global-middlewares)}})
    handler (ring/ring-handler
             router
             (ring/routes
              (ring/redirect-trailing-slash-handler)
              (ring/create-resource-handler {:path "/static"})
              (ring/create-default-handler {:not-found #'not-found-handler})))

    wrapped-handler (cors/wrap-cors handler
                                    :access-control-allow-origin (:origins cors-cfg)
                                    :access-control-allow-methods [:get :put :post :delete :patch :options]
                                    :access-control-allow-credentials (:allow-credentials cors-cfg)
                                    :access-control-allow-headers ["Content-Type" "Authorization"])]
    {:router router
     :handler wrapped-handler}))

(defn create-root-handler
  [{:keys [hot-reload? cors-mode]} deps]
  (if hot-reload?
    ;; Dev: recompile router on each request for hot reloading
    {:handler (fn dev-handler [request]
                ((:handler (create-handler (#'routes deps) cors-mode))
                 request))
     :get-router (fn get-router ; Return current router for inspection
                   []
                   (:router (create-handler (#'routes deps) cors-mode)))}
    ;; Prod: compile once for performance
    (create-handler (routes deps) cors-mode)))


(defmethod ig/init-key ::app [_ {:keys [options deps]}]
  (tel/log! :info "initializing server handler")
  (create-root-handler options deps))

(defmethod ig/init-key ::server [_ {:keys [options deps]}]
  (tel/log! {:data {:options options}
             :level :info} "initializing server")
  (jetty/run-jetty (-> deps :app :handler) {:join? false
                                            :max-idle-time 30000
                                            :port (:port options)}))

(defmethod ig/halt-key! ::server [_ server]
  (tel/log! :info "stopping server")
  (.stop server))