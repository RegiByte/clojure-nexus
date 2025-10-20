(ns nexus.core
  (:gen-class) ; Required for creating a Java main class for uberjar deployment
  (:require [nexus.system :as system]
            [migratus.core :as migratus]
            [integrant.core :as ig]
            [taoensso.telemere :as tel] ; Use telemere as default logger
            ))


;; Database Migration Functions
;; ============================
;; Migrations keep your database schema in sync with your code
;; They're versioned SQL files that can be applied (up) or rolled back (down)
;; See resources/migrations folder

(defn migrate
  "Runs database migrations to update the database schema.
     
     Migrations are important because:
     - They version control your database schema
     - They can be run in any environment (dev, staging, prod)
     - They're idempotent (safe to run multiple times)
     - Paralel work: devs can work simultaneously with many db schemas
     
     Parameters:
     - profile: :dev or :prod (defaults to :prod for safety)"
  ([]
   (migrate :prod))
  ([profile]
   (when-let [system (system/initialize-migrations profile)]
     (tel/log! :info "Running DB migrations...")
     (migratus/migrate (:nexus.db/migrations system))
     (tel/log! :info "DB migrations completed successfully"))))

(defn initialize-system
  "Initializes the entire application system using Integrant.
     
     This loads the system configuration and starts all components:
     - Database connections
     - HTTP server
     - Any other services defined in system.config.edn"
  []
  (tel/log! :info "Initializing system")
  (system/initialize))


(defn -main
  "The main entry point when running the application.
    
    Usage:
    - `lein run` - Starts the web server
    - `lein run migrate` - Runs database migrations
    
    Features:
    - Graceful shutdown: Registers a JVM shutdown hook to cleanly stop the system
    - Error handling: Catches startup errors and exits with code 1
    
    Arguments:
    - args: Command line arguments. If first arg is 'migrate', runs migrations instead"
  [& args]
  (if (= "migrate" (first args))
    (migrate)
    (try (let [system (initialize-system)]
           (tel/log! :info "System initialized")
           (.addShutdownHook
            (Runtime/getRuntime)
            (Thread. #(do
                        (tel/log! :info "Shutting down system...")
                        (ig/halt! system)
                        (tel/log! :info "System shut down successfully")))))
         (catch Exception e
           (tel/error! e "Error happened during system startup")
           (System/exit 1)))))
