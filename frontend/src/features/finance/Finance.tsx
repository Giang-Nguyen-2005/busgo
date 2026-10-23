import { useState } from "react";
import { useLocation } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { get, post } from "../../api/client";
import { getSessionVersion } from "../../api/session";
import { useAuth } from "../auth/AuthProvider";
import { canManageOperator, isSystemAdmin } from "../auth/access";
import { financeDate, financeLabel, financeMoney as money, validFinancePeriod, policyLifecycle } from "./financeModel";
import "./finance.css";

type Page<T> = { data: T[]; page: number; size: number; total: number };
type Totals = { collections: number; refunds: number; netCollections: number; commissionAccrual: number;
  commissionReversal: number; netCommission: number; operatorPayable: number; platformReceivable: number;
  unknownAmount: number; unpricedAmount: number; grossContext: number; discountContext: number;
  eventCount: number; sourceCount: number; missingCount: number; sourceCollections: number; sourceRefunds: number;
  unknownCount: number; unpricedCount: number };
type Overview = { period: { from: string; to: string }; operatorName: string; reconciliationStatus: string; totals: Totals };
type Policy = { id: number; operatorId: number; rate: number; validFrom: string; validTo: string | null; state: string; reason: string };
type Event = { id: number; sourceKey: string; eventType: string; bookingId: number; paymentId: number;
  channel: string; amount: number; commission: number | null; rate: number | null; occurredAt: string };
type Run = { id: number; operatorId: number; from: string; to: string; status: string; exceptionCount: number;
  projectedCount: number; completedAt: string; snapshot: Overview };
