# sso-ident

## Purpose

`sso-ident` is a reactive identity/session service. It is responsible for user registration, password authentication, password recovery, profile updates, MFA verification flows, sessions, integration events and application-side user state.

Authorization is deliberately split out: Ory Oathkeeper is the edge enforcement point and Ory Keto stores authorization relationships.

## Application stack

- Spring Boot 3.5.6
- Spring WebFlux
- Spring Data R2DBC + PostgreSQL
- Reactive Redis
- Spring Kafka / Redpanda
- Liquibase-managed PostgreSQL schema
- Spring Security Crypto / Argon2 password hashing
- Ory Keto integration through its HTTP API (write and read APIs)

## Runtime topology

```text
Client
  |
  v
Oathkeeper :4455
  |
  +--> nginx :80
          |
          +--> sso-ident :8080
                  |
                  +--> PostgreSQL :5432 / sso_ident
                  +--> Redis :6379
                  +--> Redpanda Kafka :19092
                  +--> Keto :4467
```

The client normally enters through Oathkeeper. Direct access to Spring on port `8080` is still useful for local development and debugging.

## PostgreSQL usage

The service connects to the dedicated `sso_ident` database. In Docker:

```text
r2dbc:postgresql://postgres:5432/sso_ident
```

For local JVM execution:

```text
r2dbc:postgresql://localhost:5433/sso_ident
```

Default local development credentials are `admin/admin`.

## Redis

Redis stores the active browser session and is also used as a cache for password-reset challenges:

```text
sso:session:<token>
sso:password-reset:code:<uuid>
sso:password-reset:attempts:<uuid>
```

Password-reset entries use the same 15-minute lifetime as the `PASSWORD_RESET` verification-code type. The database remains the source of truth, so a Redis miss can fall back to `verification_codes`.

## Redpanda / Kafka

The application publishes event families directly to Redpanda topics defined in `Services/sso-ident/src/main/resources/application.yml`.:

```text
sso.ident.user_registration
sso.ident.verification_codes
```

Verification-code events use routing keys such as `email` and `sms`. The password-reset flow uses the existing `email` route and therefore does not require a new queue or exchange.

A password-reset event contains the generated UUID as the `code` field. The UUID is stored only as a SHA-256 hash in PostgreSQL. The verification row uses the same UUID as its database `id`, which lets the confirmation endpoint validate the code without introducing another schema object.

## Registration flow

At a high level:

```text
POST /auth/register
        |
        v
create user as PENDING
        |
        +----> publish registration event to Redpanda
        |
        +----> add user to default Keto group
        |
        v
both integrations succeed?
     /       \
   yes        no
   |           |
 ACTIVE      PENDING
               |
               +--> integration failure recorded in history
```

The implementation therefore does not treat successful PostgreSQL insertion alone as successful registration.

## Login and MFA flow

```text
POST /auth/login
       |
       v
validate username/email + password
       |
       +---- email 2FA disabled --> create session -> Set-Cookie
       |
       +---- email 2FA enabled  --> MFA challenge
                                      |
                                      +--> POST /auth/mfa/email/request
                                      |       sends email code through Redpanda
                                      |
                                      +--> POST /auth/mfa/email/verify
                                              verifies code -> create session
```

The two existing MFA endpoints are intentionally kept because they perform two different operations:

- `POST /auth/mfa/email/request` generates/sends a new MFA code for an existing login challenge.
- `POST /auth/mfa/email/verify` consumes that code and completes authentication.

They are not duplicate verification-code APIs; they implement the request and consume stages of the MFA flow.

## Password recovery flow

The public recovery endpoint accepts a form field, not JSON:

```text
POST /api/v1/sso-ident/auth/password/reset
Content-Type: application/x-www-form-urlencoded

email=user@example.com
```

The response is always:

```json
{"status":"OK"}
```

This is deliberate: a caller cannot use the endpoint to distinguish an existing email from an unknown email.

When the email belongs to an active user:

```text
request reset
    |
    +--> generate UUID
    +--> SHA-256(UUID) -> verification_codes.code_hash
    +--> publish EMAIL/PASSWORD_RESET event to Redpanda
    +--> cache user/challenge + attempts in Redis for 15 minutes
```

