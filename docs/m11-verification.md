# M11 operator frontend verification

Date: 2026-09-27. Branch: `feature/operator-frontend`.

## Scope and source of truth

Frontend-only implementation based on the current `BusController`,
`BusTypeController`, `OperatorRouteController`, `TripController`, `FleetDtos`,
`RouteDtos`, `TripDtos`, `SecurityConfig` and relevant Java services. The M11
frontend remains untouched by this follow-up; its only backend change is the
demo-profile-only operator-admin fixture documented below. No database migration,
customer page, Axios client or token-refresh changes were made. No commit or push
was performed.

The broader original M11 wishlist in development-plan.md has been replaced by
the currently supported scope. Older broad API/design prose does not override
the Java contracts: `/api/v1/operator/**` requires OPERATOR_ADMIN, not staff.
The backend also requires exactly one active operator membership.

## Supported routes

- `/operator`: navigation/quick actions, no metrics.
- `/operator/trips`: paginated list; date, routeId, busId, status filters.
- `/operator/trips/new`: active owned operator route, AVAILABLE bus, future departure.
- `/operator/trips/:tripId`: read-only detail, ordered stops, segments, snapshot seats.
- `/operator/buses`: paginated plate search; status and busTypeId filters.
- `/operator/buses/new`: plate and bus type; server defaults status to AVAILABLE.
- `/operator/buses/:busId`: partial plate/type/status edits.
- `/operator/bus-types`: read-only active types.
- `/operator/bus-types/:busTypeId`: read-only detail and seat-template preview.
- `/operator/routes`: paginated owned associations.
- `/operator/routes/catalog`: paginated active global catalog and attach/reactivate.
- `/operator/routes/:operatorRouteId`: association status, ordered stops, fare editor.

## Automated results

| Command | Result |
| --- | --- |
| `cd frontend; npm run build` | PASS: strict TypeScript check and Vite production build, separate lazy operator chunks |
| `cd frontend; npm test` | PASS: 5 Node tests for complete fare-set retention/removal, empty replacement, direction/active membership, duplicate pairs, decimal/amount limits |
| `node .tools/m10-ux-checks.mjs` | PASS: existing local customer check, 30 assertions for Vietnamese dates, safe redirects, journey-bound drafts and unavailable-seat removal |
| `git diff --check` | PASS |

The initial build's TypeScript step passed, but esbuild could not read parent
directories under the filesystem sandbox. Re-running the same build with
approved host access succeeded. Build warnings: existing Zod annotation comments
and the main bundle exceeding Vite's 500 kB advisory threshold. No lint script or
pre-existing tracked frontend test suite was present. `npm test` is a small new
Node built-in test suite; no test framework dependency was added. The existing
`.tools` customer check is ignored local tooling, not a portable repository test.

## Browser checks actually performed

Using the local Vite server at `http://127.0.0.1:5173`:

1. Opened `/operator/trips?page=2&status=SCHEDULED` while unauthenticated.
   Observed the login form at
   `/login?returnTo=%2Foperator%2Ftrips%3Fpage%3D2%26status%3DSCHEDULED`.
   The registration link also preserved the return path/query.
2. Opened `/`. Observed the existing customer home, search inputs, navigation,
   featured routes and footer rendering normally.

