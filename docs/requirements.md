BUSGO – SOFTWARE REQUIREMENTS SPECIFICATION
1. Tổng quan hệ thống
1.1 Tên dự án
BusGo – Bus Ticket Booking & Management System
1.2 Mục tiêu
BusGo là hệ thống web hỗ trợ:
    • Khách hàng tìm chuyến xe phù hợp.
    • Chọn điểm đón và điểm đến.
    • Chọn ngày đi.
    • Xem danh sách chuyến xe phù hợp.
    • Chọn ghế.
    • Đặt vé.
    • Thanh toán giả lập.
    • Nhận vé điện tử.
Đồng thời hệ thống cung cấp giao diện quản trị cho nhà xe để:
    • Quản lý phương tiện.
    • Quản lý loại xe.
    • Quản lý tuyến đường.
    • Quản lý điểm đón/trả.
    • Quản lý chuyến xe.
    • Quản lý ghế.
    • Quản lý booking.
    • Quản lý khách hàng.
    • Quản lý nhân viên.
    • Theo dõi doanh thu và hoạt động vận hành.

2. Phạm vi hệ thống
2.1 Phiên bản V1
Phiên bản đầu tiên hỗ trợ:
    • Một nhà xe chính.
    • Nhiều tuyến đường.
    • Nhiều xe.
    • Nhiều chuyến.
    • Nhiều điểm đón/trả trên cùng tuyến.
    • Một ghế có thể được bán cho nhiều hành khách trên cùng chuyến nếu các đoạn đường không trùng nhau.
Ví dụ:
Đắk Lắk
↓
Đà Nẵng
↓
Huế
↓
Hà Nội
Ghế A01 có thể được bán:
Khách A:
Đắk Lắk → Đà Nẵng

Khách B:
Đà Nẵng → Hà Nội
vì hai khoảng di chuyển không chồng lấn.

2.2 Khả năng mở rộng
Mặc dù V1 chỉ hoạt động với một nhà xe, kiến trúc hệ thống phải hỗ trợ mở rộng sang:
Marketplace nhiều nhà xe
mà không phải thiết kế lại toàn bộ database hoặc backend.
Ví dụ trong tương lai:
BusGo
├── Nhà xe A
├── Nhà xe B
├── Nhà xe C
└── Nhà xe D
Khách hàng sẽ tìm chuyến dựa trên:
Điểm đón
+
Điểm đến
+
Ngày đi
sau đó hệ thống tự tìm tất cả nhà xe phù hợp.

3. Kiến trúc hệ thống
Backend:
Java
Spring Boot
Spring Security
JWT
REST API
MySQL
Frontend:
React
Tailwind CSS
Kiến trúc backend:
Modular Monolith
Các module:
auth
user
operator
fleet
location
route
trip
seat
booking
payment
reporting
Không sử dụng microservice trong V1.

4. Actors
Hệ thống có 4 role.
4.1 CUSTOMER
Khách hàng có quyền:
    • Đăng ký.
    • Đăng nhập.
    • Đăng xuất.
    • Tìm chuyến.
    • Xem chuyến.
    • Chọn ghế.
    • Đặt vé.
    • Thanh toán giả lập.
    • Xem vé điện tử.
    • Xem lịch sử booking.
    • Hủy booking nếu đáp ứng điều kiện.

4.2 OPERATOR_STAFF
Nhân viên nhà xe có quyền:
    • Xem chuyến.
    • Xem sơ đồ ghế.
    • Xem booking.
    • Tìm booking.
    • Xác nhận booking.
    • Hủy booking.
    • Xem danh sách hành khách.

4.3 OPERATOR_ADMIN
Có toàn bộ quyền của OPERATOR_STAFF và:
    • Quản lý phương tiện.
    • Quản lý loại xe.
    • Quản lý tuyến.
    • Quản lý điểm đón/trả.
    • Quản lý chuyến.
    • Quản lý nhân viên.
    • Quản lý khách hàng.
    • Xem dashboard.
    • Xem báo cáo.

