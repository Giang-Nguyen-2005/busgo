# M18C — Fleet readiness and maintenance

## Source audit

| Audit | Actual source |
| --- | --- |
| A. BusStatus | AVAILABLE, MAINTENANCE, INACTIVE; no ACTIVE bus value. Bus type separately uses ACTIVE/INACTIVE. |
| B. Assignment | Required Trip.bus FK; TripAggregateCreator validates ownership, AVAILABLE, usable type, derives arrival from final route-stop offset, locks bus, snapshots trip/seat/inventory. |
| C. Change after creation | No bus reassignment/update command. Trip lifecycle PATCH changes status only. |
| D. Assignment history | None. Assignment is immutable, so no assignment-history table is added. |
| E. Unusable buses | Owned, undeleted AVAILABLE bus and active type required; existing non-CANCELLED trip overlaps block creation. |
| F. BOARDING | M16B checks AVAILABLE/undeleted bus, active type, eligible driver/capability/licence and crew overlaps; locks trip, bus, ascending employees. Maintenance composes with this. |
| G. Trip windows | Origin departure AND estimated arrival, half-open intervals. Actual arrival is unknown. |
| H. Planned arrival | Required estimated_arrival_time with arrival > departure; final route-stop offset determines it, independent of route estimated duration. |

Audit sources: Bus/BusStatus/BusService/BusRepository, Trip/TripController/TripService/
TripAggregateCreator/TripRepository, OperationsService, OperatorTripOperationsService,
OperatorContextService/SecurityConfig, existing operator fleet UI, V1–V15 and the
M16B/M17/M18A/M18B design documents. Audit preceded implementation.

Adjustments: AVAILABLE replaces suggested ACTIVE; maintenance end is mandatory;
assignment history is omitted because assignments cannot change. Fleet was admin-only,
so staff fleet reads remain denied. V1 licence plate uniqueness is global; M18C
preserves it instead of introducing same-plate cross-operator duplicates.

## Maintenance model and lifecycle

V16 adds bus_maintenance_records and append-only bus_status_history. Maintenance
ownership derives through bus.operator_id. Typed bounded DTOs and JDBC follow
existing operational/reporting conventions; new dates use JpaJdbcTime UTC binding
and reading, matching Hibernate-written trip/bus timestamps. No entity is exposed.

Types: PERIODIC_SERVICE, OIL_CHANGE, TIRE, BRAKE, ELECTRICAL, ENGINE,
AIR_CONDITIONING, INSPECTION, REPAIR, OTHER; Vietnamese labels in UI.
Title <=150, note/completion note <=1000, cancellation reason <=500. The form asks
operators to enter operational information and avoid sensitive personal data.

Explicit commands only:

- SCHEDULED -> IN_PROGRESS (start).
- SCHEDULED -> CANCELLED (cancel).
- IN_PROGRESS -> COMPLETED (complete).

Repeated commands or reopening terminal records reject 409 without changing original
metadata/timestamps. No status PATCH or DELETE. Completion/cancellation retain the
original schedule/title/note, timestamps and actor metadata. Complete optionally
accepts recorded odometer >=0, next due date >=current Vietnam business date,
next due odometer >=0 and strictly greater than recorded odometer when both exist,
and a completion note. Odometer is manual metadata, never current synchronized mileage.

## Time and trip protection

Required future-or-now start and strictly later end. Offset input -> UTC microseconds;
API output has UTC offset. Frontend input/display always uses Asia/Ho_Chi_Minh,
regardless of browser timezone.

Overlap: maintenanceStart < tripEstimatedArrival AND tripDeparture < maintenanceEnd.
Back-to-back is allowed. Scheduling checks SCHEDULED/BOARDING/DEPARTED owned trips
and other scheduled maintenance, and rejects a bus with active maintenance.
409 safe details include owned trip ID, route, departure, arrival and status.
Trips are never silently cancelled/reassigned. Since assignment is immutable, choose
a different maintenance window/bus rather than attempting to change an existing trip.

Trip creation preserves status/type/ownership/existing trip-overlap guards and checks
active/overlapping planned maintenance under the same bus scheduling lock.
IN_PROGRESS blocks all assignments until completion, even beyond its planned end.
M16B boarding and crew replacement during BOARDING compose maintenance with existing
crew/type/status/licence rules. A scheduled conflict returns an actionable warning.

Start may be early: expand conflict start to the earlier of now and planned start;
end remains scheduled end. Overlapping other planned maintenance blocks early start.
An expired planned end rejects start; cancel and make a new schedule. Any assigned
BOARDING/DEPARTED trip blocks start regardless of planned arrival because actual
arrival is unknown. Record/status/history writes commit atomically.

## Readiness, due dates and restoration

Ready means AVAILABLE, active bus type, no IN_PROGRESS maintenance and no scheduled
interval containing now. Future maintenance outside now does not disable the bus.
Response includes status, maintenanceState, activeMaintenanceId, earliest planned
date/type, earliest explicit due date, next nonterminal trip and warnings.

