create table users (
    id uuid primary key,
    status varchar(20) not null check (status in ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    onboarding_status varchar(20) not null check (onboarding_status in ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    withdrawn_at timestamp with time zone,
    constraint chk_users_withdrawn_state check (
        (status = 'WITHDRAWN' and withdrawn_at is not null)
        or (status <> 'WITHDRAWN' and withdrawn_at is null)
    )
);

create table social_accounts (
    id uuid primary key,
    user_id uuid not null,
    provider varchar(20) not null check (provider in ('GOOGLE', 'KAKAO', 'APPLE')),
    provider_user_id varchar(120) not null,
    email varchar(255),
    profile_image_url text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_social_accounts_user foreign key (user_id) references users(id),
    constraint uk_social_accounts_provider_user unique (provider, provider_user_id),
    constraint uk_social_accounts_user_id unique (user_id)
);

create table refresh_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(255) not null,
    device_id varchar(120),
    device_name varchar(120),
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    last_used_at timestamp with time zone,
    constraint fk_refresh_tokens_user foreign key (user_id) references users(id),
    constraint uk_refresh_tokens_token_hash unique (token_hash)
);

create index idx_refresh_tokens_user_created_at on refresh_tokens(user_id, created_at desc);

create table user_profiles (
    user_id uuid primary key,
    nickname varchar(40) not null,
    mascot_name varchar(40) not null,
    timezone varchar(40) not null default 'Asia/Seoul',
    profile_image_url text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_user_profiles_user foreign key (user_id) references users(id)
);

create table survey_response_sets (
    id uuid primary key,
    user_id uuid not null,
    survey_type varchar(40) not null check (survey_type in ('ONBOARDING')),
    submitted_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_survey_response_sets_user foreign key (user_id) references users(id),
    constraint uk_survey_response_sets_user_type unique (user_id, survey_type)
);

create table survey_answers (
    id uuid primary key,
    response_set_id uuid not null,
    question_code varchar(80) not null,
    answer_text text,
    answer_number integer,
    answer_choice varchar(80),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_survey_answers_response_set foreign key (response_set_id) references survey_response_sets(id),
    constraint uk_survey_answers_response_question unique (response_set_id, question_code)
);

create table budget_cycles (
    id uuid primary key,
    user_id uuid not null,
    year_month char(7) not null,
    monthly_budget_amount integer not null,
    spent_amount integer not null default 0,
    warning_threshold_rate numeric(5,2) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_budget_cycles_user foreign key (user_id) references users(id),
    constraint chk_budget_cycles_year_month_format check (year_month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    constraint chk_budget_cycles_non_negative_amounts check (
        monthly_budget_amount >= 0
        and spent_amount >= 0
        and warning_threshold_rate >= 0
        and warning_threshold_rate <= 100
    ),
    constraint uk_budget_cycles_user_year_month unique (user_id, year_month)
);

create table saved_items (
    id uuid primary key,
    user_id uuid not null,
    input_source varchar(20) not null check (input_source in ('SHARE', 'DIRECT_INPUT')),
    original_url text,
    normalized_url text,
    title varchar(255) not null,
    image_url text,
    listed_price integer,
    currency_code char(3),
    category varchar(30) not null check (category in ('FASHION', 'BEAUTY', 'DIGITAL', 'LIVING', 'FOOD', 'HOBBY', 'SUBSCRIPTION', 'ETC')),
    category_confidence numeric(5,2),
    category_locked_by_user boolean not null default false,
    status varchar(20) not null check (status in ('SAVED', 'GO', 'STOP', 'DROPPED')),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_saved_items_user foreign key (user_id) references users(id),
    constraint chk_saved_items_non_negative_numbers check (
        (listed_price is null or listed_price >= 0)
        and (category_confidence is null or (category_confidence >= 0 and category_confidence <= 100))
    )
);

create index idx_saved_items_user_status_created on saved_items(user_id, status, created_at desc);
create index idx_saved_items_user_category_created on saved_items(user_id, category, created_at desc);
create unique index uk_saved_items_user_saved_url on saved_items(user_id, normalized_url)
    where status = 'SAVED' and normalized_url is not null;

create table item_source_metadata (
    item_id uuid primary key,
    source_domain varchar(120),
    raw_title text,
    raw_description text,
    raw_price_text varchar(120),
    raw_payload_json jsonb,
    extracted_at timestamp with time zone not null,
    constraint fk_item_source_metadata_item foreign key (item_id) references saved_items(id)
);

create table purchase_decisions (
    id uuid primary key,
    user_id uuid not null,
    item_id uuid not null,
    budget_cycle_id uuid,
    result varchar(20) not null check (result in ('GO', 'STOP')),
    final_price integer,
    budget_after_amount integer,
    similar_category_spend_amount integer,
    rationality_result varchar(20) not null check (rationality_result in ('RATIONAL', 'IRRATIONAL')),
    self_check_yes_count smallint not null,
    rationale_text text,
    is_changed boolean not null default false,
    change_count integer not null default 0,
    changed_at timestamp with time zone,
    decided_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_purchase_decisions_user foreign key (user_id) references users(id),
    constraint fk_purchase_decisions_item foreign key (item_id) references saved_items(id),
    constraint fk_purchase_decisions_budget_cycle foreign key (budget_cycle_id) references budget_cycles(id),
    constraint chk_decisions_yes_count check (self_check_yes_count between 0 and 4),
    constraint chk_decisions_non_negative_amounts check (
        (final_price is null or final_price >= 0)
        and (budget_after_amount is null or budget_after_amount >= 0)
        and (similar_category_spend_amount is null or similar_category_spend_amount >= 0)
        and change_count >= 0
    ),
    constraint chk_decisions_rationality check (
        (self_check_yes_count between 0 and 1 and rationality_result = 'RATIONAL')
        or (self_check_yes_count between 2 and 4 and rationality_result = 'IRRATIONAL')
    ),
    constraint uk_purchase_decisions_item_id unique (item_id)
);

create index idx_purchase_decisions_user_decided_at on purchase_decisions(user_id, decided_at desc);

create table purchase_decision_change_logs (
    id uuid primary key,
    decision_id uuid not null,
    user_id uuid not null,
    item_id uuid not null,
    previous_result varchar(20) not null check (previous_result in ('GO', 'STOP')),
    new_result varchar(20) not null check (new_result in ('GO', 'STOP')),
    previous_final_price integer,
    new_final_price integer,
    previous_rationality_result varchar(20) check (previous_rationality_result in ('RATIONAL', 'IRRATIONAL')),
    new_rationality_result varchar(20) check (new_rationality_result in ('RATIONAL', 'IRRATIONAL')),
    previous_self_check_yes_count smallint,
    new_self_check_yes_count smallint,
    reason_text text,
    changed_at timestamp with time zone not null,
    constraint fk_purchase_decision_change_logs_decision foreign key (decision_id) references purchase_decisions(id),
    constraint fk_purchase_decision_change_logs_user foreign key (user_id) references users(id),
    constraint fk_purchase_decision_change_logs_item foreign key (item_id) references saved_items(id),
    constraint chk_decision_logs_non_negative_amounts check (
        (previous_final_price is null or previous_final_price >= 0)
        and (new_final_price is null or new_final_price >= 0)
    ),
    constraint chk_decision_logs_prev_yes_count check (
        previous_self_check_yes_count is null or previous_self_check_yes_count between 0 and 4
    ),
    constraint chk_decision_logs_new_yes_count check (
        new_self_check_yes_count is null or new_self_check_yes_count between 0 and 4
    ),
    constraint chk_decision_logs_prev_rationality check (
        previous_self_check_yes_count is null
        or previous_rationality_result is null
        or (
            previous_self_check_yes_count between 0 and 1 and previous_rationality_result = 'RATIONAL'
        )
        or (
            previous_self_check_yes_count between 2 and 4 and previous_rationality_result = 'IRRATIONAL'
        )
    ),
    constraint chk_decision_logs_new_rationality check (
        new_self_check_yes_count is null
        or new_rationality_result is null
        or (
            new_self_check_yes_count between 0 and 1 and new_rationality_result = 'RATIONAL'
        )
        or (
            new_self_check_yes_count between 2 and 4 and new_rationality_result = 'IRRATIONAL'
        )
    )
);

create index idx_purchase_decision_change_logs_decision_changed on purchase_decision_change_logs(decision_id, changed_at desc);
create index idx_purchase_decision_change_logs_user_changed on purchase_decision_change_logs(user_id, changed_at desc);

create table self_check_response_sets (
    id uuid primary key,
    decision_id uuid not null,
    yes_count smallint not null,
    rationality_result varchar(20) not null check (rationality_result in ('RATIONAL', 'IRRATIONAL')),
    submitted_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_self_check_response_sets_decision foreign key (decision_id) references purchase_decisions(id),
    constraint chk_self_check_sets_yes_count check (yes_count between 0 and 4),
    constraint chk_self_check_sets_rationality check (
        (yes_count between 0 and 1 and rationality_result = 'RATIONAL')
        or (yes_count between 2 and 4 and rationality_result = 'IRRATIONAL')
    ),
    constraint uk_self_check_response_sets_decision_id unique (decision_id)
);

create table self_check_answers (
    id uuid primary key,
    response_set_id uuid not null,
    question_code varchar(80) not null,
    answer_boolean boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_self_check_answers_response_set foreign key (response_set_id) references self_check_response_sets(id),
    constraint uk_self_check_answers_response_question unique (response_set_id, question_code)
);

create table user_notification_settings (
    user_id uuid primary key,
    push_enabled boolean not null default true,
    regret_reminder_enabled boolean not null default true,
    wishlist_reminder_enabled boolean not null default false,
    budget_warning_enabled boolean not null default false,
    social_vote_enabled boolean not null default false,
    updated_at timestamp with time zone not null,
    constraint fk_user_notification_settings_user foreign key (user_id) references users(id)
);

create table device_push_tokens (
    id uuid primary key,
    user_id uuid not null,
    device_id varchar(120),
    platform varchar(20) not null check (platform in ('IOS', 'ANDROID')),
    push_token varchar(512) not null,
    is_active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    disabled_at timestamp with time zone,
    constraint fk_device_push_tokens_user foreign key (user_id) references users(id),
    constraint uk_device_push_tokens_push_token unique (push_token)
);

create index idx_device_push_tokens_user_active on device_push_tokens(user_id, is_active);

create table reminder_schedules (
    id uuid primary key,
    user_id uuid not null,
    item_id uuid,
    decision_id uuid,
    reminder_type varchar(40) not null check (reminder_type in ('REGRET_CHECK_7_DAYS')),
    scheduled_for timestamp with time zone not null,
    status varchar(20) not null check (status in ('SCHEDULED', 'SENT', 'FAILED', 'CANCELED')),
    sent_at timestamp with time zone,
    canceled_at timestamp with time zone,
    cancel_reason varchar(80),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_reminder_schedules_user foreign key (user_id) references users(id),
    constraint fk_reminder_schedules_item foreign key (item_id) references saved_items(id),
    constraint fk_reminder_schedules_decision foreign key (decision_id) references purchase_decisions(id),
    constraint chk_reminder_schedules_regret_decision check (
        reminder_type <> 'REGRET_CHECK_7_DAYS' or decision_id is not null
    ),
    constraint chk_reminder_schedules_status_timestamps check (
        (status = 'SCHEDULED' and sent_at is null and canceled_at is null)
        or (status = 'SENT' and sent_at is not null and canceled_at is null)
        or (status = 'FAILED' and sent_at is null and canceled_at is null)
        or (status = 'CANCELED' and sent_at is null and canceled_at is not null)
    ),
    constraint uk_reminder_schedules_decision_type unique (decision_id, reminder_type)
);

create index idx_reminder_schedules_user_scheduled_for on reminder_schedules(user_id, scheduled_for);
create index idx_reminder_schedules_status_scheduled_for on reminder_schedules(status, scheduled_for);

create table notifications (
    id uuid primary key,
    user_id uuid not null,
    notification_type varchar(40) not null check (notification_type in ('REGRET_CHECK_READY', 'WISHLIST_REMINDER', 'BUDGET_WARNING', 'SOCIAL_VOTE')),
    title varchar(140) not null,
    body text not null,
    item_id uuid,
    decision_id uuid,
    reminder_id uuid,
    target_path text,
    is_read boolean not null default false,
    read_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_notifications_user foreign key (user_id) references users(id),
    constraint fk_notifications_item foreign key (item_id) references saved_items(id),
    constraint fk_notifications_decision foreign key (decision_id) references purchase_decisions(id),
    constraint fk_notifications_reminder foreign key (reminder_id) references reminder_schedules(id),
    constraint chk_notifications_read_state check (
        (is_read = false and read_at is null)
        or (is_read = true and read_at is not null)
    )
);

create index idx_notifications_user_read_created on notifications(user_id, is_read, created_at desc);
create index idx_notifications_user_created on notifications(user_id, created_at desc);

create table purchase_reflections (
    id uuid primary key,
    user_id uuid not null,
    item_id uuid not null,
    decision_id uuid not null,
    reminder_id uuid,
    satisfaction_score smallint check (satisfaction_score between 1 and 5),
    regret_level varchar(20) not null check (regret_level in ('NONE', 'LOW', 'MEDIUM', 'HIGH')),
    still_using boolean,
    reflection_note text,
    reflected_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_purchase_reflections_user foreign key (user_id) references users(id),
    constraint fk_purchase_reflections_item foreign key (item_id) references saved_items(id),
    constraint fk_purchase_reflections_decision foreign key (decision_id) references purchase_decisions(id),
    constraint fk_purchase_reflections_reminder foreign key (reminder_id) references reminder_schedules(id),
    constraint uk_purchase_reflections_decision_id unique (decision_id)
);

create index idx_purchase_reflections_user_reflected_at on purchase_reflections(user_id, reflected_at desc);

create table mascot_profiles (
    user_id uuid primary key,
    mascot_state varchar(20) not null check (mascot_state in ('DEFAULT', 'SMILE', 'VERY_HAPPY', 'SAD')),
    last_reaction_message varchar(140),
    last_state_changed_at timestamp with time zone not null,
    reaction_expires_at timestamp with time zone,
    updated_at timestamp with time zone not null,
    constraint fk_mascot_profiles_user foreign key (user_id) references users(id)
);

create table mascot_state_events (
    id uuid primary key,
    user_id uuid not null,
    item_id uuid,
    decision_id uuid,
    event_type varchar(40) not null check (event_type in ('DECISION_REACTION', 'REACTION_RESET')),
    previous_state varchar(20) check (previous_state in ('DEFAULT', 'SMILE', 'VERY_HAPPY', 'SAD')),
    new_state varchar(20) not null check (new_state in ('DEFAULT', 'SMILE', 'VERY_HAPPY', 'SAD')),
    reaction_message varchar(140),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_mascot_state_events_user foreign key (user_id) references users(id),
    constraint fk_mascot_state_events_item foreign key (item_id) references saved_items(id),
    constraint fk_mascot_state_events_decision foreign key (decision_id) references purchase_decisions(id)
);

create index idx_mascot_state_events_user_created on mascot_state_events(user_id, created_at desc);
create index idx_mascot_state_events_decision_id on mascot_state_events(decision_id);

create table feed_posts (
    id uuid primary key,
    user_id uuid not null,
    item_id uuid,
    decision_id uuid,
    title varchar(140) not null,
    body text,
    go_count integer not null default 0,
    stop_count integer not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted_at timestamp with time zone,
    constraint fk_feed_posts_user foreign key (user_id) references users(id),
    constraint fk_feed_posts_item foreign key (item_id) references saved_items(id),
    constraint fk_feed_posts_decision foreign key (decision_id) references purchase_decisions(id),
    constraint chk_feed_posts_vote_counts check (go_count >= 0 and stop_count >= 0)
);

create index idx_feed_posts_visible_created on feed_posts(created_at desc) where deleted_at is null;
create index idx_feed_posts_user_created on feed_posts(user_id, created_at desc);

create table post_votes (
    id uuid primary key,
    post_id uuid not null,
    user_id uuid not null,
    vote_type varchar(10) not null check (vote_type in ('GO', 'STOP')),
    canceled_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_post_votes_post foreign key (post_id) references feed_posts(id),
    constraint fk_post_votes_user foreign key (user_id) references users(id),
    constraint uk_post_votes_post_user unique (post_id, user_id)
);

create index idx_post_votes_post_type_active on post_votes(post_id, vote_type) where canceled_at is null;

create table post_comments (
    id uuid primary key,
    post_id uuid not null,
    user_id uuid not null,
    body varchar(300) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted_at timestamp with time zone,
    constraint fk_post_comments_post foreign key (post_id) references feed_posts(id),
    constraint fk_post_comments_user foreign key (user_id) references users(id)
);

create index idx_post_comments_post_created_visible on post_comments(post_id, created_at) where deleted_at is null;

create table share_tokens (
    id uuid primary key,
    feed_post_id uuid not null,
    token varchar(120) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_share_tokens_feed_post foreign key (feed_post_id) references feed_posts(id),
    constraint uk_share_tokens_feed_post unique (feed_post_id),
    constraint uk_share_tokens_token unique (token)
);

create table terms_versions (
    id uuid primary key,
    terms_type varchar(40) not null check (terms_type in ('TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'MARKETING', 'AGE_CONFIRMATION')),
    version varchar(40) not null,
    title varchar(120) not null,
    content_url text,
    is_required boolean not null,
    effective_from timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_terms_versions_type_version unique (terms_type, version)
);

create index idx_terms_versions_type_effective_from on terms_versions(terms_type, effective_from desc);

create table user_terms_agreements (
    id uuid primary key,
    user_id uuid not null,
    terms_version_id uuid not null,
    agreed_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_user_terms_agreements_user foreign key (user_id) references users(id),
    constraint fk_user_terms_agreements_terms_version foreign key (terms_version_id) references terms_versions(id),
    constraint uk_user_terms_agreements_user_version unique (user_id, terms_version_id)
);

create index idx_user_terms_agreements_user_agreed on user_terms_agreements(user_id, agreed_at desc);

create table marketing_consent_histories (
    id uuid primary key,
    user_id uuid not null,
    consented boolean not null,
    changed_at timestamp with time zone not null,
    source varchar(40),
    note text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_marketing_consent_histories_user foreign key (user_id) references users(id)
);

create index idx_marketing_consent_histories_user_changed on marketing_consent_histories(user_id, changed_at desc);
