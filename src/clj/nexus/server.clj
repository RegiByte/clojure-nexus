(ns nexus.server
  (:require
   [clojure.stacktrace :as stacktrace]
   [clojure.string :as str]
   [integrant.core :as ig]
   [jsonista.core :as jsonista]
   [malli.util :as mu]
   [muuntaja.core :as muuntaja-core]
   [nexus.auth.middleware :as auth-middleware]
   [nexus.errors :as errors]
   [nexus.router.core :refer [routes]]
   [reitit.coercion.malli :as malli-coercion]
   [reitit.dev.pretty :as pretty]
   [reitit.openapi :as openapi]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.swagger-ui :as swagger-ui]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.content-type :as content-type]
   [ring.middleware.cookies :as ring-cookies]
   [ring.middleware.cors :as cors]
   [ring.middleware.default-charset :as default-charset]
   [ring.middleware.x-headers :as x-headers]
   [taoensso.telemere :as tel]))


;; Error Handling
;; ==============

(defn format-stack-trace
  "Converts an exception's stack trace to a string.
     
   Useful for logging and debugging - gives you the full error context."
  [exception]
  (with-out-str
    (stacktrace/print-stack-trace exception)))

(defn exception-handler
  "Creates a Ring middleware exception handler.
   
   Why this is important:
   - Prevents uncaught exceptions from crashing your server
   - Logs all errors for debugging
   - Returns user-friendly error responses
   - In dev mode, includes stack traces for debugging
   - In prod mode, hides implementation details for security
   
   Parameters:
   - message: Error message to show users
   - options: {:show-error-stacks? boolean} - whether to include stack traces"
  [message {:keys [show-error-stacks?]}]
  (fn [exception request]
    (tel/error! exception message)
    (-> {:status 500
         :body {:message message
                :uri (:uri request)
                :method (-> (:request-method request) name str/upper-case)}}

        ;; In dev we show stack traces to help with debugging
        ((fn [response]
           (if show-error-stacks?
             (-> response
                 (assoc-in [:body :stack-trace] (format-stack-trace exception))
                 (assoc-in [:body :exception] (.getClass exception))
                 (assoc-in [:body :data] (ex-data exception)))
             response))))))

(defn known-exception-handler
  "Creates a Ring middleware exception handler.
   
   Why this is important:
   - Prevents uncaught exceptions from crashing your server
   - Logs all errors for debugging
   - Returns user-friendly error responses
   - In dev mode, includes stack traces for debugging
   - In prod mode, hides implementation details for security
   
   Parameters:
   - message: Error message to show users
   - options: {:show-error-stacks? boolean} - whether to include stack traces"
  [fallback-message]
  (fn [exception _request]
    (let [{:keys [status data details]} (ex-data exception)]
      (-> {:status (or status
                       500)
           :body (cond-> {:error (or (ex-message exception) fallback-message)}
                   data (assoc :data data)
                   details (assoc :details details))}))))

(defn not-found-handler
  "Handles 404 Not Found responses.
   
   Called when no route matches the request path."
  [_request]
  {:status 404
   :headers {"Content-Type" "application/json"}
   :body (jsonista/write-value-as-string {:error "Not Found"})})

(defn wrap-context-deps
  "Add app dependencies of handler to request as a context key."
  [handler context]
  (fn [request]
    (-> request
        (assoc :context context)
        (handler))))

(defn global-middlewares
  "Defines the middleware stack applied to all routes.
   
   Middleware are functions that wrap your handlers, adding functionality.
   They execute in order from top to bottom for requests,
   and bottom to top for responses.
   
   This stack provides:
   1. Parameter parsing (query strings, form data)
   2. Content negotiation (Accept headers)
   3. Response formatting (JSON, EDN, etc.)
   4. Exception handling (catches and formats errors)
   5. Request body parsing (JSON -> Clojure data)
   
   Why this order matters:
   - Parse params first so they're available to other middleware
   - Handle exceptions in the middle to catch errors from both sides
   - Format response last so all responses are consistent"
  [options]
  [;; debug
   ;;  [(fn [handler]
   ;;     (fn [request]
   ;;       (let [response (handler request)]
   ;;         (tap> {:response response
   ;;                :request request})
   ;;         response)))]
   ;; prevent resources with invalid media types being loaded as stylesheets or scripts
   [x-headers/wrap-content-type-options :nosniff]
   ;; Only allow embedding iframes of same-origin
   ;; Prevent click hijacking
   [x-headers/wrap-frame-options :sameorigin]
   content-type/wrap-content-type
   [default-charset/wrap-default-charset "utf-8"]
   ring-cookies/wrap-cookies
   [wrap-context-deps (:deps options)]
   ;; JWT Auth - Cookie & Header support
   [auth-middleware/wrap-jwt-authentication]
   ;; openapi
   openapi/openapi-feature
   ;; query-params & form-params
   parameters/parameters-middleware
   ;; content-negotiation
   muuntaja/format-negotiate-middleware
   ;; encoding response body
   muuntaja/format-response-middleware
   ;; error handling
   (exception/create-exception-middleware
    (merge exception/default-handlers
           {::exception/default (exception-handler "Unhandled exception" options)
            ::errors/unauthorized (known-exception-handler "Failed to authenticate")
            ::errors/conflict (known-exception-handler "Conflict")
            ::errors/validation (known-exception-handler "Validation failedd")
            ::errors/not-found (known-exception-handler "Not found")}))
   ;; decoding request body
   muuntaja/format-request-middleware
   ;; coercing response bodys
   coercion/coerce-response-middleware
   ;; coercing request parameters
   coercion/coerce-request-middleware
   ;; multipart
   multipart/multipart-middleware])



