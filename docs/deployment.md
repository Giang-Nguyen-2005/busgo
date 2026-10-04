# V1.5 deployment and recovery

## Environments

| Mode | Profiles | Data and credentials |
| --- | --- | --- |
| DEV | `dev` | Explicit local DB password/JWT; no demo fixtures |
| DEMO | `dev,demo` | Dedicated demo schema; fictional create-only fixtures and documented demo passwords |
| Production-like | `production` | All DB settings required; strong unique secrets, no demo profile/accounts |

The production profile name does not provision infrastructure. It excludes demo
seeding. Compose only starts MySQL 8.4 bound to localhost:3307 with a persistent
volume. Do not remove the volume or reset an existing database to recover a demo.

## Local startup

1. Install Java 17, Maven 3.6.3+, Node >=22.12 and Docker Compose v2.
2. Copy root `.env.example` to `.env`; set MYSQL_PASSWORD and MYSQL_ROOT_PASSWORD.
   Run `docker compose up -d --wait` from the root. An existing server is supported
   with an explicit JDBC URL; use a separate schema for tests.
3. Export DB_PASSWORD (Compose `.env` is **not** loaded by Spring), DB_URL and
   DB_USERNAME if overriding dev defaults. Dev defaults are
   `jdbc:mysql://localhost:3307/busgo_db?connectionTimeZone=UTC` and `busgo`.
4. Generate JWT_SECRET once and keep it in an ignored local secret store:

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$jwtRandom.Dispose()
```

5. From backend run `mvn spring-boot:run '-Dspring-boot.run.profiles=dev,demo'`.
   From frontend run `npm ci` then `npm run dev`. Open http://localhost:5173.
6. `Invoke-RestMethod http://localhost:8080/api/v1/health` must return
   `{"data":{"status":"UP"}}`. Also query `/api/v1/locations` and search tomorrow's
   HCM -> Đà Lạt trips to verify DB-backed readiness and demo availability.

Flyway applies V1–V16; Hibernate `ddl-auto=validate` must pass. Do not rewrite old
migrations or run `repair` to hide drift. Before upgrading an existing schema,
back it up and rehearse on a copy. Before V10, check duplicate operator staff codes
with `GROUP BY operator_id,staff_code HAVING COUNT(*)>1`; resolve intentionally.
For a fresh-schema test, create a new isolated database and point DB_URL at it.

## Runtime settings

| Variable | Meaning |
| --- | --- |
| DB_URL / DB_USERNAME / DB_PASSWORD | JDBC connection; UTC connection timezone; required outside dev |
| JWT_SECRET | Base64 encoding of >=32 random bytes; never a VITE_* variable |
| JWT_ACCESS_TTL / JWT_REFRESH_TTL | Defaults 1h / 7d; positive refresh TTL must exceed access |
| SERVER_PORT | Backend port, default 8080 |
| API_PROXY_TARGET | Vite dev proxy target, default http://localhost:8080 |
| VITE_API_BASE_URL | Public frontend build value; default /api/v1 |
| WEB_PAYMENT_WINDOW / PHONE_PAYMENT_WINDOW | Defaults PT15M / PT30M; PAY_ON_BOARD exempt |

Use UTC JVM time for a new deployment and consistent JDBC settings. Review legacy
timestamp conventions before changing a JVM timezone on an existing database.
UI uses Vietnam time even in a non-Vietnam browser. Root `.env` configures Compose;
frontend overrides belong in `frontend/.env.local`; backend secrets are exported.

## System administrator

Bootstrap has no HTTP endpoint and is disabled by default. For one local start set
BUSGO_SYSTEM_ADMIN_BOOTSTRAP_ENABLED=true plus BUSGO_SYSTEM_ADMIN_FULL_NAME,
BUSGO_SYSTEM_ADMIN_EMAIL, BUSGO_SYSTEM_ADMIN_PHONE and BUSGO_SYSTEM_ADMIN_PASSWORD.
After verifying login, disable bootstrap and remove the password environment
variable. Existing incompatible identities fail safely, never receive privileges.
Passwords require at least 8 characters and at most 72 UTF-8 bytes.

## Production-like hosting

Run `mvn package -DskipTests` only after verification, then host the jar with Java
17 and least-privilege application credentials (including required migration DDL,
or arrange a controlled migration step). Serve `frontend/dist` behind TLS with
SPA fallback to index.html and proxy `/api/v1` to the backend on the **same origin**.
The backend currently has no cross-origin allowlist/CORS configuration; changing
VITE_API_BASE_URL alone does not enable cross-origin browser access. Vite preview
does not provide the development API proxy. Keep DB/admin ports private.

Do not log Authorization headers, passwords, request bodies, payment-token paths
(`/pay/*`, `/api/v1/public/payments/*`) or response bodies. Configure reverse-proxy
access-log redaction/exclusion for those paths; Spring's default application logs
are not a proxy log policy. Keep SQL bind logging off. Retain useful safe error
categories, health failures and migration diagnostics. No real secrets in Git.

Provide backups/restore rehearsal, process restart policy and resource monitoring.
Health is liveness only; a DB-backed probe is needed for readiness. No extra health
endpoint was added merely for this milestone. Real payment use is unsupported.

## Recovery without database editing

Restart MySQL with `docker compose up -d --wait`, then backend and frontend above.
Existing volume credentials remain authoritative even if `.env` was changed.
Same-day demo restart reuses trips; a new day proposes only missing rolling trips.
If a trip is full or has progressed, choose another future trip or create a new
one on an available bus with an active fare and non-overlapping crew. If maintenance
conflicts, choose another time/bus; never silently reassign an existing trip.
If demo identity collision prevents startup, use `dev` alone to inspect, or use a
new isolated demo schema. Never overwrite passwords or delete existing history.
