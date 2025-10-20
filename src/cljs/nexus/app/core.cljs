(ns nexus.app.core
  (:require
   [reagent.dom.client :as rdomc]
   [nexus.app.views :as views]))

(defonce app-root
  (delay (rdomc/create-root (.getElementById js/document "app"))))


(defn mount-root []
  (rdomc/render @app-root [views/app]))

(defn ^:export init! []
  (println "Initializing Nexus app...")
  (mount-root))

(defn ^:dev/after-load reload! []
  (println "Reloading...")
  (mount-root))

(comment
  (js/console.log "Hi there")
  (println "Does this goes to js? from core")
  ;
  )