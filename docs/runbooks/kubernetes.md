# Runbook: Kubernetes deployment

Covers a local deployment on [kind](https://kind.sigs.k8s.io/) and notes for production.

| Path | Content |
|---|---|
| `infrastructure/helm/workflow-platform` | Helm chart: the 5 Spring Boot services and the web app, ConfigMap, HPA, PDB, Ingress |
| `infrastructure/helm/workflow-platform/values.yaml` | Production-like defaults (placeholder hosts, no secrets) |
| `infrastructure/helm/workflow-platform/values-kind.yaml` | Local kind overrides |
| `infrastructure/helm/workflow-platform/secrets.example.yaml` | Shape of the Secrets the chart references (documentation only) |
| `infrastructure/kubernetes/dev-dependencies` | **Development-only** PostgreSQL, Kafka, Redis, Keycloak, Mailpit, WireMock, OTel collector |

## 1. Local deployment on kind

### 1.1 Prerequisites

Docker, plus `kind`, `kubectl` and `helm` (v3.15+ or v4). Without root, install them into `~/.local/bin`:

```bash
mkdir -p ~/.local/bin && cd ~/.local/bin
curl -fsSLo kind https://kind.sigs.k8s.io/dl/v0.33.0/kind-linux-amd64 && chmod +x kind
curl -fsSLo kubectl "https://dl.k8s.io/release/$(curl -fsSL https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" && chmod +x kubectl
curl -fsSL https://get.helm.sh/helm-v4.3.2-linux-amd64.tar.gz | tar xz --strip-components=1 linux-amd64/helm
cd - && kind version && kubectl version --client && helm version
```

The platform plus dependencies needs about 6 GB of memory available to Docker.

The commands below use the ports compose also uses (8080, 8180, 3000, 8025). **Stop the compose stack first**
(`docker compose stop`): Keycloak must be reachable at exactly `http://localhost:8180` (see 1.6).

All commands run from the repository root.

### 1.2 Create the cluster

```bash
kind create cluster --name workflow-platform
kubectl cluster-info --context kind-workflow-platform
```

Kubernetes 1.30 or newer is required (the chart uses the native `preStop` `sleep` action). Current kind node images
satisfy this.

### 1.3 Build and load the images

```bash
docker compose build          # tags workflow-platform/<service>:dev for the 5 Spring Boot services
# web: once apps/web has its Dockerfile
docker build -t workflow-platform/web:dev apps/web

for image in api-gateway workflow-service execution-engine worker-service trigger-service web; do
  kind load docker-image --name workflow-platform "workflow-platform/$image:dev"
done
```

`values-kind.yaml` uses `pullPolicy: Never`, so a missing image shows as `ErrImageNeverPull`. If the web image is not
available yet, add `--set components.web.enabled=false` to the `helm` command in 1.6.

### 1.4 Development dependencies

```bash
kubectl kustomize --load-restrictor LoadRestrictionsNone infrastructure/kubernetes/dev-dependencies \
  | kubectl apply --server-side -f -
kubectl -n workflow-platform wait --for=condition=Ready pod \
  -l app.kubernetes.io/part-of=workflow-platform-dev-dependencies --timeout=5m
```

This creates the `workflow-platform` namespace and single-replica PostgreSQL 17, Kafka 4.1.2 (KRaft, automatic
topic creation disabled), Redis 7, Keycloak 26.4 (`start-dev`, realm imported), Mailpit, WireMock (`mock-api`) and an
OpenTelemetry collector (debug exporter). These are for development only: default credentials, no HA, no backups.
The PostgreSQL and Kafka PVCs live on the kind node and are deleted with the cluster.

`--load-restrictor LoadRestrictionsNone` is required because the ConfigMaps are generated from the files compose
uses: `infrastructure/docker/postgres/init.sql`, `infrastructure/keycloak/workflow-platform-realm.json` and
`infrastructure/docker/mock-api/mappings/*.json`. When you add a WireMock mapping file, also list it in
`kustomization.yaml`. The init SQL only runs on an empty data volume. To start over, delete the PVC:
`kubectl -n workflow-platform delete statefulset/postgres pvc/data-postgres-0`, then apply again.

### 1.5 Secrets

The chart only references Secrets. For kind, the database passwords must match `init.sql`:

```bash
kubectl -n workflow-platform create secret generic workflow-platform-db \
  --from-literal=workflow-password=workflow \
  --from-literal=execution-password=execution \
  --from-literal=trigger-password=trigger
kubectl -n workflow-platform create secret generic workflow-platform-trigger \
  --from-literal=encryption-key="$(openssl rand -base64 32)"
```

A pod stuck in `CreateContainerConfigError` means a Secret or key is missing (`kubectl describe pod ...`).

### 1.6 Install the platform

```bash
helm upgrade --install workflow-platform infrastructure/helm/workflow-platform \
  --namespace workflow-platform \
  -f infrastructure/helm/workflow-platform/values-kind.yaml \
  --wait --timeout 10m
kubectl -n workflow-platform get pods
```

The services create their Kafka topics and run their Flyway migrations on startup. The startup probes allow up to
3 minutes.

**Token issuer.** The services check that the `iss` claim of an access token equals `OIDC_ISSUER`
(`http://localhost:8180/realms/workflow-platform`). They fetch the signing keys in-cluster from `OIDC_JWKS_URI`
(`http://keycloak:8080/...`). Keycloak runs with `KC_HOSTNAME=http://localhost:8180`, so every token carries that
issuer no matter which URL it was requested through. With `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`, back-channel
requests can still use the in-cluster name. The browser and the smoke test therefore have to reach Keycloak at
`localhost:8180`, which is the reason for the port-forward below. With a different local port, tokens would still
be valid, but the browser login redirects would point to 8180.

### 1.7 Port-forwards

Run each command in its own terminal, or append `&` to run it in the background:

```bash
kubectl -n workflow-platform port-forward svc/keycloak 8180:8080
kubectl -n workflow-platform port-forward svc/workflow-platform-api-gateway 8080:8080
kubectl -n workflow-platform port-forward svc/workflow-platform-web 3000:3000
kubectl -n workflow-platform port-forward svc/mailpit 8025:8025
```

| URL | |
|---|---|
| http://localhost:3000 | Web app (demo users from the realm, e.g. `demo` / `demo`) |
| http://localhost:8080 | API gateway |
| http://localhost:8180 | Keycloak (admin / admin) |
| http://localhost:8025 | Mailpit |

The gateway's CORS origin (`http://localhost:3000`), the Keycloak client redirect URIs and `PUBLIC_BASE_URL`
(`http://localhost:8080`, used for webhook URLs) all assume these ports.

### 1.8 Smoke test

```bash
GATEWAY=http://localhost:8080 KEYCLOAK=http://localhost:8180 ./scripts/smoke-test.sh
```

The test runs webhook → transform → HTTP request (WireMock) → condition → notification (Mailpit). It needs the
gateway, Keycloak and Mailpit port-forwards.

### 1.9 Troubleshooting

| Symptom | Check |
|---|---|
| `ErrImageNeverPull` | Image not loaded: `docker exec workflow-platform-control-plane crictl images \| grep workflow-platform` |
| `CreateContainerConfigError` | Missing Secret or key (1.5) |
| Service restarts during startup | `kubectl logs --previous`; are the dependencies ready (`kubectl get pods`)? |
| 401 from the gateway | Token issuer differs from `OIDC_ISSUER`: was the token fetched via `localhost:8180`? |
| HTTP Request node blocked | `WORKER_HTTP_ALLOWED_HOSTS` must contain the private host (kind: `mock-api`) |
| Traces | `kubectl -n workflow-platform logs deploy/otel-collector` |

After code changes: `docker compose build <service>`, `kind load docker-image ...`, then
`kubectl -n workflow-platform rollout restart deploy/workflow-platform-<service>`. The `:dev` tag does not change,
so Helm sees nothing to update.

Clean up: `helm -n workflow-platform uninstall workflow-platform` and `kind delete cluster --name workflow-platform`.

## 2. Production notes

### 2.1 Dependencies

Do not use `dev-dependencies` in production.

* **PostgreSQL:** use a managed service (RDS, Cloud SQL, Azure Database). Keep one database and one role per
  service (`workflow`, `execution`, `trigger`), use TLS (`?sslmode=require` or `verify-full` in `DB_URL`), and
  enable backups with point-in-time recovery. Size `max_connections` for
  replicas × Hikari pool: execution-engine 20, workflow-service 10, trigger-service 10.
  For example, 3 engine replicas use 60 connections. Add PgBouncer if needed.
* **Kafka:** use a managed service (MSK, Confluent Cloud, Aiven) or Strimzi, with at least 3 brokers.
  Keep `PLATFORM_MESSAGING_REPLICATION_FACTOR=3`, `min.insync.replicas=2` and automatic topic creation off. The
  services create their topics with `PLATFORM_MESSAGING_PARTITIONS` partitions, but only when a topic does not
  exist yet. Set TLS and SASL through Spring properties as env vars, for example
  `SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL` in `config` and `SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG` through
  `secretEnv`.
* **Redis:** use a managed service with a password (`SPRING_DATA_REDIS_PASSWORD` through `secretEnv`) and TLS
  (`SPRING_DATA_REDIS_SSL_ENABLED=true`). Redis holds only rate-limit buckets and the worker attempt cache (24 h).
* **Keycloak:** run it in production mode (`start`) with its own database, a real hostname and TLS. Set
  `OIDC_ISSUER` to its public realm URL. `OIDC_JWKS_URI` may be an internal URL.
* **SMTP:** set `SMTP_HOST` / `SMTP_PORT` and pass the credentials through `secretEnv`.

### 2.2 Secrets

Never commit secret values. Create the Secrets named in `secrets.example.yaml` from a secret store, for example
with the External Secrets Operator (`ExternalSecret` → `workflow-platform-db`, `workflow-platform-trigger`), Vault,
Sealed Secrets or a cloud CSI driver. Generate `TRIGGER_ENCRYPTION_KEY` with `openssl rand -base64 32`. Changing
this key makes the stored webhook signing secrets unreadable, so rotate the webhook secrets along with it.
The pods do not mount a service account token (`automountServiceAccountToken: false`).

### 2.3 Ingress and TLS

Set `ingress.enabled=true`, `ingress.host` and `ingress.className`. Create the `ingress.tls.secretName` certificate
with cert-manager (for example with an annotation `cert-manager.io/cluster-issuer: <issuer>`). The routes are
`/api` → api-gateway and `/` → web. Actuator endpoints are not routed. The default nginx annotations turn off
response buffering and raise the read timeout for the execution event stream (SSE). Adjust them for other
controllers. If the web app serves its own `/api/*` routes, change `ingress.paths`. Set `CORS_ALLOWED_ORIGINS` and
`PUBLIC_BASE_URL` to the public origin.

Images on GHCR are private by default. Add a pull secret (`imagePullSecrets`) or make the packages public.

### 2.4 Scaling

* **Kafka partitions cap consumers.** Each topic has 6 partitions, and a consumer group gets at most one active
  consumer per partition. For the execution-engine (`SPRING_KAFKA_LISTENER_CONCURRENCY`) and the worker-service
  (`WORKER_CONCURRENCY`), keep `maxReplicas × concurrency ≤ partitions`. The defaults are 3 × 2 = 6. To scale
  further, add partitions first (`kafka-topics.sh --alter --partitions N`). Partitions can never be reduced, and
  adding them changes the key-to-partition mapping for new messages.
* The HPAs (engine and worker, CPU at 70 %) scale down slowly (300 s window, 1 pod per 2 min), because every scale
  event rebalances the consumer group. For queue-driven scaling, use KEDA on consumer lag
  (`kafka_consumer_fetch_manager_records_lag`).
* api-gateway, workflow-service and trigger-service are stateless HTTP services. Scale their replicas manually.
  Trigger schedules (`FOR UPDATE SKIP LOCKED`) and the outbox publishers (PostgreSQL advisory lock) are safe with
  several replicas.
* PDBs (`minAvailable: 1`) protect the gateway, engine and worker during node drains. Run at least 2 replicas,
  otherwise a drain blocks.
* Memory: the heap is 75 % of the container limit (`-XX:MaxRAMPercentage=75`). Raise the limit rather than the
  percentage.

### 2.5 Observability

The pods carry `prometheus.io/scrape|path|port` annotations. With the Prometheus Operator, add a PodMonitor that
selects `app.kubernetes.io/part-of=workflow-platform` and scrapes path `/actuator/prometheus` on port `http`. The
Grafana dashboards in `infrastructure/observability/grafana/dashboards` filter by the `application` label, which
the services add themselves, so they work unchanged. The chart turns on histogram buckets for HTTP server
requests and Kafka listeners, which the p95 panels need. Port the alert rules from
`infrastructure/observability/prometheus/alerts.yml`. The `ServiceDown` rule uses the compose job name, so adjust
it. Point `OTLP_TRACES_ENDPOINT` at your collector.

### 2.6 Deploy, upgrade and rollback

`release.yml` publishes `ghcr.io/<owner>/<repo>/<component>` tagged `sha-<commit>`, the branch name and, for `v*`
tags, the semver tags. It then deploys `main` to the `staging` environment when the environment has a `KUBECONFIG`
secret. Optional values go in `HELM_VALUES_STAGING`. To deploy by hand:

```bash
helm upgrade --install workflow-platform infrastructure/helm/workflow-platform -n workflow-platform \
  -f my-values.yaml --set image.registry=ghcr.io/<owner>/<repo> --set image.tag=1.4.0 \
  --rollback-on-failure --wait --timeout 10m     # Helm 3: --atomic instead of --rollback-on-failure
helm -n workflow-platform history workflow-platform
helm -n workflow-platform rollback workflow-platform <revision> --wait
```

* Rolling updates use `maxUnavailable: 0`. Shutdown works like this: the `preStop` hook sleeps 10 s so endpoints
  are removed, then Spring shuts down gracefully (up to 30 s, 60 s for the worker). The termination grace period
  covers both steps.
* **Database migrations are not rolled back.** Flyway migrates on startup, and during a rolling update old and new
  pods run against the same schema. Migrations must therefore be backward compatible (expand, then contract in a
  later release). Before a rollback across a migration, check that the old version still works with the new
  schema.
* Event contracts are versioned (`*.v1` topics). Consumers must accept the events of both the old and the new
  version during a rollout.
* Configuration changes roll the pods automatically (`checksum/config` annotation). Changes to a Secret do not:
  run `kubectl rollout restart`.
