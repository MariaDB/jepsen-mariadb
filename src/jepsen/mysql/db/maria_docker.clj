(ns jepsen.mysql.db.maria-docker
  "Automates setting up and tearing down MariaDB."
  (:require [clojure [pprint :refer [pprint]]
                     [string :as str]]
            [clojure.java.io :as io]
            [clojure.tools.logging :refer [info warn]]
            [jepsen [control :as c]
                    [core :as jepsen]
                    [db :as db]
                    [util :as util :refer [meh]]]
            [jepsen.control [net :as cn]
                            [util :as cu]]
            [jepsen.os.debian :as debian]
            [jepsen.mysql [client :as mc]]
            [jepsen.mysql.db.maria :as db.maria]
            [next.jdbc :as j]
            [next.jdbc.result-set :as rs]
            [clojure.java.shell :as shell]
            [org.httpkit.client :as http]
            [slingshot.slingshot :refer [try+ throw+]]))

(def home-dir "/home/mariadb")
(def cnf-path (str home-dir "/.my.cnf"))
(def install-dir (str home-dir "/mariadb-bin"))
(def bin-dir (str install-dir "/bin"))
(def scripts-dir (str install-dir "/scripts"))
(def data-dir (str install-dir "/data"))
(def error-log (str data-dir "/error.log"))

(defn configure!
  "Writes config files"
  [test]
  (spit cnf-path
    (-> (io/resource "maria-docker.cnf")
         slurp
         (str/replace #"%PID_FILE%" (str data-dir "/mariadbd.pid"))
         (str/replace #"%DATA_DIR%" data-dir)
         (str/replace #"%ERROR_LOG%" error-log)
         (str/replace #"%IP%" "127.0.0.1")
         (str/replace #"%SERVER_ID%" "1")
         (str/replace #"%REPLICA_PRESERVE_COMMIT_ORDER%" (:replica-preserve-commit-order test))
         (str/replace #"%BINLOG_FORMAT%"
                      (.toUpperCase (name (:binlog-format test))))
         (str/replace #"%BINLOG_TRANSACTION_DEPENDENCY_TRACKING%"
                      (case (:binlog-transaction-dependency-tracking test)
                        :commit-order "COMMIT_ORDER"
                        :writeset "WRITESET"
                        :writeset-session "WRITESET_SESSION"))
         ; This option doesn't exist in Maria AFAICT
         (str/replace #"replica-preserve-commit-order.*?\n" "")
         ; Followers are super-read-only to prevent updates from
         ; accidentally arriving. Note that if we *don't* do this, mysql
         ; will murder itself by trying to run replication transactions at
         ; the same time as read queries and letting the read queries take
         ; locks, breaking the replication update thread entirely? This
         ; might be the worst system
         ; I've ever worked on.
         (str/replace #".*%SUPER_READ_ONLY%.*" "")
         (str/replace #"%INNODB_FLUSH_LOG_AT_TRX_COMMIT%"
                      (str (:innodb-flush-log-at-trx-commit test)))
         )))

(defn make-db!
  "Adds a user and DB with remote access."
  [test]
  (let [c (mc/await-open test "localhost" {:db "mysql"})]
    (j/execute! c [(str "CREATE DATABASE " mc/db)])
    (if (:innodb-strict-isolation test)
      (j/execute! c ["SET GLOBAL innodb_snapshot_isolation=ON"]))))

(defn db
  "A MySQL database. Takes CLI options."
  [opts]
  (let [; A promise which will receive the file and position of the leader node
        repl-state (promise)]
    (reify
      db/DB
      (setup! [this test node]
        (configure! test)
        (shell/sh (str scripts-dir "/mariadb-install-db"))
        (db/start! this test node)
        (make-db! test))

      (teardown! [this test node]
        (db/kill! this test node)
        (shell/sh "rm" "-rf" data-dir)
        (shell/sh "rm" cnf-path))

      db/LogFiles
      (log-files [this test node] [])

      db/Kill
      (start! [this test node]
        (shell/sh (str bin-dir "/mariadbd-safe") "--no-auto-restart" "--skip-grant-tables"))

      (kill! [this test node]
        (shell/sh (str bin-dir "/mysqladmin") "-uroot" "shutdown" "--shutdown-timeout=3600")))))
