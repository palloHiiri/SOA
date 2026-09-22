#!/bin/sh
set -eu

STATE_FILE=/state/admin-user-id
REGISTER_URL=http://oathkeeper:4455/api/v1/sso-ident/auth/register
KETO_URL=http://keto:4467/admin/relation-tuples

mkdir -p /state

if [ -s "$STATE_FILE" ]; then
  USER_ID=$(cat "$STATE_FILE")
else
  payload=$(cat <<JSON
{"email":"${SYSTEM_ADMIN_EMAIL}","username":"${SYSTEM_ADMIN_USERNAME}","password":"${SYSTEM_ADMIN_PASSWORD}","firstName":"System","lastName":"Administrator"}
JSON
)

  response_file=$(mktemp)
  trap 'rm -f "$response_file"' EXIT

  USER_ID=""
  i=0
  while [ "$i" -lt 120 ]; do
    i=$((i + 1))
    status=$(curl -sS -o "$response_file" -w '%{http_code}' \
      -X POST "$REGISTER_URL" \
      -H 'Content-Type: application/json' \
      --data "$payload" || true)

    case "$status" in
      201)
        USER_ID=$(sed -n 's/.*"userId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$response_file")
        register_status=$(sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$response_file")
        if [ -z "$USER_ID" ]; then
          echo "System admin bootstrap failed: register response has no userId" >&2
          cat "$response_file" >&2
          exit 1
        fi
        if [ "$register_status" != "ACTIVE" ]; then
          echo "System admin bootstrap failed: registered user is not ACTIVE" >&2
          cat "$response_file" >&2
          exit 1
        fi
        printf '%s\n' "$USER_ID" > "$STATE_FILE"
        break
        ;;
      400)
        echo "System admin registration failed with HTTP 400" >&2
        cat "$response_file" >&2
        exit 1
        ;;
      404|502|503|504)
        sleep 2
        ;;
      *)
        echo "System admin registration failed with HTTP $status" >&2
        cat "$response_file" >&2
        exit 1
        ;;
    esac
  done

  if [ -z "$USER_ID" ]; then
    echo "System admin bootstrap timed out waiting for Oathkeeper/sso-ident" >&2
    exit 1
  fi
fi

status=$(curl -sS -o /dev/null -w '%{http_code}' \
  -X PUT "$KETO_URL" \
  -H 'Content-Type: application/json' \
  --data "{\"namespace\":\"Role\",\"object\":\"Admin\",\"relation\":\"members\",\"subject_id\":\"$USER_ID\"}")

case "$status" in
  200|201|204|409)
    echo "System admin bootstrap completed for user $USER_ID"
    ;;
  *)
    echo "System admin bootstrap failed to add Admin role, HTTP $status" >&2
    exit 1
    ;;
esac
