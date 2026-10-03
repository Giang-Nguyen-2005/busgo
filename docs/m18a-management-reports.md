# M18A — Operator management reports and analytics

## Source audit

M16A has immutable WEB/PHONE booking source and intended payment method. PENDING
bookings already reserve BOOKED inventory; booking totals are not passenger counts.
Successful payment issues one ticket per booking item. M17 keeps original amount
and paid_at on REFUNDED payments, stores full refunds independently, voids tickets,
and persists cancelled_at and structured cancellation_reason. M16B/V13 attendance
is unique per booking item and may have no ticket for PAY_ON_BOARD NO_SHOW. Missing
or EXPECTED attendance is not a recorded no-show. Trips own immutable seat/segment
snapshots through operator_routes; route membership is operator-owned. Existing
BusGoTime and JpaJdbcTime handle Vietnam dates and Hibernate UTC Calendar storage.

## Architecture and access

ReportController → ReportService → ReportRepository (NamedParameterJdbcTemplate).
Three bounded, typed endpoints: summary, trips, routes. The summary consolidates
collections, bookings/tickets, cancellations, attendance, load, and operations.
No report tables, warehouse, scheduled aggregates, mutable analytics state, or
charting dependency. Read-only REPEATABLE_READ transactions provide a consistent
snapshot across each response's constant number of aggregate queries.

Only OPERATOR_ADMIN with exactly one active membership and an active operator can
read reports. SYSTEM_ADMIN (including mixed-role accounts) is explicitly rejected
by OperatorContextService. Staff/customer/public callers cannot read financial or
operational report endpoints. No operatorId filter or bypass exists. Foreign route
or trip filters produce empty contributions; routes return only owned associations.

## Filters and dates

Required ISO fromDate and toDate are inclusive business calendar dates at
Asia/Ho_Chi_Minh. Reversed/missing ranges and more than 366 inclusive dates reject
with HTTP 400. Optional routeId is the global route ID (not operatorRouteId), tripId,
bookingSource WEB/PHONE, and paymentMethod MOCK_ONLINE/QR_TRANSFER/PAY_ON_BOARD.
Trip/route detail supports page (default 0; 0–100000) and size (default 20; 1–100).
Filters/pagination are typed and bound; no user-supplied SQL or expression is accepted.

BusGoTime converts dates to [local midnight of fromDate, local midnight following
toDate) in UTC. Every JPA-written date predicate uses JpaJdbcTime.parameter and
planned-departure reads use JpaJdbcTime.read. No DATE(timestamp), server/JVM default
business date, or database timezone conversion is used. Daily transaction buckets
use the same bound business-date intervals, zero-filled for all selected dates.

Every response has metadata: fromDate, toDate, timezone, asOf (UTC offset timestamp),
dateBasis, and echoed route/trip/source/method filters. Clients can request a prior
period with the same filters; percent changes are not fabricated and no comparison
field is emitted. Snapshot/asOf times can differ between separate endpoint calls.

## Exact metrics

