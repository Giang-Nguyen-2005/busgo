import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useLocation, useParams, useSearchParams } from "react-router-dom";
import { get, post } from "../../api/client";
import { useAuth } from "../auth/AuthProvider";
import { canManageOperator } from "../auth/access";
import { ErrorState, Loading } from "../../components/ui";
import { dateTime, money, positiveId } from "../../utils/format";
import { SeatMap } from "../trip/SeatMap";
import type { SeatMapData } from "../../types/customer";
import { cashLabel, modificationLabels, selectionComplete } from "./modificationModel";
import type { Eligibility, ModificationHistory, ModificationMoney, ModificationQuote, ModificationType } from "./modificationModel";

export function ModificationPrice({ value: m }: { value: ModificationMoney }) {
  return <dl className="modification-money">
    <div><dt>Tổng tiền hiện tại</dt><dd>{money(m.currentTotal)}</dd></div>
    <div><dt>Tổng tiền mới</dt><dd>{money(m.newTotal)}</dd></div>
    <div><dt>Chênh lệch giá vé</dt><dd>{m.fareDelta === 0 ? "Không thay đổi giá" : money(m.fareDelta)}</dd></div>
    <div><dt>Đã thu sau hoàn tiền</dt><dd>{money(m.alreadyCollected)}</dd></div>
    <div><dt>{cashLabel(m)}</dt><dd>{money(m.newAmountDue || m.collectionRequired || m.refundRequired)}</dd></div>
  </dl>;
}
function Mapping({ quote: q }: { quote: ModificationQuote }) {
  return <><p>{q.journeyName}</p>{q.type === "TRIP_CHANGE" && <p>{dateTime(q.sourceDeparture)} → {dateTime(q.targetDeparture)}</p>}
    <ul className="modification-mapping">{q.items.map(i => <li key={i.bookingItemId}><strong>{i.oldSeatLabel} → {i.newSeatLabel}</strong></li>)}</ul></>;
}
export function ModificationSection({ bookingId, operator = false }: { bookingId: number; operator?: boolean }) {
  const auth = useAuth(); const base = `${operator ? "/operator" : ""}/bookings/${bookingId}`;
  const eligibility = useQuery({ queryKey: ["modification-eligibility", auth.user?.id, base], queryFn: () => get<Eligibility>(base + "/modification-eligibility") });
  const history = useQuery({ queryKey: ["modification-history", auth.user?.id, base], queryFn: () => get<ModificationHistory[]>(base + "/modifications") });
  const page = `${operator ? "/operator/bookings" : "/my-bookings"}/${bookingId}/modify`;
  const permitted = !operator || canManageOperator(auth.user?.roles);
  return <section className="card modification-section"><h2>{operator ? "Hỗ trợ thay đổi booking" : "Thay đổi đặt vé"}</h2>
    {eligibility.isPending ? <Loading /> : eligibility.isError ? <ErrorState error={eligibility.error} retry={() => eligibility.refetch()} /> : <>
      {permitted && <div className="modification-actions">{([['seat', 'Đổi ghế', eligibility.data.seatChange], ['trip', 'Đổi chuyến', eligibility.data.tripChange]] as const).map(([type, label, rule]) => <div key={type}>{rule.allowed ? <Link className="button secondary" to={page + '?type=' + type}>{label}</Link> : <><strong>{label}</strong><p className="muted">{rule.message}</p></>}</div>)}</div>}
      {!permitted && <p className="muted">Chỉ quản trị nhà xe có quyền hỗ trợ thay đổi.</p>}
    </>}
    {history.isError && <ErrorState error={history.error} retry={() => history.refetch()} />}
    {history.data?.filter(h => operator || h.status === "COMPLETED" || ["HELD", "AWAITING_PAYMENT"].includes(h.status)).map(h => <article className="detail-section" key={h.id}>
      <h3>{h.quote.type === "SEAT_CHANGE" ? "Đổi ghế" : "Đổi chuyến"} · {modificationLabels[h.status]}</h3>
      <p className="fine-print">{dateTime(h.completedAt || h.createdAt)} · {h.actorType === "CUSTOMER" ? "Khách hàng" : "Quản trị nhà xe"}{operator ? ' · ' + h.actorName : ''}</p>
      <Mapping quote={h.quote} /><p>{cashLabel(h.quote.money)}{h.quote.money.collectionRequired > 0 ? ': ' + money(h.quote.money.collectionRequired) : h.quote.money.refundRequired > 0 ? ': ' + money(h.quote.money.refundRequired) : ''}</p>
      {permitted && ["HELD", "AWAITING_PAYMENT"].includes(h.status) && <Link className="button secondary" to={page + '?modification=' + h.id}>Tiếp tục thay đổi</Link>}
    </article>)}
  </section>;
}
export function ModificationPage() {
  const { bookingId } = useParams(); const location = useLocation(); const [params] = useSearchParams();
  const operator = location.pathname.startsWith('/operator/'); const auth = useAuth(); const cache = useQueryClient();
  const base = `${operator ? "/operator" : ""}/bookings/${bookingId}`;
  const back = `${operator ? "/operator/bookings" : "/my-bookings"}/${bookingId}`;
  const type: ModificationType = params.get('type') === 'trip' ? 'TRIP_CHANGE' : 'SEAT_CHANGE';
  const [trip, setTrip] = useState<number>(); const [source, setSource] = useState<number[]>([]); const [selected, setSelected] = useState<number[]>([]);
  const [quote, setQuote] = useState<ModificationQuote>(); const [held, setHeld] = useState<ModificationHistory>();
  const eligibility = useQuery({ queryKey: ["modification-eligibility", auth.user?.id, base], queryFn: () => get<Eligibility>(base + '/modification-eligibility'), enabled: positiveId(bookingId) });
  const resume = useQuery({ queryKey: ["modification", auth.user?.id, base, params.get('modification')], queryFn: () => get<ModificationHistory>(base + '/modifications/' + params.get('modification')), enabled: positiveId(params.get('modification') || undefined) });
  const alternatives = useQuery({ queryKey: ["modification-alternatives", auth.user?.id, base], queryFn: () => get<{ tripId: number; departureTime: string; pickupName: string; dropoffName: string; price: number }[]>(base + '/alternative-trips'), enabled: type === 'TRIP_CHANGE' && !params.has('modification') });
  const targetTrip = type === 'SEAT_CHANGE' ? eligibility.data?.tripId : trip;
  const seatMap = useQuery({ queryKey: ["modification-seats", auth.user?.id, base, targetTrip], queryFn: () => get<SeatMapData>(base + '/modification-seat-availability', { tripId: targetTrip! }), enabled: !!targetTrip && !params.has('modification') });
  const sourceIds = type === 'TRIP_CHANGE' ? eligibility.data?.items.map(i => i.bookingItemId) || [] : source;
  const request = { type, targetTripId: targetTrip, items: sourceIds.map((id, index) => ({ bookingItemId: id, targetSeatId: selected[index] })) };
  const refresh = () => cache.invalidateQueries();
  const action = useMutation({ mutationFn: async (step: 'quote' | 'hold' | 'confirm' | 'cancel') => {
    if (step === 'quote') { setQuote(await post<ModificationQuote>(base + '/modification-quotes', request)); return; }
    if (step === 'hold') { setHeld(await post<ModificationHistory>(base + '/modifications', request)); await refresh(); return; }
    const current = held || resume.data;
    if (current) { setHeld(await post<ModificationHistory>(base + '/modifications/' + current.id + '/' + step, {})); await refresh(); }
  }});
  if (!positiveId(bookingId)) return <p>Mã đặt vé không hợp lệ.</p>;
  if (eligibility.isPending) return <Loading />;
  if (eligibility.isError) return <ErrorState error={eligibility.error} retry={() => eligibility.refetch()} />;
  const e = eligibility.data; const current = held || resume.data;
  const permitted = !operator || canManageOperator(auth.user?.roles);
  const rule = type === 'SEAT_CHANGE' ? e.seatChange : e.tripChange;
  const reset = () => { setQuote(undefined); setSelected([]); };
  return <div className="modification-workspace"><Link className="back-link" to={back}>← Chi tiết đặt vé</Link>
    <div className="page-heading"><div><span className="eyebrow">{operator ? 'HỖ TRỢ THAY ĐỔI BOOKING' : 'QUẢN LÝ ĐẶT VÉ'}</span><h1>{type === 'SEAT_CHANGE' ? 'Đổi ghế' : 'Đổi chuyến'}</h1><p><strong>{e.bookingCode}</strong> · {e.contactName} · {e.source === 'PHONE' ? 'Đặt qua điện thoại' : 'Đặt trực tuyến'}</p></div></div>
    <p className="muted">Mã đặt vé được giữ nguyên. Ghế và vé hiện tại vẫn dùng được cho đến khi thay đổi hoàn tất.</p>
    {action.isError && <ErrorState error={action.error} />}
    {resume.isError && <ErrorState error={resume.error} retry={() => resume.refetch()} />}
    {!permitted ? <p>Chỉ quản trị nhà xe có quyền hỗ trợ thay đổi.</p> : current ? <section className="card">
      <h2>{current.status === 'COMPLETED' ? (current.quote.type === 'SEAT_CHANGE' ? 'Đổi ghế thành công' : 'Đổi chuyến thành công') : modificationLabels[current.status]}</h2>
      <Mapping quote={current.quote} /><ModificationPrice value={current.quote.money} />
      <p>Mã đặt vé: <strong>{current.quote.bookingCode}</strong> · Không thay đổi</p>
      {['HELD', 'AWAITING_PAYMENT'].includes(current.status) ? <><p>Giữ ghế đến {dateTime(current.expiresAt)}. Thu và hoàn tiền trong bước này đều là mô phỏng.</p>
        <div className="modification-actions"><button disabled={action.isPending} onClick={() => action.mutate('confirm')}>Xác nhận thay đổi{current.quote.money.collectionRequired > 0 ? ' và thu thêm mô phỏng' : ''}</button><button className="secondary" disabled={action.isPending} onClick={() => action.mutate('cancel')}>Hủy yêu cầu thay đổi</button></div></> : <><p>{current.status === 'COMPLETED' ? 'Nếu đặt vé đã có vé điện tử, vé bị ảnh hưởng đã được thay thế. Vé khác được giữ nguyên.' : 'Ghế giữ cho yêu cầu đã được giải phóng.'}</p><Link className="button" to={back}>Xem đặt vé và vé hiện tại</Link></>}
    </section> : params.has('modification') ? <Loading /> : !rule.allowed ? <p className="notice info">{rule.message}</p> : <>
      <section className="card"><h2>01 · {type === 'SEAT_CHANGE' ? 'Chọn ghế cần đổi' : 'Chọn chuyến mới'}</h2>
        {type === 'SEAT_CHANGE' ? <div className="modification-actions">{e.items.map(i => <label key={i.bookingItemId}><input type="checkbox" checked={source.includes(i.bookingItemId)} onChange={() => { setSource(source.includes(i.bookingItemId) ? source.filter(id => id !== i.bookingItemId) : [...source, i.bookingItemId]); reset(); }} /> {i.seatLabel}</label>)}</div> : <>
          {alternatives.isPending ? <Loading /> : alternatives.isError ? <ErrorState error={alternatives.error} retry={() => alternatives.refetch()} /> : <label>Chuyến mới<select value={trip || ''} onChange={event => { setTrip(Number(event.target.value)); reset(); }}><option value="">Chọn chuyến</option>{alternatives.data.map(t => <option key={t.tripId} value={t.tripId}>{dateTime(t.departureTime)} · {t.pickupName} → {t.dropoffName} · {money(t.price)}/ghế</option>)}</select></label>}
          <p>Toàn bộ {e.items.length} khách chuyển cùng chuyến. Điểm đón, điểm trả và thông tin khách được giữ nguyên.</p>
        </>}
      </section>
      {targetTrip && sourceIds.length > 0 && <section className="card"><h2>02 · Chọn {sourceIds.length} ghế mới</h2>
        {seatMap.isPending ? <Loading /> : seatMap.isError ? <ErrorState error={seatMap.error} retry={() => seatMap.refetch()} /> : <SeatMap seats={seatMap.data.seats} selected={selected} disabled={action.isPending} onToggle={id => { setQuote(undefined); setSelected(selected.includes(id) ? selected.filter(s => s !== id) : selected.length < sourceIds.length ? [...selected, id] : selected); }} />}
        <ul className="modification-mapping">{e.items.map(i => { const index = sourceIds.indexOf(i.bookingItemId); return <li key={i.bookingItemId}>{i.seatLabel} → {index < 0 ? 'Giữ nguyên' : seatMap.data?.seats.find(s => s.tripSeatId === selected[index])?.seatCode || 'Chưa chọn'}</li>; })}</ul>
        {!quote && <button disabled={!selectionComplete(sourceIds, selected) || action.isPending} onClick={() => action.mutate('quote')}>Xem thay đổi</button>}
      </section>}
      {quote && <section className="card"><h2>03 · Xem thay đổi</h2><Mapping quote={quote} /><ModificationPrice value={quote.money} /><p>Giá và số tiền do hệ thống tính. Ghế được kiểm tra lại khi giữ chỗ.</p><button disabled={action.isPending} onClick={() => action.mutate('hold')}>Giữ ghế và tiếp tục xác nhận</button></section>}
    </>}
  </div>;
}

