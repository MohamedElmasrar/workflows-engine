# ADR-0010: Projections instead of synchronous calls between services

Status: accepted

## Context
The engine needs published versions and ACLs; the trigger service needs trigger configuration and ACLs. Synchronous calls would couple availability and add authentication between services.

## Decision
The workflow service publishes full ACL snapshots (`WorkflowAccessUpdated`, with a monotonic `aclRevision`) and complete immutable versions (`WorkflowVersionPublished`). Consumers keep local projections.

## Consequences
**Benefits.** No runtime dependency on the workflow service; execution history can be filtered by visibility with a local join.

**Costs and trade-offs.** Eventual consistency: right after publishing, the engine may answer `409 version-not-available` (the UI retries) and the trigger API may briefly return 404. An execution request that arrives before the version projection is retried with back-off by the consumer.
