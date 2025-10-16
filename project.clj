(defproject nexus "0.1.0-SNAPSHOT"
  :description "The nexus project is a personal project. Built for exploration and learning."
  :url "https://github.com/RegiByte/clojure-nexus"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [; Clojure
                 [org.clojure/clojure "1.11.1"]

                 ;; System
                 [aero "1.1.6"]
                 [integrant "0.10.0"]

                 ;; Validation
                 [metosin/malli "0.17.0"]

                 ;; Database
                 [org.postgresql/postgresql "42.7.5"]
                 [com.zaxxer/HikariCP "6.2.1"]
                 [com.github.seancorfield/next.jdbc "1.3.981"]
                 [com.github.seancorfield/honeysql "2.6.1270"]
                 [migratus "1.6.3"]

                 ;; Logging
                 [com.taoensso/telemere "1.1.0"]
                 [com.taoensso/telemere-slf4j "1.1.0"]
                 [org.slf4j/slf4j-api "2.1.0-alpha1"]

                 ;; Routing
                 [metosin/reitit "0.7.2"]
                 [metosin/ring-http-response "0.9.5"]
                 [ring/ring-core "2.0.0-alpha1"]
                 [ring/ring-jetty-adapter "2.0.0-alpha1"]]
  :main ^:skip-aot nexus.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:source-paths ["dev"]
                   :dependencies [[integrant/repl "0.3.3"]
                                  [djblue/portal "0.61.0"]]
                   :repl-options {:init-ns user}}})
