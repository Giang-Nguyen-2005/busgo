import {
  ArrowRight,
  Armchair,
  MapPin,
  Search,
  ShieldCheck,
  Ticket,
} from "lucide-react";
import { SearchForm } from "../features/search/SearchForm";
export function HomePage() {
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <span className="eyebrow">MỖI CHUYẾN ĐI, MỘT KHỞI ĐẦU</span>
          <h1>
            Đi đâu cũng dễ.
            <br />
            <em>Cùng BusGo.</em>
          </h1>
          <p>
            Tìm chuyến xe phù hợp, chọn chỗ bạn thích.
            <br />
            Hành trình tiếp theo bắt đầu ngay tại đây.
          </p>
        </div>
        <div className="hero-art" aria-hidden="true">
          <img src="/images/busgo/hero-coach.jpg" alt="" />
          <div className="hero-image-shade" />
        </div>
      </section>
      <div className="home-search">
        <SearchForm />
        <div className="search-note">
          <ShieldCheck size={16} />
          Thông tin chuyến và chỗ trống được cập nhật từ hệ thống.
        </div>
      </div>
      <section className="featured-destinations" aria-labelledby="featured-title">
        <div className="section-heading">
          <div>
            <span className="eyebrow">HÀNH TRÌNH NỔI BẬT</span>
            <h2 id="featured-title">Khám phá những cung đường quen thuộc</h2>
          </div>
          <p className="muted">Các tuyến đang có trong dữ liệu chuyến xe BusGo.</p>
        </div>
        <div className="destination-grid">
          {[
            ["TP. Hồ Chí Minh", "Đà Lạt", "Lâm Đồng"],
            ["TP. Hồ Chí Minh", "Nha Trang", "Khánh Hòa"],
            ["Đà Nẵng", "Huế", "Thừa Thiên Huế"],
          ].map(([from, to, province]) => (
            <article className="destination-card" key={`${from}-${to}`}>
              <span className="destination-icon"><MapPin size={20} /></span>
              <div>
                <h3>{from} <ArrowRight size={16} /> {to}</h3>
                <p>{province}</p>
              </div>
            </article>
          ))}
        </div>
      </section>
      <div className="section-heading benefits-heading">
        <div>
          <span className="eyebrow">VÌ SAO CHỌN BUSGO?</span>
          <h2>Đặt vé rõ ràng từ tìm chuyến đến lên xe</h2>
        </div>
      </div>
      <section className="benefits">
        {[
          {
            icon: Search,
            title: "Tìm chuyến thuận tiện",
            text: "Tra cứu điểm đón, điểm đến và lịch trình trong một nơi.",
          },
          {
            icon: Armchair,
            title: "Chủ động chọn chỗ",
            text: "Xem sơ đồ ghế thực tế và chọn chỗ phù hợp với bạn.",
          },
          {
            icon: Ticket,
            title: "Vé luôn trong tầm tay",
            text: "Xem vé điện tử và lịch sử đặt vé ngay trên tài khoản.",
          },
          {
            icon: ShieldCheck,
            title: "Giá vé minh bạch",
            text: "Chi phí theo ghế và tổng thanh toán được hiển thị rõ ràng.",
          },
        ].map((item) => (
          <article key={item.title}>
            <div className="benefit-icon">
              <item.icon size={23} />
            </div>
            <div>
              <h3>{item.title}</h3>
              <p>{item.text}</p>
            </div>
          </article>
        ))}
      </section>
      <section className="how-section">
        <div>
          <span className="eyebrow">ĐƠN GIẢN TỪ BƯỚC ĐẦU</span>
          <h2>
            Chuyến đi của bạn,
            <br />
            chỉ vài bước nhẹ nhàng.
          </h2>
          <p className="muted">
            BusGo là dự án đặt vé xe khách trực tuyến.
            <br />
            Thanh toán trong phiên bản này là giả lập.
          </p>
        </div>
        <ol>
          {[
            "Tìm chuyến theo hành trình",
            "Chọn ghế và điền thông tin",
            "Thanh toán giả lập, nhận vé điện tử",
          ].map((step, i) => (
            <li key={step}>
              <span>0{i + 1}</span>
              {step}
              <ArrowRight size={18} />
            </li>
          ))}
        </ol>
      </section>
    </>
  );
}
