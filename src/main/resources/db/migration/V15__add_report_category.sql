alter table post_reports
    add column report_category varchar(20) not null default 'OTHER';

alter table post_reports
    alter column report_category drop default;
