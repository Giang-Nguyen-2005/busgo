BUSGO – PAGE FLOW & UI SCREEN SPECIFICATION v1
1. Mục tiêu
Tài liệu này xác định:
    • Các màn hình của Customer.
    • Các màn hình của Operator Staff/Admin.
    • Luồng điều hướng giữa các trang.
    • Component chính trên từng trang.
    • Trạng thái UI quan trọng.
    • API mà từng màn hình sử dụng.
Nguyên tắc:
UI hiển thị
Backend quyết định nghiệp vụ
Frontend không tự xác định:
    • ghế còn hay hết;
    • giá vé;
    • quyền truy cập;
    • booking hợp lệ hay không;
    • route hợp lệ hay không.

2. Sitemap tổng thể
Public / Customer
/
├── /login
├── /register
├── /search
├── /trips/:tripId
├── /booking
├── /payment
├── /booking-success
│
├── /profile
├── /my-bookings
└── /my-bookings/:bookingId
Operator
/operator
├── /dashboard
├── /bus-types
├── /buses
├── /routes
├── /routes/:id
├── /trips
├── /trips/:id
├── /bookings
├── /bookings/:id
├── /customers
├── /staff
└── /reports

3. Main Customer Flow
HOME
↓
Search trip
↓
SEARCH RESULT
↓
Select trip
↓
TRIP DETAIL
↓
Choose pickup / dropoff
↓
Choose seat
↓
Hold seat
↓
BOOKING FORM
↓
Create booking
↓
PAYMENT
↓
Payment success
↓
TICKET / BOOKING SUCCESS

4. Homepage /
Mục tiêu
Cho user tìm chuyến nhanh nhất.
Layout
Header
------------------------------------------------
Logo      Vé của tôi      Đăng nhập

Hero section

    Bạn muốn đi đâu?

    [ Điểm đón        ]
            ⇄
    [ Điểm đến        ]

    [ Ngày đi         ]

        [ TÌM CHUYẾN ]

Popular routes

Why choose BusGo

Footer

5. Search Form
Component:
TripSearchForm
Fields:
Pickup
Dropoff
Departure Date
Không có:
Nhà xe
trong search ban đầu.
Nhà xe chỉ xuất hiện dưới dạng filter ở trang kết quả.

6. Location Autocomplete
Khi user nhập:
Đà
frontend gọi:
GET /api/v1/locations?q=Đà
Hiển thị:
Đà Nẵng
Đà Lạt
Component:
LocationAutocomplete
Phải lưu:
locationId
chứ không chỉ lưu text.

7. Search Validation
Frontend kiểm tra:
Pickup required
Dropoff required
Date required
Pickup != Dropoff
Sau đó navigate:
/search?pickup=10&dropoff=20&date=2026-09-25

8. Search Result /search
Layout
Header

Đắk Lắk → Hà Nội
25/09/2026
[Thay đổi tìm kiếm]

-------------------------------------------------

Filters             Results

Nhà xe              Trip Card
Loại xe             Trip Card
Giờ đi              Trip Card
Giá                 Trip Card

                    Pagination

9. API
GET /api/v1/trips/search
Parameters lấy từ URL.

10. Trip Card
Ví dụ:
┌─────────────────────────────────────────────┐
│ GiangBus                                    │
│ Limousine 22 phòng                         │
│                                             │
│ 18:30                      10:00            │
│ Đắk Lắk  ───────────────→ Hà Nội           │
│                                             │
│ 15h30                                      │
│                                             │
│ Còn 5 ghế               650.000đ           │
│                                             │
│                    [Chọn chuyến]            │
└─────────────────────────────────────────────┘
Component:
TripCard

11. Search Filters
Sidebar desktop.
Mobile:
[ Bộ lọc ]
mở drawer/modal.
Filters:
Nhà xe
Loại xe
Khoảng giá
Giờ khởi hành

12. Search Sorting
Dropdown:
Sắp xếp