The notification backend receives the UUID from the `code` field and can deliver it by email.

Confirmation is JSON:

```text
POST /api/v1/sso-ident/auth/password/reset/confirm
{
  "code": "<uuid>",
  "newPassword": "new-password"
}
```

The service checks the cache first, falls back to PostgreSQL on a cache miss, validates the hash/expiry/attempt count, atomically marks the verification row as `USED` together with the password update, and removes the Redis entries on success.

The reset code allows at most five attempts and expires after fifteen minutes.

## Change your own password

An authenticated session can change its own password:

```text
POST /api/v1/sso-ident/me/password
```

Body:

```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password"
}
```

The current password is checked against the stored Argon2 hash. An incorrect current password returns `401 Unauthorized`.

## Update your own profile

An authenticated session can update any supplied subset of:

```text
POST /api/v1/sso-ident/me/profile
GET  /api/v1/sso-ident/me/groups
POST /api/v1/sso-ident/admin/users/{userId}/groups
GET  /api/v1/sso-ident/admin/users/{userId}/groups
DELETE /api/v1/sso-ident/admin/users/{userId}/groups/{group}
GET  /api/v1/sso-ident/admin/groups/{group}/members
GET  /api/v1/sso-ident/admin/groups
```

Example:

```json
{
  "username": "new-login",
  "email": "new@example.com",
  "firstName": "John",
  "lastName": "Smith"
}
```

Fields omitted from the request remain unchanged. Email and username are normalized to lowercase and checked for conflicts with another user. First and last names are stored after trimming surrounding whitespace.

The endpoint returns the refreshed `MeResponse`.


## Roles and group membership

Roles are managed as a small catalog in the `roles` table so that a role can exist even when it has no members. Each role has a serial ID, a unique name, an optional description and an `is_default` flag. PostgreSQL enforces that at most one role is default. The initial catalog contains:

```text
User  (default)
Admin
```

The default role is resolved from the database during registration and assigned to every newly created user in Keto. The application does not hard-code `User` as the registration target anymore.

Keto remains the source of truth for actual membership. Direct memberships have the form:

```text
Role:<role>#members@User:<user-id>
```

The supported role APIs are:

```text
GET    /api/v1/sso-ident/me/groups
POST   /api/v1/sso-ident/admin/users/{userId}/groups
GET    /api/v1/sso-ident/admin/users/{userId}/groups
DELETE /api/v1/sso-ident/admin/users/{userId}/groups/{group}
GET    /api/v1/sso-ident/admin/groups/{group}/members
GET    /api/v1/sso-ident/admin/groups
```

`GET /me/groups` is available to every authenticated user. All `/admin/*` role endpoints require direct membership in `Role:Admin#members`. Adding a role verifies that the target user exists, that the requested role exists in the `roles` catalog, and that the user is not already a member before creating the Keto membership. Attempting to add an already assigned role returns `409 Conflict`. Unknown/stale Keto role tuples are not exposed through the application role API.

The role-removal endpoint deliberately does not allow removal of the default role. This preserves the base `User` membership assigned during registration.

## Initial system administrator

The Compose environment contains a one-shot bootstrap container named `sso-ident-system-admin-init`. After PostgreSQL migrations, Keto initialization, `sso-ident`, `clients`, `inventory` and Oathkeeper are available, the container calls the public registration endpoint through Oathkeeper:

```text
POST http://oathkeeper:4455/api/v1/sso-ident/auth/register
```

It then creates the direct Keto membership:

```text
Role:Admin#members@User:<registered-user-id>
```

The bootstrap credentials are configurable through Compose environment variables:

```yaml
environment:
  SYSTEM_ADMIN_EMAIL: ${SYSTEM_ADMIN_EMAIL:-sys@system.local}
  SYSTEM_ADMIN_USERNAME: ${SYSTEM_ADMIN_USERNAME:-sys}
  SYSTEM_ADMIN_PASSWORD: ${SYSTEM_ADMIN_PASSWORD:-system123}
```

The bootstrap container keeps the generated user UUID in the `sso_admin_state` named volume, so a normal Compose restart does not try to register the same administrator a second time. Removing that volume intentionally resets the bootstrap state; when resetting the database, remove the Compose volumes together as well.

