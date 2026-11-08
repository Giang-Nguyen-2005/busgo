import { Search, SlidersHorizontal, Armchair, Ticket } from "lucide-react";

const benefits = [
  { Icon: Search, title: "Tìm chuyến theo ý bạn", text: "Chọn điểm đón, điểm trả và ngày đi để xem những chuyến phù hợp." },
  { Icon: SlidersHorizontal, title: "Dễ so sánh, dễ chọn", text: "Lọc theo nhà xe, loại xe, khoảng giá và giờ đi để chọn chuyến phù hợp." },
  { Icon: Armchair, title: "Rõ chỗ ngồi, rõ giá", text: "Xem sơ đồ chỗ trống, giá mỗi chỗ và tổng tiền trước khi tiếp tục đặt vé." },
  { Icon: Ticket, title: "Vé luôn dễ tìm", text: "Xem trạng thái đặt vé, thanh toán và vé điện tử tại mục Vé của tôi." },
];
export function CustomerBenefits() {
  return <section className="customer-benefits" aria-labelledby="benefits-heading"><span className="eyebrow">CHUẨN BỊ CHO HÀNH TRÌNH</span><h2 id="benefits-heading">Chủ động hơn trong mỗi bước đặt vé</h2><div className="benefits-grid">{benefits.map(({ Icon, title, text }) => <article key={title}><Icon aria-hidden="true" size={26} /><h3>{title}</h3><p>{text}</p></article>)}</div></section>;
}
