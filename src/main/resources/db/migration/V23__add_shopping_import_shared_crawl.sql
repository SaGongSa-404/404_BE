alter table shopping_import_jobs
    add column leader_job_id uuid,
    add column crawl_key char(64),
    add column source_site varchar(40) not null default 'other';

alter table shopping_import_jobs
    add constraint fk_shopping_import_jobs_leader
        foreign key (leader_job_id) references shopping_import_jobs(id) on delete set null,
    add constraint chk_shopping_import_jobs_not_self_leader
        check (leader_job_id is null or leader_job_id <> id);

create index idx_shopping_import_jobs_active_crawl
    on shopping_import_jobs(crawl_key, status, created_at)
    where leader_job_id is null
      and crawl_key is not null
      and status in ('PENDING', 'RUNNING');

create index idx_shopping_import_jobs_cache
    on shopping_import_jobs(crawl_key, status, completed_at desc)
    where leader_job_id is null
      and crawl_key is not null
      and status in ('SUCCEEDED', 'FAILED');

create index idx_shopping_import_jobs_leader
    on shopping_import_jobs(leader_job_id)
    where leader_job_id is not null;
