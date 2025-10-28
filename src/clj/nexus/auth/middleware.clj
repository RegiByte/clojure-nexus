(ns nexus.auth.middleware
  (:require [nexus.auth.jwt :as jwt]
            [nexus.errors :as errors]))

(defn- extract-jwt-from-header
  "Extracts JWT from Authorization: Bearer <token>"
  [request]
  (when-let [auth-header (get-in request [:headers "authorization"])]
    (jwt/extract-token-from-header auth-header)))

(comment
  (extract-jwt-from-header {; fake request
                            :headers {"authorization" "Bearer some.token.here"}})
  ;
  )


(defn- extract-jwt-from-cookie
  "Extracts JWT from cookie (e.g. 'auth-header')"
  [request]
  (get-in request [:cookies "auth-token" :value]))

(comment
  (extract-jwt-from-cookie {; fake request
                            :cookies {"auth-token" {:value "some.cookie.here"}}})
  ;
  )

(defn- verify-and-decode
  "Try to verify token and return decoded token+claims, or nil"
  [jwt-service token]
  (when token
    ((:verify-token jwt-service) token)))

(comment
  (require '[nexus.dev-system :as ds])
  (ds/services:jwt)

  (ds/services:jwt)
  (def test-token ((:generate-token (ds/services:jwt))
                   {:id "123"
                    :email "regi@test.com"}

                   {:claims {:roles ["user" "admin"]
                             :some "claim"}}))
  test-token

  ((:verify-token (ds/services:jwt))
   test-token)

  (verify-and-decode
   (ds/services:jwt)
   test-token ;
   )

  (verify-and-decode
   (ds/services:jwt)
   "randombulshitgooooo")
  ;
  )

(defn wrap-jwt-authentication
  "Middleware that attempts to authenticate via JWT (header or cookie)
   Adds :identity to request if successful, but doesn't block if auth fails.
   
   This allows handlers to decide whether authentication is required."
  [handler]
  (fn [request]
    (let [;; Try both sources
          jwt-service (get-in request [:context :jwt]) ; injected through integrant
          token (or (extract-jwt-from-header request)
                    (extract-jwt-from-cookie request))
          ;; Verify and decode if found
          identity (verify-and-decode jwt-service token)
          ;; Add identity to request
          request-with-id (assoc request :identity identity)]
      (handler request-with-id))))

(defn ensure-authenticated!
  "Throws if request doesn't have valid identity.
   Call this at the start of handlers that need authentication"
  [request]
  (when-not (:identity request)
    (throw (errors/unauthorized "Not authenticated.")))
  nil)

(defn req-identity
  "Returns the authenticated identity, may be a user or service,
  Returns nil if no identity is present."
  [request]
  (:identity request))

(defn authenticated?
  "Returns true if request has valid authentication"
  [request]
  (some? (:identity request)))