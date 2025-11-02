(ns nexus.users.schemas.base
  "Shared primitive schemas used across all layers.
   These are layer-agnostic and can be reused in API, domain, and DB schemas.")

;; ============================================================================
;; Primitive Schemas
;; ============================================================================

(def EmailSchema
  "Valid email address format"
  [:re {:json-schema/default "test@example.com"} 
   #"^[^\s@]+@[^\s@]+\.[^\s@]+$"])

(def PasswordSchema
  "Password requirements: 8-100 characters"
  [:string {:min 8 :max 100}])

(def NameSchema
  "Name field: 1-100 characters"
  [:string {:min 1 :max 100}])

(def UUIDSchema
  "UUID identifier"
  [:uuid])

(def InstantSchema
  "Timestamp/instant"
  inst?)

(def OptionalString
  "Optional string field"
  [:maybe :string])
