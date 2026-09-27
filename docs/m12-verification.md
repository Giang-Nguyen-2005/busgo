# M12 backend P0 verification

## Implemented surface

- `GET /api/v1/operator/bookings`
- `GET /api/v1/operator/bookings/{bookingId}`
- `GET /api/v1/operator/trips/{tripId}/passengers`
- `GET /api/v1/operator/trips/{tripId}/occupancy`
- `PATCH /api/v1/operator/trips/{tripId}/status`
- Operational payment guard on mock confirmation
- Trip-first mutation locking for holds, bookings, payments, and status changes

## Domain invariants

- `trip_seat_segment_inventory` remains authoritative.
- `PENDING` booking inventory remains `BOOKED`.
- Occupancy is returned per seat and segment; whole-trip availability is derived.
- Manifest includes `CONFIRMED` and future `COMPLETED` bookings, never `PENDING`.
- Ticket passenger names are reported separately from nullable booking-item names.
- Operator ownership is included in read/lock queries and foreign IDs return 404.

## Automated coverage

`M12OperatorOperationsIT` covers authorization/isolation, booking filters and
pagination, Vietnam business date, detail relationships and name distinction,
manifest selection and seat reuse, all inventory states and counts, forward-only
transitions, and the payment lifecycle guard.

`M12OperationsConcurrencyIT` races boarding against hold/booking creation and
departure against payment confirmation using separate committed transactions.
The acceptable result depends on lock acquisition order: a customer mutation that
commits first may succeed; once the transition commits, the mutation fails with
the relevant lifecycle error.

Run with Java 17 and the real MySQL dev profile:

```powershell
$env:JAVA_HOME='C:\Users\Giang\.jdks\ms-17.0.16'
mvn -Dtest=M12OperatorOperationsIT,M12OperationsConcurrencyIT test
mvn test
```

Record actual command results in the implementation handoff; this document does
not claim success before those commands finish.

## Verification result (2026-09-27)

- `mvn test`: 24 tests passed, 0 failures/errors.
- `mvn verify -Pmysql-integration`: 24 unit tests and 124 MySQL integration tests
  passed, 0 failures/errors.
- The strengthened M12 operator suite was rerun after adding the explicit
  `17:30 UTC -> next Vietnam business date` boundary assertion: 5 passed.

## M12 frontend implementation and verification (2026-09-27)

Implemented routes:

- `/operator/bookings`: backend pagination; URL-backed search, trip, booking
  status, payment status, Vietnam creation date, page and page size.
- `/operator/bookings/:bookingId`: account/contact, trip, stops, nullable
  per-seat passenger names, actual ticket fields and payment history.
- `/operator/trips/:tripId/passengers`: confirmed/completed booking rows with
  separate seat passenger, ticket name and booking contact fields.
- `/operator/trips/:tripId/occupancy`: authoritative seat-by-segment matrix,
  segment counts, whole-trip availability, booking links and hold expiry.

Trip detail links to these views and offers only the next lifecycle transition.
A native modal explains sales closure, payment-window closure or terminal status
before confirmation. Pending requests disable repeat submission. Success and
failure invalidate operator trips (including manifest/occupancy) and bookings;
this also refreshes stale state after a competing operator's transition.

New operations views map `INVALID_TRIP_STATUS_TRANSITION`,
`PAYMENT_WINDOW_CLOSED`, `TRIP_NOT_FOUND` and `BOOKING_NOT_FOUND` to Vietnamese,
with a generic retry message for unknown failures. No raw server code/message
is used as the main error label. Tables support horizontal scrolling; the
occupancy matrix retains readable column widths and a sticky seat column.

Actual frontend checks:

- `npm test`: 17 passed (5 existing fare tests, 12 new operations tests).
  Includes real component server rendering, nullable passenger/ticket cases,
  manifest empty/reused-seat rows, ID-aligned segment cells, filter parsing and
  page resets, backend pagination, Vietnamese labels/timezone, API requests,
  transition mapping/confirmation markup, known errors and mutation invalidation.
- `npm run build`: TypeScript and Vite production build passed. Existing Zod
  annotation warnings and a >500 kB main bundle advisory remain. Initial
  sandboxed Vite configuration loading failed due to filesystem access;
  the approved build outside that sandbox succeeded.
- `git diff --check`: passed.
- No separate customer frontend regression test script/suite was present.
  The existing test suite and complete frontend build were run; customer
  sources and routes were not changed by this frontend implementation.

Manual review completed: compared frontend types/request envelopes to Java DTOs,
controllers and query semantics; reviewed route nesting, name distinctions,
matrix identity alignment and lifecycle impacts. No live browser or authenticated
end-to-end verification was performed. Mobile layout, dialog keyboard/focus
behavior and real backend success/conflict/network flows still need browser QA.
Backend tests recorded above were from the backend work and were not rerun for
this frontend-only change. No backend source changes, dependency additions,
commits or pushes were made by the frontend implementation.

Suggested remaining browser checks:

1. Sign in as an operator; filter bookings, advance pages, reload and use Back.
2. Open a booking with null per-seat names and compare ticket/contact labels.
3. Inspect empty and populated manifests and a seat reused on different segments.
4. At desktop and narrow mobile widths, scroll wide tables without page overflow.
5. Cancel then confirm each allowed lifecycle action; test a competing transition
   and verify detail/list/manifest/occupancy refresh. Check modal Escape and focus.
6. Smoke-test the customer search/hold/booking/payment routes against live data.

Deferred: check-in, boarded/no-show states, verified identity, passenger editing,
refund/cancellation actions, seat blocking mutations, automatic transitions,
dashboard metrics and M14 visual redesign.
