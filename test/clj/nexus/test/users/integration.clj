(ns nexus.test.users.integration
  (:require
   [clj-http.client :as http]
   [clojure.test :as t :refer [deftest is testing use-fixtures]]
   [jsonista.core :as json]
   [nexus.shared.strings :as strings]
   [nexus.test.helpers :as th]
   [nexus.test.test-system :as test-system]))


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
  {:firstName "John"
   :lastName "Doe"
   :middleName "Michael"
   :email "john.doe@example.com"
   :password "securepass123"})


(deftest register-user-test
  (testing "Successfully register a new user"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              path (str (th/server->http-host server) "/api/users/register")
              response (th/http-request :post
                                        path
                                        {:body test-user-data})]
          (is (nil? (-> response :body :errors)))
          (is (= 201 (:status response)))
          (is (= "User registered successfully" (-> response :body :message)))
          (is (= "john.doe@example.com" (-> response :body :user :email)))
          (is (= "John" (-> response :body :user :firstName)))
          (is (= "Doe" (-> response :body :user :lastName)))
          (is (string? (-> response :body :user :id)))
          (is (uuid? (strings/str->uuid (-> response :body :user :id))))
          (is (nil? (-> response :body :user :password-hash))
              "Password hash should not be in response")
          (is (nil? (-> response :body :user :passwordHash))
              "Password hash should not be in response"))))))

(comment
  (t/run-test register-user-test)
  ;
  )

(deftest login-user-test
  (testing "Successfully login with valid credentials"
    (test-system/with-system+server
      (fn [system]
        ;; First register a user
        (let [server (:nexus.server/server system)
              path (str (th/server->http-host server) "/api/users/register")
              register-response (th/http-request :post
                                                 path
                                                 {:body test-user-data})

              path (str (th/server->http-host server) "/api/users/login")
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
          (is (nil? (-> login-response :body :user :passwordHash))
              "Password hash should not be in response")
          (is (nil? (-> login-response :body :user :password-hash))
              "Password hash should not be in response"))))))


(deftest list-users-test
  (testing "Successfully list users with authentication"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->http-host server)

              ;; Register first user
              _ (th/http-request :post
                                 (str base-url "/api/users/register")
                                 {:body test-user-data})

              ;; Login to get token
              login-response (th/http-request :post
                                              (str base-url "/api/users/login")
                                              {:body {:email (:email test-user-data)
                                                      :password (:password test-user-data)}})
              token (-> login-response :body :token)

              ;; Register second user
              _ (th/http-request :post
                                 (str base-url "/api/users/register")
                                 {:body {:firstName "Jane"
                                         :lastName "Smith"
                                         :email "jane.smith@example.com"
                                         :password "anotherpass456"}})

              ;; List users with token
              list-response (th/http-request :get
                                             (str base-url "/api/users")
                                             {:headers {"authorization" (str "Bearer " token)}})]
          (is (= 200 (:status list-response)))
          (is (vector? (:body list-response)))
          (is (>= (count (:body list-response)) 2))
          (is (every? :email (:body list-response))))))))




(deftest get-user-test
  (testing "Successfully get user by ID with authentication"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->http-host server)

              ;; Register user
              register-response (th/http-request :post
                                                 (str base-url "/api/users/register")
                                                 {:body test-user-data})
              user-id (-> register-response :body :user :id)

              ;; Login to get token
              login-response (th/http-request :post
                                              (str base-url "/api/users/login")
                                              {:body {:email (:email test-user-data)
                                                      :password (:password test-user-data)}})
              token (-> login-response :body :token)

              ;; Get user by ID
              get-response (th/http-request :get
                                            (str base-url "/api/users/" user-id)
                                            {:headers {"authorization" (str "Bearer " token)}})]
          (is (= 201 (:status register-response)))
          (is (= 200 (:status login-response)))
          (is (not (nil? token)))
          (is (= 200 (:status get-response)))
          (is (= "john.doe@example.com" (-> get-response :body :email)))
          (is (= user-id (-> get-response :body :id))))))))

(comment
  (t/run-test get-user-test)
  (t/run-tests)
  ;
  )

(deftest update-user-test
  (testing "Successfully update user with authentication"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->http-host server)

              ;; Register user
              register-response (th/http-request :post
                                                 (str base-url "/api/users/register")
                                                 {:body test-user-data})
              user-id (-> register-response :body :user :id)

              ;; Login to get token
              login-response (th/http-request :post
                                              (str base-url "/api/users/login")
                                              {:body {:email (:email test-user-data)
                                                      :password (:password test-user-data)}})
              token (-> login-response :body :token)

              ;; Update user
              update-response (th/http-request :patch
                                               (str base-url "/api/users/" user-id)
                                               {:headers {"authorization" (str "Bearer " token)}
                                                :body {:firstName "Johnny"}})]
          (is (nil? (-> update-response :body :errors)))
          (is (= 201 (:status register-response)))
          (is (= 200 (:status update-response)))
          (is (not (nil? (:body update-response))))
          (is (= "User updated successfully" (-> update-response :body :message)))
          (is (= "Johnny" (-> update-response :body :user :firstName))))))))

