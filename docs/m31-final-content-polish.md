# M31 — Hoàn thiện nội dung website

Ngày kiểm tra 09/11/2026. M31 đang chờ người dùng review nội dung trước khi commit.

## Phạm vi và kết quả

Tiếp tục working tree M31.1–M31.6. Lượt trước đã có các helper displayCopy và presentation đang viết dở. Lượt này hoàn thiện mapping và áp dụng vào giao diện. Các thay đổi backend trong working tree thuộc M31 trước đó. Lượt content polish chỉ sửa frontend và tài liệu.

| Nội dung trước | Hiển thị sau |
| --- | --- |
| Demo · TP. Hồ Chí Minh → Phan Thiết | TP. Hồ Chí Minh → Phan Thiết |
| Điểm đón demo Phan Thiết | Phan Thiết |
| DEMO · M31-AP-FIX20 trong tiêu đề | Giảm 20.000đ |
| [DEMO M31] Ảnh minh họa | Ảnh minh họa loại xe, kèm tên loại xe |
| Mô tả nhà xe dài về DB/demo | Giới thiệu ngắn về các tuyến và loại xe trong catalogue |
| Nhân sự demo AP DRIVER | Tài xế · An Phú Express |
| DEMO-NOT-DIALABLE | Chưa cập nhật |
| Hạng giấy phép DEMO trong thẻ nhân sự | Chưa cập nhật |
| Logo có tooltip và alt lặp demo | Logo kèm tên nhà xe |
| Dự án học tập · Thanh toán mô phỏng | Thông báo phiên bản giới thiệu và giới hạn sử dụng tại footer |

Mapping khớp chính xác tên gốc của catalogue, không xóa từ khóa tùy tiện khỏi nội dung do người dùng nhập. Tên tuyến có 16 hướng và sáu địa điểm bổ sung được ánh xạ tại presentation. Tên nhà xe dùng ba mô tả gốc chính xác. Tiêu đề voucher chỉ ánh xạ sáu mã đã biết khi tiêu đề gốc khớp. ID, mã, payload nghiệp vụ, lịch và giá tiếp tục lấy từ API.

Home, destination/route cards, Search và autocomplete, Trip, thông tin trước đặt vé, Checkout, lịch sử/chi tiết đặt vé, vé QR, ưu đãi, hồ sơ nhà xe và các mục Operator có nhãn hành trình đều dùng nhãn hiển thị. Operator profile và media editor hiển thị nội dung đã làm gọn, đồng thời gửi lại giá trị gốc khi người dùng lưu mà chưa sửa trường đó. Các form quản trị khác tiếp tục giữ dữ liệu gốc để người quản trị kiểm tra và chỉnh sửa chính xác.

Banner có artwork giảm 20% chưa xác minh được ẩn cả ở Vite development, tương tự chính sách production hiện có. Hai card khám phá còn lại giữ thiết kế và liên kết. Không thêm thông tin vận hành, địa chỉ, hotline, giấy phép hay review.

## Minh bạch giao dịch

Footer công khai rằng nhà xe/lịch chạy là minh họa, vé và thanh toán không dùng cho chuyến đi thực tế. Bỏ cảnh báo lặp trên Home. Checkout có cảnh báo dễ thấy trước nút tiếp tục. Payment giữ cảnh báo thanh toán mô phỏng sẵn có. QR có dòng “QR minh họa · Không dùng để lên xe” cạnh mã. Các trang thanh toán và vé vẫn thể hiện giới hạn của giao dịch.

Mã voucher M31-AP-FIX20 và các mã khác vẫn hiển thị nguyên vẹn để nhập/sao chép. Email tài khoản, mã nhân sự, biển số, thông tin chỉnh sửa quản trị, fixtures, manifest và tài liệu kỹ thuật vẫn giữ nguyên. Tên tài khoản khách test trong ô thông tin liên hệ được giữ nguyên vì đây là thông tin tài khoản có thể chỉnh sửa, không phải copy thương mại.

