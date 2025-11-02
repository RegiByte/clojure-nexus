(ns nexus.router.helpers
  (:require [reitit.core :as reitit]))

(defn named-url
  "Finds a route by name and generates a URL path with parameters.
   
   Parameters:
   - router: Reitit router instance
   - route-name: Keyword route name (e.g., :api/users-list)
   - params: Path parameters (e.g., {:id \"123\"})
   - query-params: Query string parameters (e.g., {:limit 10})
   
   Returns: URL string or nil if route not found or params invalid
   
   Examples:
     (named-url router :homepage)
     ; => \"/\"
     
     (named-url router :api/users-get {:id \"abc-123\"})
     ; => \"/api/users/abc-123\"
     
     (named-url router :api/users-list nil {:limit 10 :offset 20})
     ; => \"/api/users?limit=10&offset=20\"
   
   Note: Returns nil instead of throwing on invalid params.
   This is intentional for graceful handling in request contexts."
  ([router route-name]
   (named-url router route-name nil))
  ([router route-name params]
   (named-url router route-name params nil))
  ([router route-name params query-params]
   (try
     (-> router
         (reitit/match-by-name route-name params)
         (reitit/match->path query-params))
     (catch java.lang.IllegalArgumentException _e
       nil))))

(defn url->route
  [router url]
  (-> router
      (reitit/match-by-path url)))


