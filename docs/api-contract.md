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
Các API /operator/** yêu cầu OPERATOR_STAFF hoặc OPERATOR_ADMIN.

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
POST /seat-holds
Role:
CUSTOMER
Request
{
  "tripId": 101,
  "pickupTripStopId": 1002,
  "dropoffTripStopId": 1006,
  "tripSeatIds": [
    501,
    503
  ]
}

22. Seat Hold Processing
Backend:
1. Validate trip.
2. Validate pickup/dropoff.
3. Determine required segments.
4. Lock inventory rows.
5. Check every seat on every segment.
6. Create hold.
7. Set HELD.
Tất cả trong transaction.

23. Seat Hold Response
{
  "data": {
    "holdToken": "29fc9351-f941-4dd9-a1d6-abc123",
    "tripId": 101,

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

    "expiresAt": "2026-09-18T21:40:00+07:00",
    "remainingSeconds": 600,

    "totalAmount": 1300000
  }
}

24. Seat Hold Errors
SEAT_NOT_AVAILABLE
INVALID_TRIP_STOP
INVALID_ROUTE_DIRECTION
TRIP_ALREADY_DEPARTED
TRIP_NOT_BOOKABLE
TOO_MANY_SEATS
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
Nên để trong configuration thay vì hard-code service.

26. Get Current Hold
GET /seat-holds/{holdToken}
Authenticated.
Chỉ chủ hold mới xem được.
Response:
{
  "data": {
    "holdToken": "...",
    "status": "ACTIVE",
    "expiresAt": "...",
    "remainingSeconds": 450,
    "seatCodes": [
      "A01",
      "A03"
    ],
    "totalAmount": 1300000
  }
}
Possible status:
ACTIVE
EXPIRED
CONSUMED
CANCELLED

27. Release Hold
DELETE /seat-holds/{holdToken}
Role CUSTOMER.
Response:
204 No Content
Backend release HELD inventory thuộc hold.

28. Create Booking
POST /bookings
CUSTOMER.
Request
{
  "holdToken": "29fc9351-f941-4dd9-a1d6-abc123",

  "contact": {
    "fullName": "Nguyen Van A",
    "phone": "0901234567",
    "email": "user@example.com"
  },

  "passengers": [
    {
      "tripSeatId": 501,
      "fullName": "Nguyen Van A"
    },
    {
      "tripSeatId": 503,
      "fullName": "Nguyen Van B"
    }
  ],

  "paymentMethod": "MOCK_QR"
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
8. Create Payment.
Không lấy:
totalAmount
price
từ frontend làm giá trị tin cậy.

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

    "payment": {
      "id": 801,
      "method": "MOCK_QR",
      "status": "PENDING"
    }
  }
}

31. Mock Payment
POST /bookings/{bookingId}/payments/mock-confirm
CUSTOMER.
Chỉ owner booking.
Request có thể để trống:
{}
Backend:
Payment PENDING → PAID

Booking PENDING → CONFIRMED

Seat inventories:
HELD → BOOKED

hold → CONSUMED
Tất cả trong cùng transaction.

32. Mock Payment Response
{
  "data": {
    "bookingCode": "BG2609180001",
    "bookingStatus": "CONFIRMED",
    "paymentStatus": "PAID"
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
GET /bookings/me
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
GET /bookings/{bookingId}
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
      "method": "MOCK_QR",
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
GET /bookings/{bookingId}/ticket
Owner.
Response:
{
  "data": {
    "bookingCode": "BG2609180001",

    "operator": "GiangBus",

    "passenger": "Nguyen Van A",

    "route": {
      "pickup": "Đắk Lắk",
      "dropoff": "Hà Nội"
    },

    "departureTime": "2026-09-25T18:30:00+07:00",

    "seatCodes": [
      "A01"
    ],

    "paymentStatus": "PAID",

    "qrValue": "BG2609180001"
  }
}
Frontend có thể render QR.
PDF ticket là optional.

40. Operator Dashboard
GET /operator/dashboard
Roles:
OPERATOR_ADMIN
Response:
{
  "data": {
    "tripsToday": 12,
    "bookingsToday": 45,
    "ticketsSoldToday": 57,
    "revenueToday": 24500000,
    "occupancyRate": 72.4,

    "recentBookings": [
    ]
  }
}
Dữ liệu phải scope theo operator của current user.

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
routeId (global Route ID)
busId
status
page
size

M3/M4 implementation note: các endpoint này yêu cầu OPERATOR_ADMIN và luôn giới hạn
dữ liệu theo operator_staff của user đã xác thực. `date` là một ngày UTC.

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

61. Staff Confirm Booking
POST /operator/bookings/{bookingId}/confirm
Dùng cho CASH/PENDING booking.
Backend:
PENDING → CONFIRMED
và seat:
HELD → BOOKED
nếu booking flow yêu cầu.

62. Confirm Cash Payment
POST /operator/bookings/{bookingId}/payment/confirm
{
  "note": "Paid at ticket counter"
}
Payment:
PENDING → PAID

63. Staff Cancel Booking
POST /operator/bookings/{bookingId}/cancel
{
  "reason": "Trip cancelled by operator"
}
Staff cancellation không nhất thiết áp dụng rule 6 giờ như CUSTOMER.
Phải release seat inventory.

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

67. Reports – Revenue
GET /operator/reports/revenue
Parameters:
from
to
groupBy
groupBy:
DAY
MONTH
ROUTE
Example:
GET /api/v1/operator/reports/revenue?from=2026-09-01&to=2026-09-30&groupBy=DAY

68. Revenue Response
{
  "data": {
    "totalRevenue": 125000000,

    "series": [
      {
        "label": "2026-09-01",
        "revenue": 5500000
      },
      {
        "label": "2026-09-02",
        "revenue": 7200000
      }
    ]
  }
}

69. Occupancy Report
GET /operator/reports/occupancy
Parameters:
from
to
routeId
Response:
{
  "data": [
    {
      "tripId": 101,
      "route": "Đắk Lắk - Hà Nội",
      "occupancyRate": 72.4
    }
  ]
}

70. Trip Cancellation
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
POST /seat-holds
DELETE /seat-holds/{token}

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
POST /seat-holds
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
