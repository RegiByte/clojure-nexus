(ns nexus.db
  (:require
   [clojure.set :as set]
   [integrant.core :as ig]
   [honey.sql :as honey]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc-rs]
   [next.jdbc.connection :as connection]
   [taoensso.telemere :as tel])
  (:import
   (com.zaxxer.hikari HikariDataSource)))

; Common functions

(def ^:private sql-params
  {:builder-fn jdbc-rs/as-maps})

(defn format-sql [query]
  (honey/format query {:quoted true}))

(defn exec!
  "Send query to db and return vector of result items."
  [db query]
  (let [query-sql (format-sql query)]
    (jdbc/execute! db query-sql sql-params)))

(defn exec-one!
  "Send query to db and return single result item."
  [db query]
  (let [query-sql (format-sql query)]
    (jdbc/execute-one! db query-sql sql-params)))

;; Turns a simple url into a db spec map
;; e.g 
;; input: postgres://user1:secretpassword@localhost:5436/nexus
;; output: {:dbtype "postgres", :host "localhost", :port 5436, :dbname "nexus", :password "secretpassword", :username "user1"}
;; this is needed because hikari needs the connection options in the spec format
(defmethod ig/init-key ::spec
  [_ {:keys [url]}]
  (tel/log! :info (str "initializing db spec from url" url))
  (-> (connection/uri->db-spec url)
      (set/rename-keys {:user :username})))


;; Handle conversion from snake-case to kebab-case automatically
(def default-options jdbc/unqualified-snake-kebab-opts)

; HikariCP connection pool lifecycle
;; Startup
(defmethod ig/init-key ::connection
  ; Turns a db spec map into a connection map
  [_ {:keys [spec]}]
  (tel/log! :info "initializing db")
  (jdbc/with-options
    (connection/->pool HikariDataSource spec)
    default-options))

;; Shutdown
(defmethod ig/halt-key! ::connection
  [_ connection]
  (println "Building system db connection")
  (tel/log! :info "tearing down db")
  (.close (jdbc/get-datasource connection)))

;; Migration management
(defmethod ig/init-key ::migrations
  [_ {:keys [connection]}]
  (tel/log! :info "initializing migrations")
  {:store :database
   :migration-dir "migrations"
   :db {:datasource (jdbc/get-datasource connection)}})
