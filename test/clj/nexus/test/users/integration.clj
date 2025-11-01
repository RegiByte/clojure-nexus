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


(deftest list-users-test
  (testing "Successfully list users with authentication"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->host server)

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
                                 {:body {:first-name "Jane"
                                         :last-name "Smith"
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
              base-url (th/server->host server)

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
          (is (= 200 (:status get-response)))
          (is (= "john.doe@example.com" (-> get-response :body :email)))
          (is (= user-id (-> get-response :body :id))))))))

(deftest update-user-test
  (testing "Successfully update user with authentication"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->host server)

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
                                                :body {:first-name "Johnny"}})]
          (is (= 201 (:status register-response)))
          (is (= 200 (:status update-response)))
          (is (not (nil? (:body update-response))))
          (is (= "User updated successfully" (-> update-response :body :message)))
          (is (= "Johnny" (-> update-response :body :user :first-name))))))))


(deftest delete-user-test
  (testing "Successfully delete user with authentication"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->host server)

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
              base-url (th/server->host server)

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
                                                   :body {:old-password "securepass123"
                                                          :new-password "newsecurepass456"}})]
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

(deftest search-users-test
  (testing "Successfully search users with authentication"
    (test-system/with-system+server
      (fn [system]
        (let [server (:nexus.server/server system)
              base-url (th/server->host server)

              ;; Register first user
              _ (th/http-request :post
                                 (str base-url "/api/users/register")
                                 {:body test-user-data})

              ;; Register second user
              _ (th/http-request :post
                                 (str base-url "/api/users/register")
                                 {:body {:first-name "Jane"
                                         :last-name "Smith"
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


(comment
  (deftest full-registration-and-login-flow-integration-test
    (testing "Full user registration and login flow via HTTP"
      (test-system/with-system+server
        (fn [system]
          (let [server (-> system :nexus.server/server)
                port (-> server .getURI .getPort)
                base-url (str "http://localhost:" port "/api/users")]

            ;; Register user via HTTP
            (let [register-response (http/post
                                     (str base-url "/register")
                                     {:body (json/write-value-as-string test-user-data)
                                      :content-type :json
                                      :accept :json
                                      :throw-exceptions false})]
              (is (= 201 (:status register-response)))
              (let [body (json/read-value (:body register-response) json/keyword-keys-object-mapper)]
                (is (= "User registered successfully" (:message body)))
                (is (= "john.doe@example.com" (-> body :user :email)))))

            ;; Login via HTTP
            (let [login-response (http/post
                                  (str base-url "/login")
                                  {:body (json/write-value-as-string
                                          {:email (:email test-user-data)
                                           :password (:password test-user-data)})
                                   :content-type :json
                                   :accept :json
                                   :throw-exceptions false})]
              (is (= 200 (:status login-response)))
              (let [body (json/read-value (:body login-response) json/keyword-keys-object-mapper)]
                (is (= "Logged in successfully" (:message body)))
                (is (string? (:token body)))

                ;; Use token to list users
                (let [token (:token body)
                      list-response (http/get
                                     base-url
                                     {:headers {"Authorization" (str "Bearer " token)}
                                      :accept :json
                                      :throw-exceptions false})]
                  (is (= 200 (:status list-response)))
                  (let [users (json/read-value (:body list-response) json/keyword-keys-object-mapper)]
                    (is (vector? users))
                    (is (>= (count users) 1))))))))))))

(deftest unauthorized-access-integration-test
  (testing "Cannot access protected endpoints without token via HTTP"
    (test-system/with-system+server
      (fn [system]
        (let [server (-> system :nexus.server/server)
              port (-> server .getURI .getPort)
              base-url (str "http://localhost:" port "/api/users")

              ;; Try to list users without token
              response (http/get
                        base-url
                        {:accept :json
                         :throw-exceptions false})]
          (is (= 401 (:status response)))))))) ;; end comment for integration tests