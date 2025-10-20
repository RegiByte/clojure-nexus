(ns user
  (:require
   [camel-snake-kebab.core :as csk]
   [honey.sql :as honey]
   [integrant.repl :as ig-repl]
   [integrant.repl.state :as ig-state]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [nexus.db :as db]
   [nexus.router.helpers :as rh]
   [nexus.system :as system]
   [reitit.core :as reitit]
   [taoensso.telemere :as tel] ; Use telemery as default logger
   ))


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

(defn services:users
  []
  (:nexus.services/users (system)))
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
  (services:users)

  ((:some-method (services:users)) "hi there!")
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
                     :uri "/hello/world!"
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

  (reitit/routes (server:router))
  (reitit/route-names (server:router))


  ;; Name based routing
  (let [match (reitit/match-by-name (server:router) :homepage {})]
    (-> match reitit/match->path))

  (let [match (reitit/match-by-name (server:router) :hello {:name "world"})]
    (-> match reitit/match->path))


  ;; Experiments
  )



(comment ;; Testing helper
  (rh/named-url (server:router) :homepage)
  (rh/named-url (server:router) :hello {:name "world"})
  (rh/named-url (server:router) :hello {:name "world"} {:foo "bar"})
  (rh/named-url (server:router) :hellos {:name "world"} {:foo "bar"})


  (rh/url->route (server:router) "/hello/regi?foo=bar")

  (rh/url->route (server:router) "/hello/:name")
  (rh/url->route (server:router) "/api/health")


  ;
  )


(comment

  (csk/->Camel_Snake_Case "hello world")

  (db/format-sql {:select [:*]
                  :from :foo.bar})

  (db/format-sql {:select [:*]
                  :from :nexus.users
                  :where [:and [:= :name "regi"] [:= :age "20"]]})

  (db/format-sql {:insert-into [:nexus.users]
                  :values [{:first_name "regi"
                            :last_name "junior"
                            :middle_name "cunha"
                            :email "regi@email.com"}]})

  (honey/format {:insert-into [:nexus.users]
                 :values [{:first-name "regi"
                           :last-name nil
                           :middle-name "cunha"
                           :email "regi@email.com"}]})

  (defn create-user [db {:keys [first-name last-name middle-name email]}]
    (db/exec-one! db {:insert-into [:nexus.users]
                      :values [{:first_name first-name
                                :last_name last-name
                                :middle_name middle-name
                                :email email}]}))

  (create-user (connection) {:first-name "Regi"
                             :last-name "Byte"
                             :middle-name "nice!"
                             :email "some.email+2@example.com"})

  (-> (services:users)
      :create
      (apply [{:first-name "Test"
               :last-name "User"
               :email "test.user@email.com"}]))
  
  ((:create (services:users)) {:first-name "Test"
                               :last-name "User"
                               :email "test.user+1@email.com"})
  
  (-> (services:users)
      :find-by-email
      (apply ["some.email+2@example.com"]))

  (defn find-user-by-email [db email]
    (db/exec-one! db {:select [:*]
                      :from :nexus.users
                      :where [:= :email email]}))

  (db/exec! (connection) {:insert-into [:nexus.users]
                          :values [{:first_name "amanda"
                                    :last_name "machado"
                                    :middle_name "princesa"
                                    :email "amanda2@email.com"}]})

  (find-user-by-email (connection) "amanda2@email.com")

  (honey/format {:where [:= nil :middle-name]})

  (defn find-users-where [db where]
    (db/exec! db {:select [:*]
                  :from :nexus.users
                  :where where}))

  (find-users-where (connection) [:not [:= nil :middle_name]])


  (connection)
  (db/exec! (connection) {:select [:*]
                          :from :nexus.users})

  (defn fn-with-doc
    "This function has docstrings"
    [a b]
    nil)

  (doc fn-with-doc)

  (def fn-partial (partial fn-with-doc 1))
  (doc fn-partial)
  (fn-partial 2)
  ;
  )

