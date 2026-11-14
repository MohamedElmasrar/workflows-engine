# ADR-0006: Directed acyclic graphs only

Status: accepted

## Context
Loops and cycles complicate join semantics, termination and history size.

## Decision
Version 1 accepts DAGs only; cycles are rejected at publication (Kahn's algorithm). Join rule: a node runs when all incoming edges are resolved and at least one is active; nodes on unselected branches are SKIPPED and skips propagate.

## Consequences
**Benefits.** Termination is guaranteed, join semantics are simple and explainable, scheduling is incremental (only successors of a finished node are evaluated).

**Costs and trade-offs.** No loops or 'for each' over arrays yet; users must model iteration outside the platform. A future version can add bounded loop nodes.
