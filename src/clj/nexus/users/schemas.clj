(ns nexus.users.schemas
  "Malli schemas for user domain validation")

;; ============================================================================
;; Base Schemas
;; ============================================================================

(def EmailSchema
  [:re #"^[^\s@]+@[^\s@]+\.[^\s@]+$"])

(def PasswordSchema
  [:string {:min 8 :max 100}])

(def NameSchema
  [:string {:min 1 :max 100}])

(def UUIDSchema
  [:uuid])

;; ============================================================================
;; Input Schemas
;; ============================================================================

(def UserRegistration
  [:map
   [:first-name NameSchema]
   [:last-name NameSchema]
   [:middle-name {:optional true} [:maybe :string]]
   [:email EmailSchema]
   [:password PasswordSchema]])

(def LoginCredentials
  [:map
   [:email {:json-schema/default "test@example.com"} EmailSchema]
   [:password {:json-schema/default "supersecretpassword"} [:string {:min 1}]]])

(def ChangePassword
  [:map
   [:user-id UUIDSchema]
   [:old-password [:string {:min 1}]]
   [:new-password PasswordSchema]])

(def UpdateUser
  [:map
   [:id UUIDSchema]
   [:updates [:map-of :keyword :any]]])

(def ListUsersParams
  [:map
   [:limit {:optional true} [:int {:min 1 :max 1000}]]
   [:offset {:optional true} [:int {:min 0}]]])

(def SearchParams
  [:map
   [:q [:string {:min 1 :max 100}]]])

(def UserIdParam
  [:map
   [:id UUIDSchema]])