- Giờ đi sớm nhất
- Giờ đi muộn nhất
- Giá thấp nhất
- Giá cao nhất

13. Search States
Phải thiết kế đủ:
Loading
Success
No result
API error
No result:
Không tìm thấy chuyến phù hợp

Hãy thử:
- đổi ngày đi;
- chọn điểm đón khác;
- chọn điểm đến khác.

14. Trip Detail /trips/:tripId
Đây là màn hình quan trọng nhất bên customer.
Layout
Trip Summary
-----------------------------------
GiangBus
Limousine 22 phòng

Đắk Lắk → Hà Nội
25/09/2026

18:30 → 10:00

-----------------------------------

1. Điểm đón
[ dropdown ]

2. Điểm trả
[ dropdown ]

3. Chọn ghế

Seat map

4. Summary

Ghế: A01, A03
Giá: 1.300.000đ

[Tiếp tục]

15. Trip Detail API
Thông tin chuyến:
GET /api/v1/trips/{tripId}
Khi pickup/dropoff đã chọn:
GET /api/v1/trips/{tripId}/seats

16. Pickup Selection
Dropdown chỉ hiển thị TripStop:
allowPickup = true
Ví dụ:
Bến xe Buôn Ma Thuột
Gia Lai
Đà Nẵng

17. Dropoff Selection
Sau khi pickup chọn xong, dropoff chỉ hiển thị:
allowDropoff = true
AND
dropoff.stopOrder > pickup.stopOrder
Frontend có thể filter để UX tốt.
Nhưng backend vẫn validate lại.

18. Seat Map
Component:
SeatMap
Ví dụ:
        ĐẦU XE

       [Tài xế]

A01    A02       B01    B02

A03    A04       B03    B04
State UI:
Available
Selected
Unavailable
Blocked
Khuyến nghị legend:
□ Trống
■ Đang chọn
× Đã có người đặt
▨ Không bán
Không cần màu quá phức tạp nếu chưa thiết kế visual system.

19. Seat Select Rules
User click AVAILABLE:
AVAILABLE → selected locally
Chưa gọi hold ngay nếu muốn giảm request.
Khi user nhấn:
Tiếp tục
frontend gọi:
POST /api/v1/seat-holds

20. Seat Hold Failure
Ví dụ A01 vừa bị user khác giữ.
Backend trả:
SEAT_NOT_AVAILABLE
UI:
Ghế A01 vừa được khách khác chọn.

Sơ đồ ghế đã được cập nhật.
Sau đó refresh seat map.

21. Seat Hold Success
Frontend nhận:
holdToken
expiresAt
totalAmount
Navigate:
/booking

22. Booking Page /booking
Protected route.
Nếu chưa login:
redirect /login
sau login phải quay lại booking.

23. Booking Layout
Thông tin chuyến

GiangBus
Đắk Lắk → Hà Nội
25/09/2026

Ghế
A01
A03

--------------------------------

Thông tin liên hệ

Họ tên
[                   ]

Số điện thoại
[                   ]

Email
[                   ]

--------------------------------

Thông tin hành khách

A01
Tên hành khách
[                   ]

A03
Tên hành khách
[                   ]

--------------------------------

Thanh toán

( ) QR
( ) Thanh toán khi lên xe

--------------------------------

Tổng cộng
1.300.000đ

[ĐẶT VÉ]

24. Hold Countdown
Trang booking hiển thị:
Giữ ghế trong:
09:24
Component:
HoldCountdown
Không tự giả định thời gian.
Dùng:
expiresAt
từ server.

25. Hold Expired
Nếu hết thời gian:
Ghế đã hết thời gian giữ.
Button:
[Chọn lại ghế]
Redirect về Trip Detail.

26. Create Booking
API:
POST /api/v1/bookings
Sau success:
    • MOCK_QR → /payment
    • CASH → có thể chuyển thẳng booking success.

27. Payment Page /payment
Chỉ dùng cho:
MOCK_QR
Layout:
Thanh toán đơn hàng

