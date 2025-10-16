;; =============================================================================
;; Portal - Visual Data Inspector
;; =============================================================================
;; Portal is a tool for visualizing Clojure data structures in development.
;; Think of it as an advanced version of println with a GUI.
;;
;; Features:
;; - Beautiful rendering of nested data structures
;; - Multiple viewers (table, tree, JSON, etc.)
;; - Search and filter capabilities
;; - Time-travel debugging (see history of tapped values)
;;
;; Integration with tap>:
;; - Clojure's tap> is like a debug console
;; - Portal subscribes to tap> calls
;; - Any (tap> data) sends data to Portal's UI
;;
;; Typical workflow:
;; 1. (start-portal!) - opens Portal in browser
;; 2. Add (tap> ...) calls in your code
;; 3. Exercise your code
;; 4. Inspect tapped values in Portal UI
;; 5. (stop-portal!) when done
;;
;; Learn more: https://github.com/djblue/portal

(ns portal
  (:require [portal.api :as p]))

;; =============================================================================
;; Portal State Management
;; =============================================================================

(def p
  "Holds the Portal instance.
   nil when stopped, contains Portal session when running."
  nil)

(defn start-portal!
  "Opens Portal in your default browser and subscribes to tap>.
   
   Once running, any (tap> data) call will send data to Portal.
   Idempotent - won't start twice if already running."
  []
  (if p
    (println "Portal already started")
    (do (alter-var-root #'p (constantly (p/open)))
        ;; Subscribe Portal to tap> - now all taps go to Portal
        (add-tap #'p/submit))))

(defn stop-portal!
  "Closes Portal and unsubscribes from tap>."
  []
  (when p
    ;; Unsubscribe from tap>
    (remove-tap #'p/submit)
    ;; Close the Portal window
    (p/close)
    (alter-var-root #'p (constantly nil))))

(defn restart-portal!
  "Convenience function to restart Portal.
   
   Useful if Portal window gets closed accidentally."
  []
  (stop-portal!)
  (start-portal!))

(defn see-docs!
  "Opens Portal's documentation in your browser."
  []
  (p/docs))

;; =============================================================================
;; Rich Comment Block - REPL Usage Examples
;; =============================================================================

(comment
  ;; Portal lifecycle
  (start-portal!)
  (stop-portal!)
  (restart-portal!)
  (see-docs!)

  ;; Send data to Portal
  (tap> "hello world!")
  (tap> "this is awesome!")
  (tap> {:message "Portal shows nested data beautifully"
         :data [1 2 3 4 5]
         :nested {:maps {:work "great"}}})

  ;; Inspect the Portal instance
  p

  ;; Paren gate
  )
