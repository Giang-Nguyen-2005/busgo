# Customer content & typography polish — 06/11/2026

## Audit trước sửa
Baseline main / ac5297fe5de6bf87792fd1e179f8867df4d491e9, working tree có các thay đổi completeness và compact seat media chưa commit. Giữ nguyên toàn bộ. Đã đối chiếu M29 customer foundations, M30.2/prebooking, M30.3/media, M30.4 public promotions và báo cáo completeness hiện tại.

| Khu vực | Dễ đọc / hierarchy / copy / trạng thái trước sửa | Hướng xử lý |
|---|---|---|
| Home | Section 23–25px, card title 17px, supporting text có nơi 12–14px. CTA tuyến chen cùng tên. Nhà xe thiếu bio/rating lặp “chưa có”. | Scale section/card riêng; CTA tuyến riêng dòng; bio trống không lặp trên card; CTA hồ sơ rõ. |
| Search | Giờ/giá đã rõ nhưng tên nhà xe 16px; ngày/tuyến 12–13px; mobile availability 12px. | Card title 18px, metadata 14px, giá 22–24px. Giữ filter/sort/layout. |
| Trip | Compact media đã đúng. Lưu ý giữ chỗ còn dài; phụ chú nhỏ. | Rút gọn copy giữ nguyên semantics; tăng độ đọc ghi chú, không đổi seat geometry. |
| Operator | Bio fallback mang tính nội bộ; tên/tuyến chưa rõ hierarchy; các empty states ít ngữ cảnh. | Giới thiệu tự nhiên, tuyến/CTA riêng dòng, trạng thái ngắn có đường tiếp tục. |
| Checkout | Form labels đúng; copy “khách trên chỗ”, “Booking”, “checkout” khó hiểu; điều kiện voucher cần giữ rõ. | Đổi cách diễn đạt, giữ mọi điều kiện và thông báo tiền mô phỏng. |
| Login/Register | Copy lời chào chung chung; form và auth story cùng h1. | Hướng dẫn mục đích tài khoản, chỉ form là h1; không đổi validation/auth. |
| Profile/My bookings | Nội dung đã đúng nhưng dài; heading/card và metadata cần đồng bộ. | Rút gọn hỗ trợ thao tác, typography chung. |
| Ticket/Payment | Amount/QR có hierarchy tốt, giữ. Metadata và note cần dễ đọc hơn. | Chỉ style/copy tĩnh; không sửa trạng thái/QR. |
| Support/FAQ | Luồng hỗ trợ đã có; câu trả lời FAQ dài, khó quét. | Rút gọn, giữ điều kiện đổi/hủy, mô phỏng và tác động yêu cầu hỗ trợ. |
| Footer | Link thật, nhóm “Khám phá & hướng dẫn” chưa tự nhiên; chú thích 12px. | Nhóm rõ, body 15px, metadata 13px. |
| Loading/error | Skeleton và retry đã có, network copy đã có hướng xử lý. | Giữ behavior; đồng bộ độ đọc và wrapping. |

Phạm vi chỉ static copy, JSX presentation, CSS. Không thay dữ liệu API hoặc logic auth/hold/booking/payment/voucher/refund. Hero và assets giữ nguyên. Không giả lập nội dung kinh doanh.

## Tiến độ qua lần gián đoạn

### Đã thực hiện trước usage limit
- Hoàn thành audit; tạo `customer-typography.css` và import ở CustomerLayout.
- Scope token typography trong `.customer-shell`, giữ font và palette M29.
- Viết lại nội dung điểm đến, ưu đãi, lợi ích, FAQ, hướng dẫn đăng nhập/đăng ký, checkout, tài khoản và footer.
- Thêm CTA hồ sơ nhà xe; không lặp bio rỗng trên card; tách tên tuyến và CTA ở Home.
- Rút gọn lưu ý giữ chỗ, giữ nội dung “chưa giữ chỗ/giá tạm tính”; giữ compact media.
- Build đã qua; test còn 1 lỗi assertion dựa trên câu ưu đãi cũ. Browser review và báo cáo chưa hoàn tất lúc bị gián đoạn.

