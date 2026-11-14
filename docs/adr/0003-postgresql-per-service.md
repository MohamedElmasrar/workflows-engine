# ADR-0003: PostgreSQL, one database per service

Status: accepted

## Context
Each service needs transactional storage; the engine needs row-level concurrency control, JSON documents for definitions/outputs and efficient timer queries.

## Decision
PostgreSQL 17. Locally one server with one database and one role per service; Flyway migrations owned by each service; JSONB for definitions, inputs and outputs; partial indexes for timer queries.

## Consequences
**Benefits.** Mature transactions, `SKIP LOCKED`, advisory locks, JSONB and partial indexes cover outbox, scheduler coordination and timers without extra infrastructure.

**Costs and trade-offs.** A shared server in development is a single point of failure and could tempt cross-database queries; roles are restricted to their own database to prevent that.
