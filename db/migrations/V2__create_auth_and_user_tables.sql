create table if not exists users (
    id uuid primary key default gen_random_uuid(),
    status varchar(20) not null,
    primary_email varchar(255),
    last_login_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists user_profiles (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users (id) on delete cascade,
    nickname varchar(40) not null,
    profile_image_url text,
    timezone varchar(40) not null default 'Asia/Seoul',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_user_profiles_user_id
    on user_profiles (user_id);

create table if not exists withdrawal_records (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users (id),
    reason varchar(100),
    detail text,
    withdrawn_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_withdrawal_records_user_id
    on withdrawal_records (user_id);

create table if not exists social_accounts (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users (id) on delete cascade,
    provider varchar(20) not null,
    provider_user_id varchar(100) not null,
    email varchar(255),
    connected_at timestamptz not null default now(),
    last_login_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_social_accounts_provider_user
    on social_accounts (provider, provider_user_id);

create index if not exists idx_social_accounts_user_id
    on social_accounts (user_id);

create table if not exists auth_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users (id) on delete cascade,
    refresh_token_hash varchar(255) not null,
    device_id varchar(100),
    device_name varchar(100),
    status varchar(20) not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_auth_sessions_user_status
    on auth_sessions (user_id, status);

create table if not exists terms_agreements (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users (id) on delete cascade,
    terms_type varchar(30) not null,
    terms_version varchar(20) not null,
    is_required boolean not null,
    agreed_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_terms_agreements_user_type
    on terms_agreements (user_id, terms_type);

