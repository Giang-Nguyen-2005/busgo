# Customer website completeness — 06/11/2026

## Audit trước triển khai
Baseline: main, HEAD ac5297fe5de6bf87792fd1e179f8867df4d491e9. Có 8 tracked files modified và các assets/components/tests M30.3 untracked; giữ nguyên, phát triển tiếp. Không đụng file bị loại trừ. Không backend/DB/live mutation.

| Khu vực | Đã hoàn chỉnh | UI/nội dung còn thiếu | Nguồn có thể tận dụng | Cần backend mới |
|---|---|---|---|---|
| Home | Hero/search, discovery, reviews thật, FAQ | Chưa có gallery 6 điểm đến; lợi ích và hướng dẫn ít; footer ít liên kết | routes + locations, operators, upcoming | Ranking phổ biến, giá thấp nhất theo tuyến; discovery routes chỉ trả tối đa 8 |
| Promotion | M30.4B public list/conditions/pagination/retry; checkout quote | Slot thứ ba đang là card chữ | Ba ảnh nguyên bản cho development preview | Campaign được xác minh trước khi công khai banner 20% |
| Search | Filters/sorts/pagination, skeleton/error/empty, thời gian/giá/chỗ từ API | Thiếu logo cạnh tên nhà xe; hướng dẫn so sánh còn ít | Logo demo exact-name/fallback; thời lượng có trong API | Logo public trong Search DTO nếu muốn logo thật không gọi N API |
| Trip | Seats/hold, thông tin trước mua, giá/tổng, lịch trình, media/modal | Loading còn đơn giản | Giữ prebooking/policy projection, thêm skeleton | Địa chỉ đón chính xác, tiện nghi được xác minh |
| Operator | Logo/bio/routes/types/upcoming/reviews | Empty states ngắn và thiếu CTA; chưa có gallery loại xe | Profile busTypes và mapping ảnh hiện có | Public contact whitelist chưa tồn tại |
| Account | Auth/profile/password, history/details, QR, mock payment, eligibility đổi/hủy, support/notifications | Hướng dẫn account còn ít; title trang chưa riêng | Các route đã có, không viết lại nghiệp vụ | Thanh toán thật, nội dung pháp lý và liên hệ được xác minh |
| Toàn site | M29 tokens, drawer keyboard, reduced motion, responsive | Metadata thiếu; footer chưa dẫn FAQ/guide | Route/title và anchor thật | SEO SSR/prerender nếu cần index sâu |

Không có section testimonial giả. Nội dung guide và account panel đang lặp ý quản lý vé: sẽ chuyển thành các bước thực hiện và lợi ích riêng. Không đặt nhãn “phổ biến” khi API chỉ chứng minh tuyến đang hoạt động. Không lấy minimum của 6 upcoming trips làm giá khởi điểm tuyến.

## Kế hoạch thực hiện
A: gallery điểm đến có route thật hoặc trạng thái chưa có tuyến; banner thứ ba DEV-only, giữ ảnh; benefits/guide/FAQ/footer.
B: logo TripCard, thông tin ngày, hướng dẫn search và skeleton chi tiết; giữ mutation nguyên vẹn.
C: profile gallery/empty states; account guidance; metadata theo route.
D: build/unit/browser M30.2/M30.4/media; 5 viewport; ảnh chụp và báo cáo giới hạn.

## Sau triển khai — phạm vi hoàn thành

| Khu vực | Kết quả |
|---|---|
| Home | Giữ nguyên hero/search. Gallery đủ 6 ảnh gốc đã tối ưu WebP, tên địa điểm rõ; link chỉ dựng từ route và location API khớp chính xác. Không match thì chỉ hiển thị trạng thái trung thực, không link giả. |
| Promotions | Development đủ ba ảnh, desktop ba cột/mobile scroll-snap; ảnh contain không cắt chữ. Banner 20% có nhãn preview chưa hiệu lực và caption rõ. `import.meta.env.DEV` là gate build, không query string/localStorage override. Production ẩn hẳn card 20%. M30.4B giữ nguyên campaign thực. |
| Hướng dẫn | Bốn lợi ích thực tế; bốn bước đặt vé; checklist ngày/điểm đón/liên hệ; FAQ tái sử dụng, bỏ thuật ngữ CUSTOMER khỏi copy. Không thêm chính sách suy đoán. |
| Footer | Giới thiệu nền tảng, hướng dẫn, FAQ, tìm chuyến; account/support links theo quyền; giữ thông báo mô phỏng/UTC+7. Không thêm hotline/pháp nhân chưa xác minh. |
| Search | Logo exact demo-name hoặc icon fallback; không gọi N profile APIs. Ngày đi thêm trên card, giữ ngày đến qua đêm; hướng dẫn so sánh. Logo CSS tách theo component để mở Search trực tiếp vẫn đúng. |
| Trip | Skeleton có accessible label cho detail/seat loading. Giữ seat map, giá/tổng, prebooking, image caption/modal, CTA và tất cả mutation logic. |
| Operator | Gallery ảnh loại xe theo mapping đã xác nhận; fallback loại chưa có ảnh; empty states tuyến/chuyến có CTA thật. Không thêm contact ngoài public DTO. |
| Account | Review auth/register/profile/history/detail/payment/tickets/change/cancel/support: giữ nghiệp vụ; bổ sung hướng dẫn profile/history, titles theo route. |
| Toàn site | Meta description cơ bản, page title cho customer routes; giữ navigation drawer/focus/reduced motion hiện có. Không thêm dependency. |

