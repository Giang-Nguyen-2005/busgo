# M24A marketplace

## Audit

- `transport_operators` has public name but no description/logo; private contact and staff data must stay outside marketplace DTOs.
- Routes belong to operators through `operator_routes`; trip stops and bus types are existing snapshots/catalogues. Public bookability requires ACTIVE operator/association and SCHEDULED future pickup.
- Existing trip search supports operator/type/price/time but loads all candidates and paginates in Java. M24A moves filtering, ordering and pagination into SQL.
- Seat availability requires every selected segment to exist and every seat/segment row to be AVAILABLE. Reuse this predicate; M20 switches current inventory and M21 releases cancelled items.
- Bookings retain current trip/customer references; items have a cancelled flag. CONFIRMED/COMPLETED retained bookings on COMPLETED trips are reviewable when an uncancelled item remains. PENDING and CANCELLED are excluded.
- Trip completion uses the existing operator operations lifecycle. Reviews must not depend on booking status automatically becoming COMPLETED.
- Customer Home/search/booking detail and the operator console are existing integration points. No operator public settings screen currently exists.
- M23 provides durable notification delivery; review notifications are optional and are not added here.
- Demo seeding is opt-in/create-only. Keep existing seed behaviour and show truthful empty rating states instead of fabricated reviews.
- Existing response/page envelopes and operator context enforce ownership and reject SYSTEM_ADMIN impersonation. Review lists and search need bounded database pagination; existing trip/location/inventory indexes cover journey predicates.

## Implementation and verification

Implemented on `feature/v2-marketplace-discovery`; verification is recorded below.

## Public profile and discovery

Active operators have /operators/{id}. Public DTOs expose name, public description/logo and database-derived rating/count only; no staff, private contacts, operator codes or customer IDs. Operator admins edit description/logo in the lightweight reviews area; staff can read. SYSTEM_ADMIN cannot use operator context, including mixed roles. Inactive operators return 404 publicly and are excluded from discovery/search.

Profiles include up to 30 active routes, 100 currently available bus types, six upcoming bookable journeys within 30 days, and paginated reviews. Home shows active operators ordered by visible-review average, then review count and stable ID; unrated operators fall back to active ID order. Routes use the truthful “Tuyến đang hoạt động” fallback rather than pretending popularity. Upcoming trips and recent reviews are real data, limited to six/four cards. Existing demo seeder is unchanged and creates no fabricated rating metrics.

## Reviews and concurrency

Authenticated CUSTOMER must own the booking. Its CURRENT trip must be COMPLETED, booking status CONFIRMED or COMPLETED, and at least one item uncancelled. Pending/full-cancelled bookings and empty retained-item sets are ineligible. One booking with multiple passengers has one active review. Review context derives through the booking, so M20's current trip/operator and M21's retained items are authoritative.

Ratings are integer 1–5, text is required and limited to 2,000 characters. Customer can create/edit/delete its review. Soft deletion preserves the row and operator response but hides both publicly and excludes its rating/count. Re-creation after deletion creates a new historical row. Booking row locks serialize creation/edit/deletion with booking mutations; a generated active_booking_id unique key additionally prevents duplicate active reviews. No rating aggregate is an editable field: SQL AVG/COUNT use visible rows. Zero reviews returns null averageRating and count 0; UI says “Chưa có đánh giá”.

Operator admin can create/update one public response (2,000 character limit) on a visible own-operator review. No customer rating/text/delete mutation is exposed to operators. Foreign booking/review/operator context is concealed with 404. Staff can read own reviews/profile but mutations return 403. No M23 changes or review notification event was added.

## Search and performance

Existing /trips/search retains journey/date semantics and supports operatorId, busTypeId, minPrice/maxPrice, departureFrom/departureTo (Vietnam business time), minRating (1–5), minSeats (1–100; default 1). Sort: RECOMMENDED (rating, count, departure), DEPARTURE_ASC/DESC, PRICE_ASC/DESC, RATING_DESC. Stable trip ID breaks ties. Defaults remain truthful for unrated operators. Filters, seat count, sorting, count and LIMIT/OFFSET are database authoritative. Page size is 1–100, default 20; customer UI requests 10.

Search and availability counts share the fail-closed segment predicate: all required segments must exist, and no seat/segment inventory may be missing or other than AVAILABLE. No capacity-minus-bookings approximation. M21 releases cancelled inventory and M20 changes current inventory through existing services. Query responses load bounded rows, with no per-review/per-trip rating lookup. SQL aggregates derive rating without materializing review collections. Existing journey/operator/inventory indexes plus V21 visible-review/booking/recent indexes support queries. Window ranking chooses one cheapest valid fare journey per trip when discovery has no selected endpoints.

