# BusGo Product Roadmap

## 1. Product Vision

BusGo is an online bus operation management and ticket booking platform.

The system has three main areas:

- Customer: search trips, select seats, create bookings, make payments and receive electronic tickets.
- Operator: manage vehicles, routes, trips, bookings, passengers, seat inventory and staff.
- System Admin: manage transport operators and platform-level access.

The primary focus of the project is transportation operation management.
Customer booking provides transaction data for the management system.

---

## 2. Current State — V1 Core

Completed or substantially implemented:

### Customer
- Authentication
- Trip search
- Trip filtering
- Seat selection
- Segment-based seat inventory
- Seat hold
- Booking creation
- Mock payment
- Electronic tickets / QR
- Booking history
- Customer profile

### Operator
- Operator dashboard
- Fleet management
- Bus type management
- Route management
- Fare management
- Trip management
- Booking management
- Seat board
- Passenger manifest
- Segment occupancy
- Trip lifecycle
- Operator staff management
- OPERATOR_ADMIN and read-only OPERATOR_STAFF roles

### Platform Administration
- SYSTEM_ADMIN
- Operator directory
- Operator creation
- Operator activation/deactivation
- Staff visibility
- Multi-operator-ready architecture

### Technical Foundation
- Spring Boot backend
- React + TypeScript frontend
- MySQL
- Flyway
- JWT authentication
- Role-based authorization
- Operator data isolation
- Segment-based inventory
- Pessimistic locking
- Demo data seeding
- Backend integration tests
- Frontend regression tests

---

## 3. V1.5 — Management Upgrade

Primary goal:
Strengthen BusGo as a management-oriented system.

Planned areas:

### Operations
- Driver management
- Bus assistant / attendant management
- Driver and assistant assignment to trips
- Boarding / passenger check-in
- No-show handling
- Improved trip operation workflow

### Booking Management
- Stronger booking administration
- Booking status history
- Cancellation flow
- Seat/trip change flow where appropriate
- Better payment/ticket state management

### Customer Management
- Customer directory
- Booking history by customer
- Customer activity overview

### Reporting and Analytics
- Revenue by day / week / month
- Ticket sales
- Seat occupancy
- Route performance
- Trip performance
- Operator performance
- Exportable management reports where useful

### Fleet Operations
- Vehicle operating status
- Trip assignment history
- Maintenance information where appropriate

---

## 4. V2 — Multi-Operator Marketplace

BusGo evolves from a management system with multi-operator support into a full marketplace platform.

Planned areas:

- Multiple transport operators competing in customer search
- Rich operator profiles
- Operator comparison
- Customer ratings/reviews if appropriate
- Promotions and coupons
- Cancellation and refund workflows
- Real payment provider integration
- Platform commissions
- Operator settlements
- Advanced platform administration
- Advanced analytics
- Notifications
- Richer customer home content
- Destination and recommendation content

---

## 5. Final Hardening Phase

Before final submission/demo:

- Feature freeze
- Full regression testing
- Security and permission review
- Database/index review
- Performance review
- Responsive testing
- Accessibility checks
- Production deployment
- Stable demo dataset
- Demo accounts
- Demo rehearsal
- Documentation
- Final report
- Presentation slides
- Demo video

---

## 6. Final Product Positioning

BusGo should be presented as:

> BusGo — an online bus operation management and ticket booking platform.

The management system is the primary focus.

Customer booking creates transactions and operational data.

Operator tools manage day-to-day transportation operations.

System Admin manages the overall multi-operator platform.

## M16A implementation — 2026-10-02

Operator-assisted PHONE booking and unpaid reservations are implemented alongside
existing WEB commerce. Employees can record PAY_ON_BOARD/QR_TRANSFER collection;
anonymous opaque links support explicitly simulated payment, with operator-assisted
ticket delivery. Zalo remains an external manual communication channel. Creation and
collection are admin-only; staff capabilities move to M16B/M19. Boarding, crew,
cancellation/refunds, rescheduling, real gateways and reporting remain future scope.
See [M16A design](m16a-assisted-booking.md) and [verification](m16a-verification.md).

## M16B.1 + M16B.2 implementation

Crew and passenger operations now have their own domain: operational employees
separate from accounts, multiple duty assignments with overlap protection, ready
crew before boarding, per-booking-item attendance and explicit pickup closure. Boarding
at open intermediate stops remains possible after origin departure. Manual PHONE
collection reuses M16A and is required before check-in/boarding. Unpaid PHONE
PAY_ON_BOARD reservations may be explicitly marked NO_SHOW without tickets;
terminal NO_SHOW blocks later new payment for that booking. No-show preserves the
booking, payment and reserved segment inventory. Admins mutate; staff read.
Details and verification: m16b-crew-boarding.md and m16b-verification.md.
Camera scanning, driver mobile, cancellation/refunds, staff mutation capabilities,
GPS, payroll, rest rules and actual-arrival tracking remain deferred.

