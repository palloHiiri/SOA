#!/bin/sh
set -eu

KETO_READ_URL="http://keto:4466"
KETO_WRITE_URL="http://keto:4467"
MAX_ATTEMPTS=10
RETRY_DELAY=2

wait_for_keto() {
  attempt=1

  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    if curl -fsS --max-time 5 "$KETO_READ_URL/health/ready" >/dev/null; then
      return 0
    fi

    sleep "$RETRY_DELAY"
    attempt=$((attempt + 1))
  done

  echo "Keto did not become ready after $MAX_ATTEMPTS attempts" >&2
  return 1
}

put_service_access() {
  service="$1"
  role="$2"
  attempt=1

  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    status="$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' \
      -X PUT "$KETO_WRITE_URL/admin/relation-tuples" \
      -H 'Content-Type: application/json' \
      --data "{\"namespace\":\"Service\",\"object\":\"$service\",\"relation\":\"access\",\"subject_set\":{\"namespace\":\"Role\",\"object\":\"$role\",\"relation\":\"members\"}}" \
    )" || status="000"

    case "$status" in
      200|201|204|409)
        echo "Keto tuple exists: Service:$service#access <- Role:$role#members"
        return 0
        ;;
      *)
        echo "Keto tuple creation attempt $attempt/$MAX_ATTEMPTS failed for Service:$service/$role with HTTP $status" >&2
        sleep "$RETRY_DELAY"
        attempt=$((attempt + 1))
        ;;
    esac
  done

  echo "Keto bootstrap failed for Service:$service/$role" >&2
  return 1
}

wait_for_keto

put_service_access "sso-ident" "User"
put_service_access "clients-service" "User"
put_service_access "tickets-service" "User"
put_service_access "tickets-service" "Admin"
put_service_access "booking-service" "User"
put_service_access "inventory-service" "Admin"