4.4 SYSTEM_ADMIN
Được chuẩn bị cho phiên bản multi-operator.
SYSTEM_ADMIN có thể:
    • Quản lý các nhà xe.
    • Kích hoạt hoặc khóa nhà xe.
    • Xem hoạt động toàn hệ thống.
Chức năng này chưa bắt buộc hoàn thiện trong V1.

5. Transport Operator
Hệ thống phải tồn tại entity:
TransportOperator
Ví dụ trong V1:
GiangBus
Các đối tượng sau phải xác định được nhà xe sở hữu:
Bus
Staff
Trip
OperatorRoute
Thiết kế này cho phép sau này tồn tại:
Nhà xe A
Nhà xe B
Nhà xe C
trong cùng hệ thống.

6. Authentication
FR-AUTH-01
Người dùng có thể đăng ký tài khoản bằng:
Full name
Email
Phone
Password
FR-AUTH-02
Email phải unique.
FR-AUTH-03
Password phải được hash.
FR-AUTH-04
Hệ thống sử dụng:
Access Token
Refresh Token
FR-AUTH-05
Backend phải kiểm tra role trước khi cho phép truy cập API quản trị.

7. Customer Profile
Customer có các thông tin:
Full name
Email
Phone
Password
Account status
Created date
Customer có thể:
    • Sửa họ tên.
    • Sửa số điện thoại.
    • Đổi mật khẩu.
    • Xem lịch sử đặt vé.

8. Location
Location đại diện cho địa điểm.
Ví dụ:
Buôn Ma Thuột
Đà Nẵng
Huế
Hà Nội
TP.HCM
Location có thể chứa:
id
name
province
district
address
latitude
longitude
status
Latitude và longitude là optional.

9. Route
Route đại diện cho một hành trình theo một chiều xác định.
Ví dụ:
Đắk Lắk → Hà Nội
Route không chỉ có điểm đầu và điểm cuối mà còn chứa các điểm trung gian.
Ví dụ:
Đắk Lắk
↓
Gia Lai
↓
Đà Nẵng
↓
Huế
↓
Nghệ An
↓
Hà Nội

10. Route Stop
Mỗi route gồm nhiều RouteStop.
RouteStop phải chứa:
route
location
stopOrder
allowPickup
allowDropoff
estimatedOffsetMinutes
status
Ví dụ:
Điểm	Thứ tự	Được đón	Được trả
Đắk Lắk	1	Có	Không
Gia Lai	2	Có	Có
Đà Nẵng	3	Có	Có
Huế	4	Có	Có
Hà Nội	5	Không	Có

11. Quy tắc hướng tuyến
Nếu customer tìm:
Đắk Lắk → Hà Nội
route hợp lệ khi:
Đắk Lắk.stopOrder < Hà Nội.stopOrder
Nếu route là:
Hà Nội
↓
Đà Nẵng
↓
Đắk Lắk
thì tìm:
Đắk Lắk → Hà Nội
không hợp lệ.
Mặc dù cả hai điểm đều xuất hiện trên route.

12. Operator Route
Một route có thể được khai thác bởi nhiều nhà xe.
Ví dụ:
Đắk Lắk → Hà Nội
có thể được khai thác bởi:
Nhà xe A
Nhà xe B
Nhà xe C
Entity:
OperatorRoute
đại diện cho:
TransportOperator
+
Route

13. Bus Type
BusType đại diện cho loại phương tiện.
Ví dụ:
Limousine 22 phòng
Giường nằm 34 chỗ
Ghế ngồi 45 chỗ
Thông tin:
name
seatCount
description
status

14. Seat Template
Mỗi BusType có sơ đồ ghế.
Ví dụ:
A01 A02
A03 A04
A05 A06
Seat Template chứa:
seatCode
row
column
floor
seatType
Frontend sử dụng dữ liệu này để render sơ đồ ghế.

15. Bus
Bus gồm:
operator
licensePlate
busType
status
Status:
AVAILABLE
MAINTENANCE
INACTIVE
Không được hard delete bus đã từng tham gia chuyến.