### Hoàn thành khi tiếp tục
- Đọc lại status/diff, code và logs, không làm lại phần đã có.
- Cập nhật các assertion copy theo nội dung mới, giữ kiểm tra điều kiện ưu đãi/chưa giữ lượt/không tự áp mã và các assertion nghiệp vụ.
- Sửa specificity để heading Home dùng đúng scale; giảm khoảng cách trip heading để ghế vẫn xuất hiện gần đầu trang, không nới test `seat map near top`.
- Tách CTA tuyến ở public operator profile; nhóm từng tên điểm đến khi xuống dòng để tránh tách tên ngắn không hợp lý. Tên dài vẫn wrap được, không ellipsis/cắt dữ liệu.
- Hoàn thiện visual regression riêng: Home, operator cards, route cards, benefits/guide, Search, Trip, public profile, Account, Support ở đủ 5 viewport; login/register; chuỗi dài và empty states.
- Chụp ảnh sau khi lazy images tải xong; kiểm tra contrast trên computed colors, giữ media/modal regression và public promotions regression.

## Typography trước / sau

| Vai trò | Trước | Sau |
|---|---|---|
| Page heading | Các page có cỡ rải rác; Trip mobile 23px | Token `--customer-text-page: clamp(26px,2.5vw,34px)`; Trip mobile giữ gọn 24px |
| Home section heading | Desktop 25px, mobile 23px | `--customer-text-section: clamp(26px,2.4vw,32px)`, weight 750, line-height 1.25 |
| Card heading | Phần lớn 17px; tên nhà xe trip 16px | Token card 20px desktop / 18px mobile; tên nhà xe TripCard 18px |
| Body / supporting text | Có chỗ 12–14px | Body 15px, line-height 1.6; description max-width 68–76ch tùy loại nội dung |
| Metadata | Tuyến/ngày/ghế có nơi 12px | Metadata 14px; ghi chú phụ 13px, line-height 1.55 |
| Price / time | Mobile giá 20px, giờ 25px | Giá 24/22px, giờ 28/26px, tabular numbers; giữ orange/structural palette |
| CTA | CTA tuyến chen tên; tên action có thuật ngữ nội bộ | CTA tuyến riêng dòng, 14px/600–650; “Chọn ngày khởi hành”, “Xem hồ sơ nhà xe” |
| FAQ / guide | Chữ nhỏ; bước chưa nổi bật | FAQ summary 16px, padding 18px; tiêu đề bước 18–20px; chú thích trước đặt vé tách rõ |
| Footer | Metadata 12px | Body 15px, metadata 13px; nhóm link màu structural, links teal |

Không đổi font toàn site, global design tokens hoặc typography operator/admin. Các scale mới được khai báo một lần tại `frontend/src/features/customer/customer-typography.css`. Hero giữ nguyên nội dung, font-size, line-height, hình và bố cục. Không sửa ảnh nguồn/banner. Trạng thái thanh toán, QR, bộ lọc, số ghế và policy từ API giữ nguyên.

## Nội dung đã viết lại — ví dụ

