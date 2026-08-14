#!/usr/bin/env bash
set -euo pipefail

action="${1:-}"
if [[ "$action" != "apply" && "$action" != "cleanup" ]]; then
  echo "Usage: CONFIRM_QA_TEST_USERS=YES $0 apply|cleanup" >&2
  exit 2
fi
if [[ "${CONFIRM_QA_TEST_USERS:-}" != "YES" ]]; then
  echo "Set CONFIRM_QA_TEST_USERS=YES after confirming the QA target" >&2
  exit 1
fi

SSH_TARGET="${SSH_TARGET:-koreaworldclass@34.66.55.165}"
SSH_IDENTITY_FILE="${SSH_IDENTITY_FILE:-$HOME/.ssh/wigul_gha_deploy}"
EXPECTED_QA_HOSTNAME="${EXPECTED_QA_HOSTNAME:-nf-qa-be}"
TOKEN_FILE="${ACCESS_TOKEN_FILE:-$HOME/.secrets/wigul-nf97-qa-users.tokens}"
mkdir -p "$(dirname "$TOKEN_FILE")"
ssh_options=(-o BatchMode=yes -o IdentitiesOnly=yes)
if [[ -n "$SSH_IDENTITY_FILE" ]]; then
  [[ -r "$SSH_IDENTITY_FILE" ]] || { echo "SSH identity file is not readable: $SSH_IDENTITY_FILE" >&2; exit 1; }
  ssh_options+=(-i "$SSH_IDENTITY_FILE")
fi

if [[ "$action" == "cleanup" ]]; then
  ssh "${ssh_options[@]}" "$SSH_TARGET" bash -s -- "$EXPECTED_QA_HOSTNAME" cleanup <<'REMOTE'
set -euo pipefail
expected_hostname="$1"
action="$2"
[[ "$(hostname)" == "$expected_hostname" ]] || { echo "Refusing host: $(hostname)" >&2; exit 1; }
env_file="$HOME/404_BE/.env.systemd"
[[ -f "$env_file" ]] || { echo "Environment file not found: $env_file" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
source "$env_file"
set +a
database_url="${SPRING_DATASOURCE_URL#jdbc:}"
psql_cmd=(psql -X -v ON_ERROR_STOP=1 -q "$database_url" -U "$SPRING_DATASOURCE_USERNAME")
active_count="$(PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" "${psql_cmd[@]}" -Atc \
  "select count(*) from shopping_import_jobs where user_id::text like '40410000-0000-0000-0000-%' and status in ('PENDING','RUNNING')")"
[[ "$active_count" == "0" ]] || { echo "Refusing cleanup: $active_count test job(s) are active" >&2; exit 1; }
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" "${psql_cmd[@]}" <<'SQL'
begin;
delete from shopping_import_jobs where user_id::text like '40410000-0000-0000-0000-%';
delete from refresh_tokens where user_id::text like '40410000-0000-0000-0000-%';
delete from user_profiles where user_id::text like '40410000-0000-0000-0000-%';
delete from social_accounts where user_id::text like '40410000-0000-0000-0000-%';
delete from users where id::text like '40410000-0000-0000-0000-%';
commit;
SQL
echo "QA_TEST_USERS_CLEANED=100" >&2
REMOTE
  rm -f "$TOKEN_FILE"
  exit 0
fi

temp_token_file="$(mktemp "${TOKEN_FILE}.XXXXXX")"
cleanup_failed_apply() {
  rm -f "$temp_token_file"
  ACCESS_TOKEN_FILE="$TOKEN_FILE" SSH_TARGET="$SSH_TARGET" SSH_IDENTITY_FILE="$SSH_IDENTITY_FILE" \
    "$0" cleanup >/dev/null 2>&1 || true
}
trap cleanup_failed_apply EXIT
ssh "${ssh_options[@]}" "$SSH_TARGET" bash -s -- "$EXPECTED_QA_HOSTNAME" apply > "$temp_token_file" <<'REMOTE'
set -euo pipefail
expected_hostname="$1"
action="$2"
[[ "$(hostname)" == "$expected_hostname" ]] || { echo "Refusing host: $(hostname)" >&2; exit 1; }
env_file="$HOME/404_BE/.env.systemd"
[[ -f "$env_file" ]] || { echo "Environment file not found: $env_file" >&2; exit 1; }
set -a
# shellcheck disable=SC1090
source "$env_file"
set +a
: "${APP_JWT_SECRET:?APP_JWT_SECRET is required}"
database_url="${SPRING_DATASOURCE_URL#jdbc:}"
psql_cmd=(psql -X -v ON_ERROR_STOP=1 -q "$database_url" -U "$SPRING_DATASOURCE_USERNAME")
active_count="$(PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" "${psql_cmd[@]}" -Atc \
  "select count(*) from shopping_import_jobs where user_id::text like '40410000-0000-0000-0000-%' and status in ('PENDING','RUNNING')")"
