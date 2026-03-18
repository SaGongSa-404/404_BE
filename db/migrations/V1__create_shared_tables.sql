create extension if not exists "pgcrypto";

create table if not exists idempotency_keys (
    id uuid primary key default gen_random_uuid(),
    key varchar(120) not null,
    scope varchar(50) not null,
    request_hash varchar(255) not null,
    response_status_code integer,
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_idempotency_keys_key
    on idempotency_keys (key);

create table if not exists outbox_events (
    id uuid primary key default gen_random_uuid(),
    aggregate_type varchar(50) not null,
    aggregate_id uuid not null,
    event_type varchar(100) not null,
    payload_json jsonb not null,
    status varchar(20) not null,
    available_at timestamptz not null default now(),
    dispatched_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_outbox_events_status_available_at
    on outbox_events (status, available_at);

