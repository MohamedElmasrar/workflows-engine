# ADR-0009: Transactional outbox with a polling publisher

Status: accepted

## Context
State changes and their events must not diverge (no event without a commit, no commit without an event).

## Decision
`outbox_event` rows are written in the business transaction. A polling publisher (200 ms) holds a PostgreSQL transaction-scoped advisory lock so only one instance per database publishes, in id order, and marks rows published after the broker acknowledged them. Trace context is stored with each row and restored on publication.

## Consequences
**Benefits.** No CDC infrastructure (Debezium) needed; ordering per database; works with any number of replicas.

**Costs and trade-offs.** Adds latency (up to one poll interval per hop, visible in the measured 0.8 s end-to-end time of a three-task workflow) and a single active publisher per service; a crash between ack and mark causes a duplicate publish, which consumers tolerate.
