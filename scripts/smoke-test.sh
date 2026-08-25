#!/usr/bin/env bash
# End-to-end check against a running server. Proves the whole auth flow works
# before you ever open the app.
#
#   ./scripts/smoke-test.sh http://localhost:8080

set -euo pipefail

BASE="${1:-http://localhost:8080}"
USER="smoke_$RANDOM$RANDOM"
PASS="smoke-password-123"

say() { printf '\n\033[36m==> %s\033[0m\n' "$1"; }
fail() { printf '\033[31mFAILED: %s\033[0m\n' "$1" >&2; exit 1; }

command -v jq >/dev/null || fail "jq is required"

say "health"
curl -fsS "$BASE/healthz" | jq -e '.status == "ok"' >/dev/null || fail "healthz"

say "readiness (database)"
curl -fsS "$BASE/readyz" | jq -e '.status == "ready"' >/dev/null || fail "readyz"

say "register $USER"
REG=$(curl -fsS -X POST "$BASE/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"display_name\":\"Smoke Test\",\"password\":\"$PASS\",\"device_name\":\"ci\",\"platform\":\"cli\"}")
ACCESS=$(echo "$REG" | jq -r '.access_token')
REFRESH=$(echo "$REG" | jq -r '.refresh_token')
RECOVERY=$(echo "$REG" | jq -r '.recovery_code')
[[ "$ACCESS" != "null" && -n "$ACCESS" ]] || fail "no access token"
[[ "$RECOVERY" != "null" && -n "$RECOVERY" ]] || fail "no recovery code"
echo "    recovery code: $RECOVERY"

say "authenticated /v1/me"
curl -fsS "$BASE/v1/me" -H "Authorization: Bearer $ACCESS" \
  | jq -e --arg u "$USER" '.username == $u' >/dev/null || fail "/v1/me"

say "reject a garbage token"
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/v1/me" -H "Authorization: Bearer not-a-token")
[[ "$CODE" == "401" ]] || fail "expected 401 for a bad token, got $CODE"

say "duplicate username is rejected"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"display_name\":\"dup\",\"password\":\"$PASS\",\"device_name\":\"ci\",\"platform\":\"cli\"}")
[[ "$CODE" == "409" ]] || fail "expected 409 for a duplicate username, got $CODE"

say "refresh token rotation"
NEW=$(curl -fsS -X POST "$BASE/v1/auth/refresh" \
  -H 'Content-Type: application/json' -d "{\"refresh_token\":\"$REFRESH\"}")
NEW_REFRESH=$(echo "$NEW" | jq -r '.refresh_token')
[[ "$NEW_REFRESH" != "$REFRESH" ]] || fail "refresh token was not rotated"

say "the old refresh token is now dead"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/v1/auth/refresh" \
  -H 'Content-Type: application/json' -d "{\"refresh_token\":\"$REFRESH\"}")
[[ "$CODE" == "401" ]] || fail "a spent refresh token must be rejected, got $CODE"

say "login with the password"
curl -fsS -X POST "$BASE/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"$PASS\",\"device_name\":\"ci\",\"platform\":\"cli\"}" \
  | jq -e '.access_token != null' >/dev/null || fail "login"

say "wrong password is rejected"
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"wrong-password\",\"device_name\":\"ci\",\"platform\":\"cli\"}")
[[ "$CODE" == "401" ]] || fail "expected 401 for a wrong password, got $CODE"

say "password recovery with the recovery code"
curl -fsS -X POST "$BASE/v1/auth/recover" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"recovery_code\":\"$RECOVERY\",\"new_password\":\"brand-new-password\"}" \
  | jq -e '.status == "password_changed"' >/dev/null || fail "recover"

say "login with the new password"
curl -fsS -X POST "$BASE/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"password\":\"brand-new-password\",\"device_name\":\"ci\",\"platform\":\"cli\"}" \
  | jq -e '.access_token != null' >/dev/null || fail "login after recovery"

printf '\n\033[32mAll smoke tests passed.\033[0m\n'
