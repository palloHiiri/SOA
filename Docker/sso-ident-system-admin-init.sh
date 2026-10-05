#!/bin/sh
set -eu

STATE_FILE=/state/admin-user-id
REGISTER_URL=http://oathkeeper:4455/api/v1/sso-ident/auth/register
LOGIN_URL=http://oathkeeper:4455/api/v1/sso-ident/auth/login
KETO_URL=http://keto:4467/admin/relation-tuples

MAX_ATTEMPTS="${MAX_ATTEMPTS:-10}"
RETRY_DELAY_SECONDS="${RETRY_DELAY_SECONDS:-3}"
CURL_CONNECT_TIMEOUT_SECONDS="${CURL_CONNECT_TIMEOUT_SECONDS:-3}"
# Registration contains Argon2 + DB + Kafka + Keto and therefore gets a larger total timeout.
REGISTER_MAX_TIME_SECONDS="${REGISTER_MAX_TIME_SECONDS:-30}"
CURL_MAX_TIME_SECONDS="${CURL_MAX_TIME_SECONDS:-3}"

mkdir -p /state

payload=$(cat <<JSON
{"email":"${SYSTEM_ADMIN_EMAIL}","username":"${SYSTEM_ADMIN_USERNAME}","password":"${SYSTEM_ADMIN_PASSWORD}","firstName":"System","lastName":"Administrator"}
JSON
)

extract_user_id() {
  response_file="$1"
  sed -n 's/.*"userId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$response_file" | head -n 1
}

extract_status() {
  response_file="$1"
  sed -n 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$response_file" | head -n 1
}

is_duplicate_registration() {
  response_file="$1"
  grep -qi 'Login is already in use' "$response_file"
}

try_login() {
  response_file="$1"

  curl -sS \
    --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" \
    --max-time "$CURL_MAX_TIME_SECONDS" \
    -o "$response_file" \
    -w '%{http_code}' \
    -X POST "$LOGIN_URL" \
    -H 'Content-Type: application/json' \
    --data "{\"login\":\"${SYSTEM_ADMIN_USERNAME}\",\"password\":\"${SYSTEM_ADMIN_PASSWORD}\"}" \
    || true
}

recover_active_user_via_login() {
  response_file="$1"
  status="$(try_login "$response_file")"

  if [ "$status" = "200" ]; then
    USER_ID="$(extract_user_id "$response_file")"
    if [ -n "$USER_ID" ]; then
      printf '%s\n' "$USER_ID" > "$STATE_FILE"
      echo "Recovered ACTIVE system admin user $USER_ID via login"
      return 0
    fi
  fi

  return 1
}

if [ -s "$STATE_FILE" ]; then
  USER_ID="$(cat "$STATE_FILE")"
  echo "Using previously stored system admin user ID $USER_ID"
else
  register_response="$(mktemp)"
  login_response="$(mktemp)"
  trap 'rm -f "$register_response" "$login_response"' EXIT

  USER_ID=""
  attempt=1

  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    status=$(curl -sS \
      --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" \
      --max-time "$REGISTER_MAX_TIME_SECONDS" \
      -o "$register_response" \
      -w '%{http_code}' \
      -X POST "$REGISTER_URL" \
      -H 'Content-Type: application/json' \
      --data "$payload" \
      || true)

    case "$status" in
      201)
        USER_ID="$(extract_user_id "$register_response")"
        registration_status="$(extract_status "$register_response")"

        if [ -z "$USER_ID" ]; then
          echo "System admin registration returned HTTP 201 without userId; retrying..." >&2
        elif [ "$registration_status" = "ACTIVE" ]; then
          printf '%s\n' "$USER_ID" > "$STATE_FILE"
          echo "System admin bootstrap registration completed for user $USER_ID"
          break
        else
          # A PENDING response is a successful DB registration. Repeating the same
          # request is now safe: sso-ident verifies the password and resumes integration.
          echo "System admin registration returned PENDING for user $USER_ID; retrying integration in ${RETRY_DELAY_SECONDS}s..." >&2
        fi
        ;;

      400)
        if is_duplicate_registration "$register_response"; then
          echo "Registration reports an existing login; the next registration attempt will resume the PENDING user." >&2

          # If the first request actually finished and already activated the user,
          # registration correctly returns a duplicate. Recover the UUID through login.
          if recover_active_user_via_login "$login_response"; then
            break
          fi
        else
          echo "System admin registration failed with non-retryable HTTP 400" >&2
          cat "$register_response" >&2
          exit 1
        fi
        ;;

      000|408|429|500|502|503|504)
        echo "System admin registration attempt $attempt/$MAX_ATTEMPTS failed with HTTP $status; retrying in ${RETRY_DELAY_SECONDS}s..." >&2
        ;;

      *)
        echo "System admin registration failed with HTTP $status" >&2
        cat "$register_response" >&2
        exit 1
        ;;
    esac

    if [ "$attempt" -ge "$MAX_ATTEMPTS" ]; then
      break
    fi

    sleep "$RETRY_DELAY_SECONDS"
    attempt=$((attempt + 1))
  done

  if [ -z "$USER_ID" ]; then
    echo "System admin registration/recovery failed after $MAX_ATTEMPTS attempts" >&2
    cat "$register_response" >&2
    exit 1
  fi
fi

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

attempt=1
while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
  status=$(curl -sS \
    --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" \
    --max-time "$CURL_MAX_TIME_SECONDS" \
    -o "$response_file" \
    -w '%{http_code}' \
    -X PUT "$KETO_URL" \
    -H 'Content-Type: application/json' \
    --data "{\"namespace\":\"Role\",\"object\":\"Admin\",\"relation\":\"members\",\"subject_id\":\"$USER_ID\"}" \
    || true)

  case "$status" in
    200|201|204|409)
      echo "System admin bootstrap completed for user $USER_ID"
      exit 0
      ;;

    000|408|429|500|502|503|504)
      echo "Admin role assignment attempt $attempt/$MAX_ATTEMPTS failed with HTTP $status; retrying in ${RETRY_DELAY_SECONDS}s..." >&2
      ;;

    *)
      echo "System admin bootstrap failed to add Admin role, HTTP $status" >&2
      cat "$response_file" >&2
      exit 1
      ;;
  esac

  if [ "$attempt" -ge "$MAX_ATTEMPTS" ]; then
    break
  fi

  sleep "$RETRY_DELAY_SECONDS"
  attempt=$((attempt + 1))
done

echo "System admin bootstrap failed to add Admin role after $MAX_ATTEMPTS attempts" >&2
cat "$response_file" >&2
exit 1
