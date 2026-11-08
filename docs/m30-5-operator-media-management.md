# M30.5 · Quản lý hình ảnh nhà xe

## Hiện trạng và phạm vi review

Bắt đầu trên `main` tại `ac5297fe5de6bf87792fd1e179f8867df4d491e9`, cùng HEAD với `origin/main`. Trước M30.5 có **21 tệp tracked đã sửa** (+218/−51), cùng nhiều tệp mới của M30.3, customer completeness và typography. Các thay đổi gồm Home, Search, Trip, checkout, tài khoản, CSS, marketplace, component, fixture và tài liệu. Các bộ ảnh demo đã có sẵn trong working tree. M30.5 tiếp nối các component đang dùng ở đó. Theo yêu cầu, giữ tất cả thay đổi để lập checkpoint chung sau khi M30.6 được review. Khi đó cần đối chiếu các thay đổi chồng lấn, đặc biệt ở `CustomerMedia.tsx`, `Marketplace.tsx`, `ui.tsx`, `TripPage.tsx` và fixture dùng chung.

README, tài liệu M29 operator dispatch/customer experience, M30.2 journey/prebooking, M30.3 assets/refinement, M30.4 promotions, customer completeness/typography và mã nguồn cho thấy:

- `bus_types` là danh mục toàn hệ thống. Mỗi `buses` có chủ sở hữu và loại xe. Các chuyến tham chiếu xe cụ thể. Trang Loại xe trước đây chỉ trình bày danh mục và sơ đồ ghế.
- Marketplace dùng `transport_operators.logo_url` trong danh sách và hồ sơ. Dashboard từng nhận URL thủ công. Search có `busImageUrl` cũ cho xe cụ thể. M30.3 đã tách ảnh ngoại thất này khỏi minh họa nội thất.
- `OperatorLogo`, `VehicleImage`, modal và trigger gọn đã tồn tại. Trước M30.5, TripCard chỉ truyền tên nhà xe cho logo. Hình nội thất demo dựa vào tên loại xe khớp chính xác.
- Backend trước M30.5 chưa có API upload ảnh. Flyway đến V27. Compose mặc định chỉ chạy MySQL. Vite và Java chạy trên host, với proxy `/api` cùng origin.

## Mô hình và lưu trữ

Flyway **V28__operator_media.sql** thêm bảng InnoDB `operator_media`. Ảnh xe thuộc cặp `(operator_id, bus_type_id)`. Một ảnh đại diện cho thư viện loại xe của nhà xe, không chứng thực cấu hình của từng chiếc xe. Logo có loại `LOGO` và `bus_type_id` null. Hai khóa ngoại giữ tham chiếu chủ sở hữu và danh mục. Migration giữ nguyên logo URL cũ và dữ liệu demo.

Bảng lưu UUID do server sinh, MIME, nội dung ảnh, phân loại, mô tả, thứ tự, cờ đại diện và thời gian. Logo mới cập nhật `logo_url` đến endpoint đọc theo UUID. Tất cả thao tác trên bytes và metadata dùng cùng transaction. Thay logo, xóa ảnh và lỗi giữa lô không cần cơ chế dọn tệp trên host. File multipart tạm của servlet chỉ phục vụ việc nhận request.

**Docker** dùng volume Compose hiện hữu `mysql_data:/var/lib/mysql` để lưu cả bytes lẫn metadata. Không cần mount thư mục ảnh vào frontend hoặc backend. Sao lưu database sẽ bao gồm ảnh. Chi phí là tăng kích thước backup và băng thông MySQL. API list chỉ chọn metadata. API ảnh trả bytes với MIME cố định, `nosniff` và `no-store`.

## API

Các đường dẫn dưới đây có tiền tố `/api/v1`. API JSON dùng `ApiResponse` hoặc lỗi chuẩn của BusGo. Các endpoint đọc một ảnh trả nội dung nhị phân.

| Phương thức | Đường dẫn | Công dụng |
| --- | --- | --- |
| POST / GET / DELETE | `/operator/marketplace-profile/logo` | Upload multipart `file`, xem bản lưu riêng hoặc xóa logo |
| PATCH | `/operator/marketplace-profile` | Lưu mô tả mà không ghi đè logo khi hai cửa sổ cập nhật đồng thời |
| GET / POST | `/operator/bus-types/{type}/media` | Danh sách có thứ tự hoặc upload nhiều phần `files`, cùng `category` và `alt` |
| GET / PATCH / DELETE | `/operator/bus-types/{type}/media/{id}` | Xem ảnh riêng, sửa phân loại/mô tả, chọn đại diện hoặc xóa |
| PUT | `/operator/bus-types/{type}/media/order` | Nhận `{"ids":[...]}` gồm đủ các UUID hiện có, mỗi UUID một lần |
| GET | `/public/operators/{owner}/media` | Logo và danh sách ảnh công khai, nhóm theo loại xe |
| GET | `/public/operators/{owner}/media/{id}` | Kiểm tra điều kiện hiển thị lại trước khi trả bytes |

