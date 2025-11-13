(ns nexus.test.core-test
  (:require
   [clojure.test :as t]
   [next.jdbc :as jdbc]
   [nexus.test.containers :as containers]
   [nexus.test.helpers :as h]
   [nexus.test.test-system :as test-sys]))

(t/deftest one-eq-one
  (t/testing "One equals one! if this doesn't pass nothing will"
    (t/is (= 1 1))))

(t/deftest counting-works
  (containers/with-test-db
    (fn [db]
      (let [result (jdbc/execute-one! db ["SELECT 1 AS number"])]
        (t/is (= 1 (:number result)))))))

(t/deftest health-endpoint
  (t/testing "Calling the health endpoint works"
    (test-sys/with-system
      (fn [system]
        (let [app (-> system :nexus.server/app :handler)
              response (h/call-handler app {:ring.request/headers {}
                                            :request-method :get
                                            :uri "/api/health"})
              content-type (get-in response [:headers "Content-Type"])]
          (t/is (= 200 (:status response)))
          (t/is (.contains content-type "application/json")))))))

(t/deftest can-get-server-port
  (t/testing "Can get the port from a running server"
    (test-sys/with-system+server
      (fn [system]
        (let [server (-> system :nexus.server/server)
              port (h/server->port server)]
          (t/is (not (nil? port)))
          (t/is (pos-int? port)))))))

(t/deftest http-request-to-running-server
  (t/testing "Making actual HTTP requests to a running server"
    (test-sys/with-system+server
      (fn [system]
        (let [server (-> system :nexus.server/server)
              port (h/server->port server)
              host (h/server->http-host server)
              url (str host "/api/health")
              response (h/http-request :get url)]

          (println "Server running on port:" port)
          (println "Response:" response)

          ;; Verify server is running and responding
          (t/is (not (nil? server)) "Server should be initialized")
          (t/is (pos? port) "Port should be positive")
          (t/is (= 200 (:status response)) "Health endpoint should return 200")
          (t/is (= "ok" (get-in response [:body :status])) "Health status should be 'ok'"))))))



(t/deftest multiple-servers-run-simultaneously
  (t/testing "Multiple test servers can run at the same time on different ports"
    (let [ports (atom [])]
      ;; Start first server
      (test-sys/with-system+server
        (fn [system1]
          (let [server1 (-> system1 :nexus.server/server)
                port1 (h/server->port server1)
                server1-host (h/server->http-host server1)]
            (swap! ports conj port1)
            (println "Server 1 running on port:" port1)

            ;; Start second server while first is still running
            (test-sys/with-system+server
              (fn [system2]
                (let [server2 (-> system2 :nexus.server/server)
                      port2 (h/server->port server2)
                      server2-host (h/server->http-host server2)]
                  (swap! ports conj port2)
                  (println "Server 2 running on port:" port2)

                  ;; Both servers should be on different ports
                  (t/is (not= port1 port2) "Servers should use different ports")

                  ;; Both servers should respond independently
                  (let [response1 (h/http-request :get (str server1-host "/api/health"))
                        response2 (h/http-request :get (str server2-host "/api/health"))]

                    (println "Response from server 1:" (:body response1))
                    (println "Response from server 2:" (:body response2))

                    (t/is (= 200 (:status response1)) "Server 1 should respond")
                    (t/is (= 200 (:status response2)) "Server 2 should respond")
                    (t/is (= "ok" (get-in response1 [:body :status])))
                    (t/is (= "ok" (get-in response2 [:body :status]))))))))))

      ;; Verify we actually tested with two different ports
      (t/is (= 2 (count @ports)) "Should have tested with 2 servers")
      (println "All ports used:" @ports))))

(comment
  (t/run-tests)
  (t/run-test multiple-servers-run-simultaneously)


  (t/run-all-tests)
  ; paren gate
  )