# ADR-0007: At-least-once delivery with idempotent consumers and fencing

Status: accepted

## Context
Exactly-once delivery across Kafka, PostgreSQL and arbitrary HTTP side effects is not achievable.

## Decision
Producers use the transactional outbox (or synchronous acknowledged sends in the stateless worker); consumers commit offsets after processing and deduplicate by event id in `processed_message`. The engine fences results by attempt id. Workers cache finished attempts in Redis to avoid re-running side effects on redelivery. HTTP nodes send `Idempotency-Key: <executionId>:<nodeId>`.

## Consequences
**Benefits.** State transitions are applied at most once per attempt even with duplicate messages, crashes and redeliveries (tested).

**Costs and trade-offs.** A node handler can run more than once (worker crash mid-call, lease expiry); external side effects may be duplicated unless the target honours the idempotency key. This is documented to users.
