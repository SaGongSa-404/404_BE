comment on constraint uk_social_accounts_user_id on social_accounts is
    'MVP policy: a user can link exactly one social login provider. Relax this constraint when multi-provider linking is introduced.';

create or replace function assert_self_check_matches_decision()
returns trigger
language plpgsql
as $$
declare
    decision_self_check_yes_count smallint;
    decision_rationality_result varchar(20);
begin
    select self_check_yes_count, rationality_result
      into decision_self_check_yes_count, decision_rationality_result
      from purchase_decisions
     where id = new.decision_id;

    if decision_self_check_yes_count is distinct from new.yes_count
        or decision_rationality_result is distinct from new.rationality_result then
        raise exception 'self_check_response_sets must match purchase_decisions rationality fields'
            using errcode = '23514';
    end if;

    return new;
end;
$$;

create or replace function assert_decision_matches_self_check()
returns trigger
language plpgsql
as $$
declare
    self_check_yes_count smallint;
    self_check_rationality_result varchar(20);
begin
    select yes_count, rationality_result
      into self_check_yes_count, self_check_rationality_result
      from self_check_response_sets
     where decision_id = new.id;

    if found and (
        self_check_yes_count is distinct from new.self_check_yes_count
        or self_check_rationality_result is distinct from new.rationality_result
    ) then
        raise exception 'purchase_decisions rationality fields must match self_check_response_sets'
            using errcode = '23514';
    end if;

    return new;
end;
$$;

do $$
begin
    if exists (
        select 1
          from self_check_response_sets sc
          join purchase_decisions pd on pd.id = sc.decision_id
         where sc.yes_count is distinct from pd.self_check_yes_count
            or sc.rationality_result is distinct from pd.rationality_result
    ) then
        raise exception 'existing self_check_response_sets and purchase_decisions rationality fields do not match'
            using errcode = '23514';
    end if;
end;
$$;

create constraint trigger trg_self_check_matches_decision
after insert or update on self_check_response_sets
deferrable initially deferred
for each row execute function assert_self_check_matches_decision();

create constraint trigger trg_decision_matches_self_check
after update on purchase_decisions
deferrable initially deferred
for each row execute function assert_decision_matches_self_check();

alter table post_votes drop constraint if exists uk_post_votes_post_user;

create unique index uk_post_votes_post_user_active on post_votes(post_id, user_id) where canceled_at is null;

update feed_posts fp
   set go_count = vote_counts.go_count,
       stop_count = vote_counts.stop_count,
       updated_at = now()
  from (
        select fp_inner.id as post_id,
               count(pv.id) filter (where pv.vote_type = 'GO' and pv.canceled_at is null)::integer as go_count,
               count(pv.id) filter (where pv.vote_type = 'STOP' and pv.canceled_at is null)::integer as stop_count
          from feed_posts fp_inner
          left join post_votes pv on pv.post_id = fp_inner.id
         group by fp_inner.id
       ) vote_counts
 where fp.id = vote_counts.post_id;

create or replace function sync_feed_post_vote_counts()
returns trigger
language plpgsql
as $$
begin
    if tg_op = 'INSERT' then
        if new.canceled_at is null then
            update feed_posts
               set go_count = go_count + case when new.vote_type = 'GO' then 1 else 0 end,
                   stop_count = stop_count + case when new.vote_type = 'STOP' then 1 else 0 end,
                   updated_at = now()
             where id = new.post_id;
        end if;
        return new;
    elsif tg_op = 'UPDATE' then
        if old.canceled_at is null then
            update feed_posts
               set go_count = go_count - case when old.vote_type = 'GO' then 1 else 0 end,
                   stop_count = stop_count - case when old.vote_type = 'STOP' then 1 else 0 end,
                   updated_at = now()
             where id = old.post_id;
        end if;

        if new.canceled_at is null then
            update feed_posts
               set go_count = go_count + case when new.vote_type = 'GO' then 1 else 0 end,
                   stop_count = stop_count + case when new.vote_type = 'STOP' then 1 else 0 end,
                   updated_at = now()
             where id = new.post_id;
        end if;
        return new;
    elsif tg_op = 'DELETE' then
        if old.canceled_at is null then
            update feed_posts
               set go_count = go_count - case when old.vote_type = 'GO' then 1 else 0 end,
                   stop_count = stop_count - case when old.vote_type = 'STOP' then 1 else 0 end,
                   updated_at = now()
             where id = old.post_id;
        end if;
        return old;
    end if;

    return null;
end;
$$;

create trigger trg_post_votes_sync_feed_counts
after insert or update or delete on post_votes
for each row execute function sync_feed_post_vote_counts();

comment on table share_tokens is
    'MVP policy: one non-expiring share token is issued per feed post. Add expires_at/revoked_at when abuse handling requires it.';
