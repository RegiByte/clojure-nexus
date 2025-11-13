(defproject nexus "0.1.0-SNAPSHOT"
  :description "The nexus project is a personal project. Built for exploration and learning."
  :url "https://github.com/RegiByte/clojure-nexus"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :source-paths ["src/clj"]

  :plugins [[lein-shell "0.5.0"]]

  :dependencies [; Clojure
                 [org.clojure/clojure "1.11.1"]
                 [org.clojure/core.async "1.8.741"]

                 ;; System
                 [aero "1.1.6"]
                 [integrant "0.10.0"]

                 ;; Validation
                 [metosin/malli "0.17.0"]
                 ;; Data Conversion
                 [metosin/jsonista "0.3.13"]
                 [camel-snake-kebab "0.4.3"]

                 ;; HTTP Requests
                 [clj-http "3.13.1"]

                 ;; Database
                 [org.postgresql/postgresql "42.7.5"]
                 [com.zaxxer/HikariCP "6.2.1"]
                 [com.github.seancorfield/next.jdbc "1.3.1070"]
                 [com.github.seancorfield/honeysql "2.6.1270"]
                 [migratus "1.6.3"]

                 ;; Auth + JWT
                 [buddy/buddy-hashers "2.0.167"]
                 [buddy/buddy-sign "3.5.351"]

                 ;; Logging
                 [com.taoensso/telemere "1.1.0"]
                 [com.taoensso/telemere-slf4j "1.1.0"]
                 [org.slf4j/slf4j-api "2.1.0-alpha1"]

                 ;; Routing
                 [ring-cors "0.1.13"]
                 [metosin/reitit "0.7.2"]
                 [metosin/ring-http-response "0.9.5"]
                 [ring/ring-core "1.15.3"]
                 [ring/ring-headers "0.4.0"]

                 ;; Async facilities
                 [manifold "0.4.4"]

                 ;; Web server
                 [aleph "0.9.3"]
                 [ring/ring-jetty-adapter "1.15.3"]]
  :main ^:skip-aot nexus.core
  :target-path "target/%s"

  :aliases {"uberjar-full" ["do" ["uberjar"]]}

  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:source-paths ["dev" "test/clj"]
                   :dependencies [[integrant/repl "0.3.3"]
                                  [djblue/portal "0.61.0"]

                                  [binaryage/devtools "1.0.7"]
                                  ;; Testing
                                  [org.testcontainers/testcontainers "1.20.3"]
                                  [org.testcontainers/postgresql "1.20.3"]]
                   :repl-options {:init-ns nexus.user}}})