## Oathkeeper + Keto authorization

For `/api/v1/sso-ident/me`:

1. Oathkeeper authenticates the request using `cookie_session`.
2. The cookie session is checked by the upstream `sso-ident` service.
3. Oathkeeper extracts the authenticated subject.
4. Oathkeeper asks Keto's read API whether the subject can `access` `Service:sso-ident`.
5. The header mutator passes the authenticated user ID downstream.
6. The request is proxied to `sso-ident`.

The current default Keto relationship is:

```text
Service:sso-ident#access@Role:User#members
```

Users are attached to the role with relationships equivalent to:

```text
Role:User#members@User:<user-id>
```

Thus the default policy is group-based rather than maintaining one explicit `Service` permission relation per user.

## Public API

The OpenAPI definition is stored at `Docs/sso_ident_openapi.yaml`.

Important endpoints include:

```text
POST /api/v1/sso-ident/auth/register
POST /api/v1/sso-ident/auth/login
POST /api/v1/sso-ident/auth/password/reset
POST /api/v1/sso-ident/auth/password/reset/confirm
POST /api/v1/sso-ident/auth/logout
POST /api/v1/sso-ident/auth/mfa/email/request
POST /api/v1/sso-ident/auth/mfa/email/verify
GET  /api/v1/sso-ident/me
POST /api/v1/sso-ident/me/password
POST /api/v1/sso-ident/me/profile
GET  /api/v1/sso-ident/me/groups
POST /api/v1/sso-ident/admin/users/{userId}/groups
GET  /api/v1/sso-ident/admin/users/{userId}/groups
DELETE /api/v1/sso-ident/admin/users/{userId}/groups/{group}
GET  /api/v1/sso-ident/admin/groups/{group}/members
GET  /api/v1/sso-ident/admin/groups
```

## Configuration

The important environment variables are:

```text
SPRING_R2DBC_URL
SPRING_R2DBC_USERNAME
SPRING_R2DBC_PASSWORD
SPRING_REDIS_HOST
SPRING_REDIS_PORT
SPRING_KAFKA_BOOTSTRAP_SERVERS
SSO_KAFKA_VERIFICATION_EMAIL_TOPIC
SSO_KETO_BASE_URL
SSO_KETO_READ_BASE_URL
SSO_SESSION_COOKIE_NAME
SSO_SESSION_TTL
SERVER_PORT
```

Inside Compose these are set to Docker service names. Outside Compose, the defaults in `application.yml` point to the published local ports.

## Database migrations

Spring Boot does not run Liquibase migrations during application startup. Migrations are executed centrally so the same workflow can cover multiple backend databases.

Run:

```bash
docker compose run --rm liquibase
```

The current sso-ident changelog remains in:

```text
Database/sso-ident/changelog-master.xml
Database/sso-ident/v1/001-init-schema.sql
Database/sso-ident/v1/002-seed-dictionaries.sql
Database/sso-ident/v1/003-audit-function.sql
Database/sso-ident/v1/004-user-cdc.sql
Database/sso-ident/v1/005-roles.sql
```

No database migration is required for the password-reset, self-password-change, or profile-update endpoints because the existing verification, credential, attribute and audit structures already support them.

## Development

Build and start the whole environment:

```bash
docker compose up -d --build
```

Useful entry points:

```text
sso-ident:   http://localhost:8080
Oathkeeper:  http://localhost:4455
Keto read:   http://localhost:4466
Keto write:  http://localhost:4467
Redpanda UI: http://localhost:8088
```

For a clean first-time PostgreSQL initialization, remove the development volume before recreating the stack:

```bash
docker compose down -v
```

Do not use `down -v` when you need to preserve development data.

## Request path through the proxy

External SSO Ident traffic enters Oathkeeper on port 4455. The Oathkeeper rule sends the request to the internal nginx service. Nginx forwards `/api/v1/sso-ident/...` to the `sso-ident` container on port 8080.


## Runtime

`sso-ident` remains a WebFlux/R2DBC reactive service, but the application is packaged as a GraalVM Native Image. Database migrations are externalized to the repository-level Liquibase container; Liquibase is intentionally not a runtime dependency of `sso-ident`.
