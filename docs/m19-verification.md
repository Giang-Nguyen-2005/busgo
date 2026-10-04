# M19 — V1.5 verification and final report

Date: 2026-10-04 (Vietnam). Branch: `feature/management-upgrade`.
Work continued from the existing working tree after the usage-limit interruption;
completed implementation was preserved. No commit or push was made.

## 1. Product audit

[Pre-change audit](m19-audit.md) covers customer/operator/admin UX, terminology,
duplicate patterns, request states, responsive/accessibility, performance, demo,
deployment and documentation drift. No major business module or schema addition.

## 2. Critical fixes

Successful HTTP responses now reject a replaced session before callbacks can write
old account data. Fleet planning reads are batched without changing mutation locks.
Cancellation dialog has keyboard containment, Escape and opener focus restoration.

## 3. Terminology changes

Booking PENDING is “Đã giữ chỗ”; payment PENDING is independently “Chưa thanh toán”.
Shared domain labels/tones cover booking, payment, ticket, trip, attendance, bus,
maintenance, operator and user states. Existing wrappers and filters reuse labels.
UI simulation wording is “mô phỏng”; no backend enums were renamed.

## 4. Assisted booking UX

Compact numbered progress, separate contact/payment explanation, safe price fallback,
empty-trip feedback and explicit successful reservation notice. PAY_ON_BOARD explains
that reservation exists before collection; QR/link explains manual delivery and no
bank transfer. Copy action explicitly identifies the simulated payment link.

## 5. Customer UX

Shared reservation labels, simulation wording and lazy navigation feedback. Real UI
registration -> home search -> seat selection/hold -> checkout -> payment -> ticket
-> owned history -> paid cancellation verified at all three widths. Existing hold
expiry/back-navigation and VOID ticket behavior retained; M17 regression covers
unpaid/paid cancellation and payment expiry.

## 6. Operator UX

Existing visual hierarchy, compact rows/cards, filters, local table scrolling and
pagination retained. Shared status/error presentation and assisted flow clarified.
Browser acceptance covers dashboard, trips/workspace, PHONE creation/collection,
crew, attendance, reports, customers, fleet and maintenance. A browser regression
found one old collection button in boarding; it now also says “thu tiền mô phỏng”.

## 7. Admin UX

Shared account/operator status badges and route loading feedback. System-admin login
and populated operator directory verified at 390/820/1440. Existing consequential
activation/deactivation confirmations and operator-context exclusion retained.

## 8. Error handling

Operations now uses shared Vietnamese domain-error mapping, including fleet conflict,
seat/hold, cancellation, crew, payment and access errors. Unknown errors use a safe
fallback; API exception messages/SQL are not rendered. No correlation identifier
exists in the current response contract; none was invented. Tests verify fallback
does not expose an injected internal SQL/password message.

## 9. Loading/empty/failure states

Maintenance bus choices expose loading/error/retry rather than a misleading empty
selector. Route navigation announces loading. Important screens retain explicit
pending/error/empty/populated handling and disclose stale refreshes. Reports browser
regression verifies incomplete inventory suppresses percentages; failed queries do
not become zero totals. Gallery capture explicitly waits for report content.

## 10. Auth/session hardening

Logout/account replacement already cleared tokens, holds and query cache. M19 adds
successful-response session-version validation. Unit regression covers late GET and
POST success. Live Edge test delayed a **real successful** operator booking response,
logged out through the UI, logged in as the other operator in the same tab, then
released it. Result: CanceledError, success callback did not repopulate cache, old
booking code absent from actual React Query data and rendered history. Switching
again to a new customer left no operator queries and showed empty owned history.
The hold key was cleared. Existing refresh architecture retained.

## 11. Security findings

Bounded source review found no new backend ownership bypass. Operator mutations
resolve active trusted context and owned objects; SYSTEM_ADMIN is excluded even
with mixed roles. Customer directory remains booking-snapshot scoped, groups exact
account IDs only and never merges phones/emails. Public payment DTO/client remain
minimal/anonymous. SQL values are bound and sorting allowlisted. No file-upload
surface or new serialized authentication fields introduced. This is not a penetration
test; live denials and existing integration tests substantiate the reviewed boundaries.

