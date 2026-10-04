# M18B — Operator customer management

## Source audit and identity

M16A contact_name/phone/email snapshots are separate from nullable customer_id.
WEB requires an account; current PHONE creation intentionally leaves customer_id
null. M16B attendance is unique per booking item with optional ticket; unpaid
PAY_ON_BOARD may have explicit NO_SHOW. M17 retains successful original payments
as REFUNDED and stores dedicated refunds, cancellation reason/time and VOID tickets.
M18A uses JDBC aggregates and trusted active operator membership. Full operational
booking detail remains the destination for deeper context.

Keys are ACCOUNT:{customer_id} and CONTACT:{booking_id}: internal IDs, never raw
phone/email or a reversible phone hash. ACCOUNT groups exact account IDs only
inside the current operator. CONTACT is one accountless booking snapshot; PHONE
bookings with identical phones remain separate. No normalization merges, fake
accounts, customer master table or mutation endpoints. A contact is not verified
human identity. Latest booking contact drives directory display; earlier snapshots
remain unchanged and searchable. Account holder, contact, passenger and payer stay
distinct. No users/profile joins occur.

## API and access

- GET /api/v1/operator/customers?q=&customerType=&sort=&page=0&size=20
- GET /api/v1/operator/customers/{customerKey}?page=0&size=20

ApiResponse wraps PagedResponse<Summary> for the directory and
{summary,bookings:PagedResponse<BookingHistory>} for detail. customerType is
ACCOUNT or OFFLINE_CONTACT. Keys validate typed positive signed-long IDs without
leading zero. Foreign/nonexistent keys return indistinguishable 404; malformed
keys/filters return 400. UTC timestamps include explicit offsets.

Admin-only: OperatorContextService requires exactly one active membership with
active operator and rejects SYSTEM_ADMIN, including mixed-role users. Staff support
is deferred; staff sees no navigation, mounts no customer queries and cannot read
either endpoint. Public/customer/system callers cannot read. Every query starts
from bookings → trips → operator_routes, using solely trusted operator context.
No operatorId request filter, authentication data, role, security status, unrelated
profile or foreign activity is exposed.

## Search, pagination and sorting

Case-normalized literal substring search covers booking contact name, phone, email
and code. Query is trimmed, raw length bounded at 150. SQL LIKE %, _ and escape
characters are escaped. Existing MySQL collation applies; no fuzzy infrastructure
or phone normalization. Historical matches return the entire owned account
aggregate, not just matching bookings. Foreign snapshots cannot match.

Default LATEST desc; NAME asc, BOOKINGS desc and MOCK_PAID desc are enums. All add
customer_key asc as tie-breaker; history orders created_at desc/id desc. Page is
0..100000 (default 0), size 1..100 (default 20). Read-only REPEATABLE_READ keeps
each response's count/rows consistent. Offset pages are stable on a fixed dataset;
new activity can reorder later requests, with no cross-request snapshot promise.

## Query safeguards, indexes and performance

NamedParameterJdbcTemplate CTEs aggregate successful original payments, dedicated
refunds and items/tickets/attendance independently to booking grain, then combine
only single-row booking totals. No money/item multiplication, DISTINCT(amount) or
one query per customer. Latest contact uses window rank with timestamp/ID ties.
Boarded journeys count distinct trip IDs with at least one BOARDED booked item.

Directory uses two queries (count/page). Detail uses one summary, one booking-ID
page, two batched payment/refund reads limited to those IDs and one history page;
empty pages skip event reads. Account detail binds customer_id and contact detail
booking ID, using existing indexes. Events expose method/status/amount/timestamps/
reason, omitting employee actors and free-text notes.

No migration/index added. V7 has bookings(customer_id,created_at), trip and item
indexes; existing operator-route/trip, unique ticket/item, attendance/item,
payment/booking, refund/payment and V15 booking-created indexes cover joins.
Leading-wildcard LIKE scans at fixture scale and would not benefit from speculative
ordinary contact indexes. Directory lifetime aggregation scans the owned cohort;
production volume/EXPLAIN profiling and cursor pagination are deferred. No claims
of production benchmarks or materialized customer analytics.

## Money and attendance

Lifetime grossMockPaid sums original PAID/REFUNDED amounts with successful paid_at;
mockRefunds sums dedicated refunds; netMockPaid is gross minus refunds. Refunded
payments remain in gross; unpaid cancellation contributes zero. Thu mô phỏng,
Hoàn tiền mô phỏng and Thu ròng mô phỏng follow M18A transaction semantics without
a date window. They are not actual revenue or customer lifetime revenue.

Recorded item counts are boarded, noShow, checkedIn and unrecorded (missing or
EXPECTED). Mixed multi-seat states and ticketless PAY_ON_BOARD no-show remain
visible. No fabricated attendance or inferred travel from tickets/trip completion.
Historical recorded states remain visible. Counts represent booked items, not
verified unique passengers; boardedJourneys does not prove the contact/account
holder travelled. confirmedBookings is exact current CONFIRMED status; COMPLETED
stays in total/history. Source counts use actual immutable WEB/PHONE values.

## UI and deferred scope

One admin navigation item Khách hàng. Directory has search/type/sort, desktop table,
mobile cards below 640px and pagination. Detail has latest contact, type badge,
booking/status/source/money/boarding summaries, compact booking history, original
contact snapshots, seats/journey/status/ticket/attendance/cancellation, expandable
payment/refund events and links to existing booking detail. History pagination is
independent. Existing session/cache cleanup, loading/error/empty states apply.
No edit/delete controls or separate history navigation.

Deferred: staff support reads, verified contact grouping, customer mutation,
marketing/campaigns/segmentation/loyalty/promotions/email/SMS/pipelines/sensitive
notes, cross-operator profiles, CRM masters, exports, production profiling and
actual payment gateways. Zalo remains external sharing, not a booking source.
