import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { adminApi } from "../../api/adminApi";
import { Empty, ErrorState, Field } from "../../components/ui";
import { AccountFields, ContactFields, ManagementFilters, StatusSelect } from "../../features/operator/ManagementFields";
import { StaffManagement } from "../../features/operator/StaffManagement";
import { adminOperatorFilters, contactRequest, createOperatorRequest, managementLabel } from "../../features/operator/management";
import { Confirm, OperatorPageHeader, OperatorTable, Pagination, positiveId, QueryState, useOperatorFilters } from "../../features/operator/shared";
import type { AdminOperatorDetail } from "../../types/admin";
import { dateTime } from "../../utils/format";

export function AdminHomePage() {
  return <><OperatorPageHeader title="Quản trị hệ thống" /><p>Quản lý thông tin, trạng thái nhà xe và xem nhân sự.</p><div className="operator-quick-actions"><Link className="card" to="/admin/operators">Danh sách nhà xe</Link><Link className="card" to="/admin/operators?create=1">Tạo nhà xe</Link></div></>;
}
export function AdminOperatorsPage() {
  const { params, set } = useOperatorFilters();
  const filters = adminOperatorFilters(params);
  const query = useQuery({ queryKey: ["admin", "operators", filters], queryFn: ({ signal }) => adminApi.operators(filters, signal) });
  return <><OperatorPageHeader title="Nhà xe"><button onClick={() => set("create", "1")}>Tạo nhà xe</button></OperatorPageHeader>
    {params.get("create") === "1" && <CreateOperatorForm cancel={() => set("create", "")} />}
    <ManagementFilters params={params} set={set} /><QueryState query={query}>{result => <>
      <OperatorTable headers={["Mã nhà xe", "Tên", "Điện thoại", "Email", "Trạng thái", "Nhân sự hoạt động", "Quản trị viên hoạt động", "Ngày tạo"]} empty={!result.data.length}>
        {result.data.map(o => <tr key={o.id}><td><Link to={`/admin/operators/${o.id}`}>{o.code}</Link></td><td>{o.name}</td><td>{o.phone || "—"}</td><td>{o.email || "—"}</td><td>{managementLabel(o.status)}</td><td>{o.activeStaffCount}</td><td>{o.activeAdminCount}</td><td>{dateTime(o.createdAt)}</td></tr>)}
      </OperatorTable><Pagination pagination={result.pagination} set={set} /></>}</QueryState></>;
}
function CreateOperatorForm({ cancel }: { cancel: () => void }) {
  const navigate = useNavigate();
  const cache = useQueryClient();
  const mutation = useMutation({ mutationFn: (data: FormData) => adminApi.createOperator(createOperatorRequest(data)),
    onSuccess: async operator => { await cache.invalidateQueries({ queryKey: ["admin", "operators"] }); navigate(`/admin/operators/${operator.id}`); } });
  return <form className="card form-stack management-form" onSubmit={e => { e.preventDefault(); mutation.mutate(new FormData(e.currentTarget)); }}><fieldset disabled={mutation.isPending}>
    <h2>Thông tin nhà xe</h2><Field label="Mã nhà xe" name="code" required maxLength={50} pattern="[A-Za-z0-9_\-]+" /><ContactFields /><StatusSelect />
    <h2>Quản trị viên ban đầu</h2><p>Bắt buộc tạo một quản trị viên ban đầu cho nhà xe.</p><AccountFields prefix="admin." />
    {mutation.isError && <ErrorState error={mutation.error} />}
    <div className="operator-actions"><button>{mutation.isPending ? "Đang tạo…" : "Tạo nhà xe"}</button><button type="button" className="secondary" onClick={cancel}>Hủy</button></div>
  </fieldset></form>;
}
export function AdminOperatorDetailPage() {
  const id = positiveId(useParams().operatorId || null);
  const query = useQuery({ queryKey: ["admin", "operators", id], queryFn: ({ signal }) => adminApi.operator(id!, signal), enabled: !!id });
  if (!id) return <Empty title="Không tìm thấy nhà xe"><Link to="/admin/operators">Danh sách nhà xe</Link></Empty>;
  return <><OperatorPageHeader title="Chi tiết nhà xe"><Link to="/admin/operators">Danh sách nhà xe</Link></OperatorPageHeader><QueryState query={query}>{operator => <OperatorDetail key={operator.id} operator={operator} />}</QueryState><StaffManagement key={id} operatorId={id} /></>;
}
function OperatorDetail({ operator: o }: { operator: AdminOperatorDetail }) {
  const cache = useQueryClient();
  const [confirm, setConfirm] = useState(false);
  const [message, setMessage] = useState("");
  const refresh = () => cache.invalidateQueries({ queryKey: ["admin", "operators"] });
  const contact = useMutation({ mutationFn: (data: FormData) => adminApi.updateOperator(o.id, contactRequest(data)), onSuccess: () => setMessage("Đã cập nhật thông tin nhà xe."), onSettled: refresh });
  const status = useMutation({ mutationFn: () => adminApi.updateStatus(o.id, o.status === "ACTIVE" ? "INACTIVE" : "ACTIVE"), onSuccess: () => { setConfirm(false); setMessage("Đã cập nhật trạng thái nhà xe."); }, onSettled: refresh });
  return <><h2>{o.name}</h2><p>Trạng thái: <strong>{managementLabel(o.status)}</strong></p>{message && <p role="status">{message}</p>}
    <form key={`${o.id}-${o.updatedAt}`} className="card form-stack management-form" onSubmit={e => { e.preventDefault(); setMessage(""); contact.mutate(new FormData(e.currentTarget)); }}><fieldset disabled={contact.isPending || status.isPending}>
      <Field label="Mã nhà xe (không thể thay đổi)" value={o.code} readOnly /><ContactFields values={{ name: o.name, phone: o.phone || "", email: o.email || "", address: o.address || "" }} />
      {contact.isError && <ErrorState error={contact.error} />}<button>{contact.isPending ? "Đang lưu…" : "Lưu thông tin liên hệ"}</button>
    </fieldset></form>
    <section className="card management-form"><h3>Trạng thái nhà xe</h3>{status.isError && <ErrorState error={status.error} />}
      {confirm ? <Confirm pending={status.isPending} cancel={() => setConfirm(false)} confirm={() => status.mutate()} text={o.status === "ACTIVE"
        ? "Ngừng hoạt động sẽ dừng quyền quản lý nhà xe và giao dịch mới của khách hàng. Chuyến xe, đặt vé, thanh toán và vé hiện có KHÔNG tự động bị hủy hoặc hoàn tiền."
        : "Kích hoạt nhà xe yêu cầu ít nhất một quản trị viên nhà xe đang hoạt động và có thể đăng nhập."} />
        : <button disabled={contact.isPending} onClick={() => { status.reset(); setMessage(""); setConfirm(true); }}>{o.status === "ACTIVE" ? "Ngừng hoạt động nhà xe" : "Kích hoạt nhà xe"}</button>}
    </section>
    <dl className="management-grid card management-form"><div><dt>Tổng nhân sự / đang hoạt động</dt><dd>{o.staffCounts.total} / {o.staffCounts.active}</dd></div><div><dt>Quản trị viên / nhân viên</dt><dd>{o.staffCounts.admins} / {o.staffCounts.staff}</dd></div>
      <div><dt>Xe / tuyến vận hành</dt><dd>{o.operationalCounts.buses} / {o.operationalCounts.routes}</dd></div><div><dt>Chuyến / đặt vé</dt><dd>{o.operationalCounts.trips} / {o.operationalCounts.bookings}</dd></div><div><dt>Ngày tạo</dt><dd>{dateTime(o.createdAt)}</dd></div><div><dt>Cập nhật lần cuối</dt><dd>{dateTime(o.updatedAt)}</dd></div></dl>
  </>;
}
