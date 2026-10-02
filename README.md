# BusGo V1

Vietnamese bus ticket booking and management application. M0–M14B are delivered;
M15 P0 hardens demo seeding, local startup and release verification.

## Delivered V1

- Customer: registration/login/profile, trip search, snapshot seat maps, temporary
  holds, server-priced bookings, mock QR payment, one electronic ticket per seat,
  and owned booking history.
- Operator admin: fleet/routes/fares, trip creation, bookings, passenger manifest,
  physical seat board, segment occupancy, forward trip operations and staff management.
- Operator staff: read-only owned trips, bookings, manifests, seats, occupancy and
  bus types. Fleet/routes/staff management and all mutations require operator admin.
- System admin: separate `/admin` workspace, operator onboarding/contact/status
  management and read-only staff visibility. It has no operator-context bypass.
- Inactive operators stop new commerce and operator access; historical customer
  bookings/tickets remain readable. Suspension does not cancel/refund anything.

Segment inventory, immutable trip snapshots, role/membership isolation and Flyway
are foundations for later work. Real gateways, refunds/cancellation redesign,
check-in, analytics, notifications and global catalogue editing are deferred after V1.
See [development plan](docs/development-plan.md) and [API contract](docs/api-contract.md).

## Prerequisites and Java 17

JDK 17, Maven 3.6.3+, Node.js >=22.12 and npm are required. Docker Compose v2 is
needed only for the Docker database path. A Java 8 runtime on PATH is insufficient.

PowerShell, replacing the path with your installed JDK:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
mvn -version
node --version
```

CMD equivalent:

```bat
set "JAVA_HOME=C:\path\to\jdk-17"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -version
mvn -version
```

Both Java and Maven must report Java 17. This checkout may have an ignored local
JDK under `.tools/jdk17/`; that directory is not a distributed dependency.

## Database: choose one local path

### A. Docker Compose MySQL 8.4

Copy `.env.example` to root `.env`, set nonempty `MYSQL_PASSWORD` and
`MYSQL_ROOT_PASSWORD`, then run from the repository root:

```powershell
docker compose up -d --wait
```

Default database/user: `busgo_db` / `busgo`; host: localhost:3307. The named volume
retains data after `docker compose down`. Changing `.env` passwords does not update
accounts in an already initialized volume. Do not delete a volume to fix credentials
when it contains development history. Configure the correct existing credentials.

### B. Existing local MySQL

Use an empty dedicated BusGo schema and an application user permitted to apply
Flyway DDL. Configure its actual host/port/schema/user/password explicitly:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3307/busgo_db?connectionTimeZone=UTC'
$env:DB_USERNAME = 'busgo'
$env:DB_PASSWORD = '<your local database password>'
```

MySQL 8.4 is the Compose reference environment. An existing server on port 3306
requires a matching DB_URL; merely having a MySQL service running is insufficient.

Flyway owns V1–V11; Hibernate uses `ddl-auto=validate`. Keep old migration files
unchanged. Existing schemas require matching Flyway history/checksums. Before
upgrading a pre-V10 database, run this read-only duplicate check:

```sql
SELECT operator_id, staff_code, COUNT(*) AS duplicates
FROM operator_staff
GROUP BY operator_id, staff_code
HAVING COUNT(*) > 1;
```

Resolve collisions deliberately using the database's collation; never blindly
delete staff or use Flyway repair to hide an incompatible schema.

## Backend environment and startup

Spring Boot/Maven do **not** automatically load the root Compose `.env`.
Export `DB_PASSWORD` separately (same value as Compose MYSQL_PASSWORD).
With `dev`, DB_URL and DB_USERNAME default to the values above; without `dev`,
all three DB variables are required. Never commit real credentials.

