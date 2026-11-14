# Development dependencies for kind (DEVELOPMENT ONLY)

Single-replica PostgreSQL 17, Kafka 4.1.2 (KRaft), Redis 7, Keycloak 26.4 (start-dev + realm import), Mailpit,
WireMock and an OpenTelemetry collector, mirroring `compose.yaml`. Default credentials, no HA, no backups, no
persistence guarantees. Production uses managed services instead (see `docs/runbooks/kubernetes.md`).

```bash
kubectl kustomize --load-restrictor LoadRestrictionsNone infrastructure/kubernetes/dev-dependencies \
  | kubectl apply --server-side -f -
```

`--load-restrictor LoadRestrictionsNone` is needed because the ConfigMaps are generated from the files compose
already uses (`infrastructure/docker/postgres/init.sql`, `infrastructure/keycloak/workflow-platform-realm.json`,
`infrastructure/docker/mock-api/mappings/*.json`) instead of copies.
