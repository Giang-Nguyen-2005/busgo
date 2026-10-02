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
