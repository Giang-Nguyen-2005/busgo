import { statusLabels as domainLabels } from "../../utils/status";
import { useState } from "react";
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { reportsApi } from "../../api/reportsApi";
import { allPages, operatorApi } from "../../api/operatorApi";
import { useAuth } from "../auth/AuthProvider";
import { canManageOperator } from "../auth/access";
import { vietnamToday } from "./dispatch";
import { OperatorPageHeader, OperatorStatusBadge } from "./shared";
import { date, dateTime, money, paymentMethodLabel } from "../../utils/format";
import type { ReportFilters, ReportSummary, ReportLoad, ReportAttendance, ReportMoney, RoutePerformance, TripPerformance, ReportTable } from "../../types/reports";
import "./reports.css";

export const reportPercent = (n: number | null) => n === null ? "Chưa đủ dữ liệu" : new Intl.NumberFormat("vi-VN", { style: "percent", maximumFractionDigits: 1 }).format(n);
export function reportPreset(preset: string, today = vietnamToday()): ReportFilters {
  const date = new Date(today + "T00:00:00Z");
  if (preset === "month") date.setUTCDate(1);
  else date.setUTCDate(date.getUTCDate() - (preset === "7" ? 6 : preset === "30" ? 29 : 0));
  return { fromDate: date.toISOString().slice(0, 10), toDate: today };
}
const tabs = ["Tổng quan", "Thu tiền", "Đặt vé & vé", "Chuyến / Tuyến", "Hành khách"];
const paymentLabels = Object.fromEntries(["MOCK_ONLINE", "QR_TRANSFER", "PAY_ON_BOARD"].map(method => [method, paymentMethodLabel(method)]));
const statusLabels: Record<string,string> = { ...domainLabels.booking, CUSTOMER_CANCELLED: "Khách hủy", OPERATOR_CANCELLED: "Nhà xe hủy", PAYMENT_TIMEOUT: "Quá hạn thanh toán" };
function Metrics({ items }: { items: [string, string | number][] }) {
  return <dl className="report-metrics">{items.map(([label,value])=><div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>;
}
export const reportLoadPercent = (load: ReportLoad, value: number | null) => load.complete && load.sellableCells === 0 ? "Không áp dụng" : reportPercent(value);
export function LoadSummary({ load }: { load: ReportLoad }) {
  return <section><h3>Tải ghế theo chặng · ngày khởi hành</h3>{!load.complete && <p className="notice warning" role="status">Dữ liệu ghế chưa đầy đủ · thiếu {load.missingCells} ô ghế-chặng hoặc cấu trúc chuyến không đầy đủ. Chưa đủ dữ liệu để tính tỷ lệ.</p>}
    <Metrics items={[["Tải đã thanh toán",reportLoadPercent(load,load.paidSegmentLoad)],["Tải đã đặt chỗ",reportLoadPercent(load,load.reservedSegmentLoad)],["Ô ghế-chặng có thể bán",load.sellableCells],["Ô đang giữ (chưa bán)",load.heldCells]]} />
    <p className="fine-print">Đã đặt chỗ gồm đặt vé chưa thu tiền. Tỷ lệ dùng tổng ô ghế-chặng; ghế bán lại ở chặng khác được tính riêng. Chuyến đã hủy không góp vào tải tổng hợp. Không có sức chứa góp vào tải thì tỷ lệ không áp dụng.</p></section>;
}
function Breakdown({ title, values }: { title: string; values: Record<string,number> }) {
  const max=Math.max(1,...Object.values(values));
  return <section className="report-panel"><h3>{title}</h3><div className="report-bars">{Object.entries(values).map(([key,value])=><div className="report-bar" key={key}><span>{statusLabels[key] || paymentLabels[key] || ({ WEB: "Trực tuyến", PHONE: "Qua điện thoại" }[key]) || "Khác"}</span><div aria-hidden="true"><i style={{width:`${value/max*100}%`}} /></div><strong>{value}</strong></div>)}</div></section>;
}
function MoneySummary({ totals }: { totals: ReportMoney }) { return <Metrics items={[["Tổng thu mô phỏng",money(totals.grossMockCollections)],["Hoàn tiền mô phỏng",money(totals.mockRefunds)],["Thu ròng mô phỏng",money(totals.netMockCollections)]]} />; }
function CollectionsChart({ trend }: { trend: ReportSummary["collections"]["trend"] }) {
  const chunk=Math.max(1,Math.ceil(trend.length/30));
  const points=[];
  for(let i=0;i<trend.length;i+=chunk) { const group=trend.slice(i,i+chunk);points.push({label:group.length===1?date(group[0].date):`${date(group[0].date)} → ${date(group.at(-1)!.date)}`,gross:group.reduce((sum,d)=>sum+Number(d.money.grossMockCollections),0),refund:group.reduce((sum,d)=>sum+Number(d.money.mockRefunds),0)}); }
  const max=Math.max(1,...points.flatMap(p=>[p.gross,p.refund]));
  return <figure className="report-chart"><figcaption>So sánh thu / hoàn mô phỏng {chunk>1?`· nhóm ${chunk} ngày`:"· từng ngày"}</figcaption><div className="report-chart-legend"><span>Tổng thu mô phỏng</span><span>Hoàn tiền mô phỏng</span></div><svg viewBox="0 0 600 180" role="img" aria-label="Biểu đồ tổng thu và hoàn tiền mô phỏng; số chính xác ở bảng xu hướng"><line x1="0" y1="150" x2="600" y2="150" stroke="currentColor" />{points.map((p,i)=>{const width=600/Math.max(1,points.length);return <g key={p.label}><title>{p.label}: thu {money(p.gross)}, hoàn {money(p.refund)}</title><rect className="report-chart-gross" x={i*width+width*.1} y={150-p.gross/max*135} width={width*.35} height={p.gross/max*135} /><rect className="report-chart-refund" x={i*width+width*.5} y={150-p.refund/max*135} width={width*.35} height={p.refund/max*135} /></g>;})}<text x="0" y="175" fontSize="12">{trend[0] && date(trend[0].date)}</text><text x="600" y="175" textAnchor="end" fontSize="12">{trend.at(-1) && date(trend.at(-1)!.date)}</text></svg></figure>;
}
export function AttendanceSummary({ attendance: a }: { attendance: ReportAttendance }) {
  return <section><h3>Ghi nhận hành khách · ngày khởi hành</h3><Metrics items={[["Vé đã lên xe",a.boardedTickets],["Vé vắng mặt",a.noShowTickets],["Đã check-in, chưa lên xe",a.checkedInNotBoarded],["Chưa ghi nhận",a.unresolvedAttendance],["Vắng mặt chưa có vé",a.ticketlessNoShows],["Tỷ lệ lên xe",reportPercent(a.boardingRate)],["Tỷ lệ vắng mặt",reportPercent(a.noShowRate)]]} />
    <p className="fine-print">Mẫu số: {a.eligibleResolvedTickets} vé hợp lệ có ghi nhận đã lên xe hoặc vắng mặt. Check-in và chưa ghi nhận chưa được tính vào mẫu số. Vắng mặt chưa có vé được trình bày riêng. Loại đặt vé đã hủy.</p></section>;
}
export function ReportContent({ report: r, tab = "Tổng quan" }: { report: ReportSummary; tab?: string }) {
  const all=tab==="Tổng quan";
  return <div className="report-content" data-from-date={r.metadata.fromDate} data-to-date={r.metadata.toDate} data-source={r.metadata.bookingSource??""}><p className="fine-print">{date(r.metadata.fromDate)} → {date(r.metadata.toDate)} · {r.metadata.timezone} · Cập nhật {dateTime(r.metadata.asOf)}</p>
    {r.bookings.bookingsCreated===0 && r.operations.trips===0 && !r.collections.totals.paidPaymentCount && !r.collections.totals.refundedPaymentCount && <p className="notice info">Không có hoạt động trong kỳ đã chọn. Các số liệu đã biết bằng 0.</p>}
    {(all || tab==="Thu tiền") && <section className="report-panel"><h2>Thu tiền mô phỏng · ngày giao dịch</h2><p className="muted">Theo ngày thu / ngày hoàn tiền. Tất cả thanh toán là mô phỏng.</p><MoneySummary totals={r.collections.totals} /><p>{r.collections.totals.paidPaymentCount} lần thu · {r.collections.totals.refundedPaymentCount} lần hoàn tiền trong kỳ</p>
      <div className="report-table-wrap"><table><caption>Phương thức thanh toán · ngày giao dịch</caption><thead><tr><th>Phương thức</th><th>Tổng thu mô phỏng</th><th>Hoàn tiền mô phỏng</th><th>Thu ròng mô phỏng</th><th>Lần thu / hoàn</th></tr></thead><tbody>{Object.entries(r.collections.byPaymentMethod).map(([method,m])=><tr key={method}><th>{paymentMethodLabel(method)}</th><td>{money(m.grossMockCollections)}</td><td>{money(m.mockRefunds)}</td><td>{money(m.netMockCollections)}</td><td>{m.paidPaymentCount} / {m.refundedPaymentCount}</td></tr>)}</tbody></table></div>
      <CollectionsChart trend={r.collections.trend} /><div className="report-table-wrap report-trend"><table><caption>Xu hướng thu / hoàn mô phỏng theo ngày</caption><thead><tr><th>Ngày</th><th>Tổng thu mô phỏng</th><th>Hoàn tiền mô phỏng</th><th>Thu ròng mô phỏng</th></tr></thead><tbody>{r.collections.trend.map(d=><tr key={d.date}><th>{date(d.date)}</th><td>{money(d.money.grossMockCollections)}</td><td>{money(d.money.mockRefunds)}</td><td>{money(d.money.netMockCollections)}</td></tr>)}</tbody></table></div></section>}
    {(all || tab==="Đặt vé & vé") && <section className="report-panel"><h2>Đặt vé & vé · ngày tạo</h2><Metrics items={[["Đặt vé mới",r.bookings.bookingsCreated],["Đang chờ thu tiền · đặt trong kỳ",r.bookings.currentUnpaidCount],["Vé phát hành",r.bookings.ticketsIssued],["Vé hiện hợp lệ (vé trong kỳ)",r.bookings.validTickets],["Vé đã vô hiệu (vé trong kỳ)",r.bookings.voidTickets],["Đặt vé trong kỳ đã hủy",r.bookings.cancellations],["Đặt vé trong kỳ quá hạn thanh toán",r.bookings.paymentTimeouts]]} /><p className="fine-print">Một booking nhiều ghế vẫn là một booking. Vé phát hành gồm vé về sau bị vô hiệu; không xác nhận đã vận chuyển khách.</p><div className="report-columns"><Breakdown title="WEB / PHONE · booking mới" values={r.bookings.bySource} /><Breakdown title="Trạng thái hiện tại · booking mới" values={r.bookings.byStatus} /></div>
      <h3>Hủy đặt vé · ngày hủy</h3><Metrics items={[["Tổng hủy trong kỳ",r.cancellations.totalCancellations],["Hủy đã hoàn tiền",r.cancellations.refundedCancellations],["Hủy chưa từng thu",r.cancellations.unpaidCancellations],["Số hoàn mô phỏng cho đặt vé hủy trong kỳ",money(r.cancellations.amountRefunded)]]} /><Breakdown title="Lý do hủy" values={r.cancellations.byReason} /></section>}
    {(all || tab==="Chuyến / Tuyến") && <LoadSummary load={r.load} />}
    {(all || tab==="Hành khách") && <AttendanceSummary attendance={r.attendance} />}
    {all && <section className="report-panel"><h2>Vận hành · ngày khởi hành</h2><Metrics items={[["Chuyến trong kỳ",r.operations.trips],["Đang đón khách",r.operations.boardingTrips],["Đang chạy",r.operations.runningTrips],["Sắp chạy thiếu tài xế được phân công",r.operations.upcomingWithoutDriver],["Điểm đón chưa đóng",r.operations.openPickups],["Chuyến dữ liệu ghế không đầy đủ",r.operations.incompleteTrips]]} /></section>}
  </div>;
}
function PerformanceTables({ filters }: { filters: ReportFilters }) {
  const [kind,setKind]=useState<"routes"|"trips">("routes");const [page,setPage]=useState(0);
  const query=useQuery<ReportTable<RoutePerformance|TripPerformance>>({queryKey:["operator","reports",kind,filters,page],queryFn:({signal})=>kind==="routes"?reportsApi.routes({...filters,page,size:20},signal):reportsApi.trips({...filters,page,size:20},signal),retry:1});
  return <section className="report-panel"><h2>Hiệu quả chuyến / tuyến</h2><p className="fine-print">Theo khởi hành dự kiến tại điểm đầu. Thu / hoàn mô phỏng là toàn bộ giao dịch được quy cho nhóm chuyến đã chọn, không phải dòng tiền giao dịch trong kỳ. Mẫu số tải luôn giữ toàn bộ sức chứa khi lọc nguồn / phương thức.</p>
    <div className="report-tabs"><button aria-pressed={kind==="routes"} onClick={()=>{setKind("routes");setPage(0);}}>Tuyến</button><button aria-pressed={kind==="trips"} onClick={()=>{setKind("trips");setPage(0);}}>Chuyến</button></div>
    {query.isPending?<p role="status">Đang tải báo cáo…</p>:query.isError?<p className="notice danger" role="alert">Không thể tải bảng báo cáo. <button onClick={()=>void query.refetch()}>Thử lại</button></p>:query.data && <>
      <div className="report-table-wrap"><table><thead><tr><th>{kind==="routes"?"Tuyến / số chuyến":"Chuyến / khởi hành / xe"}</th><th>Booking / vé hợp lệ</th><th>Thu / hoàn / ròng mô phỏng</th><th>Tải đã trả / đặt chỗ</th><th>Đã lên / vắng / chưa ghi nhận</th><th>{kind==="trips"?"Ghế trống suốt chuyến":"Chuyến đã chạy"}</th></tr></thead><tbody>{query.data.data.map((row: RoutePerformance|TripPerformance)=>{
        const trip="tripId" in row;
        return <tr key={trip?row.tripId:row.routeId}><th>{trip?<><Link to={`/operator/trips/${row.tripId}`}>{row.route} #{row.tripId}</Link><p>{dateTime(row.plannedDeparture)} · {row.bus}</p><OperatorStatusBadge status={row.status} /></>:<>{row.route}<p>{row.tripCount} chuyến</p></>}</th><td>{row.bookings} / {row.validTickets}</td><td>{money(row.money.grossMockCollections)}<br />{money(row.money.mockRefunds)}<br />{money(row.money.netMockCollections)}</td><td>{trip && row.status==="CANCELLED"?<span>Không áp dụng · Chuyến đã hủy, loại khỏi tải tổng hợp</span>:<>{!row.load.complete?<span className="notice warning">Dữ liệu ghế chưa đầy đủ</span>:<>{reportLoadPercent(row.load,row.load.paidSegmentLoad)} / {reportLoadPercent(row.load,row.load.reservedSegmentLoad)}</>}<p>{row.load.paidCells} / {row.load.sellableCells} ô đã trả</p></>}</td><td>{row.attendance.boardedTickets} / {row.attendance.noShowTickets} / {row.attendance.unresolvedAttendance}<p>{row.attendance.ticketlessNoShows} vắng chưa có vé · {row.attendance.checkedInNotBoarded} check-in</p></td><td>{trip?(row.wholeTripAvailableSeats??"Chưa đủ dữ liệu"):row.operatedTripCount}</td></tr>;
      })}</tbody></table></div>{!query.data.data.length && <p>Không có chuyến / tuyến trong kỳ.</p>}
      <nav className="report-pagination" aria-label="Phân trang báo cáo"><button disabled={page===0} onClick={()=>setPage(page-1)}>Trước</button><span>Trang {page+1} · {query.data.pagination.totalElements} dòng</span><button disabled={page+1>=query.data.pagination.totalPages} onClick={()=>setPage(page+1)}>Sau</button></nav>
    </>}
  </section>;
}
export function OperatorReportsPage() {
  const allowed=canManageOperator(useAuth().user?.roles);
  return allowed?<AuthorizedReports />:<p className="notice danger" role="alert">Bạn không có quyền xem báo cáo tài chính. Chỉ quản trị viên nhà xe được truy cập.</p>;
}
function AuthorizedReports() {
  const [filters,setFilters]=useState<ReportFilters>(()=>reportPreset("today"));const [draft,setDraft]=useState(filters);const [tab,setTab]=useState(tabs[0]);const [error,setError]=useState("");const [preset,setPreset]=useState("today");
  const routes=useQuery({queryKey:["operator","report-route-choices"],queryFn:({signal})=>allPages(p=>operatorApi.routes(p,signal)),retry:1});
  const query=useQuery({queryKey:["operator","reports","summary",filters],queryFn:({signal})=>reportsApi.summary(filters,signal),retry:1});
  return <div className="operator-reports"><OperatorPageHeader title="Báo cáo" /><form className="report-filters" onSubmit={e=>{e.preventDefault();const days=(Date.parse(draft.toDate)-Date.parse(draft.fromDate))/86400000;if(!Number.isFinite(days)||days<0||days>=366){setError("Chọn khoảng 1–366 ngày.");return;}setError("");setFilters(draft);}}>
    <label>Kỳ báo cáo<select aria-label="Kỳ báo cáo" value={preset} onChange={e=>{setPreset(e.target.value);if(e.target.value!=="custom"){const dates=reportPreset(e.target.value);setDraft({...draft,...dates});setFilters({...draft,...dates});setError("");}}}><option value="today">Hôm nay</option><option value="7">7 ngày</option><option value="30">30 ngày</option><option value="month">Tháng này</option><option value="custom">Tuỳ chọn</option></select></label>
    <label>Từ ngày<input required type="date" value={draft.fromDate} onChange={e=>{setPreset("custom");setDraft({...draft,fromDate:e.target.value});}} /></label><label>Đến ngày<input required type="date" value={draft.toDate} onChange={e=>{setPreset("custom");setDraft({...draft,toDate:e.target.value});}} /></label>
    <label>Tuyến<select aria-label="Tuyến" value={draft.routeId??""} onChange={e=>setDraft({...draft,routeId:e.target.value?Number(e.target.value):undefined})}><option value="">Tất cả tuyến</option>{routes.data?.map(r=><option key={r.route.id} value={r.route.id}>{r.route.name}</option>)}</select></label>
    <label>ID chuyến<input type="number" min="1" value={draft.tripId??""} onChange={e=>setDraft({...draft,tripId:e.target.value?Number(e.target.value):undefined})} /></label>
    <label>Nguồn booking<select aria-label="Nguồn booking" value={draft.bookingSource??""} onChange={e=>setDraft({...draft,bookingSource:(e.target.value||undefined) as ReportFilters["bookingSource"]})}><option value="">Tất cả nguồn</option><option>WEB</option><option>PHONE</option></select></label>
    <label>Phương thức<select aria-label="Phương thức" value={draft.paymentMethod??""} onChange={e=>setDraft({...draft,paymentMethod:(e.target.value||undefined) as ReportFilters["paymentMethod"]})}><option value="">Tất cả phương thức</option>{Object.entries(paymentLabels).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label><button type="submit">Áp dụng</button>
  </form>{error && <p role="alert" className="notice danger">{error}</p>}<p className="fine-print">Ngày kinh doanh Việt Nam · tối đa 366 ngày · ngày giao dịch và ngày khởi hành được ghi rõ trong từng phần.</p>
    <nav className="report-tabs" aria-label="Phần báo cáo">{tabs.map(t=><button key={t} aria-pressed={tab===t} onClick={()=>setTab(t)}>{t}</button>)}</nav>
    {query.isPending?<p role="status">Đang tải báo cáo…</p>:query.isError?<p className="notice danger" role="alert">Không thể tải báo cáo. Kiểm tra bộ lọc và quyền truy cập. <button onClick={()=>void query.refetch()}>Thử lại</button></p>:query.data && <ReportContent report={query.data} tab={tab} />}
    {tab==="Chuyến / Tuyến" && <PerformanceTables key={JSON.stringify(filters)} filters={filters} />}
  </div>;
}
export function useManagementOverview(enabled: boolean) {
  const filters=reportPreset("today");
  return useQuery({queryKey:["operator","reports","summary",filters],queryFn:({signal})=>reportsApi.summary(filters,signal),refetchInterval:60000,retry:1,enabled});
}
export function ManagementOverview({ query, operational = false }: { query: UseQueryResult<ReportSummary>; operational?: boolean }) {
  const filters=reportPreset("today");
  if (operational) return <section className="card dispatch-attention"><h2>Việc cần theo dõi hôm nay</h2>{query.isPending ? <p role="status">Đang tải vận hành…</p> : query.isError ? <p role="alert">Không thể tải vận hành. <button onClick={() => void query.refetch()}>Thử lại</button></p> : query.data && <><div className="attention-links">{[
    [query.data.operations.upcomingWithoutDriver, "chuyến sắp chạy thiếu tài xế", "/operator/trips?businessDate=" + filters.fromDate],
    [query.data.operations.openPickups, "điểm đón chưa đóng", "/operator/trips?businessDate=" + filters.fromDate],
    [query.data.attendance.unresolvedAttendance, "hành khách chưa ghi nhận", "/operator/trips?businessDate=" + filters.fromDate],
    [query.data.bookings.currentUnpaidCount, "đặt vé chưa thu tiền · tạo hôm nay", "/operator/bookings"],
  ].map(([count, label, href]) => <Link key={label} to={String(href)}><strong>{count}</strong><span>{label}</span><span aria-hidden="true">→</span></Link>)}</div><p className="fine-print">Chọn chuyến để kiểm tra nhân sự, điểm đón và hành khách. Đặt vé chưa thu tính theo ngày tạo; không phải toàn bộ công nợ.</p>{!!query.data.operations.incompleteTrips && <p className="notice warning">{query.data.operations.incompleteTrips} chuyến thiếu dữ liệu ghế.</p>}</>}</section>;
  return <section className="report-panel management-overview"><h2>Kết quả kinh doanh hôm nay</h2><Link to="/operator/reports">Mở báo cáo và chọn kỳ</Link>{query.isPending?<p role="status">Đang tải báo cáo…</p>:query.isError?<p className="notice danger">Không thể tải tổng quan quản lý. <button onClick={()=>void query.refetch()}>Thử lại</button></p>:query.data && <><Metrics items={[["Đặt vé mới · ngày tạo",query.data.bookings.bookingsCreated],["Vé hợp lệ · ngày phát hành",query.data.bookings.validTickets],["Thu mô phỏng · ngày thu",money(query.data.collections.totals.grossMockCollections)],["Hoàn mô phỏng · ngày hoàn",money(query.data.collections.totals.mockRefunds)]]} /><p className="fine-print">Giờ Việt Nam · {dateTime(query.data.metadata.asOf)}</p></>}</section>;
}
