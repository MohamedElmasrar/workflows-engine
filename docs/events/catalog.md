# Event Catalog

All events use the envelope defined in `packages/contracts` (`EventEnvelope`), serialized as JSON.
Kafka record key = `aggregateId`, except node tasks which use `executionId:nodeId` so that parallel nodes of one execution can be processed by different workers while attempts of a node stay ordered. Headers: `traceparent` (W3C), `x-correlation-id`, `x-event-type`.
Payload JSON Schemas live in `packages/contracts/src/main/resources/schemas/events/` and are verified by contract tests.

Rules:
* Additive, optional fields may be added without a version bump. Removing/renaming fields or changing semantics
  requires a new `eventVersion` and, for incompatible streams, a new topic major version.
* Consumers ignore unknown event types and unknown fields.
* Each topic has exactly one producing service (owner).

| Topic | Owner | Key | Events | Consumers |
|---|---|---|---|---|
| `workflow.events.v1` | workflow-service | workflowId | `WorkflowAccessUpdated`, `WorkflowVersionPublished`, `WorkflowArchived` | execution-engine, trigger-service |
| `execution.requests.v1` | trigger-service | workflowId | `WorkflowExecutionRequested` | execution-engine |
| `execution.node-tasks.v1` | execution-engine | executionId:nodeId | `NodeExecutionScheduled` | worker-service |
| `execution.node-results.v1` | worker-service | executionId | `NodeExecutionStarted`, `NodeExecutionCompleted`, `NodeExecutionFailed` | execution-engine |
| `execution.lifecycle.v1` | execution-engine | executionId | `WorkflowExecutionStarted`, `WorkflowExecutionCompleted`, `WorkflowExecutionFailed`, `WorkflowExecutionCancelled`, `WorkflowExecutionTimedOut` | trigger-service (delivery status) |
| `<topic>.dlt` | consumer of `<topic>` | original | original record + `kafka_dlt-*` headers | me, with `scripts/dlt.sh` (see [operations](../runbooks/operations.md)) |

## Payloads (version 1)

### WorkflowAccessUpdated
Full ACL snapshot (replace semantics). `aclRevision` increases monotonically; consumers drop older revisions.
```json
{ "workflowId": "uuid", "ownerId": "keycloak-sub", "aclRevision": 3,
  "entries": [ { "userId": "sub", "role": "EDITOR" } ], "archived": false }
```

### WorkflowVersionPublished
```json
{ "workflowId": "uuid", "version": 2, "name": "Order sync", "ownerId": "sub", "publishedBy": "sub",
  "publishedAt": "instant", "definition": { "schemaVersion": 1, "nodes": [], "edges": [], "settings": {} } }
```

### WorkflowArchived
```json
{ "workflowId": "uuid", "archivedAt": "instant" }
```

### WorkflowExecutionRequested
`executionId` is chosen by the requester so the request is idempotent end to end.
```json
{ "executionId": "uuid", "workflowId": "uuid", "workflowVersion": 2, "triggerType": "WEBHOOK|SCHEDULE",
  "triggeredBy": "webhook:<registrationId>", "input": { }, "idempotencyKey": "delivery-id" }
```

### NodeExecutionScheduled
`config` is the node configuration after template resolution.
```json
{ "executionId": "uuid", "workflowId": "uuid", "nodeId": "http", "nodeType": "HTTP_REQUEST",
  "attemptId": "uuid", "attemptNumber": 1, "timeoutMs": 30000, "config": { } }
```

### NodeExecutionStarted
```json
{ "executionId": "uuid", "nodeId": "http", "attemptId": "uuid", "workerId": "worker-1", "startedAt": "instant" }
```

### NodeExecutionCompleted
```json
{ "executionId": "uuid", "nodeId": "check", "attemptId": "uuid", "workerId": "worker-1",
  "output": { }, "branch": "true", "durationMs": 12, "completedAt": "instant" }
```

### NodeExecutionFailed
```json
{ "executionId": "uuid", "nodeId": "http", "attemptId": "uuid", "workerId": "worker-1",
  "error": { "code": "HTTP_5XX", "message": "Upstream returned 503", "retryable": true },
  "durationMs": 120, "failedAt": "instant" }
```

### WorkflowExecution{Started,Completed,Failed,Cancelled,TimedOut}
```json
{ "executionId": "uuid", "workflowId": "uuid", "workflowVersion": 2, "status": "COMPLETED",
  "occurredAt": "instant", "error": { "code": "NODE_FAILED", "message": "..." } }
```

## Error codes reported by workers

| Code | Retryable | Meaning |
|---|---|---|
| `INVALID_CONFIGURATION` | no | Node configuration failed validation |
| `UNSUPPORTED_NODE_TYPE` | no | No handler registered |
| `TIMEOUT` | yes | Handler exceeded the node timeout |
| `HTTP_5XX`, `HTTP_429` | yes | Upstream server error / throttling |
| `HTTP_4XX` | no | Upstream client error |
| `HTTP_CONNECTION_ERROR` | yes | Connect/read failure |
| `DESTINATION_BLOCKED` | no | SSRF policy rejected the destination |
| `RESPONSE_TOO_LARGE` | no | Body exceeded the configured limit |
| `CIRCUIT_OPEN` | yes | Circuit breaker for the destination is open |
| `NOTIFICATION_FAILED` | yes | Notification channel unavailable |
| `TASK_LEASE_EXPIRED` | yes | Engine-side: no result before the lease expired |