| Metric | Date basis / numerator | Denominator, exclusions, incomplete behavior |
| --- | --- | --- |
| grossMockCollections | Sum original payments.amount with paid_at in window, status PAID or REFUNDED | Payment grain; later refunds do not reduce past gross; no pending/failed payments |
| mockRefunds | Sum refunds.amount with refunded_at in window | Refund grain, independent of original paid_at; includes older payments |
| netMockCollections | Window gross minus window refunds | Can be negative; simulated collections, never settlement, accounting, profit or actual revenue |
| paidPaymentCount | Successful original payment events by paid_at | Includes now-REFUNDED payments |
| refundedPaymentCount | Refund events by refunded_at | One full-refund record per payment in M17; not count of past paid cohort now refunded |
| bookingsCreated | COUNT booking IDs with bookings.created_at in window | One multi-seat booking is one booking; current status/source breakdown; no item/ticket joins |
| cancellations / paymentTimeouts in bookings | Current CANCELLED / PAYMENT_TIMEOUT in booking-created cohort | Cohort state, not cancellation event flow |
| currentUnpaidCount | PENDING booking-created cohort with no PAID/REFUNDED payment | Cancelled unpaid bookings excluded |
| ticketsIssued | Tickets.created_at in window, VALID and VOID | Separate ticket-issued cohort, not booking-created cohort and not transported passengers |
| validTickets / voidTickets | Current VALID / VOID within ticket-issued cohort | Historical issuance remains visible after voiding |
| totalCancellations | CANCELLED bookings.cancelled_at in window | Reasons CUSTOMER_CANCELLED, OPERATOR_CANCELLED, PAYMENT_TIMEOUT; legacy missing reason is UNRECORDED, missing cancellation timestamp is outside event flow |
| refundedCancellations | Cancellation-date cohort having dedicated refunds | Lifetime associated refund amount; not independently time-filtered again |
| unpaidCancellations | Cancellation-date cohort with no successful payment | PAYMENT_TIMEOUT is never fabricated as a refund |
| amountRefunded | Lifetime refund amount associated with cancellation-date cohort | Distinct from refunds flowing in the selected transaction window |
| reservedSegmentLoad | BOOKED expected seat-segment cells on non-CANCELLED trips linked to bookings matching source/method filters | Expected complete snapshot matrix minus BLOCKED cells on those same trips; includes unpaid reservations; capacity is not reduced by booking filters |
| paidSegmentLoad | BOOKED expected cells on non-CANCELLED trips whose matching booking is CONFIRMED/COMPLETED and has PAID payment | Same sellable denominator; excludes CANCELLED trips and cancelled/refunded/unpaid bookings; no distinct-seat shortcut |
| eligibleResolvedTickets | Eligible VALID, PAID tickets on non-cancelled CONFIRMED/COMPLETED bookings with recorded BOARDED or NO_SHOW | Travel cohort by planned origin departure, not attendance-event timestamp |
| boardingRate | boardedTickets | eligibleResolvedTickets; null when denominator 0 |
| noShowRate | noShowTickets | Same resolved ticket denominator; null when denominator 0 |
| checkedInNotBoarded | Eligible tickets with CHECKED_IN | Kept separate; excluded from resolved denominator |
| unresolvedAttendance | Eligible paid tickets and PENDING ticketless booking items with missing or EXPECTED attendance | Chưa ghi nhận; never converted to no-show |
| ticketlessNoShows | Explicit NO_SHOW on ticketless PENDING booking items | Separate booking-item count; excluded from ticket boarding/no-show rates |

### Load completeness and seat reuse

Travel cohort is trips.departure_time, the planned origin departure. Expected cells
are trip seat snapshots × trip segment snapshots. LEFT JOIN preserves missing
inventory. Completeness also requires positive seat/segment counts, N stops / N−1
segments, and each segment connecting consecutive ordered stops of the same trip.
Topology failure or absent inventory means complete=false, both load ratios=null,
and wholeTripAvailableSeats=null. missingCells counts absent cells in the existing
snapshot matrix; it may be zero while a topology defect makes the trip incomplete.
The report cannot reconstruct a historical snapshot seat deleted in its entirety;
it does not compare against editable current bus types as invented past capacity.

Sellable cells = expectedCells − BLOCKED cells. Missing cells never lower expected
capacity. HELD cells are shown separately and never count as sales. If no sellable
capacity exists, load ratios are null/not applicable even when complete=true. Empty known counts
are zero. Non-overlapping reuse of one seat contributes independent segment cells.
Whole-trip availability remains a separate count of seats with every segment AVAILABLE.

Trip and route ratios aggregate SUM(numerator)/SUM(denominator), never average
percentages. Only non-CANCELLED trips contribute any aggregate load field: expected,
actual, missing, sellable, reserved, paid and held cells, or completeness. Any
incomplete non-cancelled contributing trip suppresses the aggregate percentage;
incomplete cancelled inventory cannot poison another trip's ratio. If every trip
is cancelled, aggregate load counts are zero, complete=true (no incomplete
contributor), and both ratios are null/not applicable, never 0%.

