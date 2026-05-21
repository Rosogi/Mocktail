# Mocktail

Mocktail is a self-hosted HTTP mock server for teams and internal development environments.

It lets users create mock endpoints, inspect incoming requests in real time, return static or dynamic responses, and share mock collections with teammates. Mocktail can run with LDAP authentication, database authentication, or as a local standalone app without login.

## Features

- LDAP, database authentication, and standalone deployment modes.
- Per-user mock endpoints on dedicated ports.
- Live dashboard with request logs and request/response details.
- Mock matching by HTTP method, path pattern, request body substring, advanced request conditions, active state, and priority.
- JSON, XML, SOAP, HTML, plain text, and form-url-encoded response presets.
- Response formatting for JSON, XML, SOAP, HTML, and form-url-encoded bodies.
- Global and packaged environment variables for reusable ports, URLs, headers, and credentials.
- Template variables from active environment packages, globals, request method, path, query params, headers, JSON body fields, and XML/SOAP XPath.
- Template fallback values, autocomplete, and missing environment warnings in mock and default-response editors.
- Mock collections with enable/disable controls.
- Shared collections and subscriptions in multi-user modes.
- Import and export for mocks, collections, globals, and environment packages.
- Docker Compose support for all deployment modes.

## Running Mocktail

Mocktail can be started in three modes. Choose the mode that matches how users should access the application:

| Mode | Best for | Authentication | Users | Admin UI | Shared |
|---|---|---|---|---|---|
| **Standalone** | Local single-user usage | None | One local user | No | No |
| **Database Auth** | Internal installations without LDAP | Login/password stored in Mocktail | Created by administrator | Yes | Yes |
| **LDAP** | Corporate environments with existing LDAP | LDAP | Created on first login | No | Yes |

All provided Docker Compose files start PostgreSQL automatically. The UI always runs on port `8999`; mock requests are sent to user ports.

### Standalone Mode

Standalone mode is the simplest way to run Mocktail locally.

Use it when you want a single-user local mock server without authentication.

```bash
docker compose -f docker-compose.standalone.yml up --build
```

After startup, open the UI and send mock requests to the local mock endpoint:

```text
UI:            http://localhost:8999
Mock endpoint: http://localhost:9000
```

Standalone mode creates one local user, skips authentication, and hides user-management features. Because there are no other users, the Shared section is not shown. This mode uses a single mock endpoint port configured by `MOCKTAIL_STANDALONE_USER_PORT`.

Settings:

| Variable | Default | Description |
|---|---:|---|
| `DB_PASS` | `mocktail` | PostgreSQL password used by Docker Compose. |
| `MOCKTAIL_STANDALONE_USER_PORT` | `9000` | Port used by the local standalone mock endpoint. |

Example with a custom mock port:

```bash
MOCKTAIL_STANDALONE_USER_PORT=9100 docker compose -f docker-compose.standalone.yml up --build
```

### Database Auth Mode

Database auth mode is intended for internal installations where LDAP is not available.

Users sign in with accounts managed inside Mocktail. A platform administrator creates users, resets passwords, enables or disables users, and manages roles.

```bash
docker compose -f docker-compose.database.yml up --build
```

After startup, open the UI:

```text
http://localhost:8999
```

Database auth mode enables the Admin UI and Shared collections. A platform administrator creates users and manages their roles, but cannot manually enter user passwords. Mocktail generates temporary passwords for new users and password resets, and users must change those temporary passwords on first login.

Settings:

| Variable | Default | Description |
|---|---:|---|
| `DB_PASS` | `mocktail` | PostgreSQL password used by Docker Compose. |
| `MOCKTAIL_DATABASE_BOOTSTRAP_ADMIN_LOGIN` | `admin` | Login for the first administrator. |
| `MOCKTAIL_DATABASE_BOOTSTRAP_ADMIN_PASSWORD` | empty | Optional bootstrap admin password. If empty, Mocktail generates one. |
| `MOCKTAIL_PORT_RANGE_START` | `9000` | First assignable user mock port. |
| `MOCKTAIL_PORT_RANGE_END` | `9999` | Last assignable user mock port. |

If `MOCKTAIL_DATABASE_BOOTSTRAP_ADMIN_PASSWORD` is empty, Mocktail prints the generated bootstrap admin password once in the application logs.

```bash
docker compose -f docker-compose.database.yml logs -f app
```

The provided Compose file exposes ports `9000-9020`. If you change the application port range, also update the `ports` section in `docker-compose.database.yml`.

### LDAP Mode

