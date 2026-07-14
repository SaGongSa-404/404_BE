create table shopping_import_jobs (
    id uuid primary key,
    user_id uuid not null,
    status varchar(20) not null check (status in ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    request_json jsonb not null,
    request_hash char(64) not null,
    result_json jsonb,
    error_code varchar(80),
    error_message varchar(255),
    attempt_count integer not null default 0,
    created_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    updated_at timestamp with time zone not null,
    constraint fk_shopping_import_jobs_user foreign key (user_id) references users(id),
    constraint chk_shopping_import_jobs_attempt_count check (attempt_count >= 0),
    constraint chk_shopping_import_jobs_terminal_state check (
        (status = 'SUCCEEDED' and result_json is not null and completed_at is not null and error_code is null)
        or (status = 'FAILED' and result_json is null and completed_at is not null and error_code is not null)
        or (status in ('PENDING', 'RUNNING') and result_json is null and completed_at is null and error_code is null)
    )
);

create index idx_shopping_import_jobs_queue
    on shopping_import_jobs(status, created_at, id);

create index idx_shopping_import_jobs_user_created
    on shopping_import_jobs(user_id, created_at desc);

create index idx_shopping_import_jobs_completed
    on shopping_import_jobs(completed_at)
    where status in ('SUCCEEDED', 'FAILED');

create unique index uk_shopping_import_jobs_user_active_request
    on shopping_import_jobs(user_id, request_hash)
    where status in ('PENDING', 'RUNNING');