16. Trip
Trip đại diện một chuyến xe cụ thể.
Ví dụ:
Đắk Lắk → Hà Nội
25/09/2026
18:30
Trip chứa:
operator
route
bus
departureTime
estimatedArrivalTime
basePrice
status
Trip status:
SCHEDULED
BOARDING
DEPARTED
COMPLETED
CANCELLED

17. Trip Stop
Khi Trip được tạo, hệ thống phải tạo danh sách TripStop dựa trên RouteStop.
TripStop giúp lưu thời gian thực tế của từng điểm trên chuyến.
Ví dụ:
Đắk Lắk       18:30
Gia Lai       21:00
Đà Nẵng       03:00
Huế           05:00
Hà Nội        15:00
TripStop có:
trip
location
stopOrder
plannedArrivalTime
plannedDepartureTime
allowPickup
allowDropoff
status

18. Trip Segment
Các điểm liên tiếp trên Trip tạo thành segment.
Ví dụ:
Đắk Lắk → Đà Nẵng
Đà Nẵng → Huế
Huế → Hà Nội
Nếu trip có:
Stop 1
Stop 2
Stop 3
Stop 4
thì có:
Segment 1: Stop 1 → Stop 2
Segment 2: Stop 2 → Stop 3
Segment 3: Stop 3 → Stop 4

19. Seat Inventory theo segment
Trạng thái ghế không được quản lý đơn giản theo toàn bộ Trip.
Hệ thống phải xác định trạng thái ghế theo từng segment.
Ví dụ:
Trip:
Đắk Lắk
↓
Đà Nẵng
↓
Huế
↓
Hà Nội
Ghế A01 được đặt:
Đắk Lắk → Đà Nẵng
thì:
Segment Đắk Lắk → Đà Nẵng
A01 = BOOKED
nhưng:
Đà Nẵng → Huế
Huế → Hà Nội
A01 vẫn AVAILABLE.

20. Seat availability
Một ghế chỉ được xem là AVAILABLE nếu ghế đó trống trên toàn bộ segment giữa:
pickupStop
→
dropoffStop
Ví dụ customer muốn:
Đắk Lắk → Huế
thì hệ thống phải kiểm tra:
Đắk Lắk → Đà Nẵng
AND
Đà Nẵng → Huế
Nếu ghế bị BOOKED ở bất kỳ segment nào:
ghế không khả dụng.

21. Seat Status
Trên mỗi segment, trạng thái ghế có thể là:
AVAILABLE
HELD
BOOKED
BLOCKED
Ý nghĩa:
AVAILABLE
Có thể đặt.

HELD
Đang được customer giữ tạm thời.

BOOKED
Đã được booking xác nhận.

BLOCKED
Nhà xe khóa không bán.

22. Seat Hold
Khi customer chọn ghế:
AVAILABLE → HELD
Hệ thống phải lưu:
heldBy
holdExpiresAt
Ví dụ thời gian giữ:
10 phút
Nếu quá thời gian:
HELD → AVAILABLE
Nếu booking thành công:
HELD → BOOKED

23. Search Trips
Đây là chức năng trung tâm của hệ thống.
Customer tìm chuyến bằng:
Điểm đón
Điểm đến
Ngày đi
Không bắt customer chọn nhà xe trước.

FR-SEARCH-01
Customer phải nhập:
pickupLocation
dropoffLocation
departureDate

FR-SEARCH-02
Hệ thống chỉ trả những trip mà route chứa cả:
pickupLocation
dropoffLocation

FR-SEARCH-03
Pickup phải xuất hiện trước Dropoff trong chiều chạy.
Điều kiện:
pickup.stopOrder < dropoff.stopOrder

FR-SEARCH-04
Pickup stop phải có:
allowPickup = true

FR-SEARCH-05
Dropoff stop phải có:
allowDropoff = true

FR-SEARCH-06
Trip phải thuộc đúng ngày customer chọn.

FR-SEARCH-07
Trip phải có status cho phép booking.
Ví dụ:
SCHEDULED

