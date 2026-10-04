# BusGo V1.5

Vietnamese bus operation management and ticket booking application. Customer
commerce supplies reservation data for operator dispatch, crew, reporting and fleet
workflows. V1.5 business scope is feature-complete through M18C; M19 covers final
hardening and release readiness. **All payments/refunds are simulated.**

## Architecture and modules

React 19 / TypeScript / Vite / React Router / TanStack Query frontend; Java 17 /
Spring Boot 3.5 modular monolith; MySQL / Flyway V1–V16. JPA and scoped JDBC persist
UTC instants; UI/business dates use Vietnam time. See [architecture](docs/architecture.md).

- Customer: search, seats/holds, booking, mock payment, tickets, owned history,
  profile and eligible whole-booking cancellation.
- Operator admin: routes/fares, buses/trips, PHONE reservations and mock collection,
  crew/boarding/no-show, cancellation, reports, customers, maintenance and staff.
- Operator staff: read-only owned operational screens; no mutation privileges.
- System admin: operator onboarding/contact/status and staff visibility, with no
  operator operational bypass.

Layout: `frontend/`, `backend/`, `docs/`; `database/` is reserved support storage.

## Quick start (local demo)

Prerequisites: JDK 17, Maven 3.6.3+, Node >=22.12, npm and Docker Compose v2.
Check `java -version`, `mvn -version`, `node --version`. Set JAVA_HOME to your JDK
17 and prepend its `bin` to PATH if another Java version is selected.

1. Copy `.env.example` to `.env`; choose local MYSQL_PASSWORD and MYSQL_ROOT_PASSWORD.
   Run `docker compose up -d --wait` from the repository root.
2. Export DB_PASSWORD with the same application password. Spring does not load
   Compose `.env`. Export JWT_SECRET: Base64 of at least 32 cryptographically random
   bytes; generation instructions are in [deployment](docs/deployment.md).
3. From `backend/`: `mvn spring-boot:run '-Dspring-boot.run.profiles=dev,demo'`.
4. From `frontend/`: `npm ci`, then `npm run dev`. Open http://localhost:5173.
5. Check `Invoke-RestMethod http://localhost:8080/api/v1/health`, then search
   tomorrow's Bến xe TP. Hồ Chí Minh -> Bến xe Đà Lạt.

Dev database defaults: localhost:3307, schema busgo_db, user busgo. An existing
MySQL server needs explicit DB_URL/DB_USERNAME/DB_PASSWORD. Keep integration tests
on a **separate disposable schema**, never the application/demo database.

## Configuration and deployment

| Setting | Default / requirement |
| --- | --- |
| DB_URL | Dev: jdbc:mysql://localhost:3307/busgo_db?connectionTimeZone=UTC |
| DB_USERNAME / DB_PASSWORD | Dev user busgo; password always required |
| JWT_SECRET | Required Base64 >=32 random bytes; never commit |
| JWT_ACCESS_TTL / JWT_REFRESH_TTL | 1h / 7d; positive refresh must exceed access |
| SERVER_PORT | 8080 |
| API_PROXY_TARGET | Vite development proxy: http://localhost:8080 |
| VITE_API_BASE_URL | Public build setting: /api/v1 |

`dev` does not seed; `dev,demo` explicitly creates fictional fixtures without
resetting user data/passwords. `production` excludes demo and requires all DB
settings. Bootstrap SYSTEM_ADMIN once through environment settings, then disable
it. See [deployment and recovery](docs/deployment.md) for exact variables, JVM time,
Flyway validation, same-origin proxy/CORS, TLS, safe logs and backup requirements.

Compose runs MySQL only. Production-like frontend hosting needs SPA fallback and a
same-origin `/api/v1` proxy. `npm run preview` has no development API proxy.
Changing `.env` passwords does not change accounts in an initialized MySQL volume.
Never delete a volume to fix credentials or rewrite old migrations to hide drift.

## Demo accounts and rehearsal

Local demo only: operator.admin@anphu-demo.example / DemoOperator!2026 and
operator.staff@anphu-demo.example / DemoStaff!2026. Existing changed passwords are
preserved. Register a dedicated customer once; system admin uses one-time bootstrap.

[10–15 minute V1.5 demo](docs/v1.5-demo-script.md) includes account setup,
preparation, expected results and backup paths. [Seed behavior](docs/demo-data.md)
explains rolling trips, collision safety and create-only identity reuse.

An unpaid PHONE reservation is valid; PAY_ON_BOARD stays unpaid until collection.
Tickets issue after mock payment. Cancellation may create a mock refund and VOID
its tickets. No banking, Zalo integration or automatic messaging exists.

## Verification

From backend, Java 17 with DB variables pointing at an isolated MySQL schema:

```text
mvn test
mvn verify -Pmysql-integration
mvn package -DskipTests
```

From frontend: `npm test`, `npm run build`. From root: `git diff --check`.
Integration tests run migrations/schema validation, ownership/security, commerce,
seeder preservation and concurrency; connection errors are not skipped. Stop a
running jar before repackaging it on Windows, or run an ignored separate jar copy.
Browser tests under `frontend/tests/*-browser-check.mjs` require a live isolated
backend, Edge/Playwright and their documented environment settings; synthetic
fixtures are not real API acceptance evidence.

[M19 audit](docs/m19-audit.md) · [M19 results](docs/m19-verification.md) ·
[Release notes](docs/v1.5-release-notes.md) · [API contract](docs/api-contract.md) ·
[Roadmap](docs/product-roadmap.md) · [Development plan](docs/development-plan.md)

[Standalone desktop UI gallery](docs/v1.5-ui-gallery.html): 18 real application
screenshots embedded in one offline HTML file, grouped by customer/operator/admin.
