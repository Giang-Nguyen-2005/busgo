# M31.1 — Data audit & expansion plan

Ngày audit: **08/11/2026, giờ Việt Nam**. Project: `C:\Users\Giang\busgo`.

Quy mô kế hoạch đã cập nhật theo quyết định người dùng trước M31.2. Inventory/bằng chứng audit bên dưới giữ mốc trước triển khai; số liệu schema test và lookup ID đã triển khai sau đó được ghi riêng trong [M31.2 — Locations & Routes](m31-2-locations-routes.md). Không chuyển số liệu test thành dữ liệu database ứng dụng.

Quyết định mới tại [M31.3 — Operators & Fleet](m31-3-operators-fleet.md) chốt **đúng3 nhà xe**, không thêm nhà xe thứ tư; kế hoạch fleet19 xe và26 departures/ngày dự kiến. Các lựa chọn tối đa4 nhà xe ở bản kế hoạch trước bên dưới không còn áp dụng.

## 1. Executive summary

M31 nên mở rộng **độ phủ lịch chạy và nội dung vận hành**, trước khi tăng mạnh số lượng danh mục. Source hiện định nghĩa 3 nhà xe, 7 xe, 3 loại xe, 6 địa điểm, 9 tuyến có hướng và 13 quan hệ nhà xe–tuyến. Nhưng `tripSpecs()` chỉ chạy **HCM → Đà Lạt và tuyến COASTAL**. Bảy tuyến còn lại có danh mục/giá nhưng không được seeder tạo chuyến trực tiếp. Home có thể giới thiệu tuyến mà tìm theo ngày không có chuyến; đây là giới hạn nội dung có căn cứ, chưa phải lỗi toàn vẹn MySQL.

Baseline Git: branch `main`, HEAD `df8f0b7812c061774c3d1285d5311d9b776cf31f`, message `Complete M30 and fix seat map hover UI`. Trước audit, Git chỉ báo file untracked `tatus --short`; không đọc, sửa hoặc xóa file này. Không tìm thấy `AGENTS.md` trong repo hoặc các cấp cha đã kiểm tra.

**Giới hạn bằng chứng:** Docker daemon đọc được nhưng `docker ps` không có container đang chạy. Kiểm tra listener 3306/3307/3308/8080/5173 và process `mysqld`/`mariadbd` không trả kết quả. Không khởi động stack vì startup có thể tự chạy Flyway/seeder. Không có truy vấn MySQL nào được thực thi; **mọi số record thực tế đều chưa xác minh**, kể cả schema version đang lưu. Không kết luận volume/database không tồn tại. Các database M29/M30 trong báo cáo cũ là evidence lịch sử, không phải inventory hiện tại.

Quy mô theo quyết định người dùng: **ưu tiên 3 operator hiện tại, tối đa 4 nếu thực sự cần; giữ 3 bus types; 16–24 xe tổng; 20–30 chuyến/ngày; lịch tối đa 14 ngày; khoảng 20–25 tuyến có hướng**. Catalogue M31.2 giữ đích 12 điểm đến, 24 direct routes + 1 coastal legacy = 25 routes. Không có mục tiêu 6 operators; association được chốt theo lịch và năng lực 3 nhà xe, không theo quota tăng trưởng. Triển khai trước một pilot nhỏ; chỉ tăng khi phân trang, search, lịch xe và thời gian tải đạt nghiệm thu. Giữ ba layout hiện có. Không cần migration cho danh mục cơ bản. Giá theo loại xe, phân cấp tỉnh/bến và provenance trong DB là các yêu cầu riêng cần thiết kế thêm nếu thực sự cần.

Phạm vi thực hiện M31.1 chỉ thêm tài liệu này; quy mô kế hoạch đã cập nhật theo quyết định người dùng trước M31.2. Không thay source, migration, database, cấu hình, test fixtures; không chạy seeder/test có ghi DB; không commit/push/merge/deploy. Dừng để review trước M31.2.

### Nguồn và cách đọc báo cáo

- [README](../README.md), [architecture](architecture.md), [database design](database-design.md), [API contract](api-contract.md), [demo data](demo-data.md). Architecture/API contract có phần lịch sử V1.5; đối chiếu source hiện hành khi khác nhau.
- [M29 customer](m29-customer-experience.md), [M29 dispatch](m29-operator-dispatch.md), [M29 hardening](m29-final-hardening.md); [M30 audit](m30-product-experience-audit.md), [prebooking](m30-2b-prebooking-information.md), [assets](m30-3-final-assets-integration.md), [checkout voucher](m30-4a-checkout-voucher-discovery.md), [public promotions](m30-4b-public-promotions.md), [media](m30-5-operator-media-management.md), [M30.6](m30-6-full-regression-hardening.md).
- DDL: toàn bộ `backend/src/main/resources/db/migration/V1…V28`; entity/repository/service/controller trong các package `location`, `operator`, `route`, `fleet`, `trip`, `hold`, `booking`, `payment`, `ticket`, `marketplace`, `promotion`, `media`, `admin`, `onboarding`, cùng operations/reporting/finance/support liên quan.
- Frontend: `HomePage.tsx`, `SearchPage.tsx`, `TripPage.tsx`, `features/marketplace`, `features/customer`, `features/search`, `pages/operator`, `pages/admin`, `api/operatorApi.ts`, `api/adminApi.ts`, các DTO trong `types`.
- **Source baseline** dưới đây là kết quả dự kiến khi chạy lần đầu trên schema sạch, đúng profile, không collision/skip; không có nghĩa đã chạy hoặc đã đếm DB. **Test fixture** chỉ phục vụ test. **Historical evidence** không được chuyển thành kết quả kiểm thử mới.

## 2. Database/schema inventory

### 2.1. Sổ migration đã đối chiếu

| Version | Nội dung hiện hữu / tác động mở rộng |
| --- | --- |
| V1 | Users/roles/membership, operator, location, route/stops/association/fares, bus type/template/bus |
| V2 | Seed 4 role hệ thống; không seed catalogue demo |
| V3 | `refresh_tokens` |
| V4 | Trip snapshots: trips/stops/segments/seats/inventory |
| V5 | Index search pickup và exact fare |
| V6 | Index hold token trên inventory |
| V7 | Bookings/items, liên kết inventory → item |
| V8 | Payments, booking status history, tickets |
| V9 | `buses.image_url` |
| V10 | Unique staff code trong operator |
| V11 | WEB/PHONE, phương thức thu mô phỏng, customer nullable cho PHONE |
| V12 | Employees/capabilities/driver profiles, crew, boarding, pickup closure, operational history |
| V13 | Attendance theo booking item; no-show có thể chưa có ticket |
| V14 | Payment deadline, cancellation, ticket VOID, refunds |
| V15 | Index cho query-time reports |
| V16 | Maintenance và bus status history |
| V17 | Booking modifications/items, payment purpose, ticket replacement, refund context |
| V18 | Trigger kiểm tra số dư refund |
| V19 | Partial cancellations/items; cập nhật trigger và refund context |
| V20 | Notifications/deliveries/preferences |
| V21 | Operator public description/logo, marketplace reviews |
| V22 | Delay/ETA/actual times và trip operational updates |
| V23 | Operator legal/onboarded fields, applications/invitations/history, SUSPENDED |
| V24 | Operator vouchers/usages/history; gross/discount snapshots |
| V25 | Support/disputes/receipts/notifications và audit events |
| V26 | Finance mutex/policies/events/runs/exceptions/statements, immutable triggers |
| V27 | Voucher `customer_visible`, mặc định false, discovery index |
| V28 | `operator_media`, ảnh binary trong MySQL |

Không có migration M31. Không sửa migration đã áp dụng. Có 28 file migration trong source; chưa xác minh `flyway_schema_history` thực tế.

### 2.2. Location, operator, vehicle, route và trip

Các trường liệt kê là trường nghiệp vụ/khóa quan trọng, không phải data dictionary toàn bộ timestamp. Cột “baseline” là số suy ra từ source fresh seed; **record MySQL của tất cả các hàng: chưa xác minh**. `S` = DemoDataSeeder; `API` = chức năng quản trị/nghiệp vụ hiện có; `T` = fixture test riêng. Schema không có cột provenance chung để phân loại chính xác mọi record cũ.

