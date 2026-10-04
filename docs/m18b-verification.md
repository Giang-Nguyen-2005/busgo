# M18B verification and implementation report

Completed locally on 2026-10-04 (Asia/Ho_Chi_Minh), branch
feature/management-upgrade. No commit, push or deployment.

## 1. Source audit

Read the roadmap, development plan, API contract, M16A/M16B/M17/M18A designs and
existing booking/contact/payment/ticket/refund/attendance queries, schema and access
context. PHONE has no customer account; booking items own attendance. Successful
payment issues unique tickets per item. M17 retains original paid/refunded amounts
and dedicated refunds. Reused those facts without adding a global customer master.

## 2. Files created

- backend/src/main/java/com/busgo/customer/CustomerController.java
- backend/src/main/java/com/busgo/customer/CustomerService.java
- backend/src/main/java/com/busgo/customer/CustomerRepository.java
- backend/src/main/java/com/busgo/customer/CustomerFilter.java
- backend/src/main/java/com/busgo/customer/CustomerDtos.java
- backend/src/test/java/com/busgo/CustomerFilterTest.java
- backend/src/test/java/com/busgo/M18BCustomersIT.java
- frontend/src/api/customersApi.ts
- frontend/src/types/operatorCustomers.ts
- frontend/src/features/operator/Customers.tsx
- frontend/src/features/operator/customers.css
- frontend/tests/customers.test.mjs
- frontend/tests/m18b-browser-check.mjs
- docs/m18b-customer-management.md
- docs/m18b-verification.md

## 3. Files modified

- backend/src/test/java/com/busgo/FoundationTest.java: mock new JDBC repository in
  existing datasource-free health/security unit context.
- frontend/src/features/auth/access.ts: admin customer navigation.
- frontend/src/layouts/OperatorLayout.tsx: customer icon.
- frontend/src/routes/router.tsx: directory/detail routes.
- docs/api-contract.md, docs/development-plan.md, docs/product-roadmap.md.

## 4. Migration/indexes

None. Existing ownership, customer-created, booking/item/payment/ticket/attendance
and refund indexes suffice for this implementation. Leading-wildcard contact search
is documented as a scan; no speculative full-text/customer schema.

## 5. Customer identity model

ACCOUNT:{customer_id} groups exact account IDs within owned bookings. Latest contact
snapshot drives display; historical rows preserve contact snapshots. No profile read.
Account holder/contact/passenger/payer remain distinct.

## 6. Offline-contact model

CONTACT:{booking_id}, type OFFLINE_CONTACT, one accountless booking. No fake accounts.
Keys are internal IDs, not phone/email hashes or personal data in URLs.

## 7. Grouping rules

Repeated phone/email never merges offline contacts or account/contact groups.
Same registered account at two operators gets separate owned aggregates/history.

## 8. Directory query model

Owned booking cohort, separate booking-grain money/refund/item aggregates, window
rank for latest contact, grouped customer totals. No per-customer queries.

## 9. Search/pagination/sorting

Literal lowercase trimmed name/phone/email/code search, escaped LIKE metacharacters.
Page 0..100000, size 1..100; defaults 0/20. LATEST desc, NAME asc, BOOKINGS/MOCK_PAID
desc with stable key ties. History created_at/id desc. Fixed-dataset stability;
concurrent new bookings may reorder separate requests.

## 10. Customer detail

Owned latest contact, type, latest booking/journey, current confirmed/cancelled,
source counts, total bookings, distinct recorded boarded trips and mock money.

## 11. Booking history

Paginated code/source/journey/pickup/dropoff/seats/contact snapshots/commercial
state/payment method and state/ticket validity/mixed attendance/cancellation/time.
Separate batched payment and refund events; links to existing booking detail.

## 12. Money semantics

Original PAID/REFUNDED with successful paid_at contribute grossMockPaid; dedicated
refunds contribute mockRefunds; net is gross minus refunds. Cancelled unpaid is zero.
All three use simulated wording. Multi-seat/ticket joins never multiply money.

## 13. Attendance semantics

Recorded item BOARDED/NO_SHOW/CHECKED_IN counts remain separate. Missing/EXPECTED is
unrecorded. Ticketless PHONE PAY_ON_BOARD NO_SHOW is represented. Distinct trips
with any boarding are not claims about verified contact/account-holder travel.

## 14. Privacy

Booking-local contacts only. No user/global profile joins, passwords/roles/security
status or unrelated operator activity. Event data omits actors and free-text notes.

## 15. Operator isolation

Trusted membership owns all booking/transaction/item queries via trip/operator route.
No operatorId filter. Foreign typed keys are 404. Same account and same phone across
two operators are covered by MySQL and real-backend browser fixtures.

## 16. Access policy

Admin-only, active operator membership required. Public, customer, staff and
SYSTEM_ADMIN denied. Mixed OPERATOR_ADMIN + SYSTEM_ADMIN denied using actual persisted
roles (JWT authentication reloads roles from storage). Staff support deferred.

## 17. API changes

GET /api/v1/operator/customers and GET /api/v1/operator/customers/{customerKey}.
Typed DTOs, bounded filters, UTC-offset timestamps, 400 invalid and 404 foreign.
No mutation endpoints. Full shapes in api-contract.md.

