import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { apiClient } from "../../api/client";
import { ErrorState, Loading } from "../../components/ui";
import { dateTime, money } from "../../utils/format";

export interface VoucherOption {
  id: number; code: string; name: string; description: string | null;
  discountType: string; discountValue: number; maxDiscount: number | null; minimumSpend: number;
  startsAt: string; endsAt: string; customerLimit: number | null;
  status: "AVAILABLE" | "CONDITION_NOT_MET" | "UNAVAILABLE"; reasonCode: string | null; message: string | null;
  quote: { discountAmount: number; totalAmount: number } | null;
}
interface Options {
  grossAmount: number; seatCount: number; options: VoucherOption[];
  pagination: { page: number; totalPages: number; totalElements: number };
}
export function VoucherOptionCard({ option: v, selected, disabled, onSelect }: {
  option: VoucherOption; selected: boolean; disabled: boolean; onSelect: (code: string) => void;
}) {
  return <article className="voucher-option">
    <h3>{v.name}</h3><strong>{v.code}</strong>
    {v.description && <p>{v.description}</p>}
    <p>{v.discountType === "PERCENT" ? `${v.discountValue}%` : money(v.discountValue)}{v.maxDiscount != null && ` · Giảm tối đa ${money(v.maxDiscount)}`}</p>
    <p>Giá gốc tối thiểu {money(v.minimumSpend)}{v.customerLimit != null && ` · Tối đa ${v.customerLimit} lượt/khách`}</p>
    <p className="fine-print">{dateTime(v.startsAt)} – {dateTime(v.endsAt)} · Giờ Việt Nam</p>
    {v.status === "AVAILABLE" && v.quote ? <p>Có thể giảm {money(v.quote.discountAmount)} · Dự kiến còn {money(v.quote.totalAmount)}</p> : <p>{v.status === "CONDITION_NOT_MET" ? "Chưa đạt điều kiện" : "Không thể sử dụng"}: {v.message || "Vui lòng kiểm tra lại ưu đãi."}</p>}
    <button type="button" className="secondary" aria-pressed={selected} disabled={disabled || v.status !== "AVAILABLE" || !v.quote} onClick={() => onSelect(v.code)}>{selected ? "Kiểm tra lại mã" : "Chọn mã"} {v.code}</button>
  </article>;
}
export function CheckoutVoucherOptions({ userId, holdToken, context, enabled, disabled, selectedCode, onSelect }: {
  userId?: number; holdToken: string; context: string; enabled: boolean; disabled: boolean;
  selectedCode: string; onSelect: (code: string) => void;
}) {
  const [page, setPage] = useState(0);
  const query = useQuery({
    queryKey: ["checkout-voucher-options", userId, holdToken, context, page],
    queryFn: async ({ signal }) => (await apiClient.post<{ data: Options }>("/bookings/voucher-options", { holdToken, page, size: 5 }, { signal })).data.data,
    enabled: enabled && !!userId, staleTime: 0, gcTime: 0, retry: false, refetchInterval: enabled ? 30_000 : false,
  });
  return <details className="checkout-voucher-options"><summary>Ưu đãi dành cho chuyến đi</summary>
    <p className="fine-print">Các mã dưới đây do nhà xe giới thiệu cho chỗ bạn đang giữ. Chọn mã để kiểm tra giá; số tiền và lượt ưu đãi chỉ được xác nhận khi đặt vé thành công.</p>
    {!enabled ? <p>Chưa thể kiểm tra ưu đãi cho lượt giữ chỗ này.</p> : query.isPending ? <Loading /> : query.isError ? <ErrorState error={query.error} retry={() => query.refetch()} /> : <>
      {!query.data.options.length && <p>Không có ưu đãi công khai phù hợp cho lượt giữ chỗ này. Bạn vẫn có thể nhập mã riêng hoặc đặt vé theo giá gốc.</p>}
      <div className="voucher-options-list">{query.data.options.map(option => <VoucherOptionCard key={option.id} option={option} selected={selectedCode === option.code} disabled={disabled || query.isFetching} onSelect={onSelect} />)}</div>
      {query.data.pagination.totalPages > 1 && <div className="actions"><button type="button" className="secondary" disabled={disabled || query.isFetching || page === 0} onClick={() => setPage(page - 1)}>Ưu đãi trước</button><span>Trang ưu đãi {page + 1}/{query.data.pagination.totalPages}</span><button type="button" className="secondary" disabled={disabled || query.isFetching || page + 1 >= query.data.pagination.totalPages} onClick={() => setPage(page + 1)}>Ưu đãi tiếp</button></div>}
      <button type="button" className="secondary" disabled={disabled || query.isFetching} onClick={() => { if(page) setPage(0); else void query.refetch(); }}>Cập nhật danh sách ưu đãi</button>
    </>}
  </details>;
}