## Known limitations

No rich moderation, distribution chart, review notification, sponsored ranking or popularity analytics. Route discovery is deliberately a current-routes fallback. Profile route/type samples and search filter catalogues are bounded (30/100 and 100 operators); no full catalogue UI is included. Upcoming samples cover 30 days. No demo completed review seed is added. Existing inventory treats held seats as unavailable until the current expiry job releases them. No payment gateway, promotion, voucher, live tracking or V2-wide polish.

## Verification (2026-10-06)

| Check | Result |
| --- | --- |
| Compile/test compile | Passed |
| Focused M24A | 22 passed: 20 marketplace + 2 actual M20/M21 compatibility tests |
| Focused search | 9 passed |
| Focused M20 / M21 regressions | 28 / 15 passed |
| Backend mvn test | 56 passed, no failures/errors/skips |
| Backend mvn verify -Pmysql-integration (once) | 355 integration tests passed, no failures/errors/skips; unit phase also passed |
| Backend mvn package -DskipTests | Passed |
| Frontend npm test | 133 passed; also passed after inline delete confirmation change |
| Frontend npm run build | Passed, including final asynchronous select-option fix |
| git diff --check | Passed |
| Immutable migrations | All 20 original files match the configured HEAD checkout byte-for-byte (Git CRLF checkout filter applied); only V21 added |
| Exact database | SELECT VERSION(): 9.2.0 (MySQL Community Server), not 8.4 |
| Fresh schema / packaged startup | Empty busgo_m24_acceptance: all V1–V21 applied; Hibernate ddl-auto=validate initialized and packaged JAR started on 8090 |

Focused evidence: .tools/m24-focused.log (43 M20/M21 regressions passed), .tools/m24-focused-final.log (31 marketplace/search/compatibility passed). Final logs: .tools/m24-full-unit.log, m24-full-verify.log, m24-full-package.log, m24-full-frontend-test.log, m24-full-frontend-build.log, m24-delete-ui-test.log, m24-options-build.log, m24-startup.log. Offline Maven reused the installed dependencies; no dependency upgrades were made.

### Limited acceptance evidence and remaining UI checks

- Anonymous Home showed real active operators/routes/upcoming trips and truthful “Chưa có đánh giá” states. Opening An Phú from Home showed its public profile/routes/types/available trips.
- Customer booking UI created 5 stars, then edited to 3 stars/content. Public operator API verified averages 5.0/1 then 3.0/1.
- The in-app browser stalled on the native delete confirmation. The UI now uses an inline confirmation; frontend tests/build pass. A fresh browser tab supports navigation/read/selection changes, but mouse/keyboard submit actions remained unresponsive after the old dialog stalled. User was asked to dismiss it. Do not claim a completed all-UI acceptance matrix.
- Delete was verified through the local customer API: average null/count 0 and no public deleted review. The original review row is retained with deleted_at.
- Two dedicated demo bookings were legitimately held/booked/mock-paid through existing APIs. One had a real M21 partial cancellation before the acceptance fixture marked that trip COMPLETED. Remaining travelled item was eligible and created a 4-star review, giving average 4.0/count 1. This fixture is local acceptance data only; the production seeder is unchanged.
- Operator admin review read, response write and public profile edit passed through local APIs. Public operator browser page visibly showed the retained review and response. Staff read passed and staff response returned 403. Full tests cover foreign operator, customer ownership and SYSTEM_ADMIN denial.
- Combined operator/type/price/time filter URL rendered one matching trip with 20 segment-derived seats; selected controls matched the URL after fixing asynchronous option loading. Browser sort selection updated the backend query to PRICE_DESC. Local API ascending prices [220000,220000,250000,300000,300000] and descending [300000,300000,250000,220000,220000] were correct.
- Public profile at 390px and search at 1440px were inspected visually; document width stayed within viewport (375/390 and 1425/1440 respectively). No screenshot gallery generated.
- Remaining interactive browser verification: inline review delete confirmation; partial booking review submission; operator admin response/profile forms and staff read-only controls; mouse-driven filter application. Their APIs/domain/security checks passed. Resume only these checks after browser input recovers; do not restart implementation/full suites.

Acceptance artifacts (ignored): .tools/m24-browser-fixture.json, m24-api-acceptance.json. Test and acceptance schemas are isolated from existing application databases. Changes remain modified/untracked and uncommitted on feature/v2-marketplace-discovery. **NO COMMIT. NO PUSH. NO TAG.**
