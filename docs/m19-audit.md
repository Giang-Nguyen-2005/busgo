# M19 product audit — 2026-10-04

Baseline: clean `feature/management-upgrade`, M16A–M18C delivered. This audit
precedes source changes. No new business module or schema is required.

| Area | Finding / targeted action |
| --- | --- |
| A Customer | Booking PENDING is translated as payment pending, mixing two states. Share booking labels; preserve separate payment status and invalid-ticket notices. |
| B Operator | Assisted form has no explicit success confirmation, combines contact/payment, and gives little explanation of unpaid reservations. Add compact progress, explanations and successful-reservation context. |
| C Admin | Existing headings, confirmation consequences, mobile rows and request states are adequate. Reuse status presentation and expose route loading consistently. |
| D Terminology | Operations says “Chờ xác nhận” for an existing reservation; reports/customer history have separate translations. Standardize reservation/payment and simulated-money wording. |
| E Duplication | Status maps and operations errors duplicate shared helpers. Consolidate domain-specific labels without merging DTOs or redesigning layouts. |
| F Request states | Important pages generally distinguish pending/error/empty and disclose stale refreshes. Maintenance bus selector omits its loading/error state. Lazy navigation has no visible progress. |
| G Responsive | Existing table-local scrolling/mobile cards cover trips, bookings, fleet, customers and admin. Validate 390/820/1440 and constrain assisted fieldsets/long payment links. |
| H Accessibility | Shared Field associates labels/hints/errors; tables use scoped headers and most controls have names. Native trip dialogs already manage modal focus; cancellation uses a custom dialog and needs focus containment/restoration. Improve progress and navigation announcements. |
| I Performance | Maintenance readiness reads records and trips per bus, then repeats SQL per assigned trip; dashboard repeats trips again. Batch read-only page data, preserve all mutation locks, measure query counts and local latency. Customer/report queries aggregate at booking grain and use bounded output/filter ranges, but large lifetime cohorts remain a scale limitation. |
| J Demo | Create-only seed provisions operators, staff, catalogue, rolling trips and crew, but no customer/transaction/maintenance story. Provide reproducible rehearsal steps using normal APIs/UI without resets or fabricated historical records. |
| K Deployment | Compose is MySQL only; no production reverse proxy/TLS deployment supplied. Same-origin API hosting is required by current security configuration. Document explicit DEV/DEMO/production-like settings and secrets. |
| L Documentation | README still describes V1 and defers delivered features; API contract retains obsolete proposed confirmation/revenue endpoints. Mark historical proposals and document actual V1.5 surface. |

Security review: operator context rejects SYSTEM_ADMIN and requires one active
membership; reads/mutations resolve owned objects. Customer directory groups exact
account IDs or individual offline booking snapshots, never phones/emails. Reports
aggregate payments/refunds independently; attendance is recorded-cohort data.
Maintenance locks and cancellation/payment lock order are retained. Public payment
DTO is minimal and uses an independent anonymous client. No file-upload API found.
SQL filters use bound values and sort allowlists. Unexpected error responses and
logs omit exception messages/request bodies; no token/password logging found.

Session finding: outgoing/retried requests are session-version checked, logout
clears holds/cache, but successful late responses are accepted. Reject those before
mutation success callbacks can repopulate a replacement account's cache.

Bundle finding: management routes already lazy-load. AuthPage is eager and imports
Zod/forms on every initial route; defer login/register modules and measure build.

Verification and remaining limitations belong in [M19 verification](m19-verification.md).
