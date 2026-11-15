# Design decisions

Notes on the choices I made while building this, why I made them, and what they cost me. A lot of these I would
make differently for a small real product. This project exists so I could practise distributed-systems patterns
end to end, so some of the complexity is deliberate.

## Microservices, but only five

A modular monolith would honestly have been simpler. I split the system anyway because I wanted to deal with the
real problems that come with it: eventual consistency, idempotent consumers, tracing across services. I stopped at
five services (gateway, workflow, execution engine, worker, trigger) where each one has a clearly different job and
failure profile, and didn't add a separate notification or credentials service.

The price: local development needs Docker Compose, and data between services is only eventually consistent (more on
that below).

## Kafka between the services

Tasks and results go through Kafka (KRaft mode, no ZooKeeper). It gives me a durable log, redelivery after crashes,
consumer groups to spread work over several workers, and per-key ordering.

What Kafka is not: a task queue. There is no per-message ack and no delayed delivery, so retries with back-off and
long delays are handled by timers in the engine's database instead. The partition count (6) also caps how many
consumers can work in parallel.

## PostgreSQL, one database per service

PostgreSQL covers everything I needed without extra infrastructure: transactions, JSONB for workflow definitions and
node outputs, `SKIP LOCKED`, advisory locks and partial indexes for the timer queries. Locally it's one server with a
separate database and role per service, and each role can only reach its own database so I can't cheat with
cross-service queries.

## The database is the source of truth for executions

This is the core decision of the project. An execution (with its nodes and attempts) is loaded, changed by a pure
domain method, and saved together with its history entries and outgoing events in one transaction. Every timer
(retry back-off, delay, task lease, execution deadline) is a column, and a sweeper on every engine instance polls
for due ones every second.

Because nothing important lives in memory, killing the engine at any point and starting it again just works. The
failure demo script shows exactly that. The cost is up to one second of timer latency and a small constant query
load. It's not a replay engine like Temporal; workflows are declarative graphs, not code.

## Hexagonal architecture

I wanted the state machines, scheduling rules, graph validation and permission checks to be testable without Spring,
Kafka or a database. So each service has `domain`, `application`, `infrastructure` and `presentation` packages, and
ArchUnit tests fail the build if the domain imports Spring, JPA, Kafka or an HTTP client. The engine's ~130 domain
tests run in about a second because of this. It does mean mapping code between domain objects and JPA entities.

## Only DAGs, no loops

Loops make join semantics, termination and history size much harder, so version 1 only accepts acyclic graphs
(cycles are rejected when publishing). The join rule: a node runs once all its incoming edges are resolved and at
least one is active; nodes on a branch that wasn't taken are SKIPPED and that propagates. Iteration ("for each
item") is the obvious missing feature.

## At-least-once delivery, made safe

Exactly-once across Kafka, PostgreSQL and arbitrary HTTP calls isn't possible, so I didn't pretend:
* events are written to an outbox table in the same transaction as the state change;
* consumers remember processed event ids, so a redelivered message is ignored;
* every dispatch gets an attempt id, and results from an old attempt are ignored (fencing);
* workers cache finished attempts in Redis so a redelivered task doesn't run its side effect again;
* HTTP Request nodes send `Idempotency-Key: <executionId>:<nodeId>` for APIs that support it.

State transitions happen at most once per attempt (tested). A handler can still run twice if a worker crashes in the
middle of an HTTP call, so external side effects can repeat. That's documented rather than hidden.

## Transactional outbox with a polling publisher

Outbox rows are published by a poller every 200 ms. A PostgreSQL advisory lock makes sure only one instance per
database publishes, so order is kept. I chose polling over Debezium/CDC to avoid more infrastructure. It adds latency,
most of the ~0.8 s a three-step workflow takes end to end, and a crash between "broker acknowledged" and "marked as
published" sends a row twice, which the consumers handle.

## Services keep local copies instead of calling each other

The engine needs published workflow versions and permissions; the trigger service needs trigger settings and
permissions. Rather than synchronous calls, the workflow service publishes full permission snapshots (with a
revision number) and complete versions, and the other services store them. No service depends on another one being
up at request time.

The downside is a short window right after publishing where the engine doesn't know the new version yet. Starting a
run then answers `409 version-not-available`, and the UI simply retries.

## Manual runs go straight to the engine

Webhooks and schedules go through the trigger service, but a manual run is a direct user action that wants the
execution id back immediately, so `POST /api/v1/workflows/{id}/executions` goes to the engine. Both paths end in the
same `ExecutionService.start` command.

## Delay nodes are engine timers

A delay never reaches a worker: the engine marks the node RUNNING with a `resume_at` time and the sweeper completes it.
No worker thread is blocked, and a delay survives restarts (also covered by the failure demo).

## Spring Data JPA by default, SQL where it matters

Normal lookups and updates use Spring Data repositories (derived queries, Specifications, `@Lock`, `@Modifying`) with
Lombok entities. I kept SQL, with a comment at each spot, where correctness depends on PostgreSQL features:
* `INSERT ... ON CONFLICT` for deduplication and "only apply if the revision is newer" updates; the JPA version
  (select, then insert or update) has a race window;
* `FOR UPDATE SKIP LOCKED` so several trigger-service instances never fire the same schedule;
* the outbox and processed-message store in `platform-support`, shared by every service;
* the engine's due-timer query, the history batch insert and the dashboard counts.

## Lombok, carefully

Lombok removes getters, setters, constructors and logger fields. `lombok.config` forbids `@Data` and `@Value`
because they break JPA entity identity. Domain classes only get read-only getters, so state can still only change
through methods that enforce the state machine.

## React Flow for the editor

Building a graph editor from scratch (hit testing, zoom, edge routing) wasn't the point. React Flow gives me that;
I write custom nodes, a Zustand store for the draft, and two pure functions that convert between React Flow state
and the definition JSON (round-trip tested).

## Redis only where losing it is harmless

Redis backs the gateway's rate limiter and the worker's finished-attempt cache. If Redis goes away, the rate limiter
lets requests through and the worker might re-run a task (which the engine still fences). Nothing about execution
state lives there.
