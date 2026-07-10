alter table saved_items
    add column if not exists idempotency_key varchar(120);

create unique index if not exists uk_saved_items_user_idempotency_key
    on saved_items(user_id, idempotency_key)
    where idempotency_key is not null;
