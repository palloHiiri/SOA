# Infrastructure

## Overview

The project runs the common infrastructure and backend services from one Docker Compose file. RabbitMQ has been removed; application events use Redpanda's Kafka API, and PostgreSQL CDC is handled by Debezium on Kafka Connect.

```text
                           +------------------+
                           |      Client      |
                           | Browser / Postman|
                           +--------+---------+
                                    |
                                    v
                           +------------------+
                           |    Oathkeeper    |
                           |      :4455       |
                           +---+----------+---+
                               |          |
                    authz ----+          +---- HTTP
                               |                 |
                               v                 v
                        +-------------+    +-------------+
                        |    Keto     |    |  internal   |
                        |  :4466/:4467|    |    nginx    |
                        +------+------+    +------+------+
                               |                  |
                               |             +----+----+
                               |             |         |
                               v             v         v
                        +-------------+  sso-ident   clients
                        | PostgreSQL  |
                        |    :5432    |
                        +------+------+
                               |
                               | logical replication
                               v
                      +--------------------+
                      | Debezium Kafka      |
                      | Connect :8083       |
                      +---------+----------+
                                |
                                v
                       +------------------+
                       |    Redpanda      |
                       |   Kafka :19092   |
                       +--------+---------+
                                |
                       +--------v---------+
                       | Redpanda Console |
                       |     :8088        |
                       +------------------+
```

## Docker services

| Service | Purpose | Port(s) | Persistent storage |
|---|---|---:|---|
| `postgres` | PostgreSQL for application databases + logical replication | `5433 -> 5432` | `postgres_data` |
| `sso-redis` | Sessions / reactive cache | `6379` | `redis_data` |
| `redpanda` | Kafka-compatible event broker | `19092`, `18081`, `19644` | `redpanda_data` |
| `redpanda-topic-init` | Explicit topic provisioning | — | none |
| `redpanda-console` | Kafka/Redpanda UI | `8088 -> 8080` | none |
| `debezium-connect` | Kafka Connect worker running the Debezium PostgreSQL connector | `8083` | internal Kafka topics |
| `debezium-connector-init` | Registers the Debezium connector through Kafka Connect REST | — | none |
| `keto-migrate` | Keto schema migration | — | PostgreSQL `keto` |
| `keto` | Authorization relation API | `4466`, `4467` | PostgreSQL `keto` |
| `keto-init` | Initial relation bootstrap | — | none |
| `liquibase` | Application DB migrations | — | PostgreSQL application DBs |
| `sso-ident` | Reactive identity/session API, GraalVM Native Image | `8080` | PostgreSQL `sso_ident` + Redis |
| `clients` | Client profile / passenger API + Kafka consumers, GraalVM Native Image | internal `8080` | PostgreSQL `clients` |
| `inventory` | MVC train-set import/lifecycle API + CDC source, GraalVM Native Image | internal `8080` | PostgreSQL `inventory` |
| `booking` | Spring MVC booking/sales API packaged as a WAR and deployed to Tomcat 11 | `8075 -> 8080` | PostgreSQL `booking` |
| `nginx` | Internal backend reverse proxy | internal `80` | none |
| `oathkeeper` | Reverse proxy + authentication/authorization | `4455`, `4456` | none |

## Redpanda

The development broker is based on Redpanda `v26.2.2`; the UI is Redpanda Console `v3.11.0`. The official Redpanda Docker examples use the same image family/version combination.

Broker-side automatic topic creation is explicitly disabled with `redpanda.auto_create_topics_enabled=false`. This is important because producing to an unknown topic would otherwise create it implicitly.

Topic creation is intentionally centralized in `Docker/redpanda/init-topics.sh`. Redpanda's `rpk topic create` supports idempotent creation with `--if-not-exists`.

### Application topics

```text
sso.ident.user_registration.clients
sso.ident.verification_codes.sms
sso.ident.verification_codes.email
sso.ident.user_updates.clients
```

These correspond to the four RabbitMQ queues that actually held messages in the previous topology. The former `/clients` queues that were only fed by shovel are no longer separate transport objects: `clients` consumes the same Redpanda topics directly.

### Kafka Connect / Debezium topics

The explicit bootstrap script also creates:

```text
_connect_configs
_connect_offsets
_connect_status
schema-history.sso-ident
```

The first three are Kafka Connect's distributed worker state. `schema-history.sso-ident` is Debezium's internal schema history topic. Debezium documents Kafka-backed schema history with `KafkaSchemaHistory` and requires the schema history topic to be available to the connector.

## Debezium Kafka Connect

The project now runs Debezium `3.5.2.Final` as Kafka Connect rather than Debezium Server. Debezium 3.5.2 was built against Kafka Connect 4.1.2 and its PostgreSQL connector streams database changes into Kafka topics.

The connector configuration is stored at `Docker/debezium/connectors/sso-ident-cdc.json` and registered through Kafka Connect's REST API by `Docker/debezium/register-connector.sh`.

`debezium-connector-init` retries Kafka Connect availability, connector creation and connector-status checks up to `MAX_ATTEMPTS` times. The Compose defaults are 10 attempts with a 3-second delay between attempts. If a connector was created successfully but its status endpoint is temporarily unavailable, the next attempt detects the existing connector and retries only the status check.

Important connector settings:

