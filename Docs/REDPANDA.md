# Redpanda / Kafka development guide

## Start

```bash
docker compose up -d --build
```

Check the important services:

```bash
docker compose ps redpanda redpanda-topic-init debezium-connect debezium-connector-init sso-ident clients redpanda-console
```

Redpanda UI:

```text
http://localhost:8088
```

Kafka Connect REST:

```text
http://localhost:8083
```

## Topics

Application topics:

```text
sso.ident.user_registration.clients
sso.ident.verification_codes.sms
sso.ident.verification_codes.email
sso.ident.user_updates.clients
inventory.train_set_snapshots
```

Internal topics:

```text
_connect_configs
_connect_offsets
_connect_status
schema-history.sso-ident
schema-history.inventory
```

The application topics are the direct Kafka equivalents of the four RMQ queues that actually carried messages. The two old `/clients` queues were only shovel destinations and therefore do not become separate topics.

Topic creation is explicit and idempotent:

```bash
docker compose run --rm redpanda-topic-init
```

Do not create application topics through application startup: broker-side and Kafka Connect topic auto-creation are disabled.

## Inspect topics

```bash
docker exec -it redpanda rpk topic list

docker exec -it redpanda rpk topic describe sso.ident.user_registration.clients
docker exec -it redpanda rpk topic describe sso.ident.user_updates.clients
inventory.train_set_snapshots
```

## Consume events

Registration:

```bash
docker exec -it redpanda rpk topic consume sso.ident.user_registration.clients --offset oldest
```

Identity updates:

```bash
docker exec -it redpanda rpk topic consume sso.ident.user_updates.clients --offset oldest
```

Verification codes:

```bash
docker exec -it redpanda rpk topic consume sso.ident.verification_codes.email --offset oldest
```

## Kafka Connect / Debezium

List connectors:

```bash
curl http://localhost:8083/connectors
```

Check the connector:

```bash
curl http://localhost:8083/connectors/sso-ident-cdc/status
```

Show the effective connector configuration:

```bash
curl http://localhost:8083/connectors/sso-ident-cdc
```

The connector captures only `public.sso_ident_user_cdc`, uses the existing PostgreSQL publication/slot, does not take a data snapshot, unwraps the Debezium envelope, and routes the resulting row to `sso.ident.user_updates.clients`.

## Test the CDC path

1. Start the stack.
2. Start a consumer for `sso.ident.user_updates.clients`.
3. Change the user's profile through `sso-ident`.
4. Observe the CDC event in Redpanda.
5. `clients` consumes the same event and updates its projection when `event_version` is newer.

## Test registration

1. Start a consumer for `sso.ident.user_registration.clients`.
2. Register a new user through the SSO API.
3. Observe the registration event in Redpanda.
4. Check that the `clients` projection appears without any RabbitMQ shovel.

## Test topic auto-creation is disabled

This should fail because the topic does not exist:

```bash
docker exec -it redpanda rpk topic produce this-topic-must-not-exist
```

Create the topic explicitly instead:

```bash
docker exec -it redpanda rpk topic create this-topic-must-not-exist --partitions 1 --replicas 1
```

Delete it after the test:

```bash
docker exec -it redpanda rpk topic delete this-topic-must-not-exist
```

## Inventory CDC

`inventory-service` publishes complete train-set snapshots to `inventory.train_set_snapshots`. The topic is consumed independently by `schedule-service` and `tickets-service`, using separate consumer groups. See `Docs/INVENTORY.md` for the database and snapshot contract.