## 12. Performance findings/fixes

Read-only fleet pages load maintenance and assigned trips in two scoped queries per
page of at most 100 buses, then calculate readiness/conflicts in memory. Dashboard
reuses that page data rather than re-reading each bus/trip. The new unit test proves
100 empty-plan buses use exactly two JdbcTemplate calls and no guard SQL calls;
empty input uses zero. Existing lifecycle/concurrency MySQL tests pass. Mutation
guard SQL, ownership checks, isolation level and lock order are unchanged.

## 13. Bundle findings

Home search eagerly imported forms/Zod, so deferring auth alone did not remove the
large chunk. Lazy home **and** auth routes reduce main JS from 585.71 kB (187.18 kB
gzip) to **444.65 kB (143.51 kB gzip)**. Shared schema chunk: 128.44 kB / 39.44 kB
gzip. Routes use existing React Router lazy loading; no framework migration or
warning-limit adjustment. The >500 kB warning is absent in the final build.

## 14. Accessibility fixes

Domain badges include text, not color alone. Progress uses aria-current; navigation
loading uses role=status. Shared Field associations/scoped table headings retained.
Added textarea/tabindex focus styling and reduced-motion support. Custom cancellation
modal starts on safe “Quay lại”, wraps Tab/Shift+Tab, handles Escape and restores the
opener. Live tests verify each behavior at all three widths. Native trip lifecycle
dialog already manages modal focus. No full WCAG or screen-reader certification.

## 15. Responsive verification

Headless Edge at **390, 820 and 1440**, height 1000. Core customer, operator and
system-admin screens plus M16A/M16B/M17/M18A/M18B/fleet flows report no document-level
horizontal overflow or uncaught JavaScript exceptions. Tables retain local scrolling
and existing mobile rows/cards. Physical-device testing remains unverified.

## 16. Demo seed/readiness

Create-only seeder is unchanged. Initial clean start created 21 rolling trips; same-day
restart reported 0 created / 21 reused / 0 skipped. Passwords, edited account states,
bookings and assignments are not reset. Existing DB-backed seeder preservation tests
pass in the full integration suite. Transactions/attendance/maintenance are created
through real commands during rehearsal, never manufactured as seed history. Gallery
uses a separate clean schema with fictional Vietnamese contacts, paid/unpaid PHONE
reservations, WEB payment/refund and completed/future maintenance.

## 17. Demo script

[V1.5 demo script](v1.5-demo-script.md): 10–15 minutes, role/account per step,
navigation, expected result and fallback, including WEB/PHONE, collection, crew,
boarding/no-show, cancellation, reports, customers, fleet, maintenance and admin.
Preparation uses UI workflows; no manual database correction is required.

## 18. Deployment readiness

[Deployment](deployment.md), README and `.env.example` separate DEV/DEMO/production-like
use, database credentials, JWT secret generation, TTLs, bootstrap, API base/proxy,
Flyway, Docker volume preservation, startup/health and recovery. Compose is MySQL-only.
Backend does not configure cross-origin CORS: same-origin API proxy is required;
VITE_API_BASE_URL alone does not enable cross-origin access. Production TLS, backup,
monitoring and rollout are documented responsibilities, not verified deployment.

## 19. Logging review

Source logs safe unexpected-error category rather than request bodies/exception
messages. No new JWT/password/payment-token logging. SQL bind logging is not enabled.
Proxy/access logging must redact/exclude payment-token paths; this deployment concern
is documented. Liveness remains minimal `{"data":{"status":"UP"}}`; DB readiness
is checked separately through database-backed APIs.

## 20. Migration / fresh database verification

