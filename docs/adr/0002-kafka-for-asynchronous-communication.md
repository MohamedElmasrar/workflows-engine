# ADR-0002: Kafka for asynchronous communication

Status: accepted

## Context
Engine and workers must exchange tasks and results asynchronously, survive restarts of either side and allow several worker replicas. Other services need workflow changes and lifecycle events.

## Decision
Apache Kafka (KRaft, no ZooKeeper) with versioned topics `<context>.<stream>.v<major>`, JSON envelopes, consumer groups per service and dead-letter topics `<topic>.dlt`.

## Consequences
**Benefits.** Durable log with replay, consumer groups give load balancing and redelivery after crashes, per-key ordering (results keyed by execution id), rich tooling.

**Costs and trade-offs.** Operationally heavier than RabbitMQ or a database queue. Kafka is not a task queue: there is no per-message ack or delay, so retries with back-off and long timers are handled by the engine's database, not by Kafka. Partition count caps consumer parallelism (6 in the default setup).
