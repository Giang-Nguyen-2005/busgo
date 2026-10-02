BUSGO – DEVELOPMENT PLAN & CODEX MILESTONES v1
1. Mục tiêu tài liệu
Tài liệu này dùng để hướng dẫn Coding Agent triển khai BusGo theo từng giai đoạn nhỏ.
Nguyên tắc:
Không code toàn bộ project trong một lần.
Mỗi milestone phải:
    1. Hoàn thành đúng scope.
    2. Build thành công.
    3. Test thành công.
    4. Không phá milestone trước.
    5. Commit rõ ràng.
    6. Chỉ chuyển milestone tiếp theo khi Definition of Done đạt.

2. Stack chính thức
Backend
Java
Spring Boot
Spring Security
Spring Data JPA
MySQL
JWT
Maven
Frontend
React
TypeScript
Tailwind CSS
TanStack Query
React Router
Axios
React Hook Form
Zod
Infrastructure
Docker Compose
MySQL
Git
Không sử dụng:
Microservices
Kafka
Redis
Kubernetes
RabbitMQ
trong V1.

3. Repository Structure
Repository đề xuất:
busgo/
│
├── backend/
├── frontend/
├── database/
├── docs/
├── docker-compose.yml
├── .gitignore
└── README.md
Docs:
docs/
├── requirements.md
├── database-design.md
├── api-contract.md
├── ui-specification.md
└── development-plan.md

4. Backend Package Structure
com.busgo
│
├── common
│   ├── config
│   ├── exception
│   ├── response
│   ├── security
│   └── util
│
├── auth
├── user
├── operator
├── location
├── route
├── fleet
├── trip
├── booking
├── payment
└── reporting
Mỗi module nên có:
controller
service
repository
entity
dto
mapper
Không bắt buộc module nào cũng phải có toàn bộ package nếu không cần.

5. Frontend Structure
src/
├── api/
├── app/
├── components/
├── features/
│   ├── auth/
│   ├── search/
│   ├── trip/
│   ├── booking/
│   ├── profile/
│   └── operator/
├── layouts/
├── pages/
├── routes/
├── hooks/
├── types/
└── utils/

6. Development Order
Thứ tự triển khai:
M0 Project Setup
↓
M1 Core Database
↓
M2 Authentication
↓
M3 Operator + Fleet + Route
↓
M4 Trip Generation
↓
M5 Search
↓
M6 Seat Inventory
↓
M7 Seat Hold
↓
M8 Booking
↓
M9 Payment + Ticket
↓
M10 Customer Frontend
↓
M11 Operator Frontend
↓
M12 Reporting
↓
M13 Integration & Hardening
↓
M14 Demo / Seed / Documentation

MILESTONE 0
PROJECT FOUNDATION
Objective
Tạo project chạy được nhưng chưa có business logic.

Backend tasks
Khởi tạo Spring Boot với:
Spring Web
Spring Data JPA
Spring Security
Validation
MySQL Driver
Lombok
Khuyến nghị thêm:
Flyway
để quản lý migration.

Frontend tasks
Khởi tạo React TypeScript.
Cài:
React Router
Axios
TanStack Query
Tailwind
React Hook Form
Zod

Docker
Tạo:
docker-compose.yml
chứa MySQL.
Ví dụ DB:
busgo_db

Backend config
Tạo profile:
application.yml
application-dev.yml
Secrets không commit trực tiếp.

Common API response
Tạo:
ApiResponse<T>
PagedResponse<T>
ApiError

Exception handler
Tạo global:
@RestControllerAdvice
xử lý:
validation
business exception
not found
unauthorized
unexpected error

Health Check
Endpoint:
GET /api/v1/health
Response:
{
  "data": {
    "status": "UP"
  }
}

M0 Tests
Backend starts.
Frontend starts.
Frontend build succeeds.
MySQL connection succeeds.
Health endpoint returns 200.

Definition of Done
docker compose up
khởi động được DB.
mvn test
pass.
npm run build
pass.

MILESTONE 1
CORE DATABASE MODEL
Objective
Triển khai schema nền tảng.
Không triển khai booking logic ở milestone này.

Tables
Tạo migrations cho:
users
roles
user_roles

transport_operators
operator_staff

locations

routes
route_stops
operator_routes
operator_route_fares

bus_types
seat_templates
buses

Entities
Implement JPA Entity tương ứng.
Không expose Entity trực tiếp qua REST.

Enum
Tạo enum:
UserStatus

OperatorStatus

BusStatus

RouteStatus

RoleCode
SeatType

Constraints
Đảm bảo các constraint:
users.email UNIQUE

transport_operators.code UNIQUE

route_stops(route_id, stop_order) UNIQUE

route_stops(route_id, location_id) UNIQUE

operator_routes(operator_id, route_id) UNIQUE

seat_templates(bus_type_id, seat_code) UNIQUE

buses.license_plate UNIQUE

