# ADR-0011: Manual executions are served by the execution engine

Status: accepted

## Context
The brief assigns manual triggers to the trigger service. A manual run is a direct user command that wants the new execution id synchronously.

## Decision
`POST /api/v1/workflows/{id}/executions` is routed to the engine, which authorizes against its ACL projection and creates the execution in the same transaction. Webhooks and schedules stay in the trigger service and reach the engine through `execution.requests.v1`.

## Consequences
**Benefits.** One hop less, immediate 201 with the execution, idempotency via `Idempotency-Key`.

**Costs and trade-offs.** Two entry points create executions (REST and Kafka); both use the same `ExecutionService.start` command.
