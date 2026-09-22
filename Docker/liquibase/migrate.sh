#!/bin/sh
set -eu

run_backend() {
  backend="$1"
  database="$2"
  changelog="$3"

  echo "==> Migrating ${backend} -> ${database}"
  liquibase \
    --url="jdbc:postgresql://postgres:5432/${database}" \
    --username="${POSTGRES_USER:-postgres}" \
    --password="${POSTGRES_PASSWORD:-postgres}" \
    --search-path=/liquibase/changelog \
    --changelog-file="${changelog}" \
  update
}

# Keep one entry here per backend database.
# Additional backends can add their own Database/<backend>/changelog-master.xml
# and one run_backend line without changing the compose service.
run_backend "sso-ident" "sso_ident" "sso-ident/changelog-master.xml"
run_backend "clients" "clients" "clients/changelog-master.xml"
run_backend "inventory" "inventory" "inventory/changelog-master.xml"

run_backend "tickets" "tickets" "tickets/changelog-master.xml"
run_backend "booking" "booking" "booking/changelog-master.xml"
