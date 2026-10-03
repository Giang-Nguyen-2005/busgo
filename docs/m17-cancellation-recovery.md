# M17 — Cancellation and reservation recovery

## Source audit

The implementation was audited against M16A/M16B source rather than inferred from
roadmap wording. Bookings already support PENDING, CONFIRMED, CANCELLED and
COMPLETED; payments support PENDING, PAID, FAILED and REFUNDED. PENDING bookings
own BOOKED inventory through booking_items, even without a payment or ticket.
Tickets have a unique booking_item_id and are issued only by successful payment.
PHONE bookings have no account; WEB ownership is customer_id. History already
records old/new status, user actor, note and timestamp. V14 adds reason_code.

Attendance is booking-item based, with nullable ticket_id; explicit ticketless
PHONE PAY_ON_BOARD NO_SHOW is terminal. NO_SHOW does not release inventory.
Payment, attendance, pickup closure and lifecycle mutations serialize on the trip
row. Public links hold only a hashed opaque token; token rotation is checked again
under the booking lock before confirmation. Operator context uses one active staff
membership with an active operator and rejects SYSTEM_ADMIN.

## State machine and authority

Whole bookings only: PENDING → CANCELLED or CONFIRMED → CANCELLED. Cancellation is
terminal; there is no reopen, partial item cancellation or arbitrary status PATCH.
Repeating an authorized cancellation returns its committed state, preserving
timestamps, refund, ticket void metadata and history without another release.

CUSTOMER can cancel their own WEB booking up to and including six hours before
the **selected pickup stop's planned departure**, using UTC snapshot timestamps
and existing BusGo Vietnam display utilities (Asia/Ho_Chi_Minh). Origin time is
not substituted. A missing pickup time rejects customer cancellation. The trip
must still be SCHEDULED or BOARDING, pickup must be open, and every item must have
no attendance or EXPECTED attendance. CHECKED_IN, BOARDED or NO_SHOW rejects the
whole booking. Planned times are not an invented operational arrival record.

OPERATOR_ADMIN can cancel owned WEB or PHONE bookings in SCHEDULED/BOARDING until
trip departure or explicit pickup closure, whichever is first. The customer
six-hour cutoff does not apply. Any CHECKED_IN, BOARDED or terminal NO_SHOW still
rejects. Read-only staff see eligibility/history but cannot cancel. SYSTEM_ADMIN
has no operator-context cancellation authority. Foreign booking IDs use 404;
invalid state uses 409 and request note validation uses 400.

## Atomic cancellation, refund and ticket validity

Unpaid cancellation creates no payment, ticket or refund. It preserves contact,
booking items and history. Paid cancellation requires a complete, consistent paid
payment/ticket set. It creates one full refund record, changes payment to REFUNDED
without changing original amount or paid_at, and marks every ticket VOID with
voided_at/by/reason. The transaction releases the inventory and appends one
booking_status_history transition with actor, reason, note and time.

Refunds are **Hoàn tiền mô phỏng**. No bank transfer or real reimbursement occurs.
The dedicated refunds table stores amount, payment_id, original payment_paid_at,
refunded_at/by, reason_code, note and created_at. Unique payment_id prevents a
duplicate. A composite foreign key to payment ID/amount/paid_at enforces a full
original amount and non-null successful timestamp. Payment constraints require
PAID/REFUNDED to have paid_at and PENDING/FAILED to have no paid_at. The service
requires PAID before inserting the refund and changes it to REFUNDED atomically.
PAYMENT_TIMEOUT is not an allowed refund reason. No refund edit API exists.

Tickets remain readable historically. Customer ticket reads return VOID status
and voidedAt with qrData:null after paid cancellation. Operator detail returns
ticket.status. Both UIs suppress void QR codes; operations explicitly require
VALID in addition to existing eligible booking/payment relationships. Attendance
rows and operational history are preserved and never reversed.

## Inventory validation and locking

Cancellation resolves required segments from pickup/dropoff snapshots. It locks
linked allocation rows in trip-seat, segment-order and inventory-ID order. Each
item must have exactly the complete expected segment list, the correct trip seat
in the same trip, BOOKED status and no hold metadata. Missing, extra, mismatched,
BLOCKED or held allocation aborts the whole transaction before writes. Releases
use ID, booking_item_id and BOOKED predicates, clear booking linkage and increment
version. Unrelated HELD/BLOCKED cells and non-overlapping reservations survive.

