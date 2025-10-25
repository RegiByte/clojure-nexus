(ns nexus.auth.jwt
  (:require
   [buddy.sign.jwt :as jwt]
   [clojure.string :as str]
   [integrant.core :as ig]
   [nexus.service :as service])
  (:import
   [java.time Instant]))

;; JWT Token Generation
(defn generate-token
  "Generate a JWT token for a user.
   Options:
   - :exp-hours - hours until expiration (default 24)
   - :claims - additional claims to include"
  ([opts user-data] (generate-token opts user-data {}))
  ([{:keys [jwt-secret jwt-options]}
    user-data
    {:keys [exp-hours claims]
     :or {exp-hours 24 claims {}}}]
   (let [now (Instant/now)
         exp (.plusSeconds now (* exp-hours 3600))
         token-claims (merge
                       {:user-id (:id user-data)
                        :email (:email user-data)
                        :iat (.getEpochSecond now)
                        :exp (.getEpochSecond exp)}
                       claims)]
     (jwt/sign token-claims jwt-secret jwt-options))))

(defn verify-token
  "Verify and decode a JWT token. Returns the claims if valid, nil otherwise"
  [{:keys [jwt-secret jwt-options]} token]
  (try
    (jwt/unsign token jwt-secret jwt-options)
    (catch Exception _e
      nil)))

(defn token-valid?
  "Check if a token is valid without decoding"
  [{:keys [jwt-secret jwt-options]} token]
  (some? (verify-token {:jwt-secret jwt-secret
                        :jwt-options jwt-options} token)))


;; Note: this is not part of the service, it's a standalone helper
;; may make sense to move this to http
(defn extract-token-from-header
  "Extracts bearer token from Authorization header"
  [auth-header]
  (when auth-header
    (let [parts (str/split auth-header #"\s+")]
      (when (and (= 2 (count parts))
                 (= "Bearer" (first parts)))
        (second parts)))))


(comment
  ; First generate a token

  (def test-jwt-secret "super-secret-string-string-used-for-encrypting-jwt")
  (def test-jwt-options {:alg :hs256})

  (generate-token {:jwt-secret test-jwt-secret
                   :jwt-options test-jwt-options}

                  {:id "123"
                   :email "regi@test.com"}

                  {:claims {:roles ["user" "admin"]
                            :some "claim"}})

  ; Check if it's a valid one
  (token-valid? {:jwt-secret test-jwt-secret
                 :jwt-options test-jwt-options}
                "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyLWlkIjoiMTIzIiwiZW1haWwiOiJyZWdpQHRlc3QuY29tIiwiaWF0IjoxNzYxMzUwMTM2LCJleHAiOjE3NjE0MzY1MzYsInJvbGVzIjpbInVzZXIiLCJhZG1pbiJdLCJzb21lIjoiY2xhaW0ifQ.iMBjVxbf0YUGsgJFbgFkmcBegG_N3ldiXXOJkaOAiMI"
                ;
                )

  ; Extract token info + claims
  (verify-token {:jwt-secret test-jwt-secret
                 :jwt-options test-jwt-options}
                "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyLWlkIjoiMTIzIiwiZW1haWwiOiJyZWdpQHRlc3QuY29tIiwiaWF0IjoxNzYxMzUwMTM2LCJleHAiOjE3NjE0MzY1MzYsInJvbGVzIjpbInVzZXIiLCJhZG1pbiJdLCJzb21lIjoiY2xhaW0ifQ.iMBjVxbf0YUGsgJFbgFkmcBegG_N3ldiXXOJkaOAiMI"
                ;
                )


  ; Take it from the header
  (extract-token-from-header "Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VyLWlkIjoiMTIzIiwiZW1haWwiOiJyZWdpQHRlc3QuY29tIiwiaWF0IjoxNzYxMzM3Mjc3LCJleHAiOjE3NjE0MjM2NzcsInJvbGVzIjpbInVzZXIiLCJhZG1pbiJdLCJzb21lIjoiY2xhaW0ifQ.l8agP7t3vVUjpeqirAmkY9KZNFKoqtilIsDRhVkMSV0"
                             ;
                             )

  ;
  )

(def ops
  {:generate-token generate-token
   :verify-token verify-token
   :token-valid? token-valid?})

(defmethod ig/init-key :nexus.auth/jwt [_ {:keys [options]}]
  (let [{:keys [jwt-secret]} options]
    (service/build
     {:jwt-secret jwt-secret
      :jwt-options {:alg :hs256}}
     ops)))

