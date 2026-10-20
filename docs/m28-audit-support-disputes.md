# M28 — Audit, customer support and disputes

## Status and baseline

**COMPLETE — 20 October 2026.** Implementation and acceptance evidence are recorded below. Changes are ready for human review and remain unpublished.

Baseline was `main`, HEAD `15c866d`, containing M24B/V24 and the merged M17,
M18B, M20, M21, M23, M24A, M25 and M26 work. Created the requested
`feature/v2-audit-support-disputes` branch. The pre-existing untracked file
`tatus --short` is an old diff summary and was preserved. No commit, push, merge,
tag or Git identity change was performed.

## Architecture audit

- Spring Boot 3.5.7 / Java 17, MySQL, Flyway, JWT identity reload and method security.
  Existing `/api/v1` controllers use ApiResponse for objects and PagedResponse for
  lists. M28 follows those envelopes and uses typed Java records, never database maps.
- `OperatorContextService` requires a single active membership, an active user and
  an ACTIVE or SUSPENDED operator. It explicitly rejects SYSTEM_ADMIN. M28 reuses
  this context on every operator request. It adds no customer-directory permission.
- Booking ownership is WEB source plus authenticated account. Operator identity
  follows bookings → trips → operator_routes. Old/cancelled/completed bookings and
  suspended operators remain eligible for support. No PHONE identity or fake account.
- M17 booking status/refund history, M20 modification snapshots, M21 partial
  cancellation history, M24B voucher history, M25 operational history and M26
  governance history remain authoritative for their domains. No history is replaced
  and no events are invented for pre-M28 actions.
- M23 notifications are persisted within business transactions, with asynchronous
  email delivery. Its CUSTOMER/admin-only OPERATOR scopes do not support staff or
  platform inboxes. M28 uses a narrowly scoped support inbox with the same durable
  transaction/occurrence pattern. Existing M23 inboxes and delivery preferences are
  unchanged. Support messages do not produce email delivery jobs.
- Commerce retains trip → operator → booking/payment/inventory → voucher ordering.
  Support commands acquire the support-ticket row only, then append activity,
  audit and notifications. They never invoke commercial write services or lock
  existing trip/operator/booking records for update. Ticket creation serializes
  racing retries through the unique customer/request-key index, with no user-row
  exclusive lock ahead of commercial foreign-key checks.
  Foreign-key checks still acquire the database's normal shared parent locks.

## Role and visibility matrix

| Actor | Read scope | Permitted work | Prohibited work |
|---|---|---|---|
| CUSTOMER | Own WEB booking cases; PUBLIC activity only | Create, reply to active cases, accept a resolution, bounded reopen or appeal | Notes, assignment, priority, audit search, commerce mutation |
| OPERATOR_STAFF | Current member's operator cases; PUBLIC and INTERNAL activity | Claim unassigned ordinary cases; assigned ordinary workflow/resolution; escalate to admin; factual sensitive replies; internal notes | Another operator, admin-only category decisions, platform workflow, arbitrary assignments, generic audit |
| OPERATOR_ADMIN | Own operator cases, support metadata and operator audit | Assignment/unassignment, priority, ordinary/sensitive resolution, staff escalation review, justified platform referral | Foreign tenant, platform decisions, commercial bypass |
| SYSTEM_ADMIN | Cases with a persisted formal dispute; relevant event history; metadata-only audit search | Review, clarification, public findings, structured decision, return for operator processing | Non-escalated conversations, operator impersonation, booking/payment/refund/ticket/voucher writes |

The category is immutable after creation. TRIP_INFORMATION, PICKUP_DROPOFF,
TICKET_ACCESS, BAGGAGE and BOOKING_HELP are ordinary. BOOKING_CHANGE_REQUEST,
CANCELLATION_HELP, PAYMENT_QUESTION, REFUND_COMPLAINT, SERVICE_COMPLAINT and OTHER
are conservatively admin-sensitive. Sensitive cases begin at OPERATOR_ADMIN.
Staff can acknowledge sensitive cases, but cannot claim, assign, transition,
resolve or close them. Ordinary public staff replies require assignment to that
staff member. Internal notes are permitted for current operator personnel on active
cases; they never notify customers. The platform can read internal evidence only
after a formal dispute exists.

