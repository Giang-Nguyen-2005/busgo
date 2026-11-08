# M30.6 · Báo cáo chốt ngày 08/11/2026

**Sẵn sàng để demo luồng đặt vé và quản lý ảnh bằng dữ liệu thử nghiệm.** Luồng khách đã đi từ tìm chuyến đến nhận vé qua trình duyệt, Spring Boot và MySQL. Một số browser suite cũ của màn hình điều hành và đối soát còn kết quả chưa đạt. Phạm vi chốt này không đủ để nghiệm thu toàn bộ hệ thống hoặc đưa vào vận hành thương mại.

## Đã kiểm thử đạt

| Lớp kiểm chứng | Kết quả | Bằng chứng |
| --- | --- | --- |
| Frontend | Production build React/Vite thành công. Bộ unit/component đạt 193/193, không bỏ qua test. | `.tools/m30-6/final-build.log`, `final-frontend.log` |
| Backend và MySQL | Maven `verify -Pmysql-integration` đạt 67/67 unit test và 545/545 integration test, không có failure, error hoặc skip. Chạy trên schema riêng `busgo_m306_verify`. | `.tools/m30-6/full-verify-final.log` |
| Live customer | Khách chọn chuyến và ghế, đăng ký, đăng nhập, quay lại đúng ghế, chọn voucher, xác nhận booking và thanh toán mô phỏng. Vé có QR và booking xuất hiện trong lịch sử. | `.tools/m30-6/customer/results.json`, `roles/live-results.json` |
| Đối chiếu MySQL | Booking 1 có giá gốc 250.000đ, giảm 25.000đ, thanh toán 225.000đ. Booking là CONFIRMED, payment là PAID, voucher là REDEEMED. Tài khoản có đúng một booking, một payment và một vé hợp lệ. Inventory có một chặng BOOKED và không còn dòng giữ ghế của khách. | [Kết quả truy vấn DB](evidence/m30-6/booking-verification.json) |
| Hold, đổi và hủy | Hai request đồng thời giữ cùng ghế trả 201 và 409. Checkout khôi phục được hold còn hạn sau refresh. Hold hết hạn tự nhiên bị từ chối khi tạo booking, khách quay lại chọn ghế và thấy ghế khả dụng. Đổi ghế giải phóng ghế cũ. Hủy vé void ticket, trả ghế vào inventory và tạo đúng một khoản hoàn mô phỏng dù gửi lại yêu cầu. Tài khoản khác bị từ chối đọc và hủy booking. | `.tools/m30-6/commerce.json`, `commerce-final.log` |
| Lỗi và phục hồi | Mã hết hạn, chưa đủ giá trị tối thiểu và hết lượt bị từ chối. Mã riêng không xuất hiện trong danh sách công khai nhưng có thể nhập thủ công khi hợp lệ. Khi trình duyệt chặn request thanh toán, booking vẫn PENDING. Bỏ chặn và thử lại phát hành vé. Search thử lại thành công sau lỗi mạng, bộ lọc giá trả đúng trạng thái trống và đổi chiều hoán đổi đúng điểm đón/trả. | `.tools/m30-6/recovery-checked.log` |
| Operator, staff và admin | Kiểm tra live đăng nhập, hồ sơ, chuyến/dispatch, bookings, support, tài chính và quản lý nhà xe. Staff bị từ chối ở API tài chính và quản lý tài khoản. Các lượt chạy cuối M17, M18A và M18B đạt. | `.tools/m30-6/roles/live-results.json`, `resumed-m17.log`, `final-role-results.txt` |
| Media | Dashboard upload, preview, đổi đại diện, sắp xếp. Khách thấy ảnh đúng nhà xe và loại xe. Escape trả focus, giữ ghế đã chọn. Lô có PNG giả rollback, tệp quá 5 MiB trả 413. Khi còn 10 chỗ, 11 request đồng thời chỉ lưu 10 ảnh và còn đúng một ảnh đại diện. | `.tools/m30-6/media.log`, `media-security.log` |

Trước lần gián đoạn, luồng đăng ký → khôi phục ghế → voucher → booking → thanh toán đã chạy thành công. Sau khi tiếp tục, đã đối chiếu trực tiếp booking và Flyway, sửa lỗi hold hết hạn, xác minh lại đổi/hủy, xử lý truy cập khác tài khoản và hoàn thành lượt Maven verify. Lúc chốt chỉ cần kiểm tra lại production build và Git diff, vì đã có kết quả backend trên mã sau sửa.

## Môi trường và phạm vi bằng chứng

Các lượt live sử dụng project Compose `busgo-m306-regression`, MySQL 8.4 qua loopback 3346 và volume riêng `busgo-m306-regression_mysql_data`. Frontend 5196 proxy tới backend 8136 trên schema `busgo_m306_live`. Trình kiểm tra đối chiếu container, volume, schema và đọc một giá trị ngẫu nhiên qua cả backend lẫn proxy trước khi cho phép thao tác ghi. MySQL xác nhận migration 28 thành công và cột `operator_media.content` có kiểu MEDIUMBLOB. Lượt kiểm chứng hết hạn sau khi tiếp tục dùng TTL test hai phút.

Các browser fixture M29/M30 dùng dữ liệu giả lập để kiểm tra giao diện, gồm carousel, typography, promotions, media fallback và luồng ghế. Kết quả live ở bảng trên dùng API thật. Bằng chứng restart và so sánh SHA-256 của logo và hai ảnh xe thuộc M30.5. [Bằng chứng persistence M30.5](evidence/m30-5/persistence.json)

