Database Design v1
Mình đề xuất khoảng 20 bảng, nhưng chúng chia thành từng domain rõ ràng chứ không phải 20 bảng rời rạc.
AUTH
├── users
├── roles
└── user_roles

OPERATOR
├── transport_operators
├── operator_staff
└── operator_routes

ROUTE
├── locations
├── routes
└── route_stops

FLEET
├── bus_types
├── seat_templates
└── buses

TRIP
├── trips
├── trip_stops
├── trip_segments
├── trip_seats
└── trip_seat_segment_inventory

BOOKING
├── bookings
├── booking_items
├── payments
└── booking_status_history

PRICING
└── operator_route_fares

1. users
Tất cả account của hệ thống.
users
--------------------------------
id                  BIGINT PK
full_name           VARCHAR(100)
email               VARCHAR(150) UNIQUE
phone               VARCHAR(20)
password_hash       VARCHAR(255)

status              VARCHAR(30)

created_at          DATETIME
updated_at          DATETIME
deleted_at          DATETIME NULL
Status:
ACTIVE
INACTIVE
LOCKED
Không lưu password plaintext.

2. roles
roles
-------------------------
id              BIGINT PK
code            VARCHAR(50) UNIQUE
name            VARCHAR(100)
Dữ liệu:
CUSTOMER
OPERATOR_STAFF
OPERATOR_ADMIN
SYSTEM_ADMIN

3. user_roles
Cho phép một user có nhiều role.
user_roles
-------------------------
user_id     BIGINT FK
role_id     BIGINT FK

PRIMARY KEY(user_id, role_id)

4. transport_operators
Đại diện nhà xe.
transport_operators
--------------------------------
id                  BIGINT PK

name                VARCHAR(150)
code                VARCHAR(50) UNIQUE

phone               VARCHAR(20)
email               VARCHAR(150)
address             VARCHAR(255)

status              VARCHAR(30)

created_at          DATETIME
updated_at          DATETIME
V1:
1 | GiangBus
Nhưng tuyệt đối không hard-code:
operatorId = 1
trong business logic.

5. operator_staff
Liên kết nhân viên với nhà xe.
operator_staff
--------------------------------
id                  BIGINT PK

operator_id         BIGINT FK
user_id             BIGINT FK

staff_code          VARCHAR(50)
status              VARCHAR(30)

created_at          DATETIME
Constraint:
UNIQUE(operator_id, user_id)
Sau này:
User X
→ staff Nhà xe A

User Y
→ admin Nhà xe B

6. locations
Đây là địa điểm mà user có thể search hoặc route sử dụng.
locations
--------------------------------
id                  BIGINT PK

name                VARCHAR(150)

province            VARCHAR(100)
district            VARCHAR(100)
address             VARCHAR(255)

latitude            DECIMAL(10,7) NULL
longitude           DECIMAL(10,7) NULL

status              VARCHAR(30)

created_at          DATETIME
updated_at          DATETIME
Ví dụ:
Bến xe Buôn Ma Thuột
Bến xe Trung tâm Đà Nẵng
Bến xe Nước Ngầm
Một điểm cần lưu ý
Frontend có thể cho user search:
Đắk Lắk
Hà Nội
nhưng backend cuối cùng vẫn phải map về một hoặc nhiều location_id.
Sau này có thể mở rộng thêm city/province, nhưng V1 chưa cần phức tạp hóa.

7. routes
Route là một chiều chạy xác định.
routes
--------------------------------
id                      BIGINT PK

name                    VARCHAR(200)

origin_location_id      BIGINT FK
destination_location_id BIGINT FK

estimated_distance_km   DECIMAL(8,2)
estimated_duration_min  INT

status                  VARCHAR(30)

created_at              DATETIME
updated_at              DATETIME
Ví dụ:
Đắk Lắk → Hà Nội
và
Hà Nội → Đắk Lắk
nên được xem là hai route khác nhau.
Điều này đơn giản hơn rất nhiều so với cố để một route hai chiều.

