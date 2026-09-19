(ns d-core.libs.cron-task-test
  (:require [clojure.test :refer [deftest is testing run-tests]]
            [integrant.core :as ig]
            [d-core.libs.cron-task :as cron]))

;; =============================================================================
;; Normalization & Validation Unit Tests
;; =============================================================================

(deftest normalize-tasks-from-map
  (let [tasks (#'cron/normalize-tasks
               {:cleanup {:cron "0 0 * * * ?" :handler :cleanup}})
        task (first tasks)]
    (is (= 1 (count tasks)))
    (is (= :cleanup (:id task)))
    (is (= true (:enabled task)))))

(deftest normalize-tasks-from-vector
  (let [tasks (#'cron/normalize-tasks
               [{:id :sync :cron "0 */5 * * * ?" :handler :sync}])
        task (first tasks)]
    (is (= 1 (count tasks)))
    (is (= :sync (:id task)))
    (is (= true (:enabled task)))))

(deftest validate-task-happy-path
  (let [task {:id :cleanup :cron "0 0 * * * ?" :handler :cleanup}]
    (is (= task (#'cron/validate-task! task {:cleanup (fn [_])})))))

(deftest validate-task-invalid-cron
  (is (thrown? clojure.lang.ExceptionInfo
               (#'cron/validate-task! {:id :bad :cron "nope" :handler :x}
                                      {:x (fn [_])}))))

(deftest validate-task-missing-handler
  (is (thrown? clojure.lang.ExceptionInfo
               (#'cron/validate-task! {:id :bad :cron "0 0 * * * ?" :handler :missing}
                                      {}))))

(deftest validate-task-invalid-misfire
  (is (thrown? clojure.lang.ExceptionInfo
               (#'cron/validate-task! {:id :bad :cron "0 0 * * * ?"
                                       :handler :x :misfire :nope}
                                      {:x (fn [_])}))))

;; =============================================================================
;; Scheduler Lifecycle & Runtime Operations Tests
;; =============================================================================

(deftest scheduler-lifecycle-and-task-ops
  (testing "Scheduler starts, performs task operations, and halts cleanly"
    (let [executed (atom [])
          handler-fn (fn [job-input]
                       (swap! executed conj job-input))
          config {:handlers {:task-a handler-fn
                             :task-b handler-fn}
                  :deps {:foo "bar"}
                  :tasks {:task-a {:cron "0 0 * * * ?"
                                   :handler :task-a
                                   :payload {:data 123}
                                   :description "Test task A"}}
                  :start? true
                  :shutdown-wait? false
                  :quartz {:properties {"org.quartz.threadPool.threadCount" "2"}}}
          scheduler-comp (ig/init-key :d-core.libs.cron-task/scheduler config)]
      (try
        (is (some? scheduler-comp))
        (is (.isStarted (:scheduler scheduler-comp)))
        (is (= [:task-a] (cron/list-task-ids scheduler-comp)))

        (testing "upsert-task! dynamically adds a new task"
          (cron/upsert-task! scheduler-comp
                             {:id :task-b
                              :cron "0 30 * * * ?"
                              :handler :task-b
                              :payload {:data 456}})
          (let [ids (set (cron/list-task-ids scheduler-comp))]
            (is (contains? ids :task-a))
            (is (contains? ids :task-b))))

        (testing "pause-task! and resume-task!"
          (is (true? (cron/pause-task! scheduler-comp :task-b)))
          (is (true? (cron/resume-task! scheduler-comp :task-b))))

        (testing "delete-task! removes task from scheduler"
          (is (true? (cron/delete-task! scheduler-comp :task-b)))
          (is (= [:task-a] (cron/list-task-ids scheduler-comp))))

        (testing "sync-tasks! with :replace prunes unspecified jobs"
          (cron/sync-tasks! scheduler-comp [{:id :task-b
                                             :cron "0 15 * * * ?"
                                             :handler :task-b}])
          (is (= [:task-b] (cron/list-task-ids scheduler-comp))))

        (finally
          (ig/halt-key! :d-core.libs.cron-task/scheduler scheduler-comp)
          (is (.isShutdown (:scheduler scheduler-comp))))))))

(deftest job-execution-dispatch
  (testing "Quartz triggers job and delivers payload and dependencies to handler"
    (let [delivered (promise)
          handler-fn (fn [input]
                       (deliver delivered input))
          ;; Cron expression that fires every second
          config {:handlers {:fast-task handler-fn}
                  :deps {:system-name "test-system"}
                  :tasks {:fast-task {:cron "* * * * * ?"
                                      :handler :fast-task
                                      :payload {:msg "hello-cron"}}}
                  :start? true
                  :shutdown-wait? false
                  :quartz {:properties {"org.quartz.threadPool.threadCount" "2"}}}
          scheduler-comp (ig/init-key :d-core.libs.cron-task/scheduler config)]
      (try
        (let [result (deref delivered 3000 :timeout)]
          (is (not= :timeout result) "Handler was not invoked within 3 seconds")
          (when (not= :timeout result)
            (is (= :fast-task (:task-id result)))
            (is (= :fast-task (:handler result)))
            (is (= {:msg "hello-cron"} (:payload result)))
            (is (= {:system-name "test-system"} (:deps result)))
            (is (some? (:fire-time result)))))
        (finally
          (ig/halt-key! :d-core.libs.cron-task/scheduler scheduler-comp))))))
