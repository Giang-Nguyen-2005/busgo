# M31.6 · Bàn giao fixture dữ liệu và demo local

**Kết luận ngày 08/11/2026:** bộ dữ liệu đủ để demo tìm chuyến, xem ảnh, báo giá voucher và phân quyền customer/operator/admin trong lịch 14–27/11/2026. Các phép kiểm chứng dùng MySQL test `busgo_m312_test` trên cổng 3352. Database ứng dụng không nằm trong phạm vi nghiệm thu.

## Dữ liệu đã tích hợp

| Giai đoạn | Kết quả |
| --- | --- |
| M31.1 | Audit nguồn dữ liệu, lịch legacy, thiếu hụt catalogue và kế hoạch mở rộng. [Báo cáo audit](m31-1-data-audit-and-expansion-plan.md) |
| M31.2 | Catalogue có 12 địa điểm, 24 tuyến trực tiếp và 1 tuyến COASTAL. Tra cứu địa điểm theo ID hỗ trợ link Home → Search mà không phụ thuộc trang đầu của autocomplete. [Catalogue](m31-2-locations-routes.md) |
| M31.3 | Ba nhà xe vận hành 19 xe thuộc 3 loại, với 96 mẫu ghế. Giữ tài khoản/xe legacy và bổ sung tài khoản phục vụ từng nhà xe. [Đội xe](m31-3-operators-fleet.md) |
| M31.4 | 364 chuyến mới, 26 lượt/ngày × 14 ngày, cộng 21 chuyến legacy thành 385. [Lịch và giá](m31-4-schedules-pricing.md) |
| M31.5 | Media theo nhà xe/loại xe, voucher theo phạm vi và thời hạn, nhân sự/crew cho các chuyến mẫu. Hai booking đã thanh toán thuộc các milestone trước. [Dữ liệu hỗ trợ](m31-5-supporting-data.md) |
| M31.6 | Nghiệm thu qua frontend 5186 → backend 8186 → MySQL 3352, giữ sửa lỗi tên tuyến, hoàn thiện hướng dẫn khởi động/dừng. [Hướng dẫn demo](m31-local-demo-guide.md) |

**Counts fixture.** Snapshot M31.5 ghi nhận 52 route stops, 29 liên kết nhà xe–tuyến, 34 fares, 776 trip stops, 391 trip segments, 12.886 ghế chuyến, 8 nhân viên và 12 phân công crew. Snapshot M31.6 ghi nhận 3 nhà xe, 19 xe, 3 loại xe, 12 địa điểm và 25 tuyến. Khi chốt đã đối chiếu lại 385 chuyến, 11 media, 6 voucher, 2 booking, 2 payment, 2 ticket và 10 user. Hai user tăng so với M31.5 tương ứng với customer dành cho buổi demo và admin bootstrap đã được chuẩn bị trước khi tiếp tục. Sau release, inventory có 12.986 AVAILABLE, 102 BLOCKED, 2 BOOKED và 0 HELD. Flyway có 28 migration thành công, gồm V28. M31 không thêm migration.

## Kết quả kiểm chứng

| Phạm vi | Mức bằng chứng | Kết quả |
| --- | --- | --- |
| Home, discovery, media | Browser live từ lượt trước và API fixture thật | **PASS.** Ba nhà xe và logo managed hiển thị. API tải được bytes logo/ảnh. Gallery public có 2, 3 và 3 ảnh xe theo thứ tự An Phú, Minh Thành và Tây Nguyên; cộng 3 logo thành 11 media. |
| Search | HTTP thật, receipt trước interruption | **PASS.** 8 hành trình × 3 ngày (14, 20, 27/11), đúng số chuyến, giá và giờ so với lịch đã nạp. HCM → Đà Lạt có 2 lựa chọn, sort và phân trang size 1 trả lần lượt 250.000đ và 300.000đ. Đã kiểm tra lọc giá/giờ. |
| Trip và checkout | Browser live từ lượt trước | **PASS.** TP.HCM → Phan Thiết ngày 14/11, chuyến 335 của An Phú, ghế ngồi 40 chỗ, khởi hành 06:00 giờ Việt Nam. Giá 180.000đ, quote với `M31-AP-FIX20` còn 160.000đ. Đã release hold. Kiểm tra mobile 390 px trước đó không thấy tràn ngang. |
| Promotions | HTTP và Home live | **PASS.** Ba mã công khai là `M31-AP-FIX20`, `M31-AP-MIN50`, `M31-MT-PCT10`. Mã riêng, mã chưa đến ngày và mã đã tạm dừng không xuất hiện trong danh sách public tại thời điểm kiểm tra. |
| Operator | Edge, đăng nhập thật từng tài khoản | **PASS.** Bảng dưới đối chiếu fleet hiển thị, chuyến ngày 14/11 và bus của chuyến với fleet của nhà xe đã đăng nhập. An Phú mở được dispatch, sơ đồ loại xe chỉ đọc, thư viện ảnh và voucher. |
| Operator staff | Edge + HTTP cùng session | **PASS.** Nhân viên An Phú xem được danh sách chuyến. DELETE media bằng UUID không tồn tại nhận 403; không dùng ảnh thật làm mục tiêu xóa. |
| SYSTEM_ADMIN | Edge + đối chiếu MySQL | **PASS.** Tài khoản fixture có role SYSTEM_ADMIN. Danh sách hiển thị ba nhà xe, mở được từng hồ sơ, tổng xe và tổng chuyến khớp dữ liệu phía server. |
| Dispatch hover | Edge trên dữ liệu thật | **PASS.** Chuyến 335 cho AVAILABLE, BOOKED, HELD; chuyến 223 có BLOCKED thật. Giữ tạm một ghế tầng 1 để kiểm tra HELD và release trong `finally`. Keyboard focus hiển thị, không phát sinh booking, không sửa CSS. |
| routeName | Edge live + 2 focused test | **PASS.** Từ link Home HCM → Đà Lạt đổi điểm trả sang Phan Thiết. URL chuyển sang ID 261 → 269, bỏ tên tuyến cũ, hiển thị kết quả chuyến 335. Back quay lại URL gốc đúng với endpoints cũ; Forward trở lại kết quả mới. Thay riêng ngày vẫn giữ tên tuyến hợp lệ. |
| Build | M31.6 | **PASS.** Production build TypeScript/Vite và 2/2 test `m31-6-journey.test.mjs`. Backend JAR đã package thành công trước interruption, mới hơn source backend gần nhất; không chạy lại backend suite. `git diff --check` sạch. |