8. route_stops
Đây là một trong những bảng quan trọng nhất.
route_stops
--------------------------------
id                          BIGINT PK

route_id                    BIGINT FK
location_id                 BIGINT FK

stop_order                  INT

allow_pickup                BOOLEAN
allow_dropoff               BOOLEAN

estimated_offset_minutes    INT

status                      VARCHAR(30)
Ví dụ:
id	route	location	order
1	Đắk Lắk → Hà Nội	Đắk Lắk	1
2	Đắk Lắk → Hà Nội	Gia Lai	2
3	Đắk Lắk → Hà Nội	Đà Nẵng	3
4	Đắk Lắk → Hà Nội	Huế	4
5	Đắk Lắk → Hà Nội	Hà Nội	5
Constraint bắt buộc:
UNIQUE(route_id, stop_order)
Nên thêm:
UNIQUE(route_id, location_id)
trong V1.
Như vậy cùng một location không xuất hiện hai lần trên cùng route.

9. operator_routes
Không để route thuộc riêng một nhà xe.
operator_routes
--------------------------------
id              BIGINT PK

operator_id     BIGINT FK
route_id        BIGINT FK

status          VARCHAR(30)

created_at      DATETIME
updated_at      DATETIME
Constraint:
UNIQUE(operator_id, route_id)
Ví dụ:
Route Đắk Lắk → Hà Nội

Nhà xe A ✅
Nhà xe B ✅
Nhà xe C ❌
Đây chính là thứ giúp mình mở rộng từ A → B sau này.

10. bus_types
bus_types
--------------------------------
id              BIGINT PK

name            VARCHAR(100)
seat_count      INT
description     TEXT

status          VARCHAR(30)

created_at      DATETIME
updated_at      DATETIME
Ví dụ:
Limousine 22 phòng
Sleeper 34
Seat 45

11. seat_templates
Định nghĩa sơ đồ ghế của bus_type.
seat_templates
--------------------------------
id              BIGINT PK

bus_type_id     BIGINT FK

seat_code       VARCHAR(20)

row_no          INT
column_no       INT
floor_no        INT

seat_type       VARCHAR(30)

active          BOOLEAN
Constraint:
UNIQUE(bus_type_id, seat_code)
Ví dụ:
A01
A02
A03
...
Frontend render seat map dựa trên:
row
column
floor
chứ không hard-code sơ đồ.

12. buses
buses
--------------------------------
id                  BIGINT PK

operator_id         BIGINT FK
bus_type_id         BIGINT FK

license_plate       VARCHAR(30)

status              VARCHAR(30)

created_at          DATETIME
updated_at          DATETIME
deleted_at          DATETIME NULL
Constraint:
UNIQUE(license_plate)
Status:
AVAILABLE
MAINTENANCE
INACTIVE

13. trips
Trip là chuyến chạy cụ thể.
trips
--------------------------------
id                      BIGINT PK

operator_route_id       BIGINT FK
bus_id                  BIGINT FK

departure_time          DATETIME(6)
estimated_arrival_time  DATETIME(6)

status                  VARCHAR(30)

created_at              DATETIME(6)
updated_at              DATETIME(6)
Các giá trị scheduled time được chuẩn hóa thành UTC trước khi lưu. Client chỉ gửi
departureTime có offset; estimated_arrival_time được tính bằng departure_time cộng
estimated_offset_minutes của active RouteStop cuối cùng. `routes.estimated_duration_min`
chỉ là metadata và không phải nguồn tính arrival.
Mình đề xuất trip tham chiếu:
operator_route_id
thay vì vừa:
operator_id
route_id
riêng biệt.
Vì operator_route đã xác định:
Nhà xe nào đang chạy tuyến nào.
Như vậy không thể vô tình tạo:
Nhà xe A
+
Route mà Nhà xe A không khai thác