(defn cors-config
  "Configures Cross-Origin Resource Sharing (CORS).
   
   CORS is needed when your frontend and backend are on different domains.
   
   Why different configs for dev/prod?
   - Dev: Allow all origins (#\".*\") for easy local development
   - Prod: Restrict to specific domains for security
   
   Parameters:
   - mode: :dev or :prod"
  [mode]
  (case mode
    :dev {:origins [#".*"] ; Allow all origins in development
          :allow-credentials false}
    :prod {:origins [#"http://example.prod.domain"] ; Restrict in production
           :allow-credentials false}))

;; Router & Handler Creation
;; ==========================

(defn create-handler
  "Creates the main Ring handler with routing and middleware.
   
   This is the heart of the web server. It:
   1. Creates a Reitit router from route definitions
   2. Wraps routes with middleware
   3. Adds fallback handlers (404, static files, etc.)
   4. Wraps everything with CORS
   
   Returns: {:router router-instance :handler ring-handler-fn}
   
   Parameters:
   - routes: Route definition vector
   - cors-mode: :dev or :prod
   - show-error-stacks?: Whether to include stack traces in errors"
  [{:keys [routes deps cors-mode show-error-stacks?]}]
  (let
   [cors-cfg (cors-config cors-mode)
    ;; Create the router with data-driven middleware
    router (ring/router
            routes
            {:exception pretty/exception
             :data {:muuntaja muuntaja-core/instance
                    :coercion (reitit.coercion.malli/create
                               {;; set of keys to include in error messages
                                :error-keys #{#_:type
                                              :in
                                              :value
                                              :errors
                                              :humanized
                                              #_:transformed}
                                ;; schema identity function (default: close all map schemas)
                                :compile mu/closed-schema
                                ;; strip-extra-keys (affects only predefined transformers)
                                :strip-extra-keys true
                                ;; add/set default values
                                :default-values true
                                ;; malli options
                                :options nil})
                    :middleware (global-middlewares {:show-error-stacks? show-error-stacks?
                                                     :deps deps})}})
    ;; Create the ring handler with fallbacks
    handler (ring/ring-handler
             router
             (ring/routes
              (ring/redirect-trailing-slash-handler)  ; /path/ -> /path
              (ring/create-resource-handler {:root "public"
                                             :path "/static"}) ; Serve files from resources/public at /static
              (swagger-ui/create-swagger-ui-handler {:path "/api/docs"
                                                     :config {:validatorUrl nil
                                                              :urls [{:name "Main API" :url "/api/openapi.json"}]
                                                              :urls.primaryName "Main API"
                                                              :operationSorter "alpha"}})
              (ring/create-default-handler {:not-found #'not-found-handler})))

    ;; CORS must wrap the entire ring handler, instead of being a reitit middleware
    ;; This is because CORS needs to handle OPTIONS preflight requests before routing
    wrapped-handler (cors/wrap-cors handler
                                    :access-control-allow-origin (:origins cors-cfg)
                                    :access-control-allow-methods [:get :post :put :delete :patch :options]
                                    :access-control-allow-credentials (str (:allow-credentials cors-cfg))
                                    :access-control-allow-headers ["Content-Type" "Authorization"])]
    {:router router
     :handler wrapped-handler}))

(defn create-root-handler
  "Creates the root handler with optional hot-reloading.
   
   Hot-reloading (dev mode):
   - Routes are recompiled on every request
   - You can change route handlers without restarting the server
   - Uses var quotes (#') to get latest function definitions
   
   Production mode:
   - Routes compiled once at startup for performance
   - No runtime overhead
   
   Parameters:
   - options: {:hot-reload? bool, :show-error-stacks? bool, :cors-mode keyword}
   - deps: Dependencies injected by Integrant (database, services, etc.)"
  [{:keys [hot-reload? show-error-stacks? cors-mode]} deps]
  (let [handler-params {:deps deps
                        :cors-mode cors-mode
                        :show-error-stacks? show-error-stacks?}]
    (if hot-reload?
      ;; Dev: recompile router on each request for hot reloading
      {:handler (fn dev-handler [request]
                  ((:handler (create-handler (merge handler-params {:routes (#'routes)})))
                   request))
       :get-router (fn get-router ; Return current router for inspection
                     []
                     (:router (create-handler (merge handler-params {:routes (#'routes)}))))}
      ;; Prod: compile once for performance
      (create-handler (merge handler-params {:routes (routes)})))))

(defmethod ig/init-key ::app [_ {:keys [options deps]}]
  "Initializes the application handler component.
   
   This is called by Integrant when starting :nexus.server/app.
   It creates the Ring handler that processes HTTP requests."
  (tel/log! :info "initializing server handler")
  (println {:deps deps
            :options options})
  (create-root-handler options deps))

(defmethod ig/init-key ::server [_ {:keys [options deps]}]
  "Initializes the Jetty web server component.
   
   This is called by Integrant when starting :nexus.server/server.
   It starts the actual HTTP server listening on the configured port.
   
   Parameters from config:
   - :port - Which port to listen on
   - :join? false - Don't block the thread (allows REPL interaction)
   - :max-idle-time - Close idle connections after 30 seconds"
  (tel/log! {:data {:options options}
             :level :info} "initializing server")
  (jetty/run-jetty (-> deps :app :handler) {:join? false
                                            :max-idle-time 30000
                                            :host "0.0.0.0"
                                            :port (:port options)}))

(defmethod ig/halt-key! ::server [_ server]
  "Stops the Jetty web server component.
   
   This is called by Integrant when stopping :nexus.server/server.
   Ensures graceful shutdown of the HTTP server."
  (tel/log! :info "stopping server")
  (.stop server))