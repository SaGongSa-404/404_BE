create unique index if not exists uk_notifications_reminder_type
    on notifications(reminder_id, notification_type)
    where reminder_id is not null;
