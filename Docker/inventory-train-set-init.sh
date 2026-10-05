#!/bin/sh
set -eu

API_BASE_URL="${API_BASE_URL:-http://oathkeeper:4455}"
LOGIN_URL="$API_BASE_URL/api/v1/sso-ident/auth/login"
IMPORT_URL="$API_BASE_URL/api/v1/inventory/train-sets/import"
LOOKUP_BASE_URL="$API_BASE_URL/api/v1/inventory/train-sets/by-code"
LIFECYCLE_BASE_URL="$API_BASE_URL/api/v1/inventory/train-sets"

SYSTEM_ADMIN_USERNAME="${SYSTEM_ADMIN_USERNAME:-sys}"
SYSTEM_ADMIN_PASSWORD="${SYSTEM_ADMIN_PASSWORD:-system123}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-5}"
RETRY_DELAY_SECONDS="${RETRY_DELAY_SECONDS:-3}"
CURL_CONNECT_TIMEOUT_SECONDS="${CURL_CONNECT_TIMEOUT_SECONDS:-10}"
CURL_MAX_TIME_SECONDS="${CURL_MAX_TIME_SECONDS:-180}"

STATE_DIR="${STATE_DIR:-/state}"
STATE_FILE="$STATE_DIR/train-set-ids"
COOKIE_JAR="$STATE_DIR/session.cookies"
RESPONSE_FILE="$STATE_DIR/response.json"

mkdir -p "$STATE_DIR"
rm -f "$COOKIE_JAR" "$RESPONSE_FILE"

fail() {
  echo "inventory train-set init: $*" >&2
  if [ -f "$RESPONSE_FILE" ]; then
    cat "$RESPONSE_FILE" >&2 || true
  fi
  exit 1
}

request() {
  method="$1"
  url="$2"
  shift 2

  curl -sS \
    --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" \
    --max-time "$CURL_MAX_TIME_SECONDS" \
    -o "$RESPONSE_FILE" \
    -w '%{http_code}' \
    -X "$method" "$url" "$@" \
    || true
}

login() {
  attempt=1
  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    status=$(request POST "$LOGIN_URL" \
      -H 'Content-Type: application/json' \
      -c "$COOKIE_JAR" \
      --data "{\"login\":\"$SYSTEM_ADMIN_USERNAME\",\"password\":\"$SYSTEM_ADMIN_PASSWORD\"}")

    if [ "$status" = "200" ]; then
      if grep -Eq '[[:space:]]SESSION[[:space:]]' "$COOKIE_JAR" 2>/dev/null; then
        echo "inventory train-set init: system admin login succeeded" >&2
        return 0
      fi

      echo "inventory train-set init: login returned 200 but SESSION cookie was not stored; retrying" >&2
    elif [ "$status" = "401" ] || [ "$status" = "403" ]; then
      echo "inventory train-set init: system admin login failed with HTTP $status" >&2
      cat "$RESPONSE_FILE" >&2
      return 1
    else
      echo "inventory train-set init: login attempt $attempt/$MAX_ATTEMPTS failed with HTTP $status; retrying in ${RETRY_DELAY_SECONDS}s" >&2
    fi

    sleep "$RETRY_DELAY_SECONDS"
    attempt=$((attempt + 1))
  done

  return 1
}

extract_id() {
  sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$RESPONSE_FILE" | head -n 1
}

find_existing_id() {
  code="$1"
  build_number="$2"

  status=$(request GET "$LOOKUP_BASE_URL/$code?buildNumber=$build_number" \
    -H 'Accept: application/json' \
    -b "$COOKIE_JAR")

  case "$status" in
    200)
      id="$(extract_id)"
      [ -n "$id" ] || fail "lookup returned 200 without a train-set id for $code/$build_number"
      echo "$id"
      return 0
      ;;
    404)
      return 1
      ;;
    401|403)
      echo "inventory train-set init: lookup failed with HTTP $status for $code/$build_number" >&2
      cat "$RESPONSE_FILE" >&2
      return 2
      ;;
    *)
      echo "inventory train-set init: lookup failed with HTTP $status for $code/$build_number" >&2
      cat "$RESPONSE_FILE" >&2
      return 2
      ;;
  esac
}

