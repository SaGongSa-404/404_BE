-- Actual purchase records are independent of legacy survey decisions.
create table purchase_outcomes (
    item_id uuid primary key references saved_items(id) on delete cascade,
    status varchar(20) not null check (status in ('CONSIDERING','PURCHASED','DECLINED')),
    note varchar(500) not null default '',
    actual_price integer,
    purchased_on date,
    budget_cycle_id uuid references budget_cycles(id),
    revision bigint not null check (revision > 0),
    updated_at timestamptz not null,
    constraint chk_purchase_outcome_amount check (
        (status = 'PURCHASED' and actual_price is not null and actual_price >= 0
          and purchased_on is not null and budget_cycle_id is not null)
        or (status <> 'PURCHASED' and actual_price is null and purchased_on is null and budget_cycle_id is null)
    )
);
create table purchase_outcome_mutations (
    item_id uuid not null references purchase_outcomes(item_id) on delete cascade,
    mutation_id uuid not null,
    request_json jsonb not null,
    response_json jsonb not null,
    created_at timestamptz not null default now(),
    primary key (item_id, mutation_id)
);
