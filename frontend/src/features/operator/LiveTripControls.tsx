import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { get, post } from "../../api/client";
import { useAuth } from "../auth/AuthProvider";
import { canManageOperator } from "../auth/access";
import { LiveTripBlock } from "../trip/LiveTripBlock";
import type { LiveTripState } from "../trip/liveTripModel";
import { dateTime } from "../../utils/format";
import { OperationsError } from "./OperationsShared";

type Publication = { delayMinutes: number; reason: string | null; expectedArrivalAt: string | null; eta: boolean };
export function LiveTripControls({ id, state }: { id: number; state?: LiveTripState }) {
  const manage = canManageOperator(useAuth().user?.roles);
  const cache = useQueryClient();
  const [delay, setDelay] = useState(String(state?.delayMinutes ?? 0));
  const [reason, setReason] = useState(state?.delayReason ?? "");
  const [arrival, setArrival] = useState("");
  const [message, setMessage] = useState("");
  const attempt = useRef<{ payload: string; key: string } | null>(null);
  const history = useQuery({ queryKey: ["operator", "trips", id, "live-history"], queryFn: ({ signal }) => get<{ id: number; updateType: string; state: LiveTripState; createdAt: string }[]>(`/operator/trips/${id}/operational-history`, undefined, signal) });
  const mutation = useMutation({ mutationFn: (input: Publication) => {
    const payload = JSON.stringify(input);
    if (attempt.current?.payload !== payload) attempt.current = { payload, key: crypto.randomUUID() };
    return post<LiveTripState>(`/operator/trips/${id}/${input.eta ? "eta" : "delay"}`, { requestKey: attempt.current.key, delayMinutes: input.delayMinutes, reason: input.reason, expectedArrivalAt: input.expectedArrivalAt });
  }, onSuccess: () => { attempt.current = null; setMessage("Đã công bố cập nhật chuyến xe."); void cache.invalidateQueries({ queryKey: ["operator", "trips", id] }); } });
  const active = state && !["COMPLETED", "CANCELLED"].includes(state.lifecycle);
  function publish(eta: boolean) {
    setMessage("");
    // datetime-local is explicitly interpreted in Vietnam business time, independent of browser timezone.
    mutation.mutate({ delayMinutes: Number(delay), reason: reason.trim() || null, expectedArrivalAt: eta ? new Date(arrival + ":00+07:00").toISOString() : null, eta });
  }
  return <section className="card"><LiveTripBlock state={state} />
    {message && <p role="status">{message}</p>}{mutation.isError && <OperationsError error={mutation.error} />}
    {manage && active && <form className="form-stack" onSubmit={e => { e.preventDefault(); publish(false); }}>
      <label className="field">Số phút trễ<input required type="number" min="0" step="1" value={delay} onChange={e => setDelay(e.target.value)} /></label>
      <label className="field">Lý do<input maxLength={500} value={reason} onChange={e => setReason(e.target.value)} /></label>
      <div className="operator-actions"><button disabled={mutation.isPending}>Công bố cập nhật trễ</button>
        <button type="button" className="secondary" disabled={mutation.isPending} onClick={() => { setDelay("0"); setReason(""); mutation.mutate({ delayMinutes: 0, reason: null, expectedArrivalAt: null, eta: false }); }}>Xóa thông báo trễ</button></div>
      {state.lifecycle === "DEPARTED" && <><label className="field">Dự kiến đến (giờ Việt Nam)<input type="datetime-local" value={arrival} onChange={e => setArrival(e.target.value)} /></label>
        <button type="button" disabled={mutation.isPending || !arrival || !Number.isInteger(Number(delay)) || Number(delay)<0} onClick={() => publish(true)}>Cập nhật giờ đến</button></>}
    </form>}
    <details><summary>Lịch sử cập nhật chuyến xe (50 gần nhất)</summary>
      {history.isError && <OperationsError error={history.error} />}
      {history.isPending && <p>Đang tải lịch sử…</p>}
      {history.data?.map(h => <p key={h.id}>{dateTime(h.createdAt)} · {h.state.label} · Dự kiến đến {h.state.expectedArrivalAt ? dateTime(h.state.expectedArrivalAt) : "Theo lịch"}{h.state.delayReason && ` · ${h.state.delayReason}`}</p>)}
      {history.data?.length === 0 && <p>Chưa có cập nhật vận hành.</p>}
    </details>
  </section>;
}