```text
connector.class = io.debezium.connector.postgresql.PostgresConnector
plugin.name = pgoutput
publication.name = sso_ident_user_cdc_publication
slot.name = sso_ident_user_cdc_slot
table.include.list = public.sso_ident_user_cdc
snapshot.mode = no_data
```

The connector first produces its normal topic (`sso-ident.public.sso_ident_user_cdc`), then the `RegexRouter` SMT routes it to the application topic `sso.ident.user_updates.clients`. `ExtractNewRecordState` keeps the consumer-facing payload flattened to the CDC row.

Automatic source-topic creation is disabled twice: at the Redpanda broker and at Kafka Connect via `CONNECT_TOPIC_CREATION_ENABLE=false`. Kafka Connect has its own topic-creation control independent of broker-side auto creation.

## PostgreSQL logical replication

The PostgreSQL container starts with:

```text
wal_level=logical
max_wal_senders=4
max_replication_slots=4
```

Liquibase creates the `debezium` replication role, the CDC table, the publication and the required grants. The connector owns the replication slot.

`004-user-cdc.sql` deliberately publishes only `INSERT` from `sso_ident_user_cdc`; application profile updates insert a complete snapshot row into the CDC table in the same transaction as the user change.

Tickets does not create relational copies of the Inventory tables. Its CDC projection stores the latest Inventory snapshot in JSONB using the same aggregate shape as `inventory.train_set_cdc`; the snapshot row is keyed by `train_set_id`, so that key is also the direct access path for the projection queries.

The Inventory import accepts a CDC-compatible aggregate shape. Each carriage contains a `carriageType` with a local `ref`; IDs and lifecycle status are rejected on import. A carriage type may provide either one active `scheme` or a local `schemes` array for multiple versions.

## Clients event flow

Registration and verification are application-produced Kafka records:

```text
sso-ident
  ├──> sso.ident.user_registration.clients
  └──> sso.ident.verification_codes.(sms|email)

clients
  <── sso.ident.user_registration.clients
  <── sso.ident.user_updates.clients

PostgreSQL sso_ident_user_cdc
  └──> Debezium Kafka Connect
          └──> sso.ident.user_updates.clients
```

There is no RabbitMQ virtual host, exchange, queue, binding or shovel in the new stack.

## Startup order

The important dependency chain is:

```text
postgres
  ├── liquibase ───────────────┐
  └── logical replication       │
                                v
redpanda -> redpanda-topic-init -> debezium-connect
                                      └── debezium-connector-init

postgres + liquibase + keto-init + topic-init -> sso-ident
postgres + liquibase + topic-init -------------> clients
postgres + liquibase + keto-init -------------> inventory
postgres + liquibase --------------------------> booking

sso-ident + clients + inventory -> nginx -> oathkeeper
redpanda + debezium-connect -> redpanda-console
```

## Redpanda Console

Redpanda Console is exposed on `http://localhost:8088`. It connects to the Redpanda broker and to the Kafka Connect REST API, so topics and the Debezium connector can be inspected from one UI. Redpanda documents `kafkaConnect.enabled` plus a list of Kafka Connect cluster URLs for this integration.

## Internal reverse-proxy

The Compose stack contains an internal `nginx` service between Oathkeeper and application backends. Oathkeeper access-rule `upstream.url` points to `http://nginx:80`. Nginx routes `/api/v1/sso-ident/...`, `/api/v1/clients-srv/...`, `/api/v1/inventory/...` and `/api/v1/tickets/...` to their respective services and returns 404 for unrelated paths.

## Booking service

`booking` is built as `booking.war` and deployed as the `/booking` web application in Tomcat 11. Compose publishes Tomcat on `http://localhost:8075`. The service is started after the PostgreSQL health check and successful Liquibase migration. Its existing application-level Clients Service, Tickets Service and database addresses are preserved.

The API is documented in `Docs/booking_openapi.yaml`. In addition to selling a ticket, Booking exposes a passenger ticket list and a boolean check for whether a ticket already has a sale record.

## Initial system administrator bootstrap

The Compose stack includes a one-shot `sso-ident-system-admin-init` container. It waits for the database migrations, Keto initialization, all application services and Oathkeeper, then performs the first administrator bootstrap in two steps:

1. Register the system user through Oathkeeper at `POST /api/v1/sso-ident/auth/register`.
2. Add the returned user UUID to `Role:Admin#members` through Keto's write API.

Bootstrap credentials are configured with `SYSTEM_ADMIN_EMAIL`, `SYSTEM_ADMIN_USERNAME` and `SYSTEM_ADMIN_PASSWORD`; the Compose file provides development defaults. The generated UUID is stored in the `sso_admin_state` volume to make subsequent `docker compose up` runs idempotent.



## Tickets service

`tickets` is an educational Jakarta REST/JAX-RS application packaged as a WAR and deployed to WildFly. It owns the temporary `venues`, `tickets` and `price_histories` collections and consumes the Inventory train-set snapshot topic `inventory.train_set_snapshots`. The service keeps the Inventory snapshot in local PostgreSQL JSONB and exposes read-only train-set, carriage and seat projections without creating relational copies of the Inventory model.

The service is available internally as `tickets:8080`, is routed by nginx under `/api/v1/tickets`, and is exposed by Compose on host port `8074`.
