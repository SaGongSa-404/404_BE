insert into users (id, status, primary_email, last_login_at, created_at, updated_at)
values ('11111111-1111-1111-1111-111111111111', 'ACTIVE', 'demo@404.local', now(), now(), now())
on conflict (id) do nothing;

insert into user_profiles (id, user_id, nickname, profile_image_url, timezone, created_at, updated_at)
values ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'demo-user', null, 'Asia/Seoul', now(), now())
on conflict (user_id) do nothing;

insert into social_accounts (id, user_id, provider, provider_user_id, email, connected_at, last_login_at, created_at, updated_at)
values ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'KAKAO', 'demo-kakao-user', 'demo@404.local', now(), now(), now(), now())
on conflict (provider, provider_user_id) do nothing;

insert into terms_agreements (id, user_id, terms_type, terms_version, is_required, agreed_at, created_at, updated_at)
values
  ('44444444-4444-4444-4444-444444444441', '11111111-1111-1111-1111-111111111111', 'SERVICE', 'v1', true, now(), now(), now()),
  ('44444444-4444-4444-4444-444444444442', '11111111-1111-1111-1111-111111111111', 'PRIVACY', 'v1', true, now(), now(), now())
on conflict do nothing;

insert into houses (id, owner_membership_id, name, cleanliness_level, status, created_at, updated_at)
values ('55555555-5555-5555-5555-555555555555', null, '404 Demo House', 'BALANCED', 'ACTIVE', now(), now())
on conflict (id) do nothing;

insert into house_memberships (id, house_id, user_id, role, status, joined_at, created_at, updated_at)
values ('66666666-6666-6666-6666-666666666666', '55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'OWNER', 'ACTIVE', now(), now(), now())
on conflict (id) do nothing;

update houses
set owner_membership_id = '66666666-6666-6666-6666-666666666666',
    updated_at = now()
where id = '55555555-5555-5555-5555-555555555555';

insert into invite_codes (id, house_id, code, status, expires_at, created_by_user_id, created_at, updated_at)
values ('77777777-7777-7777-7777-777777777777', '55555555-5555-5555-5555-555555555555', 'DEMO01', 'ACTIVE', null, '11111111-1111-1111-1111-111111111111', now(), now())
on conflict (code) do nothing;

insert into cleanliness_votes (id, house_id, user_id, vote_level, voted_at, created_at, updated_at)
values ('88888888-8888-8888-8888-888888888888', '55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'BALANCED', now(), now(), now())
on conflict (house_id, user_id) do nothing;

insert into spaces (id, house_id, name, sort_order, status, created_at, updated_at)
values ('99999999-9999-9999-9999-999999999999', '55555555-5555-5555-5555-555555555555', '주방', 0, 'ACTIVE', now(), now())
on conflict (id) do nothing;

insert into chore_rules (id, house_id, space_id, title, description, default_assignee_membership_id, estimated_minutes, status, created_at, updated_at)
values ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '55555555-5555-5555-5555-555555555555', '99999999-9999-9999-9999-999999999999', '분리수거', '데모 집안일', '66666666-6666-6666-6666-666666666666', 10, 'ACTIVE', now(), now())
on conflict (id) do nothing;

insert into recurrence_rules (id, chore_rule_id, frequency, interval_value, days_of_week, start_date, end_date, created_at, updated_at)
values ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'DAILY', 1, array['MON','TUE','WED','THU','FRI','SAT','SUN'], current_date, null, now(), now())
on conflict (chore_rule_id) do nothing;

insert into chore_instances (id, house_id, chore_rule_id, scheduled_date, status, current_assignee_membership_id, origin_type, created_at, updated_at)
values ('cccccccc-cccc-cccc-cccc-cccccccccccc', '55555555-5555-5555-5555-555555555555', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', current_date, 'PENDING', '66666666-6666-6666-6666-666666666666', 'RULE', now(), now())
on conflict (id) do nothing;

insert into chore_assignments (id, chore_instance_id, assignee_membership_id, assigned_by_user_id, assigned_at, assignment_type, created_at, updated_at)
values ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'cccccccc-cccc-cccc-cccc-cccccccccccc', '66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', now(), 'AUTO', now(), now())
on conflict (id) do nothing;

