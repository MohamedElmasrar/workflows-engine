# Demonstration Guide

Prerequisites: Docker with Compose v2, `curl`, `jq`, `openssl`, Python 3. About 6 GB of free memory.

## 1. Start

```bash
docker compose up -d --build                      # first build takes several minutes
docker compose --profile observability up -d      # Jaeger, Prometheus, Grafana (optional)
docker compose ps                                 # wait until everything is healthy
```

## 2. Happy path: Webhook → Transform → HTTP Request → Condition → Notification

```bash
scripts/smoke-test.sh
```

The script logs in as `demo`, creates and publishes the workflow in `scripts/order-webhook-demo.json`, reads the
webhook URL, rotates the signing secret, sends two signed webhooks and waits for both executions:

* amount 250 → mock fraud API scores 85 → condition `true` → "Alert fraud team" e-mail, "Confirm to customer" SKIPPED
* amount 40 → score 12 → condition `false` → confirmation e-mail, alert SKIPPED
* an unsigned request is rejected with 401.

In the browser (http://localhost:3000, demo/demo) open Executions to see both runs, or open the workflow, press
**Run**-style webhooks again while watching an execution page: the graph and timeline update live over SSE.
E-mails appear in Mailpit at http://localhost:8025.

## 3. Controlled failures

```bash
scripts/demo-failure-recovery.sh
```

Recorded run (2026-11-14, this machine):

| Scenario | What the script does | Observed |
|---|---|---|
| Worker crash | Starts a workflow whose HTTP node calls an 8 s endpoint, `SIGKILL`s `worker-service` while the node is RUNNING, prints the persisted state, restarts the worker | While the worker was dead the node stayed `RUNNING` (attempt 1) and its successor `PENDING`. After restart Kafka redelivered the uncommitted task; the same attempt completed and the execution reached `COMPLETED`. Had the worker stayed down longer than the lease (30 s node timeout + 60 s grace), the engine would have dispatched attempt 2 and fenced attempt 1. |
| Engine crash | Starts a workflow with a 20 s DELAY, `SIGKILL`s `execution-engine`, restarts it | The delay timer (a database column) fired after the restart; the execution completed. |
| Duplicate event | Re-publishes the last `NodeExecutionCompleted` record of that execution with the Kafka console producer | History length unchanged (13 → 13); `workflow_messages_duplicate_total` went from 0 to 1. |

Automated equivalents with assertions: `ExecutionFlowIT` (duplicates, stale attempts, lease recovery, concurrent
completions, cancellation races, delay timer, request ordering, DLT) and `WorkerFlowIT` (redelivery answered from the
attempt cache).

## 4. Traces and metrics

* Jaeger (http://localhost:16686): service `api-gateway`, find a `POST /api/v1/webhooks/...` trace. It continues
  through trigger-service → outbox publish → Kafka → execution-engine → node task → worker-service → node result →
  execution-engine, because trace context is stored with outbox rows and carried in Kafka headers.
* Grafana (http://localhost:3001, dashboards folder "Workflow Platform"): Workflow executions, Worker performance,
  Kafka processing, System health.
* Prometheus (http://localhost:9090): e.g. `sum by (status) (rate(workflow_executions_finished_total[5m]))`.

## 5. Failure inspection in the UI

Create a workflow with an HTTP Request to `http://mock-api:8080/unavailable` and a retry policy of 2 attempts, publish
and run it. The execution fails with `HTTP_5XX`; the node panel shows both attempts and the timeline shows the
scheduled retry. (This is also an automated Playwright test.)