Mã đặt vé: BG001
Tổng tiền: 1.300.000đ

      [ MOCK QR ]

Quét mã để thanh toán

[Giả lập thanh toán thành công]
Button demo:
[ Xác nhận thanh toán ]
gọi:
POST /bookings/{id}/payments/mock-confirm

28. Payment Loading
Khi confirm:
Đang xử lý...
Disable button để tránh spam request.

29. Payment Success
Navigate:
/booking-success

30. Booking Success /booking-success
Layout:
✓ Đặt vé thành công

Mã vé
BG2609180001

GiangBus

Đắk Lắk
18:30
      ↓
Hà Nội
10:00

Ghế
A01, A03

Tổng tiền
1.300.000đ

[ QR CODE ]

[ Xem vé ]
[ Vé của tôi ]

31. Login /login
Simple layout.
Email
Password

[Đăng nhập]

Chưa có tài khoản?
Đăng ký
API:
POST /auth/login

32. Register /register
Họ tên
Email
Số điện thoại
Password
Confirm password
API:
POST /auth/register

33. Profile /profile
Protected.
Sidebar:
Thông tin cá nhân
Vé của tôi
Đổi mật khẩu
Đăng xuất

34. Profile Details
API:
GET /users/me
PATCH /users/me

35. My Bookings /my-bookings
Tabs:
Sắp đi
Đã hoàn thành
Đã hủy
Booking Card:
BG001

Đắk Lắk → Hà Nội
25/09/2026 - 18:30

Ghế A01

CONFIRMED
PAID

[Xem chi tiết]

36. Booking Detail /my-bookings/:id
Hiển thị:
Booking code
Trip
Operator
Pickup
Dropoff
Passenger
Seats
Payment
Status
QR Ticket
Nếu booking được hủy:
[Hủy vé]
Button chỉ hiển thị nếu frontend thấy còn hợp lý.
Backend vẫn quyết định cuối cùng.

37. Cancel Confirmation
Modal:
Bạn có chắc muốn hủy vé?

Việc hủy vé phải tuân theo
chính sách trước giờ khởi hành.

[Không]
[Hủy vé]

38. Operator Layout
Admin dùng layout riêng.
┌─────────────────────────────────────────┐
│ Topbar                                  │
├───────────────┬─────────────────────────┤
│ Sidebar       │                         │
│               │ Main Content            │
│ Dashboard     │                         │
│ Chuyến xe     │                         │
│ Tuyến         │                         │
│ Xe            │                         │
│ Booking       │                         │
│ Khách hàng    │                         │
│ Nhân viên     │                         │
│ Báo cáo       │                         │
└───────────────┴─────────────────────────┘

39. Operator Dashboard /operator/dashboard
Cards:
Chuyến hôm nay
12

Booking hôm nay
45

Vé đã bán
57

Doanh thu
24.500.000đ

40. Dashboard Chart
Doanh thu 7 ngày
Chart dạng:
Line chart
Không cần dashboard quá phức tạp.

41. Recent Bookings
Table:
Code
Customer
Trip
Seats
Amount
Status
Time
API:
GET /operator/dashboard

42. Bus Types /operator/bus-types
Table:
Tên loại xe
Số ghế
Trạng thái
Action
Button:
+ Thêm loại xe

43. Bus Type Editor
Có:
Tên
Mô tả
và visual seat editor.
Ví dụ:
A01 A02
A03 A04
A05 A06
Admin có thể:
Add seat
Remove seat
Change seat code
Move row/column
V1 có thể đơn giản hóa bằng form table thay vì drag & drop.

44. Buses /operator/buses
Table:
Biển số
Loại xe
Trạng thái
Action
Filters:
Search biển số
Loại xe
Status

45. Bus Create/Edit
Fields:
License Plate
Bus Type
Status
Không chỉnh seat map trực tiếp ở Bus.
Seat layout thuộc BusType.