FR-SEARCH-08
Trip phải còn ít nhất một ghế AVAILABLE trong toàn bộ đoạn:
pickup → dropoff

FR-SEARCH-09
Nhà xe không phải điều kiện tìm kiếm bắt buộc.
Nhà xe chỉ là filter sau khi search.

24. Search Result
Mỗi kết quả hiển thị:
Operator
Bus type
Pickup time
Dropoff time
Duration
Starting price
Available seats
Ví dụ:
Nhà xe B
Limousine 22 phòng

18:30 Đắk Lắk
↓
10:00 Hà Nội

650.000đ
Còn 5 ghế

25. Search Filters
Customer có thể lọc:
Nhà xe
Loại xe
Khoảng giá
Giờ khởi hành

26. Search Sorting
Customer có thể sort:
Giá thấp nhất
Giờ khởi hành sớm nhất
Giờ khởi hành muộn nhất

27. Trip Detail
Trang Trip Detail hiển thị:
Nhà xe
Loại xe
Tuyến
Điểm đón
Điểm trả
Giờ đón
Giờ đến dự kiến
Giá
Sơ đồ ghế

28. Seat Selection
Customer chọn:
pickupStop
dropoffStop
seat
Backend phải kiểm tra availability theo segment.
Frontend không được tự quyết định ghế còn hay hết.

29. Booking
Booking chứa:
bookingCode
customer
trip
pickupStop
dropoffStop

contactName
contactPhone
contactEmail

totalAmount

bookingStatus
createdAt

30. Booking Item
Một booking có thể có nhiều ghế.
Ví dụ:
Booking BG001

A01
A02
A03
BookingItem chứa:
booking
seatCode
price
passengerName
Passenger name có thể optional trong V1.

31. Booking Status
PENDING
CONFIRMED
CANCELLED
COMPLETED

32. Booking Validation
Trước khi tạo booking, backend phải kiểm tra:
    • Trip tồn tại.
    • Trip chưa khởi hành.
    • Pickup hợp lệ.
    • Dropoff hợp lệ.
    • Pickup đứng trước dropoff.
    • Các ghế đang được giữ bởi đúng customer.
    • Không có segment bị BOOKED bởi booking khác.
    • Booking có ít nhất một ghế.
    • Tổng tiền được tính ở backend.

33. Pricing
V1 có thể dùng:
basePrice
hoặc giá dựa trên pickup → dropoff.
Hệ thống nên thiết kế để sau này hỗ trợ:
giá theo từng chặng
Ví dụ:
Đắk Lắk → Đà Nẵng = 250.000
Đà Nẵng → Hà Nội = 500.000
Đắk Lắk → Hà Nội = 700.000
Frontend không được gửi tổng giá làm giá trị tin cậy.
Backend phải tự tính lại.

34. Payment
V1 hỗ trợ:
CASH
MOCK_QR

35. Payment Status
PENDING
PAID
FAILED
REFUNDED

36. Mock QR Payment
Customer chọn:
MOCK_QR
Hệ thống hiển thị QR giả lập.
Sau khi giả lập thanh toán thành công:
Payment = PAID
Booking = CONFIRMED
Seats = BOOKED

37. Ticket
Sau khi booking CONFIRMED, customer nhận vé điện tử.
Ticket hiển thị:
Booking code
Operator
Passenger
Pickup
Dropoff
Departure time
Seat
Payment status
QR Code
QR có thể chứa:
bookingCode
hoặc URL xác minh vé.

38. My Bookings
Customer có trang:
Vé sắp đi
Vé đã hoàn thành
Vé đã hủy
Customer có thể xem:
    • Chi tiết booking.
    • Ghế.
    • Điểm đón.
    • Điểm trả.
    • Trạng thái thanh toán.
    • Vé điện tử.

39. Cancellation
Customer chỉ được tự hủy nếu:
departureTime - currentTime > cancellationThreshold
Ví dụ:
6 giờ
Nếu booking bị hủy:
Booking → CANCELLED
Các seat segment liên quan phải được trả lại:
BOOKED → AVAILABLE