| Bảng thật | Trường và chức năng / quan hệ | Ràng buộc và giới hạn | Nguồn; baseline source |
| --- | --- | --- | --- |
| `locations` | `name, province, district, address, latitude, longitude, status`; được route/stops tham chiếu | Province/district là text nullable, không phải FK hành chính. Không unique name/province ở DB; seed kiểm tra trùng và khớp cấu trúc | S/T; 6 |
| `transport_operators` | `code, name, phone, email, address, status, public_description, logo_url, legal_name, legal_code, legal_contact, website, onboarded_at` | Unique code/legal_code; ACTIVE/INACTIVE/SUSPENDED. Profile nằm cùng bảng; seed không điền public description/logo/legal fields | S/API/T; 3 |
| `users` | `full_name, email, phone, password_hash, status, deleted_at`; tài khoản đăng nhập | Unique email; ACTIVE/INACTIVE/LOCKED, soft delete. Không đồng nhất với operational employees | S/register/bootstrap/API/T; seed tạo 3 account operator |
| `roles`, `user_roles` | Role code và cặp user–role | 4 role cho phép; composite PK membership role. Không có bảng operator permissions riêng | V2/API/S/T; 4 roles; seed thêm 3 user-role |
| `operator_staff` | `operator_id, user_id, staff_code, status` | Unique operator/user và operator/staff_code; quyền còn cần user/role/membership hợp lệ, một operator context duy nhất | S/API/T; 3 |
| `operator_media` | UUID `id`, `operator_id, bus_type_id, category, alt, position, featured, mime, content` và timestamps | LOGO iff bus_type_id NULL; FK operator/type. Gallery theo operator/type, không theo bus/trip. Giới hạn service 12 ảnh/cặp, 5 MiB/ảnh; unique featured do service khóa, không phải SQL unique | Upload API/T; S không tạo |
| `bus_types` | `name, seat_count, description, status` | Danh mục toàn hệ thống, không có operator_id. SQL cho count ≥0 nhưng service yêu cầu count >0/layout hợp lệ. Name không unique | S/T; 3 |
| `seat_templates` | `bus_type_id, seat_code, row_no, column_no, floor_no, seat_type, active` | Unique type/code; chỉ STANDARD trong enum seat_type. Geometry dương, mã/vị trí không trùng và active count = seat_count do service kiểm tra | S/T; 96 = 22+34+40 |
| `buses` | `operator_id, bus_type_id, license_plate, status, image_url, deleted_at` | Unique plate; AVAILABLE/MAINTENANCE/INACTIVE; soft delete. Ownership giữa bus và association được service kiểm tra | S/API/T; 7 |
| `routes` | `name, origin_location_id, destination_location_id, estimated_distance_km, estimated_duration_min, status` | FK endpoints; distance/duration ≥0. Có hướng; không tự tạo chiều về, không unique endpoints/name | S/T; 9 |
| `route_stops` | `route_id, location_id, stop_order, allow_pickup, allow_dropoff, estimated_offset_minutes, status` | Unique route/order và route/location; không thể lặp cùng location trên một route. Service kiểm tra thứ tự liên tục, offsets tăng, endpoints đúng | S/T; 20 = 8×2+4 |
| `operator_routes` | `operator_id, route_id, status` | Unique operator/route. Không thể tạo hai association cùng operator/route để phân giá theo bus type | S/API/T; 13 |
| `operator_route_fares` | `operator_route_id, from_route_stop_id, to_route_stop_id, price, status` | FK không tự bảo đảm hai stops cùng route/đúng chiều; service chịu trách nhiệm. SQL price ≥0, commerce cần >0. Không có bus_type_id/trip_id/date/currency; exact journey hiện tại dùng giá ở đây | S/API/T; 18 = 12 direct+6 coastal |
| `trips` | `operator_route_id, bus_id, departure_time, estimated_arrival_time, status`, delay/expected/actual/operational_updated fields | Arrival > departure; lifecycle SCHEDULED/BOARDING/DEPARTED/COMPLETED/CANCELLED. Không có bảng operating_dates/recurrence; mỗi chuyến có timestamp UTC cụ thể. Không unique slot ở SQL; tạo dưới bus lock và check overlap | S/API/T; 21 |
| `trip_stops` | `trip_id, source_route_stop_id, location_id, stop_order, planned_arrival_time, planned_departure_time, allow_pickup, allow_dropoff, status` | Snapshot nguồn stop; unique trip/order. Location vẫn là FK; không snapshot nguyên địa chỉ. Sửa route mới không sửa trip cũ | Aggregate/T; 48 |
| `trip_segments` | `trip_id, from_trip_stop_id, to_trip_stop_id, segment_order` | Unique trip/order, from ≠ to; cùng trip/liên tục được aggregate bảo đảm | Aggregate/T; 27 |
| `trip_seats` | `trip_id, source_seat_template_id, seat_code, row_no, column_no, floor_no, seat_type` | Unique trip/code; geometry >0, snapshot không cập nhật theo template hiện tại | Aggregate/T; 678 |
| `trip_seat_segment_inventory` | `trip_seat_id, trip_segment_id, status, hold_token, held_by_user_id, hold_expires_at, booking_item_id, updated_at, version` | Unique seat/segment; AVAILABLE/HELD/BOOKED/BLOCKED; lock/version. **Không có bảng seat_holds riêng**. Cần đủ mọi segment của hành trình; missing không phải AVAILABLE | Aggregate/hold/booking/jobs/T; 882 |

**Không tồn tại bảng riêng cho provinces/cities, bus stations, pickup_points, dropoff_points, seat_layouts, operating_dates hoặc route schedules.** Bến/địa điểm cùng nằm ở `locations`; điểm đón/trả là flags trên route/trip stops. Không đặt tên các bảng tưởng tượng vào kế hoạch nhập dữ liệu. Province/district cũ chỉ là nhãn demo, chưa đối chiếu địa giới hiện hành; báo cáo này không chứng nhận tính chính thức của các nhãn đó.

### 2.3. Commerce và promotions

MySQL record count của từng bảng dưới đây **chưa xác minh**. Seeder không tạo giao dịch hay voucher, không đồng nghĩa DB đang có 0 dòng.

| Bảng thật | Chức năng / trường quan trọng / quan hệ | Ràng buộc, nguồn và giới hạn mở rộng |
| --- | --- | --- |
| `bookings` | `booking_code, customer_id, trip_id, pickup_trip_stop_id, dropoff_trip_stop_id, contact_*`, source/payment_method/deadline/cancel fields, total/gross/discount/original/voucher snapshots | Unique code/token hash; WEB cần customer, PHONE accountless; net total >0; gross−discount=total. API/commerce/T; không seed raw |
| `booking_items` | Booking → trip_seat, seat_code/passenger_name/unit_price, gross/discount/original, cancelled | Unique active seat trong booking; unit_price >0; item snapshots giữ lịch sử. API/T |
| `booking_status_history` | booking, from/to status, actor/time/note/reason_code | From ≠ to; history theo nghiệp vụ. API/jobs/T |
| `payments` | booking, method, amount, status, transaction_reference, paid_at, collector/note, purpose/modification_id | Unique transaction_reference; một PAID BOOKING payment, modification payment unique; phương thức MOCK_ONLINE/PAY_ON_BOARD/QR_TRANSFER đều mô phỏng. API/T |
| `tickets` | booking/item/payment, ticket_code, passenger_name, seat_code, status/void/replaced | Unique code và một ticket current/item; ticket replaced phải VOID; không tạo ticket để làm đẹp dashboard. Payment API/T |
| `refunds` | payment/paid timestamp/amount, actor/time/reason/note, modification_id/partial_cancellation_id | Trigger kiểm tra payment PAID, balance và ownership context; uniqueness theo refund context. Amount net đã thu, không lấy giá hiện tại. API/T |
| `booking_modifications`, `booking_modification_items` | Yêu cầu đổi, trip/journey/seat/ticket/fare snapshots, old/new gross/discount, collection/refund, expiry/status | Một active modification/booking và một target seat/request; transactional hold/thu/hoàn. API/T |
| `partial_cancellations`, `partial_cancellation_items` | Cancel một phần với actor/journey/totals/refund snapshots và item/ticket references | Giữ lại ít nhất một phần giá trị; một cancellation/item, balance checks. API/T |
| `operator_vouchers` | operator/code/name/description, FIXED_AMOUNT/PERCENT/value/cap/minimum, starts/ends/limits/state, operator_route_id, customer_visible, actors | OPERATOR funding only; unique code/operator; DRAFT/ACTIVE/PAUSED; route scope là association của chính owner; public opt-in false. API/T |
| `voucher_usages` | voucher/operator/booking/customer, RESERVED/REDEEMED/RELEASED, expiry/reserve/redeem/release times | Unique booking, composite voucher/owner FK; quota gồm REDEEMED và RESERVED chưa hết hạn. Commerce/T |
| `voucher_management_history` | voucher/actor/event/time | Lịch sử quản lý qua API/T; không giả lịch sử usage |

Không có bảng ticket_prices/promotions/campaigns riêng ngoài các bảng fare/voucher nêu trên. Không có settlement ngân hàng thật.

### 2.4. Bảng hỗ trợ vận hành và quản trị

| Bảng thật | Vai trò/quan hệ/ràng buộc đáng chú ý | Nguồn |
| --- | --- | --- |
| `operator_employees`, `employee_capabilities`, `driver_profiles` | Nhân sự vận hành/operator; unique employee_code/operator; capability DRIVER/ATTENDANT; driver có licence expiry. Không phải account đăng nhập | S tạo 2 employees/2 capabilities/1 driver profile cho An Phú; API/T |
| `trip_crew_assignments`, `operational_history` | Crew → trip/employee/actor; released assignment giữ lịch sử, chống overlap trong service. History thao tác chuyến | S chỉ gán crew đủ điều kiện cho trip An Phú mới; API/T; số assignment phụ thuộc thời gian/conflict |
| `ticket_boarding`, `trip_stop_operations` | Attendance theo item (ticket có thể NULL cho no-show unpaid); pickup closure theo trip/stop | Unique item, PK trip/stop; API/T, S không tạo attendance/closure |
| `bus_maintenance_records`, `bus_status_history` | Lịch bảo trì/status bus, actor và thời gian; end > start | API/T; S không tạo, maintenance guard chặn chuyến không assignable |
| `marketplace_reviews` | Booking → rating/text/response/deleted_at; operator suy ra từ booking/trip | Rating 1–5; một active review/booking; customer booking retained và trip completed. API/T; S không tạo |
| `operator_applications`, `operator_admin_invitations`, `operator_onboarding_history` | Hồ sơ pháp lý, operator sau approve, hashed invite, actor/history | Unique legal reservation và current invite; API/T, không giả approval/legal identity bằng seed |
| `notifications`, `notification_deliveries`, `notification_preferences` | Recipient/booking/operator/audience, outbox email attempts/preferences | Occurrence/channel unique; booking nullable sau V23; workflow API/jobs/T, không seed inbox giả |
| `support_tickets`, `support_activity`, `support_disputes`, `support_requests`, `support_notifications`, `audit_events` | Booking/customer/operator, trao đổi/visibility, escalation/decision, idempotency receipts, inbox và audit metadata | Quyền theo tenant/actor; dispute unique ticket; API/T; seeder không tạo |
| `finance_mutex`, `commission_policies`, `financial_events`, `financial_runs`, `financial_exceptions`, `financial_statements` | Serialize theo operator, policy window, ledger collection/refund, reconciliation/snapshot | Finance dùng source IDs logic, không FK commerce; event/issued statement immutable bằng triggers; currency VND và SIMULATED. API/commerce/T; không seed tiền/statement |
| `refresh_tokens` | user, token_hash, expiry | Unique hash; auth/T, không nội dung catalogue |

