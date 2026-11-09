import { CustomerBenefits } from "../features/customer/CustomerBenefits";
import { HeroArt } from "../features/customer/CustomerMedia";
import { CustomerFaq } from "../features/customer/CustomerFaq";
import { HomeDiscovery } from "../features/marketplace/Marketplace";
import { Link } from "react-router-dom";
import { SearchForm } from "../features/search/SearchForm";
import { useAuth } from "../features/auth/AuthProvider";
import { customerAccess } from "../features/customer/presentation";
export function HomePage() {
 const auth = useAuth();
 return <>
 <section className="hero"><div className="hero-copy"><span className="eyebrow">BUSGO / HÀNH TRÌNH CỦA BẠN</span><h1>Đi xa một chút.<br /><em>Thấy nhiều hơn.</em></h1><p>Từ nhịp sống thành phố đến những cung đường mới. Tìm chuyến phù hợp, chọn chỗ và theo dõi vé cùng BusGo.</p></div><HeroArt /></section>
 <div className="home-search" id="journey-search"><SearchForm /><p className="search-note">Giá và chỗ trống được kiểm tra từ hệ thống khi bạn đặt vé.</p></div>
 <HomeDiscovery /><CustomerBenefits /><section id="booking-guide" className="home-guide"><div><span className="eyebrow">HÀNH TRÌNH CỦA BẠN</span><h2>Đặt vé rõ ràng, từng bước</h2><ol className="booking-guide-steps"><li><strong>Tìm hành trình</strong><p>Chọn điểm đón, điểm trả và ngày đi. Lọc hoặc sắp xếp kết quả để so sánh các chuyến.</p></li><li><strong>Chọn chuyến và chỗ ngồi</strong><p>Xem lịch trình, chọn tối đa 5 chỗ rồi đăng nhập để tiếp tục. Chỗ chỉ được giữ khi hệ thống xác nhận.</p></li><li><strong>Kiểm tra và thanh toán</strong><p>Nhập thông tin liên hệ, kiểm tra tổng tiền và điều kiện ưu đãi nếu có. Hoàn tất thanh toán mô phỏng trong thời hạn hiển thị.</p></li><li><strong>Nhận và quản lý vé</strong><p>Mở Vé của tôi để xem vé điện tử, trạng thái thanh toán hoặc các lựa chọn đổi, hủy mà hệ thống cho phép.</p></li></ol></div><div className="card"><h2>Trước khi xác nhận đặt vé</h2><p>Kiểm tra đúng ngày đi, điểm đón và số điện thoại liên hệ. Giờ đến có thể thuộc ngày hôm sau; mọi thời gian đều hiển thị theo giờ Việt Nam.</p>{customerAccess(auth.user?.roles) ? <Link className="button secondary" to="/my-bookings">Mở Vé của tôi</Link> : !auth.authenticated ? <Link className="button secondary" to="/login">Đăng nhập tài khoản</Link> : null}</div></section>
 <CustomerFaq />
 </>;
}
