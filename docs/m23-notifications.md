# M23 — Notifications

## Baseline audit (before implementation)

- Users authenticate with JWT; persisted roles are CUSTOMER, OPERATOR_ADMIN, OPERATOR_STAFF and SYSTEM_ADMIN. Customer bookings reference users; PHONE bookings have no customer account.
- Operator identity uses active operator_staff membership plus persisted role. Existing context service rejects SYSTEM_ADMIN and ambiguous memberships. M23 uses admin-only operator inboxes.
- Booking creation reserves inventory in PENDING. PaymentTicketService commits payment, CONFIRMED status, tickets and history together. Repeated confirmation returns the existing payment. PHONE PAY_ON_BOARD is an eligible reservation before collection, without changing its PENDING semantics.
- M20 completion updates current booking journey/items, replaces tickets and records money/history in one transaction. Quote/hold creation is not completion.
- M21 execution flags selected items cancelled, adjusts total/refunds and records history in one transaction. Full cancellation releases inventory, voids tickets and records status/refund history.
- Scheduled jobs currently expire holds, unpaid bookings and modification holds. Spring scheduling is enabled. JPA uses UTC storage, JpaJdbcTime binds JDBC to those timestamps, and BusGoTime defines Asia/Ho_Chi_Minh for presentation.
- CustomerLayout and OperatorLayout provide existing header/navigation. No mail sender or email configuration exists. Current payment logic has no authoritative persisted FAILED transition; M23 must not invent one.

## Architecture and triggers

V20__notifications.sql adds notifications, notification_deliveries and notification_preferences. No broker, gateway, or unrelated UI changes. V1–V19 remain immutable.

NotificationService.record joins the caller's transaction with MANDATORY propagation. Durable notification content and email outbox rows commit with the business action; rollback removes both. No SMTP call occurs in booking/payment/modification/cancellation transactions. Database persistence failures can still reject a transaction, as with any durable outbox; SMTP failures cannot.

Events: BOOKING_CONFIRMED, PAYMENT_SUCCEEDED, PAYMENT_FAILED, BOOKING_MODIFIED, PARTIAL_CANCELLATION_COMPLETED, BOOKING_CANCELLED, TRIP_REMINDER_24H, TRIP_REMINDER_2H. Payment confirmation emits confirmation and payment success only on the initial successful transition. PHONE PAY_ON_BOARD creation emits a reservation confirmation while preserving PENDING; later collection does not repeat confirmation. PAYMENT_FAILED is available for future authoritative payment processing but has no invented mock failure trigger.

M20 emits only after completion and flush, keyed by modification ID; a successful additional collection also emits PAYMENT_SUCCEEDED for that payment. M21 emits only after execution and flush, keyed by cancellation ID. Quote/create attempts emit nothing. Full cancellation (including authoritative expiry cancellation) emits only on its successful state change. Inventory, tickets, accounting and histories retain their original semantics.

Future M22 can call record with the authoritative payment occurrence in its transaction. Future event types/categories can be added without a migration restricting event names. M25 behavior is not implemented.

## Inbox and security

Customer header bell polls every 15 seconds, displays five recent items and unread count, and links to paginated history. Inbox supports mark-one, mark-all, safe booking navigation, delivery history/retry and email preferences. Requests and cache keys use the existing authenticated session. Operator admin gets a corresponding bell/history for active single-operator membership; staff and SYSTEM_ADMIN are excluded. Operator scope includes both user and current operator, preventing access after membership changes. Customer scope rejects mixed operator/system-admin identities.

PHONE bookings never create accounts or customer inbox records. Valid booking contact email may get an ACCOUNTLESS durable event with an email delivery. That event has no public API or navigation target. Payment-link tokens grant no access to notification APIs. Operator admin receives basic booking/change/cancellation events only; operator email is out of scope.

List size defaults to 20, maximum 100, ordered by created time and ID. Ownership applies to mark-read, delivery history and retry. Destination addresses and SMTP exception text are not exposed in the API. Vietnamese plain text includes booking code, current journey/pickup, Vietnam pickup time, active seats, current amount and status. No tokens or secrets are included.

## Optional email

Export MAIL_ENABLED=true, MAIL_HOST, MAIL_PORT (587 default), MAIL_FROM, and optionally MAIL_USERNAME / MAIL_PASSWORD. MAIL_STARTTLS defaults true and is required when enabled; disable only for a trusted local test SMTP server. Credentials stay in environment/secret stores, never database rows. No SMTP connection occurs at startup. Missing/disabled configuration creates no email delivery and leaves in-app behavior intact. Existing queued records wait while email is disabled.

NotificationDeliveryWorker runs every minute, at most 20 rows per run. Each send uses an independent transaction, row locking and SKIP LOCKED. Timeout is five seconds per connection/read/write operation. SENT rows are never selected again. Failures persist only a generic safe summary and retry the same row, at most five attempts, with 1/2/4/8/16-minute backoff. A recipient can make an eligible FAILED delivery due now, without resetting attempts or resending SENT rows. Pending delivery checks current email preference and becomes SKIPPED if disabled.

Customer defaults enable booking/payment, booking change/cancellation and trip reminder email categories. Preferences affect email only; transactional in-app messages always remain. Accountless bookings have no account preference UI.

LIVE EMAIL DELIVERY = NOT CONFIGURED. Automated tests use a mocked sender and never call external SMTP.

## Reminders and duplicates

Reminder scan runs every five minutes, at most 100 candidates per threshold per run, in a 15-minute window beginning at pickup minus 24h/2h. Booking creation must precede that threshold; historical 24h reminders are not sent to late bookings. Current CONFIRMED bookings and PHONE PENDING PAY_ON_BOARD reservations with active items on a SCHEDULED/BOARDING/DEPARTED trip qualify. Cancelled bookings do not. Stored UTC timestamps use JpaJdbcTime; customer text displays Asia/Ho_Chi_Minh.