### Home cuối cùng (thứ tự)
1. Hero hiện tại + search.
2. Khám phá 6 điểm đến.
3. Ưu đãi nổi bật: bộ ảnh preview/discovery + M30.4B ưu đãi từ nhà xe.
4. Khám phá nhà xe.
5. Tuyến đang hoạt động (không gắn nhãn phổ biến khi chưa có ranking).
6. Chuyến sắp khởi hành: giá/chỗ từ API.
7. Đánh giá gần đây từ API; empty state nếu không có.
8. Lợi ích đặt vé.
9. Hướng dẫn 4 bước + kiểm tra trước xác nhận.
10. CustomerFaq.
11. Footer.

### File/component thay đổi trong lượt này
- `frontend/src/features/marketplace/DestinationDiscovery.tsx` (mới), `CustomerBenefits.tsx` tại features/customer (mới).
- `frontend/src/features/marketplace/Marketplace.tsx`, `HomePromotionalCards.tsx`, `marketplace.css`.
- `frontend/src/features/customer/CustomerFaq.tsx`, `CustomerMedia.tsx`, `customer-media.css` (mới), `customer.css`.
- `frontend/src/components/ui.tsx`, `frontend/src/layouts/CustomerLayout.tsx`.
- `frontend/src/pages/HomePage.tsx`, `SearchPage.tsx`, `TripPage.tsx`, `ProfilePage.tsx`, `MyBookingsPage.tsx`.
- `frontend/index.html`.
- `frontend/tests/customer-completeness-browser.cjs` (mới); `m30-3-final.test.mjs`, `m30-3-final-browser.cjs`, `m29-customer-browser.cjs` cập nhật assertion/selector cho yêu cầu mới.
- `docs/customer-website-completeness.md` (báo cáo này).
Các thay đổi `PublicPromotions.tsx`, `customer-admin-ux.test.mjs`, assets và visualAssets.ts đã có trước lượt này được giữ. Không sửa backend, DB, migrations, inventory, booking/payment services.

## Kiểm chứng cuối

| Kiểm tra | Kết quả / phạm vi |
|---|---|
| Frontend production build | PASS TypeScript + Vite. Warning Notifications vừa static vừa dynamic import còn tồn tại, không chặn build. |
| Frontend tests | PASS 191/191, 0 fail/skip. |
| M29 + M30.2A/B browser | PASS toàn suite `m30-2b-browser.cjs` bao gồm M29 và M30.2A: tìm chuyến/filter/sort, ghế/polling, guest/auth return, hold conflict/expiry, checkout/quote, mock payment, QR valid/void/missing, history, đổi/hủy eligibility, metadata/policy fallback. |
| M30.4B | PASS 5 nhóm public-only: zero/many, pagination, CTA/context, copy không reserve, login/checkout không auto-apply voucher, expired/hidden/error/retry. |
| Media | PASS 5 viewport Home/Search/Trip, ảnh/fallback, modal focus/Tab/Escape, ghế giữ nguyên và không POST khi mở ảnh. |
| Completeness DEV | PASS 6 ảnh, 1 link đúng fixture route và 5 destination không link giả, ba banner, anchors/FAQ keyboard/title, logo kích thước đúng. |
| Production preview | PASS cùng ứng dụng build qua vite preview, 2 banner, không render promo-featured/20%, 5 viewport. |
| Responsive | 1440, 1100, 834, 390, 360px: không document horizontal overflow. Mobile banner cuộn cục bộ có snap; seat map giữ geometry hiện có. |
| Console/page/API | Final acceptance reports không uncaught page errors, không unexpected console errors hoặc unmatched API calls. HTTP errors cố ý trong fault fixtures được ghi riêng. |
| Accessibility cơ bản | Modal/filter/drawer focus, Escape/Tab, FAQ Enter, form labels, image fallbacks, reduced motion trong regression; không phải chứng nhận WCAG/screen reader. |
| Live integration | CHƯA CHẠY. Không listener backend local 8080/8100 khi kiểm tra; không khởi động seeder/DB hoặc sửa dữ liệu live. |

