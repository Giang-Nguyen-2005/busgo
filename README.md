# BusGo

Bus Ticket Booking & Management System. This repository currently implements
**Milestone 0 — Project Foundation** only. Requirements and future milestone scope
are defined in [docs/development-plan.md](docs/development-plan.md) and the other
files under `docs/`.

## Prerequisites

- JDK 17+ (set `JAVA_HOME` to a JDK, not a Java 8 runtime)
- Maven 3.6.3+
- Node.js 22.12+ and npm
- Docker with Docker Compose v2

## Start MySQL

1. Copy `.env.example` to `.env` in the repository root.
2. Set nonempty `MYSQL_PASSWORD` and `MYSQL_ROOT_PASSWORD` in `.env`.
3. Run `docker compose up -d --wait` from the repository root.

MySQL 8.4 uses the `busgo_db` database and `busgo` account by default. It binds to
localhost port **3307** to avoid common conflicts with existing MySQL installations.
The named volume retains data when running `docker compose down`. Changing the
passwords in `.env` does not change accounts in an already initialized volume.

No business schema or seed data is installed in M0. Flyway is configured with an
empty `backend/src/main/resources/db/migration/` directory for later milestones.
Flyway may create its own schema history table on startup.

## Start the backend

Export `DB_PASSWORD` with the same value as `MYSQL_PASSWORD`, then from `backend/`:

```sh
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile defaults to `jdbc:mysql://localhost:3307/busgo_db?connectionTimeZone=UTC`
and username `busgo`. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to override.
Without the `dev` profile, all three variables are required. `SERVER_PORT` defaults
to 8080. Maven/Spring Boot do **not** automatically load the root Compose `.env` file.
For PowerShell, export a variable using `$env:DB_PASSWORD = 'your-local-password'`.

```sh
curl http://localhost:8080/api/v1/health
```

Expected HTTP 200:

```json
{"data":{"status":"UP"}}
```

Health is a process liveness endpoint, not a continuous database readiness check.
Only `GET /api/v1/health` is publicly accessible. Other requests are denied until
future milestones define authentication and access rules. No generated login,
default user, JWT, or business endpoints are provided.

## Start the frontend

From `frontend/`:

```sh
npm ci
npm run dev
```

Open http://localhost:5173. The foundation page checks the backend connection and
offers a retry if unavailable. Vite proxies `/api` to http://localhost:8080.
Optional overrides are shown in `frontend/.env.example`; copy it to
`frontend/.env.local` to use them. `VITE_*` values are public browser configuration,
so never put secrets in them. A production host must proxy `/api` to the backend
or provide a suitable API base URL and origin configuration.

## Verify

From `backend/`:

```sh
mvn test
mvn package -DskipTests
```

The default suite tests application web-context startup, the health contract,
security responses, validation, error handling, and pagination without requiring
MySQL. Its `test` profile excludes datasource/JPA/Flyway auto-configuration; it
does not substitute another database.

With MySQL running and the database environment variables configured, run:

```sh
mvn verify -Pmysql-integration
```

This additionally starts the full application with JPA/Flyway, executes `SELECT 1`
against MySQL, and checks the health endpoint over HTTP. Connection failures fail
the integration test; it is not silently skipped. Use a dedicated development/test
database for this check.

From `frontend/`:

```sh
npm run build
```

This runs strict TypeScript checking and creates the Vite production build.

## Layout

- `backend/`: Spring Boot modular monolith foundation under `com.busgo.common`
- `frontend/`: React/TypeScript, Router, Axios, TanStack Query, Tailwind;
  React Hook Form and Zod installed for later forms
- `database/`: reserved for later database support files
- `docs/`: unchanged requirements, database design, API contract, UI specification,
  and development plan

Business entities, schema migrations, authentication, and booking UI are outside M0.