Seed minimum roles
Migration seed:
CUSTOMER
OPERATOR_STAFF
OPERATOR_ADMIN
SYSTEM_ADMIN

Tests
Repository integration test cho:
duplicate email
duplicate license plate
duplicate route stop order

Definition of Done
Schema có thể tạo từ database rỗng chỉ bằng migrations.

MILESTONE 2
AUTHENTICATION & USER
Objective
Hoàn thành authentication trước các module nghiệp vụ.

APIs
Implement:
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh

GET /api/v1/users/me
PATCH /api/v1/users/me
POST /api/v1/users/me/change-password

Security
Implement:
BCrypt
JWT access token
refresh token
Spring Security filter
role authorization
Không lưu plaintext password.

Register
Registration mặc định:
CUSTOMER

JWT Claims
Chỉ chứa dữ liệu cần thiết:
userId
roles
Không đưa password hoặc thông tin nhạy cảm vào token.

Tests
register success
duplicate email rejected
login success
wrong password rejected
protected API without token → 401
invalid role → 403
refresh token works

Definition of Done
User có thể:
register
→ login
→ access /users/me
bằng JWT.

MILESTONE 3
OPERATOR, FLEET & ROUTE
Objective
Cho Operator Admin xây dựng dữ liệu vận hành cần để tạo chuyến.

Seed development operator
Development seed:
GiangBus
và một:
OPERATOR_ADMIN
Không hard-code ID.

Location APIs
Public:
GET /api/v1/locations?q=
Operator Admin có thể có API quản lý location nếu cần.
Trong V1 có thể seed location để giảm scope admin.

Bus Type APIs
GET    /api/v1/operator/bus-types
GET    /api/v1/operator/bus-types/{id}

BusType và SeatTemplate là global master data chỉ đọc đối với OPERATOR_ADMIN trong
M3. Quản trị global master data được hoãn cho SYSTEM_ADMIN/V2; V1 dùng seed/test data.

Bus APIs
GET   /api/v1/operator/buses
POST  /api/v1/operator/buses
GET   /api/v1/operator/buses/{id}
PATCH /api/v1/operator/buses/{id}

Route APIs
GET   /api/v1/operator/route-catalog
GET   /api/v1/operator/route-catalog/{routeId}
GET   /api/v1/operator/routes
POST  /api/v1/operator/routes
GET   /api/v1/operator/routes/{operatorRouteId}
PATCH /api/v1/operator/routes/{operatorRouteId}

Route và RouteStop là global master data chỉ đọc đối với OPERATOR_ADMIN. POST chỉ
attach một global Route hiện có bằng `routeId`; PATCH chỉ cập nhật OperatorRoute status.

Fare APIs
GET /api/v1/operator/routes/{id}/fares
PUT /api/v1/operator/routes/{id}/fares

Route validation
Backend validate:
>= 2 stops

no duplicate location

stop order valid

estimated offset increasing

fare fromStop < toStop

Operator isolation
Mọi API operator phải resolve:
currentOperator
từ authenticated staff.
Không cho request gửi:
operatorId
rồi tin giá trị đó.

Tests
Admin A cannot access operator B data.

Create bus type with seat layout.

Create bus.

Create route with stops.

Create route fare.

Invalid stop order rejected.

Definition of Done
Admin có thể dùng global BusType/SeatTemplate và Route/RouteStop đã seed, tạo Bus,
attach OperatorRoute và cấu hình Fare bằng API.

MILESTONE 4
TRIP GENERATION ENGINE
Objective
Tạo Trip và toàn bộ snapshot dữ liệu liên quan.
Đây là milestone backend quan trọng.

New tables
Migration:
trips
trip_stops
trip_segments
trip_seats
trip_seat_segment_inventory

API
GET  /api/v1/operator/trips
POST /api/v1/operator/trips
GET  /api/v1/operator/trips/{id}

CreateTripService
Tạo một application service chịu trách nhiệm toàn bộ transaction.
Flow:
Validate OperatorRoute
↓
Validate Bus ownership
↓
Validate Bus active
↓
Validate schedule conflict
↓
Create Trip
↓
Snapshot RouteStops
↓
Create TripStops
↓
Create TripSegments
↓
Snapshot SeatTemplates
↓
Create TripSeats
↓
Create SeatSegmentInventory

Trip Stop Time
Tính:
trip.departureTime
+
routeStop.estimatedOffsetMinutes
để sinh planned time.

M4 decision:
Client chỉ gửi OffsetDateTime `departureTime`; backend chuẩn hóa UTC và tính Trip
estimatedArrivalTime từ offset của active RouteStop cuối. Stop đầu có arrival null,
stop cuối có departure null, stop giữa có arrival/departure cùng thời điểm offset.
Không yêu cầu route.estimated_duration_min bằng final stop offset.

Inventory generation
Ví dụ:
22 seats
5 stops
=>:
4 segments
88 inventory rows