New isolated MySQL 9.2 instance at localhost:13320, data only under ignored
`.tools/m19-mysql`. Schemas: `busgo_m19_verify`, `busgo_m19_demo` and
`busgo_m19_gallery`. Existing application databases were not reset or modified.
Fresh gallery startup applied **V1 through V16**, all 16 history rows success=1,
then Hibernate validation and real application startup succeeded. Read-only metadata:
35 tables including Flyway history, 198 constraint records, 192 index-column records.
CoreDatabaseIT validates the schema/constraints in the full integration run. No M19
migration and no old migration edits. Evidence: `.tools/m19-fresh-schema.txt`,
gallery startup log and MySQL suite. Production/reference MySQL 8.4 not rerun here.

## 21. API / documentation consistency

Removed obsolete dashboard, booking-confirm/cash-confirm and revenue/occupancy
examples; marked whole-trip cancellation as an unimplemented historical proposal.
The current dashboard composes trips, report summary and fleet readiness. M12–M18C
implementation sections take precedence over historical proposals. Envelopes,
pagination, offset instants, bounded filters and authorization remain unchanged.
No DTO consolidation was justified merely for cosmetic consistency.

## 22. Date/time consistency

Core live acceptance and fleet lifecycle run in **America/New_York**. Actual trip
header is compared to Asia/Ho_Chi_Minh formatted time. Maintenance input conversion,
future/current windows and displayed times pass existing live fleet checks. Unit
test verifies UTC 18:30 on Oct 4 displays 01:30 on Oct 5 in Vietnam. No accidental
browser-local conversion was introduced; backend/JDBC remain UTC.

## 23. Number / money formatting

Existing vi-VN VND formatter reused (integer currency, thousands separators), with
regression assertions. Report percentages retain sensible precision, null remains
“Chưa đủ dữ liệu”, and complete zero sellable inventory remains “Không áp dụng”.
Money and load/attendance semantic tests pass; no report calculation changes.

## 24. README

Replaced outdated V1 entry point with V1.5 modules, stack, architecture, concise
quick start, configuration table, demo accounts, verification commands and document
links. Explicit mock-money limitation and same-origin hosting requirements.

## 25. Release notes

[V1.5 release notes](v1.5-release-notes.md) describes delivered customer/operator/admin
scope, security model, payment limits, deployment limits and deferred work without
advertising unimplemented integrations. Status is a locally verified demo-capable
release candidate, not production financial readiness.

## 26. Architecture

[Architecture](architecture.md): frontend/session/query boundary; modular monolith;
MySQL/Flyway; JWT/context isolation; segment inventory/holds; mock payment/refunds;
crew/attendance; report cohorts; customer privacy; fleet lifecycle/lock order/batching.

## 27–29. Backend, MySQL and frontend verification

| # | Command | Result | Ignored evidence |
| --- | --- | --- | --- |
| 27 | mvn test | **45 passed**, 0 failures/errors/skips | .tools/m19-unit.log |
| 28 | mvn verify -Pmysql-integration | **45 unit + 253 integration passed**, 0 failures/errors/skips | .tools/m19-verify.log |
| 27 | mvn package -DskipTests | PASS, after full verification | .tools/m19-package.log |
| 29 | npm test | **113 passed**, 0 failures/skips | .tools/m19-frontend-final.log |
| 29 | npm run build | PASS TypeScript/Vite; main 444.65 kB | .tools/m19-build-final.log |

Backend checks completed before the interruption and were preserved; no backend
source changed afterward. Frontend suite/build reran for the browser-discovered
collection wording correction. Final whitespace-only cleanup requires no test repeat.

## 30. Browser E2E

All listed live suites pass against the real M19 jar and frontend, using isolated
schemas. Each covers 390/820/1440 unless explicitly noted:

