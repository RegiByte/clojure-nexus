(ns nexus.users.schemas.api
  "API layer schemas - camelCase for HTTP requests/responses.
   
   Why separate API and domain schemas?
   
   1. Different naming conventions:
      - API: camelCase (JavaScript/JSON standard)
      - Domain: kebab-case (Clojure standard)
   
   2. Different validation rules:
      - API: Strict validation, reject unknown fields
      - Domain: May include internal fields (password-hash, etc.)
   
   3. API stability:
      - API schemas define external contract (breaking changes affect clients)
      - Domain schemas can change without affecting API
   
   4. Documentation:
      - API schemas generate OpenAPI/Swagger docs
      - Domain schemas are internal implementation
   
   Example:
     API schema:    [:map [:firstName :string]]
     Domain schema: [:map [:first-name :string]]
     
     HTTP handler transforms between them:
       Request: {:firstName \"John\"} → {:first-name \"John\"}
       Response: {:first-name \"John\"} → {:firstName \"John\"}"
  (:require
   [malli.util :as mu]
   [nexus.users.schemas.base :as base]))

;; ============================================================================
;; Request Schemas (Input)
;; ============================================================================

(def UserRegistration
  "POST /api/users - User registration request body"
  [:map
   [:firstName base/NameSchema]
   [:lastName base/NameSchema]
   [:middleName {:optional true} [:maybe :string]]
   [:email base/EmailSchema]
   [:password base/PasswordSchema]])

(def LoginCredentials
  "POST /api/auth/login - Login request body"
  [:map
   [:email {:json-schema/default "test@example.com"} base/EmailSchema]
   [:password {:json-schema/default "supersecretpassword"} [:string {:min 1}]]])

(def ChangePasswordRequest
  "PUT /api/users/:id/password - Password change request body"
  [:map
   [:oldPassword [:string {:min 1}]]
   [:newPassword base/PasswordSchema]])

(def UpdateUserFields
  "Base fields for user updates"
  [:map
   [:firstName base/NameSchema]
   [:lastName base/NameSchema]
   [:middleName [:maybe :string]]
   [:email base/EmailSchema]])
(def UpdateUserRequestParameters
  "PATCH /api/users/:id - User update request parameters"
  [:map
   [:id base/UUIDSchema]])
(def UpdateUserRequestBody
  "PATCH /api/users/:id - User update request body"
  (mu/optional-keys UpdateUserFields))

(def ListUsersParams
  "GET /api/users - Query parameters for listing users"
  [:map
   [:limit {:optional true} [:int {:min 1 :max 1000}]]
   [:offset {:optional true} [:int {:min 0}]]])

(def SearchParams
  "GET /api/users/search - Query parameters for searching users"
  [:map
   [:q [:string {:min 1 :max 100}]]])

(def UserIdParam
  "Path parameter for user ID"
  [:map
   [:id base/UUIDSchema]])

;; ============================================================================
;; Response Schemas (Output)
;; ============================================================================

(def User
  "User entity in API responses (camelCase)"
  [:map
   [:id base/UUIDSchema]
   [:email :string]
   [:firstName :string]
   [:lastName :string]
   [:middleName {:optional true} [:maybe :string]]
   [:createdAt base/InstantSchema]
   [:updatedAt base/InstantSchema]
   [:deletedAt {:optional true} [:maybe base/InstantSchema]]])

(def UserList
  "List of users in API response"
  [:sequential User])

(def AuthResponse
  "Authentication response with token and user"
  [:map
   [:message :string]
   [:token :string]
   [:user User]])

(def UserResponse
  "Generic user response with message"
  [:map
   [:message :string]
   [:user User]])

(def ChangePasswordResponse
  "Password change success response"
  [:map
   [:message :string]
   [:user [:map
           [:id base/UUIDSchema]
           [:email base/EmailSchema]]]])

(def ErrorResponse
  "Error response schema"
  [:map
   [:error :string]
   [:details {:optional true} :any]])