Bus Schedule Conflict
Implement query phát hiện overlap.
Logic cơ bản:
newStart < existingEnd
AND
newEnd > existingStart
cho cùng bus.
Khoảng thời gian mới dùng arrival do backend tính. Việc lock bus trước khi kiểm tra và
tạo aggregate tuần tự hóa các request tạo trip cho cùng một bus.

Inventory V4 có future hold columns nullable và optimistic-lock `version`; M4 chỉ tạo
AVAILABLE rows với hold fields NULL. `booking_item_id` được hoãn tới booking migration.

Tests
Bắt buộc:
Trip generates correct stop count.

Trip generates N-1 segments.

Trip generates correct seat count.

Inventory count =
seatCount × segmentCount.

Bus schedule overlap rejected.

Bus from another operator rejected.

Definition of Done
Một request tạo trip sinh đầy đủ snapshot và inventory chính xác.

MILESTONE 5
CUSTOMER TRIP SEARCH
Objective
Hoàn thành nghiệp vụ tìm chuyến.

API
GET /api/v1/trips/search
GET /api/v1/trips/{tripId}

Search input
pickupLocationId
dropoffLocationId
departureDate

Search conditions
Trip được trả khi:
pickup exists
dropoff exists

pickup.allowPickup = true
dropoff.allowDropoff = true

pickup.order < dropoff.order

trip status = SCHEDULED
và thời gian phù hợp ngày tìm kiếm.

M5 decision: `departureDate` là calendar date tại `Asia/Ho_Chi_Minh`, chuyển thành
UTC half-open interval và đối chiếu với selected pickup TripStop plannedDepartureTime.
Không dùng JVM default timezone và không chỉ lọc Trip.departureTime.

Fare
Search service resolve giá theo:
operator_route_fares
cho pickup/dropoff.
Nếu chưa có fare:
search loại Trip đó; customer Trip Detail trả TRIP_NOT_BOOKABLE.
Không tự cộng giá segment nếu requirement chưa quy định.

availableSeats là số TripSeat có inventory AVAILABLE trên TẤT CẢ required segments.
Inventory BOOKED ngoài đoạn không ảnh hưởng; M5 chỉ đọc và không reserve inventory.

Search filters
Implement sau core search:
operatorId
busTypeId
minPrice
maxPrice
departureFrom
departureTo
sort

Customer Trip Detail bắt buộc pickupLocationId và dropoffLocationId theo cặp.
Search/detail là public GET; operator management vẫn yêu cầu OPERATOR_ADMIN.

Search tests
Case A
Operator A:
Đắk Lắk → Đà Nẵng

Search:
Đắk Lắk → Hà Nội

=> A không xuất hiện.
Case B
Route:
Hà Nội → Đắk Lắk

Search:
Đắk Lắk → Hà Nội

=> không xuất hiện.
Case C
Pickup stop exists
but allowPickup=false

=> không xuất hiện.

Definition of Done
Search trả đúng trip dựa vào stop thực tế chứ không dựa đơn thuần origin/destination route.

MILESTONE 6
SEAT AVAILABILITY ENGINE
Objective
Hoàn thành engine tính ghế theo segment.
Chưa hold ghế.

API
GET /api/v1/trips/{tripId}/seats
Parameters:
pickupLocationId
dropoffLocationId

Endpoint là public GET. Backend resolve TripStop từ Trip snapshot; operator API và
mọi mutation endpoint vẫn được bảo vệ.

Required Segment Resolver
Tạo reusable service:
TripSegmentResolver
Input:
pickupStop
dropoffStop
Output:
all segment IDs between them

Seat availability
Seat AVAILABLE khi:
EVERY required inventory row
status = AVAILABLE
Nếu bất kỳ row:
BOOKED
HELD
BLOCKED
=> seat unavailable.

Customer response trả layout `TripSeat` snapshot và boolean `available`; không expose
raw inventory status hoặc dữ liệu hold. Response có exact ACTIVE journey fare theo
cùng resolution của M5 và `availableSeatCount` bằng số seat true. Journey hợp lệ sold
out vẫn trả HTTP 200/count 0. Đây chỉ là observational read, không reserve ghế.

Tests
Non-overlap
A01 BOOKED
Đắk Lắk → Đà Nẵng

request:
Đà Nẵng → Hà Nội

=> AVAILABLE
Overlap
A01 BOOKED
Đắk Lắk → Huế

request:
Đà Nẵng → Hà Nội

=> UNAVAILABLE

Definition of Done
Seat availability chính xác cho tất cả cặp pickup/dropoff.

MILESTONE 7
SEAT HOLD & CONCURRENCY
Objective
Ngăn double booking.

API
POST   /api/v1/seat-holds
GET    /api/v1/seat-holds/{token}
DELETE /api/v1/seat-holds/{token}

M7 decision: không thêm `seat_holds`. V4 inventory fields đủ biểu diễn active/stale-expired
logical hold vì một token bao phủ complete seat × consecutive-segment matrix; boundaries được
khôi phục từ first/last TripSegment snapshot. Release/cleanup xóa metadata nên token lịch sử
trả 404. V6 chỉ thêm index `trip_seat_segment_inventory(hold_token)` cho GET/release.