## Kiểm chứng đã chạy

- Production build cuối PASS với TypeScript và Vite, 2.235 modules. Có cảnh báo Rollup về annotation trong zod và import notification vừa static vừa dynamic. Build hoàn tất, bundle entry khoảng 487,79 kB, gzip 156,31 kB.
- Frontend tests cuối PASS 198/198. Hai assertion alt logo được cập nhật theo copy mới, vẫn giữ kiểm tra exact identity và ưu tiên URL API.
- git diff --check PASS sau chỉnh sửa.
- Browser Chrome thật qua Playwright ở 1440×900 và 390×900 cho Home, Search, Trip 335, hồ sơ An Phú Express, danh sách chuyến Operator và danh sách nhà xe Admin. Không phát hiện prefix Demo ·, Điểm đón demo, [DEMO M31], DB test hoặc Nhân sự demo trong nội dung các trang đã mở. Không tràn ngang viewport trên các trang này.
- Checkout được mở ở cả hai kích thước bằng một hold tạm cho chuyến 335. Cảnh báo giao dịch hiển thị và không tràn ngang. Hold đã DELETE thành công sau kiểm tra. Không tạo booking mới.
- API quote giữ đúng grossAmount 180000, discountAmount 20000, totalAmount 160000 cho M31-AP-FIX20. API vẫn trả tên gốc kỹ thuật, UI áp dụng mapping riêng. Xem checkout-results.json.
- Search và Trip dùng nguyên pickupLocationId=261, dropoffLocationId=269, tripId=335. Chưa chạy lại toàn bộ hành trình bấm từ Home qua chọn ngày sang Trip trong lượt này. Test carousel xác nhận liên kết giữ ID và test journey xác nhận xử lý routeName khi đổi điểm.
- Xác minh Start.ps1 trước khởi động có đúng project/container/volume, cổng loopback 3352, schema busgo_m312_test và baseline V28/385 chuyến. MySQL cuối có bookings=2, payments=2, tickets=2, bằng baseline đã bàn giao. Dịch vụ test đã dừng, volume giữ lại.

## Ảnh và bằng chứng

Ảnh chụp nằm tại [evidence/m31-content](evidence/m31-content). Có home/search/trip/promotions/profile/operator/admin và checkout cho cả hai kích thước. results.json ghi kiểm tra nhãn và overflow, checkout-results.json ghi giá và việc giải phóng hold. Đã xem trực quan ảnh Home desktop, Trip mobile và Checkout mobile. Một số nội dung sticky xuất hiện giữa ảnh full-page do cơ chế chụp trang dài.

- [Home desktop](evidence/m31-content/home-1440.png)
- [Search mobile](evidence/m31-content/search-390.png)
- [Trip mobile](evidence/m31-content/trip-390.png)
- [Checkout mobile](evidence/m31-content/checkout-390.png)
- [Operator desktop](evidence/m31-content/operator-1440.png)
- [Admin mobile](evidence/m31-content/admin-390.png)

## Giới hạn và readiness

Đã mở lịch sử đặt vé với tài khoản test hiện có. Chưa mở lại vé QR đã thanh toán và màn hình Payment bằng booking thuộc tài khoản đó trong lượt này. Thay đổi cảnh báo QR được kiểm tra source/build. Chưa duyệt browser mọi form Operator/Admin, mọi nhà xe, trạng thái lỗi hoặc rỗng. Các trường edit nội bộ có thể còn giá trị fixture để bảo toàn dữ liệu. Chưa kiểm chứng thao tác save trên profile/media editor trong browser sau đổi nhãn. Chưa chạy full regression hoặc backend tests trong lượt copy này.

Nội dung chính và bản build sẵn sàng để người dùng review. Quyết định commit M31 chờ review và chấp nhận các giới hạn kiểm tra trên. Báo cáo này không nghiệm thu toàn hệ thống hay xác nhận sẵn sàng vận hành bán vé thật. Không thực hiện commit/push/merge/deploy/reset/stash.

