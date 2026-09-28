# M13 P0 verification

## Implemented scope

- Disabled-by-default, environment-backed initial `SYSTEM_ADMIN` provisioning.
- `SYSTEM_ADMIN`-only operator list/create/detail/update/status/staff-view APIs.
- `OPERATOR_ADMIN`-only staff list/create/update APIs.
- Read-only `OPERATOR_STAFF` access to owned trips, bookings, manifests,
  occupancy, and active bus types.
- Inactive operators removed from every new-commerce entry point while historical
  booking/ticket reads remain available.
- Flyway V10 adds `UNIQUE(operator_id, staff_code)`.

## Concurrency and isolation

Staff mutations for one operator are serialized until their database transaction
has committed. The final-admin decision is made using a current locking database
read. This prevents concurrent demotions/deactivations from removing every active,
login-capable operator admin. Operator IDs are never accepted by operator staff
APIs; foreign staff IDs return the same 404 as absent IDs.

Customer commerce mutations lock the trip and then the owning operator before
rechecking ACTIVE status. Operator status updates lock the same operator row.
This closes the race between deactivation and hold conversion/payment.

## Commands and results

- `mvn test`: passed, 24 tests.
- Focused M13 MySQL run: passed, 6 tests (5 system/admin/staff/commerce tests
  plus the concurrent last-admin test). The concurrency test initially exposed
  a race; the post-fix run passed.
- `mvn verify -Pmysql-integration`: passed, including 24 unit tests and 130
  MySQL integration tests.
- `git diff --check`: passed.

The root `.env` contains Compose `MYSQL_*` variables; Maven does not load it and
the application consumes `DB_*`. Verification maps the local `MYSQL_*` values to
`DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` for the test process without printing
secrets.

## Deferred

Customer administration, reports, cancellation/refund, invitation
email, existing-account linking, arbitrary role editing, master-data editing,
and persisted audit events remain deferred.

## M13 frontend P0 implementation (2026-09-28)

Branch: feature/system-admin. No commit or push. Existing backend/README/API-contract
changes were present before this frontend task and were preserved; no backend
files were modified during frontend implementation. No contract mismatch required
backend intervention. Backend test results above are prior evidence, not reruns.

### Routes and capabilities

- /admin: SYSTEM_ADMIN-only overview with navigation and quick actions, no metrics.
- /admin/operators: URL-backed q/status/page/size, paginated operator list, active
  staff/admin counts, loading/empty/error states, and operator onboarding.
- /admin/operators/:operatorId: contact-only editing, read-only code, staff and
  operational counts, timestamps, status confirmation, and read-only staff table.
- /operator/staff: OPERATOR_ADMIN-only staff list/create/update, URL-backed
  q/status/role/page/size filters. Membership and login status are displayed separately.
- Onboarding separates operator details from the required initial admin. Account
  forms explain direct creation, no invitation email, and out-of-band initial
  password sharing. Passwords are not displayed after successful creation.
- Staff editing submits only staffCode/status/role. Role choices are restricted to
  OPERATOR_ADMIN and OPERATOR_STAFF. Successful staff changes refresh the user
  profile so self-demotion removes management access.
- Deactivation confirmation explains management and new-commerce suspension and
  that existing trips/bookings/payments/tickets are not automatically cancelled
  or refunded. Activation explains the active login-capable admin requirement.

### Authorization, navigation, and login

SystemAdminGuard checks auth loading/errors and only admits SYSTEM_ADMIN. Operator
access explicitly excludes SYSTEM_ADMIN, even if a mixed role list is supplied.
OPERATOR_STAFF can read overview, trips/details, bookings/details, manifests,
occupancy and bus types. Mutation routes, fleet/routes/staff navigation, creation
quick actions, trip status controls and fleet detail links are unavailable to staff.
The staff trip list does not request admin-only fleet/route choice APIs. The backend
remains authoritative for membership, operator activity, ownership and mutations.