Hold transaction
BEGIN

resolve segments

SELECT inventory
ORDER BY trip_seat_id, trip_segment_id
FOR UPDATE

verify exact expected row count and every row AVAILABLE or expired HELD

update rows:
AVAILABLE → HELD
with one UUID, authenticated owner, and captured expiry

COMMIT

Configuration
busgo.booking.seat-hold-duration=PT10M
busgo.booking.max-seats-per-hold=5

Expiration job
Scheduled job:
every 1 minute
release expired hold bằng predicate UPDATE tại database. Create vẫn reclaim expired relevant
rows dưới lock; M5/M6 không mutate và có thể xem stale expired HELD là unavailable đến cleanup.

Concurrency Test
Bắt buộc chạy test:
User A
User B

attempt same seat simultaneously

exactly one succeeds.
Đây là test không được bỏ.

M7 còn verify multi-seat all-or-nothing, missing inventory fail-closed, active hold không bị
steal (kể cả same user), và cùng physical seat có thể giữ đồng thời trên non-overlapping segments.

Definition of Done
Không thể giữ cùng một seat trên cùng overlapping segment bởi hai customer cùng lúc.

MILESTONE 8
BOOKING CORE
Objective
Chuyển active hold thành booking.

Tables
Migration:
bookings
booking_items

M8 không tạo payment, ticket hoặc booking_status_history; các phần lifecycle đó thuộc M9+.

APIs
POST /api/v1/bookings

GET /api/v1/bookings/me
GET /api/v1/bookings/{id}

Cancellation chưa thuộc M8.

Booking code
Generate server-side.
Format có thể:
BG + date + sequence/random
Ví dụ:
BG260925A4X9
Không cần sequence quá phức tạp.
Phải unique.

Create booking flow
find hold
↓
validate owner
↓
validate ACTIVE
↓
validate not expired
↓
lock HELD inventory
↓
recalculate fare
↓
create booking
↓
create booking items
↓
convert HELD inventory → BOOKED, link booking item, clear hold metadata

Booking price
Backend tính:
fare × numberOfSeats
nếu mọi seat cùng giá trong V1.
Không lấy total từ frontend.

Tests
expired hold cannot create booking

foreign hold cannot create booking

booking total is calculated server side

same hold can create exactly one booking under concurrent requests

non-overlapping segments remain reusable

Definition of Done
Active owned hold được chuyển atomically thành PENDING booking với BOOKED inventory.
Payment, confirmation, cancellation và ticket được giữ cho M9.

MILESTONE 9
PAYMENT & TICKET
Objective
Hoàn thành full customer business flow.

Table
payments

tickets

booking_status_history

Payment methods
MOCK_QR

CASH và real gateway để milestone sau.

APIs
POST /api/v1/bookings/{id}/payments/mock-confirm

GET /api/v1/bookings/{id}/ticket

MOCK_QR flow
Booking PENDING
Inventory BOOKED từ M8

↓ confirm

Payment PAID
Booking CONFIRMED
Inventory BOOKED
Booking status history và một Ticket/BookingItem được tạo

Toàn bộ phải nằm trong một transaction có booking row lock, inventory consistency check,
database uniqueness và idempotent retry. Hai confirm đồng thời cùng booking đều có thể trả cùng
kết quả đã commit, nhưng chỉ một Payment PAID và một ticket cho mỗi item tồn tại.

Ticket
Response chứa:
bookingCode
operator
pickup
dropoff
departure
seatCodes
passenger
paymentStatus
qrValue

QR
Frontend generate QR từ:
ticketCode (`qrData`)
Không lưu image QR/PDF trong DB.

Deferred
Real gateway, CASH settlement, cancellation/refund, email và SMS.

Definition of Done
Flow:
Search
→ Seat
→ Hold
→ Booking
→ Mock Payment
→ Ticket
hoàn thành bằng API.

MILESTONE 10
CUSTOMER FRONTEND
Implementation status (2026-09-20): customer flow implemented in `frontend/`.
Includes all eleven customer routes, server-backed location/trip search,
snapshot seat selection (1–5 seats), verified holds, contact booking, mock payment,
QR tickets, booking history/detail, profile and password change.
Vietnamese responsive layouts target 1440px, 1024px and 390px.
API settings/run commands and route map: README.md, section M10 customer frontend.
Audit and verification evidence: docs/m10-verification.md.
Operator frontend remains M11; cancellation, refund, real payment and PDF are
outside this M10 implementation.
Objective
Xây toàn bộ flow khách hàng.

Pages
/
 /login
 /register
 /search
 /trips/:id
 /booking
 /payment
 /booking-success
 /profile
 /my-bookings
 /my-bookings/:id

Phase 10A – Authentication UI
Implement:
login
register
auth context
protected route
token refresh
logout

