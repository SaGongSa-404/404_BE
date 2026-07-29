create index idx_post_votes_user_active_created
    on post_votes(user_id, created_at desc)
    where canceled_at is null;
