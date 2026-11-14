# Operational Runbooks (Docker Compose environment)

Commands assume the repository root. For Kubernetes see [kubernetes.md](kubernetes.md); replace
`docker compose exec <svc>` with `kubectl exec deploy/<svc>`.

## Restarting services

```bash
docker compose ps                                   # health of every container
docker compose restart execution-engine             # graceful restart (SIGTERM, in-flight work finishes or is retried)
docker compose up -d --build worker-service         # deploy a new build of one service
```

Restarts are safe at any time: execution state, timers and unsent events live in PostgreSQL; unacknowledged Kafka
messages are redelivered. After a restart, check `/actuator/health/readiness` on the service port.

## Investigating a failed execution

1. Open the execution in the web app (Executions → filter Failed) or `GET /api/v1/executions/{id}`.
   `errorCode`/`errorMessage` on the execution name the failing node; the node detail shows each attempt with its
   error code (see the [error code table](../events/catalog.md#error-codes-reported-by-workers)).
2. Timeline: `GET /api/v1/executions/{id}/events` lists every transition, retry (`NODE_RETRY_SCHEDULED` with
   `nextAttemptAt`) and ignored stale result.
3. Logs: every log line carries `correlationId` and `executionId`:
   `docker compose logs worker-service execution-engine | grep <executionId>`.
4. Trace: with the observability profile running, search Jaeger (http://localhost:16686) for the trace id found in the logs.
5. Fix the cause (configuration, upstream service), then **Retry** creates a new execution with the same input.

## Dead-letter messages

Records that could not be processed after the consumer back-off (or are malformed) are moved to `<topic>.dlt` with
`kafka_dlt-*` headers (exception, original topic/partition/offset, consumer group).

```bash
scripts/dlt.sh list                                    # counts per DLT
scripts/dlt.sh show execution.node-results.v1 5        # records with failure headers
scripts/dlt.sh replay execution.node-results.v1 100    # re-publish to the original topic after fixing the cause
```

Replay uses the consumer group `dlt-replay`, so each DLT record is replayed once. Consumers are idempotent, so a
record that had partly succeeded is harmless to replay. Malformed records go back to the DLT.

## Recovering interrupted tasks

No manual action is normally needed:

* Worker crash: the task is redelivered by Kafka when the partition is reassigned (same attempt, see the demo log),
  or the engine's lease expires (`timeout + ENGINE_LEASE_GRACE`, default 60 s) and a new attempt is dispatched; the
  old attempt is fenced.
* Engine crash: the sweeper on any replica resumes due timers within a second of startup.

To find executions that look stuck:

```sql
-- docker compose exec postgres psql -U execution -d execution
select execution_id, node_id, status, attempt_count, lease_expires_at, available_at, resume_at
from node_execution where status in ('READY','QUEUED','RUNNING') order by coalesce(lease_expires_at, available_at, resume_at);
```

A row with `lease_expires_at` in the past that does not move means the sweeper is not running (check engine logs for
`Sweeper could not advance`). Cancel an execution with `POST /api/v1/executions/{id}/cancel`.

## Diagnosing Kafka connectivity

```bash
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --all-groups
```

* Growing `LAG` for `worker-service`: workers are down or slow (scale `worker-service`, check node timeouts).
* Growing `outbox_events_pending` metric or `Outbox publication failed` warnings: the broker is unreachable; rows are
  kept and published in order once it is back. Producers inside services need no restart.
* `Connection to node -1 could not be established`: wrong `KAFKA_BOOTSTRAP` (inside compose it is `kafka:9092`,
  from the host `localhost:9094`).

## Investigating database failures

* Readiness turns DOWN when the database is unreachable; consumers stop committing offsets, back off and retry, and
  eventually move records to the DLT (replay them afterwards).
* Connection pool: `hikaricp_connections_active/pending` metrics (System health dashboard).
* Migrations: `select * from flyway_schema_history order by installed_rank;` in the service database. Services refuse to
  start when the schema does not match the entities (`ddl-auto: validate`).
* Backups before migrations: `docker compose exec postgres pg_dump -U postgres execution > execution.sql`.