Số dòng thực tế của toàn bộ nhóm này chưa xác minh. `SystemAdminBootstrapConfig/Service` là runner riêng, bật bằng cấu hình explicit để tạo admin ban đầu; không phải seeder nội dung. `database/` không cung cấp một bộ dữ liệu M31 thay thế. Flyway V2 tạo roles và các migration sau có backfill nghiệp vụ; không dùng migrations làm bộ seed demo mới.

## 3. Current demo data inventory

Nguồn chính: [DemoDataSeeder.java](../backend/src/main/java/com/busgo/demo/DemoDataSeeder.java), đối chiếu [DemoDataSeederIT.java](../backend/src/test/java/com/busgo/demo/DemoDataSeederIT.java). Không chạy hai file này trong audit.

### 3.1. Catalogue và giá

| Operator code / tên demo | Xe và layout | Account seed |
| --- | --- | --- |
| DEMO-ANPHU / An Phú Express | 51B-770.01 sleeper34; .02 standard40; .03 sleeper34 — 3 xe | 1 admin + 1 staff |
| DEMO-MINHTHANH / Minh Thành Limousine | 50F-880.01 limo22; .02 sleeper34 — 2 xe | 1 admin |
| DEMO-TAYNGUYEN / Tây Nguyên Travel | 47B-660.01 standard40; .02 limo22 — 2 xe | Không tạo account cho operator này |

Tên trên là nội dung demo của project, không chứng thực doanh nghiệp thật. Source hiện chứa địa chỉ/số điện thoại trông như vận hành thật; chưa xác minh, cần gắn nhãn và xử lý ở giai đoạn content sau, không dùng để liên hệ.

6 location: Bến xe TP. Hồ Chí Minh, Đà Lạt, Buôn Ma Thuột, Nha Trang, Đà Nẵng, Huế. Không seed tọa độ. Không có extra curbside/office pickup point. 20 route-stop rows tham chiếu cùng 6 location: 10 cho phép pickup và 10 cho phép dropoff. Đây là **số association**, không phải 20 địa chỉ vật lý. Trip snapshot đầu tiên dự kiến có 48 stops, 27 flags pickup và 27 flags dropoff, vẫn chỉ 6 location distinct.

| Route có hướng | Operator associations / giá direct VND | Seeder tạo trip? |
| --- | --- | --- |
| HCM → Đà Lạt; Đà Lạt → HCM | Mỗi chiều: An Phú 250.000; Minh Thành 300.000; Tây Nguyên 220.000 | Chỉ chiều HCM → Đà Lạt |
| HCM → BMT; BMT → HCM | Tây Nguyên 260.000 mỗi chiều | Không |
| HCM → Nha Trang; Nha Trang → HCM | An Phú 290.000 mỗi chiều | Không direct; HCM → Nha Trang có thể đi đoạn coastal |
| Đà Nẵng → Huế; Huế → Đà Nẵng | Minh Thành 140.000 mỗi chiều | Không direct; Đà Nẵng → Huế có thể đi đoạn coastal An Phú |
| COASTAL: HCM → Nha Trang → Đà Nẵng → Huế | An Phú; 6 forward fares | Có |

Coastal fares: HCM→Nha Trang 290.000; Nha Trang→Đà Nẵng 320.000; Đà Nẵng→Huế 140.000; HCM→Đà Nẵng 560.000; Nha Trang→Huế 410.000; HCM→Huế 650.000. Toàn bộ catalogue có **10 mức giá distinct: 140.000, 220.000, 250.000, 260.000, 290.000, 300.000, 320.000, 410.000, 560.000, 650.000 VND**. Giá direct HCM→Đà Lạt 220–300 nghìn theo operator; cùng operator không đổi giá khi đổi loại xe. Không phải báo giá thị trường.

### 3.2. Lịch chạy thực sự được định nghĩa

Seeder dùng `Clock` và `Asia/Ho_Chi_Minh`, bắt đầu ngày mai, lặp 3 ngày. Nếu chạy vào ngày audit 08/11/2026, cửa sổ đề xuất sẽ là **09–11/11/2026**; audit không chạy seed và không khẳng định DB có những ngày này.

| Giờ VN | Xe | Hành trình / đến theo lịch | Giá nguyên chặng; BLOCKED mới |
| --- | --- | --- | --- |
| 06:00 | 51B-770.03 | Coastal 21h → 03:00 ngày sau | 650.000; L01 khóa segment 1, L02 segment 2 |
| 06:30 | 51B-770.01 | HCM→Đà Lạt 7h → 13:30 | 250.000; 4 |
| 08:00 | 50F-880.01 | HCM→Đà Lạt 7h → 15:00 | 300.000; 2 |
| 09:30 | 47B-660.01 | HCM→Đà Lạt 7h → 16:30 | 220.000; 8 |
| 13:00 | 51B-770.02 | HCM→Đà Lạt 7h → 20:00 | 250.000; 6 |
| 18:30 | 50F-880.02 | HCM→Đà Lạt 7h → 01:30 ngày sau | 300.000; 7 |
| 22:00 | 47B-660.02 | HCM→Đà Lạt 7h → 05:00 ngày sau | 220.000; 5 |

- **7 departures/ngày, 21 trips** fresh run; rerun cùng ngày 0 mới/21 reuse; ngày kế tiếp 7 mới/14 reuse nếu không skip. Chuyến quá khứ được giữ lại. Không có job tự gia hạn lịch khi app chạy liên tục.
- Có 4 cặp route catalogue hai chiều; **không seed lịch khứ hồi**. Coastal chưa có reverse route tương ứng. Không coi đây là FK error.
- HCM→Đà Lạt có 3 operator và 6 chuyến/ngày. Các chặng coastal có An Phú; catalogue direct của operator khác không bảo đảm có chuyến để so sánh.
- Có 3 chuyến qua đêm/ngày. Coastal còn 3h trước slot hôm sau trên cùng bus; không overlap timestamp nhưng không mô hình hóa việc đưa xe từ Huế về HCM. Sáu xe Đà Lạt cũng lặp xuất phát HCM mà thiếu vòng về. Đây là hạn chế tính thực tế của timetable demo.
- 3 layout: limo một tầng A01–A22 (11 hàng, cột 1/3); sleeper hai tầng L01–L17/U01–U17; seated S01–S40 (cột 1/2/4/5). Tất cả `seat_type=STANDARD`; không suy ra amenities/VIP từ enum.
- Tính theo aggregate: 226 seats/day → 678 snapshots; inventory 294/day → **882 rows**, gồm 102 BLOCKED và 780 AVAILABLE lúc vừa seed sạch. Không có fake HELD/BOOKED. Đây là phép tính source, không phải DB count.

### 3.3. Media, voucher, giao dịch và test fixtures

Seeder chỉ gán `buses.image_url` JPG tĩnh. Không insert `operator_media`, không set logo/description công khai; logo và nội thất có fallback tĩnh ở frontend. Vì vậy source seed **chưa cung cấp media quản lý qua M30.5**. M30.5/M30.6 đã từng upload và kiểm chứng ảnh trong schema test riêng; không suy ra DB demo hiện tại có ảnh.

Seeder **không tạo voucher**, nên không có “phạm vi voucher demo mặc định”. Schema/API cho operator-wide hoặc một operator_route; FIXED_AMOUNT/PERCENT, public/private, quota/minimum/time. Các mã public/private/expired/exhausted trong test/browser harness không phải dữ liệu của DemoDataSeeder. Không chạy live harness để tạo lại.

Seeder không tạo customer, booking, payment, ticket, refund, review, maintenance hay finance statement. Đủ nền tảng demo booking khi có customer đăng ký, ngày future/SCHEDULED, operator/association/fare hợp lệ, inventory còn chỗ. Không bảo đảm readiness trên DB chưa đọc. Crew chỉ 2 employees của An Phú; gán có kiểm tra overlap và licence, nên không kỳ vọng mọi chuyến An Phú đều có crew, các operator khác chưa có seed crew.

`DemoDataSeederIT` dùng clock 2036, tự dựng bean seeder dưới profile dev và kiểm tra repeat/preservation/overlap/commerce trên schema test. Các `M5*`, `M24B*`, `M30*`, `OperatorMediaIT`, `M16BFixtures` và browser fixtures tạo trạng thái phục vụ assertions. Không import các fixture này vào demo và không dùng ngày/ID/số dòng của chúng làm inventory thật.

## 4. Data quality findings