PUT hồ sơ cũ vẫn hỗ trợ URL legacy. Thay hoặc xóa URL qua contract này cũng dọn logo upload cũ. Một URL managed mới phải được cấp bởi chức năng upload. Home discovery/hồ sơ giữ nguyên contract logo hiện có. Home upcoming, Search, Trip và public profile dùng chung truy vấn gallery được cache theo nhà xe.

## Quyền và validation

- Các private endpoint yêu cầu `OPERATOR_ADMIN`. `OperatorContextService` giải quyết membership đang hoạt động từ tài khoản xác thực. ID chủ sở hữu không phải tham số mutation.
- Upload xác minh có xe chưa xóa của chính nhà xe thuộc loại đang hoạt động, với trạng thái xe khác INACTIVE. Public gallery và cả URL ảnh yêu cầu nhà xe ACTIVE, loại xe ACTIVE và ít nhất một xe AVAILABLE chưa xóa. Logo phải còn là logo hiện hành.
- PNG/JPEG kiểm tra extension, MIME, signature, kích thước pixel, terminator và giải mã bằng ImageIO. WebP tĩnh kiểm tra RIFF, chiều dài/chunk, VP8/VP8L frame và kích thước. WebP động chưa hỗ trợ. Giới hạn 5 MiB/ảnh, 16 triệu pixel, tối đa 12 ảnh/nhà xe/loại xe và mô tả 180 ký tự. SVG, executable và nội dung giả MIME bị từ chối.
- UUID độc lập với tên file và đường dẫn client. Không có thao tác mở/xóa đường dẫn filesystem từ request. UUID không tồn tại hoặc ảnh đã bị ẩn trả 404. Public DTO không chứa bytes, đường dẫn nội bộ, kích thước hay thông tin nhân viên.
- Các mutation khóa hàng nhà xe và đọc trạng thái hiện hành ở READ COMMITTED. Điều này bảo vệ hạn mức, vị trí và ảnh đại diện khi nhiều request chạy đồng thời. Xóa ảnh đại diện sẽ chọn ảnh còn lại đầu tiên theo thứ tự hiển thị.

## Giao diện

**Dashboard.** Trong *Hồ sơ và đánh giá*, quản trị viên xem logo hiện hành, chọn tệp, xem trước, lưu, thay và xác nhận xóa. Trong *Loại xe → Chi tiết loại xe*, quản trị viên upload theo lô, chọn nội thất/ngoại thất/ghế/khác, sửa mô tả, xem lớn, chọn đại diện, đưa lên/xuống và xóa có xác nhận. Trang hiển thị trạng thái đang tải, trống, lỗi, thử lại và kết quả. Không thêm navigation.

**Khách hàng.** Thẻ chuyến chỉ hiện ảnh đại diện. Gallery dùng thứ tự đã lưu, nút trước/sau, mũi tên bàn phím, Tab, Escape và trả focus. Trip giữ trigger gọn trong phần đầu trang. Public profile hiển thị riêng từng loại xe. Caption “Ảnh do nhà xe cung cấp” mô tả nguồn ảnh.

Thứ tự fallback là ảnh managed còn đọc được trong đúng thư viện, rồi ảnh demo đúng mapping, cuối cùng là biểu tượng hoặc trạng thái trống. Demo nhận diện chỉ bật mặc định khi chạy Vite dev. Đặt `VITE_DEMO_MEDIA=true` cho bản build demo, hoặc `false` để tắt trong dev. Ảnh tĩnh giữ nhãn “Ảnh minh họa loại xe”. Khi URL logo legacy bị lỗi, biểu tượng xe vẫn hiển thị.

## Kiểm chứng

- **Build và test tự động.** Production build TypeScript/Vite thành công. Frontend đạt 193/193. Backend đạt 72/72 khi chạy toàn bộ `*Test` cùng 5 test `OperatorMediaIT` trên schema `busgo_m305_test`. Lần chạy đầu của test PNG/JPEG phát hiện Maven đang dùng bytecode cũ. Đã biên dịch lại và xác minh test từ chối cả ảnh bị cắt và ảnh nối thêm bytes. Các con số integration bên dưới có test trùng với lần chạy này.
- **Fixture trình duyệt.** Edge + API giả lập. M29 interactions kiểm tra tìm kiếm, seat map, chọn ghế, checkout, thanh toán mô phỏng và vé. Các suite M30.3, carousel, typography và M30.4B kiểm tra static fallback, khôi phục lỗi, tuyến đang hoạt động, promotions và điều hướng. Các fixture này không ghi dữ liệu vào backend.
- **Integration backend.** MockMvc + Spring Security + Flyway + MySQL thật. Trên MySQL 8.4.11, 20 test của `OperatorMediaIT`, `M30PublicPromotionsIT` và `M30VoucherOptionsIT` đạt. Có kiểm tra token của hai operator, customer/anonymous, sai association, ảnh quá lớn/giả MIME/extension, ảnh mất, ngừng công khai khi xe bảo trì, metadata, featured/reorder/delete, giới hạn 12 ảnh và vòng đời logo.
- **Live FE → BE → DB.** Edge không intercept API. Dữ liệu và tài khoản demo chỉ thuộc schema M30.5 riêng. Tải logo WebP, upload hai ảnh, preview, thay đại diện và sắp xếp qua dashboard. Request có phần thứ hai giả PNG trả 400 và số ảnh sau request giữ nguyên. Khách ẩn danh mở Home, Search, Trip và profile. ESC trả focus, ghế đã chọn được giữ khi mở gallery. Các kiểm tra overflow chạy ở 1440, 1100, 834, 390, 360 px. Các ảnh dùng trong live test là asset mẫu tải lên schema test.
- **Phạm vi booking.** Browser live M30.5 kiểm tra đến bước chọn ghế. Luồng thanh toán/đặt vé được kiểm tra bằng fixture và các integration liên quan.

