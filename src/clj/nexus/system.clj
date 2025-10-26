(ns nexus.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [taoensso.telemere :as tel]))


;; Aero Configuration Readers
;; ===========================
;; Aero is a configuration library that reads EDN files with special tags
;; These custom readers teach Aero how to handle Integrant references

(defmethod aero/reader 'ig/ref
  [_ _ value]
  "Handles #ig/ref tags in config files.
     
   Example: #ig/ref :nexus.db/connection
   This creates a reference to another component, enabling dependency injection.
   Integrant will ensure the referenced component is initialized first."
  (ig/ref value))

(defmethod aero/reader 'ig/refset
  [_ _ value]
  "Handles #ig/refset tags for one-to-many relationships.
       
   Example: If multiple components need to register with a single component,
   refset collects all of them into a set."
  (ig/refset value))


(defn load-config
  "Loads and parses the system configuration file.
     
   Why this matters:
     - Different environments (dev/prod) need different settings
     - Aero's #profile tag lets you specify environment-specific values
     - The #include tag lets you keep secrets in separate files
   
   Parameters:
     - config: Path to config file (e.g., 'system.config.edn')
     - profile: :dev or :prod
     
     Returns: Parsed configuration map
     Throws: Exception if environment file is missing"
  [{:keys [config profile]}]
  (let [config (aero/read-config (io/resource config) {:profile profile})]
    (if (get-in config [::env :aero/missing-include])
      (throw
       (ex-info (str "Missing env file for profile: " profile
                     "\nExpected file: resources/envs/" (name profile) ".edn") {}))

      config)))

(defn load-namespaces
  "Loads all namespaces referenced in the config.
     
   Why: Integrant needs the namespaces loaded to find the init/halt methods.
   For example, if config has :nexus.server/app, it loads nexus.server namespace."
  [config]
  (ig/load-namespaces config)
  config)

(defn prepare
  "Prepares the system by loading config and required namespaces.
   
   This is a pipeline that:
   1. Loads the EDN configuration file
   2. Loads all namespaces that define components"
  [config]
  (-> config
      load-config
      load-namespaces))

;; System Initialization
;; =====================

(defn initialize
  "Initializes the complete application system.
   
   This is the main function that:
   1. Loads configuration for the specified environment
   2. Loads all required namespaces
   3. Starts all components in dependency order
   
   Integrant handles the dependency graph automatically based on #ig/ref tags.
   
   Parameters:
   - profile: :dev or :prod (defaults to :prod)
   - config-file: Path to config (defaults to 'system.config.edn')
   
   Returns: Running system map (keep this to halt the system later)"
  ([]
   (initialize :prod))
  ([profile]
   (initialize profile "system.config.edn"))
  ([profile config-file]
   (tel/log! {} (str "Bootstrapping system in " profile))
   (-> {:config config-file :profile profile}
       prepare
       ig/init)))

(defn initialize-migrations
  "Initializes only the database migration component.
   
   Why separate from full initialization?
   - Migrations should run before the app starts
   - You might run migrations in a separate deployment step
   - Doesn't start the web server, just sets up DB connection for migrations"
  ([]
   (initialize-migrations :prod))
  ([profile]
   (-> {:config "system.config.edn" :profile profile}
       prepare
       (ig/init [; only init this component
                 ; integrant will load the dependency graph
                 :nexus.db/migrations]))))

;; Component Lifecycle Methods
;; ===========================

(defmethod ig/init-key ::env [_ env]
  ; forward the config, loads from env file based on profile
  ; this is almost never used directly
  env)

(comment
  (load-config {:config "system.config.edn" :profile :dev})
  (load-config {:config "system.config.edn" :profile :prod}))

  ;
  