| ID / phân loại | Bằng chứng source / mức kết luận | Hướng xử lý sau review |
| --- | --- | --- |
| D01 · Demo content limitation | 7/9 route không nằm trong tripSpecs; catalogue chiều về có nhưng không có seeded return trips | Lập ma trận route–operator–date–slot, mỗi route trong bộ demo có lịch được nghiệm thu |
| D02 · Demo content limitation | Chỉ ngày mai..+3; Home search form mặc định hôm nay; operator home cố định hôm nay | Seed missing future slots hôm nay khi còn đủ thời gian; cho chọn ngày có lịch. Không tạo giờ đã qua |
| D03 · Demo content limitation | Thiếu return/reposition trong timetable lặp; nguồn chỉ check overlap thời gian | Xây vòng xe có vị trí đầu/cuối tương thích và buffer; không giả vận hành thật |
| D04 · Demo content limitation | Tây Nguyên không có account seed, crew chỉ An Phú, không có transactions | Bổ sung dedicated admin/staff và crew; tạo story qua API nghiệp vụ ở demo riêng |
| D05 · Demo content limitation | Description/logo managed/voucher không được seed; địa chỉ/contact hiện tại chưa xác minh | Nội dung có nhãn demo, thông tin không gây hiểu nhầm; quản lý media qua đúng API |
| A01 · API/query limitation | LocationService luôn page 0 size20; Home và route-name resolver dùng q rỗng làm lookup đầy đủ | Giải quyết lookup theo ID hoặc lookup theo tập ID trước khi >20 location; không chỉ tăng limit |
| A02 · API/query limitation | Discovery routes ORDER BY id LIMIT8, profile LIMIT30; không phân trang | Home là danh sách giới thiệu có giới hạn; bảo đảm 6 destination có link bằng query/mapping rõ ràng nếu seed không đủ |
| A03 · API/query limitation | Upcoming 6 rows, 30 ngày, choice=1/trip chọn cặp rẻ nhất | Coastal có thể được giới thiệu bằng Đà Nẵng→Huế 140.000 thay vì HCM→Huế; đây là hành vi query, không sai giá. Xác định hành trình đại diện trước khi thay query |
| A04 · API/query limitation | Search lọc SCHEDULED/future pickup, ACTIVE op/association/stops/fare, price>0 và minSeats; rating NULL không đạt minRating | Chuyến bị loại có thể đúng rule; chưa có review thì filter rating có thể rỗng. Đo count trước/sau filter |
| A05 · API/query limitation | Search SQL không lọc trực tiếp route status/bus status/type status/deleted bus; CustomerJourneyResolver cũng không áp đủ các trạng thái catalog này | Khoảng trống cần xác định policy/retest ở M31.6; không khẳng định có record vi phạm. Không mở rộng backend tại M31.1 |
| U01 · UI presentation limitation | Home lấy page0 size6 operator; 6 destination assets exact-name; ảnh minh họa chỉ bật theo VITE_DEMO_MEDIA | Operator mới ngoài 6 đầu không được Home giới thiệu; tên location đổi có thể mất mapping; production demo build phải explicit flag |
| B01 · Business rule constraint | Fare không có bus_type_id/trip/date; operator_routes unique owner/route | Không hứa giá khác loại xe cùng operator/chặng. Muốn hỗ trợ thực sự phải thiết kế pricing riêng |
| B02 · Business rule constraint | PrebookingInformationMapper chỉ trả province/district, không expose address/coordinates | Thêm địa chỉ ở DB không tự làm Trip hiện địa chỉ chính xác; cần contract/provenance riêng nếu yêu cầu |
| B03 · Business rule constraint | Media public cần ACTIVE operator/type và bus AVAILABLE; upload chấp nhận bus khác INACTIVE | Có ảnh lưu nhưng bị ẩn khi mọi bus type đó bảo trì là hành vi có chủ đích, không mất ảnh |
| I01 · Data integrity issue — chưa xác minh | Không đọc được MySQL; chưa có bằng chứng orphan, duplicate, ownership mismatch, giá âm, inventory thiếu hay status drift thực tế | Chạy SELECT ở phụ lục trên đúng schema trước khi nhập M31 |

**Checklist đã đối chiếu, không phát hiện lỗi ở định nghĩa seed:** mỗi operator có xe; bus đúng owner/type; số template/geometry hợp lệ; fare pairs cùng route/đúng chiều; thời gian đến sau giờ đi; có lịch đêm hợp lệ; không overlap timestamp giữa các slot seed; image_url exterior được map theo loại. Không có chứng cứ ảnh managed sai cấu hình vì chưa đọc bytes/DB. Không tự khẳng định record cũ khớp source.

Rủi ro schema: locations/routes/bus_types/fare tuple không có đủ unique natural keys; FKs inventory không bảo đảm seat và segment cùng trip, trip FKs không bảo đảm bus owner khớp association. Service/aggregate bảo vệ đường ghi chuẩn; raw SQL seed có thể vượt các guard. Snapshot khác template mới không tự động là corruption: phải so với cấu hình tại thời điểm tạo, không ép snapshot cũ theo catalogue mới.

## 5. Home / Search / Trip data coverage

| Khu vực | Dữ liệu/API hiện tại | Độ phủ source và phần còn thiếu |
| --- | --- | --- |
| Home: 6 ảnh destination | `visualAssets.ts` + WebP trong `frontend/public/images/busgo`; DestinationDiscovery | Đủ 6 ảnh HCM/Đà Lạt/BMT/Nha Trang/Đà Nẵng/Huế; không phải DB media. Cards vẫn hiện khi không có route; exact-name lookup cần locations đầy đủ |
| Destination discovery | `/public/discovery/routes` + `/locations` | Link chọn ngày dùng endpoint location IDs của route, không giả city ID; first8 tuyến không bảo đảm 6 destination sau khi mở rộng |
| Operator discovery | `/public/discovery/operators` page0 size6 | Có 3 op source, thiếu public description/managed logo/reviews; static logo không chứng minh profile DB đầy đủ |
| Active routes | `/public/discovery/routes` | Route/association/operator ACTIVE; không đòi có future trip. Không được quảng cáo là có chuyến hàng ngày |
| Upcoming | `/public/discovery/trips` | 6 kết quả trong 30 ngày, ít nhất 1 seat; lịch 3 ngày chỉ được thêm khi seed chạy. Không cần đổ 30 ngày dữ liệu để lấp 6 cards |
| Public promotions | `/public/promotions` | Không voucher seed; static promotional cards chỉ hướng dẫn, không phải campaign. Cần opt-in ACTIVE/còn hạn/còn global quota |
| Search origin/destination | `/locations?q=`; search yêu cầu pickupLocationId/dropoffLocationId | Location là điểm cụ thể, không có search theo toàn bộ tỉnh hoặc city grouping. Bến khác trong cùng tỉnh không tự gộp |
| Search đa nhà xe/loại xe/giờ/giá | `/trips/search`, trang 10 kết quả, API default20 max100 | HCM→Đà Lạt có 3 op/3 layouts/6 giờ; tuyến khác mỏng hoặc không có chuyến. Filter theo price/time/op/type/minSeats/minRating và sorts đã có |
| Search chỗ trống | Query inventory theo mọi segment của journey | M30.6 đọc cả HELD đã hết hạn theo rule hiện tại; thiếu rows không thành ghế trống. Không tự suy ra seat_count−booked |
| Trip info/seat map | `/trips/{id}?pickupLocationId&dropoffLocationId`, availability endpoint | Có stop/seat snapshots, loại xe, giá exact journey, overnight times; detail hết ghế có thể trả conflict, không bảo đảm xem được mọi trip lịch sử |
| Pickup/dropoff | Snapshot stop IDs/flags/time + prebooking province/district | Chỉ một location mỗi thành phố trong seed; chưa có hướng dẫn cổng bến, địa chỉ verified, hotline boarding hay transfer options |
| Trip images/operator | `/public/operators/{id}/media`, profile, static fallback | Đúng cặp owner/type; chưa có managed media seed, thiếu public description. Không lấy ảnh cabin làm ảnh layout limo22 |

Home không hiển thị “toàn bộ inventory”. Search có phân trang và totalElements, nên 10 rows/trang là chủ đích, không mặc định là mất dữ liệu. Chuỗi URL vẫn phải giữ location IDs, date, operator/filter/sort/page và context quay lại.

## 6. Operator / Admin data coverage

Operator dashboard không có một `/operator/dashboard` tổng hợp; frontend compose API trips/reports/fleet/occupancy/bookings. `OperatorHomePage` lấy **businessDate hôm nay**, qua `allPages` size100. Trang Dispatch/trips có chọn ngày. Tăng lịch tương lai không tự làm dashboard hôm nay có dữ liệu.

Dispatch lấy lịch phân trang 20 rows; ngày/status được lọc phía server, nhưng route/bus/text trong bảng Dispatch chỉ lọc **trang đang tải**, đổi trang sẽ xóa các filter cục bộ đó. Đây là UI presentation limitation cần ghi vào acceptance khi lịch trong ngày vượt20, không kết luận chuyến đã bị mất khỏi database. Không nhầm với Search customer vốn gửi filter lên server.