Generate a JWT secret in PowerShell, once per local environment:

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRandom.GetBytes($jwtBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$jwtRandom.Dispose()
```

JWT_SECRET must be Base64 of at least 32 cryptographically random bytes. Keep the
same secret across local restarts to preserve token validity; save it only in an
ignored local secret store and re-export it in new shells. Optional JWT_ACCESS_TTL
and JWT_REFRESH_TTL default to `1h` / `7d`; positive refresh must exceed access.

From `backend/` (quoted Maven arguments also work in PowerShell):

```powershell
mvn spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Explicit local demo startup:

```powershell
mvn spring-boot:run '-Dspring-boot.run.profiles=dev,demo'
```

`dev` does not activate demo. Demo is excluded with `prod`/`production`; never
enable it on a real deployment. Its create-only fixtures preserve existing state
and skip occupied/inactive slots. Identity collisions fail clearly and roll back
the seed transaction; use `dev` alone to inspect them. The old reset flag is
disabled and cannot delete trips. See [demo data](docs/demo-data.md).

Backend defaults to 8080 (`SERVER_PORT` overrides). Check:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health
```

Expected: `{"data":{"status":"UP"}}`. This is liveness; also check a database-backed
location/search endpoint before rehearsal.

## SYSTEM_ADMIN: provision once

Bootstrap is disabled by default and has no HTTP endpoint. Export these five
variables for one start, with credentials kept locally outside Git:

```powershell
$env:BUSGO_SYSTEM_ADMIN_BOOTSTRAP_ENABLED = 'true'
$env:BUSGO_SYSTEM_ADMIN_FULL_NAME = 'Platform Administrator'
$env:BUSGO_SYSTEM_ADMIN_EMAIL = 'admin@example.com'
$env:BUSGO_SYSTEM_ADMIN_PHONE = '0900000000'
$env:BUSGO_SYSTEM_ADMIN_PASSWORD = '<your local secret>'
```

Start normally, verify login, then disable bootstrap and remove its password
variable. Exact identity/sole SYSTEM_ADMIN role/status/password repeats are a no-op;
incompatible existing accounts fail rather than receiving privileges. Demo never
seeds SYSTEM_ADMIN. Normal password policy: nonblank, >=8 characters, <=72 UTF-8 bytes.

```powershell
$env:BUSGO_SYSTEM_ADMIN_BOOTSTRAP_ENABLED = 'false'
Remove-Item Env:BUSGO_SYSTEM_ADMIN_PASSWORD
```

## Frontend

From `frontend/`: `npm ci`, then `npm run dev`. Open http://localhost:5173.
Vite uses strict port 5173 and proxies `/api` to localhost:8080. Optional
`frontend/.env.local` overrides are shown in `.env.example`; changing backend port
also requires API_PROXY_TARGET. VITE_* values are public, never secrets.

Production hosting must serve index.html for SPA routes and proxy `/api/v1` to the
backend, or configure a suitable API URL/origin policy. `npm run preview` does not
configure the repository's development API proxy.

Authentication is tab-scoped session storage with shared refresh rotation and one
request retry. Logout clears local session/hold/cache; password changes revoke
refresh tokens. Do not retry an uncertain booking creation automatically: check
history first. Mock payment is idempotent; it performs no banking transaction.

Operator date filtering uses Vietnam `businessDate`; legacy API `date` retains
UTC semantics. Supplying both is rejected. Display times use Asia/Ho_Chi_Minh.

JDBC operator/admin timestamp views use the same UTC Calendar binding as JPA,
including booking date-range filters. Keep JVM timezone consistent when reusing
a database; do not silently switch a legacy database's timestamp convention.
Stop a running `java -jar` instance before packaging on Windows to release its
jar-file lock.

## Verification and final demo

Backend, Java 17, dedicated integration database (not the demo/development DB):

```powershell
mvn test
mvn verify -Pmysql-integration
mvn package -DskipTests
```

Default tests exclude datasource/JPA/Flyway. The integration profile runs real
MySQL migrations, schema validation, commerce/security/concurrency contracts and
DB-backed seeder preservation tests; connection failures are not silently skipped.
Ordinary test fixtures roll back; concurrency tests clean only their own fixtures.
The seeder IT requires its reserved demo keys to be absent before its first-seed test.

Frontend: `npm test`, `npm run build`. Repository: `git diff --check`.
Retained `frontend/tests/fixtures/` and `dispatch-preview.html` are synthetic
verification surfaces, not production entry points or live API proof.

[Final 6–8 minute demo](docs/final-demo.md) · [M15 verification](docs/m15-verification.md)
Historical verification remains in `docs/m10-verification.md` through
`docs/m14b-verification.md`.

Layout: `backend/` Spring Boot; `frontend/` React/TypeScript/Vite;
`docs/` contracts/setup/verification; `database/` reserved support directory.
