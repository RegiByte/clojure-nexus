(ns nexus.http.request
  (:require [nexus.router.helpers :as rh]))

(defn router
  " Extracts the reitit router from the request (auto-injected router) "
  [req]
  (:reitit.core/router req))

(defn named-url
  " Generates a url for a request using a route name "
  [req route-name & args]
  (apply rh/named-url (router req) route-name args))

