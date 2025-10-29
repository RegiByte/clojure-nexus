(ns nexus.test.users.schemas-test
  "Tests for user domain validation schemas"
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [malli.core :as m]
   [nexus.users.schemas :as schemas]))

(deftest email-schema-test
  (testing "validates correct email formats"
    (is (true? (m/validate schemas/EmailSchema "test@example.com")))
    (is (true? (m/validate schemas/EmailSchema "user.name+tag@example.co.uk"))))

  (testing "rejects invalid email formats"
    (is (false? (m/validate schemas/EmailSchema "not-an-email")))
    (is (false? (m/validate schemas/EmailSchema "@example.com")))
    (is (false? (m/validate schemas/EmailSchema "test@")))
    (is (false? (m/validate schemas/EmailSchema "test @example.com")))))

(deftest password-schema-test
  (testing "validates passwords with correct length"
    (is (true? (m/validate schemas/PasswordSchema "12345678")))
    (is (true? (m/validate schemas/PasswordSchema "a-very-secure-password"))))

  (testing "rejects passwords that are too short"
    (is (false? (m/validate schemas/PasswordSchema "short")))
    (is (false? (m/validate schemas/PasswordSchema "1234567")))))

(deftest user-registration-schema-test
  (testing "validates correct user registration data"
    (is (true? (m/validate schemas/UserRegistration
                           {:first-name "John"
                            :last-name "Doe"
                            :email "john@example.com"
                            :password "securepass123"})))

    (is (true? (m/validate schemas/UserRegistration
                           {:first-name "Jane"
                            :last-name "Smith"
                            :middle-name "Marie"
                            :email "jane@example.com"
                            :password "anotherpass456"}))))

  (testing "rejects invalid registration data"
    (is (false? (m/validate schemas/UserRegistration
                            {:first-name "John"
                             :last-name "Doe"
                             :email "invalid-email"
                             :password "securepass123"})))

    (is (false? (m/validate schemas/UserRegistration
                            {:first-name "John"
                             :last-name "Doe"
                             :email "john@example.com"
                             :password "short"})))

    (is (false? (m/validate schemas/UserRegistration
                            {:first-name ""
                             :last-name "Doe"
                             :email "john@example.com"
                             :password "securepass123"})))))

(deftest login-credentials-schema-test
  (testing "validates correct login credentials"
    (is (true? (m/validate schemas/LoginCredentials
                           {:email "test@example.com"
                            :password "anypassword"}))))

  (testing "rejects invalid credentials"
    (is (false? (m/validate schemas/LoginCredentials
                            {:email "invalid-email"
                             :password "password"})))

    (is (false? (m/validate schemas/LoginCredentials
                            {:email "test@example.com"
                             :password ""})))))

(deftest change-password-schema-test
  (testing "validates correct password change data"
    (is (true? (m/validate schemas/ChangePassword
                           {:user-id #uuid "550e8400-e29b-41d4-a716-446655440000"
                            :old-password "oldpass123"
                            :new-password "newpass456"}))))

  (testing "rejects invalid password change data"
    (is (false? (m/validate schemas/ChangePassword
                            {:user-id "not-a-uuid"
                             :old-password "oldpass123"
                             :new-password "newpass456"})))

    (is (false? (m/validate schemas/ChangePassword
                            {:user-id #uuid "550e8400-e29b-41d4-a716-446655440000"
                             :old-password "oldpass123"
                             :new-password "short"})))))

(deftest list-users-params-schema-test
  (testing "validates correct pagination params"
    (is (true? (m/validate schemas/ListUsersParams {})))
    (is (true? (m/validate schemas/ListUsersParams {:limit 10})))
    (is (true? (m/validate schemas/ListUsersParams {:offset 20})))
    (is (true? (m/validate schemas/ListUsersParams {:limit 100 :offset 50}))))

  (testing "rejects invalid pagination params"
    (is (false? (m/validate schemas/ListUsersParams {:limit 0})))
    (is (false? (m/validate schemas/ListUsersParams {:limit 1001})))
    (is (false? (m/validate schemas/ListUsersParams {:offset -1})))))

(deftest search-params-schema-test
  (testing "validates correct search params"
    (is (true? (m/validate schemas/SearchParams {:q "john"})))
    (is (true? (m/validate schemas/SearchParams {:q "a"}))))

  (testing "rejects invalid search params"
    (is (not (m/validate schemas/SearchParams {:q ""})))
    (is (not (m/validate schemas/SearchParams {})))))

(deftest user-id-param-schema-test
  (testing "validates correct user ID param"
    (is (true? (m/validate schemas/UserIdParam
                           {:id #uuid "550e8400-e29b-41d4-a716-446655440000"}))))

  (testing "rejects invalid user ID param"
    (is (false? (m/validate schemas/UserIdParam {:id "not-a-uuid"})))
    (is (false? (m/validate schemas/UserIdParam {:id "550e8400-e29b-41d4-a716-446655440000"})))))


(comment
  (t/run-tests)

  ;
  )