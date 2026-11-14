# Architecture Decision Records

| ADR | Title |
|---|---|
| [0001](0001-microservices-with-few-services.md) | Microservices with a small number of services |
| [0002](0002-kafka-for-asynchronous-communication.md) | Kafka for asynchronous communication |
| [0003](0003-postgresql-per-service.md) | PostgreSQL, one database per service |
| [0004](0004-durable-database-backed-execution-state.md) | Durable execution engine with database-backed state |
| [0005](0005-hexagonal-architecture.md) | Hexagonal architecture for business services |
| [0006](0006-dag-only-execution.md) | Directed acyclic graphs only |
| [0007](0007-at-least-once-delivery.md) | At-least-once delivery with idempotent consumers and fencing |
| [0008](0008-react-flow-editor.md) | React Flow for the visual editor |
| [0009](0009-transactional-outbox-polling-publisher.md) | Transactional outbox with a polling publisher |
| [0010](0010-event-carried-state-transfer.md) | Projections instead of synchronous calls between services |
| [0011](0011-manual-runs-served-by-engine.md) | Manual executions are served by the execution engine |
| [0012](0012-delay-and-timers-in-engine.md) | Delay nodes are timers in the engine |
| [0013](0013-trigger-service-uses-jdbc.md) | Trigger service persistence with plain JDBC |
| [0014](0014-redis-usage.md) | Redis only for rate limiting and the worker attempt cache |
