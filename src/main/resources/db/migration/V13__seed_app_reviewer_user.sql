insert into users (id, status, onboarding_status, created_at, updated_at, withdrawn_at)
values (
    '40400000-0000-0000-0000-000000000055',
    'ACTIVE',
    'COMPLETED',
    now(),
    now(),
    null
)
on conflict (id) do update
set status = 'ACTIVE',
    onboarding_status = 'COMPLETED',
    updated_at = now(),
    withdrawn_at = null;

insert into social_accounts (id, user_id, provider, provider_user_id, email, profile_image_url, created_at, updated_at)
values (
    '40400000-0000-0000-0000-000000055001',
    '40400000-0000-0000-0000-000000000055',
    'KAKAO',
    'app-reviewer-fixed',
    'app-reviewer@sagongsa.dev',
    null,
    now(),
    now()
)
on conflict on constraint uk_social_accounts_provider_user do update
set user_id = excluded.user_id,
    email = excluded.email,
    profile_image_url = excluded.profile_image_url,
    updated_at = now();

insert into user_profiles (user_id, nickname, mascot_name, timezone, profile_image_url, notification_enabled, created_at, updated_at)
values (
    '40400000-0000-0000-0000-000000000055',
    '심사너굴',
    '너구리',
    'Asia/Seoul',
    null,
    true,
    now(),
    now()
)
on conflict (user_id) do update
set nickname = excluded.nickname,
    mascot_name = excluded.mascot_name,
    timezone = excluded.timezone,
    profile_image_url = excluded.profile_image_url,
    notification_enabled = excluded.notification_enabled,
    updated_at = now();