import_train_set() {
  name="$1"
  code="$2"
  build_number="$3"
  technical_name="$4"
  description="$5"
  carriage_a_number="$6"
  carriage_a_inventory="$7"
  carriage_a_serial="$8"
  carriage_b_number="$9"
  carriage_b_inventory="${10}"
  carriage_b_serial="${11}"
  carriage_type_code="${12}"
  carriage_type_name="${13}"
  scheme_code="${14}"
  scheme_name="${15}"
  storage_key="${16}"
  seat_a="${17}"
  seat_b="${18}"

  body=$(cat <<JSON
{
  "buildNumber": $build_number,
  "carriages": [
    {
      "carriageNumber": "$carriage_a_number",
      "carriageType": {
        "code": "$carriage_type_code",
        "description": "$carriage_type_name",
        "name": "$carriage_type_name",
        "ref": "$carriage_type_code-ref",
        "scheme": {
          "code": "$scheme_code",
          "isActive": true,
          "name": "$scheme_name",
          "seatPositions": [
            {"rotation": 0, "seatNumber": "$seat_a", "x": 100.0, "y": 100.0},
            {"rotation": 0, "seatNumber": "$seat_b", "x": 120.0, "y": 100.0}
          ],
          "storageKey": "$storage_key",
          "version": 1
        }
      },
      "inventoryNumber": "$carriage_a_inventory",
      "isActive": true,
      "position": 1,
      "serialNumber": "$carriage_a_serial"
    },
    {
      "carriageNumber": "$carriage_b_number",
      "carriageType": {
        "code": "$carriage_type_code",
        "description": "$carriage_type_name",
        "name": "$carriage_type_name",
        "ref": "$carriage_type_code-ref"
      },
      "inventoryNumber": "$carriage_b_inventory",
      "isActive": true,
      "position": 2,
      "serialNumber": "$carriage_b_serial"
    }
  ],
  "code": "$code",
  "description": "$description",
  "name": "$name",
  "technicalName": "$technical_name"
}
JSON
)

  attempt=1
  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    status=$(request POST "$IMPORT_URL" \
      -H 'Content-Type: application/json' \
      -b "$COOKIE_JAR" \
      --data "$body")

    case "$status" in
      201)
        id="$(extract_id)"
        [ -n "$id" ] || fail "import returned 201 without a train-set id"
        echo "$id"
        return 0
        ;;
      401|403)
        echo "inventory train-set init: import failed with HTTP $status" >&2
        cat "$RESPONSE_FILE" >&2
        return 1
        ;;
      409)
        # The import can commit successfully and then time out before the client
        # receives the response. Resolve an existing row by its natural import key
        # instead of sending the same payload again.
        existing_id="$(find_existing_id "$code" "$build_number")" || {
          rc=$?
          [ "$rc" -eq 1 ] && {
            echo "inventory train-set init: import conflict for $code but the existing train set could not be found" >&2
            return 2
          }
          return "$rc"
        }
        echo "inventory train-set init: $code already exists as train set $existing_id; treating import as successful" >&2
        echo "$existing_id"
        return 0
        ;;
      000|408|429|500|502|503|504)
        echo "inventory train-set init: import attempt $attempt/$MAX_ATTEMPTS failed with HTTP $status; retrying in ${RETRY_DELAY_SECONDS}s" >&2
        ;;
      *)
        echo "inventory train-set init: import failed with HTTP $status" >&2
        cat "$RESPONSE_FILE" >&2
        return 1
        ;;
    esac

    sleep "$RETRY_DELAY_SECONDS"
    attempt=$((attempt + 1))
  done

  return 1
}

change_lifecycle() {
  id="$1"
  attempt=1
  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    status=$(request POST "$LIFECYCLE_BASE_URL/$id/lifecycle" \
      -H 'Content-Type: application/json' \
      -b "$COOKIE_JAR" \
      --data '{"status":"ACTIVE"}')

    case "$status" in
      200)
        echo "inventory train-set init: train set $id is ACTIVE" >&2
        return 0
        ;;
      400)
        echo "inventory train-set init: lifecycle transition for train set $id was rejected" >&2
        cat "$RESPONSE_FILE" >&2
        return 1
        ;;
      401|403)
        echo "inventory train-set init: lifecycle failed with HTTP $status" >&2
        cat "$RESPONSE_FILE" >&2
        return 1
        ;;
      000|408|429|500|502|503|504)
        echo "inventory train-set init: lifecycle attempt $attempt/$MAX_ATTEMPTS for train set $id failed with HTTP $status; retrying in ${RETRY_DELAY_SECONDS}s" >&2
        ;;
      *)
        echo "inventory train-set init: lifecycle failed with HTTP $status for train set $id" >&2
        cat "$RESPONSE_FILE" >&2
        return 1
        ;;
    esac

    sleep "$RETRY_DELAY_SECONDS"
    attempt=$((attempt + 1))
  done

  return 1
}

# The state file makes the one-shot init safe to retry after an interrupted run.
if [ -s "$STATE_FILE" ]; then
  set -- $(cat "$STATE_FILE")
  FIRST_ID="${1:-}"
  SECOND_ID="${2:-}"
  [ -n "$FIRST_ID" ] && [ -n "$SECOND_ID" ] || fail "invalid saved train-set state"
  echo "inventory train-set init: using saved train-set ids $FIRST_ID and $SECOND_ID"
else
  login || fail "unable to log in as system admin"

  FIRST_ID="$(import_train_set \
    'Test Train Set 1' 'T-001' 1 'TEST_TRAIN_SET_001' \
    'Train set imported through API number 1' \
    '01' 'INV-INIT-001' 'SER-INIT-001' \
    '02' 'INV-INIT-002' 'SER-INIT-002' \
    'NSTD-INIT-001' 'Non Standard carriage 001' \
    'STD-INIT-001' 'Standard scheme 001' 'schemes/std-init-001.json' '1A' '1B')"

  SECOND_ID="$(import_train_set \
    'Test Train Set 2' 'T-002' 2 'TEST_TRAIN_SET_002' \
    'Train set imported through API number 2' \
    '03' 'INV-INIT-003' 'SER-INIT-003' \
    '04' 'INV-INIT-004' 'SER-INIT-004' \
    'NSTD-INIT-002' 'Non Standard carriage 002' \
    'STD-INIT-002' 'Standard scheme 002' 'schemes/std-init-002.json' '2A' '2B')" || {
      rc=$?
      [ "$rc" -eq 2 ] && fail "train-set import reported a uniqueness conflict; clear the inventory data or state volume before re-running init"
      fail "unable to import train sets"
    }

  printf '%s %s\n' "$FIRST_ID" "$SECOND_ID" > "$STATE_FILE"
fi

login || fail "unable to log in as system admin"

change_lifecycle "$FIRST_ID" || fail "unable to activate train set $FIRST_ID"
change_lifecycle "$SECOND_ID" || fail "unable to activate train set $SECOND_ID"

echo "inventory train-set init: completed successfully"
