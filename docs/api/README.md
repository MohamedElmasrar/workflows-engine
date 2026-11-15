# REST API (v1)

Reference for the API the web app uses; handy with `curl` too (see `scripts/lib.sh`).

All endpoints are served through the gateway at `http://localhost:8080`. Every `/api/**` call except webhooks needs
`Authorization: Bearer <access token>` issued by Keycloak realm `workflow-platform` (audience `workflow-api`).
Each service also publishes OpenAPI at `/v3/api-docs` (Swagger UI at `/swagger-ui.html` on the service port).

Errors use RFC 9457 Problem Details (`application/problem+json`):

```json
{ "type": "https://workflowengine.io/problems/invalid-workflow", "title": "Invalid workflow", "status": 422,
  "detail": "The workflow definition is invalid (1 error(s))", "errors": [ { "code": "UNREACHABLE_NODE", "message": "...", "nodeId": "n2", "edgeId": null } ] }
```

Every response carries `X-Correlation-Id`. Paginated responses have the shape
`{ "items": [...], "page": 0, "size": 20, "totalItems": 42, "totalPages": 3 }`.

## Workflows (workflow-service)

| Method | Path | Body / query | Result |
|---|---|---|---|
| GET | `/api/v1/me` | – | `{id, username, admin, roles[]}` |
| POST | `/api/v1/workflows` | `{name, description?, definition?}` | 201 `Workflow` |
| GET | `/api/v1/workflows` | `search, status=DRAFT\|ACTIVE\|ARCHIVED, sort=name\|createdAt\|updatedAt, direction=asc\|desc, page, size` | Page of `WorkflowSummary` (archived excluded unless `status=ARCHIVED`) |
| GET | `/api/v1/workflows/stats` | – | `{totalWorkflows, activeWorkflows}` |
| GET | `/api/v1/workflows/{id}` | – | `Workflow` (404 if not visible) |
| PUT | `/api/v1/workflows/{id}` | `{name, description?, definition, revision}` | `Workflow`; 409 if `revision` is stale; 422 on schema violations |
| DELETE | `/api/v1/workflows/{id}` | – | 204, archives (owner only) |
| POST | `/api/v1/workflows/{id}/duplicate` | – | 201 `Workflow` owned by caller |
| POST | `/api/v1/workflows/{id}/validate` | optional `{definition}` (validates the body instead of the stored draft) | `{valid, errors[]}` |
| POST | `/api/v1/workflows/{id}/publish` | – | 201 `WorkflowVersion`; 422 with `errors[]` if invalid |
| GET | `/api/v1/workflows/{id}/versions` | – | `WorkflowVersion[]` (newest first, without definition) |
| GET | `/api/v1/workflows/{id}/versions/{n}` | – | `WorkflowVersion` with definition |
| GET | `/api/v1/workflows/{id}/permissions` | – | `[{userId, username, role, grantedAt}]` |
| PUT | `/api/v1/workflows/{id}/permissions/{username}` | `{role: EDITOR\|VIEWER}` | updated list (owner only; user must have signed in once) |
| DELETE | `/api/v1/workflows/{id}/permissions/{username}` | – | updated list |

```ts
type Workflow = { id: string; name: string; description: string | null; status: 'DRAFT'|'ACTIVE'|'ARCHIVED';
  ownerId: string; role: 'OWNER'|'EDITOR'|'VIEWER'; revision: number; latestVersion: number | null;
  hasUnpublishedChanges: boolean; definition: WorkflowDefinition; createdAt: string; updatedAt: string };
type WorkflowSummary = Omit<Workflow, 'definition'|'revision'> & { triggerType: string | null; nodeCount: number };
type WorkflowVersion = { workflowId: string; version: number; name: string; publishedBy: string; publishedAt: string;
  definition: WorkflowDefinition | null };
```