| Nhu cầu | Nguồn thật | Thiếu trong fresh source seed / kế hoạch |
| --- | --- | --- |
| Profile, account/quyền | Marketplace own profile, users/roles/operator_staff | Description và managed logo; admin cho Tây Nguyên và dedicated accounts cho operator mới |
| Vehicles/types/layout | Operator buses, global bus-types | 7 xe đủ smoke booking, không đủ vòng khứ hồi phong phú; cần xe dự phòng và layout hiện có hợp lệ |
| Trips/seats | Operator trips, seats/occupancy, inventory | Chưa có hôm nay trong lần seed đầu; loại chuyến ít; không giả occupied state |
| Bookings/passengers/dispatch | Operator bookings/manifests/crew/attendance/pickups/history | Không seed booking; 2 crew không phủ các chuyến đồng thời; cần kịch bản API để có pending/paid/no-show |
| Media | Operator marketplace logo và bus-types media | Chưa seed managed rows; upload phải đúng operator/type, preserve gallery người dùng |
| Reports/customers | Reporting/customer APIs từ booking/payment/refund thật | Fresh catalogue không tạo doanh thu/attendance/customer metrics; tập rehearsal nhỏ qua workflow |
| Finance | M27A API từ payments/refunds/policy/reconciliation | Không tự bịa source/ledger; chuẩn bị policy trước giao dịch nếu muốn demo kỳ sạch |
| Admin operator list/detail | `/admin/operators` paged20/max100, staff, counts buses/routes/trips/bookings | Có catalogue counts khi DB có seed, nhưng Tây Nguyên không có admin; không giả trạng thái onboarded |
| Admin home | `AdminPages.tsx`: totalElements từ các list theo trạng thái | Không có KPI doanh thu/toàn bộ inventory ở màn hình này; không dựng card số liệu mới |
| Onboarding summary | `/admin/dashboard` trong OnboardingService | Đếm SUBMITTED/UNDER_REVIEW/ONBOARDING/ACTIVE/SUSPENDED/ACTIVE_ROUTES/UPCOMING_TRIPS; UPCOMING_TRIPS đếm SCHEDULED future, không cùng bộ lọc seat/fare như public |
| Applications/disputes/audit | M26/M28 APIs và finance admin | Seeder không tạo queue hoặc dispute. Trống hợp lệ; nếu demo populated state, tạo story có nhãn demo qua API đúng quyền |

Staff chỉ có quyền đọc phù hợp; SYSTEM_ADMIN không dùng operator context. Không gán nhiều operator membership hoặc SYSTEM_ADMIN vào admin demo để vượt giới hạn. Operational employee và login staff là hai mô hình riêng.

## 7. Missing data và API limitations / impact analysis

| Thành phần | Chỉ thêm dữ liệu | Query/API hoặc frontend có thể cần thay | Migration? |
| --- | --- | --- | --- |
| Location API | Location demo + flags/stops đúng IDs | Cần lookup đầy đủ cho discovery/route label khi vượt20; autocomplete q vẫn bounded. Frontend không dùng q rỗng làm full registry | Không cho baseline; hierarchy/verified metadata là scope riêng |
| Route API | Route/stops/association/fare forward pairs | Discovery8/profile30 có thể cần paged listing hoặc curated coverage; đừng chỉ dựa insert order | Không cho 24 routes |
| Trip search | Lịch đủ chiều/ngày/op/type/giá và seats | Kiểm tra status policy A05; profile query khi tăng inventory/stop pairs. Giữ exact journey, rating NULL semantics, pagination/sort | Chưa cần; index chỉ sau đo EXPLAIN/latency |
| Upcoming | Future slots còn ghế | choice=1 là cheapest journey/trip, không full route; 6 records/30 days. Nếu cần diversity thì query rõ ràng và tests riêng | Không mặc định |
| Seat inventory | Dùng TripAggregateCreator | Không raw insert partial inventory; giữ expired hold/missing rows rules | Không |
| Booking/quote | Rehearsal qua hold→quote→booking→mock pay | Fare nguồn hiện là exact active operator_route_fares; aggregate không snapshot bảng trip fares riêng. Booking/item mới snapshot tiền; sửa fare ảnh hưởng quote chưa commit, không lịch sử cũ | Chỉ cần nếu yêu cầu per-type/per-date pricing thật |
| Operator management | Dedicated account/member, employee/crew, xe dự phòng | `allPages` đã lấy đủ trang; giữ owner guards và mutation roles | Không |
| Media | Logo + 2–3 ảnh/cặp owner/type | Upload/preview/gallery M30.5, không chỉ set buses.image_url; fallback phụ thuộc VITE_DEMO_MEDIA | Không, V28 đủ |
| Promotions | Private/public, operator-wide/route-specific samples | Public list default6, trip5, max20 và pagination; checkout options max20. Tạo voucher không bảo đảm public/eligible | Không, V24/V27 đủ |
| Seeding | Versioned manifest và create-only runner | Điều phối M31 thay vì hai runner seed trùng; dry-run/report, cửa sổ hữu hạn | Không bắt buộc provenance table |
| Test fixtures | Thêm fixture M31 riêng theo dependency graph | Không tăng global fixtures làm thay đổi counts/IDs của M5/M24/M30. Clock cố định, schema test riêng | Không |

Giá hiện không có chiều bus type. Giai đoạn M31 có thể làm giá đa dạng **giữa operator và route**, trong khi các operator chuyên loại xe tạo trải nghiệm so sánh thực tế hơn. Nếu cần cùng operator/cùng journey giá limo khác standard, đây là **chức năng pricing mới** cần migration + DTO/quote/search/booking tests riêng; không lách bằng duplicate association/fare để đạt mục tiêu.

## 8. Proposed data expansion

### 8.1. Địa điểm và tuyến

Giữ 6 destination có ảnh; thêm **Phan Thiết, Quy Nhơn, Quảng Ngãi, Pleiku, Cần Thơ, Vũng Tàu** thành 12 nhãn điểm đến demo. Đây là đề xuất content, chưa xác minh điểm vận hành, địa giới, bến hoặc lịch hãng thật. Không ghi địa chỉ cụ thể tự bịa. Trong manifest đặt provenance `SIMULATED`/`VERIFIED_REFERENCE` kèm source/date nếu đã xác minh; schema hiện không có trường này, nên đặt trong manifest/tài liệu và nhãn mô tả phù hợp, không tự thêm column.

Một canonical location demo mỗi destination trong pilot. Dùng tên rõ “Điểm đón demo …” cho location mới, province/district theo content đã review; tọa độ để NULL nếu chưa xác minh. Giữ identity location cũ để không làm seeder collision hoặc mất mapping ảnh. Nếu muốn thêm “Demo” vào tên cũ cần quy trình đổi nội dung riêng và mapping frontend, không seed upsert. Không sửa bulk địa danh lịch sử để ép theo nhãn mới.

Đề xuất 12 cặp hai chiều = **24 directional routes**:

| Cặp hành trình demo | Vai trò |
| --- | --- |
| HCM ↔ Đà Lạt; HCM ↔ BMT; HCM ↔ Nha Trang; Đà Nẵng ↔ Huế | Giữ 8 direct routes hiện có và bổ sung lịch thật trong demo |
| HCM ↔ Cần Thơ; HCM ↔ Vũng Tàu; HCM ↔ Phan Thiết | So sánh chặng ngắn/vừa |
| Nha Trang ↔ Quy Nhơn; Quy Nhơn ↔ Đà Nẵng; Đà Nẵng ↔ Quảng Ngãi | Đa dạng hành trình miền Trung |
| BMT ↔ Pleiku; Đà Lạt ↔ Nha Trang | Kết nối cao nguyên/duyên hải |

Giữ COASTAL hiện hữu như compatibility fixture, không xóa record. Như vậy nếu giữ nguyên cả 9 route cũ, tổng catalogue sau mở rộng sẽ là **25 routes** (24 direct + 1 coastal), không phải 24 tổng. Chỉ 24 direct thuộc ma trận lịch mở rộng bắt buộc; coastal giữ lịch kiểm thử riêng hoặc không thêm slot mới sau khi review. Không đánh dấu inactive/đổi cấu trúc coastal đang có booking bằng seeder. Multi-stop mới chỉ thêm khi có mục đích demo segment reuse rõ; pilot có thể dùng coastal sẵn có.

Distance/duration dùng trường hiện có, là ước tính mô phỏng phải review hợp lý; không cần tra cứu bên ngoài ở audit này vì chưa đề xuất giá trị địa lý chính thức. Khi muốn nhãn verified, giai đoạn content phải dẫn nguồn đáng tin cậy và ngày kiểm chứng trước khi lưu/hiển thị. Offsets khởi đầu 0, tăng nghiêm ngặt, cuối bằng duration; fare đủ mọi forward pair được cho phép.

### 8.2. Nhà xe, xe và ảnh

Mặc định **giữ 3 operator hiện tại**; chỉ thêm tối đa 1 operator demo khi có nhu cầu chưa thể phục vụ bằng 3 nhà xe. Không tạo operator mới để đạt số lượng. Nếu cần nhà xe thứ tư, dùng tên hư cấu có chữ Demo, mã namespace `M31-DEMO-*`, email `.example`. Không mượn legal code/phone/logo của doanh nghiệp thật. Profile mô tả tuyến mẫu, loại xe, thanh toán mô phỏng, liên hệ demo không tiếp nhận yêu cầu vận chuyển. Contact tùy chọn để NULL nếu chưa có; account phone bắt buộc dùng định danh test được review, không quảng bá thành hotline có người trực.

Mỗi operator có 1 dedicated admin và 1 read-only staff; không đổi mật khẩu/role/membership cũ. Với 3 op, dự kiến 6 operator accounts (tối đa 8 nếu thực sự thêm operator thứ tư), nhưng nếu hiện trạng khác phải báo delta thay vì ép đủ bằng sửa record. Tạo admin còn thiếu của Tây Nguyên theo guard, không giả onboarded/legal history. Mỗi operator có fleet hợp lý theo vòng xe, một số chuyên sleeper hoặc limo; không cần mọi operator sở hữu mọi type.

Giữ 3 global layouts hiện hữu (22/34/40). **16–24 xe tổng gồm 7 xe cũ** là khoảng lập kế hoạch; phân bổ không cần đều giữa các operator. Từng bus có unique demo plate sau review format/identity, owner/type hợp lệ; một số xe dự phòng phục vụ maintenance/rehearsal, không tạo bus MAINTENANCE để rồi vẫn schedule.

