(ns nexus.test.test-system
  "Test system management with Integrant and testcontainers"
  (:require
   [integrant.core :as ig]
   [nexus.system :as system]
   [nexus.test.containers :as containers]
   [taoensso.telemere :as tel]))

;; =============================================================================
;; Manual Namespace Loading
;; =============================================================================
(defn- load-required-namespaces!
  "Manually loads namespaces needed for testing.
   Including those that contain a ig/init-key call
   
   After loading, we override the production :nexus.db/connection method
   with our test-specific version that accepts a datasource directly."
  []
  ;; Load only what you need for tests
  (require 'nexus.auth.jwt)      ;; JWT service
  (require 'nexus.server)        ;; Server app
  ;; Add other services as needed
  )

;; =============================================================================
;; Test-specific Integrant component implementations
;; =============================================================================
;; These override the production implementations from nexus.db
;; We define them here so we can inject the test database directly
;; IMPORTANT: These must be defined AFTER load-required-namespaces! is called
;; to ensure they override the production methods

(defn- setup-test-db-methods!
  "Defines test-specific Integrant methods for database components.
   
   This must be called after nexus.db is loaded to properly override
   the production methods."
  []
  (defmethod ig/init-key :nexus.db/connection [_ {:keys [datasource]}]
    ;; Returns the DB, as Hydrated by the test settings
    datasource)

  (defmethod ig/halt-key! :nexus.db/connection
    [_ _connection]
    ;; No-op for test DB - containers/with-test-db handles cleanup
    nil))

;; =============================================================================
;; Configuration Loading
;; =============================================================================

(defn- test-config
  "Builds a test-specific Integrant config with the test database.
     
   This loads the base config but overrides the database components
   to use the provided test database instead of the real one."
  [test-db]

  (let [base-config (system/load-config {:config "test.config.edn"
                                         :profile :test})]
    ;; After load-config calls ig/load-namespaces (which loads nexus.db),
    ;; we need to re-establish our test overrides
    (setup-test-db-methods!)
    (-> base-config
        (assoc :nexus.db/connection {:datasource test-db}))))

(defn setup-logging!
  []
  (tel/set-ns-filter! {:disallow ["org.eclipse.jetty.server.*"
                                  ; Testcontainers
                                  "org.testcontainers.images.*"
                                  "org.testcontainers.dockerclient.*"
                                  "org.testcontainers.utility.*"
                                  "org.testcontainers.DockerClientFactory"
                                  "tc.testcontainers.*"
                                  ; Migratus
                                  "migratus.database"]}))

(defn with-system
  "Provides a fully initialized system with a fresh test database.
     
   Flow:
   1. Creates a fresh test database (isolated from other tests)
   2. Manually loads required namespaces (avoiding production DB code)
   3. Initializes the Integrant system with test config
   4. Runs your test callback with the system map
   5. Halts the system (closes connections, etc.)
   6. Drops the test database
   
   Usage:
   (with-system
     (fn [system]
       (let [db (-> system :nexus.db/connection)
             jwt-service (-> system :nexus.auth/jwt)]
         (is (= ...)))))"
  [test-fn]
  ;; Load namespaces once (idempotent)
  (load-required-namespaces!)
  (setup-logging!)
  (containers/with-test-db
    (fn [test-db]
      (tel/set-min-level! :error)
      (let [config (test-config test-db)
            system (ig/init config)]
        (try
          (test-fn system)
          (finally
            (tel/set-min-level! :info)
            (ig/halt! system)))))))

(defn with-system+server
  "Provides a fully initialized system with a fresh test database.
   It includes the jetty server, started on a random port per test
     
   Flow:
   1. Creates a fresh test database (isolated from other tests)
   2. Manually loads required namespaces (avoiding production DB code)
   3. Initializes the Integrant system with test config
   4. Runs your test callback with the system map
   5. Halts the system (closes connections, etc.)
   6. Drops the test database
   
   Usage:
   (with-system
     (fn [system]
       (let [db (-> system :nexus.db/connection)
             jwt-service (-> system :nexus.auth/jwt)]
         (is (= ...)))))"
  [test-fn]
  ;; Load namespaces once (idempotent)
  (load-required-namespaces!)
  (setup-logging!)
  (containers/with-test-db
    (fn [test-db]
      (tel/set-min-level! :error)
      (let [config (test-config test-db)
            config+server (merge config {:nexus.server/server
                                         {:options
                                          {; Started on random port per test
                                           :port 0}
                                          :deps {:app (ig/ref :nexus.server/app)}}})
            system (ig/init config+server)]
        (try
          (test-fn system)
          (finally
            (tel/set-min-level! :info)
            (ig/halt! system)))))))

(comment
  (with-system (fn [system]
                 (println system)))

  (with-system+server (fn [system]
                        (println system)))


  ;
  )