Phase 10B – Search UI
Implement:
LocationAutocomplete
TripSearchForm
SearchResult
TripCard
Filters
Sort

Phase 10C – Trip & Seat
Implement:
Trip detail
Pickup/dropoff selection
SeatMap
SeatLegend
Seat selection
Hold API

Phase 10D – Booking
Implement:
Booking form
Countdown
Passenger names
Payment method

Phase 10E – Payment / Ticket
Implement:
Mock QR
Payment confirm
Booking Success
Ticket

Phase 10F – My Bookings
Implement:
list
details
cancel

Customer UI DoD
Không cần Postman để hoàn thành toàn bộ customer flow.

MILESTONE 11
OPERATOR FRONTEND
Implementation status (2026-09-27): supported frontend scope implemented;
production build and focused tests pass. Authenticated live acceptance testing
is pending provision of an OPERATOR_ADMIN account with active operator membership.
See docs/m11-verification.md for exact checks, limitations and follow-up steps.

The current Java controllers/DTOs define the M11 scope and supersede the broader
original UI wishlist. No backend changes, database changes or new API contracts.

11A: Separate lazy /operator route tree, OPERATOR_ADMIN-only guard, responsive
sidebar/topbar, read-only profile disclosure, logout and quick-action home.
Unauthenticated navigation preserves returnTo; authenticated users without
OPERATOR_ADMIN (including OPERATOR_STAFF) receive an explicit 403 state.

11B: Paginated bus search/filter, create and partial edit of plate/type/status.
Read-only active bus types and seat-template preview. No master-data editor.

11C: Owned route list/detail, global active catalog, attach/reactivate and
activate/deactivate association, ordered stops and complete fare-set replacement.
Fare saving explicitly reviews every intended row; omitted rows become inactive.
An empty replacement is supported with an explicit confirmation warning.

11D: Paginated trips with date/routeId/busId/status filters, create and read-only
detail with stop timeline, segment summary and snapshot seats. The operator date
filter is UTC per TripService; creation input/display use Vietnam time. Snapshot
seats do not represent live availability or occupancy.

Deferred: operator bookings/actions, passenger manifests, analytics, seat blocking,
trip edit/cancellation/status transitions, bus type/seat-template/global route/stop
editors, staff, customers, reports, refunds, CMS and system administration.
These require later supported backend scope. Final UI polish is a later milestone.

DoD for this supported scope: frontend build/tests pass, customer routes remain
registered, operator contracts match Java DTOs, and operator workflows are checked
against real authenticated API responses once a suitable account is available.

MILESTONE 12
STAFF, CUSTOMERS & REPORTS
Objective
Hoàn thiện phần quản lý phụ.

APIs/UI
/operator/customers
/operator/staff

/operator/reports/revenue
/operator/reports/occupancy

Reports
Chỉ cần:
Revenue by day
Revenue by route
Occupancy by trip
Không xây BI phức tạp.

MILESTONE 13
INTEGRATION & HARDENING
Objective
Không thêm feature mới.
Chỉ sửa lỗi và tăng độ ổn định.

Backend review
Kiểm tra:
transaction boundaries
N+1 queries
indexes
pagination
validation
authorization
operator isolation
exception responses

Security review
Kiểm tra:
password exposure
JWT logs
operator ID manipulation
booking ownership
payment ownership

Frontend review
Kiểm tra:
loading
empty state
error state
responsive customer UI
double click prevention
token expiration

Concurrency
Re-run:
seat hold concurrency test

MILESTONE 14
DEMO DATA & DOCUMENTATION
Objective
Chuẩn bị project để nộp và demo.

Seed locations
Ví dụ:
TP.HCM
Đắk Lắk
Gia Lai
Đà Nẵng
Huế
Hà Nội
Đà Lạt
Nha Trang

Seed operator
GiangBus

Seed routes
Ít nhất:
Route 1
Đắk Lắk
→ Gia Lai
→ Đà Nẵng
→ Huế
→ Hà Nội
Route 2
TP.HCM
→ Đồng Nai
→ Đà Lạt

Seed vehicles
Ít nhất:
1 Limousine
1 Sleeper

Seed trips
Ít nhất:
4–6 upcoming trips
để search demo đẹp.

15. Demo Scenario bắt buộc
Scenario 1 – Search filtering
Có:
Operator/Trip A:
Đắk Lắk → Đà Nẵng
và:
Trip B:
Đắk Lắk → Hà Nội
Search:
Đắk Lắk → Hà Nội
A không được xuất hiện.

16. Demo Scenario 2 – Seat Segment Reuse
Booking 1:
Seat A01
Đắk Lắk → Đà Nẵng
Sau đó search:
Đà Nẵng → Hà Nội
A01:
AVAILABLE

17. Demo Scenario 3 – Seat Overlap
A01:
Đắk Lắk → Huế
BOOKED
Search:
Đà Nẵng → Hà Nội
A01:
UNAVAILABLE

