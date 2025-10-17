(ns user
  (:require [nexus.system :as system]
            [integrant.repl :as ig-repl]
            [integrant.repl.state :as ig-state]
            [migratus.core :as migratus]
            [reitit.core :as reitit]
            [taoensso.telemere :as tel] ; Use telemery as default logger
            [next.jdbc :as jdbc]))


(do
  ;; REPL Setup, side effectful things
  (ig-repl/set-prep! #(system/prepare {:config "system.config.edn" :profile :dev}))

  (tel/log! "REPL loaded, happy hacking!!"))


;; Integrant repl manages the system in dev 
(defn start [] (ig-repl/go))
(defn stop [] (ig-repl/halt))
(defn restart [] (ig-repl/reset))
(defn restart-all [] (ig-repl/reset-all))

;; Acessors
(defn system
  "The running system configuration"
  []
  ig-state/system)
(defn system:config
  "The current system configuration used by Integrant"
  []
  ig-state/config)
(defn connection
  "Returns the database connection"
  []
  (:nexus.db/connection (system)))
(defn migrations
  "Returns the database migrations"
  []
  (:nexus.db/migrations (system)))
(defn server:handler
  "Returns the server handler"
  []
  (-> (:nexus.server/app (system)) :handler))
(defn server:router
  []
  (let [router-fn (-> (system) :nexus.server/app :get-router)]
    (when router-fn
      (router-fn))))
;; Acessors




;; Load and prepare the config
(defn test-config []
  (system/prepare {:config "system.config.edn" :profile :dev}))

;; Migratus - Database migration management
(defn migrate []
  (migratus/migrate (migrations)))
(defn reset-migrations []
  (migratus/reset (migrations)))
(defn rollback []
  (migratus/rollback (migrations)))
(defn create-migration [name]
  (migratus/create (migrations) name))
(defn pending-migrations []
  (migratus/pending-list (migrations)))


(comment
  ;; Lifecycle management
  (start)
  (stop)
  (restart)
  ;;/ Lifecycle management
  )

(comment
  ;; Common acessors
  (system)
  (system:config)
  (connection)
  (migrations)
  (server:handler)
  (server:router)
  ;; Common acessors
  )

(comment
  ;; Database migrations
  (create-migration "add-dummy-table")
  ; Up
  (migrate)
  ; Down
  (rollback)
  ; Not migrated yet
  (pending-migrations)
  ; (down all -> up all)
  (reset-migrations)
  ;; Database migrations
  )

(comment
  ;; Experiments
  ; Poke the handler with a raw "request map"
  (server:handler)
  ((server:handler) {:ring.request/headers {}
                     :request-method :get
                     :uri "/hello/amandinha!"
                     :query-params {:foo "bar"}})

  ; Load config and respective namespaces
  (test-config)

  ; Tap tap! See portal namespace
  (tap> "hi from user ns")

  ; Log some data with telemere
  (tel/log! {:id ::testing-log
             :data {:foo "bar!"}} "My message")


  (jdbc/execute! (connection) ["SELECT 1 AS id"])
  (jdbc/execute! (connection) ["SELECT 1 AS id"])

  ;; List all tables in nexus schema
  (jdbc/execute! (connection)
                 ["SELECT tablename FROM pg_tables WHERE schemaname = ?" "nexus"])

  ;; List all tables except system and public ones
  (jdbc/execute! (connection)
                 ["SELECT schemaname, tablename 
      FROM pg_tables 
      WHERE schemaname NOT IN ('pg_catalog', 'information_schema', 'public')
      ORDER BY schemaname, tablename"])

  ;; Get route from router
  (server:router)
  (reitit/match-by-path (server:router) "/api/health")
  ;; Experiments
  )