Each booking is reloaded under a lock and rechecked before recording. M20's current trip/pickup and M21's remaining seats/totals drive reminder content. One reminder per booking/recipient/threshold maximum, even after modification; scheduler retries reuse the same event key. No catch-up reminders after the 15-minute window, so outages may cause missed reminders.

Unique recipient_key + event_key prevents logical duplicates. Event keys include booking, type and occurrence. A booking-row lock serializes creation, backed by the unique constraint. Delivery uniqueness is notification + channel. Email has at-least-once delivery semantics: a crash after SMTP acceptance and before SENT commit may cause a duplicate email. SMTP cannot participate in the database transaction; exactly-once remote delivery is not claimed.

## Verification

Executed 2026-10-06 on branch `feature/v2-notifications`:

| Check | Result |
| --- | --- |
| Backend compile | Passed |
| Focused M23 | 26 MySQL tests across focused runs + 6 mail/category unit tests passed (32 total) |
| Initial hook regression batch | 87 passed, including the then-23 M23 tests and 64 M9/M16A/M17/M20/M21 regressions |
| Final `mvn test` | 56 passed, zero failures/errors/skips |
| Final `mvn verify -Pmysql-integration` (run once) | 330 executed: 287 passed, 1 failure and 42 errors initially |
| Repair verification | 100 affected tests passed, zero failures/errors/skips |
| Additional public payment-link security test | 1 passed; valid public context token gets 401 on all notification namespaces |
| `mvn package -DskipTests` | Passed |
| Final `npm test` | 133 passed, zero failures/skips |
| Final frontend build | Passed; repeated only after the browser-proven mobile panel clipping fix |
| `git diff --check` | Passed |
| Immutable migration comparison | V1–V19 all unchanged; only V20 added |

The full integration failures were fixture infrastructure issues: seven concurrency classes tried to delete bookings before their new notification children, and the demo suite's global inventory assertion encountered committed notification test fixtures. NotificationTestCleanup now removes only explicitly owned fixture children; M23 @AfterTransaction cleanup removes committed fixtures/users. CoreDatabaseIT expectations now include V20 and the three tables. The repair run used a new empty schema and covered all affected classes, M8 fixture cleanup, and the two added BOARDING/DEPARTED selected-pickup regressions. After it completed, the repair schema had zero bookings, notifications and trips. The full 330-test suite was not rerun, in accordance with the requested verification budget; this report does not claim a second clean full-suite run.

Evidence logs (ignored local artifacts): `.tools/m23-focused-regressions.log`, `.tools/m23-full-unit.log`, `.tools/m23-full-verify.log`, `.tools/m23-affected-recheck.log`, `.tools/m23-public-link-focused.log`, `.tools/m23-full-package.log`, `.tools/m23-full-frontend-test.log`, `.tools/m23-mobile-fix-build.log`.

### Fresh database/startup

Exact server response to SELECT VERSION(): **9.2.0**, MySQL Community Server. MySQL 8.4 was not tested. Dedicated local server binds 127.0.0.1:3323. Packaged JAR started against the empty `busgo_m23_acceptance` schema: Flyway applied all 20 migrations to v20, Hibernate initialized with configured ddl-auto=validate, and application started successfully on 8088. Startup log: `.tools/m23-startup.log`. The SMTP-disabled packaged startup is part of the optional-email acceptance evidence.

### Limited browser acceptance

- A: Local customer booking payment completed through UI; badge went 0 → 2, recent panel showed confirmation/payment, mark-one changed 2 → 1, and opening the other notification safely navigated to the booking and marked it read.
- B: M20 changed L03 → L06 through UI; one BOOKING_MODIFIED event. Retrying its completed endpoint returned success without adding an event.
- C: M21 cancelled L04 through UI; one PARTIAL_CANCELLATION_COMPLETED event, remaining seats L06/L05 and total 1,300,000 VND.
- D: Local fixture moved the current pickup into the 2h window; repeated 5-second acceptance scheduler scans produced exactly one TRIP_REMINDER_2H. Reminder used L06/L05, omitted L04, and displayed the current pickup time/total.
- E: UI saved reminder email disabled; in-app reminder remained and delivery history showed no email. Configured fake-mail integration test separately proves preference suppression with email enabled; the acceptance app has SMTP disabled.
- Ownership: foreign mark-read returned 404; unauthenticated inbox returned 401. Tests additionally cover delivery/retry ownership, operator isolation, staff/system-admin denial and bounded history. A focused MySQL/MockMvc test verified a valid accountless public payment-link token opens public context but gets 401 for customer inbox, preferences and operator inbox.
- 1440px desktop and 390px mobile inbox/panel checked visually; no horizontal overflow. Mobile mark-all reduced unread count to zero. Mobile panel clipping was corrected within M23 CSS.
- Operator admin UI showed three relevant booking/modified/partial cancellation notifications; no payment/reminder broadcast to operator. No console errors observed. Existing React Router HydrateFallback warning was observed and left outside M23 scope.

Screenshots/evidence: `.tools/m23-browser/1440-notifications.png`, `390-notifications.png`, `390-panel.png`, `390-all-read.png`, `1440-operator.png`, `evidence.json`.

Known limits: real SMTP delivery remains NOT CONFIGURED; SMTP crash can cause duplicate remote email; outages beyond reminder windows can miss reminders; PAYMENT_FAILED has no authoritative current producer; operator staff email/inbox remains excluded. No gateway, SMS, push, marketing or live-trip functionality is included.

All implementation and documentation changes remain uncommitted on `feature/v2-notifications`. **NO COMMIT. NO PUSH. NO TAG.**
