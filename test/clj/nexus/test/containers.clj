(ns nexus.test.containers
  (:require
   [clojure.java.shell :as shell]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [clojure.string :as str])
  (:import
   (org.testcontainers.containers PostgreSQLContainer)
   (org.testcontainers.containers.wait.strategy Wait)
   (org.testcontainers.utility DockerImageName)))


(defonce ^:private postgres-container (atom nil))

(defn ^:private start-pg-test-container
  "Starts a PostgreSQL container using Testcontainers.
   
   The container:
   - Runs Postgres 16 in Docker
   - Uses a random available port (no conflicts)
   - Waits for Postgres to be ready before returning
   - Returns the container instance for querying connection details"
  []
  (let [container (PostgreSQLContainer.
                   (-> (DockerImageName/parse "postgres")
                       (.withTag "16")))]
    (.start container)
    ;; Wait for Postgres to be ready to accept connections
    (.waitingFor container (Wait/forListeningPort))
    container))


(defn ^:private stop-pg-container
  []
  (let [container @postgres-container]
    (when container
      (PostgreSQLContainer/.close container)
      (reset! postgres-container nil))))

;; Lazy container initialization - starts only once per test suite.
;; 
;; Using delay ensures:
;; - Container starts on first access (not at namespace load)
;; - Container is shared across all tests (fast)
;; - Container is guaranteed to start exactly once (thread-safe)
;; 
;; Registers shutdown hook to stop container when JVM exits.
(defonce ^:private pg-test-container-delay
  (delay
    (let [container (start-pg-test-container)]
      (reset! postgres-container container)
      ;; Ensure container stops when tests finish
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. stop-pg-container))
      container)))

(defn container->pg-url
  "Takes a postgres container
   Returns a jdbc-ready database url
   Optionally takes in a database name."
  ([container] (container->pg-url
                container
                ; defaults to main db name
                (when container (.getDatabaseName container))))
  ([container db-name]
   (when container
     (let [host (.getHost container)
           port (.getMappedPort container PostgreSQLContainer/POSTGRESQL_PORT)
           user-name (.getUsername container)
           password (.getPassword container)]
       (str "jdbc:postgresql://" host
            ":" port
            "/" db-name
            "?user=" user-name
            "&password=" password)))))


(defn ^:private get-admin-db
  "Returns a datasource connected to the test container's default database.
     
   This is used for:
   - Running migrations (once)
   - Creating template databases
   - Creating/dropping test databases"
  []
  (let [container @pg-test-container-delay]
    (jdbc/get-datasource
     {:dbtype   "postgresql"
      :jdbcUrl  (container->pg-url container)})))

;; =============================================================================
;; Database Migrations
;; =============================================================================

(defn ^:private run-migrations
  "Runs all database migrations against the provided database.
   
   Uses Migratus library to:
   - Read migration scripts from resources/migrations folder
   - Execute all up migrations in order
   
   This is run once against the template database, which is then
   cloned for each test (much faster than running migrations per test)."
  [db]
  (migratus/migrate {:store :database
                     :migration-dir "migrations"
                     :db {:datasource (jdbc/get-datasource db)}}))

;; Lazy migration execution - runs once per test suite.
;; 
;; Migrations are run against the template database, which is then
;; cloned by CREATE DATABASE ... TEMPLATE ... for each test.
(defonce ^:private migrations-delay
  (delay (run-migrations (get-admin-db))))


;; =============================================================================
;; Test Database Isolation
;; =============================================================================

(def ^:private test-counter
  "Atomic counter for generating unique test database names.
   
   Each test gets test_1, test_2, test_3, etc."
  (atom 0))

(defn with-test-db
  "Provides a fresh, migrated database to the test callback.
   
   Flow:
   1. Ensure migrations have run (happens once)
   2. Create a unique database name (test_N)
   3. CREATE DATABASE test_N TEMPLATE <default> (fast - just copies structure)
   4. Run the test callback with a connection to test_N
   5. DROP DATABASE test_N (cleanup)
   
   This ensures complete test isolation - no shared state between tests.
   
   Usage:
   (with-test-db
     (fn [db]
       (jdbc/execute! db [\"INSERT INTO ...\"])
       (is (= ...))))"
  [callback]
  ;; Ensure migrations have run (blocks until complete)
  @migrations-delay

  (let [test-database-name (str "test_" (swap! test-counter inc))
        container          @pg-test-container-delay
        admin-db                 (get-admin-db)]
    ;; Create a new database by cloning the template
    ;; This is MUCH faster than running migrations for each test
    (jdbc/execute!
     admin-db
     [(format "CREATE DATABASE %s TEMPLATE %s;"
              test-database-name
              (PostgreSQLContainer/.getDatabaseName container))])

    (let [db-url (container->pg-url container test-database-name)
          db (jdbc/get-datasource
              {:dbtype   "postgresql"
               :jdbcUrl  db-url})]
      (try
        ;; Connect to the test database and run the test
        ;; with-open ensures the connection is closed after the test
        (with-open [conn (jdbc/get-connection db)]
          (callback conn))
        (finally
          ;; Close test data source
          ;; Always clean up - drop the test database
          (jdbc/execute! admin-db
                         [(format "DROP DATABASE %s;" test-database-name)]))))))

;; =============================================================================
;; Container manipulation - Why we need this?
;; When running tests from the REPL, the containers will be started, upon disconnection-
;; it can happen that the shutdown hook registered during (pg-test-container-delay) fails to trigger
;; This leads to orphaned containers, not controlled by any process
;; Usually you'd have to manually stop these containers
;; The functions below help you continue in the REPL and remove whathever containers you want 
;; =============================================================================


(defn get-running-containers
  []
  (let [cmd-result (shell/sh "docker"
                             "ps"
                             "--format"
                             "{{.ID}}|{{.Image}}|{{.Names}}|{{.Ports}}|{{.CreatedAt}}")
        cmd-output (:out cmd-result)
        lines (str/split cmd-output #"\n")
        parse-lines (comp
                     (map #(str/split % #"\|")) ; line->row(columns)
                     (map (fn [[id image name ports created-at]]
                            {:id id
                             :image image
                             :name name
                             :ports ports
                             :created-at created-at}))) ; row(columns)->map
        parsed (transduce parse-lines
                          conj [] ; init reducing fn + initial value
                          lines ; input
                          )]
    (into {} (map-indexed vector parsed))))

(defn stop-containers [& indexes]
  (let [running-containers (get-running-containers)
        containers-to-stop (select-keys running-containers indexes)]
    (doseq [[idx container] containers-to-stop]
      (println (format "Stopping container %d: %s (%s)" idx (:name container) (:id container)))
      (let [result (shell/sh "docker" "stop" (:id container))]
        (if (zero? (:exit result))
          (println (format "✓ Stopped container %s" (:id container)))
          (println (format "✗ Failed to stop container %s: %s" (:id container) (:err result))))))
    (count containers-to-stop)))

(comment
  ;; Start the container and run migrations on it
  @migrations-delay
  ;; Inspect the running DB container
  @postgres-container
  ;; Stop the container
  (stop-pg-container)
  ;; Reset it!
  (reset! postgres-container (start-pg-test-container))


  ;; List running containers
  (get-running-containers)
  ;; Stop container based on index
  (stop-containers 0)
  ;
  )