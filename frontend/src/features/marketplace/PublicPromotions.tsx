import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link, useLocation } from "react-router-dom";
import { apiClient } from "../../api/client";
import { ErrorState, Loading, Pagination } from "../../components/ui";
import type { PagedResponse } from "../../types/api";
import { routeSearch } from "../customer/presentation";
import { dateTime, money } from "../../utils/format";

export interface PublicPromotion {
  id: number; operator: { id: number; name: string };
  route: { id: number; name: string; pickupLocationId: number; dropoffLocationId: number } | null;
  code: string; name: string; description: string | null; discountType: string; discountValue: number;
  maxDiscount: number | null; minimumSpend: number; startsAt: string; endsAt: string;
  customerLimit: number | null; eligibility: "REQUIRES_CONTEXT" | "CONDITIONAL";
}
export function promotionSearch(p: PublicPromotion) {
  return p.route ? routeSearch(p.route, p.operator.id) : `/search?${new URLSearchParams({ operatorId: String(p.operator.id) })}`;
}
export function PublicPromotionCard({ promotion: p, inTrip = false }: { promotion: PublicPromotion; inTrip?: boolean }) {
  const [notice, setNotice] = useState("");
  return <article className="card public-promotion">
    {inTrip ? <strong>{p.operator.name}</strong> : <Link to={`/operators/${p.operator.id}`}>{p.operator.name}</Link>}<h3>{p.name}</h3><strong>{p.code}</strong>
    {p.description && <p>{p.description}</p>}
    <p>{p.discountType === "PERCENT" ? `${p.discountValue}%` : money(p.discountValue)}{p.maxDiscount != null && ` · Giảm tối đa ${money(p.maxDiscount)}`}</p>
    <p>Giá gốc tối thiểu {money(p.minimumSpend)}{p.customerLimit != null && ` · Tối đa ${p.customerLimit} lượt/khách`}</p>
    <p>Tuyến: {p.route?.name || "Các tuyến đủ điều kiện của nhà xe"}</p>
    <p className="fine-print">{dateTime(p.startsAt)} – {dateTime(p.endsAt)} · Giờ Việt Nam</p>
    <p className="fine-print">{inTrip ? "Chương trình có thể phù hợp với chuyến; còn phụ thuộc số ghế, giá trị đặt vé và giới hạn của bạn." : "Chương trình đang được công khai; chưa xác nhận áp dụng cho hành trình hoặc tài khoản của bạn."} Chỉ checkout mới kiểm tra theo chỗ đang giữ. Xem hoặc sao chép mã không giữ lượt ưu đãi.</p>
    <div className="market-actions">{inTrip ? <a href="#trip-seats">Chọn chỗ và kiểm tra tại checkout</a> : <Link to={promotionSearch(p)}>Tìm chuyến phù hợp</Link>}
      <button type="button" className="secondary" onClick={async () => {
        try { await navigator.clipboard.writeText(p.code); setNotice(`Đã sao chép ${p.code}. Kiểm tra mã tại checkout trước khi đặt vé.`); }
        catch { setNotice("Chưa sao chép được. Bạn có thể ghi lại mã và nhập tại checkout."); }
      }}>Sao chép mã {p.code}</button>
    </div>{notice && <p role="status">{notice}</p>}
  </article>;
}
export function PublicPromotions({ trip }: { trip?: { id: number; pickup: number; dropoff: number } }) {
  const [page, setPage] = useState(0), [now, setNow] = useState(Date.now());
  const location = useLocation();
  const [open, setOpen] = useState(location.hash === "#trip-promotions");
  const element = useRef<HTMLDetailsElement>(null), scrolled = useRef(false);
  const query = useQuery({
    queryKey: ["public-promotions", trip || "home", page],
    queryFn: async ({ signal }) => (await apiClient.get<PagedResponse<PublicPromotion>>(trip ? `/trips/${trip.id}/promotions` : "/public/promotions", {
      params: { ...(trip ? { pickupLocationId: trip.pickup, dropoffLocationId: trip.dropoff } : {}), page, size: trip ? 5 : 6 }, signal,
    })).data,
    staleTime: 0, gcTime: 0, refetchInterval: 30_000, retry: false,
  });
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  useEffect(() => {
    if (trip && location.hash === "#trip-promotions" && query.data && !scrolled.current) {
      scrolled.current = true; setOpen(true); element.current?.scrollIntoView({ block: "start" });
    }
  }, [trip, location.hash, query.data]);
  const promotions = query.data?.data.filter(p => Date.parse(p.startsAt) <= now && Date.parse(p.endsAt) > now) || [];
  const content = <>
    <p className="muted">Ưu đãi do nhà xe tài trợ, có điều kiện. Giá chuyến vẫn là giá gốc; số tiền giảm chỉ được kiểm tra tại checkout.</p>
    {query.isPending ? <Loading /> : query.isError ? <ErrorState error={query.error} retry={() => query.refetch()} /> : <>
      {promotions.length ? <div className="market-grid">{promotions.map(p => <PublicPromotionCard key={p.id} promotion={p} inTrip={!!trip} />)}</div> : <p className="muted">Chưa có ưu đãi công khai trong phạm vi này. Bạn vẫn có thể tìm chuyến và nhập mã riêng tại checkout.</p>}
      <Pagination pagination={query.data.pagination} onPage={setPage} />
      <button type="button" className="secondary" disabled={query.isFetching} onClick={() => { if(page) setPage(0); else void query.refetch(); }}>Làm mới ưu đãi công khai</button>
    </>}
  </>;
  return trip ? <details id="trip-promotions" className="public-promotions" ref={element} open={open} onToggle={e => setOpen(e.currentTarget.open)}><summary>Ưu đãi công khai cho chuyến — xem điều kiện</summary>{content}</details>
    : <section className="public-promotions"><span className="eyebrow">CHƯƠNG TRÌNH CÓ ĐIỀU KIỆN</span><h2>Ưu đãi từ nhà xe</h2>{content}</section>;
}