| Nhà xe | Xe trong fleet | Chuyến mẫu thuộc fleet | Tổng chuyến ở hồ sơ admin |
| --- | ---: | ---: | ---: |
| An Phú Express | 7 | 335 | 121 |
| Minh Thành Limousine | 6 | 363 | 118 |
| Tây Nguyên Travel | 6 | 503 | 146 |

Màu nền trước/sau hover giống nhau ở cả bốn trạng thái: AVAILABLE `#dcf2e8`, BOOKED `#dce9ff`, HELD `#fff2cb`, BLOCKED `#edf0f2`. Hold kiểm tra hover đã được trả lại. Fixture vẫn có đúng hai giao dịch lịch sử, không tạo booking/payment/ticket mới trong M31.6.

## Lỗi đã sửa và giới hạn

Link discovery trước đây có thể mang `routeName` cũ khi người dùng thay đầu hoặc cuối hành trình. `journeyParams` hiện bỏ tên tuyến khi một endpoint thay đổi, vẫn giữ các tham số độc lập như nhà xe. Tên địa điểm được tra theo đúng ID. Hai test tập trung bảo vệ cả trường hợp đổi endpoint lẫn chỉ đổi ngày.

**NOT TESTED trong M31.6:** thanh toán/hoàn tiền end-to-end mới, vận hành database ứng dụng, toàn bộ regression M30, tải production, thiết bị cảm ứng thật và đối soát M27A. Kết quả lịch sử của M30 hoặc M31.2–M31.5 chỉ áp dụng cho lượt chạy đã ghi ở các báo cáo tương ứng. Lịch, voucher và ảnh là dữ liệu demo cố định; ảnh theo loại xe trong từng nhà xe, không xác nhận tiện nghi từng biển số. Không có email hoặc thanh toán thật.

## Checklist bàn giao và phạm vi Git

- Dùng lại container `busgo-m312-catalogue-mysql-1`, project `busgo-m312-catalogue`, volume `busgo-m312-catalogue_m312_data`. Cấu hình tiếp tục demo tắt Flyway, seeder, bootstrap admin và mail. Xem lệnh và tài khoản trong hướng dẫn local.
- Đã kiểm tra vòng dừng → khởi động lại → health qua proxy → dừng. Launcher local đợi Docker báo MySQL healthy trước khi đọc baseline. Khi bàn giao, container ở trạng thái exited, các cổng 5186/8186/3352 không còn listener và volume vẫn tồn tại.
- Bằng chứng cục bộ ở `.tools/m31-6/api-smoke.json`, `browser/*-verified.json`, ảnh chụp browser và log build. Config đăng nhập, mật khẩu, JWT, log, JAR và database đều ngoài danh sách commit.
- Phần backend để review: `demo/m31/` (catalogue, fleet, schedule, support và các runner/service), các test `M31*Test.java` và `M31*IT.java` trong package demo; thay đổi có phạm vi hẹp ở location/security, RouteRepository và TripRepository.
- Phần frontend: `CustomerMedia.tsx`, `presentation.ts`, `Marketplace.tsx`, `SearchForm.tsx`, fixture trong `m29-customer-browser.cjs` và ba suite `m31-2-catalogue.test.mjs`, `m31-5-supporting-media.test.mjs`, `m31-6-journey.test.mjs`.
- Tài liệu: năm báo cáo M31.1–M31.5, báo cáo này và hướng dẫn demo local. Working tree giữ nguyên trên `main` tại `df8f0b7` để review.
