# Mocktail

Self-hosted HTTP mock server with per-user ports, LDAP authentication, and a real-time web UI.

## Features

- **Per-user mock ports** – every user gets a dedicated port in the range `9000–9999`
- **LDAP authentication** – integrates with your existing LDAP/AD; embedded LDAP available for local dev
- **Persistent mocks** – stored in PostgreSQL, survive restarts
- **Real-time request log** – WebSocket (STOMP) pushes incoming requests to your browser instantly
- **Ant-style path patterns** – `/api/users/**`, `/api/orders/*`, exact paths
- **Method + body matching** – match by HTTP method, path, and optional body substring (JSON / XML / SOAP)
- **Priority** – when multiple mocks match, the highest priority wins
- **Toggle active** – enable / disable individual mocks without deleting them

## Quick Start (dev mode)

### Prerequisites
- Java 21+
- Maven 3.9+
- PostgreSQL (or Docker)

### 1. Start PostgreSQL

```bash
docker run -d \
  --name mocktail-pg \
  -e POSTGRES_DB=mocktail \
  -e POSTGRES_USER=mocktail \
  -e POSTGRES_PASSWORD=mocktail \
  -p 5432:5432 \
  postgres:16
```

### 2. Run the application (dev profile = embedded LDAP)

```bash
cd mocktail
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Open the UI

```
http://localhost:8080
```

## An alternative way to run using docker

```bash
#Copy example config in .env
cp .env.example .env
docker compose up --build
```

### Default test users (dev profile)

| Username | Password  |
|----------|-----------|
| admin    | admin123  |
| alice    | alice123  |
| bob      | bob123    |
| charlie  | charlie123|

After first login each user is automatically assigned a port (e.g. `9000`, `9001`, …).

---

## Usage

### Sending requests to your mock endpoint

After logging in, your dedicated port is shown in the sidebar and on the dashboard, e.g.:

```
http://localhost:9000
```

Point your service under test at this URL instead of the real downstream service:

```bash
# Example: replace the real payment service URL in your service config
PAYMENT_SERVICE_URL=http://localhost:9000
```
or

Make your first call directly from the CLI:
```bash
# Direct call from CLI
curl -i localhost:9000/hello/world
```

Every request that hits your port appears **immediately** in your dashboard via WebSocket.

### Creating mocks

1. Go to **Mocks → New mock**
2. Configure:
   - **Method**: `GET`, `POST`, `*` (any), etc.
   - **Path pattern**: `/api/payments/**` (Ant-style)
   - **Request body contains**: optional substring for SOAP/JSON dispatch
   - **Response status / body / headers / content-type**
   - **Priority**: higher number = matched first when multiple rules match
3. Save. The mock is **immediately active**.

### Path pattern examples

| Pattern | Matches |
|---------|---------|
| `/api/users` | Exactly `/api/users` |
| `/api/users/**` | `/api/users/1`, `/api/users/1/roles`, … |
| `/api/orders/*` | `/api/orders/123` (single segment) |
| `/**` | Everything |

---

## Production deployment

### application-prod.yml (or environment variables)

```yaml
app:
  ldap:
    url: ldap://your-ldap-server:389
    base-dn: dc=company,dc=com
    user-dn-pattern: uid={0},ou=people
    group-search-base: ou=groups
    manager-dn: cn=readonly,dc=company,dc=com
    manager-password: secret
```

Or via environment variables:

```bash
LDAP_URL=ldap://ldap.company.com:389
LDAP_BASE_DN=dc=company,dc=com
LDAP_USER_DN_PATTERN=uid={0},ou=people
DB_URL=jdbc:postgresql://db:5432/mockserver
DB_USER=mockserver
DB_PASS=strongpassword
```

### Run with prod profile

```bash
java -jar mocktail-1.0.0.jar --spring.profiles.active=prod
```

### Docker Compose (full stack)

```yaml
version: "3.9"
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: mocktail
      POSTGRES_USER: mocktail
      POSTGRES_PASSWORD: mocktail
    volumes:
      - pg_data:/var/lib/postgresql/data

  app:
    image: eclipse-temurin:21-jre
    working_dir: /app
    volumes:
      - ./target/mocktail.jar:/app/app.jar
    ports:
      - "8080:8080"       # Admin UI
      - "9000-9020:9000-9020"  # User mock ports
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_URL: jdbc:postgresql://db:5432/mocktail
      DB_USER: mocktail
      DB_PASS: mocktail
      LDAP_URL: ldap://your-ldap:389
      LDAP_BASE_DN: dc=company,dc=com
    depends_on:
      - db
    command: ["java", "-jar", "/app/app.jar"]

volumes:
  pg_data:
```

---

## Architecture

```
Browser ──────► :8080 (Spring MVC / Thymeleaf)
                   │ Spring Security + LDAP
                   │
                   ▼
              Admin UI / REST
                   │
              ┌────┴──────┐
              │  Services  │
              │ MockService│
              │ LogService │
              └────┬──────┘
                   │ JPA / PostgreSQL
                   ▼
              mock_definitions
              request_logs
              users

Service A ──► :9000 (user alice's port)
Service B ──► :9001 (user bob's port)   ──► CatchAllFilter
                                               │ match mock
                                               │ log + WebSocket push
                                               ▼
                                         Browser dashboard (live)
```

---

## Project structure

```
src/main/java/com/rosogisoft/
├── MocktailApplication.java
├── config/
│   ├── AppProperties.java          # port range config
│   ├── BeanConfig.java             # AntPathMatcher bean
│   ├── SecurityConfig.java         # LDAP + two filter chains
│   └── WebSocketConfig.java        # STOMP broker
├── converter/
│   └── MapToJsonConverter.java     # Map<String,String> ↔ TEXT
├── domain/
│   ├── MockDefinition.java
│   ├── RequestLog.java
│   └── User.java
├── repository/
│   ├── MockDefinitionRepository.java
│   ├── RequestLogRepository.java
│   └── UserRepository.java
├── service/
│   ├── MockMatcherService.java     # Ant-path matching logic
│   ├── MockService.java            # CRUD
│   ├── PortManagerService.java     # dynamic Tomcat connectors
│   ├── RequestLogService.java
│   └── UserService.java
├── web/
│   ├── CatchAllFilter.java         # intercepts :9000-9999
│   ├── CurrentUserHelper.java
│   ├── DashboardController.java
│   ├── LoginController.java
│   ├── LoginSuccessHandler.java
│   ├── MockController.java
│   └── dto/
│       └── MockDefinitionForm.java
└── ws/
    └── RequestEventPublisher.java  # STOMP broadcast
```