40. Refund
V1 không xử lý hoàn tiền thật.
Nếu payment đã PAID và booking bị hủy:
Payment → REFUNDED
đây chỉ là trạng thái giả lập.

41. Booking Status History
Mọi thay đổi trạng thái booking phải lưu lịch sử:
booking
fromStatus
toStatus
changedBy
changedAt
note
Ví dụ:
PENDING
↓
CONFIRMED
↓
CANCELLED

42. Bus Management
Operator Admin có thể:
Xem danh sách xe
Thêm xe
Sửa xe
Khóa xe
Lọc theo trạng thái
Tìm theo biển số

43. Bus Type Management
Admin có thể:
Thêm loại xe
Sửa loại xe
Thiết lập sơ đồ ghế
Khóa loại xe

44. Route Management
Admin có thể:
Tạo route
Sửa route
Thêm stop
Xóa stop chưa sử dụng
Thay đổi thứ tự stop
Bật/tắt pickup
Bật/tắt dropoff
Khóa route

45. Trip Management
Admin có thể:
Tạo trip
Sửa trip
Xem trip
Hủy trip
Trip detail hiển thị:
Bus
Route
Stops
Seat occupancy
Passenger list
Revenue

46. Trip Validation
Một bus không được tham gia hai trip bị overlap thời gian.
Ví dụ:
Trip A:
18:00 → 06:00

Trip B:
22:00 → 08:00
cùng một bus:
Không hợp lệ.

47. Passenger Manifest
Mỗi trip có danh sách hành khách:
Seat
Passenger
Phone
Pickup
Dropoff
Booking status
Payment status

48. Booking Management
Staff/Admin có thể:
Search booking code
Search phone
Search customer name
Filter booking status
View booking
Confirm booking
Cancel booking

49. Customer Management
Admin có thể xem:
Name
Email
Phone
Total bookings
Total spending
Account status

50. Staff Management
Operator Admin có thể:
Create staff
Edit staff
Deactivate staff
Assign role
Staff phải thuộc một operator.

51. Dashboard
Dashboard hiển thị:
Trips today
Bookings today
Tickets sold today
Revenue today
Occupancy rate
Recent bookings

52. Reports
V1 hỗ trợ báo cáo cơ bản:
Revenue by day
Revenue by route
Tickets sold
Trip occupancy

53. Soft Delete
Không hard delete dữ liệu quan trọng đã được sử dụng.
Các entity như:
Bus
Route
Trip
User
Operator
phải sử dụng:
status
active
deletedAt
tùy trường hợp.

54. Concurrency
Đây là requirement bắt buộc.
Hai customer có thể cùng lúc chọn cùng một ghế.
Backend phải đảm bảo:
Không thể có hai booking BOOKED trên cùng:
seat + trip segment
Hệ thống phải sử dụng transaction và locking phù hợp.
Không được dựa vào frontend để xử lý double booking.

55. Authorization theo Operator
Staff của Nhà xe A:
không được xem dữ liệu Nhà xe B
Nếu sau này hệ thống có nhiều nhà xe.
Backend phải scope dữ liệu theo:
operatorId
ở các API quản lý.

56. Validation
Backend phải validate:
Email
Phone
Price
Departure time
Route
Stop order
Pickup/dropoff
Trip status
Seat status
Booking status
Payment status

57. Error Handling
API phải trả lỗi theo format thống nhất.
Ví dụ:
{
  "code": "SEAT_NOT_AVAILABLE",
  "message": "Seat A01 is not available for the selected route segment",
  "timestamp": "..."
}
Một số error code dự kiến:
TRIP_NOT_FOUND
INVALID_ROUTE_DIRECTION
INVALID_PICKUP_STOP
INVALID_DROPOFF_STOP
SEAT_NOT_AVAILABLE
SEAT_HOLD_EXPIRED
BOOKING_NOT_FOUND
TRIP_ALREADY_DEPARTED
BUS_SCHEDULE_CONFLICT

