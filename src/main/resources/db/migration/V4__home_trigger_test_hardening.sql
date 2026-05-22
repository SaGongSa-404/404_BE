create or replace function prevent_self_check_response_set_delete()
returns trigger
language plpgsql
as $$
begin
    raise exception 'self_check_response_sets cannot be deleted while purchase_decisions keeps rationality summary fields'
        using errcode = '23514';
end;
$$;

create trigger trg_prevent_self_check_response_set_delete
before delete on self_check_response_sets
for each row execute function prevent_self_check_response_set_delete();

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
        if old.post_id is not distinct from new.post_id
            and old.vote_type is not distinct from new.vote_type
            and old.canceled_at is not distinct from new.canceled_at then
            return new;
        end if;

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