| Evidence under .tools | Covered workflows |
| --- | --- |
| m19-acceptance.json / m19-acceptance.log | UI register/login, home search, seats/hold, checkout, payment, ticket, history, cancellation keyboard; operator screens; system-admin directory; New York timezone |
| m19-m16a/results.json | PHONE PAY_ON_BOARD/QR creation, reserved inventory/conflict, collection/tickets, copy link/anonymous payment; WEB regression and invalid token/staff denial |
| m19-m16b/results.json | Crew CRUD/assignment/conflict, missing-driver guard, paid check-in/boarding, paid and ticketless no-show, intermediate collection, pickup closure/completion |
| m19-m17/results.json | WEB unpaid/paid refund/VOID, PHONE modes, public cancelled payment, scheduled expiry, attendance cancellation boundary |
| m19-reports/results.json | Money fixture equality, source/method/date filters, load, attendance, staff denial and incomplete-inventory suppression |
| m19-customers/results.json | Account/offline identity, refund, mixed attendance, ticketless no-show, search/pagination/isolation/staff denial |
| m19-fleet-browser/results.json | Future plan ready, active maintenance blocks assignment/boarding, safe completion, conflict, cancellation/history, filters/dashboard/foreign/staff denial; New York timezone |
| m19-session-browser.json | Same-tab operator A -> B -> customer; delayed successful response rejection and actual query-cache inspection (1440) |

Intermediate failures were not skipped: old boarding label was corrected; its test
expectation updated. M17 runner previously waited only 15 seconds for the configured
60-second expiry job; it now allows 75 seconds. The new harness uses an exact
cancellation status and waits for lazy navigation. All final runs above supersede
those failures. Gallery's initially loading report was recaptured after content ready.

## 31. Security acceptance

| Boundary | Live result |
| --- | --- |
| Customer -> operator trips | 403 |
| Staff -> create bus/admin mutations | 403; mutation controls denied |
| Operator B -> A booking read/cancel | 404; fleet/history also 404 |
| SYSTEM_ADMIN -> operator context | 403; mixed-role exclusion also covered by source/IT |
| Anonymous -> protected trips | 401 |
| Foreign customer -> booking | 404 |
| Replaced payment link | 404 |
| Cancelled public link payment | 409; no payment/ticket generated |

Full MySQL regression includes ownership, report/customer privacy, maintenance,
payment/cancellation/expiry and concurrency tests. No new privilege grants.

## 32. Performance smoke

Local HTTP wall time, 2 warmups + 7 samples per endpoint, 40 extra deterministic
An Phú buses (43 owned buses total during initial comparison). Baseline uses the
previous verified M18C jar; after uses M19. The resumed after measurement overlapped
initial assisted-browser smoke activity, so it is directional, not an isolated
benchmark. No production SLA or broad endpoint speedup claim.

| Endpoint | Before median ms | After median ms | After max ms |
| --- | ---: | ---: | ---: |
| Search | 16.78 | 17.81 | 20.41 |
| Seat availability | 23.67 | 28.30 | 33.08 |
| Dashboard report summary | 31.72 | 30.90 | 38.33 |
| Reports 30 days | 29.01 | 34.91 | 50.00 |
| Reports 365 days | 40.36 | 46.39 | 54.57 |
| Customer directory | 20.06 | 27.58 | 35.50 |
| Fleet directory | **78.55** | **18.68** | 22.02 |
| Fleet warnings | **103.38** | **16.96** | 18.50 |

Evidence: `.tools/m19-perf-before.json`, `m19-perf-after.json`, `m19-perf.cjs`.
Fleet query-count regression provides stronger evidence than noisy timing. Other
endpoint variation is reported, not hidden. No speculative caching was introduced.

## 33. Remaining warnings

Zod dependency pure-annotation warnings remain visible. Existing deprecated test API
notice and Flyway warning that MySQL 9.2 exceeds its tested version range remain.
Negative integration fixtures deliberately produce constraint/security/domain errors.
Git's Windows LF/CRLF conversion advisories remain; whitespace errors were fixed.
No >500 kB chunk warning, failing test or skipped integration failure remains.

## 34. Known limitations

Local MySQL 9.2/Edge evidence is not production MySQL 8.4 rollout, scale/EXPLAIN,
physical mobile testing, screen-reader certification or penetration testing.
Lifetime customer/report aggregation, all-page dropdowns and retained nonterminal
planning rows can grow. Fleet batching removes round trips, not those data-volume
limits. Planned travel windows/immutable assignments and date-only maintenance
warnings remain. All money is mock. Production proxy/TLS/backups/monitoring remain
deployment work. Seeder intentionally does not reset consumed demo scenarios.

