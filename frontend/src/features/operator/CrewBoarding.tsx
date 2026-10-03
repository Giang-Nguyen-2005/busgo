import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { operationsApi } from "../../api/operationsApi";
import { operatorApi } from "../../api/operatorApi";
import type { AttendanceRow, BoardingStatus, Capability, Employee } from "../../types/operations";
import { useAuth } from "../auth/AuthProvider";
import { canManageOperator } from "../auth/access";
import { OperatorError, QueryState } from "./shared";
import { dateTime, money, paymentMethodLabel, paymentStatusLabel } from "../../utils/format";

export const boardingLabels: Record<BoardingStatus, string> = { EXPECTED: "Chưa ghi nhận", CHECKED_IN: "Đã check-in", BOARDED: "Đã lên xe", NO_SHOW: "Vắng mặt" };
export function EmployeeDirectory() {
  const manage = canManageOperator(useAuth().user?.roles);
  const query = useQuery({ queryKey: ["operator", "employees"], queryFn: operationsApi.employees });
  const [edit, setEdit] = useState<Employee | null | undefined>();
  return <><h1>Nhân sự vận hành</h1><p>Người làm công việc vận tải. Tài khoản đăng nhập được quản lý riêng.</p>
    {manage && <button onClick={() => setEdit(null)}>Thêm nhân sự</button>}
    {edit !== undefined && <EmployeeForm key={edit?.id || "new"} employee={edit} done={() => { setEdit(undefined); void query.refetch(); }} />}
    <QueryState query={query}>{employees => <div className="operator-manifest-list">{employees.map(e => <article className="card" key={e.id}>
      <h2>{e.employeeCode} · {e.fullName}</h2><p>{e.phone} · {e.status === "ACTIVE" ? "Hoạt động" : "Ngừng hoạt động"}</p>
      <p>{e.capabilities.map(c => c === "DRIVER" ? "Tài xế" : "Phụ xe").join(" · ")}</p>
      {e.capabilities.includes("DRIVER") && <p>Hạn giấy phép: {e.licenceExpiryDate}</p>}{manage && <button className="secondary" onClick={() => setEdit(e)}>Chỉnh sửa {e.employeeCode}</button>}
    </article>)}{!employees.length && <p>Chưa có nhân sự vận hành.</p>}</div>}</QueryState></>;
}
function EmployeeForm({ employee: e, done }: { employee: Employee | null; done: () => void }) {
  const [value, setValue] = useState<Omit<Employee, "id">>(e || { employeeCode: "", fullName: "", phone: "", status: "ACTIVE", capabilities: [], licenceNumber: "", licenceClass: "", licenceExpiryDate: "", version: 0 });
  const save = useMutation({ mutationFn: () => operationsApi.saveEmployee(value, e?.id), onSuccess: done });
  const field = (key: "employeeCode" | "fullName" | "phone" | "licenceNumber" | "licenceClass" | "licenceExpiryDate", label: string, max?: number) => <label className="field">{label}<input required maxLength={max} type={key === "licenceExpiryDate" ? "date" : "text"} value={value[key] || ""} onChange={ev => setValue({ ...value, [key]: ev.target.value })} /></label>;
  return <form className="card form-stack operator-form" onSubmit={ev => { ev.preventDefault(); save.mutate(); }}><h2>{e ? "Chỉnh sửa nhân sự" : "Thêm nhân sự"}</h2><fieldset disabled={save.isPending}>
    {field("employeeCode", "Mã nhân sự", 50)}{field("fullName", "Họ tên", 100)}{field("phone", "Điện thoại", 20)}
    <label className="field">Trạng thái<select aria-label="Trạng thái" value={value.status} onChange={ev => setValue({ ...value, status: ev.target.value as Employee["status"] })}><option value="ACTIVE">Hoạt động</option><option value="INACTIVE">Ngừng hoạt động</option></select></label>
    {(["DRIVER", "ATTENDANT"] as Capability[]).map(c => <label key={c}><input type="checkbox" checked={value.capabilities.includes(c)} onChange={ev => setValue({ ...value, capabilities: ev.target.checked ? [...value.capabilities, c] : value.capabilities.filter(v => v !== c) })} /> {c === "DRIVER" ? "Tài xế" : "Phụ xe"}</label>)}
    {value.capabilities.includes("DRIVER") && <>{field("licenceNumber", "Số giấy phép", 50)}{field("licenceClass", "Hạng giấy phép", 30)}{field("licenceExpiryDate", "Ngày hết hạn giấy phép")}<p className="fine-print">Hạng giấy phép được lưu để tra cứu; hệ thống chưa xác nhận tuân thủ pháp luật.</p></>}
    <button disabled={!value.capabilities.length}>Lưu nhân sự</button> <button type="button" className="secondary" onClick={done}>Đóng</button>
    </fieldset>{save.isError && <OperatorError error={save.error} />}</form>;
}
export function CrewSection({ tripId, status }: { tripId: number; status: string }) {
  const manage = canManageOperator(useAuth().user?.roles);
  const cache = useQueryClient();
  const crew = useQuery({ queryKey: ["operator", "crew", tripId], queryFn: () => operationsApi.crew(tripId) });
  const employees = useQuery({ queryKey: ["operator", "employees"], queryFn: operationsApi.employees });
  const [employeeId, setEmployeeId] = useState(0); const [duty, setDuty] = useState<Capability>("DRIVER");
  const change = useMutation({ mutationFn: (assignments: { employeeId: number; duty: Capability }[]) => operationsApi.replaceCrew(tripId, assignments), onSuccess: async () => { await cache.invalidateQueries({ queryKey: ["operator", "crew", tripId] }); } });
  const editable = manage && ["SCHEDULED", "BOARDING"].includes(status);
  return <section className="card"><h2>Nhân sự chuyến xe</h2><QueryState query={crew}>{data => <>
    <p className={data.ready ? "notice" : "operator-overdue"}>{data.ready ? "Nhân sự sẵn sàng" : data.warning}</p>
    {data.assignments.map(a => <p key={a.id}>{a.duty === "DRIVER" ? "Tài xế" : "Phụ xe"}: <strong>{a.fullName}</strong> {a.licenceExpiryDate && `· Hạn giấy phép ${a.licenceExpiryDate}`} {editable && <button className="secondary" disabled={change.isPending} onClick={() => change.mutate(data.assignments.filter(v => v.id !== a.id).map(v => ({ employeeId: v.employeeId, duty: v.duty })))}>Gỡ {a.fullName}</button>}</p>)}
    {editable && <form className="operator-filters" onSubmit={ev => { ev.preventDefault(); change.mutate([...data.assignments.map(v => ({ employeeId: v.employeeId, duty: v.duty })), { employeeId, duty }]); }}>
      <label className="field">Nhiệm vụ<select aria-label="Nhiệm vụ" value={duty} onChange={ev => { setDuty(ev.target.value as Capability); setEmployeeId(0); }}><option value="DRIVER">Tài xế</option><option value="ATTENDANT">Phụ xe</option></select></label>
      <label className="field">Nhân sự<select aria-label="Nhân sự" value={employeeId} onChange={ev => setEmployeeId(Number(ev.target.value))}><option value={0}>Chọn nhân sự</option>{employees.data?.filter(e => e.status === "ACTIVE" && e.capabilities.includes(duty)).map(e => <option key={e.id} value={e.id}>{e.employeeCode} · {e.fullName}</option>)}</select></label>
      <button disabled={!employeeId || change.isPending}>Phân công</button><Link to="/operator/employees">Nhân sự vận hành</Link>
    </form>}</>}</QueryState>{employees.isError && <OperatorError error={employees.error} />}{change.isError && <OperatorError error={change.error} />}</section>;
}
export function AttendanceContent({ rows, manage, tripStatus, closedStops, action, pending }: { rows: AttendanceRow[]; manage: boolean; tripStatus: string; closedStops: number[]; pending: boolean; action: (row: AttendanceRow, command: "check-in" | "board" | "no-show" | "payment") => void }) {
  const [filter, setFilter] = useState(""); const [pickup, setPickup] = useState(0); const [search, setSearch] = useState("");
  const shown = rows.filter(p => (!pickup || p.pickupStopId === pickup) && (!filter || (filter === "UNPAID" ? p.paymentStatus !== "PAID" : (p.boardingStatus || "EXPECTED") === filter)) && `${p.seatCode} ${p.passengerName} ${p.phone} ${p.bookingCode}`.toLowerCase().includes(search.toLowerCase()));
  return <><div className="operator-filters"><label className="field">Trạng thái hành khách<select aria-label="Trạng thái hành khách" value={filter} onChange={ev => setFilter(ev.target.value)}><option value="">Tất cả</option>{Object.entries(boardingLabels).map(([s,l]) => <option key={s} value={s}>{l}</option>)}<option value="UNPAID">Chưa thanh toán</option></select></label>
    <label className="field">Theo điểm đón<select aria-label="Theo điểm đón" value={pickup} onChange={ev => setPickup(Number(ev.target.value))}><option value={0}>Tất cả điểm đón</option>{[...new Map(rows.map(p => [p.pickupStopId,p.pickupName])).entries()].map(([id,name]) => <option key={id} value={id}>{name}</option>)}</select></label>
    <label className="field">Tìm hành khách<input type="search" value={search} onChange={ev => setSearch(ev.target.value)} /></label></div>
    <div className="operator-manifest-list">{shown.map(p => {
      const state = p.boardingStatus || "EXPECTED";
      const open = ["BOARDING", "DEPARTED"].includes(tripStatus) && !closedStops.includes(p.pickupStopId);
      const eligible = open && !!p.ticketId && p.paymentStatus === "PAID";
      return <article className="card" key={p.bookingItemId}><h3>{p.seatCode} · {p.passengerName}</h3><p>{p.phone} · <Link to={`/operator/bookings/${p.bookingId}`}>{p.bookingCode}</Link></p><p>{p.pickupName} → {p.dropoffName}</p>
        <p>{paymentMethodLabel(p.paymentMethod)} · {paymentStatusLabel(p.paymentStatus)} · <strong>{boardingLabels[state]}</strong></p>
        {p.pickupTime && <p className="fine-print">Đón dự kiến: {dateTime(p.pickupTime)}{Date.parse(p.pickupTime) < Date.now() && !["BOARDED", "NO_SHOW"].includes(state) && " · Đã qua giờ dự kiến; chưa xác nhận xe thực tế đến điểm đón."}</p>}
        {p.paymentStatus !== "PAID" && <p className="operator-overdue">Chưa thu tiền</p>}
        {p.paymentStatus !== "PAID" && p.paymentBlocked && <p className="fine-print">Đặt vé đã có hành khách vắng mặt; không thể thu tiền sau đó. Ghế vẫn được giữ.</p>}
        {manage && <div className="operator-actions">{p.paymentStatus !== "PAID" && !p.paymentBlocked && state !== "NO_SHOW" && p.paymentMethod !== "MOCK_ONLINE" && <button disabled={pending || !["SCHEDULED","BOARDING","DEPARTED"].includes(tripStatus)} onClick={() => action(p,"payment")}>Ghi nhận đã thu tiền</button>}
          {state === "EXPECTED" && <button disabled={pending || !eligible} onClick={() => action(p,"check-in")}>Check-in</button>}
          {state === "CHECKED_IN" && <button disabled={pending || !eligible} onClick={() => action(p,"board")}>Lên xe</button>}
          {["EXPECTED","CHECKED_IN"].includes(state) && <button className="secondary" disabled={pending || !(eligible || (open && p.source === "PHONE" && p.paymentMethod === "PAY_ON_BOARD" && !p.ticketId))} onClick={() => action(p,"no-show")}>Vắng mặt</button>}</div>}
      </article>;
    })}{!shown.length && <p>Không có hành khách phù hợp.</p>}</div></>;
}
export function BoardingManifest({ tripId, tripStatus }: { tripId: number; tripStatus: string }) {
  const manage = canManageOperator(useAuth().user?.roles); const cache = useQueryClient();
  const rows = useQuery({ queryKey: ["operator", "attendance", tripId], queryFn: () => operationsApi.attendance(tripId), refetchInterval: 30_000 });
  const pickups = useQuery({ queryKey: ["operator", "pickups", tripId], queryFn: () => operationsApi.pickups(tripId), refetchInterval: 30_000 });
  const history = useQuery({ queryKey: ["operator", "history", tripId], queryFn: () => operationsApi.history(tripId) });
  const [review, setReview] = useState<{ row: AttendanceRow; command: "payment" | "no-show" } | null>(null); const [reason, setReason] = useState(""); const [closeReview,setCloseReview] = useState<number | null>(null);
  const refresh = async () => { setReview(null); setCloseReview(null); setReason(""); await cache.invalidateQueries({ queryKey: ["operator"] }); };
  const mutate = useMutation({ mutationFn: ({ row, command }: { row: AttendanceRow; command: "check-in" | "board" | "no-show" | "payment" }) => command === "payment" ? operatorApi.recordPayment(row.bookingId, { method: row.paymentMethod }) : command === "no-show" && !row.ticketId ? operationsApi.reservationNoShow(tripId,row.bookingItemId,row.pickupStopId,reason || undefined) : operationsApi.transition(tripId,row.ticketId!,command,row.pickupStopId,reason || undefined), onSuccess: refresh });
  const close = useMutation({ mutationFn: (stop: number) => operationsApi.close(tripId, stop), onSuccess: refresh });
  return <><QueryState query={rows}>{data => <AttendanceContent rows={data} manage={manage} tripStatus={tripStatus} closedStops={pickups.data?.filter(p => p.closedAt).map(p => p.stopId) || []} pending={mutate.isPending} action={(row,command) => { if(command === "payment" || command === "no-show") setReview({ row,command }); else mutate.mutate({ row,command }); }} />}</QueryState>
    {review && <section className="card" role="region" aria-label="Xác nhận hành khách"><h3>{review.command === "payment" ? "Xác nhận đã thu tiền" : "Xác nhận vắng mặt"}</h3><p>{review.row.seatCode} · {review.row.passengerName} · {review.row.bookingCode}</p><p>{review.command === "payment" ? `Xác nhận đã thu ${money(review.row.bookingAmount)} cho toàn bộ đặt vé, bao gồm tất cả ghế của đặt vé này.` : "Vắng mặt không hoàn tiền, hủy đặt vé hoặc giải phóng ghế."}</p>{review.command === "no-show" && <label className="field">Lý do (không nhập thông tin cá nhân)<input maxLength={500} value={reason} onChange={e => setReason(e.target.value)} /></label>}<button disabled={mutate.isPending} onClick={() => mutate.mutate(review)}>Xác nhận</button> <button className="secondary" onClick={() => setReview(null)}>Đóng</button></section>}
    {mutate.isError && <OperatorError error={mutate.error} />}
    <section className="card"><h2>Điểm đón</h2><p>Đóng điểm đón sau khi đã ghi nhận lên xe hoặc vắng mặt cho từng hành khách. Giờ dự kiến không tự tạo vắng mặt.</p><QueryState query={pickups}>{data => data.map(p => <p key={p.stopId}>{p.name} · {p.closedAt ? "Đã đóng điểm đón" : "Chưa đóng"} {manage && !p.closedAt && <button disabled={mutate.isPending || close.isPending || !["BOARDING","DEPARTED"].includes(tripStatus)} onClick={() => setCloseReview(p.stopId)}>Đóng điểm đón {p.name}</button>}</p>)}</QueryState>
    {closeReview !== null && <div><p>Xác nhận đóng điểm đón? Sau khi đóng sẽ không thể check-in hoặc lên xe tại đây.</p><button disabled={close.isPending} onClick={() => close.mutate(closeReview)}>Xác nhận đóng điểm đón</button> <button className="secondary" onClick={() => setCloseReview(null)}>Đóng</button></div>}{close.isError && <OperatorError error={close.error} />}</section>
    <details className="card"><summary>Lịch sử vận hành</summary><QueryState query={history}>{events => events.map(e => <p key={e.id}>{dateTime(e.occurred_at)} · {e.action} · {e.entity_type} #{e.entity_id} · Nhân viên #{e.actor_id}{e.reason && ` · ${e.reason}`}</p>)}</QueryState></details></>;
}

