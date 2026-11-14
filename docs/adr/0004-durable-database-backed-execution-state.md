# ADR-0004: Durable execution engine with database-backed state

Status: accepted

## Context
Executions must survive engine and worker crashes, support retries with back-off, delays of up to 7 days, leases and timeouts. Kafka alone cannot express this state.

## Decision
The execution database is the source of truth. The `WorkflowExecution` aggregate (execution + nodes + attempts) is loaded, changed by a pure domain method and saved with its history entries and outbox effects in one transaction. All timers (retry `available_at`, delay `resume_at`, task `lease_expires_at`, execution `deadline_at`) are columns polled by a sweeper on every replica.

## Consequences
**Benefits.** No in-memory state is needed to recover: a restarted engine continues from the database. Every transition is persisted and observable.

**Costs and trade-offs.** Polling adds up to one sweeper interval (1 s) of timer latency and constant small load on the database. This is not a replay-based engine like Temporal: workflow logic is a declarative DAG, not code, so there is no deterministic replay requirement.
