# BusGo

Bus Ticket Booking & Management System. This repository currently implements
**Milestone 0 — Project Foundation**, **Milestone 1 — Core Database**,
**Milestone 2 — Authentication**, **Milestone 3 — Operator, Fleet & Route**,
**Milestone 4 — Trip Generation**, **Milestone 5 — Customer Trip Search**, and
**Milestone 6 — Customer Seat Availability**, **Milestone 7 — Seat Hold**, and
**Milestone 8 — Booking**.
Requirements and future milestone scope
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

Flyway applies the M1 core schema and four role seeds from
`backend/src/main/resources/db/migration/` on backend startup. Hibernate remains
configured with `ddl-auto=validate`; it never creates or mutates the schema.
No accounts, operators, routes, locations, or other demo records are seeded.

## Start the backend

Export `DB_PASSWORD` with the same value as `MYSQL_PASSWORD`, and `JWT_SECRET` as
Base64-encoded cryptographically random bytes (at least 32 bytes). Never commit
the secret. Optional `JWT_ACCESS_TTL` and `JWT_REFRESH_TTL` default to `1h` and `7d`;
both must be finite positive durations, with refresh longer than access.
Then from `backend/`:

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
Public endpoints are health, `GET /api/v1/locations`, customer trip search/detail,
`GET /api/v1/trips/{tripId}/seats`, and
`POST /api/v1/auth/register`, `/login`, and `/refresh`.
M2 also implements authenticated `GET`/`PATCH /api/v1/users/me` and
`POST /api/v1/users/me/change-password`. All other application routes remain denied.
Public registration assigns CUSTOMER; no privileged or default account is seeded.
Passwords use BCrypt with a minimum of 8 characters and maximum of 72 UTF-8 bytes,
without composition rules. Blank passwords are rejected.
Refresh tokens rotate once, with only SHA-256 hashes persisted. Password changes
revoke all refresh tokens; existing access tokens remain valid until expiry, subject
to current account status and roles checked on every request. No HTTP session is used.

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

This additionally starts the full application with Flyway and Hibernate schema
validation, checks MySQL connectivity and health over HTTP, and runs M1 repository
tests for authentication contracts, token rotation/revocation, account status,
password handling, mappings, role seeds, uniqueness, foreign keys, check constraints,
decimal precision, and retained soft-delete relationships. Connection failures
fail the integration tests; they are not silently skipped. Use a dedicated empty
MySQL development/test database for a first run. Most test records are rolled back;
concurrency tests commit isolated fixtures and remove only their own records afterward.
the migrated schema and role seeds remain. Repeating the command verifies an
already-migrated database without reapplying versioned migrations.

From `frontend/`:

```sh
npm run build
```

This runs strict TypeScript checking and creates the Vite production build.

## Layout

- `backend/`: Spring Boot foundation through M8, including trip snapshots, customer
  trip search, journey-specific seat availability, temporary holds, and customer bookings
- `frontend/`: React/TypeScript, Router, Axios, TanStack Query, Tailwind;
  React Hook Form and Zod installed for later forms
- `database/`: reserved for later database support files
- `docs/`: requirements, database design, API contract, UI specification,
  and development plan

M1 contains 13 core tables; M2 adds `refresh_tokens` in migration V3, and M4 adds the
transactional trip snapshot and segment-inventory tables in V4. M5 adds only
search-oriented indexes in V5 and public snapshot/segment-based trip discovery. M6
adds no migration: it returns the snapshotted seat layout and marks a seat available
only when that same seat is AVAILABLE on every required journey segment. The seat map
is observational, does not reserve inventory, and returns a successful sold-out map
with count zero. M7 adds a hold-token lookup index in V6 and authenticated create/get/delete
hold endpoints. Holds are server-priced, expire after ten minutes, support up to five seats,
and atomically lock the complete seat × journey-segment matrix in deterministic order. A
minute-scale predicate cleanup releases expired HELD rows; create also reclaims relevant
expired rows while locked. M5/M6 remain observational, so stale expired HELD rows can remain
unavailable until cleanup/reclamation. M8 migration V7 adds `bookings`, `booking_items`, and
the nullable inventory-to-item link. Authenticated customers can atomically convert an active
owned hold to a server-priced PENDING booking and read only their own paginated history/detail.
The conversion marks only the held journey segments BOOKED and clears hold metadata, preserving
non-overlapping reuse of the same physical seat. Payment, confirmation, cancellation, ticketing,
reporting, and business UI remain outside the implemented scope and are deferred to M9 or later.
Tests generate their own ephemeral JWT signing key.

M1 follows the documented fare foreign keys. As agreed, same-route membership and
forward stop-order validation are deferred to M3; foreign keys alone cannot enforce
those cross-table rules. Phone uniqueness and fare-pair uniqueness are not imposed
because the database design does not specify them. Unspecified lifecycle statuses
use `ACTIVE`/`INACTIVE`; the only documented seat type is `STANDARD`.