LDAP mode is intended for corporate environments where users already authenticate through LDAP.

```bash
docker compose up --build
```

After startup, open the UI:

```text
http://localhost:8999
```

LDAP mode delegates authentication to LDAP and does not store user passwords in Mocktail. Users are created in Mocktail on first successful login and receive an assigned mock endpoint port. The Admin UI is disabled because user management belongs to LDAP, while Shared collections remain available.

By default, `docker-compose.yml` uses the `dev` profile and embedded LDAP test users.

Test users:

| Username | Password |
|---|---|
| `admin` | `admin123` |
| `alice` | `alice123` |
| `bob` | `bob123` |
| `charlie` | `charlie123` |

Settings:

| Variable | Default | Description |
|---|---:|---|
| `DB_PASS` | `mocktail` | PostgreSQL password used by Docker Compose. |
| `SPRING_PROFILES_ACTIVE` | `dev` | Use `dev` for embedded LDAP, or another profile for real LDAP configuration. |
| `MOCKTAIL_LDAP_URL` | `ldap://localhost:8389` in dev | LDAP server URL. |
| `MOCKTAIL_LDAP_BASE_DN` | `dc=mockserver,dc=com` | LDAP base DN. |
| `MOCKTAIL_LDAP_USER_SEARCH_BASE` | empty | Optional user search base below the base DN. |
| `MOCKTAIL_LDAP_USER_SEARCH_FILTER` | `(sAMAccountName={0})` | LDAP user search filter. |
| `MOCKTAIL_LDAP_GROUP_SEARCH_BASE` | `ou=groups` | LDAP group search base. |
| `MOCKTAIL_LDAP_MANAGER_DN` | empty | Optional manager DN for LDAP bind. |
| `MOCKTAIL_LDAP_MANAGER_PASSWORD` | empty | Optional manager password for LDAP bind. |
| `MOCKTAIL_PORT_RANGE_START` | `9000` | First assignable user mock port. |
| `MOCKTAIL_PORT_RANGE_END` | `9999` | Last assignable user mock port. |

The provided Compose file exposes ports `9000-9020`. If you change the application port range, also update the `ports` section in `docker-compose.yml`.

## Environments and Template Variables

Mocktail has a separate **Environments** section for reusable runtime values. Use it for values such as host names, ports, base URLs, auth headers, tenant IDs, and credentials.

### Environment Variables

There are two kinds of environment values:

| Type | Description |
|---|---|
| Globals | Values available in every runtime environment. |
| Environment packages | Named sets of values, for example `Local development`, `Staging`, or `Partner sandbox`. |

Mocks and collections are not bound to a specific package. Instead, Mocktail resolves templates against the currently selected runtime environment when a request is handled. When an environment package is active, globals are still available through `{{global.key}}`; `{{env.key}}` also falls back to a global value with the same key if the active package does not define it.

The active package can also be overridden per request:

```text
X-Mocktail-Environment: Local development
```

```text
http://localhost:9000/api/users?__mocktail_env=Local%20development
```

The override value can be an environment package name or id. Use `globals` or `none` to force globals-only resolution for a request.

Environment values can be imported and exported separately from mocks and collections. Globals have their own import/export actions. Environment packages can be imported as a copy, merged into an existing package, replaced, duplicated, or exported. Hidden value state is stored with the variable, so hidden values remain hidden after reopening the page and appear as `********` in autocomplete previews.

### Template Syntax

Templates use double braces:

| Expression | Value source |
|---|---|
| `{{env.baseUrl}}` | Active environment package, then globals fallback. |
| `{{global.companyId}}` | Globals only. |
| `{{request.method}}` | Incoming HTTP method. |
| `{{request.path}}` | Incoming request path. |
| `{{param.policy}}` | Query parameter. |
| `{{header.X-Correlation-Id}}` | Request header. |
| `{{transaction.id}}` | JSON request body field. Nested fields are supported. |
| `{{xpath:string(//*[local-name()='CustomerId'])}}` | XML/SOAP XPath expression. |
| `{{fn.uuid('correlationId')}}` | Response function. Functions are only available while building a response. |

Environment values can be composed from other environment values:

```text
address = http://localhost
port = :9000
url = {{address}}{{port}}
```

Use `??` to provide a fallback value when a template value is missing:

```text
{{env.port ?? 8080}}
{{env.enabled ?? true}}
{{env.deletedAt ?? null}}
{{global.companyId ?? 'default-company'}}
{{address.city??'Unknown'}}
```

