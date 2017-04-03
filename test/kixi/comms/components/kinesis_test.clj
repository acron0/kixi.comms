(ns kixi.comms.components.kinesis-test
  (:require [kixi.comms.components.kinesis :refer :all]
            [clojure
             [spec :as s]
             [test :refer :all]]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as timbre :refer [error info]]
            [environ.core :refer [env]]
            [kixi.comms.schema]
            [kixi.comms :as comms]
            [kixi.comms.components.test-base :refer :all]
            [kixi.comms.components.all-component-tests :as all-tests]
            [amazonica.aws.dynamodbv2 :as ddb]
            [amazonica.aws.kinesis :as kinesis]))

(def test-kinesis (or (env :kinesis-endpoint) "kinesis.eu-central-1.amazonaws.com"))
(def test-dynamodb (or (env :dynamodb-endpoint) "http://localhost:8000"))

(def profile (env :profile "local"))
(def app-name "kixi-comms-test")

(def test-region "eu-central-1")
(def test-stream-names {:command (str "kixi-comms-test-" profile "-command")
                        :event   (str "kixi-comms-test-" profile "-event")})

(def dynamodb-table-names [(event-worker-app-name app-name profile)
                           (command-worker-app-name app-name profile)])

(def system (atom nil))

(defn table-exists?
  [table]
  (try
    (ddb/describe-table {:endpoint test-dynamodb} table)
    (catch Exception e false)))

(defn clear-tables
  [endpoint table-names]
  (doseq [sub-table-names (partition-all 10 table-names)]
    (doseq [table-name sub-table-names]
      (ddb/delete-table {:endpoint endpoint} :table-name table-name))
    (loop [tables sub-table-names]
      (when (not-empty tables)
        (recur (doall (filter table-exists? tables)))))))

(defn cycle-system-fixture*
  [system-func system-atom]
  (fn [all-tests]
    [all-tests]
    (timbre/with-merged-config
      {:level :info}
      (system-func system-atom)
      (all-tests)

      (component/stop-system @system-atom)
      (reset! system-atom nil)

      (info "Deleting tables...")
      (clear-tables test-dynamodb dynamodb-table-names)

      (info "Deleting streams...")
      (delete-streams! test-kinesis (vals test-stream-names))

      (info "Finished"))))

(defn kinesis-system
  [system]
  (when-not @system
    (comms/set-verbose-logging! true)
    (reset! system
            (component/start-system
             (component/system-map
              :kinesis
              (map->Kinesis {:profile profile
                             :app app-name
                             :endpoint test-kinesis
                             :dynamodb-endpoint test-dynamodb
                             :streams test-stream-names
                             :metric-level :NONE}))))))

(use-fixtures :once (cycle-system-fixture* kinesis-system system))

(def opts {})

(def long-wait 500)

(deftest kinesis-command-roundtrip-test
  (binding [*wait-per-try* long-wait]
    (all-tests/command-roundtrip-test (:kinesis @system) opts)))

(deftest kinesis-event-roundtrip-test
  (binding [*wait-per-try* long-wait]
    (all-tests/event-roundtrip-test (:kinesis @system) opts)))

(deftest kinesis-only-correct-handler-gets-message
  (binding [*wait-per-try* long-wait]
    (all-tests/only-correct-handler-gets-message (:kinesis @system) opts)))

(deftest kinesis-multiple-handlers-get-same-message
  (binding [*wait-per-try* long-wait]
    (all-tests/multiple-handlers-get-same-message (:kinesis @system) opts)))

(deftest kinesis-roundtrip-command->event
  (binding [*wait-per-try* long-wait]
    (all-tests/roundtrip-command->event (:kinesis @system) opts)))

(deftest kinesis-roundtrip-command->event-with-key
  (binding [*wait-per-try* long-wait]
    (all-tests/roundtrip-command->event-with-key (:kinesis @system) opts)))

(deftest kinesis-roundtrip-command->multi-event
  (binding [*wait-per-try* long-wait]
    (all-tests/roundtrip-command->multi-event (:kinesis @system) opts)))

(deftest kinesis-processing-time-gt-session-timeout
  (binding [*wait-per-try* long-wait]
    (all-tests/processing-time-gt-session-timeout (:kinesis @system) opts)))

(deftest kinesis-detaching-a-handler
  (binding [*wait-per-try* long-wait]
    (all-tests/detaching-a-handler (:kinesis @system) opts)))

(deftest kinesis-infinite-loop-defended
  (binding [*wait-per-try* long-wait]
    (all-tests/infinite-loop-defended (:kinesis @system) opts)))
