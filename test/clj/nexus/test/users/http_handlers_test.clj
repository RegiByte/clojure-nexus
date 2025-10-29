(ns nexus.test.users.http-handlers-test
  "Tests for user HTTP handlers - both happy and unhappy paths"
  (:require
   [clojure.test :as t :refer [deftest is testing use-fixtures]]
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

(def test-user-data-2
  {:first-name "Jane"
   :last-name "Smith"
   :email "jane.smith@example.com"
   :password "anotherpass456"})

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn- create-request
  "Create a mock request map"
  [system & {:keys [body path query headers]
             :or {body {} path {} query {} headers {}}}]
  {:context {:db (:nexus.db/connection system)
             :jwt (:nexus.auth/jwt system)}
   :parameters {:body body
                :path path
                :query query}
   :headers headers})

(defn- create-authenticated-request
  "Create a mock authenticated request with JWT token"
  [system token & {:keys [body path query]
                   :or {body {} path {} query {}}}]
  (let [jwt-service (:nexus.auth/jwt system)
        decoded ((:verify-token jwt-service) token)
        context {:db (:nexus.db/connection system)
                 :jwt jwt-service}]
    {:context context
     :parameters {:body body
                  :path path
                  :query query}
     :headers {"authorization" (str "Bearer " token)}
     :identity decoded}))

;; ============================================================================
;; Happy Path Tests
;; ============================================================================

(deftest register-user-test
  (testing "Successfully register a new user"
    (test-system/with-system
      (fn [system]
        (let [request (create-request system :body test-user-data)
              response (handlers/register-handler request)]
          (is (nil? (-> response :body :errors)))
          (is (= 201 (:status response)))
          (is (= "User registered successfully" (-> response :body :message)))
          (is (= "john.doe@example.com" (-> response :body :user :email)))
          (is (= "John" (-> response :body :user :first-name)))
          (is (= "Doe" (-> response :body :user :last-name)))
          (is (uuid? (-> response :body :user :id)))
          (is (nil? (-> response :body :user :password-hash))
              "Password hash should not be in response"))))))

(deftest login-user-test
  (testing "Successfully login with valid credentials"
    (test-system/with-system
      (fn [system]
        ;; First register a user
        (let [register-req (create-request system :body test-user-data)
              _ (handlers/register-handler register-req)

              ;; Then login
              login-req (create-request system
                                        :body {:email (:email test-user-data)
                                               :password (:password test-user-data)})
              response (handlers/login-handler login-req)]
          (is (= 200 (:status response)))
          (is (= "Logged in successfully" (-> response :body :message)))
          (is (string? (-> response :body :token)))
          (is (= "john.doe@example.com" (-> response :body :user :email)))
          (is (nil? (-> response :body :user :password-hash))
              "Password hash should not be in response"))))))

(deftest list-users-test
  (testing "Successfully list users with authentication"
    (test-system/with-system
      (fn [system]
        ;; Register and login to get token
        (let [register-req (create-request system :body test-user-data)
              _ (handlers/register-handler register-req)

              login-req (create-request system
                                        :body {:email (:email test-user-data)
                                               :password (:password test-user-data)})
              login-resp (handlers/login-handler login-req)
              token (-> login-resp :body :token)

              ;; Register another user
              register-req-2 (create-request system :body test-user-data-2)
              _ (handlers/register-handler register-req-2)

              ;; List users
              list-req (create-authenticated-request system token)
              response (handlers/list-users-handler list-req)]
          (is (= 200 (:status response)))
          (is (vector? (:body response)))
          (is (>= (count (:body response)) 2))
          (is (every? :email (:body response))))))))

(deftest get-user-test
  (testing "Successfully get user by ID with authentication"
    (test-system/with-system
      (fn [system]
        ;; Register and login
        (let [register-req (create-request system :body test-user-data)
              register-resp (handlers/register-handler register-req)
              user-id (-> register-resp :body :user :id)

              login-req (create-request system
                                        :body {:email (:email test-user-data)
                                               :password (:password test-user-data)})
              login-resp (handlers/login-handler login-req)
              token (-> login-resp :body :token)

              ;; Get user
              get-req (create-authenticated-request system token :path {:id user-id})
              response (handlers/get-user-handler get-req)]
          (is (= 200 (:status response)))
          (is (= "john.doe@example.com" (-> response :body :email)))
          (is (= user-id (-> response :body :id))))))))

(deftest update-user-test
  (testing "Successfully update user with authentication"
    (test-system/with-system
      (fn [system]
        ;; Register and login
        (let [register-req (create-request system :body test-user-data)
              register-resp (handlers/register-handler register-req)
              user-id (-> register-resp :body :user :id)

              login-req (create-request system
                                        :body {:email (:email test-user-data)
                                               :password (:password test-user-data)})
              login-resp (handlers/login-handler login-req)
              token (-> login-resp :body :token)

              ;; Update user
              update-req (create-authenticated-request system token
                                                       :path {:id user-id}
                                                       :body {:first-name "Johnny"})
              response (handlers/update-user-handler update-req)]
          (is (= 200 (:status response)))
          (is (= "User updated successfully" (-> response :body :message)))
          (is (= "Johnny" (-> response :body :user :first-name))))))))

(deftest delete-user-test
  (testing "Successfully delete user with authentication"
    (test-system/with-system
      (fn [system]
        ;; Register and login
        (let [register-req (create-request system :body test-user-data)
              register-resp (handlers/register-handler register-req)
              user-id (-> register-resp :body :user :id)

              login-req (create-request system
                                        :body {:email (:email test-user-data)
                                               :password (:password test-user-data)})
              login-resp (handlers/login-handler login-req)
              token (-> login-resp :body :token)

              ;; Delete user
              delete-req (create-authenticated-request system token :path {:id user-id})
              response (handlers/delete-user-handler delete-req)]
          (is (= 200 (:status response)))
          (is (= "User deleted successfully" (-> response :body :message))))))))

(deftest change-password-test
  (testing "Successfully change password with authentication"
    (test-system/with-system
      (fn [system]
        ;; Register and login
        (let [register-req (create-request system :body test-user-data)
              register-resp (handlers/register-handler register-req)
              user-id (-> register-resp :body :user :id)

              login-req (create-request system
                                        :body {:email (:email test-user-data)
                                               :password (:password test-user-data)})
              login-resp (handlers/login-handler login-req)
              token (-> login-resp :body :token)

              ;; Change password
              change-pw-req (create-authenticated-request
                             system token
                             :path {:id user-id}
                             :body {:old-password "securepass123"
                                    :new-password "newsecurepass456"})
              response (handlers/change-password-handler change-pw-req)]
          (is (= 200 (:status response)))
          (is (= "Password changed successfully" (-> response :body :message)))

          ;; Verify can login with new password
          (let [new-login-req (create-request system
                                              :body {:email (:email test-user-data)
                                                     :password "newsecurepass456"})
                new-login-resp (handlers/login-handler new-login-req)]
            (is (= 200 (:status new-login-resp)))))))))

(deftest search-users-test
  (testing "Successfully search users with authentication"
    (test-system/with-system
      (fn [system]
        ;; Register users
        (let [register-req-1 (create-request system :body test-user-data)
              _ (handlers/register-handler register-req-1)

              register-req-2 (create-request system :body test-user-data-2)
              _ (handlers/register-handler register-req-2)

              ;; Login
              login-req (create-request system
                                        :body {:email (:email test-user-data)
                                               :password (:password test-user-data)})
              login-resp (handlers/login-handler login-req)
              token (-> login-resp :body :token)

              ;; Search for "Jane"
              search-req (create-authenticated-request system token :query {:q "Jane"})
              response (handlers/search-users-handler search-req)]
          (is (= 200 (:status response)))
          (is (vector? (:body response)))
          (is (>= (count (:body response)) 1))
          (is (some #(= "jane.smith@example.com" (:email %)) (:body response))))))))

;; ============================================================================
;; Unhappy Path Tests
;; ============================================================================

(deftest register-duplicate-email-test
  (testing "Cannot register with duplicate email"
    (test-system/with-system
      (fn [system]
        ;; Register first user
        (let [register-req-1 (create-request system :body test-user-data)
              _ (handlers/register-handler register-req-1)

              ;; Try to register with same email
              register-req-2 (create-request system :body test-user-data)]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Email already registered"
               (handlers/register-handler register-req-2))))))))

(deftest login-invalid-credentials-test
  (testing "Cannot login with invalid credentials"
    (test-system/with-system
      (fn [system]
        ;; Register user
        (let [register-req (create-request system :body test-user-data)
              _ (handlers/register-handler register-req)

              ;; Try to login with wrong password
              login-req (create-request system
                                        :body {:email (:email test-user-data)
                                               :password "wrongpassword"})]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Invalid credentials"
               (handlers/login-handler login-req))))))))

(deftest login-nonexistent-user-test
  (testing "Cannot login with non-existent user"
    (test-system/with-system
      (fn [system]
        (let [login-req (create-request system
                                        :body {:email "nonexistent@example.com"
                                               :password "somepassword"})]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Invalid credentials"
               (handlers/login-handler login-req))))))))

(deftest list-users-without-auth-test
  (testing "Cannot list users without authentication"
    (test-system/with-system
      (fn [system]
        (let [request (create-request system)]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Not authenticated"
               (handlers/list-users-handler request))))))))

