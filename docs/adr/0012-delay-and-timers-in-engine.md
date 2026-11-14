# ADR-0012: Delay nodes are timers in the engine

Status: accepted

## Context
Delays must not block worker threads and must survive restarts.

## Decision
DELAY nodes never go to a worker: the engine moves them to RUNNING with `resume_at` and the sweeper completes them.

## Consequences
**Benefits.** No round trip through Kafka; durable by construction; tested across an engine kill.

**Costs and trade-offs.** The worker's handler registry does not contain DELAY; node types are split between engine-executed (triggers, DELAY) and worker-executed.
