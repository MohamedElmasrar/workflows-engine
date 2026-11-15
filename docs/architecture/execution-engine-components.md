# Execution Engine Components (C4 level 3)

```mermaid
flowchart TB
  subgraph presentation
    REST[ExecutionController<br/>start / list / get / cancel / retry / events]
    SSE[ExecutionStreamController<br/>SSE from persisted history]
  end
  subgraph infrastructure
    KL[EngineKafkaListeners<br/>workflow events · requests · node results]
    SW[ExecutionSweeper<br/>timers, leases, pending starts]
    REPO[JpaExecutionRepository<br/>Spring Data JPA, aggregate ↔ tables, optimistic lock]
    CAT[WorkflowCatalogAdapter<br/>version + ACL projections]
    RM[ExecutionReadModelAdapter<br/>Specifications, history, stats]
    OUT[OutboxEffectPublisher]
    MET[MicrometerExecutionMetrics]
    TX[RetryingTransactionRunner]
  end
  subgraph application
    ES[ExecutionService]
    PS[WorkflowProjectionService]
    QS[ExecutionQueryService]
  end
  subgraph domain
    AGG[WorkflowExecution aggregate<br/>state machines, join rules, retries, fencing]
    G[ExecutionGraph]
    TPL[TemplateResolver]
    MASK[SensitiveDataMasker]
    POL[ExecutionAccessPolicy]
  end
  REST --> ES & QS
  SSE --> QS
  KL --> ES & PS
  SW --> ES
  ES --> TX --> REPO
  ES --> AGG
  AGG --> G & TPL & MASK
  ES --> CAT & OUT & MET
  ES --> POL
  QS --> RM & REPO
  PS --> CAT
  OUT --> OB[(outbox_event)]
  REPO --> DB[(execution DB)]
```

One operation = one transaction: load aggregate (`OPTIMISTIC_FORCE_INCREMENT`), call one domain method with `now`,
persist changed rows, append history entries, write outbox effects. Conflicts are retried up to five times.

Persistence uses Spring Data JPA repositories with Lombok entities. SQL remains only where it is the correct tool:
the sweeper's due-timer UNION over partial indexes, the JDBC batch insert of history rows, the dashboard
`count(*) FILTER` aggregate, and the projection upserts (`INSERT ... ON CONFLICT ... WHERE revision < excluded`)
that must be atomic under concurrent consumers.