Login defaults: SYSTEM_ADMIN -> /admin; OPERATOR_ADMIN and OPERATOR_STAFF ->
/operator; CUSTOMER retains the existing safe customer destination. Authorized
return paths are preserved; management return paths that the role cannot access
fall back to the appropriate home. External return URLs remain rejected.

### Error mapping

Vietnamese messages cover OPERATOR_NOT_FOUND, STAFF_NOT_FOUND,
OPERATOR_CODE_ALREADY_EXISTS, EMAIL_ALREADY_EXISTS, STAFF_CODE_ALREADY_EXISTS,
STAFF_MEMBERSHIP_CONFLICT, LAST_OPERATOR_ADMIN_REQUIRED,
OPERATOR_ACTIVATION_NOT_ALLOWED and ACCESS_DENIED. Unknown errors use a safe
fallback. Shared operator errors no longer render raw server messages/codes.

### Files created in this frontend task

- frontend/src/api/adminApi.ts
- frontend/src/types/admin.ts
- frontend/src/features/auth/access.ts
- frontend/src/features/auth/SystemAdminGuard.tsx
- frontend/src/features/operator/management.ts
- frontend/src/features/operator/ManagementFields.tsx
- frontend/src/features/operator/StaffManagement.tsx
- frontend/src/layouts/AdminLayout.tsx
- frontend/src/pages/admin/AdminPages.tsx
- frontend/tests/management.test.mjs

### Files modified in this frontend task

- frontend/src/api/errors.ts
- frontend/src/api/operatorApi.ts
- frontend/src/types/operator.ts
- frontend/src/features/auth/AuthProvider.tsx
- frontend/src/features/auth/OperatorGuard.tsx
- frontend/src/features/operator/TripStatusAction.tsx
- frontend/src/features/operator/operations.ts
- frontend/src/features/operator/operator.css
- frontend/src/features/operator/queries.ts
- frontend/src/features/operator/shared.tsx
- frontend/src/layouts/OperatorLayout.tsx
- frontend/src/pages/AuthPage.tsx
- frontend/src/pages/operator/OperatorHomePage.tsx
- frontend/src/pages/operator/OperatorTripsPages.tsx
- frontend/src/routes/router.tsx
- frontend/tests/helpers/tsx-loader.mjs
- frontend/tests/operator-operations.test.mjs
- docs/m13-verification.md (already present as an untracked backend report)
- docs/development-plan.md (preserved existing changes)

### Frontend verification executed

- npm test from frontend/: passed, 26 tests (9 new M13 tests plus all 17 existing
  fare/operations regression tests). No standalone customer test suite exists;
  customer login-return behavior is covered in the new tests.
- Tests exercise real SSR components, guard role/loading/error states, direct URL
  denial, staff navigation and mutation visibility, labels, URL filter parsing,
  Vietnamese errors, whitelisted form mapping, Axios request bodies/endpoints,
  read-only staff rendering and operator list/onboarding rendering.
- npm run build from frontend/: passed TypeScript and Vite production build.
  Initial Vite run failed on sandbox parent-directory access; approved execution
  outside that restriction passed. Non-blocking Zod annotation and chunk-size
  warnings remain. Initial type/test failures were corrected before the passing run.
- git diff --check: passed (Git may emit LF/CRLF normalization warnings).
- Manual source/contract review: Java DTOs/controllers/query repositories,
  role gates, nullability, status impact copy and responsive CSS inspected.
- Manual browser verification completed: none. SSR tests are automated evidence,
  not browser or backend end-to-end verification.

### Still unverified / manual browser checklist

- Run live logins for all four roles, reload protected URLs, and check return paths.
- Create an operator with its initial admin; verify duplicate-code/email errors,
  contact updates, activation constraints and deactivation effects against the API.
- Create/update staff; verify duplicate staff code, final-admin protection,
  membership conflicts, self-demotion and account/membership inactivity.
- Verify loading/network-error/retry/empty states, browser back/forward filters,
  and multiple result pages against a populated backend.
- Inspect desktop and narrow mobile layouts, menu interaction, stacked forms,
  horizontal table scrolling, native field validation and keyboard operation.