## M17 implementation — 2026-10-03

Whole-booking cancellation and reservation recovery now extend WEB/PHONE commerce.
Eligible customers cancel until six hours before their selected pickup; operator
admins cancel before departure/pickup closure with attendance guards. Paid
cancellation voids tickets and records a full simulated refund. New WEB/QR payment
deadlines enable bounded recovery; PAY_ON_BOARD and legacy null deadlines stay
reserved. Eligible unpaid WEB customer recovery remains available during operator
suspension. Reporting screens, real refunds, partial changes and rescheduling remain
future scope. See [M17 design](m17-cancellation-recovery.md) and
[verification](m17-verification.md).

## M18A implementation — 2026-10-03

Operator admins now have bounded query-time management reports and dashboard
aggregates: mock collections/refunds/net flows, booking and ticket cohorts,
WEB/PHONE, payment methods, trip/route seat-segment load and recorded attendance.
Vietnam business dates and explicit transaction/departure bases prevent misleading
money or transported-passenger claims. Incomplete inventory suppresses percentages.
One Báo cáo page provides shared filters, charts and paginated management tables.
M18B customer directory, CSV, calculated comparisons, real revenue/settlement,
prediction/AI and platform BI remain future scope. Design and evidence:
[M18A reports](m18a-management-reports.md), [verification](m18a-verification.md).

## M18B implementation — 2026-10-03

Operator admins now have a read-only customer support directory and booking history
at /operator/customers. Exact account IDs group only operator-owned bookings;
accountless PHONE contacts remain separate per booking, even with repeated phones.
Latest booking-local contact snapshots, simulated gross/refund/net amounts and
recorded per-item attendance preserve identity and privacy boundaries. Staff support
reads and marketing CRM remain deferred. No new table/index. See
[M18B design](m18b-customer-management.md) and [verification](m18b-verification.md).

## M18C implementation — 2026-10-04

Operational fleet readiness, bounded planned maintenance and explicit
start/complete/cancel commands extend operator management. Existing AVAILABLE /
MAINTENANCE / INACTIVE statuses, planned departure/arrival conflicts, M16B readiness,
operator isolation and immutable trip assignments remain domain truth. Status history
and safe restoration preserve intentional inactivity; explicit date due metadata
powers upcoming/overdue warnings. Fleet directory, bus workspace, maintenance list
and one dashboard fleet warning section are implemented. M18A cohorts stay unchanged.
See [M18C design](m18c-fleet-maintenance.md) and [verification](m18c-verification.md).
Staff fleet access, actual mileage/arrival, accounting, parts, GPS/IoT, predictive AI
and procurement remain deferred.

## M19 — V1.5 feature freeze and release readiness (2026-10-04)

M16A–M18C business scope is feature-complete. M19 adds targeted terminology,
assisted-booking clarity, shared statuses/errors, session response isolation,
cancellation keyboard focus, route splitting and read-only fleet batching.
No new module, migration, staff privilege or payment semantics. Earlier “future”
notes are milestone history; current scope is [V1.5 release notes](v1.5-release-notes.md).
See [audit](m19-audit.md), [verification](m19-verification.md),
[demo script](v1.5-demo-script.md), [deployment](deployment.md) and
[architecture](architecture.md). M19 changes must remain uncommitted/unpushed.

## M20 — Booking modification (2026-10-05)

Implemented partial same-trip seat changes and whole-booking trip changes, authoritative quotes/eligibility, target holds, simulated cash adjustment, atomic switch, affected ticket replacement, immutable history and shared customer/operator workspace. Booking identity and V1.5 ownership/source semantics remain. Verification and compatibility limitations: [M20 report](m20-booking-modification.md). M21/M22 remain separate; no real gateway or settlement is included. Changes remain uncommitted and unpushed.

## M21 — Partial cancellation / partial refund (2026-10-05)

Implemented same-booking subset cancellation, authoritative quotes, simulated partial refunds using M20 accounting, immutable history and shared customer/operator detail workflow. M20 seat/trip changes and full cancellation now operate on remaining active items. V19 is additive; V1–V18 stay immutable. M22+ and M29 visual polish remain deferred. Verification: [M21 report](m21-partial-cancellation.md). No commit, push or tag.
