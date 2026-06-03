do $$
declare
    v_constraint_name text;
begin
    select conname into v_constraint_name
    from pg_constraint
    where conrelid = 'post_reports'::regclass
      and contype = 'c'
      and pg_get_constraintdef(oid) like '%target_type%';

    if v_constraint_name is not null then
        execute 'alter table post_reports drop constraint ' || quote_ident(v_constraint_name);
    end if;
end $$;

alter table post_reports
    add constraint chk_post_reports_target_type
        check (target_type in ('POST', 'COMMENT', 'USER'));
