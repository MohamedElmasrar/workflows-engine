# ADR-0001: Microservices with a small number of services

Status: accepted

## Context
The platform has clearly separable capabilities with different scaling and failure profiles: authoring (low traffic, CRUD with validation), orchestration (stateful, latency-sensitive), task execution (CPU/IO-bound, side effects, horizontally scaled) and triggering (public webhook endpoint, time-based). The portfolio goal also requires demonstrating distributed-systems practice.

## Decision
Five deployable services (gateway, workflow, execution engine, worker, trigger) plus Keycloak. No further split (for example no separate notification or credential service) until a requirement justifies it.

## Consequences
**Benefits.** Workers scale independently of the engine; the public webhook surface is isolated from the authoring API; each service owns its schema.

**Costs and trade-offs.** More moving parts than a modular monolith: eventual consistency between services (version and ACL projections), distributed tracing becomes necessary, local development needs Docker Compose. A modular monolith would have been simpler for a single team and is a valid alternative.
