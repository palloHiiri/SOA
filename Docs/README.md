# SOA Labs

This repository contains the current SOA lab infrastructure and services. The main integrated stack is a small identity/authorization platform built around **sso-ident + Ory Oathkeeper + Ory Keto**, with PostgreSQL, Redis, Redpanda and Debezium Kafka Connect as infrastructure.

## Current state

- `sso-ident` is a Spring Boot/WebFlux reactive identity and session service, built as a GraalVM Native Image.
- Ory Keto provides relationship-based authorization.
- Ory Oathkeeper is the reverse proxy and authentication/authorization enforcement point.
- PostgreSQL is shared as one container but separated into dedicated databases.
- Redis stores application session data.
- Redpanda is the Kafka-compatible event backbone.
- Debezium 3.5.2.Final runs as a Kafka Connect cluster and captures the SSO CDC table from PostgreSQL.
- Kafka topic creation is disabled at the broker and in Kafka Connect; topics are provisioned by `Docker/redpanda/init-topics.sh`.
- `Services/sso-ident` publishes registration and verification events directly to Redpanda.
- `Services/clients` consumes the registration and identity-update topics directly; the old RabbitMQ shovel topology is gone. It is also built as a GraalVM Native Image.
- `inventory` is the master service/database for train-set and rolling-stock reference data. Its current MVC API imports complete train sets and changes lifecycle; it publishes complete train-set snapshots through Debezium to Redpanda and is built as a GraalVM Native Image.
- `tickets` is the educational JAX-RS/WildFly ticket service, packaged as a WAR and consuming Inventory CDC snapshots from Redpanda.

## Quick start

```bash
docker compose up -d --build
```

For application database migrations only:

```bash
docker compose run --rm liquibase
```

Main local endpoints:

```text
sso-ident:       http://localhost:8071
Oathkeeper:      http://localhost:4455
Keto read:       http://localhost:4466
Keto write:      http://localhost:4467
Redpanda UI:     http://localhost:8088
Kafka:           localhost:19092
Kafka Connect:   http://localhost:8083
Schema Registry: http://localhost:18081
Redpanda Admin:  http://localhost:19644
Inventory API:   http://localhost:4455/api/v1/inventory
Tickets API:     http://localhost:4455/api/v1/tickets
PostgreSQL:      localhost:5433
Redis:           localhost:6379
```

Default development credentials:

```text
PostgreSQL: postgres / postgres
```

## Kafka topics

Four application topics replace the four RMQ queues that actually carried data in the previous version:

```text
sso.ident.user_registration.clients
sso.ident.verification_codes.sms
sso.ident.verification_codes.email
sso.ident.user_updates.clients
inventory.train_set_snapshots
```

Kafka Connect also uses its internal topics and Debezium uses a dedicated schema-history topic:

```text
_connect_configs
_connect_offsets
_connect_status
schema-history.sso-ident
schema-history.inventory
```

All of these are created explicitly by `Docker/redpanda/init-topics.sh`. Redpanda's broker-side automatic topic creation and Kafka Connect source-topic creation are both disabled.

## Repository layout

```text
Database/                 Liquibase database definitions
Docker/redpanda/          Redpanda topic bootstrap scripts
Docker/debezium/          Kafka Connect and Debezium connector config
Services/sso-ident/       Spring Boot identity service
Services/clients/         Client profile / passenger service
Docs/                     Detailed infrastructure and service documentation
docker-compose.yml        Integrated development environment
```

## Documentation

Detailed infrastructure documentation: `Docs/INFRASTRUCTURE.md`

Detailed `sso-ident` documentation: `Docs/SSO_IDENT.md`

Detailed clients integration/event documentation: `Docs/CLIENTS_INTEGRATION.md` and `Docs/CLIENTS_EVENTS.md`

Inventory service/database/CDC documentation: `Docs/INVENTORY.md`

Tickets service/database/REST documentation: `Docs/TICKETS.md`

Tickets OpenAPI specification: `Docs/tickets_openapi.yaml`

OpenAPI specifications: `Docs/sso_ident_openapi.yaml`, `Docs/clients_openapi.yaml`, `Docs/inventory_openapi.yaml`

## Internal reverse-proxy

Oathkeeper sends protected/public SSO Ident requests to the internal `nginx` service. Nginx routes `/api/v1/sso-ident/...` to `sso-ident:8080`. It uses Docker DNS (`127.0.0.11`) together with variable-based `proxy_pass`, so a recreated `sso-ident` container can receive a new IP without restarting nginx. The nginx service is internal-only and is not published to the host.