(deftest update-user-invalid-parameters-test
  (testing "Returns 400 for invalid update parameters"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->http-host server)

              ;; Register user
              register-response (th/http-request :post
                                                 (str base-url "/api/users/register")
                                                 {:body test-user-data})
              user-id (-> register-response :body :user :id)

              ;; Login to get token
              login-response (th/http-request :post
                                              (str base-url "/api/users/login")
                                              {:body {:email (:email test-user-data)
                                                      :password (:password test-user-data)}})
              token (-> login-response :body :token)

              ;; Update user
              update-response (th/http-request :patch
                                               (str base-url "/api/users/" user-id)
                                               {:headers {"authorization" (str "Bearer " token)}
                                                :body {:firstName "Updated name"
                                                       :password "updated password"}})

              ;; Errors
              errors (-> update-response :body :errors)
              first-error (first errors)
              error-path (-> first-error :path first)
              error-message (-> first-error :message)]
          (is (not (nil? errors)))
          (is (= 201 (:status register-response)))
          (is (= 400 (:status update-response)))
          (is (not (nil? (:body update-response))))
          (is (= "password" error-path))
          (is (= "disallowed key" error-message)))))))

(comment
  (t/run-test update-user-test)
  (t/run-test update-user-invalid-parameters-test)
  (t/run-tests)


  ;
  )

(deftest delete-user-test
  (testing "Successfully delete user with authentication"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->http-host server)

              ;; Register user
              register-response (th/http-request :post
                                                 (str base-url "/api/users/register")
                                                 {:body test-user-data})
              user-id (-> register-response :body :user :id)

              ;; Login to get token
              login-response (th/http-request :post
                                              (str base-url "/api/users/login")
                                              {:body {:email (:email test-user-data)
                                                      :password (:password test-user-data)}})
              token (-> login-response :body :token)

              ;; Delete user
              delete-response (th/http-request :delete
                                               (str base-url "/api/users/" user-id)
                                               {:headers {"authorization" (str "Bearer " token)}})

              verification-response (th/http-request :get
                                                     (str base-url "/api/users/" user-id)
                                                     {:headers {"authorization" (str "Bearer " token)}})]
          (is (= 200 (:status delete-response)))
          (is (= 404 (:status verification-response)))
          (is (= "User deleted successfully" (-> delete-response :body :message)))
          (is (= "User not found" (-> verification-response :body :error))))))))

(deftest change-password-test
  (testing "Successfully change password with authentication"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->http-host server)

              ;; Register user
              register-response (th/http-request :post
                                                 (str base-url "/api/users/register")
                                                 {:body test-user-data})
              user-id (-> register-response :body :user :id)

              ;; Login to get token
              login-response (th/http-request :post
                                              (str base-url "/api/users/login")
                                              {:body {:email (:email test-user-data)
                                                      :password (:password test-user-data)}})
              token (-> login-response :body :token)

              ;; Change password
              change-pw-response (th/http-request :post
                                                  (str base-url "/api/users/" user-id "/change-password")
                                                  {:headers {"authorization" (str "Bearer " token)}
                                                   :body {:oldPassword "securepass123"
                                                          :newPassword "newsecurepass456"}})]
          (is (= 201 (:status register-response)))
          (is (= 200 (:status login-response)))
          (is (= 200 (:status change-pw-response)))
          (is (nil? (-> change-pw-response :body :errors)))
          (is (= "Password changed successfully" (-> change-pw-response :body :message)))

          ;; Verify can login with new password
          (let [new-login-response (th/http-request :post
                                                    (str base-url "/api/users/login")
                                                    {:body {:email (:email test-user-data)
                                                            :password "newsecurepass456"}})]
            (is (= 200 (:status new-login-response)))))))))

(comment
  (t/run-test change-password-test)
  (t/run-tests)
  ;
  )

(deftest search-users-test
  (testing "Successfully search users with authentication"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->http-host server)

              ;; Register first user
              _ (th/http-request :post
                                 (str base-url "/api/users/register")
                                 {:body test-user-data})

              ;; Register second user
              _ (th/http-request :post
                                 (str base-url "/api/users/register")
                                 {:body {:firstName "Jane"
                                         :lastName "Smith"
                                         :email "jane.smith@example.com"
                                         :password "anotherpass456"}})

              ;; Login to get token
              login-response (th/http-request :post
                                              (str base-url "/api/users/login")
                                              {:body {:email (:email test-user-data)
                                                      :password (:password test-user-data)}})
              token (-> login-response :body :token)

              ;; Search for "Jane"
              search-response (th/http-request :get
                                               (str base-url "/api/users/search?q=Jane")
                                               {:headers {"authorization" (str "Bearer " token)}})]
          (is (= 200 (:status search-response)))
          (is (vector? (:body search-response)))
          (is (>= (count (:body search-response)) 1))
          (is (some #(= "jane.smith@example.com" (:email %)) (:body search-response))))))))

(deftest unauthorized-access-integration-test
  (testing "Cannot access protected endpoints without token via HTTP"
    (test-system/with-system+server
      (fn [system]
        (let [server (-> system :nexus.server/server)
              base-url (str (th/server->http-host server) "/api/users")

              ;; Try to list users without token
              response (http/get
                        base-url
                        {:accept :json
                         :throw-exceptions false})]
          (is (= 401 (:status response)))))))) ;; end comment for integration tests

(comment
  (t/run-tests)
  (t/run-test register-user-test)
  ;
  )