Warnings follow real data: maintenance, inactive/type inactive, planned window now,
trip-plan conflicts, due soon and overdue. No fabricated missing-service warning.
Most recent COMPLETED record per maintenance type supersedes that type's old due
metadata, including null/unknown. Unrelated tyre work cannot erase a periodic due date.
Explicit date before Vietnam today is overdue; today through today+7 is due soon.
Future scheduled work within the next 7*24 hours is also upcoming. No reliable current
odometer exists, so recorded or next-due odometer never generates overdue warnings.

Start stores prior bus status and sets MAINTENANCE. Complete restores AVAILABLE only
when prior status proves AVAILABLE and current status remains MAINTENANCE. Originally
INACTIVE returns to INACTIVE; manually changed INACTIVE stays INACTIVE. Originally
MAINTENANCE stays MAINTENANCE until explicit restoration. While a record is active,
admin may mark INACTIVE but cannot override it to MAINTENANCE or restore AVAILABLE.
Scheduling/cancelling a plan does not change bus status.

Status history records real old/new status, reason, maintenance ID, actor and UTC time,
including existing bus PATCH changes. No-op status updates create no history.
No update/delete/history backfill; workspace shows latest 100 changes. Maintenance
history retains terminal records separately. No assignment history is justified.

## Security and concurrency

OPERATOR_ADMIN only, exactly one active membership with active operator. Staff retains
existing trip/crew reads but cannot access fleet. SYSTEM_ADMIN including mixed roles,
customers and public are denied. Anonymous 401; role/context denial 403; foreign or
missing owned bus/record/history/trips 404. No operatorId query selection. Internal
guards receive only bus IDs validated through owned bus/trip paths; conflict rows
are scoped to the trusted operator. No foreign actor/contact/profile fields.

READ_COMMITTED on maintenance/status writes and both TripService.create and
TripAggregateCreator prevents a route/context pre-lock read from becoming a stale
REPEATABLE_READ planning snapshot. One lock order:

1. Check ownership, identify existing nonterminal trips for this owned bus.
2. Lock trip rows by ascending ID.
3. Lock and refresh bus.
4. Re-read candidates. A newly inserted trip not already locked returns
   FLEET_PLAN_CHANGED (409), rolling back; reload/retry from the beginning. Never
   acquire an additional trip lock after the bus.
5. Lock record if applicable, revalidate, mutate, append status history.

Trip creation locks bus then inserts its NEW row; it never locks a preexisting trip.
Lifecycle retains trip -> bus -> ascending employees; maintenance never locks employees.
All relevant existing trip rows are locked when mutation correctness depends on them.
A concurrent new trip cannot evade planning checks or introduce a reverse lock path.

## API, UI and reports

See api-contract.md for routes/envelopes. Existing buses append readiness and preserve
paging. New lists wrap PagedResponse in ApiResponse. Maintenance filters: owned bus,
status, type, inclusive Vietnam scheduled-start date range, page 0–100000, size 1–100.
IN_PROGRESS first, SCHEDULED start ascending, completed/cancelled recent descending;
stable ID ties. Assigned trips show nonterminal upcoming first then recent history.

Directory: compact desktop table; responsive phone cards below 640px. Bus workspace:
Tổng quan, Lịch chuyến (paging), Bảo trì (filters/forms/actions), Lịch sử.
`/operator/maintenance` is the separate fleet maintenance list. Explicit start/complete/cancel use inline confirmation forms.
Conflict details link to unchanged trips; loading/error/empty states remain explicit.
Bus selectors fetch all pages, not only the first 100 buses.

Dashboard adds one Cảnh báo đội xe section: physical status counts, due soon, date
overdue and up to 20 upcoming unready/conflicting trips in seven days. Status counts
do not assert crew readiness. Staff does not mount this query. M18A financial/report
contracts remain unchanged; separate fleet counts avoid mixing current lifetime
readiness with date-filtered financial cohorts.

## Indexes, query limits and deferred scope

New indexes: maintenance(bus_id,status,scheduled_start), maintenance(status,scheduled_start),
maintenance(bus_id,status,completed_at,id), history(bus_id,changed_at,id).
Reuse V4 trips(bus_id,departure_time,estimated_arrival_time,status); no duplicate trip
index, odometer indexes or edits to V1–V15.

Fleet page <=100 buses. Readiness reads active planning, latest completion per type
and nonterminal assignments. Dashboard iterates owned pages with per-bus reads; this
is correct at local fixture scale but still has per-bus queries. Large-fleet batching,
EXPLAIN/production profiling and cursor history are deferred. No production benchmark
claim. Actual travel windows and immutable-assignment recovery remain limitations.

Deferred: parts/inventory/warehouse/fuel, payroll, GPS/telematics/IoT, insurance,
depreciation/accounting, predictive AI, procurement/vendors, synchronized odometer,
repair costs/invoices/payments, exports and staff fleet reads.
