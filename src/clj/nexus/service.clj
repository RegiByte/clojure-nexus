(ns nexus.service)

;; Service Pattern
;; ===============
;; This module implements a lightweight service pattern using partial application.
;; 
;; Problem: Services need dependencies (DB, config, etc.) but we want to:
;; 1. Keep function signatures clean (no deps in every call)
;; 2. Make services inspectable via REPL
;; 3. Avoid heavy abstractions (protocols/records)
;;
;; Solution: Partially apply deps to all service functions at initialization.
;;
;; Example:
;;   (def ops {:generate-token generate-token-fn
;;             :verify-token verify-token-fn})
;;   
;;   (def jwt-service (build {:jwt-secret "secret"} ops))
;;   ; => {:generate-token (partial generate-token-fn {:jwt-secret "secret"})
;;   ;     :verify-token (partial verify-token-fn {:jwt-secret "secret"})}
;;   
;;   ; Now consumers call without providing deps:
;;   ((:generate-token jwt-service) user-data)
;;   ; Instead of:
;;   (generate-token-fn {:jwt-secret "secret"} user-data)

(defn build 
  "Builds a service map by partially applying deps to all operation functions.
   
   Parameters:
   - deps: Map of dependencies (e.g., {:db conn, :jwt-secret \"...\"}
   - ops: Map of operation functions (e.g., {:create-user fn, :find-user fn})
   
   Returns: Map with same keys, but functions have deps pre-applied.
   
   The resulting service can be called like:
     ((:operation-name service) arg1 arg2)
   
   Instead of:
     (operation-name deps arg1 arg2)"
  [deps ops]
  (into {} (for [[k f] ops] [k (partial f deps)])))