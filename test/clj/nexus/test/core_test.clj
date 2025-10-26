(ns nexus.test.core-test
  (:require
   [clojure.test :refer :all]
   [clojure.test :as t]
   [next.jdbc :as jdbc]
   [nexus.core :refer :all]
   [nexus.test.containers :as containers]
   [nexus.test.helpers :as h]
   [nexus.test.system :as test-sys]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 1 1))))

(t/deftest counting-works
  (containers/with-test-db
    (fn [db]
      (let [result (jdbc/execute-one! db ["SELECT 1 AS number"])]
        (t/is (= (:number result) 1))))))

(t/deftest health-endpoint
  (test-sys/with-system
    (fn [system]
      (let [app (-> system :nexus.server/app :handler)
            response (h/call-handler app {:ring.request/headers {}
                                          :request-method :get
                                          :uri "/api/health"})
            content-type (get-in response [:headers "Content-Type"])]
        (println {;:response response
                  ;:app app
                  :content-type content-type})
        (t/is (= 200 (:status response)))
        (t/is (.contains content-type "application/json"))))))


(comment
  (t/run-tests)


  ; paren gate
  )