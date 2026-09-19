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
POST   /api/v1/operator/bus-types
PATCH  /api/v1/operator/bus-types/{id}

Bus Type create
Phải hỗ trợ seat templates.
Backend tự tính:
seatCount

Bus APIs
GET   /api/v1/operator/buses
POST  /api/v1/operator/buses
GET   /api/v1/operator/buses/{id}
PATCH /api/v1/operator/buses/{id}

Route APIs
GET  /api/v1/operator/routes
POST /api/v1/operator/routes
GET  /api/v1/operator/routes/{id}
PUT  /api/v1/operator/routes/{id}

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
Admin có thể tạo hoàn chỉnh:
Bus Type
↓
Bus
↓
Route
↓
Stops
↓
Fare
bằng API.

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

Fare
Search service resolve giá theo:
operator_route_fares
cho pickup/dropoff.
Nếu chưa có fare:
trip không bookable cho cặp stop đó
hoặc return configuration error.
Không tự cộng giá segment nếu requirement chưa quy định.

Search filters
Implement sau core search:
operatorId
busTypeId
minPrice
maxPrice
departureFrom
departureTo
sort

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
pickupTripStopId
dropoffTripStopId

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

Public status mapping
Backend response:
AVAILABLE
UNAVAILABLE
BLOCKED
Không expose người đang hold.

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

Có thể cần entity mới
Mặc dù inventory đã chứa:
holdToken
heldBy
expiresAt
khuyến nghị thêm table:
seat_holds
để quản lý lifecycle hold rõ hơn.
Schema gợi ý:
id
hold_token
user_id
trip_id
pickup_trip_stop_id
dropoff_trip_stop_id
status
expires_at
created_at
Status:
ACTIVE
EXPIRED
CONSUMED
CANCELLED
Đây là một điều chỉnh database hợp lý trước khi code milestone này.

Hold transaction
BEGIN

resolve segments

SELECT inventory
FOR UPDATE

verify every row AVAILABLE

create hold

update rows:
AVAILABLE → HELD

COMMIT

Configuration
seat-hold.duration-minutes=10
booking.max-seats=5

Expiration job
Scheduled job:
every 1 minute
release expired hold.

Concurrency Test
Bắt buộc chạy test:
User A
User B

attempt same seat simultaneously

exactly one succeeds.
Đây là test không được bỏ.

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
booking_status_history

APIs
POST /api/v1/bookings

GET /api/v1/bookings/me
GET /api/v1/bookings/{id}

POST /api/v1/bookings/{id}/cancel

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
create status history

Booking price
Backend tính:
fare × numberOfSeats
nếu mọi seat cùng giá trong V1.
Không lấy total từ frontend.

Cancellation
Implement:
customer cancellation threshold = 6 hours
Khi cancel:
Booking → CANCELLED

BOOKED/HELD inventory owned by booking
→ AVAILABLE

Tests
expired hold cannot create booking

foreign hold cannot create booking

booking total is calculated server side

cancel releases only relevant segments

booking history created

Definition of Done
Booking lifecycle chạy đúng trước payment.

MILESTONE 9
PAYMENT & TICKET
Objective
Hoàn thành full customer business flow.

Table
payments

Payment methods
MOCK_QR
CASH

APIs
POST /api/v1/bookings/{id}/payments/mock-confirm

GET /api/v1/bookings/{id}/ticket

MOCK_QR flow
Booking PENDING
Payment PENDING
Inventory HELD

↓ confirm

Payment PAID
Booking CONFIRMED
Inventory BOOKED
Hold CONSUMED
phải transaction.

CASH flow
Có thể:
Booking CONFIRMED
Payment PENDING
Inventory BOOKED
sau booking nếu business rule chốt như vậy.
Staff xác nhận tiền sau.

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
bookingCode
Không cần lưu image QR trong DB.

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
Objective
Cho admin vận hành hệ thống hoàn toàn bằng UI.

Pages priority
11A
Operator Layout
Dashboard
11B
Bus Types
Seat Template Editor
Buses
11C
Routes
Route Stops
Fare Editor
11D
Trips
Trip Create
Trip Detail
11E
Bookings
Booking Detail
Passenger Manifest

DoD
Admin demo không cần sửa DB bằng MySQL Workbench.

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
