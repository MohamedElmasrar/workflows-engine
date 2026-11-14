# ADR-0013: Trigger service persistence with plain JDBC

Status: accepted

## Context
The trigger tables are flat and the scheduler relies on `FOR UPDATE SKIP LOCKED` and `INSERT ... ON CONFLICT`.

## Decision
Use `JdbcTemplate` in the trigger service; JPA/Hibernate is used where aggregates benefit from it (workflow service, execution engine).

## Consequences
**Benefits.** The SQL that coordinates replicas is explicit and reviewable.

**Costs and trade-offs.** Two persistence styles in the codebase.