[[ "$active_count" == "0" ]] || { echo "Refusing apply: $active_count previous test job(s) are active" >&2; exit 1; }

PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" "${psql_cmd[@]}" <<'SQL'
begin;
delete from shopping_import_jobs where user_id::text like '40410000-0000-0000-0000-%';
insert into users (id, status, onboarding_status, created_at, updated_at, withdrawn_at)
select ('40410000-0000-0000-0000-' || lpad(value::text, 12, '0'))::uuid,
       'ACTIVE', 'COMPLETED', now(), now(), null
from generate_series(1, 100) value
on conflict (id) do update
set status = 'ACTIVE', onboarding_status = 'COMPLETED', updated_at = now(), withdrawn_at = null;

insert into social_accounts (id, user_id, provider, provider_user_id, email, profile_image_url, created_at, updated_at)
select ('40410000-0000-0000-0001-' || lpad(value::text, 12, '0'))::uuid,
       ('40410000-0000-0000-0000-' || lpad(value::text, 12, '0'))::uuid,
       'KAKAO', 'qa-load-user-' || lpad(value::text, 3, '0'),
       'qa-load-user-' || lpad(value::text, 3, '0') || '@sagongsa.test', null, now(), now()
from generate_series(1, 100) value
on conflict on constraint uk_social_accounts_provider_user do update
set user_id = excluded.user_id, email = excluded.email, profile_image_url = null, updated_at = now();

insert into user_profiles (user_id, nickname, mascot_name, timezone, profile_image_url, notification_enabled, created_at, updated_at)
select ('40410000-0000-0000-0000-' || lpad(value::text, 12, '0'))::uuid,
       '부하테스트' || lpad(value::text, 3, '0'), '너구리', 'Asia/Seoul', null, true, now(), now()
from generate_series(1, 100) value
on conflict (user_id) do update
set nickname = excluded.nickname, mascot_name = excluded.mascot_name,
    timezone = excluded.timezone, profile_image_url = null,
    notification_enabled = true, updated_at = now();
commit;
SQL

python3 - <<'PY'
import base64
import hashlib
import hmac
import json
import os
import time
import uuid

def encode(value):
    raw = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()

now = int(time.time())
key = hashlib.sha256(os.environ["APP_JWT_SECRET"].encode()).digest()
issuer = os.environ.get("APP_AUTH_ISSUER", "404-backend")
for number in range(1, 101):
    user_id = f"40410000-0000-0000-0000-{number:012d}"
    provider_user_id = f"qa-load-user-{number:03d}"
    header = encode({"alg": "HS256"})
    payload = encode({
        "iss": issuer,
        "jti": str(uuid.uuid4()),
        "iat": now,
        "exp": now + 7200,
        "sub": user_id,
        "provider": "kakao",
        "providerUserId": provider_user_id,
        "name": f"부하테스트{number:03d}",
        "email": f"qa-load-user-{number:03d}@sagongsa.test",
        "authorities": ["ROLE_USER"],
        "token_type": "access",
        "userId": user_id,
    })
    signing_input = f"{header}.{payload}"
    signature = base64.urlsafe_b64encode(
        hmac.new(key, signing_input.encode(), hashlib.sha256).digest()
    ).rstrip(b"=").decode()
    print(f"{signing_input}.{signature}")
PY
echo "QA_TEST_USERS_CREATED=100" >&2
REMOTE

token_count="$(awk 'NF { count++; seen[$0] = 1 } END { print count + 0, length(seen) }' "$temp_token_file")"
if [[ "$token_count" != "100 100" ]]; then
  echo "Expected 100 unique tokens, received: $token_count" >&2
  exit 1
fi
chmod 600 "$temp_token_file"
mv "$temp_token_file" "$TOKEN_FILE"
trap - EXIT
echo "QA_TEST_USERS_CREATED=100"
echo "ACCESS_TOKEN_FILE=$TOKEN_FILE"
