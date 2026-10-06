# V1.5 contract status (M19)

The implementation sections M12–M18C below supersede the historical numbered
proposal where they differ. All routes use /api/v1. No API was renamed in M19.
PENDING booking = a successfully reserved booking (“Đã giữ chỗ”); payment PENDING
is separately “Chưa thanh toán”. Every payment/refund remains simulated.

Current corrections: no /operator/dashboard endpoint (UI composes trips, reports
summary and fleet/readiness); no /bookings/{id}/confirm or /payment/confirm; no
/reports/revenue or /reports/occupancy and no whole-trip cancellation endpoint.
Use M16A collection, M17 whole-booking cancellation and M18A report endpoints.
POST booking is not generally safe to replay after an uncertain response: check
history first. Successful payment/cancellation use their documented idempotency.

BUSGO – REST API CONTRACT v1
1. API Overview
Base URL:
/api/v1
Data format:
application/json
Authentication:
Authorization: Bearer <access_token>
Các API public:
    • đăng ký
    • đăng nhập
    • lấy danh sách địa điểm
    • tìm chuyến
    • xem chi tiết chuyến
Các API booking yêu cầu CUSTOMER đăng nhập.
Các API đọc vận hành /operator/** cho phép OPERATOR_STAFF hoặc OPERATOR_ADMIN
theo ma trận M13 bên dưới. Quản lý đội xe/tuyến/nhân viên và mọi thay đổi yêu cầu
OPERATOR_ADMIN. SYSTEM_ADMIN không được sử dụng operator context.

2. Response Format
2.1 Success response
Với object:
{
  "data": {
  }
}
Với pagination:
{
  "data": [],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 125,
    "totalPages": 7
  }
}

3. Error Response
Format thống nhất:
{
  "code": "SEAT_NOT_AVAILABLE",
  "message": "Seat A01 is no longer available.",
  "details": null,
  "timestamp": "2026-09-18T21:00:00+07:00"
}
Validation:
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed.",
  "details": {
    "email": "Email is invalid",
    "phone": "Phone is required"
  },
  "timestamp": "2026-09-18T21:00:00+07:00"
}

4. HTTP Status Convention
200 OK
201 Created
204 No Content

400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
422 Unprocessable Entity

500 Internal Server Error
Các lỗi nghiệp vụ như:
SEAT_NOT_AVAILABLE
BUS_SCHEDULE_CONFLICT
ưu tiên:
409 Conflict

5. Authentication API
POST /auth/register
Public.
Request
{
  "fullName": "Nguyen Van A",
  "email": "user@example.com",
  "phone": "0901234567",
  "password": "Password123!"
}
Response
{
  "data": {
    "id": 15,
    "fullName": "Nguyen Van A",
    "email": "user@example.com",
    "phone": "0901234567",
    "role": "CUSTOMER"
  }
}
Errors
EMAIL_ALREADY_EXISTS
PHONE_ALREADY_EXISTS
INVALID_PASSWORD

6. Login
POST /auth/login
Request
{
  "email": "user@example.com",
  "password": "Password123!"
}
Response
{
  "data": {
    "accessToken": "jwt...",
    "refreshToken": "jwt...",
    "expiresIn": 3600,
    "user": {
      "id": 15,
      "fullName": "Nguyen Van A",
      "email": "user@example.com",
      "roles": [
        "CUSTOMER"
      ]
    }
  }
}

7. Refresh Token
POST /auth/refresh
{
  "refreshToken": "..."
}
Response:
{
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "expiresIn": 3600
  }
}

8. Current User
GET /users/me
Authenticated.
Response:
{
  "data": {
    "id": 15,
    "fullName": "Nguyen Van A",
    "email": "user@example.com",
    "phone": "0901234567",
    "roles": [
      "CUSTOMER"
    ]
  }
}

9. Update Profile
PATCH /users/me
{
  "fullName": "Nguyen Van B",
  "phone": "0912345678"
}

10. Change Password
POST /users/me/change-password
{
  "currentPassword": "Password123!",
  "newPassword": "NewPassword456!"
}

11. Location Search
GET /locations
Public.
Query:
?q=da
Ví dụ:
GET /api/v1/locations?q=da
Response:
{
  "data": [
    {
      "id": 10,
      "name": "Đà Nẵng",
      "province": "Đà Nẵng"
    },
    {
      "id": 17,
      "name": "Đà Lạt",
      "province": "Lâm Đồng"
    }
  ]
}
Frontend sử dụng API này cho autocomplete.

12. Trip Search
GET /trips/search
Đây là API public quan trọng nhất.
Parameters:
pickupLocationId
dropoffLocationId
departureDate
Optional:
operatorId
busTypeId
minPrice
maxPrice
departureFrom
departureTo
sort
page
size

M5 time rule: `departureDate` là ngày kinh doanh tại `Asia/Ho_Chi_Minh`.
Backend chuyển khoảng nửa mở `[00:00 ngày đã chọn, 00:00 ngày kế tiếp)` sang UTC
và áp dụng khoảng đó cho `plannedDepartureTime` của selected pickup TripStop, không
phải chỉ `Trip.departureTime`. Response scheduled time tiếp tục dùng ISO-8601 UTC (`Z`).

`departureFrom` và `departureTo` là local time trong cùng ngày kinh doanh;
hai đầu mút đều inclusive. Nếu truyền cả hai thì `departureFrom <= departureTo`.
Ví dụ:
GET /api/v1/trips/search?pickupLocationId=10&dropoffLocationId=20&departureDate=2026-09-25&page=0&size=20

13. Search Validation
Backend phải kiểm tra:
pickupLocationId != dropoffLocationId
Trip chỉ được trả về nếu:
Trip có pickup TripStop
AND pickup.allowPickup = true

Trip có dropoff TripStop
AND dropoff.allowDropoff = true

pickup.stopOrder < dropoff.stopOrder

Trip status = SCHEDULED

Trip chạy phù hợp ngày yêu cầu

Trip chỉ được trả về nếu có exact ACTIVE fare giá > 0 cho OperatorRoute và cặp
source RouteStop đã chọn, đồng thời có ít nhất một TripSeat AVAILABLE trên mọi
TripSegment thuộc đoạn pickup → dropoff. Thiếu fare hoặc availableSeats = 0 thì
loại riêng Trip đó khỏi kết quả, không làm lỗi toàn bộ search.

14. Search Result Response
{
  "data": [
    {
      "tripId": 101,

      "operator": {
        "id": 1,
        "name": "GiangBus"
      },

      "busType": {
        "id": 3,
        "name": "Limousine 22 phòng"
      },

      "pickup": {
        "tripStopId": 1002,
        "locationId": 10,
        "name": "Đắk Lắk",
        "departureTime": "2026-09-25T18:30:00+07:00"
      },

      "dropoff": {
        "tripStopId": 1006,
        "locationId": 20,
        "name": "Hà Nội",
        "arrivalTime": "2026-09-26T10:00:00+07:00"
      },

      "durationMinutes": 930,

      "price": 650000,

      "availableSeats": 5
    }
  ],

  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}

15. Available Seat Count
availableSeats phải được tính theo:
pickup → dropoff
Không được tính toàn bộ trip.
Một seat AVAILABLE khi mọi SeatSegmentInventory thuộc đoạn yêu cầu đều AVAILABLE.
Phải đếm số TripSeat thỏa toàn bộ đoạn, không đếm số inventory row AVAILABLE.
Search chỉ đọc inventory và không reserve ghế.

16. Search Sorting
Parameter:
sort
Supported values:
PRICE_ASC
PRICE_DESC
DEPARTURE_ASC
DEPARTURE_DESC
Default:
DEPARTURE_ASC

17. Trip Detail
GET /trips/{tripId}
M5 bắt buộc truyền đồng thời:
pickupLocationId
dropoffLocationId
Ví dụ:
GET /api/v1/trips/101?pickupLocationId=10&dropoffLocationId=20

Thiếu một hoặc cả hai parameter trả 400. Endpoint biểu diễn selected journey và
validate direction, pickup/dropoff permission, future SCHEDULED pickup, exact fare,
và segment availability. Trip tồn tại nhưng thiếu exact ACTIVE fare hoặc không còn
ghế usable trả `TRIP_NOT_BOOKABLE`; search tương ứng chỉ loại Trip đó.

`GET /trips/search`, `GET /trips/{tripId}` và `GET /trips/{tripId}/seats` là public.
Quyền public không áp dụng cho `/operator/**` hoặc bất kỳ mutation endpoint nào.
Response:
{
  "data": {
    "id": 101,

    "operator": {
      "id": 1,
      "name": "GiangBus"
    },

    "bus": {
      "busType": "Limousine 22 phòng"
    },

    "status": "SCHEDULED",

    "stops": [
      {
        "tripStopId": 1001,
        "locationId": 5,
        "name": "TP.HCM",
        "stopOrder": 1,
        "allowPickup": true,
        "allowDropoff": false,
        "arrivalTime": null,
        "departureTime": "2026-09-25T12:00:00+07:00"
      },
      {
        "tripStopId": 1002,
        "locationId": 10,
        "name": "Đắk Lắk",
        "stopOrder": 2,
        "allowPickup": true,
        "allowDropoff": true,
        "arrivalTime": "2026-09-25T18:20:00+07:00",
        "departureTime": "2026-09-25T18:30:00+07:00"
      }
    ]
  }
}

18. Seat Map
GET /trips/{tripId}/seats
Parameters bắt buộc:
pickupLocationId
dropoffLocationId
Ví dụ:
GET /api/v1/trips/101/seats?pickupLocationId=10&dropoffLocationId=20

Backend resolve hai `TripStop` từ snapshot bất biến của Trip, validate quyền đón/trả,
hướng đi, trạng thái SCHEDULED, thời gian pickup trong tương lai, chuỗi segment liên
tiếp và exact ACTIVE fare giống M5. Endpoint là read-only và không reserve ghế.

19. Seat Map Response
{
  "data": {
    "tripId": 101,

    "pickup": {
      "tripStopId": 1002,
      "locationId": 10,
      "name": "Đắk Lắk",
      "departureTime": "2026-09-25T18:30:00+07:00"
    },

    "dropoff": {
      "tripStopId": 1006,
      "locationId": 20,
      "name": "Hà Nội",
      "arrivalTime": "2026-09-26T10:00:00+07:00"
    },

    "price": 650000,
    "availableSeatCount": 1,

    "seats": [
      {
        "tripSeatId": 501,
        "seatCode": "A01",
        "row": 1,
        "column": 1,
        "floor": 1,
        "seatType": "STANDARD",
        "available": true
      },
      {
        "tripSeatId": 502,
        "seatCode": "A02",
        "row": 1,
        "column": 2,
        "floor": 1,
        "seatType": "STANDARD",
        "available": false
      }
    ]
  }
}
Frontend chỉ render.
Frontend không tự suy luận availability.

20. Seat Availability Semantics
`available = true` chỉ khi cùng một `TripSeat` có inventory `AVAILABLE` trên mọi
required `TripSegment` của journey. `HELD`, `BOOKED`, `BLOCKED`, hoặc thiếu inventory
row trên bất kỳ required segment nào đều cho `available = false`. Inventory ngoài
journey không ảnh hưởng. API không expose raw inventory status hoặc dữ liệu hold.

`availableSeatCount` luôn bằng số phần tử `seats` có `available = true`. Journey hợp
lệ nhưng sold out vẫn trả HTTP 200, count 0 và toàn bộ seat false. Đây là quan sát
tại thời điểm đọc, không giữ ghế và không bảo đảm ghế vẫn còn ở request M7 sau đó.

21. Create Seat Hold
POST /api/v1/seat-holds
Role:
CUSTOMER
Request
{
  "tripId": 101,
  "pickupLocationId": 10,
  "dropoffLocationId": 20,
  "tripSeatIds": [
    501,
    503
  ]
}

22. Seat Hold Processing
Backend:
1. Resolve TripStop snapshots, future SCHEDULED Trip, complete segments và exact ACTIVE fare
   bằng cùng `CustomerJourneyResolver`/`TripSegmentResolver` của M5/M6.
2. Reject empty, duplicate, over-five, malformed seat lists; every TripSeat must belong to Trip.
3. Normalize seat IDs and segment IDs, then lock the complete inventory matrix in
   `(trip_seat_id, trip_segment_id)` order with `SELECT ... FOR UPDATE`.
4. While those rows remain locked, require the exact expected row count and require every row
   to be AVAILABLE or an expired HELD row (`hold_expires_at <= capturedNow`).
5. Atomically set the whole matrix to HELD with one UUID token, authenticated owner ID, and
   one server-derived expiry. Any unavailable or missing row rolls back the entire request.

The lock, check, expired-row reclamation, and update run in one MySQL transaction. The same
physical seat remains reusable for non-overlapping segment sets.

23. Seat Hold Response
{
  "data": {
    "holdToken": "29fc9351-f941-4dd9-a1d6-abc123",
    "tripId": 101,

    "pickup": {
      "tripStopId": 1002,
      "locationId": 10,
      "name": "Đắk Lắk",
      "departureTime": "2026-09-18T12:00:00Z"
    },
    "dropoff": {
      "tripStopId": 1006,
      "locationId": 20,
      "name": "Hà Nội",
      "arrivalTime": "2026-09-19T03:00:00Z"
    },
    "tripSeatIds": [501, 503],
    "seats": [
      {
        "tripSeatId": 501,
        "seatCode": "A01"
      },
      {
        "tripSeatId": 503,
        "seatCode": "A03"
      }
    ],

    "pricePerSeat": 650000,
    "totalPrice": 1300000,
    "expiresAt": "2026-09-18T14:40:00Z",
    "status": "ACTIVE"
  }
}

24. Seat Hold Errors
SEAT_NOT_AVAILABLE
INVALID_PICKUP_STOP
INVALID_DROPOFF_STOP
INVALID_ROUTE_DIRECTION
TRIP_ALREADY_DEPARTED
TRIP_NOT_BOOKABLE
VALIDATION_ERROR
Ví dụ:
{
  "code": "SEAT_NOT_AVAILABLE",
  "message": "One or more selected seats are no longer available.",
  "details": {
    "seatCodes": [
      "A03"
    ]
  },
  "timestamp": "..."
}

25. Seat Hold Rules
V1:
MAX_SEATS_PER_BOOKING = 5
HOLD_DURATION = 10 minutes
Configuration dùng `busgo.booking.max-seats-per-hold=5` và
`busgo.booking.seat-hold-duration=PT10M`. Client không được gửi owner, token, expiry,
status hoặc price. Một non-expired HELD row không thể bị giữ lại, kể cả bởi cùng user.

26. Get Current Hold
GET /api/v1/seat-holds/{holdToken}
Authenticated.
Chỉ chủ hold mới xem được.
Response:
{
  "data": {
    "holdToken": "...",
    "status": "ACTIVE",
    "expiresAt": "...",
    "tripId": 101,
    "pickup": { "tripStopId": 1002, "locationId": 10, "name": "Đắk Lắk", "departureTime": "..." },
    "dropoff": { "tripStopId": 1006, "locationId": 20, "name": "Hà Nội", "arrivalTime": "..." },
    "tripSeatIds": [501, 503],
    "seats": [
      { "tripSeatId": 501, "seatCode": "A01" },
      { "tripSeatId": 503, "seatCode": "A03" }
    ],
    "pricePerSeat": 650000,
    "totalPrice": 1300000
  }
}
Possible status:
ACTIVE
EXPIRED

GET không extend/refresh expiry. Token của user khác và token đã release/cleanup đều trả
`404 SEAT_HOLD_NOT_FOUND`; stale expired metadata chưa cleanup có thể trả `EXPIRED`.

27. Release Hold
DELETE /api/v1/seat-holds/{holdToken}
Role CUSTOMER.
Response:
204 No Content
Backend chỉ release row có đồng thời token, authenticated owner và status HELD. DELETE là
idempotent và không leak token của user khác: unknown, already released và foreign token đều 204.

Cleanup chạy khoảng mỗi phút bằng một transactional database UPDATE có predicate
`status = 'HELD' AND hold_expires_at <= capturedNow`. Nó không chạm BOOKED/BLOCKED hoặc
active HELD. Correctness không phụ thuộc scheduler: create vẫn reclaim expired relevant rows
dưới pessimistic lock. M5/M6 tiếp tục read-only; stale expired HELD có thể hiện unavailable
cho đến cleanup hoặc locked reclamation.

28. Create Booking (M8)
POST /api/v1/bookings
CUSTOMER.
Request
{
  "holdToken": "29fc9351-f941-4dd9-a1d6-abc123",
  "contactName": "Nguyen Van A",
  "contactPhone": "0901234567",
  "contactEmail": "user@example.com"
}

29. Booking Creation Rules
Backend phải:
1. Find hold by holdToken.
2. Verify hold belongs to current user.
3. Verify hold has not expired.
4. Verify seats still HELD by this hold.
5. Recalculate fare.
6. Create Booking.
7. Create BookingItems.
8. Chuyển đúng inventory matrix HELD → BOOKED, clear hold metadata và link booking item.
Không lấy:
totalAmount
price
từ frontend làm giá trị tin cậy.

M8 tạo booking `PENDING`. Payment, confirm, cancellation và ticket thuộc M9. Foreign/nonexistent
hold trả `404 SEAT_HOLD_NOT_FOUND`; expired trả `409 SEAT_HOLD_EXPIRED`; matrix hỏng trả
`409 SEAT_NOT_AVAILABLE`; thiếu exact active fare trả `409 TRIP_NOT_BOOKABLE`.

30. Create Booking Response
{
  "data": {
    "bookingId": 701,
    "bookingCode": "BG2609180001",

    "status": "PENDING",

    "trip": {
      "id": 101,
      "operator": "GiangBus"
    },

    "pickup": {
      "name": "Đắk Lắk",
      "time": "2026-09-25T18:30:00+07:00"
    },

    "dropoff": {
      "name": "Hà Nội",
      "time": "2026-09-26T10:00:00+07:00"
    },

    "seats": [
      "A01",
      "A03"
    ],

    "totalAmount": 1300000,

    "createdAt": "2026-09-18T10:00:00Z"
  }
}

31. Mock Payment
POST /api/v1/bookings/{bookingId}/payments/mock-confirm
CUSTOMER.
Chỉ owner booking.
Request để trống; amount, customer và status luôn do server suy ra.
Backend:
    • lock Booking bằng database row lock
    • kiểm tra inventory đã BOOKED và link đúng BookingItem từ M8
    • tạo Payment MOCK_ONLINE/PAID với Booking.totalAmount
    • Booking PENDING → CONFIRMED
    • ghi booking_status_history
    • tạo đúng một Ticket cho mỗi BookingItem
Tất cả trong cùng transaction. Gọi lại sau khi thành công trả cùng kết quả và không tạo bản ghi trùng.
Lần confirm đầu tiên và các lần retry thành công đều trả HTTP 200.

32. Mock Payment Response
{
  "data": {
    "paymentId": 801,
    "bookingId": 701,
    "bookingCode": "BG2609180001",
    "method": "MOCK_ONLINE",
    "amount": 650000,
    "bookingStatus": "CONFIRMED",
    "paymentStatus": "PAID",
    "transactionReference": "MOCK-...",
    "paidAt": "2026-09-18T10:05:00Z"
  }
}

33. Cash Payment
Nếu:
paymentMethod = CASH
V1 có thể tạo:
booking = CONFIRMED
payment = PENDING
và payment được STAFF xác nhận khi khách trả tiền.
Điều này sát thực tế hơn việc bắt CASH phải PAID ngay.

34. My Bookings
GET /api/v1/bookings/me
CUSTOMER.
Parameters:
status
page
size
Status filter:
UPCOMING
COMPLETED
CANCELLED
Response:
{
  "data": [
    {
      "bookingId": 701,
      "bookingCode": "BG2609180001",
      "operator": "GiangBus",
      "pickup": "Đắk Lắk",
      "dropoff": "Hà Nội",
      "departureTime": "2026-09-25T18:30:00+07:00",
      "seatCodes": [
        "A01",
        "A03"
      ],
      "totalAmount": 1300000,
      "bookingStatus": "CONFIRMED",
      "paymentStatus": "PAID"
    }
  ]
}

35. Booking Detail
GET /api/v1/bookings/{bookingId}
Owner hoặc STAFF/ADMIN có quyền tương ứng.
Response:
{
  "data": {
    "id": 701,
    "bookingCode": "BG2609180001",

    "status": "CONFIRMED",

    "operator": {
      "id": 1,
      "name": "GiangBus"
    },

    "trip": {
      "id": 101
    },

    "pickup": {
      "tripStopId": 1002,
      "name": "Đắk Lắk",
      "time": "2026-09-25T18:30:00+07:00"
    },

    "dropoff": {
      "tripStopId": 1006,
      "name": "Hà Nội",
      "time": "2026-09-26T10:00:00+07:00"
    },

    "contact": {
      "fullName": "Nguyen Van A",
      "phone": "0901234567",
      "email": "user@example.com"
    },

    "items": [
      {
        "seatCode": "A01",
        "passengerName": "Nguyen Van A",
        "price": 650000
      }
    ],

    "payment": {
      "method": "MOCK_ONLINE",
      "status": "PAID"
    },

    "totalAmount": 650000
  }
}

36. Cancel Booking
POST /bookings/{bookingId}/cancel
CUSTOMER.
Request:
{
  "reason": "Change of travel plan"
}
Backend validate:
booking owner
booking status cancellable
trip has not departed
cancellation threshold

37. Customer Cancellation Rule
Config:
CUSTOMER_CANCEL_BEFORE_HOURS = 6
Nếu:
departureTime - now <= 6 hours
return:
BOOKING_CANCELLATION_NOT_ALLOWED

38. Cancellation Processing
Booking CONFIRMED
→ CANCELLED
Seat inventory thuộc booking:
BOOKED → AVAILABLE
Nếu mock payment:
PAID → REFUNDED
trong V1.
Ghi booking_status_history.

39. Ticket API
GET /api/v1/bookings/{bookingId}/ticket
Owner.
Booking phải CONFIRMED và có Payment PAID cùng bộ Ticket đầy đủ; nếu chưa thanh toán trả
`TICKET_NOT_AVAILABLE`. Foreign booking trả `BOOKING_NOT_FOUND`.
Response:
{
  "data": {
    "bookingId": 701,
    "bookingCode": "BG2609180001",
    "status": "CONFIRMED",
    "paymentStatus": "PAID",
    "paymentMethod": "MOCK_ONLINE",
    "amount": 650000,
    "operator": { "id": 1, "name": "GiangBus" },
    "route": { "id": 1, "name": "Đắk Lắk - Hà Nội" },
    "pickup": { "tripStopId": 1002, "locationId": 1, "name": "Đắk Lắk", "time": "2026-09-25T18:30:00Z" },
    "dropoff": { "tripStopId": 1006, "locationId": 2, "name": "Hà Nội", "time": "2026-09-26T10:00:00Z" },
    "departureTime": "2026-09-25T18:30:00+07:00",
    "arrivalTime": "2026-09-26T10:00:00+07:00",
    "tickets": [{
      "ticketId": 901,
      "ticketCode": "TKT-...",
      "passengerName": "Nguyen Van A",
      "seatCode": "A01",
      "qrData": "TKT-..."
    }]
  }
}
Một BookingItem/ghế có đúng một ticket. Nếu passengerName của item null thì dùng contactName.
Frontend render QR từ `qrData`; backend không tạo QR image hoặc PDF trong M9.

40. Operator Dashboard
UI composition, not GET /operator/dashboard: operator trips/occupancy/bookings,
M18A /operator/reports/summary and M18C /operator/fleet/readiness.

41. Operator Bus Types
GET /operator/bus-types
OPERATOR_ADMIN.
GET /operator/bus-types/{id}

Trong M3, BusType và SeatTemplate là global master data chỉ đọc đối với
OPERATOR_ADMIN. API chỉ trả BusType ACTIVE và SeatTemplate active. Quản trị master
data được hoãn cho SYSTEM_ADMIN/V2; dữ liệu V1 dùng seed/test fixtures.

42. Update Bus Type
Không có API OPERATOR_ADMIN cập nhật BusType/SeatTemplate trong M3 vì đây là global
master data dùng chung giữa các operator.

43. Bus Management
GET /operator/buses
Filters:
status
busTypeId
q
page
size
POST /operator/buses
{
  "licensePlate": "51B-12345",
  "busTypeId": 3
}
GET /operator/buses/{id}
PATCH /operator/buses/{id}
{
  "licensePlate": "51B-12345",
  "status": "MAINTENANCE"
}

44. Routes
GET /operator/routes
Routes mà current operator khai thác.
POST /operator/routes
Body:
{
  "routeId": 10
}
Chỉ attach global Route đã tồn tại vào current operator. ID dưới
`/operator/routes/{id}` luôn là OperatorRoute ID.

45. Create Route
Global Route/RouteStop creation and editing are deferred to SYSTEM_ADMIN/V2.
OPERATOR_ADMIN can inspect active definitions through:
GET /operator/route-catalog
GET /operator/route-catalog/{routeId}

46. Route Validation
Phải có:
>= 2 stops
First stop:
allowPickup = true
khuyến nghị.
Last stop:
allowDropoff = true
khuyến nghị.
Không được duplicate location trong cùng route V1.
estimatedOffsetMinutes phải tăng dần.

47. Route Detail
GET /operator/routes/{operatorRouteId}
Response gồm association của current operator và global ordered stops chỉ đọc.

48. Route Update
PATCH /operator/routes/{operatorRouteId}
Chỉ cập nhật association status ACTIVE/INACTIVE. Không cập nhật global Route/RouteStop.

49. Route Fare Management
GET /operator/routes/{operatorRouteId}/fares
PUT /operator/routes/{operatorRouteId}/fares
Request:
{
  "fares": [
    {
      "fromRouteStopId": 1,
      "toRouteStopId": 3,
      "price": 250000
    },
    {
      "fromRouteStopId": 1,
      "toRouteStopId": 5,
      "price": 700000
    }
  ]
}
Validation:
from.stopOrder < to.stopOrder
price > 0

50. Trip Management
GET /operator/trips
Filters:
date
businessDate
routeId (global Route ID)
busId
status
page
size

M3/M4 compatibility note: `date` vẫn là một ngày UTC. M14A thêm
`businessDate=YYYY-MM-DD`, được hiểu là ngày lịch tại `Asia/Ho_Chi_Minh` và đổi
thành khoảng UTC nửa mở `[00:00, 00:00 ngày kế tiếp)` trước khi truy vấn
`departureTime`. Ví dụ `businessDate=2030-09-21` truy vấn
`[2030-09-20T17:00:00Z, 2030-09-21T17:00:00Z)`. Không được truyền đồng thời
`date` và `businessDate`; trường hợp đó trả HTTP 400 `VALIDATION_ERROR`.
Pagination, các filter còn lại và sort ổn định `departureTime ASC, id ASC` không
đổi. Timestamps trong response tiếp tục là offset-aware UTC. Quyền đọc hỗ trợ cả
`OPERATOR_ADMIN` và `OPERATOR_STAFF`, luôn giới hạn theo operator_staff của user
đã xác thực.

51. Create Trip
POST /operator/trips
{
  "operatorRouteId": 8,
  "busId": 12,
  "departureTime": "2026-09-25T18:30:00+07:00"
}
Request không nhận `operatorId`, `arrivalTime`, stops, segments, seats hoặc inventory.
`departureTime` bắt buộc là ISO-8601 có offset. Backend chuẩn hóa instant về UTC;
datetime không có timezone bị từ chối.
Backend:
Validate operator route.
Validate bus ownership.
Validate bus status.
Validate active RouteStops và active SeatTemplates.
Calculate estimatedArrivalTime từ offset của active RouteStop cuối cùng.
Check schedule conflict.

Create Trip.
Create TripStops.
Create TripSegments.
Create TripSeats.
Create inventory.

52. Trip Creation Response
{
  "data": {
    "id": 101,
    "status": "SCHEDULED",

    "route": {
      "operatorRouteId": 8,
      "routeId": 3,
      "name": "Đắk Lắk - Hà Nội"
    },

    "bus": {
      "id": 12,
      "licensePlate": "51B-12345",
      "busTypeId": 2,
      "busTypeName": "Limousine"
    },

    "departureTime": "2026-09-25T11:30:00Z",
    "estimatedArrivalTime": "2026-09-26T03:00:00Z",

    "seatCount": 22,
    "segmentCount": 4
  }
}

53. Bus Schedule Conflict
Nếu bus đang có:
Trip A
18:00 → 06:00
và tạo:
Trip B
22:00 → 08:00
return:
{
  "code": "BUS_SCHEDULE_CONFLICT",
  "message": "The selected bus is already assigned to another trip during this period."
}
HTTP:
409

54. Operator Trip Detail
GET /operator/trips/{tripId}
Bao gồm:
Trip info
Stops
Bus
Segments
Seat snapshots

M4 chưa trả seat occupancy, bookings hoặc revenue. TripStop planned time:
stop đầu có arrival null và departure bằng Trip departure; stop giữa có arrival/departure
bằng Trip departure cộng offset; stop cuối có arrival bằng thời gian tính toán và departure null.
Tất cả scheduled datetime trả về là offset-aware UTC (`Z`).

55. Passenger Manifest
GET /operator/trips/{tripId}/passengers
Response:
{
  "data": [
    {
      "bookingCode": "BG001",
      "seatCode": "A01",
      "passengerName": "Nguyen Van A",
      "phone": "0901234567",
      "pickup": "Đắk Lắk",
      "dropoff": "Đà Nẵng",
      "bookingStatus": "CONFIRMED",
      "paymentStatus": "PAID"
    }
  ]
}

56. Operator Seat Map
GET /operator/trips/{tripId}/seats
Có thể truyền pickup/dropoff hoặc xem toàn trip.
Nếu không truyền segment, response nên cho occupancy chi tiết:
{
  "tripSeatId": 501,
  "seatCode": "A01",
  "segments": [
    {
      "from": "Đắk Lắk",
      "to": "Đà Nẵng",
      "status": "BOOKED"
    },
    {
      "from": "Đà Nẵng",
      "to": "Huế",
      "status": "AVAILABLE"
    }
  ]
}
Trang này rất hữu ích cho demo admin.

57. Block Seat
POST /operator/trips/{tripId}/seats/{tripSeatId}/block
OPERATOR_STAFF hoặc ADMIN.
Request:
{
  "fromTripStopId": 1002,
  "toTripStopId": 1006,
  "reason": "Reserved for maintenance"
}
Backend set:
AVAILABLE → BLOCKED
trên các segment yêu cầu.
Không được block inventory đã BOOKED.

58. Unblock Seat
POST /operator/trips/{tripId}/seats/{tripSeatId}/unblock
Chỉ BLOCKED → AVAILABLE.

59. Operator Booking List
GET /operator/bookings
Filters:
q
status
paymentStatus
tripId
date
page
size
q search:
booking code
customer name
phone

60. Operator Booking Detail
GET /operator/bookings/{bookingId}
Chỉ booking thuộc operator hiện tại.

61–63. Assisted booking, collection and cancellation
See M16A/M17 below. POST /operator/bookings creates a PENDING reservation with
BOOKED inventory. POST /operator/bookings/{id}/payments records mock collection.
POST /operator/bookings/{id}/cancel accepts {note?}; OPERATOR_ADMIN only.
No staff mutation or separate booking-confirm endpoint is implemented.

64. Customers
GET /operator/customers
OPERATOR_ADMIN.
Khuyến nghị chỉ hiển thị customer đã từng booking với operator hiện tại.
Response:
{
  "data": [
    {
      "id": 15,
      "fullName": "Nguyen Van A",
      "email": "user@example.com",
      "phone": "0901234567",
      "totalBookings": 5,
      "totalSpent": 3500000
    }
  ]
}

65. Staff Management
GET /operator/staff
POST /operator/staff
Request:
{
  "fullName": "Tran Van B",
  "email": "staff@giangbus.vn",
  "phone": "0900000000",
  "password": "InitialPassword123!",
  "role": "OPERATOR_STAFF"
}
Only:
OPERATOR_ADMIN

66. Update Staff
PATCH /operator/staff/{staffId}
{
  "status": "INACTIVE"
}
Không hard delete.

67–69. Management reports
See M18A: /operator/reports/summary, /trips and /routes; fromDate/toDate,
maximum 366 Vietnam business dates. Simulated collections/refunds/net, not revenue.

70. Trip Cancellation — HISTORICAL PROPOSAL, NOT IMPLEMENTED
POST /operator/trips/{tripId}/cancel
Request:
{
  "reason": "Vehicle unavailable"
}
Backend phải:
Trip → CANCELLED
Tất cả active booking của trip:
→ CANCELLED
Inventory:
HELD/BOOKED → AVAILABLE
Payment mock PAID:
→ REFUNDED
Ghi booking histories.
Trong V1 có thể xử lý synchronous vì dữ liệu nhỏ.

71. Critical Error Codes
Authentication
INVALID_CREDENTIALS
ACCESS_TOKEN_EXPIRED
REFRESH_TOKEN_INVALID
ACCESS_DENIED
User
USER_NOT_FOUND
EMAIL_ALREADY_EXISTS
PHONE_ALREADY_EXISTS
Route
ROUTE_NOT_FOUND
INVALID_ROUTE
INVALID_ROUTE_DIRECTION
INVALID_PICKUP_STOP
INVALID_DROPOFF_STOP
DUPLICATE_ROUTE_STOP
Trip
TRIP_NOT_FOUND
TRIP_NOT_BOOKABLE
TRIP_ALREADY_DEPARTED
TRIP_CANCELLED
BUS_SCHEDULE_CONFLICT
Seat
SEAT_NOT_FOUND
SEAT_NOT_AVAILABLE
SEAT_ALREADY_BOOKED
SEAT_BLOCKED
SEAT_HOLD_EXPIRED
SEAT_HOLD_NOT_FOUND
SEAT_HOLD_ACCESS_DENIED
Booking
BOOKING_NOT_FOUND
BOOKING_ALREADY_CANCELLED
BOOKING_ALREADY_CONFIRMED
BOOKING_CANCELLATION_NOT_ALLOWED
INVALID_BOOKING_STATE
Payment
PAYMENT_NOT_FOUND
PAYMENT_ALREADY_PAID
INVALID_PAYMENT_STATE
BOOKING_NOT_PAYABLE
PAYMENT_ALREADY_INVALID
BOOKING_INVENTORY_INCONSISTENT
TICKET_NOT_AVAILABLE

72. Idempotency
Một điểm quan trọng cho booking/payment.
Request thanh toán nên hỗ trợ:
Idempotency-Key
Ví dụ:
Idempotency-Key: e241bd...
Nếu frontend gửi lại request do network timeout, backend không được tạo hoặc thanh toán booking hai lần.
V1 có thể chỉ implement idempotency cho:
POST /bookings
POST /bookings/{id}/payments/mock-confirm
Nếu muốn giảm scope, có thể đánh dấu đây là SHOULD HAVE.

73. Security Rules
Backend không được tin:
operatorId
customerId
price
role
totalAmount
paymentStatus
từ frontend khi có thể suy ra từ authentication/database.
Ví dụ:
current customer
phải lấy từ JWT.
Operator:
current operator
phải lấy từ account STAFF.

74. Operator Data Isolation
Mọi API:
/operator/**
phải enforce:
resource.operatorId == currentUser.operatorId
Ví dụ Staff nhà xe A gửi:
GET /operator/trips/999
mà trip 999 thuộc nhà xe B:
403 hoặc 404
Khuyến nghị trả:
404
để tránh expose resource tồn tại.

75. Date/Time Convention
Backend/API dùng ISO-8601.
Ví dụ:
2026-09-25T18:30:00+07:00
Database có thể lưu timezone-consistent timestamps.
Tuyệt đối tránh frontend/backend tự hiểu timezone khác nhau.

76. Money Convention
Database:
DECIMAL
API có thể trả integer VND:
{
  "price": 650000
}
Không sử dụng floating-point cho tiền.

77. Pagination Convention
Parameters:
page=0
size=20
Giới hạn:
size <= 100
Default:
page=0
size=20

78. API Naming Rule
URL sử dụng noun plural:
/trips
/bookings
/buses
/routes
Không sử dụng:
/getTrips
/createBooking
/deleteBus
Action endpoints chỉ dùng khi không thể biểu diễn CRUD tự nhiên:
/cancel
/confirm
/block
/unblock

79. Minimal API Set để đạt V1
Codex phải ưu tiên API theo thứ tự:
Milestone 1
POST /auth/register
POST /auth/login

GET /locations
Milestone 2
GET /trips/search
GET /trips/{id}
GET /trips/{id}/seats
Milestone 3
POST /api/v1/seat-holds
GET /api/v1/seat-holds/{token}
DELETE /api/v1/seat-holds/{token}

POST /bookings
GET /bookings/me
GET /bookings/{id}
Milestone 4
POST /bookings/{id}/payments/mock-confirm
POST /bookings/{id}/cancel
GET /bookings/{id}/ticket
Milestone 5
/operator/bus-types
/operator/buses
/operator/routes
/operator/trips
Milestone 6
/operator/bookings
/operator/trips/{id}/passengers
/operator/dashboard
Milestone 7
staff
reports
seat blocking
polish

80. End-to-End API Demo
Hệ thống được xem là hoàn thành core backend nếu chạy được:
1.
POST /auth/login

2.
GET /locations?q=dak

3.
GET /trips/search
    pickup = Đắk Lắk
    dropoff = Hà Nội
    date = ...

4.
GET /trips/101/seats

5.
POST /api/v1/seat-holds
    A01

6.
POST /bookings

7.
POST /bookings/701/payments/mock-confirm

8.
GET /bookings/701/ticket
và admin:
1.
POST /operator/routes

2.
POST /operator/buses

3.
POST /operator/trips

4.
GET /operator/trips/101/passengers

5.
GET /operator/dashboard

81. Critical Integration Tests
Backend bắt buộc phải có test cho các case:
Test 1 – Invalid operator route
Nhà xe A không chạy Hà Nội
→ trip không được xuất hiện.
Test 2 – Wrong direction
Route:
Hà Nội → Đắk Lắk

Search:
Đắk Lắk → Hà Nội

→ 0 results.
Test 3 – Reuse seat on non-overlapping segment
A01
Đắk Lắk → Đà Nẵng BOOKED

A01
Đà Nẵng → Hà Nội

→ AVAILABLE.
Test 4 – Overlapping segment
A01
Đắk Lắk → Huế BOOKED

Request:
Đà Nẵng → Hà Nội

→ NOT AVAILABLE.
Test 5 – Concurrent hold
User A + User B
hold A01 cùng lúc.

→ chỉ 1 success.
Test 6 – Hold expiration
Hold expires
→ seat AVAILABLE.
Test 7 – Cancellation
Confirmed booking cancelled
→ affected inventory released.
Test 8 – Operator isolation
Staff A requests Trip B
→ denied.

82. Final API Principle
Business rule quan trọng nhất:
Frontend asks.
Backend decides.
Database protects.
Frontend có thể hiển thị ghế trống, giá, tuyến và trạng thái.
Nhưng backend luôn phải tính/validate lại:
route validity
seat availability
fare
booking state
payment state
authorization
trước khi ghi dữ liệu.

## M2 authentication implementation decisions

Approved password policy: at least 8 characters, nonblank, at most 72 UTF-8 bytes;
no uppercase/lowercase/digit/symbol requirements. Passwords are BCrypt hashes.
Emails are stripped and lowercased before lookup/registration. Phone remains
required at registration but is not unique, following the database design;
`PHONE_ALREADY_EXISTS` is therefore not emitted by M2 registration.

Access and refresh tokens are signed JWTs with distinct token-use claims and
finite configurable lifetimes (defaults: 1 hour / 7 days). Refresh tokens require
persisted SHA-256 hashes and single-use rotation. Password changes revoke all
refresh tokens. Already-issued access tokens remain valid until expiration, with
current user status, deletion state, and persisted roles checked on each request.
Only ACTIVE, non-deleted accounts may authenticate. Public registration assigns
CUSTOMER regardless of extra client-supplied role fields.

Registration returns HTTP 201. Profile PATCH updates supplied fullName/phone
fields and returns the same profile shape as GET; omitted/null fields are unchanged.
Password change returns HTTP 204. Invalid credentials, invalid access tokens,
and prohibited account states return HTTP 401 INVALID_CREDENTIALS; expired access
tokens return ACCESS_TOKEN_EXPIRED; invalid, expired, consumed, revoked, or
unpersisted refresh tokens return REFRESH_TOKEN_INVALID. Missing authentication
retains the foundation's HTTP 401 UNAUTHORIZED response. Denied authorization
returns HTTP 403 ACCESS_DENIED. Duplicate email returns HTTP 409 EMAIL_ALREADY_EXISTS.
Password policy violations return HTTP 400 INVALID_PASSWORD; other field validation
retains VALIDATION_ERROR. All errors retain the common API error envelope.

## M12 operator operations backend (P0)

All endpoints below require `OPERATOR_ADMIN`. The operator is resolved from the
authenticated staff membership; no endpoint accepts an `operatorId`. A resource
owned by another operator returns the same 404 as a missing resource.

### `GET /api/v1/operator/bookings`

Query parameters: `q` (maximum 100 characters), `tripId`, `status`,
`paymentStatus`, `date`, `page` (default 0), and `size` (default 20, maximum 100).
Results sort by `createdAt DESC, id DESC`. `date` is the booking creation date in
`Asia/Ho_Chi_Minh`; the backend converts its midnight boundaries to UTC before
querying. A booking with no payment row has the derived payment status `PENDING`.
API timestamps remain UTC offset date-times.

### `GET /api/v1/operator/bookings/{bookingId}`

Returns the booking/trip/route/customer/contact/stops, exact nullable
`BookingItem.passengerName`, item-level ticket summary, complete payment history,
amount, and timestamps. `ticket.passengerName` is a ticket snapshot and may be a
copy of the booking contact; it is not a verified passenger identity.

### `GET /api/v1/operator/trips/{tripId}/passengers`

Returns one row per booking item for `CONFIRMED` and `COMPLETED` bookings only.
Rows retain booking/item/seat IDs, journey stops, exact nullable item passenger
name, nullable ticket snapshot name/code, contact, and derived payment status.
No check-in, boarding, no-show, or identity-verification state is inferred.

### `GET /api/v1/operator/trips/{tripId}/occupancy`

Returns trip status, seat/segment counts, a derived whole-trip available-seat
count, per-segment `AVAILABLE`/`HELD`/`BOOKED`/`BLOCKED` counts, and every
seat-by-segment inventory state. `BOOKED` cells may include booking ID/code/status;
`HELD` cells include only expiry and never the hold owner's identity. No single
whole-trip status is assigned to a seat.

M14A completeness contract: the expected matrix is the trip snapshot seats
multiplied by the trip snapshot segments, never the set of existing inventory
rows. The response also contains `complete`, `expectedInventoryCellCount`,
`actualInventoryCellCount`, and `missingInventoryCellCount`. Every expected cell
appears in the seat's `segments` list. A missing inventory row is represented by
`missing: true` and a null/omitted `status`; a persisted cell has `missing: false`
and exactly one of the existing four statuses. Missing cells are not fabricated,
are never treated as `AVAILABLE`, and exclude their seat from
`wholeTripAvailableSeatCount`. The endpoint does not expire or rewrite `HELD`
rows. `OPERATOR_ADMIN` and `OPERATOR_STAFF` have the same operator-scoped read
contract; mutation permissions are unchanged.

### `PATCH /api/v1/operator/trips/{tripId}/status`

Body: `{ "status": "BOARDING" }`. Allowed transitions are strictly
`SCHEDULED -> BOARDING -> DEPARTED -> COMPLETED`. Repeating the current status is
an idempotent success. Backward/skipped transitions, entering/leaving `CANCELLED`,
and leaving `COMPLETED` return HTTP 409 `INVALID_TRIP_STATUS_TRANSITION`.

### Mock payment operational guard

New confirmation is allowed only while the trip is `SCHEDULED` or `BOARDING` and
the booking pickup departure is in the future. Otherwise it returns HTTP 409
`PAYMENT_WINDOW_CLOSED`. A previously valid confirmed payment remains idempotently
readable after departure.

## M13 system administration backend (P0)

`/api/v1/admin/**` requires `SYSTEM_ADMIN`. That role does not bypass the
operator namespace or operator ownership. Operator staff/admin context still
requires exactly one active membership in an active operator.

### System-admin operator APIs

- `GET /api/v1/admin/operators?q=&status=&page=0&size=20`
- `POST /api/v1/admin/operators`
- `GET /api/v1/admin/operators/{operatorId}`
- `PATCH /api/v1/admin/operators/{operatorId}`
- `PATCH /api/v1/admin/operators/{operatorId}/status`
- `GET /api/v1/admin/operators/{operatorId}/staff?q=&status=&role=&page=0&size=20`

List size is 1–100 and `q` is at most 100 characters. Operator lists sort by
`name ASC, id ASC`. Creation requires `name`, immutable normalized `code`,
`status`, and `initialAdmin` with `fullName`, `email`, `phone`, `password`, and
`staffCode`. It atomically creates the operator, dedicated user,
`OPERATOR_ADMIN` role, and ACTIVE membership. Passwords are never returned.
Operator PATCH accepts only name/phone/email/address. Activation requires an
active, login-capable operator admin; repeated status changes are idempotent.

### Operator staff APIs

- `GET /api/v1/operator/staff?q=&status=&role=&page=0&size=20`
- `POST /api/v1/operator/staff`
- `PATCH /api/v1/operator/staff/{staffId}`

These require `OPERATOR_ADMIN`. Creation accepts a new dedicated user's
`fullName`, `email`, `phone`, `password`, operator-scoped `staffCode`, and exactly
one role: `OPERATOR_ADMIN` or `OPERATOR_STAFF`. Existing accounts are not linked.
PATCH may change staff code, membership status, or the operator role. It cannot
remove the final active, login-capable administrator. Foreign staff IDs use
`STAFF_NOT_FOUND`; staff codes are unique per operator.

### Read-only staff matrix

`OPERATOR_STAFF` and `OPERATOR_ADMIN` may GET owned trip list/detail, booking
list/detail, manifests, occupancy, and active bus types. Trip creation/status,
fleet writes, routes/fares, and staff management remain `OPERATOR_ADMIN` only.

### Inactive operator semantics

INACTIVE suspends operator management and all new customer commerce: public
search, commerce trip detail, seat maps, holds, booking conversion, and new mock
payment confirmation reject or omit the operator. It does not cancel trips or
bookings, refund payments, or mutate inventory. Historical customer booking and
issued-ticket reads remain available. Reactivation changes no trip/inventory state.

Clarification: the mock-confirm endpoint checks inactive operator/association before
the existing CONFIRMED-payment branch, so repeat confirmation while suspended also
returns PAYMENT_WINDOW_CLOSED. Read the issued ticket through the ticket endpoint;
do not use confirmation as a historical read. Confirmed repeat calls after trip
departure remain idempotent when the operator and association are active.

## M15 local fixtures (no public API change)

Demo seeding is explicit-profile, create-only and transactional. Existing identity
collisions fail without granting roles or modifying records. Admin/staff initial
passwords are set only on first creation; account/membership/operator suspension,
fares, trips, inventory, holds and bookings are preserved. Occupied proposed slots
are skipped under the production bus scheduling lock. The old demo reset flag has
no destructive effect. Demo creates no CUSTOMER or SYSTEM_ADMIN account and no
booking/payment/ticket. See demo-data.md and final-demo.md for local preparation.

M15 also aligns JDBC views of JPA-written timestamps (operator booking/list,
manifest, admin/staff creation dates) with Hibernate's configured UTC Calendar
binding. Operator booking date-range parameters use the same binding. This fixes
cross-view offsets on a non-UTC JVM without rewriting existing records, changing
businessDate semantics or changing direct-JDBC hold expiry timestamps. Keep JVM
timezone consistent for an existing database; moving legacy data between JVM
timezones still needs explicit review of its timestamp conventions.


## M16A assisted bookings and collection

PHONE is a booking source; Zalo is only an external manual communication channel.
Sources: WEB, PHONE. Intended/actual methods: MOCK_ONLINE (WEB), PAY_ON_BOARD or
QR_TRANSFER (PHONE). No gateway, real banking QR, or Zalo send operation exists.

Operator list/detail append source and paymentMethod. Detail customer is nullable
for offline callers. Existing contact, items and payments contracts remain; payment
summaries append collectedByUserId (nullable) and referenceNote (nullable). No payment
rows and no tickets exist before successful collection. PENDING reserves BOOKED
inventory; CONFIRMED means successful payment and issued tickets, not boarding.

### Operator mutations (OPERATOR_ADMIN and active operator membership only)

- POST /api/v1/operator/bookings → 201, ApiResponse<BookingResponse>.
  Body: tripId, pickupLocationId, dropoffLocationId, tripSeatIds (1–5 unique positive
  IDs), contactName (required, ≤100), contactPhone (required, ≤20), contactEmail
  (optional valid email, ≤150), paymentMethod (PAY_ON_BOARD or QR_TRANSFER).
  Booking source is assigned PHONE; customer ownership cannot be supplied.
  Same journey fare, hold allocation and complete inventory validation as WEB.
- POST /api/v1/operator/bookings/{bookingId}/payments → 200,
  ApiResponse<PaymentConfirmation>. Body: method (must match booking's PHONE method),
  referenceNote (optional, ≤500). Records the full amount and current employee/time.
  One successful payment and one ticket per item; repeats return existing payment.
- POST /api/v1/operator/bookings/{bookingId}/payment-link → 200,
  {data:{path:"/pay/<opaque-token>"}}. QR_TRANSFER PHONE only. Relative path is resolved
  against the frontend origin. Reissuing revokes the previous link; raw token is
  returned once and never stored in the database.

Foreign trip/booking accesses use 404 BOOKING_NOT_FOUND. Staff mutation and system
admin operator-context requests use 403 ACCESS_DENIED. Inactive operator membership
rejects management; public confirmation uses 409 PAYMENT_WINDOW_CLOSED. Overlapping
or incomplete inventory uses 409 SEAT_NOT_AVAILABLE; incomplete booked inventory
uses 409 BOOKING_INVENTORY_INCONSISTENT. Invalid request fields use 400
VALIDATION_ERROR. Unsupported/mismatched method or terminal booking uses 409
BOOKING_NOT_PAYABLE. New payments require SCHEDULED or BOARDING; employee PHONE
collection during BOARDING is allowed after pickup time. This never performs check-in.

### Anonymous payment links

- GET /api/v1/public/payments/{token} → 200, ApiResponse<PublicPayment>.
- POST /api/v1/public/payments/{token}/mock-confirm → 200, the same minimal contract.
  No request body, account login, payer account, booking ID, or editable fields.
  QR_TRANSFER mock confirmation only. Existing paid outcome is idempotent.

PublicPayment fields: bookingCode, operator (name), journey (route name), pickup and
 dropoff ({name,time}), seats (seat codes), amount, method, status, mockPayment:true.
No contact, account, employee, database IDs or internal payment references are exposed.
Both responses use Cache-Control:no-store and Referrer-Policy:no-referrer. Invalid,
malformed or revoked tokens return 404 PAYMENT_LINK_NOT_FOUND with no private data.
Active paid links remain idempotent after departure; inactive operators close repeats
consistently with WEB. Unpaid public confirmation closes at pickup departure. No token
TTL or anonymous ticket download is introduced in M16A. The anonymous page directs
customers to the operator for ticket delivery; operator detail displays ticket QR.

WEB authenticated booking/hold/payment/ticket ownership and behavior remain intact.
V11 defaults historical bookings to WEB/MOCK_ONLINE and renames historical MOCK_QR
payment rows. PHONE never creates a fake customer account. See m16a-assisted-booking.md
for implementation, concurrency and deferred scope; m16a-verification.md for evidence.

## M16B.1 + M16B.2 crew and boarding

Operator ownership is derived from the active membership. OPERATOR_ADMIN performs
all mutations; OPERATOR_STAFF reads only. SYSTEM_ADMIN has no operator context.
Foreign IDs return 404; customer/staff writes return 403; missing/invalid credentials
return 401. All endpoints retain ApiResponse envelopes and the common error format.

- GET /api/v1/operator/employees -> Employee[] (ordered by code, ID).
- GET /api/v1/operator/employees/{id} -> Employee.
- POST /api/v1/operator/employees -> 201 Employee.
- PATCH /api/v1/operator/employees/{id} -> Employee.
  Body is the complete editable representation: employeeCode (1-50), fullName
  (1-100), phone (1-20), status ACTIVE/INACTIVE, nonempty capabilities containing
  DRIVER and/or ATTENDANT, licenceNumber (<=50), licenceClass (<=30),
  licenceExpiryDate (ISO date); all licence fields required for DRIVER.
  PATCH also requires current version. Employee returns id and version.
- GET /api/v1/operator/trips/{id}/crew -> {assignments, ready, warning}.
  Assignment: id, employeeId, duty, fullName, status, licenceExpiryDate.
- PUT /api/v1/operator/trips/{id}/crew -> same result.
  Body: {assignments:[{employeeId,duty}]}, complete desired active set (0-20).
  Repeated sets preserve existing assignment/history; removed entries are released.
- GET /api/v1/operator/trips/{id}/attendance -> AttendanceRow[].
  bookingItemId, bookingId, bookingCode, bookingAmount (full booking amount),
  seatCode, passengerName (item/contact fallback), phone, source, paymentMethod,
  paymentBlocked (boolean: any item in this booking has terminal NO_SHOW),
  paymentStatus, nullable ticketId/ticketCode/boardingStatus, pickupStopId,
  pickupName, dropoffName, pickupTime (UTC offset timestamp, planned/advisory).
  Includes PENDING/CONFIRMED/COMPLETED booking items, unlike the compatible legacy
  /passengers read. Unpaid rows have no ticket; explicit no-show displays NO_SHOW.
- POST /api/v1/operator/trips/{tripId}/booking-items/{itemId}/no-show
  Body: {stopId,reason?}; exact booked pickup, reason <=500. Operator admin only.
  Unpaid PENDING PHONE PAY_ON_BOARD items receive NO_SHOW without payment/ticket
  or commercial/inventory changes. Paid items use existing ticket no-show rules.
  Returns the attendance row including booking_item_id and nullable ticket_id.
  Duplicates preserve timestamps/history even after closure; history entity is
  BOOKING_ITEM for ticketless attendance. New payment for any booking containing
  NO_SHOW rejects BOOKING_ATTENDANCE_TERMINAL; already paid retries remain idempotent.
- POST /api/v1/operator/trips/{tripId}/tickets/{ticketId}/check-in
- POST /api/v1/operator/trips/{tripId}/tickets/{ticketId}/board
- POST /api/v1/operator/trips/{tripId}/tickets/{ticketId}/direct-board
- POST /api/v1/operator/trips/{tripId}/tickets/{ticketId}/no-show
  Body: {stopId,reason?}; stopId must be the ticket's booked pickup; reason <=500.
  Result is the committed ticket_boarding row: id, booking_item_id, ticket_id, status,
  checked_in_at/by, boarded_at/by, no_show_at/by, actual_boarding_stop_id,
  pickup_stop_id and version (snake_case fields). EXPECTED -> CHECKED_IN ->
  BOARDED; direct-board atomically records both transitions; explicit no-show
  accepts EXPECTED/CHECKED_IN. Repeating target state returns unchanged result.
- GET /api/v1/operator/trips/{id}/pickups -> [{stopId,name,stopOrder,closedAt}].
- POST /api/v1/operator/trips/{tripId}/stops/{stopId}/close-pickup
  Body: {stopId,reason?}, same stop as path. Returns pickups list. Explicitly
  resolve every valid passenger first; no implicit no-show. Repeat is idempotent.
- GET /api/v1/operator/trips/{id}/history -> ordered operational_history rows:
  id, trip_id, entity_type, entity_id, action, actor_id, occurred_at, reason.

Check-in/boarding and ticket commands require eligible paid issued tickets.
The booking-item command additionally supports unpaid no-show. BOARDING permits
open pickups; DEPARTED permits only open intermediate pickups. Closed pickup and
terminal trip reject new attendance. New tickets receive EXPECTED with payment;
historical tickets are not backfilled and missing state displays Chưa ghi nhận.
No-show never changes payment/booking/inventory. Lifecycle PATCH remains explicit
forward-only and idempotent, but BOARDING requires valid ready crew, DEPARTED
requires closed origin pickup, and COMPLETED requires all pickup stops closed and
all valid passengers resolved. No automatic attendance based on planned times.

The existing POST /operator/bookings/{id}/payments also allows authenticated PHONE
collection during DEPARTED at open intermediate pickups; it never records check-in.
WEB and anonymous payment timing/ownership remains unchanged.

Conflict codes (409): STALE_EMPLOYEE, EMPLOYEE_CODE_EXISTS,
EMPLOYEE_HAS_ASSIGNMENTS, EMPLOYEE_NOT_ELIGIBLE, DRIVER_PROFILE_REQUIRED,
DRIVER_LICENCE_EXPIRED, DUPLICATE_CREW, CREW_WINDOW_CLOSED,
CREW_SCHEDULE_CONFLICT, CREW_NOT_READY, TICKET_NOT_ELIGIBLE,
WRONG_PICKUP_STOP, BOARDING_WINDOW_CLOSED, PICKUP_CLOSED, CHECK_IN_REQUIRED,
INVALID_BOARDING_TRANSITION, PICKUP_UNRESOLVED, RESERVATION_NOT_ELIGIBLE,
BOOKING_ATTENDANCE_TERMINAL. Not-found codes (404):
EMPLOYEE_NOT_FOUND, TRIP_NOT_FOUND, TICKET_NOT_FOUND, PICKUP_STOP_NOT_FOUND,
BOOKING_ITEM_NOT_FOUND.
Malformed/invalid body fields use existing 400 validation errors.

V12 adds operational tables without changing V1-V11 or backfilling attendance.
V13 anchors attendance to booking items, backfills only references on existing
attendance, and makes ticket_id nullable. Ticketless records must be NO_SHOW.
See m16b-crew-boarding.md and m16b-verification.md for rules and evidence.

## M17 cancellation and reservation recovery

Explicit commands (ApiResponse<Recovery>):
- POST /api/v1/bookings/{id}/cancel — CUSTOMER, own WEB booking only.
- POST /api/v1/operator/bookings/{id}/cancel — OPERATOR_ADMIN, owned booking.
- GET /api/v1/bookings/{id}/recovery — owning customer.
- GET /api/v1/operator/bookings/{id}/recovery — owning admin/read-only staff.

Cancellation body: {"note":"optional, maximum 500 characters"}; {} is valid.
Reason is derived by the server: CUSTOMER_CANCELLED or OPERATOR_CANCELLED.
PAYMENT_TIMEOUT is reserved for the scheduled expiry service and never refunds.

Recovery fields: bookingId, status, eligible, ineligibleReason, paid,
paymentDueAt, customerCutoffAt, cancelledAt, cancelledBy, reasonCode, note,
refunds [{id,paymentId,amount,refundedAt,refundedBy,reasonCode,note}],
tickets [{id,ticketCode,status,voidedAt}],
history [{fromStatus,toStatus,actorId,reasonCode,note,changedAt}].
Timestamps use the existing UTC offset representation. Booking detail adds recovery;
creation returns recovery:null. Operator item.ticket adds status VALID/VOID.
Customer ticket reads support historical paid cancellation: paymentStatus REFUNDED,
ticket status VOID, voidedAt, and qrData:null. Unpaid cancellation has no ticket.

Whole PENDING/CONFIRMED bookings only, terminal CANCELLED. Repeat cancellation
returns committed metadata without duplicate release/history/refund. Customer
cutoff is six hours before selected pickup planned departure, inclusive. Both
commands require SCHEDULED/BOARDING, open pickup, and no CHECKED_IN/BOARDED/NO_SHOW
item. Admin does not use the customer cutoff. Inactive operator permits only
eligible unpaid owning WEB customer recovery; suspended paid/PHONE support is deferred.

409 codes: BOOKING_NOT_CANCELLABLE, CANCELLATION_WINDOW_CLOSED,
CUSTOMER_CANCELLATION_CUTOFF, CANCELLATION_ATTENDANCE_CONFLICT, PICKUP_CLOSED,
CANCELLATION_OPERATOR_SUSPENDED, CANCELLATION_STATE_INCONSISTENT,
BOOKING_INVENTORY_INCONSISTENT. Foreign ownership uses 404 BOOKING_NOT_FOUND;
wrong roles use 403; anonymous callers use 401; invalid note/body uses 400.

New WEB deadlines default to 15 minutes, PHONE QR_TRANSFER to 30 minutes, capped
at selected pickup departure. PAY_ON_BOARD and legacy null deadlines do not expire.
Pending payment rejects at/after deadline even before the scheduled job runs.
Expiry uses the same atomic cancellation guards with PAYMENT_TIMEOUT and no refund.
Cancelled public links expose only their existing minimal context/status and reject
confirmation with BOOKING_NOT_PAYABLE; invalid/rotated hashes remain 404. Ticket
operations explicitly reject VOID. Refunds are simulated full refunds only:
"Hoàn tiền mô phỏng" never means a bank transfer.

See [M17 design](m17-cancellation-recovery.md) and
[verification](m17-verification.md) for locking, migration and evidence.

## M18A operator management reports

Admin only, active trusted operator context; staff/customer/public/system-admin
denied. No operatorId parameter. Inclusive ISO fromDate/toDate required, 1–366
Asia/Ho_Chi_Minh dates → half-open UTC window. Optional routeId (global route ID),
tripId, bookingSource WEB/PHONE, paymentMethod MOCK_ONLINE/QR_TRANSFER/PAY_ON_BOARD.
Foreign IDs contribute no data. Invalid ranges, enums, IDs or pagination use 400;
anonymous 401, wrong roles/inactive context 403.

- GET /api/v1/operator/reports/summary → ApiResponse<Summary>.
  data={metadata,collections,bookings,cancellations,attendance,load,operations}.
  collections={totals,byPaymentMethod,trend:[{date,money}]}.
  bookings={bookingsCreated,byStatus,bySource,cancellations,paymentTimeouts,
  currentUnpaidCount,ticketsIssued,validTickets,voidTickets}.
  cancellations={totalCancellations,byReason,refundedCancellations,
  unpaidCancellations,amountRefunded}.
  operations={trips,boardingTrips,runningTrips,upcomingWithoutDriver,
  openPickups,incompleteTrips}.
- GET /api/v1/operator/reports/trips → ApiResponse<Table<TripPerformance>>.
  Table={metadata,data,pagination:{page,size,totalElements,totalPages}}.
  Row={tripId,routeId,route,plannedDeparture,status,bus,bookings,validTickets,
  attendance,money,load,wholeTripAvailableSeats}.
- GET /api/v1/operator/reports/routes → ApiResponse<Table<RoutePerformance>>.
  Same table envelope. Row={routeId,route,tripCount,operatedTripCount,bookings,
  validTickets,attendance,money,load}. Zero-activity owned routes included.
  Both tables accept page=0..100000 (default 0), size=1..100 (default 20).

Metadata={fromDate,toDate,timezone,asOf,dateBasis,routeId,tripId,bookingSource,
paymentMethod}. Money={grossMockCollections,mockRefunds,netMockCollections,
paidPaymentCount,refundedPaymentCount}. Money uses decimal values; all are simulated.
Load={expectedCells,actualCells,missingCells,sellableCells,reservedCells,paidCells,
heldCells,complete,reservedSegmentLoad,paidSegmentLoad}. Ratios are fractions (0..1);
incomplete load or zero capacity → null. Whole-trip availability is null if incomplete.
Summary/route load counts and completeness aggregate only non-CANCELLED trips.
An all-cancelled cohort returns zero contributing load counts, complete=true and
null load ratios (not applicable). CANCELLED trip rows remain in table counts and
retain inventory counts/completeness, but reservedSegmentLoad/paidSegmentLoad are
always null. Incomplete cancelled trips do not affect non-cancelled aggregate load.
Money, refunds, cancellations, booking-created and ticket-issued cohorts retain
their existing date/status semantics. Response field shapes are unchanged.
Attendance={eligibleResolvedTickets,boardedTickets,noShowTickets,checkedInNotBoarded,
unresolvedAttendance,ticketlessNoShows,boardingRate,noShowRate}. Both rates are null
when no resolved eligible ticket exists; ticketless no-show is separate.

Summary collections use paid_at/refunded_at separately, bookings use created_at,
tickets use ticket.created_at, cancellation flow uses cancelled_at. Attendance,
load and operations use planned origin departure. Trip/route money is *lifetime*
attributed to the departure cohort, not transaction flow during selected dates.
All date bases are explicit in metadata/UI. Refund today on an older payment does
not change historical gross. Exact definitions/exclusions/incomplete behavior:
[M18A reporting design](m18a-management-reports.md).

## M18B — Operator customer management

Admin-only read endpoints, active operator membership required; SYSTEM_ADMIN
(including mixed roles), staff, customer and anonymous callers are denied.

- GET /api/v1/operator/customers?q=&customerType=&sort=&page=0&size=20
  returns ApiResponse<PagedResponse<Summary>>.
- GET /api/v1/operator/customers/{customerKey}?page=0&size=20
  returns ApiResponse<{summary,bookings:PagedResponse<BookingHistory>}>.

Keys are ACCOUNT:{customer_id} or CONTACT:{booking_id}; URL-encode them. Account
grouping includes only this operator's bookings; each accountless booking is a
separate OFFLINE_CONTACT even with repeated phone/email. No user profile fields.
Summary: customerKey/customerType/displayName/phone/email/latestBookingAt/
latestJourney, total/confirmed/cancelled/web/phone booking counts, boardedJourneys,
attendance {boarded,noShow,checkedIn,unrecorded}, money
{grossMockPaid,mockRefunds,netMockPaid}. Contact display uses latest owned snapshot.

History includes booking ID/code/source/contact snapshots, routeName/pickup/dropoff/
seats, bookingStatus/paymentMethod/paymentStatus, validTickets/voidTickets,
attendance/money, cancellationReason/cancelledAt/createdAt, payments
[{id,method,status,amount,paidAt,createdAt}], refunds [{id,amount,reason,refundedAt}].
Timestamps carry UTC offset. All transactions are simulated. Boarding counts refer
to booked items and never verify that the account holder/contact travelled.

Search literal case-normalized substring name/phone/email/code, max 150 chars;
historical matches preserve complete owned aggregates. Type ACCOUNT/OFFLINE_CONTACT;
sort LATEST (default desc), NAME (asc), BOOKINGS/MOCK_PAID (desc), stable key ties.
Page 0..100000; size 1..100, default 20. Invalid filters/keys 400; owned contact
absence/foreign keys 404. No writes or operatorId filter. Details:
[M18B design](m18b-customer-management.md).

## M18C — Fleet maintenance and readiness

Admin-only with active trusted operator context; SYSTEM_ADMIN including mixed roles,
staff, customer and public denied. No operatorId selector. Missing/foreign owned IDs
404; wrong roles/context 403; anonymous 401. All timestamps have UTC offset, date
filters use inclusive Asia/Ho_Chi_Minh business dates.

Existing GET /operator/buses and /operator/buses/{id} append readiness:
{operationalReady,status,maintenanceState,activeMaintenanceId,nextMaintenanceDate,
nextMaintenanceType,nextDueDate,nextAssignedTrip,warnings}. Existing list envelope
is unchanged. BusStatus remains AVAILABLE/MAINTENANCE/INACTIVE.

New endpoints under /api/v1/operator:

- GET /maintenance?busId=&status=&maintenanceType=&fromDate=&toDate=&page=0&size=20
- GET /buses/{busId}/maintenance (same filters except bus selection)
- POST /buses/{busId}/maintenance -> 201 ApiResponse<Maintenance>.
  Body: maintenanceType, title (required <=150), note (<=1000), scheduledStart and
  scheduledEnd (required offset timestamps; start >=now, end >start).
- POST /maintenance/{id}/start -> ApiResponse<Maintenance>; no body.
- POST /maintenance/{id}/complete -> ApiResponse<Maintenance>.
  Body: odometerKm?, nextDueDate?, nextDueOdometerKm?, note? (<=1000); {} valid.
- POST /maintenance/{id}/cancel -> ApiResponse<Maintenance>.
  Body: reason? (<=500); {} valid.
- GET /buses/{busId}/trips?page=0&size=20 -> ApiResponse<PagedResponse<AssignedTrip>>.
- GET /buses/{busId}/history -> ApiResponse<StatusHistory[]> (latest 100).
- GET /fleet/readiness -> ApiResponse<FleetWarnings>.

Maintenance list envelope ApiResponse<PagedResponse<Maintenance>>, page 0–100000,
size 1–100. Types: PERIODIC_SERVICE/OIL_CHANGE/TIRE/BRAKE/ELECTRICAL/ENGINE/
AIR_CONDITIONING/INSPECTION/REPAIR/OTHER. Status SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED.
Maintenance fields: id,busId,licensePlate,maintenanceType,status,title,note,
scheduledStart,scheduledEnd,startedAt,completedAt,cancelledAt,odometerKm,nextDueDate,
nextDueOdometerKm,completionNote,cancellationReason,createdBy,completedBy,cancelledBy,
createdAt,updatedAt. AssignedTrip={id,route,departureTime,estimatedArrivalTime,status}.
StatusHistory={id,previousStatus,newStatus,reasonCode,maintenanceId,changedBy,changedAt}.
FleetWarnings={availableBuses,maintenanceBuses,inactiveBuses,maintenanceDueSoon,
overdueMaintenance,upcomingNotReady:AssignedTrip[]} (up to 20 upcoming trips).

Lifecycle SCHEDULED -> IN_PROGRESS or CANCELLED; IN_PROGRESS -> COMPLETED.
Terminal commands cannot repeat/reopen. No DELETE/PATCH maintenance status.
Completion values nonnegative; next due odometer >recorded value when both provided;
next due date >=Vietnam today. Date overdue is based only on explicit latest completed
metadata per type, never unknown current odometer. Bus restoration preserves prior
and intentionally changed INACTIVE status; originally MAINTENANCE stays unavailable.

409: MAINTENANCE_TRIP_CONFLICT (details.trips includes only safe owned trip data),
BUS_MAINTENANCE_CONFLICT, INVALID_MAINTENANCE_TRANSITION, MAINTENANCE_WINDOW_ENDED,
FLEET_PLAN_CHANGED. Existing BUS_NOT_AVAILABLE/BUS_SCHEDULE_CONFLICT/CREW_NOT_READY
still apply. 404 MAINTENANCE_NOT_FOUND/BUS_NOT_FOUND. Invalid fields/ranges/enums 400.
Intervals use origin departure/estimated arrival with strict half-open overlap;
back-to-back allowed. Cancelled/completed records do not conflict. IN_PROGRESS blocks
assignment irrespective of planned end. Existing immutable trip assignment is unchanged.
V16 adds maintenance and status audit tables/indexes. See m18c-fleet-maintenance.md.

## M20 booking modification

Customer /api/v1/bookings/{id} and owned operator /api/v1/operator/bookings/{id} expose modification-eligibility, alternative-trips, modification-seat-availability, modification-quotes, modifications, and modifications/{mid}/confirm or /cancel. Quotes and creation accept {type,targetTripId,items:[{bookingItemId,targetSeatId}]}. POST creates target holds; confirmation simulates cash adjustments and atomically switches the existing booking. Staff may read eligibility/history; mutation requires customer ownership or Operator Admin. See [M20 contract, lifecycle and money semantics](m20-booking-modification.md).

## M21 partial cancellation

Shared customer /api/v1/bookings/{id} and operator /api/v1/operator/bookings/{id}: GET partial-cancellation-eligibility; POST partial-cancellation-quote; POST partial-cancellations; GET partial-cancellations; GET partial-cancellations/{cancellationId}. POST body is {bookingItemIds:[...]}. Quotes are transient; execution revalidates locked state and returns immutable completion snapshots. Staff may read only. Selection must leave an active item; 409 FULL_CANCELLATION_REQUIRED directs all-item selection to existing full cancellation. ITEM_CANCELLED, ATTENDANCE_CONFLICT, MODIFICATION_ACTIVE, SOURCE_TRIP_CLOSED, CUSTOMER_CUTOFF, PAYMENT_EXPIRED and PICKUP_CLOSED are authoritative domain reasons. Foreign booking/item/history IDs are 404. See [M21 contract and accounting](m21-partial-cancellation.md).