Cancelled trips remain in departure-cohort rows/counts/statuses and all historical
booking, ticket, collection/refund and cancellation reporting. Individual CANCELLED
trip rows retain inventory counts/completeness as diagnostics, but both load ratios
are always null; the UI says Không áp dụng and explicitly states exclusion from
aggregate load. Whole-trip available seats remain a separate inventory diagnostic.
No future predicted or actual-travel utilization is claimed. No global trip-status
filter is applied to transaction dates, booking-created or ticket-issued cohorts.

### Route / trip performance

Departure-filtered tables attribute *lifetime* booking counts, currently VALID
tickets, successful original collections, refunds, and recorded attendance to
those trips. This is explicitly different from the transaction-date collections
report. Route rows include zero-activity owned routes, tripCount for all selected
statuses and operatedTripCount for DEPARTED/COMPLETED. Trip rows include planned
departure, current lifecycle status, bus plate, load completeness and separate
whole-trip available seats. Attendance and load use the same travel cohort.

Operations show selected trips, BOARDING/DEPARTED counts, future SCHEDULED/BOARDING
trips without an active assigned DRIVER, open ACTIVE pickup-enabled stops on active
trips, and incomplete trip counts. Driver assignment is an overview warning, not
the comprehensive crew licence/bus/overlap readiness decision from M16B.

## Query safeguards and indexes

Ownership is rooted in trips → operator_routes with trusted current operator ID.
Source/method filters use a booking cohort. Payments and refunds never join items,
tickets or inventory before summation. Travel performance independently groups
booking, ticket, payment, refund, attendance and inventory domains to one row per
trip before combining them. Attendance has unique ticket/item relationships.
EXISTS is used for payment eligibility and cancellation refund existence; no
DISTINCT(amount) workaround. There are no per-trip network/JPA query loops.
Trip page selection occurs before expensive matrix aggregation; route page
selection similarly narrows contributing trips. The summary is deliberately
unpaginated aggregates bounded to 366 dates, with at most 366 daily buckets.

V15 adds only five nonduplicated range indexes after inspection of V1–V14:
bookings(created_at,trip_id), bookings(cancelled_at,trip_id),
payments(paid_at,booking_id), refunds(refunded_at,payment_id),
tickets(created_at,booking_id). Existing trip/operator-route departure, inventory
seat-segment, booking-item, ticket booking/item, refund payment and attendance item
indexes cover domain joins. No original migration is changed.

## Frontend

One admin navigation item Báo cáo at /operator/reports, with Tổng quan, Thu tiền,
Đặt vé & vé, Chuyến / Tuyến, Hành khách sections. Shared presets Hôm nay / 7 ngày /
30 ngày / Tháng này / Tuỳ chọn, inclusive date inputs, route/trip/source/method
filters and validation. Lightweight CSS source/status charts and SVG collections
versus refunds chart accompany exact compact tables. Long chart periods are grouped
into at most 30 consecutive date bins; exact daily values remain in the trend table.
Horizontal scrolling is local to tables; summaries/filters stack on mobile.

Admin dashboard uses the same summary service for today, adds booking/ticket/mock
money metrics, WEB/PHONE and unpaid cohorts, driver/pickup/attendance warnings and
incomplete inventory. Existing operational shortcuts and today's schedule remain.
Staff never mount financial queries, see report navigation, or get backend access.
Loading, empty and failure states do not substitute fabricated zeros for failed
queries. Authorization loss uses existing session clearing and query-cache cleanup.

## Deferred scope

CSV (optional stretch), XLSX/PDF, explicit comparison calculations, customer directory
(M18B), platform BI, profit/accounting/settlement, actual revenue, prediction/AI,
passenger-km, driving hours, warehouse/materialized tables, top-route ranking and
extended dashboard trend panels. The reports page provides route performance and
daily trends. Production-scale profiling, production migration and physical-device
accessibility acceptance remain separate from local verification.
