-- Trigger service schema. Owned exclusively by trigger-service.

create table trigger_registration (
    workflow_id      uuid primary key,
    workflow_version integer     not null,
    trigger_kind     varchar(16) not null check (trigger_kind in ('MANUAL', 'WEBHOOK', 'SCHEDULE')),
    updated_at       timestamptz not null default now()
);

create table webhook_registration (
    id                uuid primary key,
    workflow_id       uuid         not null unique references trigger_registration (workflow_id),
    workflow_version  integer      not null,
    token_hash        char(64)     not null unique,
    token_encrypted   text         not null,
    secret_encrypted  text         not null,
    require_signature boolean      not null,
    required_fields   jsonb        not null default '[]'::jsonb,
    active            boolean      not null,
    updated_at        timestamptz  not null default now()
);

create table scheduled_trigger (
    id               uuid primary key,
    workflow_id      uuid         not null unique references trigger_registration (workflow_id),
    workflow_version integer      not null,
    cron             varchar(120) not null,
    timezone         varchar(64)  not null,
    payload          jsonb        not null default '{}'::jsonb,
    next_fire_at     timestamptz,
    last_fired_at    timestamptz,
    active           boolean      not null,
    updated_at       timestamptz  not null default now()
);
create index ix_schedule_due on scheduled_trigger (next_fire_at) where active;

create table trigger_delivery (
    id               uuid primary key,
    workflow_id      uuid         not null,
    trigger_type     varchar(16)  not null,
    dedupe_key       varchar(300) not null unique,
    execution_id     uuid         not null unique,
    execution_status varchar(16)  not null,
    payload_bytes    integer      not null,
    received_at      timestamptz  not null
);
create index ix_delivery_workflow on trigger_delivery (workflow_id, received_at desc);

create table workflow_access (
    workflow_id uuid        not null,
    user_id     varchar(64) not null,
    role        varchar(16) not null,
    primary key (workflow_id, user_id)
);
create index ix_access_user on workflow_access (user_id);

create table workflow_acl_revision (
    workflow_id  uuid primary key,
    owner_id     varchar(64) not null,
    acl_revision bigint      not null
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
