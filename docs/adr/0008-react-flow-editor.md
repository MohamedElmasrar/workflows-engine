# ADR-0008: React Flow for the visual editor

Status: accepted

## Context
The central UI feature is a graph editor with custom nodes, typed handles, zoom/pan and minimap.

## Decision
`@xyflow/react` with custom node components, dagre for automatic layout, Zustand for editor state, TanStack Query for server state.

## Consequences
**Benefits.** Mature, accessible building blocks; no need to build hit-testing, viewport management or edge routing.

**Costs and trade-offs.** Dependency on a third-party library's data model; mapping functions convert between React Flow state and the definition schema (round-trip tested).