14. Validation của Trip
Khi tạo trip:
trip.operator_route.operator_id
phải bằng:
bus.operator_id
Ví dụ:
Trip của Nhà xe A
+
Bus Nhà xe B
→ REJECT.
Ngoài ra cùng một bus không được có hai trip overlap.

15. trip_stops
Đây là snapshot của route_stops.
Rất quan trọng.
Không nên chỉ sử dụng trực tiếp route_stops.
trip_stops
--------------------------------
id                      BIGINT PK

trip_id                 BIGINT FK

source_route_stop_id    BIGINT FK NULL
location_id             BIGINT FK

stop_order              INT

planned_arrival_time    DATETIME NULL
planned_departure_time  DATETIME NULL

allow_pickup            BOOLEAN
allow_dropoff           BOOLEAN

status                  VARCHAR(30)
Constraint:
UNIQUE(trip_id, stop_order)

Quy tắc planned time M4:
    • stop đầu: arrival NULL, departure = trip departure
    • stop giữa: arrival = departure = trip departure + RouteStop offset
    • stop cuối: arrival = trip departure + RouteStop offset, departure NULL
Không tự suy diễn dwell time.

Tại sao phải copy?
Giả sử hôm nay route:
Đắk Lắk
↓
Đà Nẵng
↓
Hà Nội
Có 100 booking.
Ngày mai admin sửa route thành:
Đắk Lắk
↓
Huế
↓
Hà Nội
Nếu trip cũ sử dụng trực tiếp route_stops:
lịch sử 100 booking cũ sẽ bị sai.
Vì vậy khi tạo Trip:
RouteStop
↓ COPY
TripStop
Trip lịch sử không bị ảnh hưởng.

16. trip_segments
Từ TripStop tạo ra segment.
Ví dụ:
Stop 1: Đắk Lắk
Stop 2: Đà Nẵng
Stop 3: Huế
Stop 4: Hà Nội
Sinh:
Segment 1:
1 → 2

Segment 2:
2 → 3

Segment 3:
3 → 4
Table:
trip_segments
--------------------------------
id                  BIGINT PK

trip_id             BIGINT FK

from_trip_stop_id   BIGINT FK
to_trip_stop_id     BIGINT FK

segment_order       INT
Constraint:
UNIQUE(trip_id, segment_order)

17. trip_seats
Khi tạo Trip:
Bus
↓
BusType
↓
SeatTemplate
hệ thống copy thành TripSeat.
trip_seats
--------------------------------
id                      BIGINT PK

trip_id                 BIGINT FK

source_seat_template_id BIGINT FK NULL

seat_code               VARCHAR(20)

row_no                  INT
column_no               INT
floor_no                INT
seat_type               VARCHAR(30)
Constraint:
UNIQUE(trip_id, seat_code)
Một lần nữa đây cũng là snapshot.
Nếu sau này sửa SeatTemplate thì Trip cũ không bị phá.

18. Bảng quan trọng nhất:
trip_seat_segment_inventory
Đây là core của seat booking.
trip_seat_segment_inventory
--------------------------------
id                  BIGINT PK

trip_seat_id        BIGINT FK
trip_segment_id     BIGINT FK

status              VARCHAR(30)

hold_token          VARCHAR(100) NULL
held_by_user_id     BIGINT FK NULL
hold_expires_at     DATETIME NULL

updated_at          DATETIME
version             BIGINT
Constraint cực kỳ quan trọng:
UNIQUE(trip_seat_id, trip_segment_id)
Status:
AVAILABLE
HELD
BOOKED
BLOCKED

V4 tạo sẵn các cột hold nullable và `version`, nhưng M4 không triển khai hành vi hold.
Inventory mới có status AVAILABLE và toàn bộ hold fields là NULL. `booking_item_id`
chưa có trong V4 vì bảng booking_items chưa tồn tại; milestone booking sẽ thêm bằng
migration riêng sau khi tạo booking_items. TripSeat không có availability/booking status
toàn cục vì availability được quản lý theo từng segment.

