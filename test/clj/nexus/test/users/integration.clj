(ns nexus.test.users.integration
  (:require
   [clj-http.client :as http]
   [clojure.test :as t :refer [deftest is testing use-fixtures]]
   [jsonista.core :as json]
   [nexus.shared.strings :as strings]
   [nexus.test.helpers :as th]
   [nexus.test.test-system :as test-system]
   [nexus.users.http-handlers :as handlers]))


;; ============================================================================
;; Test Fixtures
;; ============================================================================

(use-fixtures :once
  (fn [f]
    (test-system/setup-logging!)
    (f)))

;; ============================================================================
;; Test Data
;; ============================================================================

(def test-user-data
  {:first-name "John"
   :last-name "Doe"
   :middle-name "Michael"
   :email "john.doe@example.com"
   :password "securepass123"})


(deftest register-user-test
  (testing "Successfully register a new user"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              path (str (th/server->host server) "/api/users/register")
              response (th/http-request :post
                                        path
                                        {:body test-user-data})]
          (is (nil? (-> response :body :errors)))
          (is (= 201 (:status response)))
          (is (= "User registered successfully" (-> response :body :message)))
          (is (= "john.doe@example.com" (-> response :body :user :email)))
          (is (= "John" (-> response :body :user :first-name)))
          (is (= "Doe" (-> response :body :user :last-name)))
          (is (string? (-> response :body :user :id)))
          (is (uuid? (strings/str->uuid (-> response :body :user :id))))
          (is (nil? (-> response :body :user :password-hash))
              "Password hash should not be in response"))))))

(deftest login-user-test
  (testing "Successfully login with valid credentials"
    (test-system/with-system+server
      (fn [system]
        ;; First register a user
        (let [server (:nexus.server/server system)
              path (str (th/server->host server) "/api/users/register")
              register-response (th/http-request :post
                                                 path
                                                 {:body test-user-data})

              path (str (th/server->host server) "/api/users/login")
              login-response (th/http-request :post path
                                              {:body {:email (:email test-user-data)
                                                      :password (:password test-user-data)}})]
          (is (= 201 (:status register-response)))
          (is (= 200 (:status login-response)))
          (is (nil? (-> register-response :body :errors)))
          (is (nil? (-> login-response :body :errors)))
          (is (= "Logged in successfully" (-> login-response :body :message)))
          (is (string? (-> login-response :body :token)))
          (is (= "john.doe@example.com" (-> login-response :body :user :email)))
          (is (nil? (-> login-response :body :user :password-hash))
              "Password hash should not be in response"))))))


(comment
  (t/run-test login-user-test)


  ;
  )