### Persistence và kiểm tra HTTP sau restart

Ngày 07/11/2026, đã kiểm tra nhãn Compose, schema `busgo_m305_volume`, cổng loopback 3345 và tên volume trước khi restart riêng container `busgo-m305-volume-test-mysql-1`. Volume `busgo-m305-volume-test_mysql_data` mount tại `/var/lib/mysql`. Sau khi container trở lại trạng thái healthy, backend trả lại cùng logo (54.158 byte) và hai ảnh xe (184.050 và 168.034 byte). Cả ba SHA-256 từ HTTP trùng với bytes trong MySQL trước và sau restart. UUID, URL, phân loại, mô tả, vị trí, featured và timestamps cũng trùng khớp. [Bằng chứng persistence](evidence/m30-5/persistence.json)

Kiểm tra qua HTTP thật xác nhận POST/DELETE ẩn danh trả 401, tệp vượt 5 MiB trả 413 và cả lô rollback nếu tệp thứ hai giả PNG. Với hai ảnh ban đầu và 11 upload đồng thời, server chấp nhận đúng 10, từ chối một và giữ đúng một ảnh đại diện. Đã xóa riêng các ảnh sinh bởi phép thử đồng thời và đối chiếu gallery với trạng thái ban đầu.

Lượt integration cuối trên schema native độc lập đạt 20/20. Hai nhà xe trong test quyền sở hữu cùng vận hành một loại xe. Nhà xe B nhận 404 khi sửa hoặc xóa UUID của A. Customer nhận 403, công chúng chỉ đọc được ảnh đang hiển thị.

Đã dừng Vite, backend và hai MySQL của lượt kiểm thử. Container Docker đang ở trạng thái exited, volume test và datadir native được giữ để có thể kiểm tra lại. Working tree vẫn trên `main` tại `ac5297f`; checkpoint chung chờ review M30.6.

### Ảnh chụp từ live browser

| Màn hình | Ví dụ |
| --- | --- |
| Logo dashboard | [1440 px](evidence/m30-5/operator-logo-1440.png) |
| Gallery dashboard | [1440 px](evidence/m30-5/operator-vehicles-1440.png) · [390 px](evidence/m30-5/operator-vehicles-390.png) |
| Home | [1440 px](evidence/m30-5/home-1440.png) · [360 px](evidence/m30-5/home-360.png) |
| Search | [1440 px](evidence/m30-5/search-1440.png) · [390 px](evidence/m30-5/search-390.png) |
| Trip Detail | [1440 px](evidence/m30-5/trip-1440.png) · [360 px](evidence/m30-5/trip-360.png) |
| Gallery và hồ sơ công khai | [Gallery 390 px](evidence/m30-5/trip-gallery-390.png) · [Profile 390 px](evidence/m30-5/public-profile-390.png) |

Toàn bộ 23 screenshot nằm trong `docs/evidence/m30-5/`. Log và harness live của lượt nghiệm thu tại `.tools/m30-5/`. Harness live có thao tác ghi và chỉ dùng với stack test cô lập đã xác minh. Không trỏ nó vào DB ứng dụng.

## Giới hạn và M30.6

Ảnh gắn với loại xe trong phạm vi từng nhà xe. M30.6 có thể mở rộng đến từng xe hoặc chuyến nếu sản phẩm cần phân biệt cấu hình. File gốc vẫn chứa metadata nhúng. Chưa tạo thumbnail, chuyển mã, kiểm duyệt nội dung, giải mã toàn bộ WebP phía server hoặc kiểm thử tải production. Nên đo dung lượng ảnh, thời gian tải và chi phí backup trên dữ liệu đại diện trước khi quyết định tách object storage. Kiểm thử accessibility thủ công với screen reader và kiểm thử cảm ứng trên thiết bị thật vẫn cần thực hiện.
