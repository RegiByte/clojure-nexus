(ns nexus.errors
  "Custom error types for consistent HTTP error responses.
   
   How this works:
   1. Service layer throws errors using these functions
   2. Exception middleware catches them by ::type
   3. Middleware extracts :status and formats response
   
   Example flow:
     Service: (throw (unauthorized \"Invalid token\" {:user-id 123}))
     → Exception middleware catches ::unauthorized
     → Returns: {:status 401, :body {:error \"Invalid token\", :data {...}}}
   
   Why ex-info?
   - Clojure's standard exception with data
   - Middleware can inspect ex-data for :type, :status, :details
   - Preserves stack traces for debugging
   
   Error types map to HTTP status codes:
   - ::validation  → 400 Bad Request
   - ::unauthorized → 401 Unauthorized
   - ::forbidden   → 403 Forbidden
   - ::not-found   → 404 Not Found
   - ::conflict    → 409 Conflict")

(defn validation-error
  ([details] (validation-error "Validation failed" details))
  ([message details]
   (ex-info message
            {:type ::validation
             :status 400
             :details details})))

(defn not-found
  ([resource] (not-found resource nil))
  ([resource id]
   (ex-info (str resource " not found")
            {:type ::not-found
             :status 404
             :resource resource
             :id id})))

(defn conflict
  ([message] (conflict message nil))
  ([message data]
   (ex-info message
            {:type ::conflict
             :status 409
             :data data})))

(defn unauthorized
  ([] (unauthorized "Unauthorized"))
  ([message] (unauthorized message nil))
  ([message data]
   (ex-info message
            {:type ::unauthorized
             :status 401
             :data data})))

(defn forbidden
  ([] (forbidden "Forbidden"))
  ([message]
   (ex-info message
            {:type ::forbidden
             :status 403})))

(defn error-type [ex]
  (when (instance? clojure.lang.ExceptionInfo ex)
    (:type (ex-data ex))))

(defn validation-error? [ex]
  (= ::validation (error-type ex)))

(defn not-found? [ex]
  (= ::not-found (error-type ex)))

(comment
  (let [ex (validation-error "failed to validate"
                             {:reason :because-yes!})]
    {:type (error-type ex)
     :validation-error? (validation-error? ex)
     :not-found-error? (not-found? ex)})

  ;
  )