## 35. Deferred post-V1.5 scope

Real gateways/settlement/accounting, loyalty/promotions, marketing CRM, notifications,
chat/reviews, GPS/IoT/AI, rescheduling/seat changes/partial cancellation, multi-currency,
pricing engine, exports, expanded staff mutations and major new admin modules.

## V1.5 Desktop UI Gallery

[Standalone gallery](v1.5-ui-gallery.html): **18 real screenshots**, captured at
**1440 × 1000**, browser zoom 100%, from separate clean `busgo_m19_gallery` with
real backend/frontend. Six customer screens; eleven operator screens spanning
dashboard, trips/workspace/crew, PHONE booking, passengers, reports, customer
directory/detail, fleet readiness and maintenance; one system-admin operator screen.

Useful populated records use fictional contacts and normal domain commands. No
password, JWT, public payment token or real-person sensitive test record is shown.
Ticket QR codes identify fictional demo tickets. Temporary PNGs remain ignored under
`.tools/m19-ui-gallery/`; only the self-contained HTML is an intended deliverable.

PNG data URLs are embedded in UTF-8 HTML (3,950,224 bytes), with no external assets.
Direct `file:///.../docs/v1.5-ui-gallery.html` was opened in Edge at 1440×1000 after
**all M19 backend/frontend/MySQL services were stopped**, with browser networking
offline. All 18 images decoded at natural width 1440; correct Vietnamese title and
UTF-8 verified; zero remote requests, broken image references, page overflow or
JavaScript exceptions. Image enlargement, Escape close and focus return passed.
Contact sheet and offline gallery were visually inspected. Evidence:
`.tools/m19-gallery-offline.json` and `.tools/m19-ui-gallery/offline-gallery.png`.

## 36. Final repository hygiene

`git diff --check` passes after whitespace cleanup. No commit/push. Existing
migrations unchanged. Only intended source/tests/docs/config are changed; runtime
DBs, logs, jars, screenshots, secrets and build output stay ignored. All M19 test
services stopped. The following exact final status is recorded below.

```text
 M .env.example
 M README.md
 M backend/src/main/java/com/busgo/fleet/BusService.java
 M backend/src/main/java/com/busgo/fleet/MaintenanceService.java
 M docs/api-contract.md
 M docs/demo-data.md
 M docs/development-plan.md
 M docs/product-roadmap.md
 M frontend/src/api/client.ts
 M frontend/src/api/errors.ts
 M frontend/src/components/ui.tsx
 M frontend/src/features/booking/CancellationSection.tsx
 M frontend/src/features/customer/presentation.ts
 M frontend/src/features/operator/CrewBoarding.tsx
 M frontend/src/features/operator/Customers.tsx
 M frontend/src/features/operator/FleetMaintenance.tsx
 M frontend/src/features/operator/OperationsShared.tsx
 M frontend/src/features/operator/Reports.tsx
 M frontend/src/features/operator/fleet.ts
 M frontend/src/features/operator/management.ts
 M frontend/src/features/operator/operations.ts
 M frontend/src/features/operator/shared.tsx
 M frontend/src/layouts/CustomerLayout.tsx
 M frontend/src/pages/AuthPage.tsx
 M frontend/src/pages/BookingDetailPage.tsx
 M frontend/src/pages/HomePage.tsx
 M frontend/src/pages/PaymentPage.tsx
 M frontend/src/pages/TicketPage.tsx
 M frontend/src/pages/admin/AdminPages.tsx
 M frontend/src/pages/operator/OperatorBookingCreatePage.tsx
 M frontend/src/pages/operator/OperatorBookingsPages.tsx
 M frontend/src/routes/router.tsx
 M frontend/src/styles.css
 M frontend/src/utils/format.ts
 M frontend/tests/assisted-booking.test.mjs
 M frontend/tests/crew-boarding.test.mjs
 M frontend/tests/m16a-browser-check.mjs
 M frontend/tests/m16b-browser-check.mjs
 M frontend/tests/m17-browser-check.mjs
?? backend/src/test/java/com/busgo/FleetReadBatchTest.java
?? docs/architecture.md
?? docs/deployment.md
?? docs/m19-audit.md
?? docs/m19-verification.md
?? docs/v1.5-demo-script.md
?? docs/v1.5-release-notes.md
?? docs/v1.5-ui-gallery.html
?? frontend/src/components/DomainStatusBadge.tsx
?? frontend/src/components/RouteProgress.tsx
?? frontend/src/utils/status.ts
?? frontend/tests/m19-hardening.test.mjs
```


