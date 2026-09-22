# Clients integration

## 1. Overview

`clients` is integrated with `sso-ident` through Redpanda Kafka topics. RabbitMQ, its exchanges/queues, and shovel plugins are no longer part of the deployment.

```text
                         +-------------------+
                         |      sso-ident    |
                         +---------+---------+
                                   |
                     application Kafka events
                                   |
                                   v
                         +-------------------+
                         |     Redpanda      |
                         |      :19092       |
                         +----+---------+----+
                              |         |
                   registration       CDC updates
                              |         |
                              v         ^
                         +-------------------+
                         |      clients      |
                         |   consumer group  |
                         +-------------------+
                                   ^
                                   |
                         +---------+---------+
                         | Debezium Kafka     |
                         | Connect :8083      |
                         +---------+---------+
                                   ^
                         PostgreSQL logical WAL
```

## 2. Topics

The four application topics are:

```text
sso.ident.user_registration.clients
sso.ident.verification_codes.sms
sso.ident.verification_codes.email
sso.ident.user_updates.clients
```

These are created explicitly by `Docker/redpanda/init-topics.sh`.

Kafka Connect state and Debezium schema history are also provisioned explicitly:

```text
_connect_configs
_connect_offsets
_connect_status
schema-history.sso-ident
```

## 3. Registration path

Registration is produced directly by `sso-ident` to:

```text
sso.ident.user_registration.clients
```

`clients` consumes this topic with the Kafka consumer group `clients`. The old `sso.ident.user_registration.clients -> shovel -> clients.user_registration` path no longer exists.

The registration payload is a JSON event:

```json
{
  "event": "SSO_IDENT_USER_REGISTERED",
  "userId": "...",
  "username": "...",
  "email": "...",
  "firstName": "...",
  "lastName": "..."
}
```

Processing is idempotent because `clients.user_id` is unique.

## 4. Verification path

`sso-ident` publishes verification-code events to either:

```text
sso.ident.verification_codes.sms
sso.ident.verification_codes.email
```

The topic is selected from the event `channel` field.

## 5. Identity update / CDC path

Profile changes write a complete event row to `sso_ident_user_cdc` in the same database transaction as the identity update. Liquibase creates the CDC table, publication and replication role.

Debezium's PostgreSQL connector runs inside Kafka Connect and captures only `INSERT` operations from `public.sso_ident_user_cdc`. It uses PostgreSQL `pgoutput`, the existing publication and a dedicated replication slot.

The connector emits to its normal Debezium topic namespace and then applies:

```text
ExtractNewRecordState
        ↓
RegexRouter
        ↓
sso.ident.user_updates.clients
```

The `clients` service receives the flattened row.

## 6. Consumer semantics

The `clients` service uses Kafka consumer group `clients` and `auto-offset-reset=earliest`. Automatic commits are disabled; Spring Kafka commits the record offset after the listener method returns successfully. A listener failure prevents the offset from being committed, allowing the record to be retried.

The service keeps `identity_version` and applies an update only when the incoming `event_version` is greater than the stored value.

## 7. Debezium / Kafka Connect configuration

Kafka Connect is started from the official Debezium Connect image:

```text
quay.io/debezium/connect:3.5.2.Final
```

Debezium 3.5.2.Final is a stable 3.5 release and is built against Kafka Connect 4.1.2.

The connector is registered by `Docker/debezium/register-connector.sh` from:

```text
Docker/debezium/connectors/sso-ident-cdc.json
```

The worker has `CONNECT_TOPIC_CREATION_ENABLE=false`, while the Redpanda broker has `auto_create_topics_enabled=false`. Kafka Connect has its own topic creation switch, independent of broker-side topic auto-creation.

## 8. Startup order

```text
postgres -> liquibase
redpanda -> redpanda-topic-init
                              
redpanda-topic-init + liquibase -> debezium-connect -> debezium-connector-init

redpanda-topic-init + liquibase -> clients
redpanda-topic-init + liquibase + keto-init -> sso-ident
```

## 9. Useful endpoints

```text
Redpanda UI:   http://localhost:8088
Kafka broker:  localhost:19092
Kafka Connect: http://localhost:8083
Schema Reg.:   http://localhost:18081
Admin API:     http://localhost:19644
```
