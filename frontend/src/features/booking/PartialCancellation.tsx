import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { get, post } from "../../api/client";
import { ErrorState, Loading } from "../../components/ui";
import { useAuth } from "../auth/AuthProvider";
import { dateTime, money } from "../../utils/format";
import { canReviewPartial } from "./partialCancellationModel";

type Rule = { allowed: boolean; reasonCode: string | null; message: string | null };
type Item = { bookingItemId: number; passengerName: string; seatCode: string; amount: number; cancelled: boolean; eligibility: Rule };
type Eligibility = { eligibility: Rule; bookingCode: string; journeyName: string; departure: string; items: Item[] };
type Quote = { bookingCode: string; journeyName: string; departure: string; items: Item[]; currentTotal: number; cancelledAmount: number; newTotal: number; netCollected: number; refundRequired: number; newAmountDue: number };
type History = { id: number; actorType: string; actorName: string; completedAt: string; quote: Quote };

function Review({ quote: q }: { quote: Quote }) {
  return <><p><strong>{q.bookingCode}</strong> · {q.journeyName} · {dateTime(q.departure)}</p>
    <ul>{q.items.map(i => <li key={i.bookingItemId}>{i.passengerName} · Ghế {i.seatCode} · {money(i.amount)}</li>)}</ul>
    <dl className="modification-money">
      <div><dt>Tổng tiền trước khi hủy</dt><dd>{money(q.currentTotal)}</dd></div>
      <div><dt>Số tiền hủy</dt><dd>{money(q.cancelledAmount)}</dd></div>
      <div><dt>Tổng tiền còn lại</dt><dd>{money(q.newTotal)}</dd></div>
      <div><dt>Đã thu sau các lần hoàn trước</dt><dd>{money(q.netCollected)}</dd></div>
      <div><dt>Hoàn lại (mô phỏng)</dt><dd>{money(q.refundRequired)}</dd></div>
      <div><dt>Còn phải thanh toán</dt><dd>{money(q.newAmountDue)}</dd></div>
    </dl></>;
}

export function PartialCancellationSection({ bookingId, operator = false }: { bookingId: number; operator?: boolean }) {
  const auth = useAuth(); const cache = useQueryClient();
  const base = `${operator ? "/operator" : ""}/bookings/${bookingId}`;
  const [open, setOpen] = useState(false); const [selected, setSelected] = useState<number[]>([]);
  const [quote, setQuote] = useState<Quote>(); const [confirmed, setConfirmed] = useState(false);
  const [completed, setCompleted] = useState(false);
  const eligibility = useQuery({ queryKey: ["partial-eligibility", auth.user?.id, base], queryFn: () => get<Eligibility>(base + "/partial-cancellation-eligibility") });
  const history = useQuery({ queryKey: ["partial-history", auth.user?.id, base], queryFn: () => get<History[]>(base + "/partial-cancellations") });
  const action = useMutation({ mutationFn: async (execute: boolean) => {
    if (!execute) { setQuote(await post<Quote>(base + "/partial-cancellation-quote", { bookingItemIds: selected })); setConfirmed(false); return; }
    await post<History>(base + "/partial-cancellations", { bookingItemIds: quote!.items.map(i => i.bookingItemId) });
    setOpen(false); setQuote(undefined); setSelected([]); setConfirmed(false); setCompleted(true);
    await cache.invalidateQueries();
  }, onError: () => { setQuote(undefined); setConfirmed(false); void eligibility.refetch(); } });
  const reset = () => { setQuote(undefined); setConfirmed(false); action.reset(); };
  return <section className="card modification-section"><h2>Hủy một phần</h2>
    {completed && <p className="notice success" role="status">Đã hủy các hành khách đã chọn. Mã đặt vé và các vé còn lại được giữ nguyên.</p>}
    {eligibility.isPending ? <Loading /> : eligibility.isError ? <ErrorState error={eligibility.error} retry={() => eligibility.refetch()} /> : <>
      {!eligibility.data.eligibility.allowed && <p className="muted">{eligibility.data.eligibility.message}</p>}
      {eligibility.data.eligibility.allowed && !open && <button className="button secondary" onClick={() => { setOpen(true); setCompleted(false); }}>Hủy một phần</button>}
      {open && <div className="detail-section">
        <p>Chọn hành khách cần hủy. Phải giữ lại ít nhất một hành khách; để hủy hết, dùng chức năng hủy toàn bộ đặt vé.</p>
        {!quote ? <fieldset disabled={action.isPending}><legend>Hành khách / ghế</legend>
          {eligibility.data.items.map(i => <label className="detail-row" key={i.bookingItemId}>
            <span><input type="checkbox" checked={selected.includes(i.bookingItemId)} disabled={!i.eligibility.allowed || !eligibility.data.eligibility.allowed}
              onChange={e => { reset(); setSelected(e.target.checked ? [...selected, i.bookingItemId] : selected.filter(id => id !== i.bookingItemId)); }} /> {i.passengerName} · {i.seatCode} · {money(i.amount)}
              {!i.eligibility.allowed && <small> · {i.eligibility.message}</small>}</span>
          </label>)}
        </fieldset> : <><h3>Xem lại trước khi hủy</h3><Review quote={quote} />
          <label><input type="checkbox" checked={confirmed} disabled={action.isPending} onChange={e => setConfirmed(e.target.checked)} /> Tôi xác nhận hủy các hành khách trên và số tiền hoàn / còn phải trả.</label></>}
        {action.isError && <ErrorState error={action.error} />}
        <div className="modification-actions">
          {quote ? <><button className="button" disabled={!confirmed || action.isPending || !eligibility.data.eligibility.allowed} onClick={() => action.mutate(true)}>{action.isPending ? "Đang xử lý…" : "Xác nhận hủy một phần"}</button><button className="button secondary" disabled={action.isPending} onClick={reset}>Chọn lại</button></> : <button className="button" disabled={action.isPending || !canReviewPartial(eligibility.data.items, selected) || !eligibility.data.eligibility.allowed} onClick={() => action.mutate(false)}>Xem số tiền hoàn / còn phải trả</button>}
          <button className="button secondary" disabled={action.isPending} onClick={() => { setOpen(false); reset(); }}>Đóng</button>
        </div>
      </div>}
    </>}
    {history.isError && <ErrorState error={history.error} retry={() => history.refetch()} />}
    {history.data?.map(h => <details className="detail-section" key={h.id}><summary>Đã hủy một phần · {dateTime(h.completedAt)} · {money(h.quote.cancelledAmount)}</summary>
      <p>{h.actorType === "CUSTOMER" ? "Khách hàng" : "Quản trị nhà xe"}{operator ? " · " + h.actorName : ""}</p><Review quote={h.quote} />
    </details>)}
  </section>;
}
