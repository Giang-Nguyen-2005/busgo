import { Link } from "react-router-dom";
import { SearchForm } from "../features/search/SearchForm";
import { useAuth } from "../features/auth/AuthProvider";
import { customerAccess } from "../features/customer/presentation";
export function HomePage() {
 const auth = useAuth();
 return <>
 <section className="hero"><div className="hero-copy"><span className="eyebrow">CÙNG BUSGO</span><h1>Tìm chuyến dễ dàng.<br /><em>Chọn chỗ bạn thích.</em></h1><p>Tra cứu hành trình, chọn chỗ và quản lý vé trong một nơi.</p></div><div className="hero-art" aria-hidden="true"><img src="/images/busgo/hero-coach.jpg" alt="" /><div className="hero-image-shade" /></div></section>
 <div className="home-search"><SearchForm /><p className="search-note">Giá và chỗ trống được kiểm tra từ hệ thống khi bạn đặt vé.</p></div>
 <section className="home-guide"><div><span className="eyebrow">HÀNH TRÌNH CỦA BẠN</span><h2>Đặt vé rõ ràng, từng bước</h2><ol><li>Tìm chuyến theo điểm đón, điểm trả và ngày đi.</li><li>Chọn tối đa 5 chỗ, kiểm tra giá và thông tin liên hệ.</li><li>Xác nhận thanh toán giả lập và xem vé điện tử.</li></ol></div><div className="card"><h2>Mọi thông tin trong tầm tay</h2><p>Xem giờ đi, giờ đến, giá mỗi chỗ và tổng tiền trước khi đặt. Vé điện tử và lịch sử đặt vé nằm trong tài khoản của bạn.</p><p className="notice info">Phiên bản này dùng thanh toán giả lập. Không có giao dịch tiền thật.</p>{customerAccess(auth.user?.roles) ? <Link className="button secondary" to="/my-bookings">Mở Vé của tôi</Link> : !auth.authenticated ? <Link className="button secondary" to="/login">Đăng nhập tài khoản</Link> : null}</div></section>
 </>;
}
