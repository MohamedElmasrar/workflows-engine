# Workflow Engine

[![Java](https://img.shields.io/badge/Java-21-ED8B00.svg?style=flat&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F.svg?style=flat&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud%20Gateway-2025.0-6DB33F.svg?style=flat&logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud-gateway)
[![Spring Security](https://img.shields.io/badge/Spring%20Security-OAuth2%20resource%20server-6DB33F.svg?style=flat&logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![Lombok](https://img.shields.io/badge/Lombok-1.18-BC4521.svg?style=flat)](https://projectlombok.org)
[![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-2025.0-6DB33F.svg?style=flat&logo=spring&logoColor=white)](https://spring.io/projects/spring-data-jpa)
[![Hibernate](https://img.shields.io/badge/Hibernate-6.6-59666C.svg?style=flat&logo=hibernate&logoColor=white)](https://hibernate.org/orm/)
[![Flyway](https://img.shields.io/badge/Flyway-migrations-CC0200.svg?style=flat&logo=flyway&logoColor=white)](https://flywaydb.org)
[![Resilience4j](https://img.shields.io/badge/Resilience4j-circuit%20breaker-0B7BBE.svg?style=flat)](https://resilience4j.readme.io)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-336791.svg?style=flat&logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![Apache Kafka](https://img.shields.io/badge/Apache%20Kafka-4.1%20KRaft-231F20.svg?style=flat&logo=apachekafka&logoColor=white)](https://kafka.apache.org)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D.svg?style=flat&logo=redis&logoColor=white)](https://redis.io)
[![Keycloak](https://img.shields.io/badge/Keycloak-26-4D4D4D.svg?style=flat&logo=keycloak&logoColor=white)](https://www.keycloak.org)
[![Node.js](https://img.shields.io/badge/Node.js-24+-5FA04E.svg?style=flat&logo=nodedotjs&logoColor=white)](https://nodejs.org)
[![Next.js](https://img.shields.io/badge/Next.js-16-000000.svg?style=flat&logo=nextdotjs&logoColor=white)](https://nextjs.org)
[![React](https://img.shields.io/badge/React-19-61DAFB.svg?style=flat&logo=react&logoColor=black)](https://react.dev)
[![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6.svg?style=flat&logo=typescript&logoColor=white)](https://www.typescriptlang.org)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-4-06B6D4.svg?style=flat&logo=tailwindcss&logoColor=white)](https://tailwindcss.com)
[![shadcn/ui](https://img.shields.io/badge/shadcn%2Fui-components-000000.svg?style=flat&logo=shadcnui&logoColor=white)](https://ui.shadcn.com)
[![React Flow](https://img.shields.io/badge/React%20Flow-12-FF0072.svg?style=flat)](https://reactflow.dev)
[![TanStack Query](https://img.shields.io/badge/TanStack%20Query-5-FF4154.svg?style=flat&logo=reactquery&logoColor=white)](https://tanstack.com/query)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg?style=flat&logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-Helm-326CE5.svg?style=flat&logo=kubernetes&logoColor=white)](https://helm.sh)
[![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-CI%2FCD-2088FF.svg?style=flat&logo=githubactions&logoColor=white)](https://docs.github.com/actions)
[![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-tracing-425CC7.svg?style=flat&logo=opentelemetry&logoColor=white)](https://opentelemetry.io)
[![Prometheus](https://img.shields.io/badge/Prometheus-metrics-E6522C.svg?style=flat&logo=prometheus&logoColor=white)](https://prometheus.io)
[![Grafana](https://img.shields.io/badge/Grafana-dashboards-F46800.svg?style=flat&logo=grafana&logoColor=white)](https://grafana.com)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-integration%20tests-291A3F.svg?style=flat)](https://testcontainers.com)
[![Vitest](https://img.shields.io/badge/Vitest-5-6E9F18.svg?style=flat&logo=vitest&logoColor=white)](https://vitest.dev)
[![Playwright](https://img.shields.io/badge/Playwright-1.65-2EAD33.svg?style=flat)](https://playwright.dev)

A personal project: a small workflow automation platform in the spirit of n8n, with Temporal-style durable
execution underneath. You draw a workflow in the browser, publish it, and trigger it by hand, with a signed webhook,
or on a cron schedule. The steps run on separate workers through Kafka, and you can watch every step live.

I built it to get hands-on with the hard parts of distributed systems, not just talk about them: durable state
machines, the transactional outbox, idempotent consumers, task leases, crash recovery, and tracing a request across
services and Kafka. Everything runs locally with Docker Compose.

![Workflow editor](docs/screenshots/editor.png)

## What it can do

* **Visual editor.** Drag nodes from a palette, connect them, configure each node in a side panel. Conditions
  have `true` and `false` outputs. Auto-layout, validation with the broken nodes highlighted, version history,
  sharing with other users.
* **Nine node types.** Manual, Webhook and Schedule triggers; HTTP Request, Transform, Log, Notification (e-mail or
  log), Condition, and Delay (up to 7 days, survives restarts).
* **Passing data between steps.** `{{ trigger.body.orderId }}` or `{{ nodes.score.output.body.score }}` in any
  text field. These are plain property paths; no user code is ever executed.
* **Live monitoring.** The execution page updates over Server-Sent Events: graph colours, a timeline, and each
  node's input, output, attempts and errors. Reconnecting replays what you missed. Runs can be cancelled or retried.
* **Failure handling.** Retries with exponential back-off, timeouts, re-dispatch when a worker disappears,
  dead-letter topics, and protection against duplicate messages.
* **Observability.** JSON logs with trace and correlation ids, Prometheus metrics, Grafana dashboards, and Jaeger
  traces that follow one webhook through all services.

## How it's put together

```mermaid
flowchart LR
  browser[Web app<br/>Next.js + React Flow] -->|REST + SSE| gw[API Gateway]
  ext[Webhook sender] --> gw
  gw --> wf[Workflow Service] & ee[Execution Engine] & tr[Trigger Service]
  wf -- workflow.events.v1 --> kafka{{Kafka}}
  tr -- execution.requests.v1 --> kafka
  kafka --> ee
  ee -- execution.node-tasks.v1 --> kafka --> wk[Worker Service]
  wk -- execution.node-results.v1 --> kafka
  wf --> db1[(workflow db)]
  ee --> db2[(execution db)]
  tr --> db3[(trigger db)]
  wk --> ext2[External APIs / SMTP]
  browser -. OIDC PKCE .-> kc[Keycloak]
```

| Part | What it does |
|---|---|
| `services/api-gateway` | Routing, token check, rate limiting, CORS, correlation ids |
| `services/workflow-service` | Drafts, validation, immutable published versions, sharing |
| `services/execution-engine` | The durable state machines, scheduling, retries, leases, timeouts, live updates |
| `services/worker-service` | Runs the nodes (HTTP with SSRF protection, condition, transform, log, notification) |
| `services/trigger-service` | Webhook endpoints (HMAC signatures, replay protection) and cron schedules |
| `apps/web` | The web app |
| `packages/contracts` | Shared event formats and JSON schemas |
| `packages/platform-support` | Outbox, duplicate detection, Kafka dead-lettering, error responses |

If you want the details: [architecture](docs/architecture/overview.md) (state machines, join rules, failure
scenarios), [why I chose what I chose](docs/design-decisions.md), [security](docs/architecture/security.md),
[REST API](docs/api/README.md), [events](docs/events/catalog.md).

## Screenshots

| Dashboard | Executions | Execution detail |
|---|---|---|
| ![](docs/screenshots/dashboard.png) | ![](docs/screenshots/executions.png) | ![](docs/screenshots/execution-detail.png) |

## Running it

You need Docker with Compose v2 and roughly 6 GB of free memory. The helper scripts also use `curl`, `jq`, `openssl`
and Python 3. To build outside Docker: JDK 21+ and Node 24+. The code targets Java 21; the
Docker images build with JDK 24 and run on Java 25 and Node 26.

```bash
docker compose up -d --build                    # first build takes a few minutes
docker compose --profile observability up -d    # optional: Jaeger, Prometheus, Grafana
scripts/smoke-test.sh                           # creates the demo workflow and runs it twice
```

| URL | What | Login |
|---|---|---|
| http://localhost:3000 | Web app | `demo`/`demo` (also `alice`, `bob`, and `admin` as admin; password = username) |
| http://localhost:8080 | API gateway | Bearer token |
| http://localhost:8180 | Keycloak | `admin`/`admin` |
| http://localhost:8025 | Mailpit, catches the notification e-mails | – |
| http://localhost:16686, :9090, :3001 | Jaeger, Prometheus, Grafana | Grafana `admin`/`admin` |
| http://localhost:8081-8084/swagger-ui.html | OpenAPI page of each service | – |

All passwords and keys in `compose.yaml` are local development values. The ones you might want to change:

| Variable | Default | Meaning |
|---|---|---|
| `TRIGGER_ENCRYPTION_KEY` | dev key | AES key for stored webhook secrets (`openssl rand -base64 32`) |
| `ENGINE_LEASE_GRACE` | `60s` | How long after a node's timeout the engine gives up on a worker and re-dispatches |
| `WORKER_HTTP_ALLOWED_HOSTS` | `mock-api` | Hosts allowed to resolve to private addresses |
| `EXECUTION_START_RATE` / `_BURST` | `5` / `20` | Manual starts per user per second |

## The demo workflow

`scripts/order-webhook-demo.json` is what `smoke-test.sh` uses. A signed webhook receives an order, a Transform node
shapes it, an HTTP Request asks a (mock) fraud API for a score, and a Condition sends high-risk orders to the fraud
team and confirms the rest to the customer by e-mail.

`scripts/demo-failure-recovery.sh` then breaks things on purpose: it kills a worker in the middle of an HTTP call,
kills the engine during a delay, and replays a Kafka message. All three runs still end correctly.
[docs/demo-guide.md](docs/demo-guide.md) walks through both, with the results I got.

## Tests

```bash
./mvnw verify -DskipITs          # unit, architecture and contract tests (no Docker needed)
./mvnw verify                    # plus integration tests against real PostgreSQL, Kafka and Redis (Testcontainers)
cd apps/web && npm ci && npm run lint && npm run typecheck && npm test
cd apps/web && npx playwright install chromium && npm run test:e2e   # needs the Compose stack running
scripts/load-test.py 200 20      # throughput and latency, results in docs/performance.md
```

Last full run: 253 backend tests, 78 frontend unit tests and 2 Playwright journeys, all passing, both on Docker
Compose and on a local kind cluster.

## Kubernetes and CI

I also wrote a Helm chart (`infrastructure/helm/workflow-platform`) and tested it on a local kind cluster; the steps
are in [docs/runbooks/kubernetes.md](docs/runbooks/kubernetes.md). GitHub Actions builds and tests everything on each
push (`.github/workflows/ci.yml`) and publishes images to GHCR from `main` (`release.yml`).

## Limitations

* Messages are delivered at least once. State changes are safe, but if a worker crashes mid-call an HTTP request can
  be sent twice. HTTP nodes send an `Idempotency-Key` header for APIs that support it.
* Only acyclic graphs, and one trigger per workflow.
* Right after publishing, starting a run can return `409 version-not-available` for a moment; the UI retries.
* No credential store: an API key typed into an HTTP header is saved as part of the workflow.
* Cancelling doesn't interrupt a step that is already running on a worker; its result is just ignored.
* Single Kafka broker and single PostgreSQL. It runs on my laptop, not in production.

## When something goes wrong

* **A container isn't healthy after `up`:** check `docker compose logs <service>`. Usually Keycloak or Kafka were
  still starting; run `docker compose up -d` again.
* **Login loops or 401 everywhere:** open the app on `localhost:3000`, not `127.0.0.1`. Tokens must come from
  `localhost:8180`.
* **HTTP node fails with `DESTINATION_BLOCKED`:** the URL points to a private address. Only hosts in
  `WORKER_HTTP_ALLOWED_HOSTS` may do that.
* **Port already in use:** the stack uses 3000, 5432, 6379, 8025, 8080-8084, 8089, 8180 and 9094.

More in [docs/runbooks/operations.md](docs/runbooks/operations.md).