| Trước | Sau / lý do |
|---|---|
| “Nhà xe chưa cập nhật giới thiệu công khai.” | “Thông tin giới thiệu chưa được cung cấp.” ở profile; Home không lặp bio rỗng dưới rating. |
| “Chọn ngày tìm chuyến” chen sau tên tuyến | “Chọn ngày khởi hành” trên dòng CTA riêng. |
| “Chương trình đang được công khai; chưa xác nhận áp dụng…” | “Cần kiểm tra hành trình và tài khoản của bạn để xác nhận điều kiện áp dụng.” |
| “Chỉ checkout mới kiểm tra theo chỗ đang giữ.” | “Điều kiện được kiểm tra theo chỗ bạn đang giữ.” Các câu lân cận vẫn nêu rõ xem/sao chép không giữ lượt. |
| “Chưa có ưu đãi công khai trong phạm vi này…” | “Hiện chưa có chương trình phù hợp để giới thiệu. Bạn vẫn có thể chọn chuyến hoặc nhập mã riêng ở bước thông tin đặt vé.” |
| “Booking thành công mới xác nhận số tiền.” | “…số tiền và lượt ưu đãi chỉ được xác nhận khi đặt vé thành công.” |
| “Ghế đang chọn chưa được giữ chỗ. Giá tạm tính; chỉ khi hệ thống…” | “Chỗ đang chọn chưa được giữ. Giá tạm tính sẽ được xác nhận khi giữ chỗ thành công.” |
| “Bạn chưa cung cấp tên riêng cho từng khách trên chỗ.” | “Bước này chưa thu thập tên riêng của từng hành khách.” |
| Empty review có khung lớn và link tìm chuyến | Lời giải thích ngắn khi nào có nhận xét, link “Tìm hiểu nhà xe”. |
| Đăng ký: “Sẵn sàng cho chuyến đi tiếp theo…” | “Tạo tài khoản để giữ chỗ, đặt vé và theo dõi chuyến đi.” |
| “Khám phá & hướng dẫn” | “Thông tin cho chuyến đi”. |

FAQ được rút gọn theo tìm chuyến → giữ chỗ → thông tin liên hệ → thanh toán mô phỏng → quản lý vé → hỗ trợ. Không thêm chính sách, tiện nghi, giới thiệu kinh doanh, số liệu hoặc review. Những trạng thái đã rõ như network retry, hết hạn giữ chỗ, vé không hợp lệ, lịch sử trống và Search không có kết quả được giữ semantics, áp dụng typography chung.

## Component / file trong task này

Production:
- `frontend/src/features/customer/customer-typography.css` (mới), `visualAssets.ts`, `CustomerBenefits.tsx`, `CustomerFaq.tsx`.
- `frontend/src/features/marketplace/Marketplace.tsx`, `DestinationDiscovery.tsx`, `HomePromotionalCards.tsx`, `PublicPromotions.tsx`.
- `frontend/src/features/booking/CheckoutVoucherOptions.tsx` (chỉ copy).
- `frontend/src/layouts/CustomerLayout.tsx` (import CSS, footer group).
- `frontend/src/pages/AuthPage.tsx` (copy + semantic heading), `BookingPage.tsx`, `TripPage.tsx`, `ProfilePage.tsx`, `MyBookingsPage.tsx` (chỉ copy).
- Các phần Search/Ticket/Payment/Support dùng style chung; không thay flow hoặc logic.

Tests:
- `frontend/tests/customer-typography-browser.cjs` mới.
- Copy-dependent assertions ở `m30-2a-browser.cjs`, `m30-4b.test.mjs`, `m30-4b-browser.cjs`, `m30-4b-live.cjs`, `m30-3-final.test.mjs`, `m30-3-final-browser.cjs`, `m30-3a.test.mjs` được cập nhật theo câu mới. File live chỉ cập nhật locator, không chạy hoặc tạo dữ liệu live.
- Các thay đổi completeness/media đã có trước task vẫn được giữ, không liệt kê nhầm là mới triển khai lại.

## Screenshot và evidence

Thư mục `.tools/content-polish/` (ignored artifacts), mở `review.html` để xem toàn bộ:
- `visual/home-{1440,1100,834,390,360}.png`.
- `visual/operators-*`, `routes-*`, `benefits-*`, `guide-*`: ảnh từng section.
- `visual/search-*`, `trip-*`, `operator-*`, `account-*`, `support-*`: đủ 5 viewport.
- `visual/home-empty-360.png`, `search-empty-360.png`, `search-long-360.png`, `route-long-360.png`, `login-360.png`, `register-360.png`.
- `media/`: Home/Search/Trip và modal sau typography từ media regression.
- `visual/contrast.json`, `visual/acceptance.json`, `media/acceptance.json`, logs build/tests/visual/media/promotions/customer-flow.
- Ảnh trước task được giữ tại `.tools/customer-complete/` và `.tools/seat-media-refinement/`, đã liên kết từ review index. Fixture card counts khác nhau giữa hai bộ; không dùng chúng làm pixel-diff tuyệt đối.

