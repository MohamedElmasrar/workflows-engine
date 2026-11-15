# Runbook: Kubernetes deployment

How I deploy the project on a local kind cluster with the Helm chart. I ran through these steps end to end;
the smoke test and the Playwright tests pass on the cluster.

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

## 2. If I ever deploy it for real

The chart is written so it could run on a real cluster, but I've only run it on kind. Things I would change first:

* use managed PostgreSQL, Kafka (3 brokers, replication factor 3) and Redis instead of `dev-dependencies`;
* run Keycloak in production mode with TLS and remove the `workflow-cli` password-grant client and demo users;
* create the Secrets from a secret store and generate a real `TRIGGER_ENCRYPTION_KEY`;
* enable the Ingress with a TLS certificate and set `CORS_ALLOWED_ORIGINS` and `PUBLIC_BASE_URL`;
* keep Flyway migrations backward compatible, because old and new pods share the database during a rolling update.

`release.yml` already publishes the images to GHCR and can run `helm upgrade` against a `staging` environment if a
`KUBECONFIG` secret is set.
