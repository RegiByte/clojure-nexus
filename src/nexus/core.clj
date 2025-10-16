(ns nexus.core
  (:gen-class)
  (:require [nexus.system :as system]
            [migratus.core :as migratus]
            [integrant.core :as ig]
            [taoensso.telemere :as tel] ; Use telemere as default logger
            ))

(defn migrate
  ([]
   (migrate :prod))
  ([profile]
   (when-let [system (system/initialize-migrations profile)]
     (tel/log! :info "Running DB migrations...")
     (migratus/migrate (:nexus.db/migrations system))
     (tel/log! :info "DB migrations completed successfully"))))

(defn initialize-system []
  (tel/log! :info "Initializing system")
  (system/initialize))


(defn -main
  "Starts the system
   If the first argument is 'migrate', 
   it will run the migrations with the prod profile"
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