Assignment requires an active, non-deleted user with OPERATOR_STAFF role, exactly
one active membership, the same operator and no SYSTEM_ADMIN role. Sensitive or
escalated cases cannot be assigned to staff. Removing a membership immediately
denies future access and new notifications; an obsolete assignment does not prevent
an admin from resolving or unassigning the case. Assignee choices are bounded to
100 directory candidates; the write always revalidates eligibility.

## Ticket lifecycle

| Operation | Preconditions | Result |
|---|---|---|
| Create | Owned WEB booking, valid category/text, request key | OPEN; NONE or OPERATOR_ADMIN by category |
| Staff claim | Ordinary, NONE, active, unassigned | Assigned to self, IN_PROGRESS |
| Admin assign/release | Active, non-platform; same-operator eligible staff for assignment | Assignment changed; state retained |
| Transition | Active ordinary case assigned to staff, or authorized admin; non-platform | IN_PROGRESS or WAITING_CUSTOMER; no no-op transition |
| Customer reply | Active own case | Append PUBLIC message; WAITING_CUSTOMER becomes IN_PROGRESS |
| Resolve | Assigned ordinary staff case, or authorized admin; non-platform | RESOLVED; actor, admin-review flag and server time recorded |
| Accept | Customer's RESOLVED non-platform case | CLOSED; terminal |
| Reopen | Customer's RESOLVED case, within seven days, fewer than two previous reopens | OPEN; reopen count increments; prior resolution stays in activity |
| Staff escalation | Assigned ordinary active case | OPERATOR_ADMIN, IN_PROGRESS, assignment cleared |
| Customer appeal after staff resolution | RESOLVED, within seven days | OPERATOR_ADMIN, IN_PROGRESS |
| Customer appeal after admin resolution | RESOLVED, within seven days, no previous platform round | PLATFORM, IN_PROGRESS; real dispute created |
| Admin platform referral | Active non-platform case, reason, no previous platform round | PLATFORM, IN_PROGRESS; real dispute created |

Escalation is separate from progress. Replies, notes and all actions are append-only
activity entries. Each mutation requires the current `version`, a request key and a
reason/content of 1–4000 characters. Subject is 1–160 characters. Category, actor,
role, timestamps, ownership and visibility authority come from the server.

Closed cases cannot be reopened. Seven days is inclusive at the boundary, measured
with the backend Clock. Each case permits two reopens and one formal platform
round. An admin may return a dispute for more operator processing; the final
platform finding remains immutable and cannot be appealed repeatedly through M28.
The returned support case may still be resolved and accepted by the customer.

## Platform dispute workflow

Dispute identity is a unique child of the original support ticket. Customer,
operator and booking references are obtained from that ticket, not duplicated
or supplied again. All dispute API/frontend IDs deliberately mean **ticket ID**.

SUBMITTED → UNDER_REVIEW → WAITING_INFORMATION → UNDER_REVIEW; decisions require
UNDER_REVIEW. The process ends in CLOSED with one structured outcome:

- OPERATOR_RESOLUTION_UPHELD
- CUSTOMER_COMPLAINT_UPHELD
- ADDITIONAL_REVIEW_REQUIRED
- INSUFFICIENT_EVIDENCE

The first, second and fourth close the support case. ADDITIONAL_REVIEW_REQUIRED
closes the platform round and returns the case to OPERATOR_ADMIN / IN_PROGRESS.
Clarification requests and party replies are public activity. A decision stores its
outcome, rationale, actor and server timestamp on the dispute and appends an
immutable `DECISION_<OUTCOME>` activity event. There is no decision edit/delete API.

A complaint upheld is a support finding. It does not refund money or change a
balance, discount, ticket, seat, payment, commission or settlement. The platform UI
explains that any appropriate money operation must use the existing guarded
M17/M20/M21 workflow; it provides no commercial action buttons.

## Persistence, audit coverage and privacy

V25__audit_support_disputes.sql adds only:

