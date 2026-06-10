alter table notifications
    add column if not exists channel_id varchar(40),
    add column if not exists dedupe_key varchar(160);

alter table notifications
    drop constraint if exists notifications_notification_type_check;

alter table notifications
    add constraint notifications_notification_type_check
        check (notification_type in (
            'REGRET_CHECK_READY',
            'REGRET_CHECK_FOLLOW_UP',
            'WISHLIST_REMINDER',
            'BUDGET_WARNING',
            'BUDGET_RESET',
            'SOCIAL_VOTE',
            'SOCIAL_FIRST_VOTE',
            'SOCIAL_VOTE_SUMMARY',
            'SOCIAL_DECISION_NUDGE',
            'SOCIAL_COMMENT',
            'APP_UPDATE',
            'MAINTENANCE_NOTICE'
        ));

create unique index if not exists uk_notifications_user_type_dedupe
    on notifications(user_id, notification_type, dedupe_key)
    where dedupe_key is not null;

create index if not exists idx_notifications_type_created
    on notifications(notification_type, created_at);
