# Users Module - Usage Examples

This document provides practical examples of using the refactored users module.

## Table of Contents
- [Basic Usage](#basic-usage)
- [Testing Queries](#testing-queries)
- [Testing Schemas](#testing-schemas)
- [Integration Testing](#integration-testing)
- [Advanced Patterns](#advanced-patterns)

## Basic Usage

### Registering a User

```clojure
(ns my-app.handlers
  (:require [nexus.users.service :as users]))

(defn register-handler [request]
  (let [user-data (get-in request [:body :user])
        result (users/register-user! 
                 {:db (:db request)}
                 user-data)]
    {:status 201
     :body {:user result}}))

;; Example request body:
{:user {:first-name "John"
        :last-name "Doe"
        :email "john@example.com"
        :password "securepass123"
        :middle-name "Michael"}}  ; optional
```

### Authenticating a User

```clojure
(defn login-handler [request]
  (let [credentials (get-in request [:body :credentials])
        {:keys [token user]} (users/authenticate-user
                               {:db (:db request)
                                :jwt (:jwt request)}
                               credentials)]
    {:status 200
     :body {:token token
            :user user}}))

;; Example request body:
{:credentials {:email "john@example.com"
               :password "securepass123"}}
```

### Listing Users with Pagination

```clojure
(defn list-users-handler [request]
  (let [params (select-keys (:params request) [:limit :offset])
        users (users/list-users
                {:db (:db request)}
                params)]
    {:status 200
     :body {:users users}}))

;; Example query params:
;; GET /api/users?limit=20&offset=40
```

### Searching Users

```clojure
(defn search-users-handler [request]
  (let [query (get-in request [:params :q])
        results (users/search
                  {:db (:db request)}
                  query)]
    {:status 200
     :body {:results results}}))

;; Example query:
;; GET /api/users/search?q=john
```

### Updating a User

```clojure
(defn update-user-handler [request]
  (let [user-id (get-in request [:params :id])
        updates (get-in request [:body :updates])
        result (users/update-user!
                 {:db (:db request)}
                 user-id
                 updates)]
    {:status 200
     :body {:user result}}))

;; Example request:
;; PATCH /api/users/550e8400-e29b-41d4-a716-446655440000
{:updates {:first-name "Jane"
           :email "jane@example.com"}}
```

### Changing Password

```clojure
(defn change-password-handler [request]
  (let [user-id (get-in request [:identity :id])  ; from JWT
        password-data (get-in request [:body :password])
        result (users/change-password!
                 {:db (:db request)}
                 (assoc password-data :user-id user-id))]
    {:status 200
     :body {:message "Password changed successfully"
            :user result}}))

;; Example request body:
{:password {:old-password "oldpass123"
            :new-password "newpass456"}}
```

### Deleting a User (Soft Delete)

```clojure
(defn delete-user-handler [request]
  (let [user-id (get-in request [:params :id])
        result (users/delete-user!
                 {:db (:db request)}
                 user-id)]
    {:status 200
     :body {:message "User deleted successfully"
            :user result}}))
```

## Testing Queries

### Unit Testing Query Builders

```clojure
(ns my-app.users.queries-test
  (:require [clojure.test :refer [deftest is testing]]
            [nexus.users.queries :as queries]))

(deftest find-by-email-query-test
  (testing "generates correct HoneySQL structure"
    (let [query (queries/find-by-email-query "test@example.com")]
      ;; Assert on the data structure
      (is (= [:*] (:select query)))
      (is (= [:nexus.users] (:from query)))
      (is (= [:= :email "test@example.com"] (:where query)))
      
      ;; Can also test the entire structure
      (is (= {:select [:*]
              :from [:nexus.users]
              :where [:= :email "test@example.com"]}
             query)))))

(deftest insert-user-query-test
  (testing "includes all required fields"
    (let [user-data {:first-name "John"
                     :last-name "Doe"
                     :email "john@example.com"
                     :password-hash "hashed"}
          query (queries/insert-user-query user-data)]
      (is (= :nexus.users (first (:insert-into query))))
      (is (= "John" (get-in query [:values 0 :first_name])))
      (is (contains? (first (:values query)) :password_hash)))))

(deftest list-users-query-test
  (testing "applies default pagination"
    (let [query (queries/list-users-query {})]
      (is (= 50 (:limit query)))
      (is (= 0 (:offset query)))))
  
  (testing "applies custom pagination"
    (let [query (queries/list-users-query {:limit 10 :offset 20})]
      (is (= 10 (:limit query)))
      (is (= 20 (:offset query))))))
```

### Testing Query Composition

```clojure
(deftest query-composition-test
  (testing "can compose queries"
    (let [base-query (queries/find-by-email-query "test@example.com")
          ;; Can add additional clauses
          extended-query (assoc base-query :limit 1)]
      (is (= 1 (:limit extended-query)))
      (is (= [:= :email "test@example.com"] (:where extended-query))))))
```

## Testing Schemas

### Unit Testing Validation

```clojure
(ns my-app.users.schemas-test
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [nexus.users.schemas :as schemas]))

(deftest user-registration-validation-test
  (testing "valid registration data"
    (is (m/validate schemas/UserRegistration
                    {:first-name "John"
                     :last-name "Doe"
                     :email "john@example.com"
                     :password "securepass123"})))
  
  (testing "invalid email format"
    (is (not (m/validate schemas/UserRegistration
                         {:first-name "John"
                          :last-name "Doe"
                          :email "not-an-email"
                          :password "securepass123"}))))
  
  (testing "password too short"
    (is (not (m/validate schemas/UserRegistration
                         {:first-name "John"
                          :last-name "Doe"
                          :email "john@example.com"
                          :password "short"}))))
  
  (testing "missing required fields"
    (is (not (m/validate schemas/UserRegistration
                         {:first-name "John"
                          :email "john@example.com"})))))

(deftest validation-error-messages-test
  (testing "provides helpful error messages"
    (let [invalid-data {:first-name ""
                        :last-name "Doe"
                        :email "invalid"
                        :password "short"}
          errors (m/explain schemas/UserRegistration invalid-data)]
      (is (some? errors))
      ;; Can inspect error structure
      (is (contains? (me/humanize errors) :first-name))
      (is (contains? (me/humanize errors) :email))
      (is (contains? (me/humanize errors) :password)))))
```

## Integration Testing

### Testing Service Functions with Database

```clojure
(ns my-app.users.service-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [nexus.users.service :as users]
            [nexus.test.test-system :as test-system]))

(use-fixtures :each test-system/with-test-db)

(deftest register-user-integration-test
  (testing "successfully registers a new user"
    (let [user-data {:first-name "John"
                     :last-name "Doe"
                     :email "john@example.com"
                     :password "securepass123"}
          result (users/register-user!
                   {:db test-system/*db*}
                   user-data)]
      (is (uuid? (:id result)))
      (is (= "john@example.com" (:email result)))
      (is (= "John" (:first_name result)))
      (is (not (contains? result :password)))
      (is (not (contains? result :password_hash)))))
  
  (testing "prevents duplicate email registration"
    (let [user-data {:first-name "John"
                     :last-name "Doe"
                     :email "john@example.com"
                     :password "securepass123"}]
      ;; Register first user
      (users/register-user! {:db test-system/*db*} user-data)
      ;; Try to register again with same email
      (is (thrown? Exception
                   (users/register-user! {:db test-system/*db*} user-data))))))

(deftest authenticate-user-integration-test
  (testing "successfully authenticates valid credentials"
    ;; Setup: register a user
    (users/register-user!
      {:db test-system/*db*}
      {:first-name "John"
       :last-name "Doe"
       :email "john@example.com"
       :password "securepass123"})
    
    ;; Test authentication
    (let [{:keys [token user]} (users/authenticate-user
                                 {:db test-system/*db*
                                  :jwt test-system/*jwt*}
                                 {:email "john@example.com"
                                  :password "securepass123"})]
      (is (string? token))
      (is (= "john@example.com" (:email user)))
      (is (not (contains? user :password_hash)))))
  
  (testing "rejects invalid credentials"
    (is (thrown? Exception
                 (users/authenticate-user
                   {:db test-system/*db*
                    :jwt test-system/*jwt*}
                   {:email "john@example.com"
                    :password "wrongpassword"})))))

(deftest list-users-integration-test
  (testing "lists users with pagination"
    ;; Setup: create multiple users
    (doseq [i (range 5)]
      (users/register-user!
        {:db test-system/*db*}
        {:first-name (str "User" i)
         :last-name "Test"
         :email (str "user" i "@example.com")
         :password "password123"}))
    
    ;; Test listing
    (let [result (users/list-users
                   {:db test-system/*db*}
                   {:limit 3 :offset 0})]
      (is (= 3 (count result)))
      (is (every? :email result)))))
```

## Advanced Patterns

### Custom Query Composition

```clojure
(ns my-app.users.custom-queries
  (:require [nexus.users.queries :as queries]))

(defn find-active-users-query
  "Compose a query to find only active (non-deleted) users"
  [params]
  (let [base-query (queries/list-users-query params)]
    (update base-query :where
            (fn [existing-where]
              (if existing-where
                [:and existing-where [:is :deleted_at nil]]
                [:is :deleted_at nil])))))

;; Usage
(db/exec! db (find-active-users-query {:limit 10}))
```

### Conditional Query Building

```clojure
(defn search-users-with-filters-query
  "Build a search query with optional filters"
  [{:keys [search-term role created-after]}]
  (let [base-query (if search-term
                     (queries/search-users-query search-term)
                     (queries/list-users-query {}))
        conditions (cond-> []
                     role (conj [:= :role role])
                     created-after (conj [:> :created_at created-after]))]
    (if (seq conditions)
      (update base-query :where
              (fn [existing]
                (if existing
                  (into [:and existing] conditions)
                  (if (= 1 (count conditions))
                    (first conditions)
                    (into [:and] conditions)))))
      base-query)))

;; Usage
(db/exec! db (search-users-with-filters-query
               {:search-term "john"
                :role "admin"
                :created-after #inst "2024-01-01"}))
```

### Batch Operations

```clojure
(defn register-users-batch!
  "Register multiple users in a transaction"
  [{:keys [db]} users-data]
  ;; Validate all users first
  (doseq [user-data users-data]
    (users/validate! schemas/UserRegistration user-data))
  
  ;; Execute in transaction
  (db/with-transaction [tx db]
    (mapv (fn [user-data]
            (users/register-user! {:db tx} user-data))
          users-data)))

;; Usage
(register-users-batch!
  {:db db}
  [{:first-name "John" :last-name "Doe" 
    :email "john@example.com" :password "pass123"}
   {:first-name "Jane" :last-name "Smith"
    :email "jane@example.com" :password "pass456"}])
```

### Query Inspection and Debugging

```clojure
(ns my-app.debug
  (:require [nexus.users.queries :as queries]
            [honey.sql :as sql]))

(defn inspect-query
  "Inspect the SQL that will be generated from a query"
  [query-fn & args]
  (let [query (apply query-fn args)
        [sql params] (sql/format query)]
    (println "Query:" query)
    (println "SQL:" sql)
    (println "Params:" params)
    query))

;; Usage in REPL
(inspect-query queries/find-by-email-query "test@example.com")
;; Query: {:select [:*], :from [:nexus.users], :where [:= :email "test@example.com"]}
;; SQL: SELECT * FROM nexus.users WHERE email = ?
;; Params: ["test@example.com"]
```

### Validation with Custom Error Handling

```clojure
(defn register-user-with-friendly-errors!
  "Register user with user-friendly error messages"
  [{:keys [db]} user-data]
  (try
    (users/register-user! {:db db} user-data)
    (catch Exception e
      (let [error-data (ex-data e)]
        (cond
          ;; Validation error
          (= (:type error-data) :validation-error)
          (throw (ex-info "Please check your input"
                          {:status 400
                           :errors (get-in error-data [:details])}))
          
          ;; Conflict error (duplicate email)
          (= (:type error-data) :conflict)
          (throw (ex-info "This email is already registered"
                          {:status 409
                           :field :email}))
          
          ;; Other errors
          :else
          (throw e))))))
```

## Tips and Best Practices

1. **Always validate at the service layer**: Don't skip validation even if you trust the input
2. **Test queries in isolation**: Use the query builders for fast unit tests
3. **Use transactions for multi-step operations**: Ensure data consistency
4. **Log important operations**: Use telemere for observability
5. **Handle errors gracefully**: Provide meaningful error messages to users
6. **Compose queries when needed**: Build on top of existing query builders
7. **Keep queries pure**: Query builders should have no side effects
8. **Validate early**: Catch validation errors before database operations

