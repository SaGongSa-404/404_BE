create table if not exists houses (
    id uuid primary key default gen_random_uuid(),
    owner_membership_id uuid,
    name varchar(80) not null,
    cleanliness_level varchar(20),
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists house_memberships (
    id uuid primary key default gen_random_uuid(),
    house_id uuid not null references houses (id) on delete cascade,
    user_id uuid not null references users (id),
    role varchar(20) not null,
    status varchar(20) not null,
    joined_at timestamptz not null default now(),
    left_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'fk_houses_owner_membership'
    ) then
        alter table houses
            add constraint fk_houses_owner_membership
            foreign key (owner_membership_id)
            references house_memberships (id);
    end if;
end $$;

create unique index if not exists uq_house_memberships_active_house_user
    on house_memberships (house_id, user_id)
    where status = 'ACTIVE';

create index if not exists idx_house_memberships_user_id
    on house_memberships (user_id);

create table if not exists invite_codes (
    id uuid primary key default gen_random_uuid(),
    house_id uuid not null references houses (id) on delete cascade,
    code varchar(20) not null,
    status varchar(20) not null,
    expires_at timestamptz,
    created_by_user_id uuid not null references users (id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists uq_invite_codes_code
    on invite_codes (code);

create table if not exists house_roles (
    id uuid primary key default gen_random_uuid(),
    house_id uuid not null references houses (id) on delete cascade,
    role_name varchar(30) not null,
    permissions_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists cleanliness_votes (
    id uuid primary key default gen_random_uuid(),
    house_id uuid not null references houses (id) on delete cascade,
    user_id uuid not null references users (id),
    vote_level varchar(20) not null,
    voted_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_cleanliness_votes_house_id
    on cleanliness_votes (house_id);

create unique index if not exists uq_cleanliness_votes_house_user
    on cleanliness_votes (house_id, user_id);

create table if not exists spaces (
    id uuid primary key default gen_random_uuid(),
    house_id uuid not null references houses (id) on delete cascade,
    name varchar(40) not null,
    sort_order integer not null default 0,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_spaces_house_sort_order
    on spaces (house_id, sort_order);