`WorkflowDefinition` follows `packages/contracts/src/main/resources/schemas/workflow-definition.v1.json`
(see [architecture overview](../architecture/overview.md#4-workflow-definition-model-schema-version-1)).

## Executions (execution-engine)

| Method | Path | Body / query | Result |
|---|---|---|---|
| POST | `/api/v1/workflows/{id}/executions` | `{input?: object, version?: number}`; header `Idempotency-Key` optional | 201 `Execution` (200 when the idempotency key matched an earlier run); 409 `version-not-available` right after publishing (retry after a moment) |
| GET | `/api/v1/executions` | `workflowId, status, from, to (ISO-8601), page, size` | Page of `ExecutionSummary` |
| GET | `/api/v1/executions/stats` | – | `{executionsToday, completedToday, failedToday, running, pending, recent: ExecutionSummary[]}` |
| GET | `/api/v1/executions/{id}` | – | `Execution` |
| POST | `/api/v1/executions/{id}/cancel` | – | `Execution`; 409 if already finished |
| POST | `/api/v1/executions/{id}/retry` | – | 201 new `Execution` (same version and input, `retryOf` set) |
| GET | `/api/v1/executions/{id}/events` | `after` (sequence), `limit` | `HistoryEntry[]` |
| GET | `/api/v1/executions/{id}/events/stream` | `after` or header `Last-Event-ID` | `text/event-stream` |

```ts
type Execution = { id: string; workflowId: string; workflowName: string; workflowVersion: number;
  status: 'PENDING'|'RUNNING'|'COMPLETED'|'FAILED'|'CANCELLED'|'TIMED_OUT'; triggerType: 'MANUAL'|'WEBHOOK'|'SCHEDULE'|'RETRY';
  triggeredBy: string; input: unknown; output: unknown | null; errorCode: string | null; errorMessage: string | null;
  retryOf: string | null; createdAt: string; startedAt: string | null; finishedAt: string | null; deadlineAt: string | null;
  durationMs: number | null; lastEventSequence: number; definition: WorkflowDefinition; nodes: NodeExecution[] };
type NodeExecution = { nodeId: string; nodeType: string; name: string;
  status: 'PENDING'|'READY'|'QUEUED'|'RUNNING'|'COMPLETED'|'FAILED'|'SKIPPED'|'CANCELLED'|'TIMED_OUT';
  attemptCount: number; input: unknown; output: unknown; branch: string | null;
  error: { code: string; message: string; retryable: boolean } | null; startedAt: string | null; finishedAt: string | null;
  durationMs: number | null; nextAttemptAt: string | null; resumeAt: string | null; attempts: Attempt[] };
type Attempt = { attemptId: string; number: number; status: 'DISPATCHED'|'RUNNING'|'COMPLETED'|'FAILED'|'LEASE_EXPIRED'|'CANCELLED';
  workerId: string | null; dispatchedAt: string; startedAt: string | null; finishedAt: string | null;
  errorCode: string | null; errorMessage: string | null };
type HistoryEntry = { sequence: number; type: 'EXECUTION_CREATED'|'EXECUTION_STATUS_CHANGED'|'NODE_STATUS_CHANGED'|'NODE_RETRY_SCHEDULED'|'STALE_RESULT_IGNORED';
  nodeId: string | null; attemptId: string | null; status: string | null; details: Record<string, unknown>; occurredAt: string };
```

Inputs and outputs are masked: values of keys that look sensitive (`authorization`, `password`, `token`, `secret`,
`apiKey`, `cookie`, ...) are replaced by `••••••`.

### Live updates (SSE)

`GET /api/v1/executions/{id}/events/stream` emits `event: transition` with `id: <sequence>` and a `HistoryEntry` as
`data`, a comment every 15 s while idle, and `event: end` once the execution is terminal and all transitions were sent.
Reconnect with `Last-Event-ID: <last sequence>` (or `?after=`) to receive exactly the missed transitions. Browsers
cannot set `Authorization` on `EventSource`, so the web app reads the stream with `fetch`.

## Triggers (trigger-service)

| Method | Path | Body | Result |
|---|---|---|---|
| GET | `/api/v1/workflows/{id}/triggers` | – | `{webhook: {url, active, requireSignature, requiredFields, version} \| null, schedule: {cron, timezone, active, nextFireAt, lastFiredAt, version} \| null}`; `url` is null for viewers |
| POST | `/api/v1/workflows/{id}/triggers/webhook/secret` | – | `{secret}` – new signing secret, shown once (owner/editor) |
| GET | `/api/v1/workflows/{id}/trigger-deliveries` | `limit` | recent deliveries with execution id and status |
| POST | `/api/v1/webhooks/{token}` | JSON payload | 202 `{deliveryId, executionId, duplicate:false}`; 200 with `duplicate:true` for a repeated delivery; 401 bad/missing signature; 404 unknown token; 413 >256 KiB; 415 non-JSON; 422 missing required field |

Webhook signing (when `requireSignature` is true):

```
X-Webhook-Timestamp: <unix seconds>            (must be within ±5 minutes)
X-Webhook-Signature: sha256=<hex HMAC-SHA256(secret, timestamp + "." + rawBody)>
X-Webhook-Delivery-Id: <optional sender id, used for deduplication>
```

The workflow receives `{body, headers, query, receivedAt}` as trigger output (`{{ trigger.body.orderId }}`).