The `??` separator works with or without spaces around it. Fallbacks support strings, numbers, booleans, and `null`. Fallback parsing happens only inside `{{...}}`, so normal response text such as `What???` is left unchanged.

### Response Functions

Functions live under the `fn` namespace and are available in response bodies, response content type values, extra response header names, extra response header values, and the default response body. They are not allowed in mock method, path, or request matching conditions so matching remains deterministic.

Built-in functions include:

```text
{{fn.uuid('correlationId')}}
{{fn.uuid('correlationId', 'hexUpper')}}
{{fn.randomInt(1000, 9999)}}
{{fn.randomDigits(6)}}
{{fn.randomAlnum(24)}}
{{fn.nowEpochMillis()}}
{{fn.sequence('orders')}}
{{fn.ulid('eventId')}}
```

The first argument of `fn.uuid` and `fn.ulid` is an optional request-scoped cache key. Reusing the same key in headers and body produces the same value for one handled request.

Custom pure functions are managed under **Functions** and are written in Starlark-style syntax.
The editor accepts only the function body; Mocktail generates `def main(...)`
from the signature before saving and executing it.

Function metadata uses a single autocomplete signature, for example
`fn.authToken(authHeader string) string`; the return type is derived from the
signature.

```python
if authHeader.startswith("Bearer "):
    return authHeader.substring(7, None)
return authHeader
```

Custom functions are called the same way as standard functions:

```text
{{fn.authToken(header.Authorization)}}
{{fn.someFunction(fn.anotherFunction())}}
{{fn.someFunction({{env.someValue}})}}
```

Collections that use custom functions cannot be shared; share the functions separately from the Shared page.

### Where Templates Work

Templates can be used in:

- Mock path pattern.
- Basic request body contains matching.
- Advanced request matching condition target and value.
- Response body.
- Extra response header names and values.
- Default response body in Settings.

Template-enabled editors show autocomplete suggestions. The left side shows the expression, and the right side shows the resolved preview value. Missing `env` and `global` references without a fallback are shown as warnings in the mock list, in the mock editor, and under the Default response body editor.

## JSON Mock Example

This example mocks a risk scoring service used during checkout. The application sends transaction data to an external service, and Mocktail returns a deterministic scoring response while still echoing useful request values.

Create a mock in the UI:

| Field | Value |
|---|---|
| Method | `POST` |
| Path pattern | `/risk/score` |
| Response status | `200` |
| Response Content-Type | `application/json` |
| Priority | `100` |
| Active | enabled |

Response body:

```json
{
  "transactionId": "{{transaction.id}}",
  "decision": "APPROVE",
  "score": 18,
  "amount": "{{transaction.amount}}",
  "currency": "{{transaction.currency}}",
  "customerSegment": "{{customer.segment}}",
  "policy": "{{param.policy}}",
  "correlationId": "{{header.X-Correlation-Id}}",
  "serviceBaseUrl": "{{env.riskServiceBaseUrl ?? 'http://localhost:9000'}}",
  "companyId": "{{global.companyId ?? 'demo-company'}}",
  "handledBy": "{{request.method}} {{request.path}}"
}
```

Send a request to your mock endpoint. In standalone mode the default endpoint is `localhost:9000`. In LDAP or database mode, use the port shown in the sidebar.

```bash
curl -i -X POST 'http://localhost:9000/risk/score?policy=checkout-v2' \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: corr-7f31' \
  -d '{
        "transaction": {
          "id": "txn_10001",
          "amount": 129.9,
          "currency": "USD"
        },
        "customer": {
          "id": "cus_7788",
          "segment": "gold"
        },
        "device": {
          "ip": "203.0.113.42"
        }
      }'
```

Expected response:

```json
{
  "transactionId": "txn_10001",
  "decision": "APPROVE",
  "score": 18,
  "amount": "129.9",
  "currency": "USD",
  "customerSegment": "gold",
  "policy": "checkout-v2",
  "correlationId": "corr-7f31",
  "serviceBaseUrl": "http://localhost:9000",
  "companyId": "demo-company",
  "handledBy": "POST /risk/score"
}
```

## MCP access for LLM clients

Mocktail can expose a user-scoped MCP endpoint for LLM clients such as Claude, Codex, Cursor, VS Code, and other tools that support the Model Context Protocol.

MCP is disabled by default. Enable it explicitly:

```bash
MOCKTAIL_MCP_ENABLED=true docker compose -f docker-compose.standalone.yml up --build
```

The same variable works for LDAP and database-auth deployments:

