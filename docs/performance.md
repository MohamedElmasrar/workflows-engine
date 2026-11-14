# Performance Measurements

Measured with `scripts/load-test.py` against the Docker Compose environment on the development machine
(12 CPU threads, 15 GiB RAM, all services and infrastructure on the same host, one replica each, 6 partitions,
worker concurrency 3). Workflow: Manual trigger → Transform → Condition → Log (three worker tasks per execution).
The gateway's execution-start rate limit was raised for the run (`EXECUTION_START_RATE=500`), all other settings default.

| Run | Executions | Client concurrency | Completed | Start request p50 / p95 | End-to-end p50 / p95 / max | Throughput |
|---|---|---|---|---|---|---|
| Baseline | 20 | 1 | 20 | 24 / 30 ms | 808 / 1149 / 1410 ms | 11.3 executions/s (latency-bound, single client) |
| Load | 200 | 20 | 200 | 262 / 614 ms | 5036 / 7352 / 7806 ms | 18.6 executions/s, 55.8 worker tasks/s |

End-to-end = execution `createdAt` to `finishedAt`. Observations:

* Unloaded, each hop (engine → worker → engine) costs roughly 250 ms, mostly the 200 ms outbox poll interval plus Kafka
  round trips. Lowering `platform.outbox.poll-interval` trades latency for database load.
* Under load, latency grows because the worker processes one record per poll with three listener threads; the engine
  was the busiest container (about 25 % CPU of one core at the end of the run). More worker replicas (up to the
  partition count) and a higher `WORKER_CONCURRENCY` are the first levers.
* All 220 executions of both runs reached COMPLETED; none failed or stayed pending.

These numbers describe this single-host setup only and are not a capacity claim for a production deployment.
