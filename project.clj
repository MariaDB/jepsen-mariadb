(defproject jepsen.mysql "0.0.1-SNAPSHOT"
  :description "Tests for MySQL with read replicas"
  :url "https://github.com/jepsen-io/jepsen"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [cheshire "5.11.0"]
                 [elle "0.1.8-SNAPSHOT"]
                 [jepsen "0.3.4-SNAPSHOT"]
                 [com.github.seancorfield/next.jdbc "1.3.883"]
                 [com.mysql/mysql-connector-j "8.1.0"]
                 [org.mariadb.jdbc/mariadb-java-client "3.2.0"]]
  :main jepsen.mysql.cli
  :repl-options {:init-ns jepsen.mysql}
  :jvm-opts ["-Djava.awt.headless=true"
             "-agentpath:/home/aphyr/yourkit/bin/linux-x86-64/libyjpagent.so=disablestacktelemetry,exceptions=disable,delay=1000"
             "-server"])
