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
  (hash-password "some super really really long password that is definitely more than
                                            255 characters, it should definitely break the column size
                           until it breaks I'll continue typing, typing and typing until we reach at least 255
                           characters")
  
  (count
   "bcrypt+sha512$290fb0eaa79a072e38ec0c08b5726066$12$dbf5f3a4e3fe8ccb222817ceaf50d14e682bc55a806e5d3d"
   )

  (count "some super really really long password that is definitely more than
                           255 characters, it should definitely break the column size
          until it breaks I'll continue typing, typing and typing until we reach at least 255
          characters")
  ;
  )