import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import axios from "axios";
import { ErrorState, Loading } from "../components/ui";
import { dateTime, money, paymentMethodLabel } from "../utils/format";
import type { ApiResponse } from "../types/api";
import type { PublicPayment } from "../types/assisted";

// Independent client: anonymous links never attach account access/refresh tokens.
const publicClient = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1", timeout: 10_000 });
export function PublicPaymentContent({ payment: p }: { payment: PublicPayment }) {
  return <><h1>Thanh toán đặt vé</h1><h2>{p.bookingCode}</h2><p>{p.operator} · {p.journey}</p><p>Đón: {p.pickup.name} · {p.pickup.time && dateTime(p.pickup.time)}</p><p>Trả: {p.dropoff.name} · {p.dropoff.time && dateTime(p.dropoff.time)}</p><p>Ghế: {p.seats.join(", ")}</p><strong>{money(p.amount)}</strong><p>{paymentMethodLabel(p.method)}</p><p>{p.status === "CANCELLED" ? "Đặt vé đã hủy, không còn thanh toán được." : p.status === "CONFIRMED" ? "Đã thanh toán. Liên hệ nhà xe để nhận vé điện tử." : "Chưa thanh toán"}</p><p className="notice info">Đây là mô phỏng thanh toán. Không chuyển tiền vào tài khoản ngân hàng.</p></>;
}
export function PublicPaymentPage() {
  const token = useParams().token || "";
  const cache = useQueryClient();
  const key = ["public-payment", token];
  const query = useQuery({ queryKey: key, queryFn: async () => (await publicClient.get<ApiResponse<PublicPayment>>(`/public/payments/${encodeURIComponent(token)}`)).data.data, retry: false, gcTime: 0 });
  const confirm = useMutation({ mutationFn: async () => (await publicClient.post<ApiResponse<PublicPayment>>(`/public/payments/${encodeURIComponent(token)}/mock-confirm`)).data.data, onSuccess: result => cache.setQueryData(key, result) });
  return <main className="public-payment card"><meta name="referrer" content="no-referrer" /><strong className="brand">BusGo</strong>{query.isPending ? <Loading /> : query.isError ? <ErrorState error={query.error} /> : <><PublicPaymentContent payment={query.data} />{query.data.status === "PENDING" && <button disabled={confirm.isPending} onClick={() => confirm.mutate()}>{confirm.isPending ? "Đang xác nhận…" : "Xác nhận mô phỏng thanh toán"}</button>}{confirm.isError && <ErrorState error={confirm.error} />}</>}</main>;
}
