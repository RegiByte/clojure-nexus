(ns nexus.router.helpers
  (:require [reitit.core :as reitit]))

(defn named-url
  " Finds a route by name in the registered routes
  If found it turns it into a url path, accepting path params and query params "
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