Media theo cặp owner/type có bus thực sự vận hành: 1 logo/operator, 2–3 ảnh đúng layout/cặp, alt rõ “ảnh minh họa dữ liệu demo”. Không gán ảnh cabin đôi cho limo22 ghế 1+1. Managed UI hiện ghi “Ảnh do nhà xe cung cấp”; caption/alt hoặc demo banner cần làm rõ đây là bộ minh họa mô phỏng. Seeder không thay logo/gallery đã được người dùng chỉnh.

### 8.3. Lịch và giá

Lịch mục tiêu **20–30 departures/ngày tổng**, gồm cả các slot coastal nếu tiếp tục tạo. Không yêu cầu mọi directional route chạy mỗi ngày: các tuyến phụ có thể chạy luân phiên trong cửa sổ tối đa14 ngày. Ưu tiên lịch hai chiều, một vài tuyến trọng tâm có nhiều operator/giờ để Search so sánh; không nhân tất cả operator×route. Chốt associations sau khi kiểm chứng fleet/vòng xe, không đặt mục tiêu 32/33 associations. Có khung sáng/chiều/tối và overnight phù hợp.

Vòng xe ghi rõ origin/destination từng lượt, thời gian đến, buffer tối thiểu do manifest quy định (ví dụ 60–120 phút tùy hành trình demo), chuyến về hoặc ngày nghỉ. Không giả định bus tự trở về điểm xuất phát. Số xe cuối chốt bằng lịch vòng, giữ trong khoảng16–24 khi đáp ứng được lịch; giảm độ phủ/tần suất trước khi tăng fleet ngoài scope. Crew phân công riêng phải tránh overlap và đủ licence/capability; không mặc định crew An Phú hiện có phủ mọi chuyến mới.

Mức giá mẫu để thiết kế dataset: short-haul 100–180 nghìn; chặng vừa 180–320 nghìn; chặng dài/overnight 300–650 nghìn VND. **Đây là dải mô phỏng chưa phải giá vận hành/khảo sát thị trường**. Mỗi association/chặng có một giá >0; forward fare có thể không bằng tổng fare segment, không báo lỗi chỉ vì không cộng tuyến tính. Không in “từ …” thấp hơn giá active bookable thực tế. Search dùng giá gross; voucher chỉ quyết định net ở quote/booking.

### 8.4. Điểm đón/trả và supporting data

Stops là dữ liệu nền **trước khi tạo trip**, không đợi M31.5 mới thêm stop bắt buộc. Điểm đầu pickup-only, cuối dropoff-only, trung gian có flags theo kịch bản. Không tạo thêm pickup point ngoài itinerary rồi gắn nhầm fare vào route khác. Mỗi trip snapshot tham chiếu đúng stop nguồn, mỗi journey có fare và inventory đủ segments.

Không tuyên bố giờ pickup là giờ xe sẽ đến thực tế. Địa chỉ chưa xác minh chỉ dùng mô tả phạm vi demo; prebooking hiện không trả address. Có thể hoàn thiện location name/province/district/address và operator public_description trong các trường hiện hữu; `locations` không có cột description riêng. Gate, map pin, boarding instruction và verified address cần một thiết kế contract riêng nếu muốn hiển thị, không nhét HTML/text vào trường sai nghĩa.

### 8.5. Voucher và story vận hành

Khoảng **8 vouchers** trên vài operator demo: 2 public active (fixed/percent, một operator-wide và một association-specific), 1 private active nhập tay, 1 active minimum cao để từ chối quote, 1 future, 1 expired, 1 paused, 1 draft. Quota exhausted/per-customer dùng test clock và giao dịch API trong rehearsal riêng; không fake `voucher_usages` để tạo vẻ có khách thật. Code namespace rõ, không thêm platform-funded/stacking chưa hỗ trợ.

Public opt-in chỉ trong môi trường demo đã xác minh; không phát hành campaign giả trên môi trường có khách thật. Thời hạn voucher theo thời điểm đặt, không chỉ ngày khởi hành; điều kiện public không đồng nghĩa customer eligible. Không reset quota/extend voucher cũ sau khi có usage.

Chuẩn bị tùy chọn 12–20 bookings demo qua API/UI: unpaid PHONE, WEB pending, paid ticket, đổi ghế/chuyến, hủy một phần/toàn phần/refund, boarding/no-show, review sau completed. Tách rehearsal khỏi catalogue runner; không tự tạo payment/ticket/ledger bằng SQL. Không bắt buộc hoàn tất tất cả story mới cho phép catalogue expansion; báo coverage thực tế.

## 9. Proposed data volumes và hiệu năng

| Hạng mục | Pilot | Đích sau nghiệm thu | Lý do/giới hạn |
| --- | --- | --- | --- |
| Operators | 3 cũ + hoàn thiện quyền/nội dung | 3 mặc định, tối đa4 khi cần | Giữ operator hiện tại; chất lượng profile/lịch quan trọng hơn số cards |
| Destinations / canonical locations | 6 cũ + 2 mới | 12 / khoảng 12–18 location | Chỉ thêm bến phụ có mục đích; >20 location cần giải quyết lookup trước |
| Routes | 4 cặp có ảnh + coastal legacy | 24 directional direct + coastal legacy = 25 tổng dự kiến | Không gọi 24 là 24 cặp; không xóa route cũ |
| Operator-route | Giữ 13, thêm có chọn lọc | Theo route allocation của 3 operator; không quota cố định | Chọn một số tuyến cạnh tranh; không nhân mọi op×route |
| Bus types/templates | 3 / 96 | Giữ nguyên 3 / 96 | Không thêm bus type trong scope M31 đã chốt |
| Buses | Tính đủ cho pilot round trips | 16–24 tổng, gồm 7 cũ và dự phòng | Lịch vòng quyết định con số cuối |
| Future trips | 7 ngày, subset route | 20–30/ngày × tối đa14 = 280–420 slots nếu đủ14 ngày | Bao gồm coastal mới nếu có; legacy trips giữ nguyên, không xóa để ép tổng |
| Media | 3 logos + gallery mẫu | 3 logos (tối đa4) + 2–3 ảnh/cặp owner/type thực sự vận hành | Ví dụ 6 cặp ×3 +3 =21 ảnh, ≤300 KiB/ảnh: khoảng6,2 MiB, chưa gồm DB overhead |
| Voucher | 3–4 đủ flow | Khoảng8 | Chất lượng case tốt hơn số lượng |
| Rehearsal | 4–6 booking story | 12–20 optional | Qua API, không seed lịch sử giả |

Với trung bình32 ghế/trip và đủ14 ngày: 280–420 trips → khoảng **8.960–13.440 trip_seats**. Nếu tất cả direct, inventory cùng khoảng; trung bình1,5 segment/trip → khoảng **13.440–20.160 inventory rows**. Công thức đúng là tổng seat_count×segment_count của từng trip; đây chỉ là ước lượng capacity, không benchmark. Fare rows cho n-stop route tối đa n(n−1)/2 mỗi association nếu mọi forward journey được mở bán.

Query search dùng window function theo trip và correlated availability/ratings/promotions; nhiều stop pairs làm ứng viên tăng nhanh. Chưa có đo tải mới. M31.6 đo 10/100/280–420 trips, ngày cao điểm, trang2+, filter kết hợp và gallery; ghi p50/p95 và SQL plans trên môi trường xác minh. Mục tiêu kỹ thuật đề xuất cho demo local: p95 search ≤1s sau warm-up, không N+1 gallery theo từng card cùng operator, không tải blob trong list. Nếu không đạt, giữ pilot và tối ưu có bằng chứng; không thêm index vô điều kiện.

## 10. Safe seeding architecture

### 10.1. Lựa chọn

| Cách | Đánh giá |
| --- | --- |
| Nhồi toàn bộ vào DemoDataSeeder | Ít entry point nhưng file hiện đã lớn, identity rất chặt; dễ làm regression M15 và thay đổi fixture toàn bộ test |
| **Module M31 + manifest versioned, reuse production aggregate/guards** | Khuyến nghị: tách catalogue/schedule/media/rehearsal, có report; giữ create-only contract. Không cần framework/import platform mới |
| SQL import trực tiếp | Không ưu tiên: vượt owner/layout/maintenance/commerce guard, khó tạo aggregate/snapshot đúng |
| Script gọi API có kiểm soát | Phù hợp media và rehearsal; cần xác minh stack/schema độc lập, auth đúng role và tránh retry booking mù. Không coi health/localhost là bằng chứng đúng DB |

Đề xuất module dưới `backend/src/main/java/com/busgo/demo/m31/` gồm manifest/specs, planner/validator và runner. Tên file/module là **đề xuất**, chưa tạo. Catalog runner dùng explicit profile/flag, không auto-on trong `dev`. Khi bật M31, chỉ một coordinator tạo lịch; không để legacy runner và M31 chạy hai bộ slot cạnh tranh không có thứ tự. Có thể reuse catalogue legacy create-only, nhưng schedule M31 thay cho legacy schedule trong profile mới, giữ profile demo cũ tương thích/test cũ.

### 10.2. Hợp đồng an toàn bắt buộc

