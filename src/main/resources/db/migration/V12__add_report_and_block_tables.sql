create table post_reports (
    id          uuid         primary key,
    reporter_user_id uuid    not null,
    target_type varchar(10)  not null check (target_type in ('POST', 'COMMENT')),
    target_id   uuid         not null,
    reason      varchar(100),
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    constraint fk_post_reports_reporter foreign key (reporter_user_id) references users(id),
    constraint uk_post_reports unique (reporter_user_id, target_type, target_id)
);

create table user_blocks (
    id               uuid        primary key,
    blocker_user_id  uuid        not null,
    blocked_user_id  uuid        not null,
    created_at       timestamptz not null,
    updated_at       timestamptz not null,
    constraint fk_user_blocks_blocker foreign key (blocker_user_id) references users(id),
    constraint fk_user_blocks_blocked foreign key (blocked_user_id) references users(id),
    constraint uk_user_blocks         unique (blocker_user_id, blocked_user_id),
    constraint chk_user_blocks_self   check  (blocker_user_id <> blocked_user_id)
);