Final cancellation order: trip → operator → booking → payment rows by ID → ticket
rows → attendance/pickup closure → inventory rows in deterministic order.
The existing payment path retains trip → operator → booking → inventory → payment/
tickets/attendance; operations retain their trip-first commercial/attendance
reads. These child orders cannot conflict between commands on a trip because the
exclusive trip lock comes first in every path. No new reverse child-to-trip path
is introduced. Booking/trip/operator entities refresh under locking current reads
to defeat an assisted caller's pre-lock MySQL REPEATABLE_READ snapshot.

If cancellation wins, payment rejects BOOKING_NOT_PAYABLE before inventory
validation. If payment wins, cancellation sees CONFIRMED and creates one refund
and void set. Attendance winning blocks cancellation; cancellation winning blocks
attendance. Closure cannot pass unresolved bookings; cancelled bookings no longer
block closure. Each manual cancellation and expiry runs in one transaction.

## Payment deadlines and scheduled expiry

New WEB bookings use 15 minutes; PHONE QR_TRANSFER uses 30 minutes, capped at
selected planned pickup departure. Positive durations are configurable through
busgo.booking.web-payment-window and phone-payment-window. PAY_ON_BOARD has a null
deadline and is never expired for being unpaid. This is separate from seat-hold
expiry. V14 does not backfill or cancel historical PENDING rows: null deadlines
are deliberately ignored.

New confirmation for a PENDING booking rechecks payment_due_at after acquiring
locks and rejects at/after the deadline. Successful paid retries keep their
existing idempotency. Expiry rechecks current PENDING state, meaningful deadline,
payment absence and operational eligibility under the same locks. If payment
committed first, expiry does nothing; if expiry committed first, payment cannot
resurrect the booking. Expiry reasons are PAYMENT_TIMEOUT with a null actor.

PaymentExpiryJob runs every minute after a one-minute initial delay, selecting a
bounded batch (default 100; configurable 1..1000) ordered by payment_due_at/id.
Each booking uses a separate transactional service call. It ignores null legacy
deadlines, PAY_ON_BOARD, paid/cancelled rows, departed/completed trips, closed
pickups and CHECKED_IN/BOARDED/NO_SHOW. Inconsistent allocations are logged and
rolled back, not silently released. Configuration: payment-expiry-interval,
payment-expiry-initial-delay and payment-expiry-batch-size under busgo.booking.

## Suspension recovery and public links

An owning customer can cancel an eligible unpaid WEB reservation while its
operator is inactive. This releases stranded inventory without granting new
commerce. Paid customer cancellation during suspension is deferred to platform
support; operator management remains blocked by active membership rules. PHONE
recovery without a customer account is deferred during suspension. Suspension
itself does not cancel bookings. Deadline recovery continues independently of
operator activation but still respects operational guards.

Cancelled links may show the minimal existing booking context and CANCELLED state;
confirmation rejects and cannot reissue tickets. No actor, reason, note or refund
internals are exposed publicly. Rotated tokens retain their uniform 404 behavior.

## API and UI

POST /api/v1/bookings/{id}/cancel and
POST /api/v1/operator/bookings/{id}/cancel accept {note?:string}, maximum 500
characters. Reason is server-derived from command authority. They return the
committed Recovery representation. GET on the corresponding /recovery paths
returns eligibility, cutoff/deadline, cancellation metadata, refund summaries,
ticket validity and ordered history. Existing booking detail also adds recovery.

Customer detail offers Huỷ đặt chỗ or Huỷ và hoàn tiền mô phỏng only when eligible.
Operator detail has an explicit cancellation section with eligibility, actor,
reason, time and refund state. The confirmation dialog shows code, trip/journey,
pickup, seats, contact, amount, paid state, policy and resulting release/void/mock
refund. Existing CANCELLED booking and REFUNDED payment list filters are retained;
cancelled records stay searchable. No table redesign or reporting screen is added.

## Migration and deferred work

V14 is additive after V13; V1–V13 are unchanged. It adds cancellation metadata,
nullable payment_due_at, structured history reason, ticket validity, refunds and
indexes/constraints. Pre-existing inconsistent payment timestamps must be resolved
before applying the added check; migration does not rewrite such legacy records.
No trigger privileges, schema reset or seeded-booking cancellation is required.

Deferred: partial cancellation/refund, seat change, reschedule, trip-wide batch
cancellation, real bank refund/gateway, accounting integration, support workflow
for suspended paid/PHONE bookings, attendance reversal and reporting screens.
Stored timestamps/reasons support M18 gross/refund/net/timeout reporting later.