46. Routes /operator/routes
Table:
Tên tuyến
Điểm đầu
Điểm cuối
Số điểm dừng
Status
Action

47. Route Create
Flow:
Thông tin tuyến
↓
Danh sách điểm dừng
↓
Giá vé

48. Route Basic Info
Route Name
Estimated Distance
Estimated Duration

49. Route Stop Editor
Table editable:
Order	Location	Pickup	Dropoff	Offset
1	Đắk Lắk	✓		0
2	Gia Lai	✓	✓	180
3	Đà Nẵng	✓	✓	480
4	Hà Nội		✓	930
Buttons:
+ Add Stop
↑ Move
↓ Move
Remove

50. Route Fare Editor
Matrix hoặc table.
V1 nên dùng table:
From        To           Price

Đắk Lắk    Đà Nẵng      250.000
Đắk Lắk    Hà Nội       700.000
Đà Nẵng    Hà Nội       500.000
Button:
+ Add Fare

51. Trips /operator/trips
Table:
Trip ID
Route
Bus
Departure
Arrival
Bookings
Status
Action
Filters:
Date
Route
Bus
Status
Button:
+ Tạo chuyến

52. Create Trip
Form:
Route
Bus
Departure date
Departure time
Sau khi chọn:
Route:
Đắk Lắk → Hà Nội

Bus:
51B-12345
Limousine 22
Backend tự sinh toàn bộ:
TripStops
Segments
Seats
Inventory
Frontend không cần xử lý.

53. Trip Detail /operator/trips/:id
Tabs:
Overview
Seats
Passengers
Bookings

54. Trip Overview
Hiển thị:
Route
Bus
Departure
Estimated Arrival
Status

Stops timeline
Timeline:
18:30 Đắk Lắk
   │
21:00 Gia Lai
   │
03:00 Đà Nẵng
   │
10:00 Hà Nội

55. Operator Seat View
Admin xem seat map.
Click A01:
A01

Đắk Lắk → Gia Lai
BOOKED - Nguyễn A

Gia Lai → Đà Nẵng
AVAILABLE

Đà Nẵng → Hà Nội
BOOKED - Nguyễn B
Đây là một trong những màn hình demo đẹp nhất của project.

56. Block Seat UI
Admin chọn:
Seat A03
From: Đắk Lắk
To: Đà Nẵng
Reason
Button:
[Block seat]

57. Passenger Manifest
Table:
Seat
Passenger
Phone
Pickup
Dropoff
Booking
Payment
Có search.

58. Operator Bookings /operator/bookings
Table:
Booking Code
Customer
Trip
Seats
Total
Payment
Status
Created
Filters:
Search
Date
Trip
Booking status
Payment status

59. Operator Booking Detail
Hiển thị toàn bộ:
Customer
Trip
Seats
Pickup
Dropoff
Amount
Payment
Booking history
Actions:
Confirm
Cancel
Confirm payment
tùy trạng thái.

60. Booking History Component
Timeline:
18:00 Created
PENDING

18:03 Payment confirmed
CONFIRMED

...

61. Customers /operator/customers
Table:
Customer
Email
Phone
Bookings
Total spent
Last booking
Không cần edit customer nhiều.

62. Staff /operator/staff
Admin only.
Table:
Staff Code
Name
Email
Phone
Role
Status
Actions:
Add Staff
Deactivate

63. Reports /operator/reports
Tabs:
Revenue
Occupancy

64. Revenue Report
Filters:
From
To
Group By
Cards:
Total revenue
Total tickets
Chart + table.

65. Occupancy Report
Table:
Trip
Route
Departure
Seats
Occupancy

66. Operator Mobile Support
Customer pages:
Mobile-first / responsive
Operator dashboard:
Desktop-first
V1 chỉ cần admin usable trên tablet/laptop.
Không cần mobile admin hoàn hảo.

67. Common Components
Frontend nên có component dùng lại:
AppHeader
AppFooter

TripSearchForm
LocationAutocomplete
TripCard