Ảnh là ứng dụng thật với dữ liệu API fixture; tên, giá, review, booking và QR trong ảnh không phải dữ liệu kinh doanh/live. Full-page Trip chụp sticky summary tại vị trí viewport lúc chụp; regression kiểm tra CTA thao tác được.

## Giới hạn
- Kiểm thử browser dùng Edge với viewport mô phỏng, không phải iOS/Android thật hay Safari/Firefox.
- Contrast kiểm tra 175 mẫu chữ/CTA trên nền solid tính từ computed styles, tối thiểu 4.57:1. Không kiểm chứng chữ nằm trong artwork đã duyệt và không coi đây là chứng nhận WCAG toàn hệ thống hoặc screen-reader audit.
- Không chạy live FE→BE→DB trong task này. Không thay backend, DB, seed, quyền, authentication, seat holds, booking, payment/refund hoặc tính voucher.
- Thanh toán/hoàn tiền vẫn mô phỏng. Ưu đãi thật tiếp tục từ M30.4B; banner 20% giữ gate development như trước.
- Public dữ liệu còn thiếu vẫn hiển thị trạng thái thật, không thay bằng giới thiệu/đánh giá giả.

## Kết quả kiểm thử cuối cùng

| Kiểm tra | Kết quả |
|---|---|
| `npm --prefix frontend run build` | PASS TypeScript + Vite production. Warning Notifications vừa static vừa dynamic import đã có, không chặn build. |
| `npm --prefix frontend test` | PASS 191/191, 0 fail/skip. |
| `m30-2b-browser.cjs` (bao gồm M29 + M30.2A/B) | PASS 31 nhóm, 68 captures: search/filter/sort, ghế, hold/conflict/expiry, login/return, checkout/quote, payment mô phỏng, valid/void/missing QR, booking history, eligible/ineligible đổi/hủy, prebooking fallback. |
| `m30-4b-browser.cjs --public-only` | PASS 5 nhóm, 10 captures: điều kiện/pagination, CTA/context, copy không tạo hold/quote, voucher không auto-apply, expired/hidden/error/retry. |
| `m30-3-final-browser.cjs --media-only` | PASS 3 nhóm: modal focus/Tab/Escape/return, không POST khi xem ảnh, giữ selected seats, type/image failure fallback, compact media và 5 viewport. |
| `customer-typography-browser.cjs` | PASS hierarchy, route CTA riêng, 3 fixture operator cards không bio giả, Account/Support/Auth, long text và empty states. |
| Responsive | Home/Search/Trip/Profile/Account/Support ở 1440/1100/834/390/360px, không document horizontal overflow. Route card tên ngắn giữ theo từng điểm đến, chuỗi dài vẫn wrap. |
| Contrast mẫu | 175 mẫu computed color, min 4.573:1 (giá orange trên nền subtle). Đạt AA normal text trong phạm vi mẫu; xem giới hạn ở trên. |
| Runtime | Các acceptance cuối: zero uncaught page errors, unexpected console errors và unmatched API requests. Fault fixtures có HTTP errors cố ý ghi riêng. |
| `git diff --check` | PASS; chỉ cảnh báo LF/CRLF của Git. |
| Live integration | KHÔNG CHẠY, không tuyên bố live PASS. |

Lịch sử kiểm tra: đã sửa lỗi specificity heading và spacing seat map tìm thấy bằng browser, không giảm/ngắt assertion. Các test trông chờ câu copy cũ (public terms, giữ chỗ, đăng nhập) đã được đổi locator/assertion đúng nội dung mới; sau đó toàn suite customer chạy lại PASS. Không bỏ test hoặc thêm fallback để che lỗi.

Branch/HEAD vẫn `main` / `ac5297fe5de6bf87792fd1e179f8867df4d491e9`. Backend/assets không thay đổi trong task này. Không commit, push, merge, reset/stash; dừng cho người dùng review.