- Verify customer search/hold/booking/payment and historical tickets remain correct
  with inactive operators. No live backend mutations or browser sessions were run.

Responsive implementation reuses the existing operator shell and scrollable tables;
new forms use two columns on desktop and one below 700px. This is functional P0,
not an M14 visual redesign.

Deferred scope remains customer administration, reports/analytics,
cancellation/refunds, invitations/email, existing-account linking, arbitrary
role editing, global master-data editing, audit-event UI and M14 visual polish.

### Final git status --short

Includes pre-existing backend and documentation work.

```text
 M README.md
 M backend/src/main/java/com/busgo/booking/BookingService.java
 M backend/src/main/java/com/busgo/booking/OperatorBookingService.java
 M backend/src/main/java/com/busgo/common/security/SecurityConfig.java
 M backend/src/main/java/com/busgo/hold/SeatHoldService.java
 M backend/src/main/java/com/busgo/operator/OperatorContextService.java
 M backend/src/main/java/com/busgo/operator/repository/OperatorStaffRepository.java
 M backend/src/main/java/com/busgo/operator/repository/TransportOperatorRepository.java
 M backend/src/main/java/com/busgo/payment/PaymentTicketService.java
 M backend/src/main/java/com/busgo/trip/TripService.java
 M backend/src/main/java/com/busgo/trip/operations/OperatorTripOperationsService.java
 M backend/src/main/java/com/busgo/trip/search/CustomerJourneyResolver.java
 M backend/src/main/java/com/busgo/trip/search/TripSearchRepository.java
 M backend/src/main/java/com/busgo/user/repository/UserRoleRepository.java
 M backend/src/main/resources/application.yml
 M backend/src/test/java/com/busgo/CoreDatabaseIT.java
 M backend/src/test/java/com/busgo/FoundationTest.java
 M docs/api-contract.md
 M docs/development-plan.md
 M frontend/src/api/errors.ts
 M frontend/src/api/operatorApi.ts
 M frontend/src/features/auth/AuthProvider.tsx
 M frontend/src/features/auth/OperatorGuard.tsx
 M frontend/src/features/operator/TripStatusAction.tsx
 M frontend/src/features/operator/operations.ts
 M frontend/src/features/operator/operator.css
 M frontend/src/features/operator/queries.ts
 M frontend/src/features/operator/shared.tsx
 M frontend/src/layouts/OperatorLayout.tsx
 M frontend/src/pages/AuthPage.tsx
 M frontend/src/pages/operator/OperatorHomePage.tsx
 M frontend/src/pages/operator/OperatorTripsPages.tsx
 M frontend/src/routes/router.tsx
 M frontend/src/types/operator.ts
 M frontend/tests/helpers/tsx-loader.mjs
 M frontend/tests/operator-operations.test.mjs
?? backend/src/main/java/com/busgo/admin/
?? backend/src/main/java/com/busgo/operator/OperatorStaffController.java
?? backend/src/main/java/com/busgo/operator/OperatorStaffDtos.java
?? backend/src/main/java/com/busgo/operator/OperatorStaffQueryRepository.java
?? backend/src/main/java/com/busgo/operator/OperatorStaffService.java
?? backend/src/main/resources/db/migration/V10__operator_staff_code_unique.sql
?? backend/src/test/java/com/busgo/M13StaffConcurrencyIT.java
?? backend/src/test/java/com/busgo/M13SystemAdminIT.java
?? docs/m13-verification.md
?? frontend/src/api/adminApi.ts
?? frontend/src/features/auth/SystemAdminGuard.tsx
?? frontend/src/features/auth/access.ts
?? frontend/src/features/operator/ManagementFields.tsx
?? frontend/src/features/operator/StaffManagement.tsx
?? frontend/src/features/operator/management.ts
?? frontend/src/layouts/AdminLayout.tsx
?? frontend/src/pages/admin/
?? frontend/src/types/admin.ts
?? frontend/tests/management.test.mjs
```