58. Pagination
Các danh sách lớn phải hỗ trợ pagination:
Trips
Bookings
Customers
Buses
Staff

59. Search Admin
Admin có thể search bằng:
Booking code
Customer
Phone
License plate
Route

60. Logging
Backend phải log các hoạt động quan trọng:
Login failure
Booking creation
Booking cancellation
Payment changes
Trip cancellation
System errors
Không log:
Password
JWT
Sensitive payment data

61. Non-functional Requirement
Performance
Các API search thông thường nên phản hồi nhanh trong phạm vi dữ liệu demo.
Security
    • Password hashing.
    • JWT.
    • Authorization.
    • Backend validation.
    • Không expose password.
Maintainability
Code phải được chia module rõ ràng.
Không viết toàn bộ logic vào Controller.
Khuyến nghị:
Controller
↓
Service
↓
Repository

62. Out of Scope V1
Không triển khai:
Thanh toán thật
Hoàn tiền thật
GPS realtime
Theo dõi xe trực tiếp
Mobile app native
Microservices
Kafka
Chat
Coupon engine
Loyalty
AI recommendation
Dynamic pricing
Bảo hiểm
Giao hàng
Parcel delivery
Operator self-registration
Commission settlement
Driver application

63. Future V2
Hệ thống có thể mở rộng:
Multiple Operators
Operator onboarding
Operator dashboard
Platform admin
Operator review/rating
Commission
Settlement
Real payment
Real refund
GPS tracking
Mobile app
Advanced schedule management
Recurring trips
Dynamic pricing
Promo codes

64. Customer Flow
Flow chính của customer:
Home
↓
Chọn điểm đón
↓
Chọn điểm đến
↓
Chọn ngày đi
↓
Search
↓
Danh sách trip phù hợp
↓
Filter nhà xe / loại xe / giờ / giá
↓
Trip Detail
↓
Chọn pickup
↓
Chọn dropoff
↓
Chọn ghế
↓
Hold Seat
↓
Nhập thông tin
↓
Booking
↓
Payment
↓
Ticket

65. Admin Flow
Login
↓
Dashboard
↓
Quản lý:

Operator
Bus Type
Bus
Route
Stops
Trip
Booking
Customer
Staff
Reports

66. Definition of Done
V1 được xem là hoàn thành khi demo được đầy đủ flow:
ADMIN

Tạo Bus Type
↓
Tạo Seat Layout
↓
Tạo Bus
↓
Tạo Route
↓
Tạo Stops
↓
Tạo Trip


CUSTOMER

Register
↓
Login
↓
Chọn điểm đón
↓
Chọn điểm đến
↓
Chọn ngày
↓
Search
↓
Chọn Trip
↓
Chọn ghế
↓
Booking
↓
Mock Payment
↓
Receive Ticket


ADMIN

Xem Booking
↓
Xem Passenger List
↓
Xem Revenue
↓
Xem Dashboard
Ngoài ra phải chứng minh được trường hợp:
Khách A:
A01
Đắk Lắk → Đà Nẵng

Khách B:
A01
Đà Nẵng → Hà Nội
được phép.
Nhưng:
Khách C:
A01
Gia Lai → Huế
nếu đoạn này overlap với booking khác:
phải bị từ chối.
Đây là một trong những nghiệp vụ quan trọng nhất của hệ thống.

67. Nguyên tắc phát triển
Codex phải tuân thủ:
    1. Không tự ý thêm feature ngoài Requirement.
    2. Không chuyển sang microservice.
    3. Không bỏ qua backend validation.
    4. Không xử lý seat availability chỉ ở frontend.
    5. Không lưu trạng thái ghế theo toàn Trip nếu nghiệp vụ cần kiểm tra theo segment.
    6. Không hard-code một operator vào business logic.
    7. Không hard-code danh sách route/location.
    8. Không tin totalAmount từ frontend.
    9. Không hard-delete dữ liệu lịch sử.
    10. Mỗi milestone phải build và test thành công trước khi chuyển sang milestone tiếp theo.