18. Demo Scenario 4 – Concurrent Hold
Có thể dùng integration test thay vì live UI.
Hai request đồng thời:
A01
same segment
Expected:
1 success
1 SEAT_NOT_AVAILABLE

19. Git Strategy
Với một người + agent, không cần Git Flow phức tạp.
Dùng:
main
dev
Feature branch nếu task lớn:
feature/auth
feature/trip-search
feature/booking

20. Commit Convention
Khuyến nghị:
feat:
fix:
refactor:
test:
docs:
chore:
Ví dụ:
feat: implement trip segment inventory generation

test: add concurrent seat hold integration test

fix: prevent operator staff accessing foreign trips

21. Codex Task Size
Không giao task kiểu:
Build BusGo.
Không giao:
Implement all backend.
Task Codex nên có kích thước khoảng:
1 feature
hoặc
1 tightly related set of endpoints
Ví dụ tốt:
Implement the Trip creation application service.

Use the existing entities and migrations.

It must:
- validate operator route ownership
- validate bus ownership/status
- detect schedule conflicts
- create trip stops as snapshots
- create trip segments
- create trip seats as snapshots
- create seat-segment inventory

Add integration tests for:
- successful creation
- schedule conflict
- foreign operator bus
- expected inventory count

Do not modify unrelated modules.

22. Mandatory Codex Workflow
Mỗi task agent phải:
1. Read relevant docs.
2. Inspect existing code.
3. State files likely affected.
4. Implement.
5. Run tests.
6. Fix failures.
7. Summarize changes.
Không cho phép:
rewrite project architecture
nếu không có yêu cầu.

23. Agent Guardrails
Codex không được tự:
switch database
add Redis
add Kafka
create microservices
replace React
replace Spring Boot
change API contract
remove segment-based inventory
change authentication model
Nếu phát hiện vấn đề architecture:
report issue first
thay vì tự redesign.

24. Documentation Rule
Nếu implementation buộc phải thay đổi:
database
API
business rule
thì docs tương ứng phải được cập nhật cùng commit.

25. Testing Pyramid
Ưu tiên:
Unit Tests
      ↓
Integration Tests
      ↓
Small number of end-to-end tests
Core booking nên thiên về integration test vì:
transactions
database constraints
locking
rất quan trọng.

26. Backend Test Priority
P0:
Seat segment availability
Seat hold concurrency
Booking creation
Operator isolation
Trip creation
P1:
Authentication
Cancellation
Fare calculation
Trip search
P2:
Dashboard
Reports

27. Frontend Test Priority
Không cần coverage cực cao.
Ưu tiên:
Search Form
Seat Selection
Hold Expiry
Booking Form
Protected Route

28. V1 Feature Freeze
Khi Milestone 12 hoàn tất:
FEATURE FREEZE
Không thêm:
rating
promotion
chat
real payment
GPS
map tracking
trước khi M13 hoàn tất.

29. Scope Reduction Order
Nếu thiếu thời gian, bỏ theo thứ tự:
1. Advanced Reports
2. Customers page
3. Staff management UI
4. Seat blocking UI
5. PDF ticket
6. Popular routes section
Không được bỏ:
Search
Trip
Segment seat availability
Hold
Booking
Payment mock
Admin routes/trips
vì đó là core project.

30. Final Definition of Done
BusGo V1 hoàn thành khi:
Customer
Search route
→ choose trip
→ choose pickup/dropoff
→ choose seats
→ hold seats
→ create booking
→ pay
→ receive ticket
→ view booking
→ cancel eligible booking
Operator
Create bus type
→ create bus
→ create route/stops/fares
→ create trip
→ see seat occupancy
→ see bookings
→ see passengers
→ dashboard
Technical
No double booking.

Segment reuse works.

Operator isolation works.

All migrations work from empty DB.

Backend test suite passes.

Frontend build passes.

No hard-coded operator ID.

No hard-coded routes.

No critical business logic in frontend.

31. Recommended First Codex Task
Sau khi repository và docs đã có, task đầu tiên cho Codex nên chỉ là:
MILESTONE 0 – Project Foundation
Không yêu cầu agent bắt đầu database/business logic cùng lúc.
Sau M0 chạy ổn mới giao M1.

32. Recommended Working Loop
Workflow xuyên suốt project:
Bạn + ChatGPT
↓
chốt task

ChatGPT
↓
viết prompt Codex rõ ràng

Codex
↓
implement + test

Bạn
↓
chạy thử / xem UI

ChatGPT
↓
review lỗi / feedback / task tiếp theo

Codex
↓
sửa
Agent là người triển khai.
Requirement, architecture và quyết định scope vẫn được kiểm soát bên ngoài agent.

## Delivered V1 and deferred scope (M15)

M0–M14B deliver authentication, customer discovery/holds/bookings/mock payment and
tickets, operator management/operations, read-only staff and isolated system-admin
operator management. M15 P0 hardens local setup, create-only demo fixtures and
release verification. Earlier roadmap/acceptance examples above describe planned
scope; they are not evidence that cancellation, refund or global catalogue editors
are delivered.

