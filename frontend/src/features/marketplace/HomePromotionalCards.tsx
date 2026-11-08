import { ArrowUpRight, MapPin } from "lucide-react";
import { AssetImage } from "../customer/CustomerMedia";

// These cards are discovery links. Campaign terms remain exclusively in PublicPromotions.
export const homePromotionalCards = [
  { key: "booking", image: "/images/busgo/promo-booking.webp", title: "Khám phá ưu đãi đặt vé", description: "Tìm hiểu điều kiện và thời hạn của từng chương trình.", href: "#public-promotions" },
  { key: "operators", image: "/images/busgo/promo-operators.webp", title: "Khám phá nhà xe", description: "Tìm hiểu nhà xe và các tuyến đang vận hành.", href: "#public-operators" },
  // Unverified artwork is available only in Vite development, never a production build.
  { key: "journey", image: "/images/busgo/promo-featured.webp", title: "Kiểm tra chương trình hiện có", description: "Ảnh mẫu có mức giảm 20% chưa được xác minh. Chỉ các chương trình kèm điều kiện bên dưới là nguồn ưu đãi thực tế.", href: "#public-promotions" },
] as const;

export function HomePromotionalCards() {
  const preview = import.meta.env?.DEV === true;
  const cards = homePromotionalCards.filter(card => card.key !== "journey" || preview);
  return <><p className="muted">Khám phá ưu đãi từ nhà xe. Xem phạm vi áp dụng và điều kiện trước khi chọn chuyến.</p>
    <div className="home-promo-cards" aria-label="Gợi ý khám phá">
      {cards.map(card => <a className="card home-promo-card" key={card.key} href={card.href}>
        <span className="home-promo-art" aria-hidden="true"><MapPin size={48} /><AssetImage src={card.image} alt="" width={1200} height={1200} /></span>
        <span className="home-promo-copy">{card.key === "journey" && <small className="promo-preview-label">Bản xem trước · Chưa có hiệu lực</small>}<strong>{card.title} <ArrowUpRight size={16} /></strong><span>{card.description}</span></span>
      </a>)}
    </div></>;
}
