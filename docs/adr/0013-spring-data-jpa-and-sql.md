# ADR-0013: Spring Data JPA by default, SQL only for atomic coordination

Status: accepted (supersedes the earlier "trigger service uses plain JDBC")

## Context
All three stateful services need ordinary lookups and updates, and a few operations whose correctness depends on
PostgreSQL features: `FOR UPDATE SKIP LOCKED` (scheduler replicas), `INSERT ... ON CONFLICT` (idempotent
deduplication and revision-guarded projection updates), advisory locks (outbox publisher election), and aggregate
queries with `FILTER`.

## Decision
Use Spring Data JPA repositories (derived queries, JPQL, Specifications, `@Lock`, `@Modifying`) with Lombok entities
for everything that can be expressed safely that way. Keep SQL, with a comment explaining why, for:
* `INSERT ... ON CONFLICT` upserts and dedupe inserts (processed messages, trigger deliveries, ACL and version
  projections, trigger registration, user directory);
* `FOR UPDATE SKIP LOCKED` (as a Spring Data native query in the trigger service);
* the outbox writer/publisher and processed-message store in `platform-support`, which are shared by all services;
* the engine's due-timer UNION, the history batch insert and the dashboard statistics.

## Consequences
**Benefits.** Most queries are type-checked at startup, row mapping disappears, and all services follow one style.
The replica-coordination and idempotency SQL stays explicit and reviewable.

**Costs and trade-offs.** Two persistence styles coexist inside some adapters; entities written by SQL upserts are
read through JPA, so such writes must not run after the same entity was loaded in the same transaction (they don't).
Inserts of domain-assigned ids use `EntityManager.persist` to avoid the extra SELECT that `save()` issues.
