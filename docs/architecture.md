# BusGo V1.5 architecture

BusGo is a React application backed by a Spring Boot modular monolith and MySQL.
Customer, operator and platform administration are separate routed workspaces.

```text
Browser (React / TypeScript / React Router / TanStack Query)
    -> same-origin /api/v1 reverse proxy
    -> Spring Security JWT -> controllers -> transactional domain services
    -> JPA + scoped JDBC -> MySQL (Flyway V1–V16)
```

Frontend routes load on demand. Query data is temporary; sessionStorage holds
tab-scoped access/refresh tokens and the current hold. Logout clears session,
hold and query cache. Requests and responses carry an in-memory session version
so an old account's completion cannot populate the replacement session. Shared
Vietnam-time/VND helpers and domain-specific status presentation avoid conflating
reservations, payment, tickets and attendance.

The backend modules cover auth/users, operators/staff, catalogue/routes/fares,
fleet, trips, holds, bookings, payment/tickets, operations, customers and reports.
Controllers return DTOs rather than persistence entities. Security checks both
roles and current database identity. Operator services resolve exactly one active
membership; SYSTEM_ADMIN is explicitly excluded from operational context, including
mixed-role identities. Customer commerce validates ownership. Public payment links
are narrowly scoped opaque capabilities with minimal DTOs and revocation.

Trip creation snapshots stops, seats, segments and prices. Inventory is one row per
seat/segment, permitting reuse across non-overlapping journeys. Holds expire;
booking consumes a hold or atomically reserves PHONE inventory. An unpaid PHONE
booking is already a reservation. Full simulated payment issues one ticket per
item. Eligible whole-booking cancellation releases inventory, voids tickets and
records one full mock refund when paid. Payment expiry never manufactures refunds.

Operational employees are separate from login staff accounts. Crew assignments,
per-item attendance and explicit pickup closure govern trip progress. Ticketless
PAY_ON_BOARD no-shows remain resolvable without fabricating payment/tickets.
Fleet maintenance composes with trip/crew readiness. Future plans do not disable
current availability; active maintenance blocks assignment/boarding. Completion
preserves deliberate INACTIVE status. Mutation order stays existing trips in ID
order -> bus -> maintenance (or employees), and new-trip creation inserts under
the bus lock. Read-only fleet pages batch planning data in groups of at most 100.

Reports reduce payments, refunds and attendance independently before combining
them. Collections/refunds use event dates; load uses departure cohorts and
seat-segments. Cancelled trips are excluded from aggregate load, missing inventory
suppresses percentages, and attendance counts reflect actual recorded cohorts.
Customer management reads only this operator's booking contact snapshots; exact
account IDs group bookings while accountless contacts remain one per booking.

Flyway owns the schema and Hibernate validates it at startup. No M19 migration or
old migration rewrite. UTC persistence/API instants and Asia/Ho_Chi_Minh business
dates are distinct. Current health is minimal liveness, not a database readiness
claim. Compose supplies MySQL, not a complete production hosting stack.

There is no real bank settlement, gateway, messaging infrastructure, GPS, account
ledger or trip reassignment. Query-time reporting and retained nonterminal planning
history require production-scale profiling before large deployments.