1. Profile chỉ demo/dev, loại trừ prod/production như hiện có; thêm opt-in M31 false mặc định và xác minh database target theo allowlist môi trường đã review. Không bật bằng migration. Dry-run là mặc định của công cụ nhập thủ công; dry-run không khởi động ApplicationRunner có side effect.
2. Manifest version/hash, reserved stable keys (operator code, user email, bus plate, composite location/route identity và cấu trúc). Resolve generated IDs, không hardcode PK hoặc dùng insert order làm định danh.
3. Preflight schema V28/checksum và hiện trạng chỉ đọc; phân biệt matching/reuse, collision, missing. Cùng tên nhưng không đủ bằng chứng identity thì fail/report; không nhận quyền sở hữu record cũ chỉ nhờ prefix hoặc structural match.
4. Create-only, không update identity/status/password/fare/gallery/profile người dùng đã sửa. Với nội dung thiếu trên reserved operator cũ, không tự điền bằng mutable upsert; dùng thao tác quản trị explicit có report hoặc chỉ tạo nội dung đầy đủ cho records mới. Không xóa/reset gì.
5. Không delete/overwrite booking/payment/ticket/hold/voucher usage/refund/history. Inventory của trip reused giữ nguyên, kể cả BLOCKED được operator mở lại. Không tự gán crew vào trip cũ đã thay đổi.
6. Trip mới qua `TripAggregateCreator`, bus lock/overlap/owner/maintenance/type/layout/route/operator checks; đủ aggregate hoặc rollback. Không chỉ insert trips. Recheck sau lock, tuần tự hóa runner cùng môi trường (không coi lookup slot là unique guarantee).
7. Catalogue preflight toàn bộ; transaction phù hợp từng phase/batch hữu hạn, không khóa hàng nghìn trip trong một transaction startup. Có summary created/reused/skipped/conflict, không log secrets/contact khách. Lỗi không tạo aggregate dở dang; partial batch đã commit phải được report và rerun an toàn.
8. Media qua validator/service hiện có hoặc import adapter có cùng validation/lock/quota. Reuse dựa trên manifest receipt và metadata/hash đã xác minh; không upload lặp mỗi startup, không overwrite logo do user sửa. UUID/path managed do server tạo; static URL không tự trở thành V28 media.
9. Voucher qua đúng owner và service, giữ immutable terms/usage; activation/visibility explicit trong demo. Story commerce dùng API riêng, không replay POST booking khi không rõ kết quả: đọc history trước.
10. In report ngày tìm kiếm, location IDs đã resolve, operator, slot, skip reason, số rows aggregate và cửa sổ; không báo thành công đồng nghĩa bookable. Acceptance đọc API thật sau seed.

### 10.3. Lịch tương đối ngày hiện tại, không nhân bản vô hạn

Khuyến nghị **cửa sổ hữu hạn có anchor explicit** cho M31: khi chuẩn bị demo chọn `anchorDate` và horizon tối đa14 ngày (business zone VN), lưu receipt run/manifest trong ignored demo artifact; rerun dùng lại anchor đó, không ngầm đổi ngày mỗi restart. Slot key ổn định là schedule spec + departure business date, resolve về operator-route/bus/departure. Exact existing trip reuse bất kể lifecycle; collision/overlap skip/report. Bus assignment deterministic, không đổi bus để lách collision tạo thêm trip.

Cho hôm nay chỉ tạo slot chưa qua và có lead time được định nghĩa (ví dụ ≥2h); ngày mai..anchor+13 đầy đủ. Không backdate. Clock inject để test boundary UTC/VN/overnight. Rerun khi anchor đã cũ chỉ báo readiness giảm; thao tác “prepare next demo window” explicit mới mở một cửa sổ khác có trần số slot và report delta.

Không có automatic daily scheduler trong baseline M31. Bởi vậy **không có tăng trưởng vô hạn do app restart**. Nếu sau này chọn rolling refill tự động, dù future window chỉ14 ngày thì tổng lịch sử vẫn tăng mỗi ngày nếu không xóa; phải nói rõ điều này, đặt quota tổng trips demo và dừng/report khi chạm quota, không purge giao dịch. Không có giải pháp vừa giữ toàn bộ lịch sử vừa giới hạn tổng DB cố định bằng cách chỉ cắt future window.

Receipt file không phải quyền sửa DB: mất receipt vẫn lookup/create-only và fail collision, không re-upload/ghi đè để “repair”. Có thể bổ sung provenance table bằng migration tương lai nếu cần quản lý nhiều môi trường, nhưng không bắt buộc cho pilot và không thực hiện ở M31.1.

## 11. M31.2–M31.6 implementation roadmap

Thứ tự dependency: location → route + stops → operator/account → type/template/bus → association + fares → trip snapshots/inventory → media/voucher/rehearsal. Labels milestone giữ như yêu cầu; stops tối thiểu và fares bắt buộc phải sẵn trước trip, không đợi supporting-data phase.

| Giai đoạn | Mục tiêu / dữ liệu | File/module dự kiến | Rủi ro | Tiêu chí hoàn thành |
| --- | --- | --- | --- | --- |
| **M31.2 — Locations & Routes** | Chốt manifest, pilot location/cặp route và stops, provenance/nhãn demo; chuẩn bị 12-destination/24-direct-route matrix | Mới `demo/m31` manifest/planner; docs demo data/M31; LocationService/Controller và discovery frontend chỉ khi sửa lookup A01 có scope review | Trùng natural keys, đổi tên mất mapping, location API chỉ20, route sai endpoints | Dry-run có delta/collision; location IDs được API nhận; stops đúng order/offset/flags; 6 asset destinations giữ mapping; rerun không trùng; không ghi người dùng. Không tạo trip |
| **M31.3 — Operators & Vehicles** | Dedicated accounts cho op thiếu/new; 3 profiles demo (tối đa4 nếu cần); 3 layouts; fleet vòng xe, dự phòng; association theo matrix | `demo/m31`, operator/user repositories/context, fleet modules khi cần integration; M30.5 media adapter/UI có thể chuẩn bị nhưng chưa upload lặp | Mixed role/tenant, plate collision, bus_type global, thiếu quyền Tây Nguyên | Đúng owner và một membership context/account; admin/staff phân quyền; active layout count/geometry; xe không overlap theo plan; description/contact demo rõ; preserve credentials/status |
| **M31.4 — Schedules & Pricing** | Fares đủ forward pairs trước trip; pilot7 ngày rồi14 ngày; hai chiều, sáng/chiều/tối/overnight, 20–30/day tổng, tối đa14 ngày, phù hợp16–24 xe | M31 schedule planner/runner + TripAggregateCreator/repositories sử dụng lại; docs/clock tests riêng | Pricing không per-type, bus reposition/maintenance, seed legacy chạy cạnh tranh, cửa sổ tự kéo dài | Same-anchor rerun 0 new; future slots đúng VN; arrival/overnight/buffer/vòng xe hợp lý; đủ seats×segments; search chiều về/đa op; quote/booking giá khớp; conflict skip giữ nguyên |
| **M31.5 — Pickup/Dropoff & Supporting Data** | Hoàn thiện nhãn point, media đúng owner/type, employee/crew, voucher matrix, rehearsal tùy chọn | M31 content/import adapter; `media`, `promotion`, `operations` APIs sử dụng lại; frontend mapping/caption nếu cần; docs rehearsal | Thay route cũ không đổi snapshots; caption ảnh giả thành thật; quota/privacy; fake finance | Không sửa snapshots/trades cũ; media persist và public guard đúng; vouchers public/private/invalid đúng scope; không gửi mail thật; story tạo qua workflow có receipt, tiền mô phỏng |
| **M31.6 — Data Integration & Acceptance** | Đếm DB thật, quality queries, FE→API→MySQL coverage, pagination/performance và report final | M31 acceptance report/tests riêng; query/frontend chỉ sửa các hạn chế đã chứng minh; không rewrite M29/M30 | Historical evidence bị coi là rerun; cũ fixtures fragile; policy status A05; finance EXCEPTIONS | Home6 assets/links; search đầy đủ trang và filters; Trip/hold/quote/booking/pay/ticket; return/overnight/segment reuse; operator/admin đúng quyền; source-vs-live counts rõ; repeat preservation pass; ghi giới hạn còn lại |

Pilot nên dùng 4 cặp route cũ có ảnh trước, rồi mở 8 cặp mới; không tăng đồng thời mọi chiều dữ liệu. Bổ sung integration tests riêng cho create-only/collision/clock/overlap/preservation và inventory completeness; không sửa assertions cũ chỉ để hợp dataset mới.

## 12. Risks and dependencies

- **DB chưa đọc:** trước M31.2 phải xác nhận instance/schema/backup và đọc count/quality; không tự start launcher để “audit”, vì profile startup có tác dụng ghi. Cổng launcher3308 khác manual3307; schemas M29/M30 ở báo cáo cũ không phải DB đích mặc định.
- **Compose/MySQL:** `docker-compose.yml` dùng mysql8.4, bind loopback, persistent volume; launcher dùng project/schema riêng và `scripts/local/mysql-demo.yml` bật trust-function-creators cho trigger migrations. Không xóa volume, không đổi password để sửa account đã init. `ddl-auto=validate`, Flyway enabled; startup không phải read-only.
- **Seeding compatibility:** seeder cũ kiểm tra cả identity/structure/image URL; không đổi fields legacy bằng seed M31 rồi để startup demo cũ fail collision. Phải điều phối profile/runner rõ ràng.
- **Content:** chưa xác minh địa giới/bến/phone/operator identity. Giữ demo labels; nếu bổ sung dữ liệu thật phải có nguồn và quyền sử dụng ảnh. Không hứa hành trình/amenity dựa vào ảnh hoặc seat_type STANDARD.
- **Business rules:** không làm per-type pricing bằng duplicate fare; không lấy total paid làm occupancy/boarding; không coi pending booking là chưa giữ ghế; giữ ticket/payment/attendance tách nghĩa.
- **Transaction/retry:** không ghi raw giao dịch; không replay booking mù; finance events/statements immutable; không “sửa” lịch sử để report đẹp. Quota/reconciliation phải dựa source thật trong demo.
- **M30 evidence:** M30.6 báo backend67 unit/545 integration và FE193 pass lịch sử; không chạy lại ở task này. Báo cáo còn P2 browser suite M15/M15.5/operator-cleanup và kỳ finance EXCEPTIONS chưa chốt. M31 không được tuyên bố các vấn đề đó đã giải quyết chỉ bằng thêm data, cũng không cần làm lại toàn bộ M29/M30.
- **Hiệu năng:** correlated query, bounded lists, blobs/no-store và allPages có chi phí; đo bộ target trước khi tăng. Không xem 280–420 trips là quy mô production được chứng minh.