(deftest get-nonexistent-user-test
  (testing "Returns 404 for non-existent user"
    (test-system/with-system
      (fn [system]
        ;; Register and login
        (let [register-req (create-request system :body test-user-data)
              _ (handlers/register-handler register-req)

              login-req (create-request system
                                        :body {:email (:email test-user-data)
                                               :password (:password test-user-data)})
              login-resp (handlers/login-handler login-req)
              token (-> login-resp :body :token)

              ;; Try to get non-existent user
              fake-id (java.util.UUID/randomUUID)
              get-req (create-authenticated-request system token :path {:id fake-id})
              response (handlers/get-user-handler get-req)]
          (is (= 404 (:status response)))
          (is (= "User not found" (-> response :body :error))))))))

(deftest update-user-without-auth-test
  (testing "Cannot update user without authentication"
    (test-system/with-system
      (fn [system]
        (let [fake-id (java.util.UUID/randomUUID)
              request (create-request system
                                      :path {:id fake-id}
                                      :body {:first-name "Hacker"})]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Not authenticated"
               (handlers/update-user-handler request))))))))

(deftest change-password-wrong-old-password-test
  (testing "Cannot change password with wrong old password"
    (test-system/with-system
      (fn [system]
        ;; Register and login
        (let [register-req (create-request system :body test-user-data)
              register-resp (handlers/register-handler register-req)
              user-id (-> register-resp :body :user :id)

              login-req (create-request system
                                        :body {:email (:email test-user-data)
                                               :password (:password test-user-data)})
              login-resp (handlers/login-handler login-req)
              token (-> login-resp :body :token)

              ;; Try to change password with wrong old password
              change-pw-req (create-authenticated-request
                             system token
                             :path {:id user-id}
                             :body {:old-password "wrongoldpassword"
                                    :new-password "newsecurepass456"})]
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"Invalid password"
               (handlers/change-password-handler change-pw-req))))))))



(comment
  (t/run-tests)

  ;
  )