19. Ví dụ thực tế
Trip:
Đắk Lắk
↓
Đà Nẵng
↓
Huế
↓
Hà Nội
Có ghế:
A01
Database:
Seat	Segment	Status
A01	Đắk Lắk → Đà Nẵng	AVAILABLE
A01	Đà Nẵng → Huế	AVAILABLE
A01	Huế → Hà Nội	AVAILABLE
Khách A đặt:
Đắk Lắk → Đà Nẵng
Sau booking:
Seat	Segment	Status
A01	Đắk Lắk → Đà Nẵng	BOOKED
A01	Đà Nẵng → Huế	AVAILABLE
A01	Huế → Hà Nội	AVAILABLE
Khách B đặt:
Đà Nẵng → Hà Nội
→ kiểm tra segment:
Đà Nẵng → Huế
Huế → Hà Nội
cả hai AVAILABLE.
=> được phép.
Cuối cùng:
A01
Đắk Lắk → Đà Nẵng = Khách A
Đà Nẵng → Hà Nội = Khách B
Đúng nghiệp vụ mình vừa bàn.

20. Seat Hold
Giả sử user chọn:
A01
Đà Nẵng → Hà Nội
Backend lấy:
Segment 2
Segment 3
Transaction:
Segment 2 AVAILABLE → HELD
Segment 3 AVAILABLE → HELD
Cùng một:
hold_token
Ví dụ:
f7bd-829a...
và:
hold_expires_at =
now + 10 phút

21. Concurrency
Đây là phần Codex phải được yêu cầu làm rất kỹ.
Transaction logic:
BEGIN

SELECT inventory rows
FOR UPDATE

Kiểm tra tất cả AVAILABLE

Nếu tất cả AVAILABLE:
    update → HELD

Nếu một row không AVAILABLE:
    rollback

COMMIT
Như vậy:
User A
User B
ấn A01 gần như cùng lúc thì chỉ một người giữ được.
Column:
version
cũng có thể sử dụng cho optimistic locking.
Nhưng với seat booking, mình nghiêng về:
Pessimistic locking
ở transaction ngắn.

22. bookings
bookings
--------------------------------
id                      BIGINT PK

booking_code            VARCHAR(50) UNIQUE

customer_id             BIGINT FK
trip_id                 BIGINT FK

pickup_trip_stop_id     BIGINT FK
dropoff_trip_stop_id    BIGINT FK

contact_name            VARCHAR(100)
contact_phone           VARCHAR(20)
contact_email           VARCHAR(150)

total_amount            DECIMAL(12,2)

status                  VARCHAR(30)

created_at              DATETIME
updated_at              DATETIME
cancelled_at            DATETIME NULL
Status:
PENDING
CONFIRMED
CANCELLED
COMPLETED

23. Booking route validation
Backend phải đảm bảo:
pickupTripStop.trip_id
=
booking.trip_id
và:
dropoffTripStop.trip_id
=
booking.trip_id
và:
pickup.stopOrder
<
dropoff.stopOrder

24. booking_items
Một booking nhiều ghế.
booking_items
--------------------------------
id                  BIGINT PK

booking_id          BIGINT FK
trip_seat_id        BIGINT FK

passenger_name      VARCHAR(100) NULL

unit_price          DECIMAL(12,2)

created_at          DATETIME
Ví dụ:
Booking BG001

A01
A02
A03
=> 3 booking_items.
Constraint:
UNIQUE(booking_id, trip_seat_id)

25. Booking Item không cần lưu từng segment?
Không cần.
Vì booking đã có:
pickup_trip_stop_id
dropoff_trip_stop_id
Nên hệ thống có thể suy ra:
booking item A01
+
pickup 2
+
dropoff 5
→ chiếm segment:
2→3
3→4
4→5
Sau khi booking thành công:
trip_seat_segment_inventory.booking_item_id
sẽ trỏ tới booking item tương ứng.

