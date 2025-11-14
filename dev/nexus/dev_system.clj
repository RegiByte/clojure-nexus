(ns nexus.dev-system
  (:require
   [integrant.repl :as ig-repl]
   [integrant.repl.state :as ig-state]
   [migratus.core :as migratus]
   [nexus.system :as system]))


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
(defn services:jwt
  []
  (:nexus.auth/jwt (system)))
(defn services:stream
  "Returns the stream service for managing SSE/WebSocket connections"
  []
  (:nexus.router.realtime.service/service (system)))
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
  (services:jwt)
  (services:stream)
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
