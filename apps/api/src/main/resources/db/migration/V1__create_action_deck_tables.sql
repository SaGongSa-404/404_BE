create table if not exists users (
    id uuid primary key,
    nickname varchar(80) not null,
    timezone varchar(40) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists saved_contents (
    id uuid primary key,
    user_id uuid not null references users (id),
    url varchar(1000) not null,
    title varchar(255) not null,
    note varchar(1000),
    source_domain varchar(120) not null,
    category varchar(40) not null,
    tags_csv varchar(500),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_saved_contents_user_created_at
    on saved_contents (user_id, created_at desc);

create table if not exists practice_cards (
    id uuid primary key,
    saved_content_id uuid not null references saved_contents (id) on delete cascade,
    user_id uuid not null references users (id),
    category varchar(40) not null,
    status varchar(20) not null,
    action_title varchar(140) not null,
    action_detail varchar(500) not null,
    encouragement_message varchar(255) not null,
    rationale varchar(255) not null,
    estimated_minutes integer not null,
    energy_level varchar(20) not null,
    scheduled_for date not null,
    completed_at timestamp with time zone,
    completion_note varchar(500),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists idx_practice_cards_user_status
    on practice_cards (user_id, status, scheduled_for, created_at desc);

create table if not exists practice_card_events (
    id uuid primary key,
    practice_card_id uuid not null references practice_cards (id) on delete cascade,
    user_id uuid not null references users (id),
    event_type varchar(20) not null,
    note varchar(500),
    created_at timestamp with time zone not null
);

create index if not exists idx_practice_card_events_user_created_at
    on practice_card_events (user_id, created_at desc);