26. payments
payments
--------------------------------
id                  BIGINT PK

booking_id          BIGINT FK

method              VARCHAR(30)
amount              DECIMAL(12,2)

status              VARCHAR(30)

transaction_ref     VARCHAR(100) NULL

paid_at             DATETIME NULL
created_at          DATETIME
updated_at          DATETIME
Method:
CASH
MOCK_QR
Status:
PENDING
PAID
FAILED
REFUNDED
Không xóa payment cũ khi booking bị cancel.

27. booking_status_history
booking_status_history
--------------------------------
id                  BIGINT PK

booking_id          BIGINT FK

from_status         VARCHAR(30)
to_status           VARCHAR(30)

changed_by_user_id  BIGINT FK NULL

note                VARCHAR(500)

changed_at          DATETIME
Ví dụ:
PENDING
↓
CONFIRMED
↓
CANCELLED
đều giữ lại.

28. Giá vé
Đây là phần mình muốn thiết kế để không khóa đường tương lai.
Thêm:
operator_route_fares
operator_route_fares
--------------------------------
id                      BIGINT PK

operator_route_id       BIGINT FK

from_route_stop_id      BIGINT FK
to_route_stop_id        BIGINT FK

price                   DECIMAL(12,2)

status                  VARCHAR(30)

created_at              DATETIME
updated_at              DATETIME
Ví dụ:
From	To	Price
Đắk Lắk	Đà Nẵng	250k
Đắk Lắk	Huế	350k
Đắk Lắk	Hà Nội	700k
Đà Nẵng	Huế	150k
Đà Nẵng	Hà Nội	500k
Nhờ đó:
Đắk Lắk → Hà Nội
không nhất thiết phải bằng tổng giá từng đoạn.
Nhà xe tự cấu hình giá.

29. Search không dựa vào origin/destination của route
Đây là yêu cầu quan trọng từ vấn đề bạn phát hiện.
User tìm:
Đắk Lắk → Hà Nội
25/09/2026
Backend không query đơn giản:
route.origin = daklak
AND
route.destination = hanoi
mà phải tìm TripStop:
Pickup:
location = Đắk Lắk
allowPickup = true

Dropoff:
location = Hà Nội
allowDropoff = true

pickup.stopOrder < dropoff.stopOrder
và:
trip.departure/date phù hợp
trip.status = SCHEDULED

30. Ví dụ vấn đề Nhà xe A
Giả sử:
Nhà xe A

Đắk Lắk
↓
Gia Lai
↓
Đà Nẵng
END
Nhà xe B:
Đắk Lắk
↓
Đà Nẵng
↓
Huế
↓
Hà Nội
User:
Đắk Lắk → Hà Nội
Query tìm TripStop.
Nhà xe A:
Đắk Lắk ✅
Hà Nội ❌
→ loại.
Nhà xe B:
Đắk Lắk ✅
Hà Nội ✅

order pickup < order dropoff
→ giữ.
Do đó kết quả:
Nhà xe B
Nhà xe A hoàn toàn không xuất hiện.
Đây chính là behavior mình cần.

31. Index bắt buộc
Nếu không thêm index thì search sau này rất chậm.
route_stops
INDEX(route_id, stop_order)
INDEX(location_id)
trip_stops
INDEX(trip_id, stop_order)
INDEX(location_id, trip_id)
trips
INDEX(departure_time)
INDEX(operator_route_id, departure_time)
INDEX(status, departure_time)
trip_segments
INDEX(trip_id, segment_order)
trip_seat_segment_inventory
UNIQUE(trip_seat_id, trip_segment_id)

INDEX(trip_segment_id, status)
INDEX(status, hold_expires_at)
bookings
UNIQUE(booking_code)

