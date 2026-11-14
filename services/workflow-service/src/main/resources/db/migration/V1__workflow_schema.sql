-- Workflow service schema. Owned exclusively by workflow-service.

create table workflow (
    id                 uuid primary key,
    owner_id           varchar(64)   not null,
    name               varchar(100)  not null,
    description        varchar(1000),
    status             varchar(16)   not null check (status in ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    draft_definition   jsonb         not null,
    revision           bigint        not null,
    latest_version     integer,
    published_revision bigint,
    acl_revision       bigint        not null,
    created_at         timestamptz   not null,
    updated_at         timestamptz   not null,
    archived_at        timestamptz,
    lock_version       bigint        not null default 0
);
create index ix_workflow_owner on workflow (owner_id);
create index ix_workflow_status_updated on workflow (status, updated_at desc);

create table workflow_version (
    workflow_id    uuid         not null references workflow (id),
    version        integer      not null check (version > 0),
    name           varchar(100) not null,
    definition     jsonb        not null,
    schema_version integer      not null,
    published_by   varchar(64)  not null,
    published_at   timestamptz  not null,
    primary key (workflow_id, version)
);

-- Published versions are immutable: reject any UPDATE or DELETE at the database level.
create function forbid_workflow_version_change() returns trigger as $$
begin
    raise exception 'workflow_version rows are immutable';
end;
$$ language plpgsql;

create trigger workflow_version_immutable
    before update or delete on workflow_version
    for each row execute function forbid_workflow_version_change();

create table workflow_permission (
    workflow_id uuid        not null references workflow (id) on delete cascade,
    user_id     varchar(64) not null,
    role        varchar(16) not null check (role in ('EDITOR', 'VIEWER')),
    granted_by  varchar(64) not null,
    granted_at  timestamptz not null,
    primary key (workflow_id, user_id)
);
create index ix_workflow_permission_user on workflow_permission (user_id);

create table app_user (
    id           varchar(64)  primary key,
    username     varchar(255) not null unique,
    email        varchar(320),
    last_seen_at timestamptz  not null
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
