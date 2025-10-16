(ns nexus.system
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [taoensso.telemere :as tel]))

; Aero
;; Aero configs to properly parse refs and refsets from config.edn
(defmethod aero/reader 'ig/ref [_ _ value]
  (ig/ref value))

(defmethod aero/reader 'ig/refset [_ _ value]
  (ig/refset value))


(defn load-config
  [{:keys [config profile]}]
  (let [config (aero/read-config (io/resource config) {:profile profile})]
    (if (get-in config [::env :aero/missing-include])
      (throw
       (ex-info (str "Missing env file for profile: " profile
                     "\nExpected file: resources/envs/" (name profile) ".edn") {}))

      config)))

(defn load-namespaces
  [config]
  (ig/load-namespaces config)
  config)

(defn prepare
  [config]
  (-> config
      load-config
      load-namespaces))


(defn initialize
  ([]
   ;; No profile? then it is prod!
   (initialize :prod))
  ([profile]
   (initialize profile "system.config.edn"))
  ; Now we can get started :D
  ([profile config-file]
   (tel/log! {} (str "Bootstrapping system in " profile))

   (-> {:config config-file :profile profile}
       prepare
       ig/init)))

(defn initialize-migrations
  ([]
   (initialize-migrations :prod))
  ([profile]
   (-> {:config "system.config.edn" :profile profile}
       prepare
       (ig/init [:nexus.db/migrations]))))


(defmethod ig/init-key ::env [_ env]
  (tel/log! :info (str "initializing env " env))
  env)

(comment

  (load-config {:config "system.config.edn" :profile :dev})
  (load-config {:config "system.config.edn" :profile :prod})

  ;
  )