| Table | Purpose |
|---|---|
| support_tickets | Case identity, trusted owners, immutable category, current state/assignment, bounded reopen counters and version |
| support_activity | Append-only messages, notes, assignment/status/escalation/resolution/decision history, visibility and actor snapshots |
| support_disputes | Unique platform round per original ticket; state and final finding |
| support_requests | Per-ticket/actor request-key receipt with semantic fingerprint |
| support_notifications | Durable, recipient/role/operator-scoped support workspace updates and read state |
| audit_events | Reusable metadata-only committed-success audit stream |

V1–V24 and historical commercial rows are unchanged. Foreign keys protect the
ticket/booking/users/operator links; unique keys protect creation retries, command
receipts and one dispute per ticket. CHECK constraints bound statuses, category,
priority, escalation, visibility, reopen count and outcome. Indexes support tenant,
customer and assigned queues, chronological activity, disputes and audit filters.

Persisted generic audit coverage from deployment onward:

- Every committed M28 case creation, message, internal note, claim, assignment,
  priority/status change, resolution/acceptance, reopen, escalation, review,
  clarification and platform decision.
- Existing authenticated M26 governance/invitation handling through its explicit
  history writer. Public application submission remains its existing domain history.
- M24B voucher creation, term edit, activation and pause through its history writer.

AuditService uses MANDATORY propagation. Actor ID/role snapshot, operator, resource
type/ID, event/action, SUCCESS and server timestamp are written in the caller's
transaction. JSON metadata is currently the empty allowlisted object; no message,
subject, request body, JWT, raw invitation token, password/hash, credential,
stack trace or exception text is included. Correlation ID is optional and currently
unset. Rejected attempts are not persisted as success events.

This is **selected workflow coverage**, not a global audit of all BusGo actions.
M17/M20/M21 commerce, M25 trip operations and voucher redemption retain their
existing histories rather than acquiring broad new audit hooks. No backfill.
Generic audit readers receive typed metadata with no content/JSON payload.
There are no audit/activity edit/delete business endpoints. Privileged direct SQL
and backup/retention administration are outside the application's immutability
guarantee; there is no cryptographic tamper-proof ledger.

## API contract

Customer root: `/api/v1/support`. Operator root:
`/api/v1/operator/support`. Platform root: `/api/v1/admin/disputes`.
The shared `/tickets` resource below is intentional, including under the platform
root, and differs from the suggested flat dispute paths in the milestone brief.

| Method / suffix | Contract |
|---|---|
| GET /tickets | Paged case queue; status; operator queue=mine/unassigned; platform disputeStatus=SUBMITTED/UNDER_REVIEW/WAITING_INFORMATION/CLOSED |
| POST /tickets | Customer root only: bookingId, category, subject, description, requestKey |
| GET /tickets/{ticketId} | Scoped ticket, booking reference, state/version and server-computed permitted action names |
| GET /tickets/{ticketId}/messages | Chronological visibility-filtered activity page |
| POST /tickets/{ticketId}/{action} | Validated scoped workflow action |
| GET /tickets/{ticketId}/dispute | SYSTEM_ADMIN only, existing formal dispute and finding |
| GET /dashboard | Scope-specific counters |
| GET /assignees | Operator-admin only, bounded eligible staff choices |
| GET /notifications | Paged support workspace inbox |
| POST /notifications/read-all | Current authorized recipient's support updates only |

Actions: messages, claim, assign, priority, transition, resolve, escalate, reopen,
accept-resolution, appeal, review, request-information, decision. Every command has
`{version,requestKey,content,internal?,assignedUserId?,status?,priority?,outcome?}`.
`internal` is allowed only for an authorized operator message. A null assignee
releases assignment. There is no category/actor/operator override. Unknown action
paths and invalid current-state/action combinations cannot bypass the policy.

GET `/api/v1/operator/audit-events` and `/api/v1/admin/audit-events` support resource
(SUPPORT/GOVERNANCE/VOUCHER), positive resourceId and pagination. Staff/customers
are denied. The operator filter always comes from membership, never a parameter.

