# Clients Service

## 1. Назначение

`clients` отвечает за данные личного кабинета клиента в системе бронирования железнодорожных билетов.

Сервис решает две разные задачи:

- хранит локальную проекцию identity-данных пользователя из `sso-ident`;
- хранит собственные данные клиента: профиль, пассажиров и задел под будущие атрибуты.

### Source of truth

| Данные | Владелец |
|---|---|
| `user_id` | `sso-ident` |
| `email` | `sso-ident` |
| `username` | `sso-ident` |
| `first_name` | `sso-ident` |
| `last_name` | `sso-ident` |
| `phone_number` | `clients` |
| `extra_fields` | `clients` |
| пассажиры | `clients` |
| `client_attributes.attributes` | `clients` |

`clients` не предоставляет API для редактирования identity-полей.

---

## 2. REST API

Base path:

```text
/api/v1/clients-srv
```

Все endpoint'ы защищены Oathkeeper.

### 2.1 Получить профиль текущего клиента

```http
GET /api/v1/clients-srv/client/profile
X-User-ID: <UUID>
```

`X-User-ID` добавляется Oathkeeper автоматически. В обычном клиентском запросе его не следует задавать вручную.

Ответ `200 OK`:

```json
{
  "clientId": "8f6a48b8-f2c6-4f5d-9ec4-1a5f4b2d1f70",
  "userId": "2e59ac8b-5d3f-43b4-8f0c-4f83e6ab1db3",
  "email": "ivan@example.com",
  "firstName": "Иван",
  "lastName": "Иванов",
  "username": "ivanov",
  "phoneNumber": null,
  "extraFields": {},
  "updatedAt": "2026-09-13T19:20:10Z"
}
```

Если клиент ещё не создан, сервис возвращает `404`.

### 2.2 Изменить профиль клиента

```http
PUT /api/v1/clients-srv/client/profile
Content-Type: application/json
X-User-ID: <UUID>
```

Endpoint изменяет только поля, которыми владеет `clients`: `phoneNumber` и `extraFields`. Поля `email`, `username`, `firstName` и `lastName` изменяются только через `sso-ident`.

```json
{
  "phoneNumber": "+79991234567",
  "extraFields": {
    "preferredLanguage": "ru",
    "marketingOptIn": false
  }
}
```

`extraFields` заменяется целиком, а не объединяется с предыдущим JSON. PATCH endpoint намеренно не предоставляется. `phoneNumber: null` очищает номер телефона. Пустой `phoneNumber` также сохраняется как `NULL`.

Ответ `200 OK` — полный `ClientProfileResponse`.

---

### 2.3 Список пассажиров

```http
GET /api/v1/clients-srv/client/passengers
```

Ответ `200 OK` — массив `PassengerResponse`.

Список сортируется по:

1. `lastName`;
2. `firstName`;
3. `id`.

Пример:

```json
[
  {
    "id": "bce8ac4f-47c2-4f53-a45b-4a8f3bb6a4ce",
    "clientId": "8f6a48b8-f2c6-4f5d-9ec4-1a5f4b2d1f70",
    "firstName": "Иван",
    "middleName": "Иванович",
    "lastName": "Иванов",
    "documentTypeId": 1,
    "documentTypeCode": "PASSPORT_RU",
    "documentTypeName": "Паспорт гражданина РФ",
    "documentSeriesNumber": "1234",
    "documentNumber": "567890",
    "birthDate": "1995-06-17",
    "email": "ivan@example.com",
    "phoneNumber": "+79991234567"
  }
]
```

---

### 2.4 Получить пассажира

```http
GET /api/v1/clients-srv/client/passengers/{id}
```

Пассажир ищется одновременно по `id` и `client_id` текущего пользователя. Знание UUID чужого пассажира не даёт доступа к его данным.

`200 OK` — `PassengerResponse`.

---

### 2.5 Создать пассажира

```http
POST /api/v1/clients-srv/client/passengers
Content-Type: application/json
```

Request:

```json
{
  "firstName": "Иван",
  "middleName": "Иванович",
  "lastName": "Иванов",
  "documentTypeId": 1,
  "documentSeriesNumber": "1234",
  "documentNumber": "567890",
  "birthDate": "1995-06-17",
  "email": "ivan@example.com",
  "phoneNumber": "+79991234567"
}
```

`201 Created` — созданный `PassengerResponse`.

#### Валидация

- `firstName`: обязателен, максимум 255 символов;
- `middleName`: обязателен, максимум 255 символов; пустая строка разрешена;
- `lastName`: обязателен, максимум 255 символов;
- `documentTypeId`: обязателен;
- `documentSeriesNumber`: обязателен, максимум 64 символа;
- `documentNumber`: обязателен, максимум 128 символов;
- `birthDate`: обязателен и должен быть датой в прошлом;
- `email`: обязателен, корректный email, максимум 320 символов;
- `phoneNumber`: необязателен, максимум 64 символа.

При сохранении:

- `firstName`, `middleName`, `lastName`, document fields и телефон обрезаются по краям;
- email нормализуется в lowercase;
- пустой `phoneNumber` хранится как `NULL`.

