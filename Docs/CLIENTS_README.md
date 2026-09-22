# Clients Service — documentation

`clients` — реактивный Spring Boot сервис личного кабинета пользователя в билетной платформе.

Сервис хранит профиль клиента, дополнительные профильные данные и список пассажиров, на которых клиент может оформлять билеты. Identity-данные (`user_id`, `email`, `username`, `first_name`, `last_name`) принадлежат `sso-ident` и в `clients` являются локальной проекцией.

## Contents

- `CLIENTS.md` — назначение сервиса, API, модель данных, безопасность, обработка событий и правила владения данными.
- `CLIENTS_INTEGRATION.md` — интеграция с Oathkeeper, Keto, nginx, Redpanda Kafka, Debezium Kafka Connect и Liquibase.
- `CLIENTS_EVENTS.md` — форматы Kafka-событий и CDC-поток.
- `clients_openapi.yaml` — OpenAPI 3.1 спецификация REST API.
- `examples/` — готовые `curl`/JSON примеры.

## Local entry point

Публичный вход в API проходит через Oathkeeper:

```text
http://localhost:4455/api/v1/clients-srv/...
```

Не рекомендуется обращаться к контейнеру `clients` напрямую в интеграционной схеме.

## Security model

Oathkeeper:

1. проверяет пользовательскую сессию через `sso-ident`;
2. проверяет отношение `Service:clients-service#access` в Keto;
3. передаёт идентификатор пользователя в downstream через `X-User-ID`.

`clients-service` не принимает `clientId` пользователя из URL для определения владельца. Владелец всегда вычисляется из `X-User-ID`.

## Quick start

Из корня проекта:

```bash
docker compose up -d --build
```

Проверка сервиса:

```bash
curl http://localhost:4455/api/v1/clients-srv/client/profile
```

Запрос требует действующую SSO-сессию и будет возвращать `401/403`, если Oathkeeper не смог аутентифицировать/авторизовать пользователя.