Edge đã kiểm tra viewport 1440, 1100, 834, 390 và 360 px. Home, Search và Trip không tràn ngang trong các phép đo. Trip giữ trigger gọn ở phần đầu trang, hỗ trợ Escape và trả focus cả khi mô phỏng CSS zoom 200%. Ba mẫu tải production trên loopback ghi nhận Home từ 332–500 ms, Search 263–285 ms và Trip 225–372 ms đến khi phần tử chính xuất hiện. Trip chưa tải bytes ảnh xe trước khi mở gallery. Đây là số đo trên máy kiểm thử, không phải cam kết thời gian đáp ứng ngoài thực tế. [Số đo chi tiết](evidence/m30-6/performance.json)

Ảnh tham khảo: [Home 1440 px](evidence/m30-6/home-1440.png), [Search 390 px](evidence/m30-6/search-390.png), [Trip 390 px](evidence/m30-6/trip-390.png), [dashboard media M30.5](evidence/m30-5/operator-vehicles-390.png).

## Lỗi đã sửa

- **Ghế hết hạn giữ chỗ.** Search và SeatMap từng chỉ đếm dòng AVAILABLE, trong khi API giữ chỗ đã có thể lấy lại dòng HELD hết hạn. Truy vấn đọc hiện dùng cùng điều kiện hết hạn cho các chặng của hành trình. Ghế đã bán, bị khóa, thiếu inventory hoặc thiếu thời điểm hết hạn vẫn không được mở bán. Checkout cũng xử lý trường hợp tác vụ dọn dữ liệu đã xóa hold hết hạn trước khi khách quay lại.
- **Deadlock khi tạo yêu cầu hỗ trợ đồng thời với hủy vé.** Hai giao dịch có thể lấy khóa nhà xe và booking theo thứ tự ngược nhau. Luồng tạo yêu cầu hỗ trợ nay lấy khóa chia sẻ trên nhà xe trước khi insert qua khóa ngoại của booking. Suite cạnh tranh M28 đạt cả 6 test trong lượt verify cuối.
- **Kiểm chứng migration.** Test schema còn kỳ vọng 27 migration và thiếu bảng media. Đã đối chiếu schema thật trước khi cập nhật test. Các test nâng cấp lịch sử so sánh đủ cột của phiên bản nguồn, kể cả cột sinh tự động, và dựng lại trạng thái thanh toán tại thời điểm nhập lịch sử hoàn tiền.
- **Giao diện ghế và điều hành.** Link hồ sơ và trigger ảnh trên Trip có thể nằm cùng hàng, giúp sơ đồ ghế xuất hiện sớm hơn. Màn hình hành khách cho phép mở danh sách vé và liên hệ đặt vé từ phần điểm danh. Các fixture cũ đã được điều chỉnh để trả đúng cấu trúc API đang dùng.

## Còn lỗi hoặc chưa kiểm thử

- **P2 · Browser suite điều hành cũ.** Lượt cuối của M15, M15.5 và operator-cleanup vẫn dừng ở thao tác hoặc chờ phần tử trên màn hình điều hành/manifest. Chưa đủ bằng chứng quy toàn bộ lỗi cho fixture. Nên kiểm tra trực tiếp màn hình này trước khi đưa vào kịch bản demo. Log ở `.tools/m30-6/final-archive.txt`.
- **Đối soát M27A chưa chốt.** Một lượt live mong đợi CLEAN nhưng nhận EXCEPTIONS trong kỳ đã có giao dịch trước chính sách hoa hồng. Chưa có kết quả đạt cuối cho toàn bộ kịch bản đối soát và phát hành bảng kê trên kỳ sạch.
- **Chưa kiểm thử** thiết bị cảm ứng thật, screen reader thủ công, Firefox/WebKit, tải đồng thời ở quy mô sản xuất hoặc khôi phục toàn bộ ứng dụng từ backup. Lượt đo hiệu năng và viewport chỉ đại diện môi trường cục bộ.
- **Phạm vi thương mại.** Chưa có thanh toán và quyết toán hoàn tiền thật. Monitoring, cảnh báo, diễn tập phục hồi, kiểm duyệt ảnh và xác thực nội dung hỗ trợ/chính sách vận hành vẫn là việc riêng trước khi bán vé bằng tiền thật.

## Quyết định đề xuất

Chốt M30.6 ở mức **demo luồng khách và quản lý media**. Kịch bản demo nên đi từ tìm chuyến đến QR, sau đó đổi hoặc hủy vé để thấy inventory và hoàn tiền mô phỏng. Bước phát triển sản phẩm tiếp theo nên tập trung vào màn hình sau đặt vé: để khách dễ tìm lại QR, điều kiện đổi/hủy và trạng thái hoàn tiền từ lịch sử vé.

## Kiểm tra cuối trước commit M30

Trên route `/operator/dispatch`, Edge tái hiện lỗi hover ở ghế trống A05. CSS chung `button:hover` thắng selector `.operator-status-AVAILABLE`, đổi nền từ #dcf2e8 sang #115e59 trong khi chữ vẫn là #116041. Độ tương phản chỉ còn 1,00:1. Sau khi ưu tiên selector trạng thái cho `.db-seat.operator-status-AVAILABLE` và dùng viền báo hover, độ tương phản khi hover là 6,46:1.

Browser regression dùng route React thật và inventory fixture tại 1440, 834, 390 và 360 px. Đã kiểm tra ghế trống, đã đặt, đang giữ, bị khóa, focus bàn phím, chọn để xem chi tiết và disabled khi chưa chọn chặng. Mẫu sơ đồ loại xe vẫn chỉ đọc. Build thành công, 193/193 test frontend đạt. [Hover trước sửa](evidence/seat-hover/before-hover.png) · [Hover sau sửa](evidence/seat-hover/after-hover.png)