Формат серии/номера документа намеренно не зашит в общую API-валидацию. Конкретные правила зависят от `documentType` и могут добавляться позже.

---

### 2.6 Обновить пассажира

```http
PUT /api/v1/clients-srv/client/passengers/{id}
Content-Type: application/json
```

Тело имеет ту же схему, что и создание пассажира. Обновление полное.

`200 OK` — обновлённый `PassengerResponse`.

---

### 2.7 Удалить пассажира

```http
DELETE /api/v1/clients-srv/client/passengers/{id}
```

При успешном удалении:

```http
204 No Content
```

Удаление возможно только для пассажира текущего клиента.

---

## 3. Ошибки

Сервис использует единый формат:

```json
{
  "code": "BAD_REQUEST",
  "message": "Invalid client data"
}
```

Основные случаи:

| HTTP | `code` | Когда |
|---:|---|---|
| 400 | `BAD_REQUEST` | некорректный UUID в `X-User-ID`, ошибки валидации или ограничения БД |
| 404 | `NOT_FOUND` | профиль не найден |
| 400 | `BAD_REQUEST` | `GET/PUT/DELETE` пассажира, если пассажир не принадлежит текущему клиенту или отсутствует |
| 401/403 | — | обычно формируются Oathkeeper, если нет сессии или доступа в Keto |

---

## 4. Модель данных

### `clients`

| Поле | Тип | Ограничения |
|---|---|---|
| `id` | UUID | PK |
| `user_id` | UUID | NOT NULL, UNIQUE |
| `email` | VARCHAR(320) | NOT NULL |
| `first_name` | VARCHAR(255) | nullable |
| `last_name` | VARCHAR(255) | nullable |
| `username` | VARCHAR(128) | nullable |
| `identity_version` | BIGINT | NOT NULL, default 0 |
| `updated_at` | TIMESTAMPTZ | NOT NULL |

### `client_profiles`

| Поле | Тип | Ограничения |
|---|---|---|
| `id` | UUID | PK |
| `client_id` | UUID | NOT NULL, UNIQUE, FK → `clients.id` |
| `phone_number` | VARCHAR(64) | nullable |
| `extra_fields` | JSONB | NOT NULL, default `{}` |
| `updated_at` | TIMESTAMPTZ | NOT NULL |

### `client_passengers`

| Поле | Тип | Ограничения |
|---|---|---|
| `id` | UUID | PK |
| `client_id` | UUID | NOT NULL, FK → `clients.id` |
| `first_name` | VARCHAR(255) | NOT NULL |
| `middle_name` | VARCHAR(255) | NOT NULL, default `''` |
| `last_name` | VARCHAR(255) | NOT NULL |
| `document_type_id` | INTEGER | NOT NULL, FK → `document_types.id` |
| `document_series_number` | VARCHAR(64) | NOT NULL |
| `document_number` | VARCHAR(128) | NOT NULL |
| `birth_date` | DATE | NOT NULL |
| `email` | VARCHAR(320) | NOT NULL |
| `phone_number` | VARCHAR(64) | nullable |
| `updated_at` | TIMESTAMPTZ | NOT NULL |

### `document_types`

Справочник документов. В текущей миграции присутствуют:

- `PASSPORT_RU`;
- `BIRTH_CERTIFICATE`;
- `FOREIGN_PASSPORT`;
- `FOREIGN_ID`.

### `client_attributes`

| Поле | Тип | Ограничения |
|---|---|---|
| `id` | UUID | PK |
| `client_id` | UUID | NOT NULL, UNIQUE, FK → `clients.id` |
| `attributes` | JSONB | NOT NULL, default `{}` |

API для `client_attributes` пока не реализован.

При регистрации клиента строка в `client_attributes` создаётся сразу.

---

## 5. Жизненный цикл клиента

`clients` не управляет lifecycle identity-пользователя.

Регистрация:

```text
sso-ident
    ↓ user.registration
Redpanda
  ├── sso.ident.user_registration.clients ──> clients consumer
  └── sso.ident.user_updates.clients ──────> clients consumer
    ↓
clients-service
    ├─ clients
    ├─ client_profiles
    └─ client_attributes
```

После регистрации клиентские пассажиры создаются отдельно через REST API.

Удаление/деактивация пользователя в `sso-ident` пока не вызывает lifecycle-операций в `clients`.

---

## 6. Синхронизация identity

Identity-поля в `clients` являются read-model.

При изменении профиля `sso-ident` выполняет в одной транзакции:

```text
UPDATE user identity fields
INSERT sso_ident_user_cdc
COMMIT
```

Строка CDC содержит полный snapshot:

- `user_id`;
- `event_version`;
- `email`;
- `username`;
- `first_name`;
- `last_name`;
- `updated_at`.

Debezium читает только `INSERT` из CDC-таблицы через PostgreSQL logical replication и публикует flattened-событие в топик `sso.ident.user_updates.clients` через Kafka Connect.

`clients` обновляет identity-данные только если входящая `event_version` больше текущей `identity_version`.

Это делает consumer устойчивым к повторной доставке и к приходу более старого события.
