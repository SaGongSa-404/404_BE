do $$
declare
    v_constraint_name text;
begin
    select conname into v_constraint_name
    from pg_constraint
    where conrelid = 'users'::regclass
      and contype = 'c'
      and pg_get_constraintdef(oid) like '%ACTIVE%'
      and pg_get_constraintdef(oid) like '%SUSPENDED%'
      and pg_get_constraintdef(oid) like '%WITHDRAWN%';

    if v_constraint_name is not null then
        execute 'alter table users drop constraint ' || quote_ident(v_constraint_name);
    end if;
end $$;

alter table users
    add constraint chk_users_status
        check (status in ('ACTIVE', 'SUSPENDED', 'BANNED', 'WITHDRAWN')),
    add column suspended_until timestamptz,
    add column banned_at timestamptz;

alter table post_reports
    add column reported_user_id uuid,
    add column post_id uuid;

update post_reports pr
set reported_user_id = fp.user_id,
    post_id = fp.id
from feed_posts fp
where pr.target_type = 'POST'
  and pr.target_id = fp.id;

update post_reports pr
set reported_user_id = pc.user_id,
    post_id = pc.post_id
from post_comments pc
where pr.target_type = 'COMMENT'
  and pr.target_id = pc.id;

update post_reports
set reported_user_id = target_id
where target_type = 'USER';

alter table post_reports
    alter column reported_user_id set not null,
    alter column report_category type varchar(30),
    add constraint fk_post_reports_reported_user foreign key (reported_user_id) references users(id),
    add constraint fk_post_reports_post foreign key (post_id) references feed_posts(id),
    add constraint chk_post_reports_content_post_id check (
        (target_type = 'USER' and post_id is null)
        or (target_type in ('POST', 'COMMENT') and post_id is not null)
    );

create index idx_post_reports_target_count on post_reports(target_type, target_id, created_at);
create index idx_post_reports_reported_user on post_reports(reported_user_id, created_at desc);
create index idx_post_reports_post on post_reports(post_id, created_at desc);

alter table feed_posts
    add column moderation_status varchar(20) not null default 'ACTIVE',
    add column moderation_status_updated_at timestamptz,
    add constraint chk_feed_posts_moderation_status
        check (moderation_status in ('ACTIVE', 'BLINDED', 'REVIEW_PENDING', 'REMOVED'));

alter table post_comments
    add column moderation_status varchar(20) not null default 'ACTIVE',
    add column moderation_status_updated_at timestamptz,
    add constraint chk_post_comments_moderation_status
        check (moderation_status in ('ACTIVE', 'BLINDED', 'REVIEW_PENDING', 'REMOVED'));

create index idx_feed_posts_active_created on feed_posts(created_at desc)
    where deleted_at is null and moderation_status = 'ACTIVE';

create index idx_post_comments_active_post_created on post_comments(post_id, created_at)
    where deleted_at is null and moderation_status = 'ACTIVE';

create table user_sanctions (
    id uuid primary key,
    user_id uuid not null,
    target_type varchar(10) check (target_type in ('POST', 'COMMENT', 'USER')),
    target_id uuid,
    sanction_type varchar(20) not null check (sanction_type in ('WARNING', 'SUSPENSION', 'PERMANENT_BAN')),
    reason varchar(255),
    starts_at timestamptz not null,
    ends_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint fk_user_sanctions_user foreign key (user_id) references users(id),
    constraint chk_user_sanctions_period check (
        (sanction_type = 'SUSPENSION' and ends_at is not null)
        or (sanction_type <> 'SUSPENSION' and ends_at is null)
    )
);

create index idx_user_sanctions_user_created on user_sanctions(user_id, created_at desc);
