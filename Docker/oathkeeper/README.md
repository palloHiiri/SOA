# Oathkeeper dev setup

The proxy listens on `http://localhost:4455`.

The rule protects `GET /api/v1/sso-ident/me` with:

1. `cookie_session` -> calls the `sso-ident` Compose service at `http://sso-ident:8080/api/v1/sso-ident/me`.
2. `remote_json` -> asks Keto whether the authenticated UUID may `access` `Service:sso-ident`.
3. `header` mutator -> injects `X-User-ID` with the authenticated subject.
4. The request is proxied to the local Spring application.


Oathkeeper forwards requests to the internal nginx service. Nginx is responsible for routing `/api/v1/sso-ident/...` to the `sso-ident` backend. Its Docker-DNS/variable `proxy_pass` setup handles container IP changes after backend recreation without restarting nginx.

## sso-ident role administration

Role administration is protected by the `Role:Admin#members` relationship. The access rules for `/api/v1/sso-ident/admin/*` use Oathkeeper's `remote_json` authorizer with a per-rule Keto payload checking:

```json
{
  "namespace": "Role",
  "object": "Admin",
  "relation": "members",
  "subject_id": "<authenticated-user-id>"
}
```

The ordinary `/me/groups` endpoint uses the normal `Service:sso-ident#access` authorization inherited from the `User` role.
