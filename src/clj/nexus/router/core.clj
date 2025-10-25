(ns nexus.router.core
  (:require
   [nexus.router.api :as nexus-api]
   [nexus.router.web :as web-api]))


(defn routes
  "This is the main entrypoint for application routes.
   No one is required to separate their routes, 
   but I think it looks quite clean and easy to follow."
  []
  [["/api", {:tags #{:api}}, (nexus-api/routes)]
   ["", {:tags #{:web}}, (web-api/routes)]
   ;
   ])