SeatMap
SeatLegend
HoldCountdown

BookingSummary
PaymentStatusBadge
BookingStatusBadge

Pagination
ConfirmModal
EmptyState
LoadingSpinner
ErrorState
Operator:
OperatorSidebar
OperatorTopbar

DataTable
PageHeader
FilterBar

RouteStopEditor
FareEditor
TripTimeline
SeatSegmentDetail
StatsCard

68. Status Badge Convention
Booking:
PENDING
CONFIRMED
CANCELLED
COMPLETED
Payment:
PENDING
PAID
FAILED
REFUNDED
Trip:
SCHEDULED
BOARDING
DEPARTED
COMPLETED
CANCELLED
Bus:
AVAILABLE
MAINTENANCE
INACTIVE
Nên dùng component chung thay vì hard-code CSS ở từng page.

69. Protected Routes
Customer:
/profile
/booking
/payment
/my-bookings/**
yêu cầu authenticated.
Operator:
/operator/**
yêu cầu:
OPERATOR_STAFF
hoặc
OPERATOR_ADMIN
Một số page:
/staff
/reports
có thể chỉ ADMIN.

70. Role-based UI
Frontend có thể ẩn menu user không có quyền.
Ví dụ OPERATOR_STAFF:
Dashboard
Trips
Bookings
không thấy:
Staff Management
Nhưng backend vẫn phải enforce authorization.

71. Navigation Guard
Nếu user truy cập:
/operator/dashboard
nhưng role CUSTOMER:
403 page
Không redirect về dashboard customer một cách mơ hồ.

72. 404 Page
Không tìm thấy trang

[Quay về trang chủ]

73. 403 Page
Bạn không có quyền truy cập trang này.

74. API Error Handling UI
Ví dụ:
NETWORK_ERROR
Hiển thị:
Không thể kết nối đến máy chủ.

[Thử lại]
Không show stack trace/backend exception.

75. Loading UX
Search:
TripCard Skeleton
Tables:
Loading rows
Buttons:
Spinner + disabled
để tránh double submit.

76. Toast Notifications
Success:
Đã cập nhật thông tin.
Đã hủy booking.
Đã tạo chuyến.
Error:
Không thể tạo chuyến.
Ghế vừa được người khác chọn.

77. Responsive Customer Header
Desktop:
Logo
Trang chủ
Vé của tôi
Login/Profile
Mobile:
Logo             ☰

78. Home Visual Priority
Homepage nên ưu tiên:
Search form
hơn banner quảng cáo.
Không nên để agent tạo landing page rất đẹp nhưng search form bị chìm.
Search là chức năng chính.

79. UI Design Direction
Đề xuất:
Clean
Modern
Travel-oriented
Large whitespace
Rounded cards vừa phải
Readable typography
Không copy giao diện Vexere pixel-by-pixel.
Chỉ tham khảo:
search-first UX
trip cards
filters
booking progression

80. Customer Page Priority
Must Have:
Home
Search
Trip Detail
Booking
Payment
Booking Success
Login
Register
My Bookings
Booking Detail
Should Have:
Profile polish
Popular routes
Better filters
PDF ticket

81. Operator Page Priority
Must Have:
Dashboard
Bus Types
Buses
Routes
Trips
Trip Detail
Bookings
Should Have:
Customers
Staff
Reports
Advanced seat block

82. Page ↔ API Mapping
Home
GET /locations
Search
GET /trips/search
Trip Detail
GET /trips/{id}
GET /trips/{id}/seats
POST /seat-holds
Booking
GET /seat-holds/{token}
POST /bookings
Payment
POST /bookings/{id}/payments/mock-confirm
My Bookings
GET /bookings/me
Booking Detail
GET /bookings/{id}
GET /bookings/{id}/ticket
POST /bookings/{id}/cancel

83. Operator Page ↔ API Mapping
Dashboard:
GET /operator/dashboard
Bus types:
GET /operator/bus-types
POST /operator/bus-types
PATCH /operator/bus-types/{id}
Buses:
GET /operator/buses
POST /operator/buses
PATCH /operator/buses/{id}
Routes:
GET /operator/routes
POST /operator/routes
GET /operator/routes/{id}
PUT /operator/routes/{id}
PUT /operator/routes/{id}/fares
Trips:
GET /operator/trips
POST /operator/trips
GET /operator/trips/{id}
POST /operator/trips/{id}/cancel
Trip passengers:
GET /operator/trips/{id}/passengers
Bookings:
GET /operator/bookings
GET /operator/bookings/{id}
POST /operator/bookings/{id}/confirm
POST /operator/bookings/{id}/cancel

84. Main UI Edge Cases
Frontend phải xử lý rõ:
Trip vừa bị cancel.
Seat vừa bị người khác giữ.
Hold vừa hết hạn.
Booking đã bị cancel.
Payment đã được confirm trước đó.
Bus/route inactive.
No search result.
Network timeout.

85. Critical Demo Scenario
Để demo project, seed data nên có:
Nhà xe GiangBus

Route:
Đắk Lắk
↓
Gia Lai
↓
Đà Nẵng
↓
Huế
↓
Hà Nội
Có ít nhất 2 trip.
Booking:
A01
Đắk Lắk → Đà Nẵng
Sau đó demo search:
Đà Nẵng → Hà Nội
A01 vẫn AVAILABLE.
Nhưng:
Gia Lai → Huế
nếu overlap đoạn A01 đã được book:
A01 phải unavailable.
Đây là demo giúp chứng minh hệ thống không chỉ là CRUD.

86. Frontend Folder Proposal
src/
├── app/
├── api/
├── components/
├── features/
│
├── features/auth/
├── features/search/
├── features/trip/
├── features/booking/
├── features/profile/
├── features/operator/
│
├── layouts/
├── pages/
├── routes/
├── hooks/
├── utils/
└── types/
Không nên:
components/
  150 files không phân domain

87. State Management
V1 không nhất thiết cần Redux.
Có thể dùng:
React Query / TanStack Query
+
Context cho authentication
Server state:
React Query
Auth state:
AuthContext
Local UI state:
useState
Giảm complexity.

88. Form Management
Có thể dùng:
React Hook Form
với validation library như:
Zod
nhưng không bắt buộc.
Backend vẫn là nguồn validation cuối cùng.

89. API Layer
Frontend phải có centralized API layer.
Ví dụ:
api/
├── authApi
├── locationApi
├── tripApi
├── bookingApi
└── operatorApi
Không gọi fetch() trực tiếp khắp component.

90. UI Definition of Done
Frontend V1 được xem là hoàn thành khi user có thể:
Tìm chuyến
↓
Chọn trip
↓
Chọn pickup/dropoff
↓
Chọn ghế
↓
Booking
↓
Thanh toán
↓
Nhận ticket
mà không cần thao tác trực tiếp database/Postman.
Operator có thể:
Tạo loại xe
↓
Tạo xe
↓
Tạo route
↓
Tạo trip
↓
Xem booking
↓
Xem passenger list
hoàn toàn thông qua UI.

91. Nguyên tắc dành cho Coding Agent
Agent không được:
    1. Tự thay đổi page flow.
    2. Tự bỏ bước seat hold.
    3. Tự thêm lựa chọn nhà xe vào search chính.
    4. Tự hard-code route/location.
    5. Tự tính giá cuối cùng ở frontend.
    6. Tự suy luận seat availability ở frontend.
    7. Tự thiết kế một admin khác hoàn toàn API contract.
    8. Clone giao diện Vexere trực tiếp.
    9. Thêm dependency lớn nếu chưa cần.
    10. Viết tất cả frontend vào vài component khổng lồ.
Agent phải ưu tiên:
Correct flow
↓
Correct API integration
↓
Usability
↓
Visual polish
thay vì ưu tiên animation/trang trí trước.
