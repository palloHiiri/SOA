#!/bin/sh
set -eu

MAX_ATTEMPTS="${MAX_ATTEMPTS:-10}"
RETRY_DELAY_SECONDS="${RETRY_DELAY_SECONDS:-3}"
CONNECT_TIMEOUT_SECONDS="${CONNECT_TIMEOUT_SECONDS:-3}"

run_backend() {
  backend="$1"
  database="$2"
  changelog="$3"
  attempt=1

  echo "==> Migrating ${backend} -> ${database}"

  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    if liquibase \
      --url="jdbc:postgresql://postgres:5432/${database}?sslmode=disable&connectTimeout=${CONNECT_TIMEOUT_SECONDS}" \
      --username="${POSTGRES_USER:-postgres}" \
      --password="${POSTGRES_PASSWORD:-postgres}" \
      --search-path=/liquibase/changelog \
      --changelog-file="${changelog}" \
      update
    then
      echo "<== Migration ${backend} completed"
      return 0
    fi

    if [ "$attempt" -ge "$MAX_ATTEMPTS" ]; then
      break
    fi

    echo "Migration ${backend} attempt ${attempt}/${MAX_ATTEMPTS} failed; retrying in ${RETRY_DELAY_SECONDS}s..." >&2
    sleep "$RETRY_DELAY_SECONDS"
    attempt=$((attempt + 1))
  done

  echo "Migration ${backend} failed after ${MAX_ATTEMPTS} attempts" >&2
  return 1
}

run_backend "sso-ident" "sso_ident" "sso-ident/changelog-master.xml"
run_backend "clients" "clients" "clients/changelog-master.xml"
run_backend "inventory" "inventory" "inventory/changelog-master.xml"

run_backend "tickets" "tickets" "tickets/changelog-master.xml"
run_backend "booking" "booking" "booking/changelog-master.xml"