INDEX(customer_id, created_at)
INDEX(trip_id)
INDEX(status)
INDEX(contact_phone)

M5 query indexes (migration V5):

    • trip_stops(location_id, allow_pickup, planned_departure_time, trip_id, stop_order)
    • operator_route_fares(operator_route_id, from_route_stop_id, to_route_stop_id, status)

M7 query index (migration V6):

    • trip_seat_segment_inventory(hold_token)

V6 không thêm bảng hold. Một logical hold dùng cùng opaque UUID, owner và expiry trên toàn bộ
seat × required-segment matrix. GET suy ra immutable journey boundary từ ordered consecutive
TripSegment rows khi token metadata còn tồn tại. Release/cleanup xóa metadata, nên hệ thống
không giữ lifecycle history sau đó.

Customer search chọn candidate bằng TripStop snapshot, exact ACTIVE fare và
`Asia/Ho_Chi_Minh` business-date window đã chuyển sang UTC. Availability được tính
theo cùng một TripSeat trên mọi required TripSegment; query không thay đổi inventory.

32. ERD tổng quát
USER
 │
 ├──────────── USER_ROLE ───────── ROLE
 │
 └──────────── OPERATOR_STAFF
                    │
                    ▼
             TRANSPORT_OPERATOR
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
        BUS              OPERATOR_ROUTE
          │                   │
          ▼                   ▼
      BUS_TYPE               ROUTE
          │                   │
          ▼                   ▼
    SEAT_TEMPLATE         ROUTE_STOP
                              │
                              │ snapshot
                              ▼
OPERATOR_ROUTE ──────────── TRIP
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
         TRIP_STOP      TRIP_SEGMENT      TRIP_SEAT
              │               │               │
              └───────────────┼───────────────┘
                              ▼
                    SEAT_SEGMENT_INVENTORY
                              │
                              ▼
                           BOOKING
                              │
                        BOOKING_ITEM
                              │
             ┌────────────────┴───────────────┐
             ▼                                ▼
          PAYMENT                  BOOKING_STATUS_HISTORY

33. Khi tạo Trip sẽ xảy ra chuyện gì?
Đây cũng nên được Codex implement bằng một application service duy nhất:
Admin Create Trip
      ↓
Validate OperatorRoute
      ↓
Validate Bus belongs to Operator
      ↓
Validate Bus schedule
      ↓
Create Trip
      ↓
Copy RouteStops
→ TripStops
      ↓
Generate TripSegments
      ↓
Copy SeatTemplates
→ TripSeats
      ↓
Generate:
TripSeat × TripSegment
      ↓
SeatSegmentInventory
Ví dụ:
22 ghế
5 stops
Có:
4 segments
Inventory:
22 × 4 = 88 records
Rất nhỏ.
Ngay cả:
45 ghế
10 stops
chỉ:
45 × 9 = 405 records/trip
Với scope đồ án, hoàn toàn ổn.

34. Khi user chọn ghế
Customer

Pickup = Đà Nẵng
Dropoff = Hà Nội

Seat = A01
Backend:
Find pickup order = 3
Find dropoff order = 6

Required segments:

3→4
4→5
5→6

↓
Lock inventory rows
↓
Check AVAILABLE
↓
HELD 10 minutes

35. Khi booking thành công
HELD
↓
Create Booking
↓
Create BookingItems
↓
Create Payment
↓
Mock payment success
↓
Booking CONFIRMED
↓
Inventory BOOKED
↓
Link booking_item_id
Tất cả phần quan trọng phải nằm trong transaction.

36. Khi booking bị hủy
Booking CONFIRMED
↓
CANCELLED
Inventory liên quan:
BOOKED
↓
AVAILABLE
Nhưng chỉ release những segment thuộc:
pickup → dropoff
của booking đó.
Không release cả seat trên toàn trip.

