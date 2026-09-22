#!/bin/sh
set -eu

BROKERS="${REDPANDA_BROKERS:-redpanda:9092}"
ADMIN="${REDPANDA_ADMIN:-redpanda:9644}"

echo "Waiting for Redpanda Admin API at ${ADMIN}..."

until rpk cluster health \
    -X admin.hosts="${ADMIN}" \
    --exit-when-healthy >/dev/null 2>&1
do
  sleep 2
done

echo "Redpanda is healthy"

echo "Creating Kafka Connect internal topics..."

rpk topic create _connect_configs \
  -X brokers="${BROKERS}" \
  --partitions 1 \
  --replicas 1 \
  -c cleanup.policy=compact \
  --if-not-exists

rpk topic create _connect_offsets \
  -X brokers="${BROKERS}" \
  --partitions 3 \
  --replicas 1 \
  -c cleanup.policy=compact \
  --if-not-exists

rpk topic create _connect_status \
  -X brokers="${BROKERS}" \
  --partitions 1 \
  --replicas 1 \
  -c cleanup.policy=compact \
  --if-not-exists

echo "Creating Debezium schema history topic..."

rpk topic create schema-history.sso-ident \
  -X brokers="${BROKERS}" \
  --partitions 1 \
  --replicas 1 \
  -c cleanup.policy=compact \
  --if-not-exists

rpk topic create schema-history.inventory \
  -X brokers="${BROKERS}" \
  --partitions 1 \
  --replicas 1 \
  -c cleanup.policy=compact \
  --if-not-exists


echo "Creating application topics..."

rpk topic create sso.ident.user_registration.clients \
  -X brokers="${BROKERS}" \
  --partitions 3 \
  --replicas 1 \
  --if-not-exists

rpk topic create sso.ident.verification_codes.sms \
  -X brokers="${BROKERS}" \
  --partitions 3 \
  --replicas 1 \
  --if-not-exists

rpk topic create sso.ident.verification_codes.email \
  -X brokers="${BROKERS}" \
  --partitions 3 \
  --replicas 1 \
  --if-not-exists

rpk topic create sso.ident.user_updates.clients \
  -X brokers="${BROKERS}" \
  --partitions 3 \
  --replicas 1 \
  --if-not-exists

rpk topic create inventory.train_set_snapshots \
  -X brokers="${BROKERS}" \
  --partitions 3 \
  --replicas 1 \
  --if-not-exists

echo
echo "Topics:"
rpk topic list -X brokers="${BROKERS}"