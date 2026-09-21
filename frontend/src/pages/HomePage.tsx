import {
  ArrowRight,
  Armchair,
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
          <div className="road" />
          <div className="bus-art">
            <div className="bus-window" />
            <div className="bus-name">
              BusGo <ArrowRight />
            </div>
            <i />
            <i />
          </div>
          <span className="art-caption">HÀNH TRÌNH CỦA BẠN</span>
        </div>
      </section>
      <div className="home-search">
        <SearchForm />
        <div className="search-note">
          <ShieldCheck size={16} />
          Thông tin chuyến và chỗ trống được cập nhật từ hệ thống.
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
