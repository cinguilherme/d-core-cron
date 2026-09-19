# d-core-cron

Quartz-backed cron scheduler component and runtime task management for Clojure applications and Duct/Integrant systems.

## Features

- **Integrant / Duct Lifecycle**: `:d-core.libs.cron-task/scheduler` manages the Quartz scheduler startup, task sync, and clean shutdown.
- **Config-Driven Cron Tasks**: Define tasks as a map or vector with standard Quartz cron expressions (including seconds precision).
- **Handler & Dependency Injection**: Scheduled jobs dispatch into registered handler functions with contextual dependencies and execution metadata.
- **Dynamic Task Management**: Runtime APIs for `upsert-task!`, `delete-task!`, `pause-task!`, `resume-task!`, `sync-tasks!`, and `list-task-ids`.
- **Flexible Storage**: Defaults to in-memory `RAMJobStore` for lightweight scheduling, or full JDBC clustering (`JobStoreTX`) with PostgreSQL or MySQL for distributed deployments.
- **Misfire Instructions**: Configurable misfire policies (`:fire-now`, `:do-nothing`, `:ignore-misfires`).
- **Timezone Support**: Schedule tasks in specific timezones (`"UTC"`, `"America/Sao_Paulo"`, etc.).

## Installation

Add to your `deps.edn`:

```clojure
org.clojars.cinguilherme/d-core-cron {:mvn/version "0.1.0"}
```

Or for local development:

```clojure
org.clojars.cinguilherme/d-core-cron {:local/root "../d-core-cron"}
```

## Quick Start

### Integrant / Duct Configuration

```clojure
{:d-core.libs.cron-task/scheduler
 {:handlers {:cleanup #ig/ref :my-app.handlers/cleanup
             :sync    #ig/ref :my-app.handlers/sync}
  :deps {:db #ig/ref :my-app.db/client}
  :tasks {:cleanup {:cron "0 0 * * * ?"
                    :handler :cleanup
                    :payload {:limit 100}
                    :timezone "UTC"
                    :enabled true}
          :sync {:cron "0 */5 * * * ?"
                 :handler :sync
                 :payload {:scope :daily}}}
  :sync-mode :replace
  :start? true
  :shutdown-wait? true
  :quartz {:properties {"org.quartz.threadPool.threadCount" "4"}}}}
```

### Handler Contract

Handlers receive a single execution map:

```clojure
(defn cleanup-handler
  [{:keys [task-id handler payload deps context fire-time scheduled-fire-time]}]
  (println "Running task" task-id "with payload" payload)
  ;; Execute business logic
  )
```

## Documentation

For comprehensive documentation, including task shape, sync modes, clustering, and runtime APIs, see [docs/cron_task.md](docs/cron_task.md).
