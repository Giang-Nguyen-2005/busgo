import { money } from "../../utils/format";
export type PromotionPriceData = { grossAmount: number; discountAmount: number; totalAmount: number; originalGrossAmount: number; originalDiscountAmount: number; voucherCode?: string; voucherName?: string };
export function PromotionPrice({ price }: { price?: PromotionPriceData }) {
  if (!price?.voucherCode) return null;
  return <dl className="summary" aria-label="Chi tiết giá"><dt>Giá vé gốc hiện tại</dt><dd>{money(price.grossAmount)}</dd><dt>Ưu đãi nhà xe{price.voucherCode ? ` · ${price.voucherCode}` : ""}</dt><dd>−{money(price.discountAmount)}</dd><dt>Tổng thanh toán</dt><dd><strong>{money(price.totalAmount)}</strong></dd></dl>;
}