### Phụ lục A — SQL read-only để xác minh sau này

Chỉ chạy trên kết nối/schema đã xác nhận. Các câu dưới đây không được thực thi trong M31.1. Dùng transaction consistent snapshot read-only cho một lần kiểm kê; không gọi procedure, không khóa ghi. `information_schema.TABLE_ROWS` không phải count chính xác của InnoDB.

```sql
SELECT DATABASE() AS schema_name, @@hostname AS host_name, @@port AS port,
       @@version AS mysql_version, UTC_TIMESTAMP(6) AS audited_at_utc;
SELECT version, description, success FROM flyway_schema_history
ORDER BY installed_rank;

START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;

SELECT 'locations' AS table_name, COUNT(*) AS actual_rows FROM locations
UNION ALL SELECT 'transport_operators', COUNT(*) FROM transport_operators
UNION ALL SELECT 'users', COUNT(*) FROM users
UNION ALL SELECT 'operator_staff', COUNT(*) FROM operator_staff
UNION ALL SELECT 'bus_types', COUNT(*) FROM bus_types
UNION ALL SELECT 'seat_templates', COUNT(*) FROM seat_templates
UNION ALL SELECT 'buses', COUNT(*) FROM buses
UNION ALL SELECT 'routes', COUNT(*) FROM routes
UNION ALL SELECT 'route_stops', COUNT(*) FROM route_stops
UNION ALL SELECT 'operator_routes', COUNT(*) FROM operator_routes
UNION ALL SELECT 'operator_route_fares', COUNT(*) FROM operator_route_fares
UNION ALL SELECT 'trips', COUNT(*) FROM trips
UNION ALL SELECT 'trip_stops', COUNT(*) FROM trip_stops
UNION ALL SELECT 'trip_segments', COUNT(*) FROM trip_segments
UNION ALL SELECT 'trip_seats', COUNT(*) FROM trip_seats
UNION ALL SELECT 'trip_seat_segment_inventory', COUNT(*) FROM trip_seat_segment_inventory
UNION ALL SELECT 'operator_media', COUNT(*) FROM operator_media
UNION ALL SELECT 'bookings', COUNT(*) FROM bookings
UNION ALL SELECT 'booking_items', COUNT(*) FROM booking_items
UNION ALL SELECT 'payments', COUNT(*) FROM payments
UNION ALL SELECT 'tickets', COUNT(*) FROM tickets
UNION ALL SELECT 'refunds', COUNT(*) FROM refunds
UNION ALL SELECT 'operator_vouchers', COUNT(*) FROM operator_vouchers
UNION ALL SELECT 'voucher_usages', COUNT(*) FROM voucher_usages;

-- Không có chuyến nào; phân biệt với không có chuyến future bookable.
SELECT r.id, r.name FROM routes r
WHERE NOT EXISTS (SELECT 1 FROM operator_routes o JOIN trips t
  ON t.operator_route_id=o.id WHERE o.route_id=r.id);

SELECT o.code, COUNT(b.id) AS nondeleted_buses
FROM transport_operators o LEFT JOIN buses b
  ON b.operator_id=o.id AND b.deleted_at IS NULL
GROUP BY o.id, o.code;

SELECT DATE(DATE_ADD(t.departure_time, INTERVAL 7 HOUR)) AS departure_date_vn,
       o.code, r.name, COUNT(*) AS departures
FROM trips t JOIN operator_routes a ON a.id=t.operator_route_id
JOIN transport_operators o ON o.id=a.operator_id JOIN routes r ON r.id=a.route_id
GROUP BY departure_date_vn, o.id, o.code, r.id, r.name
ORDER BY departure_date_vn, o.code, r.name;

-- Layout count và vị trí trùng, không chỉ nhìn seat_count.
SELECT bt.id, bt.name, bt.seat_count, COUNT(s.id) AS active_template_count
FROM bus_types bt LEFT JOIN seat_templates s ON s.bus_type_id=bt.id AND s.active=TRUE
GROUP BY bt.id, bt.name, bt.seat_count
HAVING bt.seat_count<=0 OR COUNT(s.id)<>bt.seat_count;
SELECT bus_type_id, floor_no, row_no, column_no, COUNT(*) AS duplicates
FROM seat_templates WHERE active=TRUE
GROUP BY bus_type_id, floor_no, row_no, column_no HAVING COUNT(*)>1;

-- Ownership/time và schedule overlap. Không coi đổi status bus sau trip là lỗi tự động.
SELECT t.id, b.operator_id AS bus_owner, a.operator_id AS trip_owner
FROM trips t LEFT JOIN buses b ON b.id=t.bus_id
LEFT JOIN operator_routes a ON a.id=t.operator_route_id
WHERE b.id IS NULL OR a.id IS NULL OR b.operator_id<>a.operator_id
   OR t.estimated_arrival_time<=t.departure_time;
SELECT a.id AS first_trip, b.id AS second_trip, a.bus_id
FROM trips a JOIN trips b ON a.bus_id=b.bus_id AND a.id<b.id
WHERE a.status<>'CANCELLED' AND b.status<>'CANCELLED'
  AND a.departure_time<b.estimated_arrival_time
  AND a.estimated_arrival_time>b.departure_time;

-- Exact fare scope, duplicate natural keys và reverse catalogue coverage.
SELECT f.id FROM operator_route_fares f
JOIN operator_routes a ON a.id=f.operator_route_id
JOIN route_stops p ON p.id=f.from_route_stop_id
JOIN route_stops d ON d.id=f.to_route_stop_id
WHERE p.route_id<>a.route_id OR d.route_id<>a.route_id
   OR p.stop_order>=d.stop_order OR f.price<=0;
SELECT operator_route_id, from_route_stop_id, to_route_stop_id, COUNT(*) AS duplicates
FROM operator_route_fares
GROUP BY operator_route_id, from_route_stop_id, to_route_stop_id HAVING COUNT(*)>1;
SELECT name, province, COUNT(*) AS duplicates FROM locations
GROUP BY name, province HAVING COUNT(*)>1;
SELECT name, origin_location_id, destination_location_id, COUNT(*) AS duplicates
FROM routes GROUP BY name, origin_location_id, destination_location_id HAVING COUNT(*)>1;
SELECT r.id, r.name FROM routes r WHERE NOT EXISTS
 (SELECT 1 FROM routes back_route WHERE back_route.origin_location_id=r.destination_location_id
  AND back_route.destination_location_id=r.origin_location_id);

-- Missing inventory: trả từng cặp còn thiếu, không giả AVAILABLE.
SELECT t.id AS trip_id, s.id AS seat_id, g.id AS segment_id
FROM trips t JOIN trip_seats s ON s.trip_id=t.id
JOIN trip_segments g ON g.trip_id=t.id
LEFT JOIN trip_seat_segment_inventory i ON i.trip_seat_id=s.id AND i.trip_segment_id=g.id
WHERE i.id IS NULL;
SELECT i.id FROM trip_seat_segment_inventory i
JOIN trip_seats s ON s.id=i.trip_seat_id JOIN trip_segments g ON g.id=i.trip_segment_id
WHERE s.trip_id<>g.trip_id;

SELECT operator_id, bus_type_id, category, COUNT(*) AS images,
       SUM(OCTET_LENGTH(content)) AS stored_bytes
FROM operator_media GROUP BY operator_id, bus_type_id, category;
SELECT operator_id, operator_route_id, state, customer_visible,
       COUNT(*) AS vouchers, MIN(starts_at) AS earliest_start, MAX(ends_at) AS latest_end
FROM operator_vouchers GROUP BY operator_id, operator_route_id, state, customer_visible;

COMMIT;
```

Các SELECT này là baseline quality checks, không thay thế public query acceptance. Reverse endpoint match chưa chứng minh reverse itinerary đầy đủ; so sánh snapshot với template hiện tại cần xét lịch sử. “Giá bất thường” cần benchmark demo đã duyệt, không gắn lỗi chỉ vì khác dải đề xuất. Với trip status và bus/operator status, trước tiên phân biệt lifecycle lịch sử với điều kiện mở bán hiện tại.

## 13. Recommended next task

**Review M31.1, sau đó M31.2 — Locations & Routes pilot + manifest và dry-run create-only.** Trước khi ghi dữ liệu, xác minh database đích và inventory thật, chốt 12 destination/24 direct-route matrix, giữ coastal legacy, quyết định lookup khi locations vượt20 và cơ chế anchor/profile M31. Không mở rộng pricing schema hoặc production backend chỉ để đạt số lượng.

M31.1 hoàn tất ở mức audit source và thiết kế có giới hạn bằng chứng nêu trên; live DB counts/quality/API acceptance vẫn chưa xác minh. Chỉ tài liệu được tạo; dừng tại đây cho người dùng review.
