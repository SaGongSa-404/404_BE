create table if not exists weekly_snapshots (
    id uuid primary key default gen_random_uuid(),
    house_id uuid not null references houses (id) on delete cascade,
    week_start_date date not null,
    week_end_date date not null,
    status varchar(20) not null,
    generated_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_weekly_snapshots_house_week_start
    on weekly_snapshots (house_id, week_start_date);

create table if not exists weekly_house_stats (
    id uuid primary key default gen_random_uuid(),
    snapshot_id uuid not null references weekly_snapshots (id) on delete cascade,
    total_chores integer not null default 0,
    completed_chores integer not null default 0,
    completion_rate numeric(5,2) not null default 0,
    accepted_adjustments integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_weekly_house_stats_snapshot_id
    on weekly_house_stats (snapshot_id);

create table if not exists weekly_member_stats (
    id uuid primary key default gen_random_uuid(),
    snapshot_id uuid not null references weekly_snapshots (id) on delete cascade,
    membership_id uuid not null references house_memberships (id),
    assigned_chores integer not null default 0,
    completed_chores integer not null default 0,
    completion_rate numeric(5,2) not null default 0,
    substitute_acceptances integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_weekly_member_stats_snapshot_id
    on weekly_member_stats (snapshot_id);

create table if not exists weekly_satisfactions (
    id uuid primary key default gen_random_uuid(),
    snapshot_id uuid not null references weekly_snapshots (id) on delete cascade,
    user_id uuid not null references users (id),
    score integer not null,
    comment text,
    submitted_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_weekly_satisfactions_snapshot_user
    on weekly_satisfactions (snapshot_id, user_id);