```bash
MOCKTAIL_MCP_ENABLED=true docker compose up --build
MOCKTAIL_MCP_ENABLED=true docker compose -f docker-compose.database.yml up --build
```

When MCP is enabled, open **Settings** and use the **LLM access** section to generate a personal token. The token belongs to the currently signed-in user, is shown only once, and must be configured in the LLM client as a Bearer token.

The MCP endpoint is:

```text
http://localhost:8999/mcp
```

Token regeneration immediately invalidates the previous token. Existing Claude, Codex, and other MCP sessions must be updated with the new token.

First-stage permissions:

| Area | Access |
|---|---|
| Request logs | `None`, `Read`, or `Read + delete` |
| Mocks | `None` or `Read` |

Request log deletion and clearing require both `Read + delete` permission and an explicit `confirm=true` argument in the MCP tool call.

Available first-stage tools depend on the token permissions:

| Tool | Required permission | Description |
|---|---|---|
| `mocktail_recent_request_logs` | Request logs: `Read` | List recent request logs for the token owner. |
| `mocktail_get_request_log` | Request logs: `Read` | Read one request log. Sensitive headers are redacted. |
| `mocktail_delete_request_log` | Request logs: `Read + delete` | Delete one request log. Requires `confirm=true`. |
| `mocktail_clear_request_logs` | Request logs: `Read + delete` | Clear all request logs for the token owner. Requires `confirm=true`. |
| `mocktail_list_mocks` | Mocks: `Read` | List mock definitions owned by the token owner. |
| `mocktail_get_mock` | Mocks: `Read` | Read one mock definition owned by the token owner. |

Example Claude Code setup:

```bash
claude mcp add --transport http mocktail http://localhost:8999/mcp \
  --header "Authorization: Bearer YOUR_MOCKTAIL_TOKEN"
```

For clients that do not support custom HTTP headers directly, use a local MCP wrapper or the client's supported secret/header configuration.

## FAQ

### Where is the Mocktail UI?

Open:

```text
http://localhost:8999
```

### Which port should my application call?

Use the mock endpoint port shown in the sidebar.

In standalone mode the default mock endpoint is:

```text
http://localhost:9000
```

### Where do I find the generated database admin password?

In database auth mode, if no bootstrap password is provided, Mocktail prints the generated admin password once in the application logs:

```bash
docker compose -f docker-compose.database.yml logs -f app
```

### Why is `/admin` unavailable?

The Admin UI is available only in database auth mode.

LDAP mode uses external user management, so `/admin` is disabled. Standalone mode has no user administration.

### Why is the Shared section unavailable?

Shared collections are disabled in standalone mode because there are no other users.

Shared collections are available in LDAP and database auth modes.

### Why does my old admin or old users still exist after restarting Docker?

PostgreSQL data is stored in Docker volumes. Restarting containers does not remove the database.

To reset a local database, stop the Compose stack and remove its volume:

```bash
docker compose -f docker-compose.database.yml down -v
```

Use the matching Compose file for the mode you are running.

### What happens if no mock matches a request?

Mocktail returns the configured default response for that user.

You can edit it in:

```text
Settings -> Default response
```

The default response body supports the same template syntax, autocomplete suggestions, and missing environment warnings as mock response bodies.

### Why does my mock not match?

Check:

- The mock is active.
- The HTTP method matches, or the mock method is `*`.
- The path pattern matches the request path.
- The request body contains condition is empty or matches the raw request body.
- Advanced request matching conditions match the incoming request.
- Any `env` or `global` values used in the path or matching rules exist in the active runtime environment, or have fallback values.
- Another matching mock with a higher priority is not winning first.

### How do I know if a mock references missing environment values?

Mocktail shows a warning icon in the mock list when a mock uses missing `env` or `global` values. The mock editor also shows the missing references with the field where they were found. Request-derived values such as `{{request.method}}`, `{{param.id}}`, `{{header.Authorization}}`, JSON fields, and XPath expressions are not treated as missing environment values.

### Why can I open the UI but requests to the mock endpoint fail?

Make sure the user mock port is exposed by Docker Compose.

The provided LDAP and database Compose files expose:

```text
9000-9020
```

If a user is assigned a port outside that exposed range, either expose a wider range in Compose or configure the application port range to fit the exposed ports.

### How do I view application logs?

Standalone:

```bash
docker compose -f docker-compose.standalone.yml logs -f app
```

Database auth:

```bash
docker compose -f docker-compose.database.yml logs -f app
```

LDAP:

```bash
docker compose logs -f app
```
