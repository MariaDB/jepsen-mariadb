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
            [clojure.java.shell :refer [sh]]
            [org.httpkit.client :as http]
            [slingshot.slingshot :refer [try+ throw+]]))

(def default-home-dir "/home/mariadb")
(def default-cnf-path (str default-home-dir "/.my.cnf"))
(def default-install-dir (str default-home-dir "/mariadb-bin"))
(def default-data-dir (str default-install-dir "/data"))

(defn cnf-path
  "Mariadb config file path"
  [test]
  (let [opt-tmp-dir (:mariadb-tmp-dir test)
        opt-server-num (:mariadb-server-number test)]
    (str opt-tmp-dir "/my-" opt-server-num ".cnf")))

(defn data-dir
  "Mariadb data directory path"
  [test]
  (let [opt-data-dir (:mariadb-data-dir test)
        opt-server-num (:mariadb-server-number test)]
    (if (some? opt-data-dir)
      (str opt-data-dir "-" opt-server-num) default-data-dir)))

(defn pid-file
  "MariaDB pid file"
  [test]
  (str (data-dir test) "/mariadbd.pid"))

(defn install-dir
  "MariaDB install dir"
  [test]
  (let [opt-install-dir (:mariadb-install-dir test)]
    (if (some? opt-install-dir) opt-install-dir default-install-dir)))

(defn socket
  "MariaDB socket file"
  [test]
  (let [opt-server-num (:mariadb-server-number test)
        opt-tmp-dir (:mariadb-tmp-dir test)]
  (str opt-tmp-dir "/mariadb-" opt-server-num ".sock")))

(defn configure!
  "Writes config files"
  [test]
  (let [opt-cnf-path (cnf-path test)
        opt-data-dir (data-dir test)
        opt-error-log (str opt-data-dir "/error.log")
        opt-pid-file (pid-file test)
        opt-server-num (:mariadb-server-number test)
        opt-tmp-dir (:mariadb-tmp-dir test) 
        port (str (+ 3306 opt-server-num))
        opt-socket (socket test)]
    (spit opt-cnf-path
      (-> (io/resource "maria-docker.cnf")
           slurp
           (str/replace #"%TMPDIR%" opt-tmp-dir)
           (str/replace #"%PORT%" port)
           (str/replace #"%SOCKET%" opt-socket)
           (str/replace #"%PID_FILE%" opt-pid-file)
           (str/replace #"%DATA_DIR%" opt-data-dir)
           (str/replace #"%ERROR_LOG%" opt-error-log)
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
           ))))

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
        (sh (str (install-dir test) "/scripts/mariadb-install-db")
            (str "--defaults-file=" (cnf-path test)))
        (db/start! this test node)
        (make-db! test))

      (teardown! [this test node]
        (if-not (:dont-teardown test)
          ((db/kill! this test node)
           (sh "rm" "-rf" (data-dir test))
           (sh "rm" (cnf-path test)))))

      db/LogFiles
      (log-files [this test node] [])

      db/Kill
      (start! [this test node]
        (let [opt-cnf-path (cnf-path test)
              opt-install-dir (install-dir test)
              bin-dir (str opt-install-dir "/bin")
              rr-trace-dir (:rr test)]
           (if (some? rr-trace-dir)
             (future (sh "rr" "record"
                      (str bin-dir "/mariadbd")
                      (str "--defaults-file=" opt-cnf-path)
                      (str "--basedir=" opt-install-dir)
                      (str "--plugin-dir=" opt-install-dir "/lib/plugin")
                      "--skip-grant-tables"
                      :env {:_RR_TRACE_DIR rr-trace-dir}))
             (sh (str bin-dir "/mariadbd-safe")
                 (str "--defaults-file=" opt-cnf-path)
                 "--no-auto-restart" "--skip-grant-tables"))
           (while
             (not= (:exit
                     (sh (str bin-dir "/mysqladmin")
                         (str "--defaults-file=" opt-cnf-path)
                         (str "--socket=" (socket test))
                         "-uroot" "ping"))
                   0)
               (info "Waiting for mariadbd start...")
               (Thread/sleep 1000))
           (info "mariadbd started")))

      (kill! [this test node]
        (let [opt-cnf-path (cnf-path test)
              opt-install-dir (install-dir test)
              bin-dir (str opt-install-dir "/bin")]
        (sh (str bin-dir "/mysqladmin")
            (str "--defaults-file=" opt-cnf-path)
            (str "--socket=" (socket test))
            "-uroot" "shutdown" "--shutdown-timeout=3600"))))))
