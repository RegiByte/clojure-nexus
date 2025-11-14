(ns nexus.user
  (:require
   [integrant.repl :as ig-repl]
   [next.jdbc :as jdbc]
   [nexus.dev-system :as ds :refer [connection restart server:handler
                                    server:router services:jwt services:stream
                                    start stop system test-config]]
   [nexus.router.helpers :as rh]
   [nexus.router.realtime.service :as stream-svc]
   [nexus.system :as system]
   [nexus.users.service :as users]
   [reitit.core :as reitit]
   [taoensso.telemere :as tel] ; Use telemery as default logger
   ))


(do
  ;; REPL Setup, side effectful things
  (ig-repl/set-prep! #(system/prepare {:config "system.config.edn" :profile :dev}))

  (tel/log! "REPL loaded, happy hacking!!")

  ;; Ensure REPL shuts down system before closing
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread. #(do
               (when (system)
                 (stop))))))


(comment
  ; Lifecycle 
  (start)
  (stop)
  (restart)
  (system)
  ;
  )

(comment
  (inc 2)
  (+ 1 2)


  ;; Testing Users service
  (defn user-deps [] {:db (connection)
                      :jwt (services:jwt)})
  (users/register-user!
   (user-deps)
   {:first-name "Reginaldo"
    :last-name "Junior"
    :middle-name "Adriano"
    :email "regi+test1@nexus.com"
    :password "somenicepassword"})

  (println {:first-name "Reginaldo"
            :last-name "Junior"
            :middle-name "Adriano"
            :email "regi+4@test.com"
            :password "somenicepassword"})

  (users/authenticate-user
   (user-deps)
   {:email "regi+2@test.com"
    :password "somenicepassword"})

  (users/change-password!
   (user-deps)
   {:user-id 1
    :old-password "somenicepassword"
    :new-password "updatedpassword!"})

  (when-let [result (users/authenticate-user
                     {:db (connection)
                      :jwt (services:jwt)}
                     {:email "regi+2@test.com"
                      :password "updatedpassword!"})]
    (:token result))

  (users/list-users
   (user-deps)
   {:offset 0
    :limit 50})

  (users/delete-user!
   (user-deps)
   #uuid "4437d63a-c70e-4d8f-8ebc-515c4e71457b")

  (java.util.UUID/fromString "03a8ade1-615c-4dd3-a535-71f5742a063d")

  (users/find-by-id
   (user-deps)
   #uuid "03a8ade1-615c-4dd3-a535-71f5742a063d")


  ;; Testing Users service
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
  ;; Testing JWT

  (def test-jwt ((:generate-token (services:jwt))
                 {:id "123"
                  :email "regi@test.com"}

                 {:claims {:roles ["user" "admin"]
                           :some "claim"}}))


  ((:token-valid? (services:jwt))
   test-jwt)

  ((:verify-token (services:jwt))
   (str test-jwt "aaaaaa"))


;
  )

(comment
  ;; Testing Stream Service

  ;; Check active streams
  (stream-svc/get-active-streams (services:stream))
  (stream-svc/get-active-streams-by-channel (services:stream) :broadcast-channel)
  (stream-svc/get-active-streams-by-type (services:stream) :sse)
  (stream-svc/get-active-streams-by-type (services:stream) :websocket)
  (stream-svc/stream-count (services:stream))

  ;; Close a specific stream (get ID from browser console or logs)
  (stream-svc/close-stream! (services:stream) "517f76b0-6d09-405f-87a3-f35e189dc0db")

  ;; Close all streams (useful for testing shutdown behavior)
  (stream-svc/close-all-streams! (services:stream))

  ;; After closing streams, check the browser - connections should immediately close!
  ;; No more 15-second timeout!

  ;
  )
