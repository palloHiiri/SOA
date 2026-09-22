#!/bin/sh
set -eu

CONNECT_URL="${CONNECT_URL:-http://debezium-connect:8083}"
CONNECTOR_CONFIG_DIR="${CONNECTOR_CONFIG_DIR:-/connectors}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-10}"
RETRY_DELAY_SECONDS="${RETRY_DELAY_SECONDS:-3}"

retry() {
  attempt=1
  while true; do
    if "$@"; then
      return 0
    fi

    if [ "$attempt" -ge "$MAX_ATTEMPTS" ]; then
      return 1
    fi

    echo "Attempt ${attempt}/${MAX_ATTEMPTS} failed. Retrying in ${RETRY_DELAY_SECONDS}s..."
    sleep "$RETRY_DELAY_SECONDS"
    attempt=$((attempt + 1))
  done
}

wait_for_connect() {
  curl -fsS "${CONNECT_URL}/connectors" >/dev/null
}

register_connector() {
  config="$1"
  name="$2"

  if curl -fsS "${CONNECT_URL}/connectors/${name}" >/dev/null 2>&1; then
    echo "Connector ${name} already exists"
  else
    echo "Registering connector ${name}"
    curl -fsS -X POST \
      -H 'Content-Type: application/json' \
      --data-binary "@${config}" \
      "${CONNECT_URL}/connectors" >/dev/null
  fi

  curl -fsS "${CONNECT_URL}/connectors/${name}/status" >/dev/null
}

echo "Waiting for Kafka Connect at ${CONNECT_URL}"
retry wait_for_connect

for config in "${CONNECTOR_CONFIG_DIR}"/*.json; do
  [ -f "$config" ] || continue

  name="$(sed -n 's/.*"name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$config" | head -n 1)"

  if [ -z "$name" ]; then
    echo "Cannot determine connector name from ${config}" >&2
    exit 1
  fi

  retry register_connector "$config" "$name"
  echo "Connector ${name} is registered and has a readable status"
done
