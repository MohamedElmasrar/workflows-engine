# ADR-0005: Hexagonal architecture for business services

Status: accepted

## Context
Business rules (state machines, scheduling, graph validation, authorization) must be testable without Spring, Kafka or a database.

## Decision
Packages `domain`, `application` (use cases and ports), `infrastructure` (adapters) and `presentation` (REST). ArchUnit tests enforce that the domain does not depend on Spring, JPA, Kafka or HTTP clients. Jackson `JsonNode` is allowed in the domain as the representation of user-defined JSON. In-ports are concrete use-case classes rather than interfaces, because each has a single implementation.

## Consequences
**Benefits.** Fast unit tests (the engine's 128 domain tests run in about a second); adapters can change without touching rules.

**Costs and trade-offs.** Mapping code between domain and JPA entities; more files than a layered CRUD application.
