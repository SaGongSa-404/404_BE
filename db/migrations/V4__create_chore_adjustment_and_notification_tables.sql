create table if not exists chore_rules (
    id uuid primary key default gen_random_uuid(),
    house_id uuid not null references houses (id) on delete cascade,
    space_id uuid not null references spaces (id),
    title varchar(80) not null,
    description text,
    default_assignee_membership_id uuid references house_memberships (id),
    estimated_minutes integer,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists recurrence_rules (
    id uuid primary key default gen_random_uuid(),
    chore_rule_id uuid not null references chore_rules (id) on delete cascade,
    frequency varchar(20) not null,
    interval_value integer not null default 1,
    days_of_week varchar(20)[],
    start_date date not null,
    end_date date,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_recurrence_rules_chore_rule_id
    on recurrence_rules (chore_rule_id);

create table if not exists chore_instances (
    id uuid primary key default gen_random_uuid(),
    house_id uuid not null references houses (id) on delete cascade,
    chore_rule_id uuid not null references chore_rules (id) on delete cascade,
    scheduled_date date not null,
    status varchar(20) not null,
    current_assignee_membership_id uuid references house_memberships (id),
    origin_type varchar(20) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_chore_instances_house_date_status
    on chore_instances (house_id, scheduled_date, status);

create table if not exists chore_assignments (
    id uuid primary key default gen_random_uuid(),
    chore_instance_id uuid not null references chore_instances (id) on delete cascade,
    assignee_membership_id uuid not null references house_memberships (id),
    assigned_by_user_id uuid not null references users (id),
    assigned_at timestamptz not null default now(),
    assignment_type varchar(20) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_chore_assignments_assignee_membership_id
    on chore_assignments (assignee_membership_id);

create table if not exists chore_completions (
    id uuid primary key default gen_random_uuid(),
    chore_instance_id uuid not null references chore_instances (id) on delete cascade,
    completed_by_user_id uuid not null references users (id),
    completed_at timestamptz not null,
    memo text,
    proof_image_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_chore_completions_chore_instance_id
    on chore_completions (chore_instance_id);

create table if not exists adjustment_requests (
    id uuid primary key default gen_random_uuid(),
    house_id uuid not null references houses (id) on delete cascade,
    chore_instance_id uuid not null references chore_instances (id) on delete cascade,
    requester_membership_id uuid not null references house_memberships (id),
    request_type varchar(20) not null,
    reason text,
    requested_date date,
    status varchar(20) not null,
    expires_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_adjustment_requests_instance_status
    on adjustment_requests (chore_instance_id, status);

create index if not exists idx_adjustment_requests_house_status
    on adjustment_requests (house_id, status);

create table if not exists adjustment_responses (
    id uuid primary key default gen_random_uuid(),
    adjustment_request_id uuid not null references adjustment_requests (id) on delete cascade,
    responder_membership_id uuid not null references house_memberships (id),
    decision varchar(20) not null,
    responded_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_adjustment_responses_request_id
    on adjustment_responses (adjustment_request_id);

create table if not exists adjustment_reward_counters (
    id uuid primary key default gen_random_uuid(),
    membership_id uuid not null references house_memberships (id) on delete cascade,
    accepted_substitute_count integer not null default 0,
    last_settled_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_adjustment_reward_counters_membership_id
    on adjustment_reward_counters (membership_id);

create table if not exists notifications (
    id uuid primary key default gen_random_uuid(),
    house_id uuid not null references houses (id) on delete cascade,
    actor_user_id uuid references users (id),
    type varchar(30) not null,
    title varchar(120) not null,
    body text not null,
    payload_json jsonb not null default '{}'::jsonb,
    occurred_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_notifications_house_occurred_at
    on notifications (house_id, occurred_at desc);

create table if not exists notification_receipts (
    id uuid primary key default gen_random_uuid(),
    notification_id uuid not null references notifications (id) on delete cascade,
    user_id uuid not null references users (id),
    read_at timestamptz,
    hidden_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_notification_receipts_notification_user
    on notification_receipts (notification_id, user_id);

create index if not exists idx_notification_receipts_user_read_at
    on notification_receipts (user_id, read_at);

create table if not exists push_devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users (id) on delete cascade,
    platform varchar(20) not null,
    push_token varchar(255) not null,
    status varchar(20) not null default 'ACTIVE',
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_push_devices_push_token
    on push_devices (push_token);

create table if not exists push_delivery_logs (
    id uuid primary key default gen_random_uuid(),
    notification_id uuid not null references notifications (id) on delete cascade,
    push_device_id uuid not null references push_devices (id) on delete cascade,
    provider varchar(20) not null,
    status varchar(20) not null,
    provider_message_id varchar(255),
    failure_reason text,
    sent_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_push_delivery_logs_notification_id
    on push_delivery_logs (notification_id);