V2-ready infrastructure: immutable trip snapshots, segment inventory, Flyway,
role/membership isolation and transaction/concurrency guards. Deferred after V1:
refunds/cancellation redesign, real gateways, analytics, notifications, check-in,
global master-data editing, new workflows, broad CSS refactoring and bundle tuning.

M15 implementation retains V1–V10 unchanged, disables operator-code-based reset,
preserves existing fixture state, adds a deterministic daily timetable and reserved
create-only staff fixture. CUSTOMER registers once; SYSTEM_ADMIN uses the existing
opt-in bootstrap. Read docs/demo-data.md and docs/final-demo.md for preparation,
and docs/m15-verification.md for current-branch results and limits. A live P0 check
also corrected JDBC/JPA timestamp parity for operator/admin views on non-UTC JVMs,
without changing stored records or hold-expiry semantics. Historical
verification below remains unchanged.

## M12 backend P0 implementation

Implemented operator booking list/detail, confirmed passenger manifest,
segment-aware occupancy, and the forward-only trip operations state machine.
Customer hold, booking, and mock-payment mutations now share the lock order
`Trip -> Booking (when applicable) -> inventory rows`. This serializes operational
status transitions against new customer mutations while retaining the existing
inventory `FOR UPDATE` protection.

M12 P0 intentionally does not include frontend work, cancellation/refund flows,
seat block mutations, check-in/boarding state, passenger editing, automatic trip
transitions, dashboard analytics, or trip status history. No schema migration is
required for this scope.

## M12 operator operations frontend implementation

Implemented booking list/detail, passenger manifest, seat-by-segment occupancy,
and next-step trip lifecycle actions within the M11 operator architecture.
The sidebar now includes Đặt vé; trip detail links to bookings, passengers and
occupancy. Filters persist in the URL and use backend pagination. All new
timestamps display in Vietnam time; booking creation-date filtering follows
the backend Vietnam business date.

Seat passenger identity remains separate from ticket names and booking contacts.
Lifecycle confirmation explains closure of sales/payment windows and terminal
completion. New errors use Vietnamese messages; queries refresh after mutations
and conflicts. Responsive tables retain horizontal scrolling where needed.

Frontend verification: 17 tests passed; TypeScript/Vite build passed;
`git diff --check` passed. Browser/manual end-to-end QA remains outstanding;
see `docs/m12-verification.md` for precise coverage and the remaining checklist.
Existing backend changes were preserved. Customer source files were unchanged.
No commit or push was performed. M14 polish and unsupported operational domains
(check-in, no-show, passenger editing, refunds, seat blocking, analytics) remain
deferred.

## M13 backend P0 implementation

Implemented secure opt-in `SYSTEM_ADMIN` bootstrap, explicit `/api/v1/admin/**`
operator administration, operator staff account/membership management, read-only
`OPERATOR_STAFF` operational access, operator-scoped staff-code uniqueness, and
inactive-operator commerce suspension. `SYSTEM_ADMIN` never inherits operator
access, and operator-owned queries continue to derive ownership from membership.

M13 backend P0 deliberately excludes frontend work, customer administration,
reports/analytics, cancellation/refund, invitations/email, existing-account
linking, arbitrary role editing, global master-data editing, and persisted audit
events. Verification evidence is in `docs/m13-verification.md`.

## M13 frontend P0 implementation

Implemented the separate SYSTEM_ADMIN layout/guard and operator list, onboarding,
contact editing, status confirmations and read-only staff view. Added operator
admin staff list/create/update with restricted roles and URL-backed filters.
OPERATOR_STAFF now has a read-only operational surface with route-level denial
of management pages and hidden mutation actions. Login redirects respect role
and authorized return paths while retaining customer behavior. Vietnamese
contract-error mappings, null-safe DTOs and responsive forms reuse the existing
HTTP/auth, query, layout and table infrastructure.

Verification: all 26 frontend tests passed (including the 17 existing operator
regressions); TypeScript/Vite production build passed; git diff --check passed.
Manual browser, mobile visual QA and live backend end-to-end checks remain
outstanding. Full file inventory, evidence and checklist: docs/m13-verification.md.
Backend changes were preserved without modification. No commit or push performed.
M13 deferred domains and M14 redesign remain out of scope.

## M14A minimal backend contract improvements

Implemented only the two backend gaps identified by the M14A audit. Operator trip
list now accepts `businessDate` with `Asia/Ho_Chi_Minh` calendar semantics by
reusing `BusGoTime.businessDate`; the legacy UTC `date` filter remains available,
and supplying both is a validation error. Existing pagination, filters, stable
sorting, UTC API timestamps, operator ownership, and admin/staff read permissions
remain intact.

