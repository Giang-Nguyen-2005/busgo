# M16A — Operator assisted booking and collection

## Source audit and decisions

The existing customer flow resolves an exact active journey fare, holds the complete
seat × consecutive-segment matrix, and converts that owned hold into a PENDING
booking with BOOKED inventory. PENDING is already an unpaid reservation; it has no
automatic booking expiry. Successful payment moves it to CONFIRMED and creates one
ticket per booking item. V8 already enforces one PAID payment per booking and one
ticket per item. Operator booking reads use JDBC, customer ownership reads use JPA,
and operator context requires exactly one active membership in an active operator.

M16A reuses these semantics and services. No new booking status or placeholder
payment is introduced. Booking mutations stay OPERATOR_ADMIN only. OPERATOR_STAFF
keeps existing read access and cannot create bookings, record money, or issue links.
SYSTEM_ADMIN is rejected by operator context even when also holding an operator role.

## Data model and migration

V11 follows V10; V1–V10 are unchanged. Existing bookings receive source WEB and
payment method MOCK_ONLINE. Existing MOCK_QR payment rows become MOCK_ONLINE.
Booking source and intended payment method are immutable JPA columns. Constraints
allow WEB only with an account and MOCK_ONLINE; PHONE only without an account and
PAY_ON_BOARD or QR_TRANSFER. customer_id and contact_email become nullable.

Account holder, contact, passenger, and payer remain separate concepts. PHONE uses
contact name/phone and optional email; it never creates or links a customer account.
Passenger names stay nullable; the existing ticket fallback uses the contact name
without claiming verified passenger identity. Customer /bookings/me and detail
queries retain exact account ownership, so PHONE bookings cannot appear in unrelated
accounts. Anonymous mock payment has no authenticated payer or employee collector.

The intended method is on the booking, available before any transaction exists.
Actual successful payment records retain method, paid_at, optional employee actor,
and optional reference_note. Manual receipt references use COLLECT-, simulated
online references use MOCK-. An authenticated WEB customer is not stored as an
employee collector. No bank credentials, banking token, or screenshots are stored.

## Assisted flow

`/operator/bookings` → **Tạo đặt vé**, or trip workspace → **Đặt chỗ cho khách**.
Choose the Vietnam business date/trip (vehicle and departure visible), pickup and
forward dropoff, exact fare, one to five seats, contact, and payment choice. Review
the journey, seats, contact, method, unpaid state, and amount before creating.
The UI reuses the customer SeatMap geometry. Changing journey clears selected
seats; a conflict preserves contact data, refreshes availability, and asks for a
new selection. Trip preselection initializes its Vietnam date.

Create authorizes operator ownership first, then invokes SeatHoldService and the
existing BookingService hold conversion in one outer transaction. Employee identity
is only the internal temporary hold actor, never the customer account owner.
Any failure rolls back both the internal hold and booking. Complete inventory
validation, sorted inventory locks, expired-hold reclaim, active fare checks,
SCHEDULED/pickup-window checks, and non-overlapping reuse remain shared with WEB.
Concurrent requests serialize on the existing trip → operator → inventory order.

After creation the detail shows code, source, contact, journey, seats, amount,
payment choice, unpaid state, and no tickets. PAY_ON_BOARD says **Thu tiền khi khách
lên xe**. Staff can read these details but mutation controls are absent.

## Payment and tickets

**Ghi nhận đã thu tiền** opens a review; **Xác nhận đã thu tiền** records the full
booking amount with its immutable method, employee actor, time and optional note.
Both PHONE methods share this command. No partial payments are supported.
New confirmation follows the existing SCHEDULED/BOARDING lifecycle. During BOARDING
an employee may collect after the selected pickup time; this does not set a
passenger boarding/check-in state. DEPARTED/COMPLETED trips reject new collection.

QR_TRANSFER offers **Tạo link thanh toán**, a visible link, **Copy link thanh toán**,
and **Mở trang thanh toán**. Employees send it manually through their chosen channel.
PHONE is a booking source. Zalo is not a source and has no API integration: it is
only an external communication channel for manually sending a BusGo link.

The anonymous `/pay/:token` page has BusGo branding, booking code, operator, route,
pickup/dropoff and times, seats, amount, method, and explicit **mô phỏng thanh toán**
wording. It never displays a bank account or a bank-transfer QR. Confirmation uses
the same payment/ticket transaction as manual collection. A paid page removes the
confirmation action, remains readable, and directs the caller to the operator for
ticket delivery. Repeated confirmation returns the existing outcome.

Operator detail shows ticket code, passenger display name, seat and a QR for each
issued ticket. Ticket QR encodes the existing ticket code, as in WEB tickets.
Offline ticket delivery remains operator-assisted; there is no anonymous ticket
download, public booking management, or public ticket QR lookup API in this scope.

## Public link security and concurrency

Each link contains 32 SecureRandom bytes encoded as 43 URL-safe Base64 characters.
Only the SHA-256 hash is stored, with a unique, binary-collated CHAR(64) index.
The raw token is returned once to the authenticated employee. A fresh issuance
revokes the previous link; the UI explains this explicitly. Never persist raw
tokens in application storage or documentation.

Public GET/POST contracts are booking-scoped and contain no booking/database IDs,
account/contact details, employee actor, payment reference, or management fields.
Invalid/malformed/revoked tokens use the same 404. Responses are no-store with
no-referrer; the page also sets no-referrer and uses a separate Axios client that
never attaches account access or refresh tokens. Payment confirmation rechecks the
hash under the booking lock, including races against link revocation.

The payment lock order remains trip → operator → booking → booked inventory →
payment/tickets. Since assisted callers initially load a booking for authorization,
locking refreshes are required under MySQL REPEATABLE READ to observe current
committed status rather than a pre-lock snapshot. Manual/manual and manual/public
races therefore produce one successful payment, ticket set, and status history.
Inactive operator/association closes confirmation, including repeats, consistently
with WEB semantics. Active paid links remain idempotent after departure. Unpaid
public confirmation closes at pickup departure; cancelled/nonpayable status returns
409. No separate token TTL is introduced; explicit expiry metadata can be added later.

## Deferred scope

COUNTER/AGENT sources, capability grants for staff (M16B/M19), crew assignments,
passenger boarding/no-show, cancellation/refund, seat changes/rescheduling, reporting
screens (M18), real payment providers/banking QR, Zalo integration, and public ticket
delivery are deferred. Stored source/method/payment result and collection metadata
support truthful later reporting. Existing booking list payment-state filters remain;
additional source/method filters are deferred to keep this milestone focused.