List pagination: page 0–100000, size 1–100, defaults 0/20. Queues/audit/inboxes sort
created_at DESC, id DESC; conversations sort created_at ASC, id ASC. Lists return
`{data:[...],pagination:{page,size,totalElements,totalPages}}`. Objects use `{data}`.
No-store headers protect case, inbox and audit responses. Foreign/nonexistent case
IDs return the same 404. Invalid workflow/version/request-key reuse returns 409
INVALID_SUPPORT_ACTION; role-policy failures return 403 ACCESS_DENIED; malformed
DTOs/parameters use the existing validation envelope.

## Transactions and concurrent requests

Mutations lock the ticket before reading its current state and require a matching
version. The service uses READ_COMMITTED, preventing a prior membership read from
leaving a stale repeatable-read ticket snapshot after waiting for its lock.
Every successful command increments the version once. Same request key plus
same semantic content returns the current case without additional writes;
different semantic content conflicts. Fingerprints use length-delimited fields,
not ambiguous concatenation. Version is excluded from the semantic fingerprint so
a lost-response retry after refetch remains idempotent. New keys with stale
versions conflict. Failed/rolled-back transactions leave no receipt/success audit,
activity or notification. Reads create no events.

Creation uses the unique customer/request-key index to serialize inserts. A losing
insert catches only a duplicate-key conflict, re-reads the committed winner under
READ_COMMITTED and compares the fingerprint. Matching retries return the original
case; changed payloads conflict. No exclusive user-row lock precedes booking foreign
key checks, avoiding an inversion with commerce notification creation. This does not prohibit intentionally
creating separate cases with different keys. There is no global customer-directory
read, commerce override, background case-closing job or unbounded in-memory log.

## Notifications and operational counters

Support notifications persist in the business transaction and have unique
recipient/event occurrence keys based on ticket ID/version. Payloads contain only a
case reference and a generic update message, never note text. The actor is omitted
from their own notifications. Public updates notify the customer, current eligible
operator admins/assignee, and platform admins when formally escalated. Internal
notes generate no customer notifications. Read APIs revalidate current membership.

The support list's expandable updates panel is the inbox for all four roles,
including staff and platform admins. It supports paging, links and marking read.
No sockets, external provider, SMTP dependency or aggressive polling was added.
Reload/refocus and the detail's refresh action fetch current data. Existing M23
booking/trip inboxes remain separate and unchanged.

Dashboard status counts use current status across all time. Admin unassigned and
escalation counts are active OPEN/IN_PROGRESS cases only. OVERDUE is a deliberately
simple 24-hour activity-response target: OPEN/IN_PROGRESS with no update in the
last 24 hours; it excludes waiting/resolved/closed. This is an operational prompt,
not a contractual SLA. Staff status counts cover assigned-to-me; AVAILABLE covers
unassigned ordinary cases, and RESOLVED_BY_ME uses stored resolver/current terminal
state. Platform counts group actual dispute state.

## Frontend

- Customer `/support`, `/support/new`, `/support/tickets/:id`, plus a booking-detail
  support link with booking preselected. Owned bookings are paged choices.
- Operator `/operator/support`, `/operator/support/tickets/:id`,
  `/operator/audit-events`; staff navigation explicitly permits the support routes
  without opening other write workspaces.
- Platform `/admin/disputes`, `/admin/disputes/:id`, `/admin/audit-events`.

Vietnamese labels, UTC-offset API timestamps formatted for Vietnam, text-only
React rendering, accessible labels, loading/empty/error states, bounded paging and
responsive cards/forms use the existing application foundation. Escalations,
appeals and final decisions require a visible confirmation. Action controls follow
server eligibility. Query keys contain account ID and workspace; requests carry
abort signals, and the existing login/logout cache clear prevents cross-account
conversation reuse. No dangerouslySetInnerHTML, arbitrary upload or rich chat.

## Verification and browser evidence

All verification used the new isolated MySQL 9.2.0 instance at localhost:3328.
Neither development data nor previous milestone databases were reset.

