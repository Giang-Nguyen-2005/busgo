import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { get, post } from "../../api/client";
import { ErrorState, Loading } from "../../components/ui";
import { useAuth } from "../auth/AuthProvider";
import { canManageOperator } from "../auth/access";
import { dateTime, money } from "../../utils/format";

import { statusPresentation } from "../../utils/status";
import type { Recovery } from "../../types/recovery";
const reasons: Record<string,string> = {
  BOOKING_NOT_CANCELLABLE: "Đặt vé không còn ở trạng thái có thể hủy.",
  CANCELLATION_WINDOW_CLOSED: "Chuyến đã khởi hành hoặc kết thúc.",
  CANCELLATION_ATTENDANCE_CONFLICT: "Có khách đã điểm danh, lên xe hoặc được ghi nhận vắng mặt.",
  PICKUP_CLOSED: "Điểm đón đã đóng.",
  CANCELLATION_OPERATOR_SUSPENDED: "Nhà xe tạm ngưng. Hủy vé đã thanh toán cần hỗ trợ từ nền tảng.",
  CUSTOMER_CANCELLATION_CUTOFF: "Đã qua hạn hủy: 6 giờ trước giờ đón dự kiến.",
  CUSTOMER_CANCELLED: "Khách hàng hủy", OPERATOR_CANCELLED: "Nhà xe hủy", PAYMENT_TIMEOUT: "Quá hạn thanh toán",
};
export function CancellationSection({ bookingId, bookingCode, route, pickup, seats, contact, amount, operator = false }: {
  bookingId: number; bookingCode: string; route: string; pickup: string; seats: string[]; contact: string; amount: number; operator?: boolean;
}) {
  const auth=useAuth(); const cache=useQueryClient(); const [review,setReview]=useState(false); const [note,setNote]=useState("");
  const dialog = useRef<HTMLElement>(null);
  const pending = useRef(false);
  const path=`${operator ? "/operator" : ""}/bookings/${bookingId}`;
  const query=useQuery({ queryKey:["recovery",auth.user?.id,operator,bookingId],queryFn:({signal})=>get<Recovery>(path+"/recovery",undefined,signal),staleTime:0 });
  const cancel=useMutation({mutationFn:()=>post<Recovery>(path+"/cancel",{note:note.trim() || undefined}),onSuccess:async result=>{
    setReview(false); cache.setQueryData(["recovery",auth.user?.id,operator,bookingId],result);
    await Promise.all([cache.invalidateQueries({queryKey:["booking"]}),cache.invalidateQueries({queryKey:["bookings"]}),cache.invalidateQueries({queryKey:["tickets"]}),cache.invalidateQueries({queryKey:["operator"]}),cache.invalidateQueries({queryKey:["recovery"]})]);
  },onError:()=>{ void query.refetch(); }});
  pending.current = cancel.isPending;
  useEffect(() => {
    if (!review) return;
    const previous = document.activeElement as HTMLElement | null;
    const node = dialog.current;
    if (!node) return;
    const controls = () => Array.from(node.querySelectorAll<HTMLElement>('button:not(:disabled), textarea:not(:disabled), [href], [tabindex="0"]'));
    // Start on the safe return action, trap keyboard focus and restore the opener.
    controls().at(-1)?.focus();
    const keyboard = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !pending.current) { event.preventDefault(); setReview(false); }
      if (event.key !== 'Tab') return;
      const items = controls(); const first = items[0]; const last = items.at(-1);
      if (!first) { event.preventDefault(); node.focus(); return; }
      if (event.shiftKey && (document.activeElement === first || !node.contains(document.activeElement))) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || !node.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', keyboard);
    return () => { document.removeEventListener('keydown', keyboard); previous?.focus(); };
  }, [review]);
  if(query.isPending) return <Loading />;
  if(query.isError) return <ErrorState error={query.error} retry={()=>query.refetch()} />;
  const r=query.data; const permitted=!operator || canManageOperator(auth.user?.roles);
  const action=operator ? "Hủy đặt vé" : r.paid ? "Huỷ và hoàn tiền mô phỏng" : "Huỷ đặt chỗ";
  return <section className="card cancellation-section"><h2>Hủy đặt vé và khôi phục chỗ</h2>
    {r.paymentDueAt && r.status==="PENDING" && <p>Hạn thanh toán: {dateTime(r.paymentDueAt)}</p>}
    {r.cancelledAt ? <><p role="status">Đã hủy · {dateTime(r.cancelledAt)}</p><p>{reasons[r.reasonCode || ""] || r.reasonCode} · {r.cancelledBy ? `Người thực hiện #${r.cancelledBy}` : "Hệ thống"}</p>{r.note && <p>{r.note}</p>}</> : <>
      <p>{operator ? "Có thể hủy trước khi chuyến khởi hành và trước khi điểm đón đóng. Khách đã điểm danh, lên xe hoặc vắng mặt không thể hủy." : "Chỉ hủy toàn bộ đặt vé, đến 6 giờ trước giờ đón dự kiến. Không thể hủy sau điểm danh, lên xe hoặc ghi nhận vắng mặt."}</p>
      {!operator && r.customerCutoffAt && <p>Hạn hủy: {dateTime(r.customerCutoffAt)}</p>}
      {!r.eligible && <p className="notice info">{reasons[r.ineligibleReason || ""] || "Không thể hủy đặt vé này."}</p>}
      {r.eligible && permitted && <button className="secondary" onClick={()=>{cancel.reset();setReview(true);}}>{action}</button>}
      {!permitted && <p className="muted">Chỉ quản trị nhà xe được hủy đặt vé.</p>}
    </>}
    {r.refunds.map(refund=><p className="notice info" key={refund.id}>Hoàn tiền mô phỏng · {money(refund.amount)} · {dateTime(refund.refundedAt)}. Đây là giao dịch mô phỏng, không chuyển tiền về tài khoản ngân hàng.</p>)}
    {r.tickets.filter(t=>t.status==="VOID").map(t=><p key={t.id}>Vé {t.ticketCode} · Đã vô hiệu (VOID), không dùng để lên xe.</p>)}
    {!!r.history.length && <details><summary>Lịch sử đặt vé</summary>{r.history.map((h,i)=><p key={i}>{dateTime(h.changedAt)} · {statusPresentation("booking", h.fromStatus).label} → {statusPresentation("booking", h.toStatus).label} · {reasons[h.reasonCode || ""] || h.reasonCode || "Thanh toán"} · {h.actorId ? `Người thực hiện #${h.actorId}` : "Hệ thống / liên kết thanh toán"}{h.note && ` · ${h.note}`}</p>)}</details>}
    {review && <div className="cancellation-overlay"><section ref={dialog} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="cancellation-title" className="card cancellation-dialog"><h2 id="cancellation-title">Xác nhận hủy đặt vé</h2>
      <p><strong>{bookingCode}</strong> · {route}</p><p>Điểm đón: {pickup}</p><p>Liên hệ: {contact}</p><p>Ghế: {seats.join(", ")}</p><p>{money(amount)} · {r.paid ? "Đã thanh toán" : "Chưa thanh toán"}</p>
      <p>Hủy toàn bộ đặt vé, giải phóng ghế{r.paid ? ", vô hiệu mọi vé và hoàn tiền mô phỏng toàn bộ." : ". Không tạo thanh toán, vé hoặc hoàn tiền."}</p>
      <p>{operator ? "Chuyến chưa khởi hành, điểm đón chưa đóng và chưa có điểm danh." : "Hạn hủy là 6 giờ trước giờ đón dự kiến."} Không thể mở lại đặt vé đã hủy.</p>
      <label className="field">Ghi chú (không bắt buộc)<textarea maxLength={500} value={note} onChange={e=>setNote(e.target.value)} /></label>
      <div className="actions"><button disabled={cancel.isPending} onClick={()=>cancel.mutate()}>Xác nhận hủy</button><button className="secondary" disabled={cancel.isPending} onClick={()=>setReview(false)}>Quay lại</button></div>
      {cancel.isError && <ErrorState error={cancel.error} />}
    </section></div>}
  </section>;
}