## File thay đổi của lớp nội dung

- `frontend/src/components/ui.tsx`
- `frontend/src/features/booking/BookingSummary.tsx`
- `frontend/src/features/booking/CheckoutVoucherOptions.tsx`
- `frontend/src/features/customer/CustomerMedia.tsx`
- `frontend/src/features/customer/presentation.ts`
- `frontend/src/features/marketplace/DestinationDiscovery.tsx`
- `frontend/src/features/marketplace/HomePromotionalCards.tsx`
- `frontend/src/features/marketplace/Marketplace.tsx`
- `frontend/src/features/marketplace/PublicPromotions.tsx`
- `frontend/src/features/operator/CrewBoarding.tsx`
- `frontend/src/features/operator/Customers.tsx`
- `frontend/src/features/operator/OperatorMediaEditor.tsx`
- `frontend/src/features/operator/Reports.tsx`
- `frontend/src/features/operator/TripWorkspace.tsx`
- `frontend/src/features/operator/Vouchers.tsx`
- `frontend/src/features/search/SearchForm.tsx`
- `frontend/src/features/trip/PrebookingDetails.tsx`
- `frontend/src/layouts/CustomerLayout.tsx`
- `frontend/src/pages/BookingDetailPage.tsx`
- `frontend/src/pages/BookingPage.tsx`
- `frontend/src/pages/HomePage.tsx`
- `frontend/src/pages/MyBookingsPage.tsx`
- `frontend/src/pages/PublicPaymentPage.tsx`
- `frontend/src/pages/SearchPage.tsx`
- `frontend/src/pages/TicketPage.tsx`
- `frontend/src/pages/TripPage.tsx`
- `frontend/src/pages/operator/OperatorBookingCreatePage.tsx`
- `frontend/src/pages/operator/OperatorBookingsPages.tsx`
- `frontend/src/pages/operator/OperatorDispatchPage.tsx`
- `frontend/src/pages/operator/OperatorHomePage.tsx`
- `frontend/src/pages/operator/OperatorRoutesPages.tsx`
- `frontend/src/pages/operator/OperatorSeatsPage.tsx`
- `frontend/src/pages/operator/OperatorTripOperationsPages.tsx`
- `frontend/src/pages/operator/OperatorTripsPages.tsx`
- `frontend/tests/m30-3-final.test.mjs`
- `frontend/src/features/customer/displayCopy.ts`
- `docs/m31-final-content-polish.md` và ảnh/bằng chứng trong `docs/evidence/m31-content/`.

## Đối chiếu evidence trước local commit M31

Ngày 09/11/2026, review ảnh phát hiện `promotions-1440.png` và `promotions-390.png` ghi trang “Không tìm thấy trang” tại `/promotions`. Lượt browser trước đã mở sai URL cho mục này. Hai ảnh và results.json giữ nguyên như bằng chứng thô, không tính là PASS cho trang Promotions riêng. Các thẻ ưu đãi công khai đã xuất hiện trong ảnh Home, và API quote đã được đối chiếu như trên. Không có kiểm tra browser mới trong lượt chốt commit.

Các ảnh Admin/Operator vẫn có tên tài khoản, mã nhà xe, biển số và liên hệ fixture. Đây là thông tin quản trị nội bộ của bộ dữ liệu giả lập, không có password/JWT/API token. Checkout chứa tên và email `.example` của khách test, không có dữ liệu khách thực tế. Secrets, local runtime config, scripts, logs và database vẫn ở ngoài commit. Hướng dẫn local phụ thuộc workspace/fixture đã bàn giao, clone repository đơn thuần chưa đủ để chạy bộ demo này.

Người dùng đã quyết định chốt M31 với các giới hạn đã ghi nhận và chuyển sang M32. Lượt chốt chỉ review/stage/local commit, không chạy thêm chức năng hoặc test.