| Check | Exact result / evidence |
|---|---|
| Initial M28 lifecycle/security run | 25/25 PASS; `.tools/m28-focused.log` |
| Final creation-lock/concurrency recheck | 31/31 PASS (25 lifecycle/security + 6 concurrency); `.tools/m28-lock-final.log` |
| Final dashboard/scope recheck | 26/26 PASS; `.tools/m28-dashboard-final.log` |
| Final M28 unique test cases | 33 PASS across latest affected runs: 26 support + 6 concurrency + 1 populated migration |
| Backend unit suite | 61/61 PASS; `.tools/m28-full-verify.log` |
| Broad integration run, once | 499 executed: 497 PASS, 1 failure and 1 error in migration test harnesses; same log |
| Affected core/schema and M24B recheck | CoreDatabaseIT 44/44 and M24BMigrationIT 1/1 PASS; `.tools/m28-affected-recheck.log` |
| Fresh migration | V1–V25 applied to empty busgo_m28_test, Hibernate validate initialized; `.tools/m28-focused.log` |
| Populated upgrade | M28MigrationIT 1/1 PASS; V24 baseline and V25 result compared across every legacy table; `.tools/m28-migration-final.log` |
| Final backend package | PASS; `.tools/m28-package-final.log` |
| Final frontend tests | 143/143 PASS; `.tools/m28-frontend-complete.log` |
| Final frontend production build | PASS; `.tools/m28-build-complete.log` |
| Migration immutability / whitespace | V1–V24 have no diff; `git diff --check` PASS |

The broad run is **not** represented as a green full-verify command. M24BMigrationIT
initially rejected the new isolated schema name; it now explicitly allows the two
named milestone test schemas and targets V24 for its V23→V24 responsibility. The
new M28 restore harness initially attempted generated-column insertion, then hit
the existing refund trigger when restoring final REFUNDED payment snapshots. It
now excludes generated columns, inserts refunds in their original PAID context,
and restores final payment statuses **inside the new test copy**, before comparing
the complete V24 snapshot and applying V25. Source rows are unchanged. The upgraded
fixture includes a redeemed voucher, paid/cancelled discounted booking, simulated
refund, voided ticket, notifications, inventory and retained domain histories.

After repairs, only affected tests were rerun. The final focused suite additionally
covers creation/cancellation concurrency and conflicting creation retries; dashboard
tests verify absent-state zeros and tenant/assignee separation. There is no second
full-suite claim. Tests assert complete commerce-row equality around a platform
decision, audit/notification rollback, idempotent occurrences, stale versions,
category restrictions, ownership, removed membership, suspension, note privacy,
validation, HTTP guards and metadata-only audit visibility.

