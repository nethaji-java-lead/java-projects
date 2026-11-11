# Secured Item Management REST API

Java 17 + Spring Boot 3.4 + Gradle + PostgreSQL + Keycloak.

## Access rules

| Operation | USER | ADMIN |
|---|---:|---:|
| `POST /api/items` | Yes | Yes |
| `GET /api/items` (all items) | No (403) | Yes |
| `GET /api/items/{id}` for own item | Yes | Yes |
| `GET /api/items/{id}` for another user's item | No (403) | Yes |

The item's `ownerId` is taken from the verified JWT `sub` claim (`Authentication.getName()`), never from the request body.

## 1. Prerequisites

- JDK 17+
- Docker Desktop / Docker Engine with Compose
- Gradle wrapper (optional; use installed Gradle 8.13+ if wrapper is not included)

## 2. Start PostgreSQL and Keycloak

From the project root:

```bash
docker compose up -d
```

Keycloak: http://localhost:8081 (admin / admin)  
Realm: `item-management`  
Local demo users:
- `demo-user` / `ChangeMe_User_123!`
- `demo-admin` / `ChangeMe_Admin_123!`

These are development-only credentials. Change them before use outside a local demo. Never use these credentials or `start-dev` in production.

## 3. Run the API

```bash
gradle bootRun
```

API base URL: http://localhost:8080

The API validates JWT signature and timestamps using Keycloak's issuer metadata/JWKS, then checks the `aud` claim contains `item-api`. Realm roles are mapped to `ROLE_USER` / `ROLE_ADMIN`.

## 4. Get an access token for local testing

For a quick local-only test, use the Keycloak token endpoint with the `item-cli` public client and password grant (enabled only for this demo client):

```bash
curl -X POST 'http://localhost:8081/realms/item-management/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'client_id=item-cli' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'username=demo-user' \
  --data-urlencode 'password=ChangeMe_User_123!'
```

Copy `access_token` from the response:

```bash
export USER_TOKEN='<access_token>'
```

For the admin token, repeat with `demo-admin` and set `ADMIN_TOKEN`.

Password grant is included only to make local Bruno/curl testing straightforward. For real browser/mobile applications, use Authorization Code with PKCE; do not embed client secrets in public clients.

## 5. Try the APIs

Create an item as USER:

```bash
curl -i -X POST http://localhost:8080/api/items \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Keyboard","description":"Mechanical keyboard","price":2999.00}'
```

Read the created item using its returned `id`:

```bash
curl -i http://localhost:8080/api/items/<ITEM_UUID> \
  -H "Authorization: Bearer $USER_TOKEN"
```

List all items as ADMIN:

```bash
curl -i http://localhost:8080/api/items \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

Expected security checks:
- Missing/invalid/expired token: `401 Unauthorized`
- USER calls `GET /api/items`: `403 Forbidden`
- USER reads another user's item: `403 Forbidden`
- ADMIN lists all items: `200 OK`
- Valid create: `201 Created`

## 6. Configuration notes

Environment variables:
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `KEYCLOAK_ISSUER_URI` (default `http://localhost:8081/realms/item-management`)
- `KEYCLOAK_AUDIENCE` (default `item-api`)

The API uses `ddl-auto: update` for local development only. Use Flyway/Liquibase migrations and `ddl-auto: validate` in production. Also configure TLS, strong secrets, restricted CORS, production Keycloak settings, and appropriate token/session policies before deployment.

## 7. Important implementation notes

- Collection listing is ADMIN-only. USERs retrieve individual records they own.
- The example stores Keycloak's stable subject (`sub`) as `ownerId`.
- For privacy, consider returning `404` instead of `403` for cross-owner item lookups if the API should not reveal that an item exists.
- The current sample has no refresh-token handling; access-token lifecycle is managed by Keycloak/client.
