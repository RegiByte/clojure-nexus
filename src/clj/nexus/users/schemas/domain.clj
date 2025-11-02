(ns nexus.users.schemas.domain
  "Domain/service layer schemas - kebab-case for business logic.
   These schemas represent data as it flows through the service layer."
  (:require [nexus.users.schemas.base :as base]
            [malli.util :as mu]))

;; ============================================================================
;; Service Layer Input Schemas
;; ============================================================================

(def UserRegistration
  "User registration data in service layer (kebab-case)"
  [:map
   [:first-name base/NameSchema]
   [:last-name base/NameSchema]
   [:middle-name {:optional true} [:maybe :string]]
   [:email base/EmailSchema]
   [:password base/PasswordSchema]])

(def LoginCredentials
  "Login credentials in service layer"
  [:map
   [:email base/EmailSchema]
   [:password [:string {:min 1}]]])

(def ChangePassword
  "Password change request in service layer"
  [:map
   [:user-id base/UUIDSchema]
   [:old-password [:string {:min 1}]]
   [:new-password base/PasswordSchema]])

(def UpdateUserFields
  "Base fields for user updates"
  [:map
   [:first-name base/NameSchema]
   [:last-name base/NameSchema]
   [:middle-name [:maybe :string]]
   [:email base/EmailSchema]])

(def UpdateUser
  "User update data in service layer"
  [:map
   [:id base/UUIDSchema]
   [:updates (mu/optional-keys UpdateUserFields)]])

(def ListUsersParams
  "Pagination parameters for listing users"
  [:map
   [:limit {:optional true} [:int {:min 1 :max 1000}]]
   [:offset {:optional true} [:int {:min 0}]]])

(def SearchParams
  "Search parameters"
  [:map
   [:q [:string {:min 1 :max 100}]]])