type Statement = { id: number; runId: number; issuedAt: string; snapshot: Overview };
type Exception = { id: number; sourceKey: string; reason: string };
const summaryLabels: [keyof Totals, string][] = [
  ["grossContext", "Giá vé gốc (ngữ cảnh vé ban đầu)"], ["discountContext", "Giảm giá nhà xe (vé ban đầu)"],
  ["collections", "Tiền khách đã thanh toán (mô phỏng)"], ["refunds", "Tiền hoàn (mô phỏng)"],
  ["netCollections", "Thu trừ hoàn (mô phỏng)"], ["commissionAccrual", "Hoa hồng phát sinh"],
  ["commissionReversal", "Hoa hồng hoàn lại"], ["netCommission", "Hoa hồng BusGo"],
  ["operatorPayable", "Nền tảng phải trả nhà xe (nội bộ)"], ["platformReceivable", "Hoa hồng nhà xe phải trả (nội bộ)"],
  ["unknownAmount", "Giá trị ròng chưa rõ bên thu"], ["unpricedAmount", "Giá trị ròng chưa tính hoa hồng"],
];
export function Summary({ value }: { value: Overview }) {
  return <><p>{value.operatorName} · Kỳ {value.period.from} – {value.period.to} · Giờ Việt Nam · VND</p>
    <p>Đối soát: {financeLabel(value.reconciliationStatus)} · {value.totals.unknownCount} sự kiện chưa rõ bên thu · {value.totals.unpricedCount} sự kiện chưa có hoa hồng.</p>
    <section className="finance-reconciliation" aria-label="Nguồn và sổ nội bộ"><div><h2>Nguồn thanh toán</h2><p>Thu <strong>{money(value.totals.sourceCollections)}</strong></p><p>Hoàn {money(value.totals.sourceRefunds)}</p><small>{value.totals.sourceCount} sự kiện nguồn</small></div><div><h2>Sổ đã ghi</h2><p>Thu <strong>{money(value.totals.collections)}</strong></p><p>Hoàn {money(value.totals.refunds)}</p><small>{value.totals.eventCount} bút toán</small></div><div><h2>Cần xác minh</h2><p>{value.totals.missingCount} sự kiện chưa ghi sổ</p><p>{value.totals.unknownCount} chưa rõ bên thu · {money(value.totals.unknownAmount)}</p><p>{value.totals.unpricedCount} chưa tính hoa hồng · {money(value.totals.unpricedAmount)}</p></div></section>
    <p className="finance-liabilities">Phải trả nhà xe (nội bộ): <strong>{money(value.totals.operatorPayable)}</strong> · Phải thu hoa hồng (nội bộ): <strong>{money(value.totals.platformReceivable)}</strong>. Chưa xác nhận thanh toán thực tế.</p>
    <p>Nguồn thanh toán ghi nhận: thu {money(value.totals.sourceCollections)}, hoàn {money(value.totals.sourceRefunds)}.</p>
    <p>{value.totals.eventCount} bút toán / {value.totals.sourceCount} sự kiện nguồn · {value.totals.missingCount} sự kiện chưa ghi sổ.
      Số tổng hợp bên dưới lấy từ sổ đã ghi; kết quả đối soát chi tiết nằm trong từng lần chạy.</p>
    <details className="finance-metrics"><summary>Chi tiết số liệu sổ nội bộ</summary><div className="finance-summary">{summaryLabels.map(([key, label]) =>
      <div key={key}><span>{label}</span><strong>{money(value.totals[key])}</strong></div>)}</div></details></>;
}
function Pager({ page, total, set }: { page: number; total: number; set: (n: number) => void }) {
  return <div className="finance-pager"><button disabled={page === 0} onClick={() => set(page - 1)}>Trước</button>
    <span>Trang {page + 1} · {total} bản ghi</span><button disabled={(page + 1) * 20 >= total} onClick={() => set(page + 1)}>Sau</button></div>;
}
export function FinancePage() {
  const auth = useAuth(); const platform = useLocation().pathname.startsWith("/admin/");
  const allowed = platform ? isSystemAdmin(auth.user?.roles) : canManageOperator(auth.user?.roles);
  if (!allowed) return <p role="alert">Bạn không có quyền xem báo cáo tài chính.</p>;
  return <FinanceWorkspace key={`${auth.user?.id}-${platform}-${getSessionVersion()}`} platform={platform} />;
}
function FinanceWorkspace({ platform }: { platform: boolean }) {
  const auth = useAuth(); const cache = useQueryClient(); const base = `/${platform ? "admin" : "operator"}/finance`;
  const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(new Date());
  const [from, setFrom] = useState(today.slice(0, 8) + "01"), [to, setTo] = useState(today);
  const [operator, setOperator] = useState(""), [operatorPage, setOperatorPage] = useState(0);
  const [bookingId, setBookingId] = useState(""), [channel, setChannel] = useState("");
  const [tab, setTab] = useState("ledger"), [page, setPage] = useState(0), [selectedRun, setSelectedRun] = useState<number>();
  const [exceptionPage, setExceptionPage] = useState(0), [error, setError] = useState(""), [notice, setNotice] = useState("");
  const [rate, setRate] = useState(""), [start, setStart] = useState(""), [end, setEnd] = useState(""), [reason, setReason] = useState("");
  const [confirmation, setConfirmation] = useState<{ id: number; rate: number; start: string }>();
  const periodValid = validFinancePeriod(from, to);
  const params = { from, to, operatorId: operator || undefined, page, size: 20, bookingId: bookingId || undefined, channel: channel || undefined };
  const identity = ["finance", auth.user?.id, auth.user?.roles.join(","), getSessionVersion(), base, operator];
  const overview = useQuery({ queryKey: [...identity, "overview", from, to], queryFn: ({ signal }) => get<Overview>(base + "/overview", params, signal), enabled: periodValid });
  const operators = useQuery({ queryKey: [...identity, "operators", operatorPage], queryFn: ({ signal }) =>
    get<Page<{ id: number; name: string }>>(base + "/operators", { page: operatorPage, size: 20 }, signal), enabled: platform });
  const policies = useQuery({ queryKey: [...identity, "policies", page], queryFn: ({ signal }) => get<Page<Policy>>(base + "/policies", params, signal), enabled: tab === "policies" });
  const events = useQuery({ queryKey: [...identity, "ledger", from, to, page, bookingId, channel], queryFn: ({ signal }) => get<Page<Event>>(base + "/ledger", params, signal), enabled: tab === "ledger" && periodValid });
  const comparison = useQuery({ queryKey: [...identity, "comparison", from, to, page], queryFn: ({ signal }) => get<Page<{ id: number; name: string; totals: Totals }>>(base + "/operator-comparison", params, signal), enabled: platform && tab === "comparison" && periodValid });
  const runs = useQuery({ queryKey: [...identity, "runs", page], queryFn: ({ signal }) => get<Page<Run>>(base + "/reconciliations", params, signal), enabled: tab === "runs" });
  const statements = useQuery({ queryKey: [...identity, "statements", page], queryFn: ({ signal }) => get<Page<Statement>>(base + "/statements", params, signal), enabled: tab === "statements" });
  const exceptions = useQuery({ queryKey: [...identity, "exceptions", selectedRun, exceptionPage], queryFn: ({ signal }) =>
    get<Page<Exception>>(`${base}/reconciliations/${selectedRun}/exceptions`, { operatorId: operator || undefined, page: exceptionPage, size: 20 }, signal), enabled: !!selectedRun && tab === "runs" });
  const mutation = useMutation({ mutationFn: ({ path, body }: { path: string; body?: object }) => post(base + path, body),
    onSuccess: () => { setError(""); setNotice("Đã lưu dữ liệu nội bộ."); setConfirmation(undefined); void cache.invalidateQueries({ queryKey: ["finance"] }); },
    onError: () => setError("Không thể lưu. Kiểm tra kỳ hiệu lực, lịch trùng và quyền truy cập rồi thử lại.") });
  const loading = overview.isFetching || [events, policies, runs, statements, exceptions, comparison].some(q => q.isFetching);
  const failed = overview.isError || operators.isError || [events, policies, runs, statements, exceptions, comparison].some(q => q.isError);
  const changeTab = (value: string) => { setTab(value); setPage(0); setSelectedRun(undefined); };
  return <section className="finance"><h1>Tài chính / Sổ nội bộ & Hoa hồng</h1>
    <p className="finance-banner"><strong>MÔ PHỎNG / NỘI BỘ</strong> — Số liệu công nợ nội bộ, chưa xác nhận tiền ngân hàng hoặc chi trả thực tế.
      Khoản chưa rõ bên thu hoặc chưa có hoa hồng không được cộng vào công nợ đã xác định.</p>
    <div className="finance-controls">
      <label>Từ ngày<input type="date" value={from} onChange={e => { setFrom(e.target.value); setPage(0); }} /></label>
      <label>Đến ngày<input type="date" value={to} onChange={e => { setTo(e.target.value); setPage(0); }} /></label>
      {platform && <label>Nhà xe<select aria-label="Nhà xe" value={operator} onChange={e => { setOperator(e.target.value); setPage(0); setSelectedRun(undefined); }}>
        <option value="">Tất cả nhà xe</option>{operators.data?.data.map(o => <option key={o.id} value={o.id}>{o.name}</option>)}</select></label>}
    </div>
    {platform && operators.data && operators.data.total > 20 && <Pager page={operatorPage} total={operators.data.total} set={setOperatorPage} />}
    {!periodValid && <p role="alert">Chọn kỳ từ 1 đến 93 ngày.</p>}
    {loading && <p role="status">Đang tải số liệu…</p>}
    {failed && <p role="alert">Không tải được số liệu. <button onClick={() => void cache.invalidateQueries({ queryKey: ["finance"] })}>Thử lại</button></p>}
    {error && <p role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    <nav className="finance-tabs" aria-label="Báo cáo tài chính">{[["ledger", "Giao dịch"], ["policies", "Chính sách hoa hồng"], ["runs", "Đối soát"], ["statements", "Bảng kê"], ...(platform ? [["comparison", "So sánh nhà xe"]] : [])].map(([key, label]) =>
      <button key={key} aria-pressed={tab === key} onClick={() => changeTab(key)}>{label}</button>)}</nav>
    {periodValid && overview.data && <Summary value={overview.data} />}
    {tab === "ledger" && <div className="finance-controls"><label>Mã đặt vé<input type="number" min="1" value={bookingId} onChange={e => { setBookingId(e.target.value); setPage(0); }} /></label>
      <label>Bên thu<select aria-label="Bên thu" value={channel} onChange={e => { setChannel(e.target.value); setPage(0); }}><option value="">Tất cả</option>{["PLATFORM_MEDIATED_SIMULATED", "OPERATOR_RECORDED_SIMULATED", "UNKNOWN_OR_UNVERIFIED"].map(c => <option key={c} value={c}>{financeLabel(c)}</option>)}</select></label></div>}
    {tab === "comparison" && comparison.data && <><div className="finance-table" role="region" aria-label="Bảng số liệu — cuộn ngang để xem đầy đủ" tabIndex={0}><table><caption>So sánh nhà xe trong kỳ</caption><thead><tr><th>Nhà xe</th><th>Thu trừ hoàn</th><th>Hoa hồng</th><th>Phải trả nhà xe</th><th>Phải thu hoa hồng</th><th>Chưa xác định</th></tr></thead><tbody>
      {comparison.data.data.map(o => <tr key={o.id}><td><button onClick={() => { setOperator(String(o.id)); changeTab("ledger"); }}>{o.name}</button></td><td>{money(o.totals.netCollections)}</td><td>{money(o.totals.netCommission)}</td><td>{money(o.totals.operatorPayable)}</td><td>{money(o.totals.platformReceivable)}</td><td>{o.totals.unknownCount} bên thu / {o.totals.unpricedCount} hoa hồng</td></tr>)}</tbody></table></div><Pager page={page} total={comparison.data.total} set={setPage} /></>}
    {tab === "ledger" && events.data && <><div className="finance-table" role="region" aria-label="Bảng số liệu — cuộn ngang để xem đầy đủ" tabIndex={0}><table><caption>Thu và hoàn theo từng sự kiện nguồn</caption><thead><tr><th>Thời điểm</th><th>Đặt vé</th><th>Loại</th><th>Bên thu</th><th>Số tiền</th><th>Hoa hồng</th></tr></thead>
      <tbody>{events.data.data.map(e => <tr key={e.id}><td>{financeDate(e.occurredAt)}</td><td>#{e.bookingId}<small>Khoản thu #{e.paymentId}</small></td><td>{financeLabel(e.eventType)}</td>
        <td>{financeLabel(e.channel)}</td><td>{money(e.amount)}</td><td>{money(e.commission)}<small>{e.rate == null ? "Chưa cấu hình" : `${e.rate}%`}</small></td></tr>)}</tbody></table></div>
      {!events.data.total && <p>Chưa có giao dịch trong kỳ.</p>}<Pager page={page} total={events.data.total} set={setPage} /></>}
    {tab === "policies" && <>{platform && <form className="finance-form" onSubmit={e => {
      e.preventDefault(); if (!operator) { setError("Chọn một nhà xe để tạo chính sách."); return; }
      mutation.mutate({ path: "/policies", body: { operatorId: Number(operator), rate: Number(rate), validFrom: start + ":00+07:00", validTo: end ? end + ":00+07:00" : null, reason } });
    }}><h2>Tạo chính sách hoa hồng</h2><p>Tính trên tiền khách thực trả sau giảm giá nhà xe. Không có tỷ lệ mặc định. Thời điểm theo giờ Việt Nam.</p>
      <label>Tỷ lệ (%)<input type="number" min="0" max="100" step="0.0001" required value={rate} onChange={e => setRate(e.target.value)} /></label>
      <label>Hiệu lực từ (giờ Việt Nam)<input type="datetime-local" required value={start} onChange={e => setStart(e.target.value)} /></label>
      <label>Kết thúc (không bắt buộc)<input type="datetime-local" value={end} onChange={e => setEnd(e.target.value)} /></label>
      <label>Lý do<textarea required maxLength={500} value={reason} onChange={e => setReason(e.target.value)} /></label>
      <button disabled={mutation.isPending}>Lưu bản nháp</button></form>}
      {policies.data?.data.map(p => <article key={p.id}><h3>Chính sách #{p.id} · Nhà xe #{p.operatorId} · {p.rate}%</h3><p>{financeDate(p.validFrom)} → {p.validTo ? financeDate(p.validTo) : "Không giới hạn"} · {policyLifecycle(p.state,p.validFrom,p.validTo)}</p>
        <p>{p.reason}</p>{platform && p.state === "DRAFT" && <button onClick={() => setConfirmation({ id: p.id, rate: p.rate, start: p.validFrom })}>Kích hoạt</button>}</article>)}
      {policies.data && !policies.data.total && <p>Chưa cấu hình chính sách. Giao dịch được ghi nhận nhưng hoa hồng chưa xác định.</p>}
      {policies.data && <Pager page={page} total={policies.data.total} set={setPage} />}
      {confirmation && <div role="region" aria-label="Xác nhận chính sách" className="finance-confirm"><h3>Xác nhận kích hoạt {confirmation.rate}%</h3>
        <p>Hiệu lực {financeDate(confirmation.start)}. Chính sách trước đó sẽ kết thúc tại thời điểm này nếu bị thay thế. Các giao dịch đã ghi sổ giữ nguyên.</p>
        <button disabled={mutation.isPending} onClick={() => mutation.mutate({ path: `/policies/${confirmation.id}/activate` })}>Xác nhận kích hoạt</button><button onClick={() => setConfirmation(undefined)}>Quay lại</button></div>}</>}
    {tab === "runs" && <>{platform && <div className="finance-controls"><button disabled={!operator || !periodValid || mutation.isPending} onClick={() =>
      mutation.mutate({ path: "/reconciliations", body: { operatorId: Number(operator), from, to, projectMissing: false } })}>Kiểm tra kỳ đã chọn</button>
      <button disabled={!operator || !periodValid || mutation.isPending} onClick={() => mutation.mutate({ path: "/reconciliations", body: { operatorId: Number(operator), from, to, projectMissing: true } })}>Bổ sung sự kiện thiếu & đối soát</button>
      {!operator && <p>Chọn một nhà xe để đối soát.</p>}</div>}
      {runs.data?.data.map(r => <article key={r.id}><h3>Đối soát #{r.id} · {financeLabel(r.status)}</h3>
        <p>{r.from} – {r.to} · {r.exceptionCount} vấn đề · Bổ sung {r.projectedCount} bút toán</p>
        <button onClick={() => { setSelectedRun(r.id); setExceptionPage(0); }}>Xem vấn đề</button>
        {platform && <button disabled={mutation.isPending} onClick={() => mutation.mutate({ path: `/reconciliations/${r.id}/statement` })}>Phát hành bảng kê nội bộ</button>}</article>)}
      {runs.data && !runs.data.total && <p>Chưa có lần đối soát.</p>}{runs.data && <Pager page={page} total={runs.data.total} set={setPage} />}
      {selectedRun && exceptions.data && <article><h3>Vấn đề của lần đối soát #{selectedRun}</h3>{exceptions.data.data.map(e => <p key={e.id}>{e.sourceKey.startsWith("refund:") ? "Khoản hoàn" : "Khoản thu"} #{e.sourceKey.split(":")[1]} · {financeLabel(e.reason)}</p>)}
        {!exceptions.data.total && <p>Không phát hiện sai lệch.</p>}<Pager page={exceptionPage} total={exceptions.data.total} set={setExceptionPage} /></article>}</>}
    {tab === "statements" && <>{statements.data?.data.map(s => <details key={s.id}><summary>Bảng kê nội bộ #{s.id} · {financeDate(s.issuedAt)}</summary>
      <p>MÔ PHỎNG / NỘI BỘ · Ảnh chụp số liệu bất biến từ đối soát #{s.runId}. Không xác nhận đã chi trả. Xem vấn đề trong lịch sử đối soát tương ứng.</p><Summary value={s.snapshot} /></details>)}
      {statements.data && !statements.data.total && <p>Chưa phát hành bảng kê.</p>}{statements.data && <Pager page={page} total={statements.data.total} set={setPage} />}</>}
  </section>;
}