These checks do not claim live operator authorization or successful API mutations.
The explicit `dev,demo` fixture now provisions the documented local-only
`OPERATOR_ADMIN` login in [Demo data](demo-data.md#demo-operator-login-local-demo-only).
It is scoped to one active An Phú Express demo membership and deliberately has no
`OPERATOR_STAFF` role. Authenticated workflows and visual responsive acceptance
remain pending a running backend verification with that fixture.

## Static verification results

| Area | Evidence / result |
| --- | --- |
| Customer routes | Router diff only adds the separate operator root; every existing customer route and CustomerLayout remain intact. |
| Guard | Uses existing auth state and `/users/me` query; unauthenticated redirect, profile loading/error/retry, exact OPERATOR_ADMIN role check, explicit 403 for every other authenticated role. |
| OPERATOR_ADMIN access | Static pass: successful profile with the role renders Outlet. Live account verification pending. |
| Non-admin / OPERATOR_STAFF rejection | Static pass: no staff or SYSTEM_ADMIN bypass; explicit 403 and logout. Live verification pending. |
| Trips | GET list/detail and POST create match Java fields. Filter uses global routeId; creation uses owned operatorRouteId. No update/cancel/status mutation exists. |
| Trip time | UTC date filter matches TripService's UTC day bounds. Form parses Vietnam time and sends ISO offset timestamp; server computes arrival. |
| Snapshot seats | Floor/row/column layout is read-only, neutral styling, explicit non-occupancy notice; no passenger/booking data. |
| Buses | GET/POST/PATCH use supported endpoints. Creation sends only plate/type. Editing sends changed fields only, avoiding resubmitting an unchanged inactive type. Status enum matches Java. |
| Bus types | GET list/detail only; no editing API, controls or route. |
| Catalog attach | Real paged `/operator/route-catalog` data; POST `/operator/routes` with routeId. Active duplicate errors shown from backend; inactive association reactivated by backend. |
| Route activation | PATCH association with ACTIVE/INACTIVE; deactivation confirmation and pending controls; invalidates route list/detail/choices. No global route editing. |
| Fares | Loads the entire active set through GET; PUT sends `{ fares: [...] }` containing all retained rows. Required IDs, positive decimal price, actual stopOrder, active stop membership and unique pairs validated. Explicit full-set preview and empty-set confirmation. Background refetch does not reset local form edits. |
| Pagination | Uses the direct PagedResponse envelope; page/size stored in URL, filters reset page, boundary buttons disabled, empty results keep pagination accessible. Choice lists fetch all pages. |
| States | Shared query loading/error/retry, empty tables, mutation error messages and pending controls. Successful edits update/invalidate corresponding queries. |
| Error messages | OperatorError displays server message and code, including membership, duplicate plate/route, schedule conflict, invalid layout and fare errors; network failures use existing error fallback. |
| Responsive shell | Static pass: minmax workspace, mobile navigation toggle with aria-expanded, 1050/700px breakpoints, wrapping controls, horizontally scrollable tables/seat grids, single-column mobile fare rows. Live 390/1024/1440px inspection pending. |
| API/session reuse | Exactly the existing Axios instance and refresh interceptors; no operator ID in requests. ApiResponse and PagedResponse handled separately. |
| Navigation | Only implemented features; topbar discloses the real user's read-only profile and logout. No customer-only profile redirect for operator-only users. |

## Authenticated manual acceptance checklist (pending)

Start the backend with `dev,demo`, then sign in as
`operator.admin@anphu-demo.example` / `DemoOperator!2026`. The fixture has
OPERATOR_ADMIN plus exactly one active An Phú Express membership. Use a separate
CUSTOMER/OPERATOR_STAFF account for rejection checks. These are follow-up steps,
not claimed results:

1. Log in through the preserved returnTo and verify the operator shell. Confirm
   CUSTOMER, OPERATOR_STAFF and SYSTEM_ADMIN-only accounts receive 403. Log out
   and verify operator pages redirect again.
2. Test trips/buses filters, page and size changes, refresh/back/forward, empty
   searches and API error retry. Verify real backend totals and UTC day boundaries.
3. Create a bus, edit plate/type/status; cancel and confirm maintenance/inactive
   prompts. Exercise duplicate plate errors. Inspect a read-only bus type layout.
4. Browse catalog, attach a route, deactivate/cancel/reactivate its association.
   Attempt an already-active attach and verify the backend conflict is visible.
5. Start with multiple fare pairs. Edit one price and retain the others; remove
   one pair; add a forward pair; reject backward/duplicate pairs. Verify the PUT
   body includes the complete intended set. Cancel confirmation without saving.
   Explicitly confirm an empty replacement only on disposable test data, then
   restore the original complete set.
6. Create a future trip on a ready bus and active route; verify navigation to
   detail, stop times, segments and snapshot layout. Exercise a schedule conflict.
   Check list and detail contain no occupancy/passenger/booking information.
7. Inspect at 390, 1024 and 1440 pixels: sidebar toggle, table/seat scrolling,
   complete fare rows, form labels, errors, confirmations and disabled controls.
8. Repeat customer search → seats → hold → booking → mock payment → tickets
   with a CUSTOMER account to complete full end-to-end regression acceptance.

## Limitations and deferred scope

- Authenticated API mutations and responsive operator screenshots are unverified
  until the live acceptance checklist can be completed with a provisioned account.
- Profile access in the operator topbar is read-only. Customer profile editing
  remains on its existing customer-protected route.
- Fare replacement has no backend version/ETag concurrency contract. The editor
  retains local edits across background refetches, but another administrator can
  still change the fare set before this user's replacement.
- The account/membership requirement is enforced by the backend; an admin role
  alone does not guarantee access to operator data.
- All-page choice loading prioritizes completeness; a server-backed option-search
  endpoint would be preferable at very large fleet/route counts, but none exists.
- No operator bookings/detail/actions, passenger manifest, analytics, live
  occupancy, seat blocking/unblocking, trip edit/cancel/status transitions,
  bus type/seat-template/global route/global stop editors, staff, customers,
  reports, refunds, CMS or system admin. Final visual polish is deferred.

## File inventory

Modified:

- `README.md`
- `docs/development-plan.md`
- `frontend/package.json` (Node test command only)
- `frontend/src/routes/router.tsx` (separate lazy operator tree)

Created:

- `docs/m11-verification.md`
- `frontend/src/types/operator.ts`
- `frontend/src/api/operatorApi.ts`
- `frontend/src/features/auth/OperatorGuard.tsx`
- `frontend/src/layouts/OperatorLayout.tsx`
- `frontend/src/features/operator/shared.tsx`
- `frontend/src/features/operator/queries.ts`
- `frontend/src/features/operator/TripTimeline.tsx`
- `frontend/src/features/operator/SeatLayoutPreview.tsx`
- `frontend/src/features/operator/FareEditor.tsx`
- `frontend/src/features/operator/fareSchema.ts`
- `frontend/src/features/operator/operator.css`
- `frontend/src/pages/operator/OperatorHomePage.tsx`
- `frontend/src/pages/operator/OperatorTripsPages.tsx`
- `frontend/src/pages/operator/OperatorBusesPages.tsx`
- `frontend/src/pages/operator/OperatorBusTypesPages.tsx`
- `frontend/src/pages/operator/OperatorRoutesPages.tsx`
- `frontend/tests/operator-fares.test.mjs`

Related pages are grouped by domain while forms/shared components remain focused.
Git changes are unstaged frontend/docs only; no commits or pushes.
