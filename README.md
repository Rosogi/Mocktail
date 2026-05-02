# 🍸 Mocktail

**Self-hosted HTTP mock server with per-user endpoints, LDAP/database/standalone deployment modes, real-time request inspection, and mock collections.**

Mocktail lets your team intercept and inspect HTTP requests during development and testing — without touching real downstream services. Each developer gets a personal endpoint on a dedicated port, with a live web dashboard, full request history, and flexible mock rules that support JSON/XML/SOAP responses and dynamic templating.

---

## Table of contents

- [Features](#features)
- [How it works](#how-it-works)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Usage guide](#usage-guide)
   - [Dashboard](#dashboard)
   - [Creating mocks](#creating-mocks)
   - [Mock matching rules](#mock-matching-rules)
   - [Template variables](#template-variables)
   - [Collections](#collections)
   - [Import and export](#import-and-export)
- [Production deployment](#production-deployment)
- [Project structure](#project-structure)
- [Tech stack](#tech-stack)
- [Development notes](#development-notes)

---

## Features

| Feature | Description |
|---|---|
| **Per-user ports** | Every user gets a dedicated port (9000–9999). Requests to your port are only matched against your mocks. |
| **Deployment modes** | Run with LDAP auth, database auth, or standalone without auth. |
| **Real-time request log** | Incoming requests appear instantly in your browser via WebSocket. Click any row to see full request and response details. |
| **Flexible mock matching** | Match by HTTP method, Ant-style path pattern, and optional request body substring. |
| **Template variables** | Use values from the incoming request inside your mock response body — JSON fields, query params, headers, and more. |
| **Mock collections** | Group related mocks into collections. Enable or disable an entire collection with one click. |
| **Import / export** | Export your mocks (or a single collection) as a JSON file. Share with teammates and import on their instance. |
| **JSON / XML / SOAP** | Supports any content type. Built-in formatter and presets for JSON, XML, SOAP, plain text, and HTML. |
| **Self-hosted** | Runs as a single Spring Boot JAR or via Docker Compose. No external services required. |

---

## How it works

```
Your service  ─────────────────────────────────►  Mocktail :9000
                                                       │
                                                  CatchAllFilter
                                                  matches your mocks
                                                       │
                                              ┌────────┴─────────┐
                                         Mock found         No match
                                              │                   │
                                     render response         404 JSON
                                     (with templates)
                                              │
                                    save to request log
                                    push via WebSocket
                                              │
                                    Your browser dashboard
                                    sees the request live
```

In LDAP and database modes, each user gets a dedicated port and Mocktail registers a live Tomcat connector for that port. In standalone mode, Mocktail creates one local user and one mock port. Ports are persisted in PostgreSQL and restored automatically on restart.

The UI runs on port **8999**. Mock endpoints run on ports **9000–9999** in LDAP/database modes, and on `MOCKTAIL_STANDALONE_USER_PORT` in standalone mode.

---

## Quick start

### Prerequisites

- Docker and Docker Compose
- Ports 8999 and 9000–9020 available on your machine

### 1. Clone and configure

```bash
git clone https://github.com/Rosogi/mocktail.git
cd mocktail
```

The default `docker-compose.yml` starts LDAP mode with embedded LDAP test users.

### 2. Start

```bash
docker compose up --build
```

First build takes 3–5 minutes (Maven dependency download). Subsequent builds take ~30 seconds thanks to Docker layer caching.

### 3. Open the UI

```
http://localhost:8999
```

### Default test users (dev profile)

| Username | Password   |
|----------|------------|
| admin    | admin123   |
| alice    | alice123   |
| bob      | bob123     |
| charlie  | charlie123 |

After first login each user is automatically assigned a mock port.

---

## Configuration

All configuration is done via environment variables, either in `.env` (Docker Compose) or passed directly to the JAR.

### `.env` reference

```env
MOCKTAIL_MODE=ldap # ldap | database | standalone

# ── Application profile ───────────────────────────────────────
# "dev"  – embedded LDAP, verbose SQL logging
# "prod" – real LDAP, optimised for production
SPRING_PROFILES_ACTIVE=dev

# ── Database ──────────────────────────────────────────────────
DB_PASS=mockserver

# ── LDAP (required in LDAP mode with real LDAP) ───────────────
MOCKTAIL_LDAP_URL=ldap://your-ldap-server:389
MOCKTAIL_LDAP_BASE_DN=dc=company,dc=com
MOCKTAIL_LDAP_USER_SEARCH_FILTER=(sAMAccountName={0})
MOCKTAIL_LDAP_GROUP_SEARCH_BASE=ou=groups

# Manager account — only if anonymous bind is not allowed
MOCKTAIL_LDAP_MANAGER_DN=cn=readonly,dc=company,dc=com
MOCKTAIL_LDAP_MANAGER_PASSWORD=secret

# ── Database auth bootstrap admin ─────────────────────────────
MOCKTAIL_DATABASE_BOOTSTRAP_ADMIN_LOGIN=admin
# Optional. If empty, Mocktail generates a 20-character temporary password
# and prints it once in application logs.
MOCKTAIL_DATABASE_BOOTSTRAP_ADMIN_PASSWORD=

# ── Standalone ────────────────────────────────────────────────
MOCKTAIL_STANDALONE_USER_PORT=9000
```

### Deployment modes

```bash
# LDAP mode with embedded LDAP dev users
docker compose up --build

# Database auth mode
docker compose -f docker-compose.database.yml up --build

# Standalone mode, no login and no Shared section
docker compose -f docker-compose.standalone.yml up --build
```

### Port range

By default Mocktail assigns ports from **9000 to 9999**, supporting up to 1000 users. The range is configurable in `application.yml`:

```yaml
mocktail:
  ports:
    range-start: 9000
    range-end:   9999
```

---

## Usage guide

### Dashboard

After logging in you land on the Dashboard. At the top you will see:

- **Your endpoint** — the full URL your services should send requests to, e.g. `http://mocktail-host:9000`
- **Requests counter** — total number of logged requests
- **Mocks counter** — number of mocks you have configured
- **Clear** — wipe the request log
- **LIVE indicator** — green when WebSocket is connected, grey when offline

Every incoming request appears as a new row in the table. Click any row to open the **Request detail modal**, which shows:

- Time, method, status, remote IP, content type, query string
- Matched mock name (or "no match" if no rule was found)
- Full request headers
- Request body and response body side by side, with scrolling and resize support

### Creating mocks

Go to **Mocks → New mock** and fill in the form:

| Field | Description |
|---|---|
| **Name** | A human-readable label shown in the request log |
| **Collection** | Optionally assign this mock to a collection |
| **Method** | HTTP method to match. Use `*` to match any method |
| **Path pattern** | Ant-style path pattern (see below) |
| **Request body contains** | Optional substring to match in the raw request body |
| **Status** | HTTP response status code |
| **Content-Type** | Response content type |
| **Priority** | When multiple mocks match, the one with the highest priority wins |
| **Active** | Toggle to enable or disable this mock without deleting it |
| **Response body** | The response body, supports template variables |
| **Extra headers** | Additional response headers, one per line as `Name: Value` |

Use the **Fill preset** button to populate the response body with a template for the selected content type. Use the **Format** button to auto-indent JSON or XML.

### Mock matching rules

When a request arrives on your port, Mocktail searches your active mocks in priority order (highest first). A mock matches when **all** of the following are true:

1. `httpMethod` equals the request method, or is `*`
2. `pathPattern` matches the request path (Ant-style)
3. If `requestBodyContains` is set, the request body contains that substring

The first matching mock wins. If no mock matches, Mocktail returns a 404 with a JSON error body.

#### Path pattern examples

| Pattern | Matches |
|---|---|
| `/api/users` | Exactly `/api/users` |
| `/api/users/**` | `/api/users/1`, `/api/users/1/roles`, and deeper |
| `/api/orders/*` | `/api/orders/123` but not `/api/orders/123/items` |
| `/**` | Everything |

### Template variables

You can embed values from the incoming request directly in your mock response body using `{{expression}}` syntax.

| Expression | Resolves to |
|---|---|
| `{{name}}` | Top-level field `name` from JSON request body |
| `{{user.address.city}}` | Nested JSON path (dot-separated) |
| `{{param.id}}` | URL query parameter `?id=...` |
| `{{header.Authorization}}` | Request header value (case-insensitive) |
| `{{request.method}}` | HTTP method (`GET`, `POST`, …) |
| `{{request.path}}` | Request path (`/api/users/1`) |

If an expression cannot be resolved (field missing, body not JSON, etc.) the placeholder is left unchanged in the response.

#### Example

Mock response body:
```json
{
  "message": "Hello, {{name}}!",
  "yourId": "{{param.id}}",
  "via": "{{request.method}} {{request.path}}",
  "token": "{{header.Authorization}}"
}
```

Incoming request:
```bash
curl -X POST "http://localhost:9000/api/greet?id=42" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer abc123" \
  -d '{"name": "Alice"}'
```

Response:
```json
{
  "message": "Hello, Alice!",
  "yourId": "42",
  "via": "POST /api/greet",
  "token": "Bearer abc123"
}
```

### Collections

Collections let you group related mocks and control them together.

Go to **Collections** in the sidebar to:

- **Create** a new collection with a name and optional description
- **View** a collection — see all its mocks, add or remove mocks
- **Enable all** — activate every mock in the collection at once
- **Disable all** — deactivate every mock without deleting them
- **Export** — download the collection as a JSON file
- **Delete** — remove the collection (mocks are kept, just uncollected)

To assign a mock to a collection, either select it in the **New mock / Edit mock** form, or open the collection's detail page and use the **Add mock** panel.

### Import and export

#### Exporting

- **All mocks**: Mocks page → **Export all** button — downloads a JSON file with all your mocks and collections
- **Single collection**: Collections page → collection card → **Export** button

#### Importing

Mocks page → **Import** button → select a `.json` file exported from Mocktail.

On import:
- Collections from the file are created if they don't already exist (matched by name)
- Mocks are always added as new — existing mocks are never modified
- Mocks without a collection are imported as uncollected

#### Export file format

```json
{
  "version": 1,
  "exportedAt": "2024-04-10T12:00:00Z",
  "exportedBy": "alice",
  "collections": [
    {
      "name": "Payment service",
      "description": "Mocks for the payment API",
      "mocks": [
        {
          "name": "Create payment 200",
          "httpMethod": "POST",
          "pathPattern": "/api/payments",
          "responseStatus": 200,
          "responseBody": "{\"id\": \"{{param.ref}}\", \"status\": \"ok\"}",
          "responseContentType": "application/json",
          "priority": 0,
          "active": true
        }
      ]
    }
  ],
  "mocks": []
}
```

---

## Production deployment

### Switch to real LDAP

Set the following in your `.env`:

```env
SPRING_PROFILES_ACTIVE=prod
MOCKTAIL_MODE=ldap
MOCKTAIL_LDAP_URL=ldap://ldap.company.com:389
MOCKTAIL_LDAP_BASE_DN=dc=company,dc=com
MOCKTAIL_LDAP_USER_SEARCH_FILTER=(sAMAccountName={0})
MOCKTAIL_LDAP_GROUP_SEARCH_BASE=ou=groups
MOCKTAIL_LDAP_MANAGER_DN=cn=readonly,dc=company,dc=com
MOCKTAIL_LDAP_MANAGER_PASSWORD=your-password
```

### Expose more ports

If you expect more than 21 users, expand the port range in `docker-compose.yml`:

```yaml
ports:
  - "8999:8999"
  - "9000-9099:9000-9099"   # up to 100 users
```

And update `application.yml`:

```yaml
mocktail:
  ports:
    range-end: 9099
```

### Run without Docker

```bash
# Build
mvn package -DskipTests

# Run
java -jar target/mock-server-1.0.0.jar \
  --spring.profiles.active=prod \
  --mocktail.deployment.mode=ldap \
  --DB_URL=jdbc:postgresql://localhost:5432/mocktail \
  --DB_USER=mocktail \
  --DB_PASS=strongpassword \
  --mocktail.auth.ldap.url=ldap://ldap.company.com:389 \
  --mocktail.auth.ldap.base-dn=dc=company,dc=com
```

---

## Project structure

```
src/main/java/com/mockserver/
├── MockServerApplication.java
│
├── config/
│   ├── AppProperties.java          # Port range config
│   ├── BeanConfig.java             # AntPathMatcher bean
│   ├── SecurityConfig.java         # LDAP auth + two filter chains
│   └── WebSocketConfig.java        # STOMP broker setup
│
├── converter/
│   └── MapToJsonConverter.java     # JPA: Map<String,String> ↔ TEXT
│
├── domain/
│   ├── MockCollection.java         # Collection entity
│   ├── MockDefinition.java         # Mock rule entity
│   ├── RequestLog.java             # Incoming request log entity
│   └── User.java                   # User entity (username + assigned port)
│
├── repository/
│   ├── MockCollectionRepository.java
│   ├── MockDefinitionRepository.java
│   ├── RequestLogRepository.java
│   └── UserRepository.java
│
├── service/
│   ├── MockCollectionService.java  # Collection CRUD + enable/disable all
│   ├── MockImportExportService.java# JSON serialization for export/import
│   ├── MockMatcherService.java     # Ant-path matching logic
│   ├── MockService.java            # Mock CRUD
│   ├── MockTemplateEngine.java     # {{variable}} substitution
│   ├── PortManagerService.java     # Dynamic Tomcat connector registration
│   ├── RequestLogService.java      # Log persistence and retrieval
│   └── UserService.java            # User provisioning on first login
│
├── web/
│   ├── CatchAllFilter.java         # Intercepts all requests on ports 9000–9999
│   ├── CurrentUserHelper.java      # Resolves User from Spring Security context
│   ├── DashboardController.java
│   ├── LoginController.java
│   ├── LoginSuccessHandler.java    # Provisions user + port on first login
│   ├── MockCollectionController.java
│   ├── MockController.java
│   └── dto/
│       ├── MockDefinitionForm.java
│       └── MockExportDto.java
│
└── ws/
    └── RequestEventPublisher.java  # Pushes new log entries over STOMP

src/main/resources/
├── application.yml                 # Base configuration
├── application-dev.yml             # Dev profile (embedded LDAP)
├── db/migration/
│   ├── V1__init.sql                # users, mock_definitions, request_logs
│   └── V2__collections.sql        # mock_collections + collection_id FK
├── ldap/
│   └── test-users.ldif             # Embedded LDAP test data
├── static/
│   ├── css/
│   │   ├── badges.css              # HTTP method and status badges
│   │   └── main.css                # Layout, sidebar, modals, forms
│   └── js/
│       ├── dashboard.js            # WebSocket, live log, request detail modal
│       └── mocks.js                # Format button, fill preset, char counter
└── templates/
    ├── login.html
    ├── dashboard.html
    ├── collections/
    │   ├── list.html
    │   └── detail.html
    ├── mocks/
    │   ├── list.html
    │   └── form.html
    └── fragments/
        ├── head.html               # <head> with CSS links
        ├── sidebar.html            # Navigation sidebar
        └── flash.html              # Success/error alert messages
```

---

## Tech stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2 |
| Language | Java 21 |
| Security | Spring Security + Spring LDAP |
| Persistence | Spring Data JPA + Hibernate 6 + PostgreSQL 16 |
| Migrations | Flyway |
| Real-time | WebSocket (STOMP + SockJS) |
| Templates | Thymeleaf 3.1 |
| Build | Maven 3.9 |
| Frontend | Bootstrap 5.3 + Bootstrap Icons |
| Container | Docker + Docker Compose |

---

## Development notes

### Running locally without Docker

```bash
# Start PostgreSQL
docker run -d --name mocktail-pg \
  -e POSTGRES_DB=mocktail \
  -e POSTGRES_USER=mocktail \
  -e POSTGRES_PASSWORD=mocktail \
  -p 5432:5432 postgres:16

# Run with dev profile (embedded LDAP on port 8389)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
