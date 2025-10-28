(ns nexus.auth.hashing
  (:require
   [buddy.hashers :as hashers]))


;; Password Hashing
(defn hash-password
  "Hash a plaintext password using bcrypt+sha512"
  [password]
  (hashers/derive password {:alg :bcrypt+sha512}))

(defn verify-password
  "Check if a raw password matches the given hash"
  [password hash]
  (hashers/check password hash))

;; Since this is a simple facility for hashing/checking passwords
;; and it does not depend on any external values
;; it's not integrated through integrant
;; use the fns directly

(comment
  (def test-hash (hash-password "secretpassword"))
  (verify-password "secretpassword" test-hash)
  (verify-password "secretpassword--" test-hash)
  
  ;
  )