# Users Module

This module implements the user management domain with a clean separation of concerns.

## Architecture

The module is organized into three layers:

### 1. Queries (`queries.clj`)

Pure HoneySQL query builders that return data structures. These functions:
- Have **no side effects**
- Take simple parameters (strings, UUIDs, maps)
- Return HoneySQL data structures
- Can be **tested in isolation** without a database

**Example:**
```clojure
(queries/find-by-email-query "user@example.com")
;; => {:select [:*], :from [:nexus.users], :where [:= :email "user@example.com"]}
```

**Benefits:**
- Easy to test without database
- Composable and reusable
- Clear separation between query logic and execution
- Can be inspected and debugged easily

### 2. Schemas (`schemas.clj`)

Malli validation schemas for all user operations. These define:
- Input validation rules
- Type constraints
- Business rules (e.g., password length, email format)

**Example:**
```clojure
(def UserRegistration
  [:map
   [:first-name [:string {:min 1 :max 100}]]
   [:last-name  [:string {:min 1 :max 100}]]
   [:middle-name {:optional true} [:maybe :string]]
   [:email EmailSchema]
   [:password [:string {:min 8 :max 100}]]])
```

**Benefits:**
- Centralized validation logic
- Self-documenting API contracts
- Reusable across different layers (API, service, etc.)
- Can generate JSON schemas for API documentation

### 3. Service (`service.clj`)

Orchestration layer that:
- **Validates inputs** using schemas
- **Builds queries** using query functions
- **Executes queries** via database connection
- Implements **business logic** (password hashing, authentication, etc.)
- Handles **error cases** and logging

**Example:**
```clojure
(defn register-user!
  [{:keys [db]} data]
  (validate! schemas/UserRegistration data)  ; Validate input
  (when (find-by-email {:db db} (:email data))
    (throw (errors/conflict "Email already registered")))
  (let [password-hash (hashing/hash-password (:password data))
        query (queries/insert-user-query       ; Build query
               (assoc data :password-hash password-hash))]
    (db/exec-one! db query)))                  ; Execute query
```

## Testing Strategy

### Query Tests
Test pure HoneySQL data structures without database:

```clojure
(deftest find-by-email-query-test
  (is (= {:select [:*]
          :from [:nexus.users]
          :where [:= :email "test@example.com"]}
         (queries/find-by-email-query "test@example.com"))))
```

### Schema Tests
Test validation rules with Malli:

```clojure
(deftest user-registration-schema-test
  (is (m/validate schemas/UserRegistration
                  {:first-name "John"
                   :last-name "Doe"
                   :email "john@example.com"
                   :password "securepass123"})))
```

### Service Tests
Test business logic with database (integration tests):

```clojure
(deftest register-user-test
  (with-test-db [db]
    (let [result (service/register-user! 
                  {:db db}
                  {:first-name "John"
                   :last-name "Doe"
                   :email "john@example.com"
                   :password "securepass123"})]
      (is (uuid? (:id result)))
      (is (= "john@example.com" (:email result))))))
```

## API Functions

### Query Functions (Pure)

- `find-by-email-query` - Find user by email
- `find-by-id-query` - Find user by ID
- `insert-user-query` - Insert new user
- `list-users-query` - List users with pagination
- `update-user-query` - Update user fields
- `soft-delete-user-query` - Soft delete user
- `update-password-query` - Update password
- `search-users-query` - Search users by name/email

### Service Functions (Side Effects)

- `register-user!` - Register new user with validation
- `authenticate-user` - Authenticate and return JWT token
- `change-password!` - Change user password
- `list-users` - List users with pagination
- `update-user!` - Update user fields
- `delete-user!` - Soft delete user
- `search` - Search users
- `find-by-email` - Find user by email
- `find-by-id` - Find user by ID

## Validation Schemas

- `UserRegistration` - New user registration
- `LoginCredentials` - Login credentials
- `ChangePassword` - Password change request
- `UpdateUser` - User update request
- `ListUsersParams` - Pagination parameters
- `SearchParams` - Search parameters
- `UserIdParam` - User ID parameter

## Benefits of This Architecture

1. **Testability**: Query builders can be tested without database
2. **Composability**: Queries can be composed and reused
3. **Validation**: All inputs validated at service layer
4. **Separation of Concerns**: Clear boundaries between layers
5. **Maintainability**: Easy to understand and modify
6. **Type Safety**: Malli schemas provide runtime type checking
7. **Documentation**: Schemas serve as API documentation

## Usage Example

```clojure
(ns my-app.api
  (:require [nexus.users.service :as users]))

;; Register a new user
(users/register-user! 
  {:db db-conn}
  {:first-name "John"
   :last-name "Doe"
   :email "john@example.com"
   :password "securepass123"})

;; Authenticate user
(users/authenticate-user
  {:db db-conn :jwt jwt-service}
  {:email "john@example.com"
   :password "securepass123"})

;; List users with pagination
(users/list-users
  {:db db-conn}
  {:limit 20 :offset 0})

;; Search users
(users/search
  {:db db-conn}
  "john")
```

## Future Enhancements

- Add query composition helpers for complex queries
- Add more granular validation schemas
- Add query result transformers
- Add caching layer for frequently accessed queries
- Add audit logging for sensitive operations