37. Khi hold hết hạn
Scheduled job có thể chạy ví dụ:
mỗi 1 phút
tìm:
status = 'HELD'
AND hold_expires_at < NOW()
rồi:
HELD → AVAILABLE
Đây là job rất nhỏ và không cần Kafka/Redis.

38. Một quyết định mình muốn chốt ngay
Mình không đề xuất Redis cho seat hold ở V1.
Mặc dù production booking system có thể dùng Redis hoặc distributed locking, nhưng đồ án hiện tại chỉ cần:
MySQL
+
transaction
+
SELECT FOR UPDATE
+
expiration timestamp
là đủ.
Vừa dễ hiểu, vừa dễ trình bày khi demo.

39. Một thay đổi so với requirement trước
Trong requirement cũ có:
trip_seat.status
Mình đề nghị bỏ status booking khỏi trip_seats.
Bởi vì một ghế có thể:
BOOKED segment 1

AVAILABLE segment 2

BOOKED segment 3
Nên nói:
TripSeat A01 = BOOKED
là không còn chính xác.
Trạng thái thực phải nằm ở:
trip_seat_segment_inventory
Đây là thay đổi quan trọng nhất trong DB design.

40. Database scope cuối cùng
Mình chốt phiên bản đầu với 19 bảng chính:
1.  users
2.  roles
3.  user_roles

4.  transport_operators
5.  operator_staff

6.  locations
7.  routes
8.  route_stops
9.  operator_routes
10. operator_route_fares

11. bus_types
12. seat_templates
13. buses

14. trips
15. trip_stops
16. trip_segments
17. trip_seats
18. trip_seat_segment_inventory

19. bookings
20. booking_items
21. payments
22. booking_status_history
Thực tế là 22 bảng, nhưng mỗi bảng có trách nhiệm rất rõ và nhiều bảng chỉ là bảng mapping/snapshot.
Không nên cố giảm xuống còn 8–10 bảng chỉ để nhìn đơn giản; làm vậy phần booking sau này sẽ trở nên khó xử lý hơn.

Kiến trúc dữ liệu mình sẽ chốt ở mức này
Điểm quan trọng nhất là giờ database hỗ trợ được cả hai trường hợp:
V1

Một nhà xe
→ rất đơn giản để vận hành
và:
V2

Nhiều nhà xe
→ không cần redesign database
đồng thời giải quyết đúng bài toán bạn vừa nêu:
User:
Đắk Lắk → Hà Nội

↓ Search

Chỉ những Trip thực sự:
- đi qua Đắk Lắk,
- cho phép đón,
- sau đó đi qua Hà Nội,
- cho phép trả,
- đúng ngày,
- còn ghế trên đoạn đó

mới xuất hiện.
Và một ghế được tái sử dụng trên các đoạn không overlap.

## M2 authentication persistence decision

The approved M2 addition is `refresh_tokens`, created by Flyway V3. Existing M1
tables and migrations are unchanged.

| Column | Type | Constraint |
| --- | --- | --- |
| id | BIGINT | Auto-increment primary key |
| user_id | BIGINT | Required foreign key to users(id), indexed |
| token_hash | VARCHAR(64), ASCII binary collation | Required unique SHA-256 hex digest |
| expires_at | DATETIME(6) | Required UTC expiry, indexed |
| created_at | DATETIME(6) | Required UTC creation time |

Raw refresh tokens are never persisted. Rotation consumes the old row and creates
a new row in one transaction. User-row locking serializes rotation with password
changes; password changes delete all refresh-token rows for that user. There is
no session history or token-family subsystem. Expired rows cannot authenticate;
automatic removal of expired rows is not part of M2.

## M3 shared master-data decision

`bus_types`, `seat_templates`, `routes`, and `route_stops` remain global master data.
M3 operator APIs expose them read-only. Operator ownership begins at `buses` and
`operator_routes`; fares are operator-scoped through `operator_routes`. Global
master-data administration is deferred to SYSTEM_ADMIN/V2, with no M3 ownership
columns or association tables added.