Browser acceptance used fictional accounts, demo supply and one simulated paid
WEB booking (#1, trip #7) in busgo_m28_acceptance, API 8098 / frontend 5188:

| Role / flow | Observed browser result |
|---|---|
| CUSTOMER | Created BAGGAGE case #1 from owned booking; literal HTML payload rendered as text with zero image elements; received staff reply; private note absent (zero matching elements); accepted resolution and case CLOSED |
| OPERATOR_STAFF | Claimed case #1; appended internal note; sent public reply; resolved ordinary case; observed assignment/category-aware controls |
| OPERATOR_ADMIN | Prioritized sensitive REFUND_COMPLAINT case #2 HIGH; recorded reasoned resolution; inspected scoped queue, dashboard and paged support updates |
| CUSTOMER appeal | Created sensitive case #2; appealed admin resolution; original case became PLATFORM with a real dispute |
| SYSTEM_ADMIN | Queue contained escalated case #2; ordinary case #1 absent; reviewed evidence/reason, requested clarification, resumed review and recorded CUSTOMER_COMPLAINT_UPHELD with rationale; case/dispute CLOSED |
| Final decision visibility | Customer saw the public decision in history with zero mutation forms on the closed case |
| Audit | Platform resource=SUPPORT/resourceId=2 search returned chronological metadata only, with pagination and no conversation body |
| Mobile | Customer create/detail, staff assigned queue/inbox, operator queue/inbox and platform decision form at 390×844: document width 375, no horizontal overflow |
| Console | Fresh final form inspection: zero errors, one existing React Router HydrateFallback warning; earlier inspection also recorded three hydration warning occurrences |

Final-code acceptance additionally created case #3, assigned eligible staff through
the operator-admin form, checked the staff assigned-to-me queue and new assignment
notice, and verified explicit zero dashboard states. The console inspection then
exposed a real booking-selector contract mismatch (`id` versus `bookingId`). It
was corrected by reusing the existing BookingListItem type and adding an automated
rendering regression. Browser creation of case #4 from the dropdown **without a
preselected booking URL** passed. Fresh DOM inspection confirmed selected booking
value `1`; the new form had zero console errors after the fix. Earlier React list-key
errors are historical fixed failures, not ignored acceptance evidence.

The original native confirmation dialog stalled browser tooling; it was replaced
with an accessible required inline checkbox. Final platform decision submission
with that confirmation passed. An acceptance-process/browser interruption was
recovered by restarting only the identified isolated services and signing in again.
This is recorded as harness history, not omitted or counted as a passing action.

Screenshots: `.tools/m28-platform-decision-1440.png`,
`.tools/m28-operator-support-390.png`, `.tools/m28-customer-decision-390.png`,
`.tools/m28-staff-assignment-390.png`, `.tools/m28-customer-create-390.png`.
Viewport overrides were reset. API-based integration assertions are not counted
as browser interaction evidence. No real email/gateway transaction was attempted.

## Limits and future work

No PHONE identity flow, anonymous tickets, attachment storage, sockets, chatbot,
external messaging, settlement report or UI redesign. Staff choice lists currently
cover at most 100 candidates. Offset paging is deterministic for a fixed dataset;
it is not a cross-request snapshot. There is one platform round, with operator
follow-up rather than unlimited appeals. Audit access is metadata-only; export,
retention policy and production volume/EXPLAIN tuning remain future work.

M22 must retain existing payment-provider and idempotency guards. M27 owns any
future commission/settlement consequence. Neither milestone is implemented by
M28, and a support decision remains incapable of monetary execution.

## Final Git and human-review handoff

Branch: feature/v2-audit-support-disputes. HEAD: 15c866d. All M28 files are
uncommitted; the pre-existing untracked tatus --short is preserved and unrelated.
No commit, push, merge, tag, Git identity change or historical migration rewrite.
Human review can begin with SupportService policy/locking, V25, the explicit M26/
M24B hooks, and M28 test classes, then the shared role-aware support UI.

Modified/new task files (excluding ignored local evidence and the unrelated file):



- `backend/src/main/java/com/busgo/audit/AuditService.java`
- `backend/src/main/java/com/busgo/common/security/SecurityConfig.java`
- `backend/src/main/java/com/busgo/onboarding/OnboardingService.java`
- `backend/src/main/java/com/busgo/promotion/VoucherService.java`
- `backend/src/main/java/com/busgo/support/AuditController.java`
- `backend/src/main/java/com/busgo/support/SupportController.java`
- `backend/src/main/java/com/busgo/support/SupportDtos.java`
- `backend/src/main/java/com/busgo/support/SupportService.java`
- `backend/src/main/resources/db/migration/V25__audit_support_disputes.sql`
- `backend/src/test/java/com/busgo/CoreDatabaseIT.java`
- `backend/src/test/java/com/busgo/M24BMigrationIT.java`
- `backend/src/test/java/com/busgo/M28ConcurrencyIT.java`
- `backend/src/test/java/com/busgo/M28MigrationIT.java`
- `backend/src/test/java/com/busgo/M28SupportIT.java`
- `docs/api-contract.md`
- `docs/database-design.md`
- `docs/development-plan.md`
- `docs/m28-audit-support-disputes.md`
- `docs/product-roadmap.md`
- `frontend/src/features/auth/access.ts`
- `frontend/src/features/support/Support.tsx`
- `frontend/src/features/support/support.css`
- `frontend/src/features/support/supportModel.ts`
- `frontend/src/layouts/AdminLayout.tsx`
- `frontend/src/layouts/CustomerLayout.tsx`
- `frontend/src/layouts/OperatorLayout.tsx`
- `frontend/src/pages/BookingDetailPage.tsx`
- `frontend/src/routes/router.tsx`
- `frontend/tests/support.test.mjs`