## 18. Directory UI

One Khách hàng navigation entry. Existing operator filter visuals, search/type/sort,
compact desktop table, local table scrolling, mobile cards, loading/error/empty and
pagination. Long contact values wrap safely.

## 19. Detail UI

Contact/type header, metric cards, WEB/PHONE and item attendance summary, compact
booking cards, cancellation, expandable simulated transactions and booking links.
No edit/delete buttons. Responsive cards visually reviewed.

## 20. Query safeguards

Bound parameters, enum sort expressions, strict typed numeric keys, separate money
domains, REPEATABLE_READ response snapshot. Scoped batch events prevent N+1 reads.

## 21. Performance

Directory two queries; nonempty detail five fixed queries, independent of customer/
page row count. Detail event queries restricted to page IDs. Directory lifetime
aggregation scans owned cohort; production profiling and cursor pagination deferred.

## 22. Unit tests

mvn test passed: 39 tests, zero failures/errors/skips. Two new CustomerFilterTest
cases verify defaults, literal escaping, bounds and typed ID validation.
Evidence: .tools/m18b-unit.log.

## 23. MySQL integration tests

mvn verify -Pmysql-integration passed: 39 units + 235 integration cases, zero
failures/errors/skips. Six new M18BCustomersIT cases cover ownership/account/contact
grouping, no fake accounts, snapshot search, pagination/sorts, gross/refund/net,
multi-seat/tickets, paid/unpaid cancellation, mixed/missing/checked-in attendance,
ticketless no-show, all caller policies including persisted mixed roles and safe
response fields. Isolated MySQL 9.2 at 127.0.0.1:13318/busgo_m18b_verify; no existing
application DB reset. Evidence: .tools/m18b-verify.log.

## 24. Frontend tests/build

npm test passed: 101 tests, zero failures/skips, including eight new customer tests.
Covers empty/account/offline directory, typed links/mobile structure, history and
mixed attendance, transaction wording, no-show, authorization with no query mounting,
search/filter/page query keys and responsive CSS. npm run build passed TypeScript
and Vite. Evidence: .tools/m18b-frontend.log, .tools/m18b-build.log.

## 25. Browser verification

Real backend at 8089, frontend at 5179, isolated busgo_m18b_browser schema. Edge
headless at 390, 820 and 1440 pixels. Fixtures created through APIs: paid WEB account
with differing global profile/contact, eleven owned history records, PHONE contacts
with repeated phone/email, paid/refunded two-seat booking, mixed boarded/no-show,
ticketless unpaid no-show, explicit check-in and unrecorded items; foreign operator
has the same account and same phone. Only a newly registered platform fixture receives
its SYSTEM_ADMIN role via local test-schema SQL; operational fixtures use APIs.

Acceptance covers populated/empty directory, name/phone/email search, badges, preserved
snapshots, gross/refund/net, payment/refund events, cancellation, mixed attendance,
existing detail links, ten-row directory/history pagination, foreign directory and
typed-key privacy, staff navigation/route denial. API checks additionally deny system,
customer and public. All three widths: no document overflow or JS page errors.
Screenshot review improved visible search styling and contact column widths; final
frontend tests/build and browser acceptance were rerun after those refinements.

Evidence: .tools/m18b-browser.log, .tools/m18b-browser/results.json and 24 PNGs.

## 26. Regression verification

All existing backend/frontend suites included: M16A assisted booking/payment, M16B
crew/boarding/no-show, M17 cancellation/refund/expiry/concurrency and M18A reports.
mvn package -DskipTests passed (.tools/m18b-package.log). git diff --check passed.
Warnings were retained: Flyway notes MySQL 9.2 beyond tested version, Java test
instrumentation/class sharing, expected negative-test SQL/demo warnings, existing
Vite >500 kB bundle warning and Git LF→CRLF notices. No warning flags disabled.

## 27. Unverified items

No outstanding required local checks. Production-scale query plans/load, production
deployment/migration, physical-device and comprehensive accessibility acceptance
were not performed. No production performance or real financial claims.

## 28. Deferred scope

Staff support reads, contact identity verification/grouping, customer mutations,
marketing CRM, segmentation/loyalty/promotions/campaigns/notifications/sensitive
notes, cross-operator profiles, exports, cursor pagination and real gateways.

## 29. git status --short

Recorded after implementation; .tools logs/DB/screenshots are ignored runtime artifacts.

```text
 M backend/src/test/java/com/busgo/FoundationTest.java
 M docs/api-contract.md
 M docs/development-plan.md
 M docs/product-roadmap.md
 M frontend/src/features/auth/access.ts
 M frontend/src/layouts/OperatorLayout.tsx
 M frontend/src/routes/router.tsx
?? backend/src/main/java/com/busgo/customer/
?? backend/src/test/java/com/busgo/CustomerFilterTest.java
?? backend/src/test/java/com/busgo/M18BCustomersIT.java
?? docs/m18b-customer-management.md
?? docs/m18b-verification.md
?? frontend/src/api/customersApi.ts
?? frontend/src/features/operator/Customers.tsx
?? frontend/src/features/operator/customers.css
?? frontend/src/types/operatorCustomers.ts
?? frontend/tests/customers.test.mjs
?? frontend/tests/m18b-browser-check.mjs
```
