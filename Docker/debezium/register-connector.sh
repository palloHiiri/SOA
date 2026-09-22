#!/bin/sh
set -eu

CONNECT_URL="${CONNECT_URL:-http://debezium-connect:8083}"
CONNECTOR_CONFIG_DIR="${CONNECTOR_CONFIG_DIR:-/connectors}"

echo "Waiting for Kafka Connect at ${CONNECT_URL}..."
until curl -fsS "${CONNECT_URL}/connectors" >/dev/null; do
  sleep 2
done

for config in "${CONNECTOR_CONFIG_DIR}"/*.json; do
  [ -f "${config}" ] || continue

  name="$(sed -n 's/.*"name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "${config}" | head -n1)"

  if curl -fsS "${CONNECT_URL}/connectors/${name}" >/dev/null 2>&1; then
    echo "Connector ${name} already exists; leaving the running connector unchanged."
  else
    echo "Registering connector ${name}."
    curl -fsS -X POST \
      -H 'Content-Type: application/json' \
      --data-binary "@${config}" \
      "${CONNECT_URL}/connectors" >/dev/null
  fi

  curl -fsS "${CONNECT_URL}/connectors/${name}/status"
  printf '\nConnector %s is configured.\n' "${name}"
done