Operator occupancy now evaluates the authoritative trip-seat snapshot by
trip-segment snapshot cross-product. It reports expected, actual, and missing cell
counts plus a completeness flag. Missing cells stay visible with `missing: true`
and no invented inventory status, and any affected seat is excluded from the
whole-trip available count. The four persisted statuses and all booking, hold,
payment, trip, locking, manifest, and customer-availability semantics are
unchanged. No schema migration or frontend work is included.

Focused MySQL integration coverage was added for Vietnam date boundaries,
overnight departures, conflicting date parameters, ownership/staff access,
complete and incomplete matrices, all four statuses, multiple segments, and
non-overlapping physical-seat reuse. Verification evidence and environment
limitations are recorded in `docs/m14a-verification.md`.

## M14A operator UX implementation and verification

Completed the trip-centered operator workspace with a shared header and overview,
physical seat board, passenger, and occupancy tabs. Added the staff-readable seat
route, Vietnam businessDate dashboard/list, URL-backed journey context, exact
snapshot geometry, explicit missing-inventory handling, ordered multi-segment
states, on-demand booking inspection, identity-safe passenger search/grouping,
contact-aware booking lookup, and responsive operator-only styling. Admin-only
lifecycle wording and controls retain the existing backend state machine.

Verification completed 2026-09-30: 42 frontend tests passed (all existing
regressions included); TypeScript/Vite build passed; git diff --check passed.
Isolated browser fixture checks covered desktop 1440×1100, tablet 820×1000, and
mobile 390×844: dashboard/list, shared tabs, seat geometry and drawer/sheet,
passengers, matrix scrolling, keyboard focus/Escape/restoration, role presentation,
on-demand booking fetches, and cached data on refresh failure. Build warnings
remain for Zod annotations and the main bundle exceeding 500 kB.

No live backend verification is claimed. Existing backend changes were preserved;
no customer/admin redesign, commit, or push was performed. The usage-limit
continuation finished verification/documentation only. Detailed file inventory,
route behavior, evidence, practical limitations (including staff ID filters and
booking-list seat counts), and deferred scope are in docs/m14a-verification.md.

## M14B customer and system-admin UX implementation and verification

Completed customer search/journey context, compact result cards, truthful bus
imagery, five-seat-limit feedback, mobile summary ordering, payment/ticket/history
presentation, profile feedback and role-aware navigation. System admin now has
API-derived totals, a compact directory, dedicated `/admin/operators/new`
onboarding, overview-first detail, stable contact drafts, read-only staff and
explicit activation/deactivation consequences. Backend behavior and permissions
remain authoritative; no backend or operator/staff redesign was included.

Verification completed 2026-10-01: 65 frontend tests passed, TypeScript/Vite build
passed and git diff --check passed. The isolated synthetic browser fixture covered
13 surfaces at 320, 390, 768, 820, 1024 and 1440px (78 checks), with no document
horizontal overflow. Scoped customer/admin body sizing preserves operator CSS.
Focused interactions covered seat geometry/limits, contact validation/countdown,
payment and repeat tickets, empty/refresh states, admin totals/search/contact draft
stability/status rejection, profile feedback and filter keyboard focus. Shared
Field accessible names remain stable when descriptions/errors appear.

Existing Zod annotation and bundle-size warnings remain. No live backend, real
credential/payment, physical-device or full screen-reader verification is claimed.
No commit or push performed. File inventory, detailed evidence, fixture limits,
unverified items and deferred scope are in docs/m14b-verification.md.
## Product Direction

BusGo is being developed as a management-oriented transportation platform.

### Current V1
BusGo currently consists of:
- Customer booking flow
- Operator management and operations
- System administration
- Multi-operator-ready architecture

### V1.5 Focus
The next development phase prioritizes operator management:
- operational dashboard
- booking management
- trip operations
- driver/assistant assignment
- boarding/check-in
- reporting and analytics
- customer management where useful

### V2 Direction
BusGo will evolve toward a multi-operator marketplace:
- multiple transport operators
- customer comparison between operators
- cancellation/refund
- promotions
- real payment integration
- commissions/settlements
- richer platform administration
- advanced reporting

### Final Product Positioning
BusGo is positioned as:

"An online bus operation management and ticket booking platform."

The Operator Management experience is the primary management focus.
Customer booking provides transaction flow into the management system.
System Admin provides platform-level governance.


## M16A — Operator assisted booking and payment collection

Implemented PHONE assisted bookings without customer accounts, immutable WEB/PHONE
source and intended payment method, PAY_ON_BOARD collection, opaque QR_TRANSFER mock
payment links, admin-only mutation commands, operator ticket QR, and focused creation
UI. Shared hold/conversion/payment services preserve inventory and ticket invariants.
V11 follows V10; staff capabilities, boarding, cancellation, rescheduling, gateways,
Zalo API and reporting screens remain deferred. Zalo is only a manually used external
communication channel, not a booking source.

See [M16A design](m16a-assisted-booking.md) and [verification](m16a-verification.md)
for source audit, contracts, files, migration, test results and practical limitations.
No commit or push is performed for this milestone.
