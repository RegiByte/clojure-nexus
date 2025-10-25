(ns nexus.service)

;; This file contains common helpers for building and working with services
;; Note: This was purposely designed to be dead-simple
;; There is no fancy abstraction, no protocols and records
;; Just mapping and partially applied functions
;; The code that consumes this services should not be concerned
;; with providing the deps

(defn build 
  "Builds a service map by partially applying the deps to the first arg
   The resulting map has the same keys and can be inspected through a REPL"
  [deps ops]
  (into {} (for [[k f] ops] [k (partial f deps)])))