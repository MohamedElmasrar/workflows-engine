-- Execution engine schema. Owned exclusively by execution-engine.

-- Projections of workflow-service data (event-carried state transfer).
create table workflow_projection (
    workflow_id    uuid primary key,
    owner_id       varchar(64)  not null,
    name           varchar(100),
    archived       boolean      not null default false,
    acl_revision   bigint       not null default 0,
    latest_version integer,
    updated_at     timestamptz  not null default now()
);

create table workflow_access (
    workflow_id uuid        not null references workflow_projection (workflow_id) on delete cascade,
    user_id     varchar(64) not null,
    role        varchar(16) not null check (role in ('OWNER', 'EDITOR', 'VIEWER')),
    primary key (workflow_id, user_id)
);
create index ix_workflow_access_user on workflow_access (user_id);

create table workflow_version_snapshot (
    workflow_id  uuid         not null,
    version      integer      not null,
    name         varchar(100) not null,
    definition   jsonb        not null,
    published_at timestamptz  not null,
    primary key (workflow_id, version)
);

create table workflow_execution (
    id               uuid primary key,
    workflow_id      uuid         not null,
    workflow_version integer      not null,
    workflow_name    varchar(100) not null,
    status           varchar(16)  not null check (status in ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','TIMED_OUT')),
    trigger_type     varchar(16)  not null,
    triggered_by     varchar(200) not null,
    input            jsonb        not null,
    output           jsonb,
    error_code       varchar(64),
    error_message    varchar(2000),
    idempotency_key  varchar(200),
    retry_of         uuid references workflow_execution (id),
    created_at       timestamptz  not null,
    started_at       timestamptz,
    finished_at      timestamptz,
    deadline_at      timestamptz,
    event_sequence   bigint       not null,
    version          bigint       not null,
    foreign key (workflow_id, workflow_version) references workflow_version_snapshot (workflow_id, version)
);
create unique index ux_execution_idempotency on workflow_execution (workflow_id, idempotency_key)
    where idempotency_key is not null;
create index ix_execution_workflow_created on workflow_execution (workflow_id, created_at desc);
create index ix_execution_created on workflow_execution (created_at desc);
create index ix_execution_status_created on workflow_execution (status, created_at desc);
create index ix_execution_running_deadline on workflow_execution (deadline_at) where status = 'RUNNING';
create index ix_execution_pending on workflow_execution (created_at) where status = 'PENDING';

create table node_execution (
    execution_id       uuid         not null references workflow_execution (id) on delete cascade,
    node_id            varchar(64)  not null,
    node_type          varchar(32)  not null,
    status             varchar(16)  not null check (status in ('PENDING','READY','QUEUED','RUNNING','COMPLETED','FAILED','SKIPPED','CANCELLED','TIMED_OUT')),
    attempt_count      integer      not null default 0,
    current_attempt_id uuid,
    available_at       timestamptz,
    lease_expires_at   timestamptz,
    resume_at          timestamptz,
    input              jsonb,
    output             jsonb,
    branch             varchar(16),
    error_code         varchar(64),
    error_message      varchar(2000),
    error_retryable    boolean,
    started_at         timestamptz,
    finished_at        timestamptz,
    primary key (execution_id, node_id)
);
-- Partial indexes for the sweeper's timer queries.
create index ix_node_ready_due on node_execution (available_at) where status = 'READY';
create index ix_node_lease_due on node_execution (lease_expires_at) where status in ('QUEUED', 'RUNNING');
create index ix_node_resume_due on node_execution (resume_at) where status = 'RUNNING' and resume_at is not null;

-- One row per dispatch to a worker; carries the task lease (TaskLease in the domain vocabulary).
create table node_attempt (
    attempt_id       uuid primary key,
    execution_id     uuid        not null,
    node_id          varchar(64) not null,
    attempt_number   integer     not null,
    status           varchar(16) not null,
    worker_id        varchar(200),
    dispatched_at    timestamptz not null,
    lease_expires_at timestamptz,
    started_at       timestamptz,
    finished_at      timestamptz,
    error_code       varchar(64),
    error_message    varchar(2000),
    foreign key (execution_id, node_id) references node_execution (execution_id, node_id) on delete cascade,
    unique (execution_id, node_id, attempt_number)
);

create table execution_event (
    id           bigserial    primary key,
    execution_id uuid         not null references workflow_execution (id) on delete cascade,
    sequence     bigint       not null,
    event_type   varchar(40)  not null,
    node_id      varchar(64),
    attempt_id   uuid,
    status       varchar(16),
    details      jsonb        not null default '{}'::jsonb,
    occurred_at  timestamptz  not null,
    unique (execution_id, sequence)
);

create table outbox_event (
    id           bigserial    primary key,
    event_id     uuid         not null unique,
    topic        varchar(200) not null,
    message_key  varchar(200) not null,
    event_type   varchar(100) not null,
    envelope     jsonb        not null,
    headers      jsonb        not null default '{}'::jsonb,
    created_at   timestamptz  not null default now(),
    published_at timestamptz,
    attempts     integer      not null default 0,
    last_error   text
);
create index ix_outbox_unpublished on outbox_event (id) where published_at is null;
create index ix_outbox_published_at on outbox_event (published_at) where published_at is not null;

create table processed_message (
    consumer     varchar(100) not null,
    event_id     uuid         not null,
    processed_at timestamptz  not null default now(),
    primary key (consumer, event_id)
);
