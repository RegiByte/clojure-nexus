(ns nexus.test.users.queries-test
  "Tests for pure HoneySQL query builders"
  (:require
   [clojure.test :as t :refer [deftest is testing]]
   [nexus.users.queries :as queries]))

(deftest find-by-email-query-test
  (testing "builds correct query to find user by email"
    (let [result (queries/find-by-email-query "test@example.com")]
      (is (= {:select [:*]
              :from [:nexus.users]
              :where [:and [:= :email "test@example.com"]
                      [:= nil :deleted_at]]}
             result)))))

(deftest find-by-id-query-test
  (testing "builds correct query to find user by id"
    (let [user-id #uuid "550e8400-e29b-41d4-a716-446655440000"
          result (queries/find-by-id-query user-id)]
      (is (= {:select [:*]
              :from [:nexus.users]
              :where [:and
                      [:= :id user-id]
                      [:= nil :deleted_at]]}
             result)))))

(deftest insert-user-query-test
  (testing "builds correct query to insert a new user"
    (let [user-data {:first-name "John"
                     :last-name "Doe"
                     :middle-name "M"
                     :email "john@example.com"
                     :password-hash "hashed-password"}
          result (queries/insert-user-query user-data)]
      (is (= :nexus.users (get-in result [:insert-into 0])))
      (is (= "John" (get-in result [:values 0 :first_name])))
      (is (= "Doe" (get-in result [:values 0 :last_name])))
      (is (= "john@example.com" (get-in result [:values 0 :email])))
      (is (= "hashed-password" (get-in result [:values 0 :password_hash])))
      (is (vector? (:returning result))))))

(deftest list-users-query-test
  (testing "builds correct query with default pagination"
    (let [result (queries/list-users-query {})]
      (is (= 50 (:limit result)))
      (is (= 0 (:offset result)))
      (is (= [:nexus.users] (:from result)))
      (is (= [[:created_at :desc]] (:order-by result)))))

  (testing "builds correct query with custom pagination"
    (let [result (queries/list-users-query {:limit 10 :offset 20})]
      (is (= 10 (:limit result)))
      (is (= 20 (:offset result))))))

(deftest update-user-query-test
  (testing "builds correct query to update user"
    (let [user-id #uuid "550e8400-e29b-41d4-a716-446655440000"
          updates {:first-name "Jane" :email "jane@example.com"}
          result (queries/update-user-query user-id updates)]
      (is (= :nexus.users (:update result)))
      (is (= [:= :id user-id] (:where result)))
      (is (= [:*] (:returning result)))
      (is (map? (:set result))))))

(deftest soft-delete-user-query-test
  (testing "builds correct query to soft delete user"
    (let [user-id #uuid "550e8400-e29b-41d4-a716-446655440000"
          result (queries/soft-delete-user-query user-id)]
      (is (= :nexus.users (:update result)))
      (is (= [:= :id user-id] (:where result)))
      (is (= [:now] (get-in result [:set :deleted_at])))
      (is (= [:*] (:returning result))))))

(deftest update-password-query-test
  (testing "builds correct query to update password"
    (let [user-id #uuid "550e8400-e29b-41d4-a716-446655440000"
          new-hash "new-hashed-password"
          result (queries/update-password-query user-id new-hash)]
      (is (= :nexus.users (:update result)))
      (is (= [:= :id user-id] (:where result)))
      (is (= new-hash (get-in result [:set :password_hash])))
      (is (= [:id :email] (:returning result))))))

(deftest search-users-query-test
  (testing "builds correct query to search users"
    (let [result (queries/search-users-query "john")]
      (is (= [:nexus.users] (:from result)))
      (is (= [:*] (:select result)))
      (is (vector? (:where result)))
      (is (= :or (first (:where result))))
      (is (= 4 (count (:where result)))) ; :or + 3 conditions
      (is (some #(and (vector? %) (= :ilike (first %))) (rest (:where result)))))))
