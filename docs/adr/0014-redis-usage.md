# ADR-0014: Redis only for rate limiting and the worker attempt cache

Status: accepted

## Context
Redis was optional in the brief.

## Decision
Redis backs the gateway's token-bucket rate limiter (shared across gateway replicas) and the worker's finished-attempt cache. It is not used for execution state or coordination.

## Consequences
**Benefits.** Both uses are tolerant of Redis loss: the rate limiter fails open, the attempt cache degrades to re-running a task (still fenced by the engine).

**Costs and trade-offs.** One more component to operate.