## M19.1 final UI evidence — 04/10/2026

This section updates the final frontend/gallery evidence; the original M19 backend, security, migration and performance results above remain historical evidence and were not rerun for cosmetic changes.

- Continued current feature/management-upgrade working tree; preserved M19 changes. Backend source/test baseline hashes unchanged. No new backend/domain/schema/API/dependency changes.
- Frontend: **121 tests passed**; production build passed. Main JS **444.71 kB / 143.52 kB gzip**, versus M19 **444.65 kB / 143.51 kB gzip**. Main chunk remains below 500 kB; no production SLA claim.
- Real backend/frontend on isolated busgo_m19_gallery/MySQL 13320; no destructive seed. Live customer mock booking/payment and cancellation keyboard regression passed at **390, 820, 1440** in **America/New_York**. Final operator/admin/ticket pass completed at all three widths after visual fixes, with no document horizontal overflow or JavaScript errors.
- Checked operations-first dashboard order, real trip-status quick filter, compact crew/keyboard disclosure, employee capability filter, passenger states/actions, maintenance filtering and collapsed history, report Vietnamese payment labels, customer metric groups, admin table and Vietnam dates. Full-size screenshots and contact sheet were visually reviewed. No unsupported list readiness/crew data was fabricated.
- Known local verification findings and scope limitations are recorded in [M19.1 UI polish](m19.1-ui-polish.md).

### V1.5 Desktop UI Gallery — refreshed for M19.1

**18 screenshots**, real populated Vietnamese application at **1440×1000**: six customer screens, eleven operator screens covering dispatch/crew/passengers/assisted booking/reports/customers/fleet/maintenance, and one system-admin directory. [Standalone HTML](v1.5-ui-gallery.html) embeds every screenshot (4,026,187 bytes); temporary PNGs remain ignored.

**Offline PASS:** after backend, Vite and isolated MySQL stopped, opened the file directly in an offline browser. All 18 images decoded, UTF-8 Vietnamese/title correct, no external requests, broken images, document overflow or JavaScript errors. Enlargement, Escape and focus restoration passed. Ports 13320, 8090, 8091, 5179 and 5180 had no remaining listeners.

Final git diff --check passed. No logs, temporary screenshots, runtime databases, build output, credentials or IDE artifacts appear in the change list. No commit or push. Current status includes both preserved M19 and M19.1 work (54 modified + 14 untracked source/documentation files):

