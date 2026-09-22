#!/bin/sh
set -eu

until curl -fsS http://keto:4466/health/ready >/dev/null; do
  sleep 1
done

status=$(curl -sS -o /dev/null -w "%{http_code}" -X PUT http://keto:4467/admin/relation-tuples \
  -H 'Content-Type: application/json' \
  --data '{"namespace":"Service","object":"sso-ident","relation":"access","subject_set":{"namespace":"Role","object":"User","relation":"members"}}')

case "$status" in
  200|201|204|409) ;;
  *) echo "Keto bootstrap failed for sso-ident with HTTP $status" >&2; exit 1 ;;
esac

status=$(curl -sS -o /dev/null -w "%{http_code}" -X PUT http://keto:4467/admin/relation-tuples \
  -H 'Content-Type: application/json' \
  --data '{"namespace":"Service","object":"clients-service","relation":"access","subject_set":{"namespace":"Role","object":"User","relation":"members"}}')

case "$status" in
  200|201|204|409) ;;
  *) echo "Keto bootstrap failed for clients-service with HTTP $status" >&2; exit 1 ;;
esac

status=$(curl -sS -o /dev/null -w "%{http_code}" -X PUT http://keto:4467/admin/relation-tuples \
  -H 'Content-Type: application/json' \
  --data '{"namespace":"Service","object":"inventory-service","relation":"access","subject_set":{"namespace":"Role","object":"Admin","relation":"members"}}')

case "$status" in
  200|201|204|409) exit 0 ;;
  *) echo "Keto bootstrap failed for inventory-service with HTTP $status" >&2; exit 1 ;;
esac

put_role_access() {
  service="$1"
  role="$2"
  status=$(curl -sS -o /dev/null -w "%{http_code}" -X PUT http://keto:4467/admin/relation-tuples \
    -H 'Content-Type: application/json' \
    --data "{\"namespace\":\"Service\",\"object\":\"${service}\",\"relation\":\"access\",\"subject_set\":{\"namespace\":\"Role\",\"object\":\"${role}\",\"relation\":\"members\"}}")
  case "$status" in
    200|201|204|409) ;;
    *) echo "Keto bootstrap failed for ${service}/${role} with HTTP $status" >&2; exit 1 ;;
  esac
}

put_role_access "tickets-service" "User"
put_role_access "tickets-service" "Admin"