Lần chạy đầu có lỗi khai báo trùng khi tích hợp và style cũ ảnh hưởng gallery/logo; đã sửa rồi build/test/chụp lại. Windows sandbox gây Edge startup/Vite realpath EPERM: chạy local được cấp quyền. Test cũ bấm “retry đầu tiên” nay trỏ gallery mới: đã scope retry vào nhà xe, giữ assertion phục hồi nhà xe. Các lỗi lần đầu không được tính PASS; kết quả cuối nằm trong acceptance JSON.

### Evidence và screenshot
Thư mục `C:/Users/Giang/busgo/.tools/customer-complete/` (ignored artifacts):
- `review.html`: chỉ mục ảnh cho reviewer.
- `completeness/home-{1440,1100,834,390,360}.png`: Home development đủ 6 ảnh.
- `production/home-{1440,1100,834,390,360}.png`: Home bản build.
- `browser/interior-search-{1440,1100,834,390,360}.png`.
- `browser/interior-trip-{1440,1100,834,390,360}.png`.
- `browser/promotions-{1440,1100,834,390,360}.png`, `interior-modal-390.png`, `operator-profile-360.png`.
- `build.log`, `tests.log`, `m302b.log`, `m304b.log`, `media.log`, `completeness.log`, `production.log`.
- `m302b-acceptance.json`, `m304b-acceptance.json`, `browser/acceptance.json`, `completeness/acceptance.json`, `production/acceptance.json`.

Tất cả giá, tên khách, đánh giá, số chỗ và QR trong screenshot là fixture thử nghiệm. Screenshot full-page Trip có thể ghi sticky summary tại vị trí viewport lúc chụp; browser regression kiểm tra CTA thao tác được. Không dùng screenshot làm bằng chứng live integration.

## Cần dữ liệu/backend thực và thứ tự trước launch
1. Nghiệm thu live FE→BE→DB trên môi trường test tách biệt: đầy đủ auth/hold/concurrency/quote/booking/payment/cancel/change. Lượt này không thay thế nghiệm thu đó.
2. Hoàn thiện payment/refund thật trước khi bán vé tiền thật; hiện toàn bộ vẫn mô phỏng. Xác minh điều khoản, chính sách, pháp nhân và liên hệ hỗ trợ rồi mới tạo các trang/links tương ứng.
3. Cung cấp đủ tuyến/chuyến thực. Discovery routes hiện tối đa 8 và upcoming tối đa 6; gallery không phải danh mục đầy đủ. Nếu cần chọn nhiều tuyến theo thành phố: thêm public destination catalogue có pagination/mapping được xác minh.
4. Public logo projection cho Search; public contacts được whitelist; địa chỉ đón/trả đã xác minh và hướng dẫn boarding; media/amenity metadata thực. Không lấy contact quản trị hay đoán từ ảnh.
5. Chỉ mở banner 20% khi có campaign backend khớp được xác minh và mapping có kiểm tra hiệu lực/phạm vi. Không bật cờ preview cho production; asset gốc vẫn trong public folder nhưng UI production không hiển thị lời hứa này.
6. Nếu cần “tuyến phổ biến” hoặc “giá từ”: backend cần ranking/aggregate fare đúng thời gian/chặng; không suy từ 6 chuyến sắp khởi hành.
7. Sau review UI: thiết bị iOS/Android thật, Safari/Firefox, screen reader và zoom 200%; đo performance với dữ liệu/ảnh production. SEO sâu cần prerender/SSR và canonical có domain thực; hiện metadata SPA cơ bản.

Dừng để user review. Không commit, push, merge, reset/stash, deploy hoặc thay dữ liệu live.

## Follow-up: seat-selection media refinement

Removed the full interior block below SeatMap. TripPage now uses `VehicleImage compact` beside trip/operator information: a 44px keyboard-accessible secondary action opens the existing labelled dialog. Only a subtle “Chưa có ảnh minh họa” is rendered for unknown/failed media. The illustration label and disclaimer remain in the modal. Other thumbnail consumers remain unchanged. No inventory, hold, checkout, backend or booking logic changed.

Validation: frontend build PASS; 191/191 tests PASS. Updated media browser assertions verify the seat workspace has no media block, trigger sits in trip heading, no visible large thumbnail, selected seats survive modal interaction, focus/Tab/Escape and no media-triggered POST. Evidence for this follow-up is `.tools/seat-media-refinement/`; previous screenshots above describe the prior layout.