```text
M .env.example
 M README.md
 M backend/src/main/java/com/busgo/fleet/BusService.java
 M backend/src/main/java/com/busgo/fleet/MaintenanceService.java
 M docs/api-contract.md
 M docs/demo-data.md
 M docs/development-plan.md
 M docs/product-roadmap.md
 M frontend/src/api/client.ts
 M frontend/src/api/errors.ts
 M frontend/src/components/ui.tsx
 M frontend/src/features/admin/admin.css
 M frontend/src/features/booking/CancellationSection.tsx
 M frontend/src/features/customer/customer.css
 M frontend/src/features/customer/presentation.ts
 M frontend/src/features/operator/CrewBoarding.tsx
 M frontend/src/features/operator/Customers.tsx
 M frontend/src/features/operator/FleetMaintenance.tsx
 M frontend/src/features/operator/OperationsShared.tsx
 M frontend/src/features/operator/Reports.tsx
 M frontend/src/features/operator/TripWorkspace.tsx
 M frontend/src/features/operator/customers.css
 M frontend/src/features/operator/fleet.css
 M frontend/src/features/operator/fleet.ts
 M frontend/src/features/operator/management.ts
 M frontend/src/features/operator/operations.ts
 M frontend/src/features/operator/operator.css
 M frontend/src/features/operator/reports.css
 M frontend/src/features/operator/shared.tsx
 M frontend/src/layouts/CustomerLayout.tsx
 M frontend/src/layouts/OperatorLayout.tsx
 M frontend/src/pages/AuthPage.tsx
 M frontend/src/pages/BookingDetailPage.tsx
 M frontend/src/pages/HomePage.tsx
 M frontend/src/pages/PaymentPage.tsx
 M frontend/src/pages/TicketPage.tsx
 M frontend/src/pages/admin/AdminPages.tsx
 M frontend/src/pages/operator/OperatorBookingCreatePage.tsx
 M frontend/src/pages/operator/OperatorBookingsPages.tsx
 M frontend/src/pages/operator/OperatorBusesPages.tsx
 M frontend/src/pages/operator/OperatorHomePage.tsx
 M frontend/src/pages/operator/OperatorTripsPages.tsx
 M frontend/src/routes/router.tsx
 M frontend/src/styles.css
 M frontend/src/utils/format.ts
 M frontend/tests/assisted-booking.test.mjs
 M frontend/tests/crew-boarding.test.mjs
 M frontend/tests/customer-admin-ux.test.mjs
 M frontend/tests/customers.test.mjs
 M frontend/tests/fleet.test.mjs
 M frontend/tests/m16a-browser-check.mjs
 M frontend/tests/m16b-browser-check.mjs
 M frontend/tests/m17-browser-check.mjs
 M frontend/tests/reports.test.mjs
?? backend/src/test/java/com/busgo/FleetReadBatchTest.java
?? docs/architecture.md
?? docs/deployment.md
?? docs/m19-audit.md
?? docs/m19-verification.md
?? docs/m19.1-ui-polish.md
?? docs/v1.5-demo-script.md
?? docs/v1.5-release-notes.md
?? docs/v1.5-ui-gallery.html
?? frontend/src/components/DomainStatusBadge.tsx
?? frontend/src/components/RouteProgress.tsx
?? frontend/src/utils/status.ts
?? frontend/tests/m19-hardening.test.mjs
?? frontend/tests/m19.1-ui-polish.test.mjs
```

## M19.2 approved final UI and gallery — 04/10/2026

This supersedes the M19.1 frontend/gallery totals above; backend verification remains historical and was not rerun. The current working tree retains all prior M19/M19.1 changes. No backend source, domain, API, database or dependency change was made for M19.2 or gallery regeneration. No commit or push.

- User explicitly approved M19.2 visual review. Final changes and focused responsive evidence are in [M19.2 visual information](m19.2-visual-information.md). No further UI change was required during gallery regeneration.
- Final `npm test`: **124 passed, 0 failed**. Final `npm run build`: **passed**, main JS **444.71 kB / 143.51 kB gzip**. Final `git diff --check`: **passed**.
- [Final standalone gallery](v1.5-ui-gallery.html): **19 embedded 1440×1000 screenshots**, **4,200,876 bytes**. Replaced four changed entries (PHONE booking, customer directory, customer detail/history, Fleet); fourteen unchanged PNGs retained byte-for-byte. Added the changed Bus Detail, which had no M19.1 entry. No unchanged page was recaptured.
- Offline verification passed at 1440 and 390, opened directly from disk after confirming ports **8090, 8091, 5179, 5180, 13320** had no listeners. All nineteen images decoded at native dimensions; Vietnamese UTF-8/title/count, enlargement, Escape and focus restoration passed. No remote requests, document overflow or JavaScript errors. Services remain stopped.
- Ignored evidence: `.tools/m19.2-ui-gallery/manifest.json`, `regeneration.json`, `offline-verification.json`, `.tools/m19.2-gallery-tests.log`, `.tools/m19.2-gallery-build.log`. Exact complete final `git status --short` is recorded in `.tools/m19.2-gallery-status.txt`, including existing backend changes from M19; those were not modified in this phase.
