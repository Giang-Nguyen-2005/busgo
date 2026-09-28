import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { adminApi } from "../../api/adminApi";
import { operatorApi } from "../../api/operatorApi";
import { ErrorState, Field } from "../../components/ui";
import type { OperatorStaff } from "../../types/operator";
import { dateTime } from "../../utils/format";
import { AccountFields, ManagementFilters, RoleSelect, StatusSelect } from "./ManagementFields";
import { createStaffRequest, managementLabel, staffFilters, updateStaffRequest } from "./management";
import { OperatorTable, Pagination, QueryState, useOperatorFilters } from "./shared";

export function StaffTable({ rows, edit }: { rows: OperatorStaff[]; edit?: (staff: OperatorStaff) => void }) {
  return <OperatorTable headers={["Mã nhân sự", "Họ tên", "Email", "Điện thoại", "Thành viên", "Vai trò", "Tài khoản", "Ngày tạo", ...(edit ? ["Thao tác"] : [])]} empty={!rows.length}>
    {rows.map(s => <tr key={s.staffId}><td>{s.staffCode || "—"}</td><td>{s.user.fullName}</td><td>{s.user.email}</td><td>{s.user.phone || "—"}</td><td>{managementLabel(s.membershipStatus)}</td><td>{managementLabel(s.role)}</td><td>{managementLabel(s.user.status)}</td><td>{dateTime(s.createdAt)}</td>{edit && <td><button className="secondary" onClick={() => edit(s)}>Cập nhật</button></td>}</tr>)}
  </OperatorTable>;
}
export function StaffManagement({ operatorId }: { operatorId?: number }) {
  const { params, set } = useOperatorFilters();
  const filters = staffFilters(params);
  const cache = useQueryClient();
  const [editor, setEditor] = useState<OperatorStaff | "new" | null>(null);
  const [message, setMessage] = useState("");
  const query = useQuery({ queryKey: operatorId ? ["admin", "operators", operatorId, "staff", filters] : ["operator", "staff", filters],
    queryFn: ({ signal }) => operatorId ? adminApi.staff(operatorId, filters, signal) : operatorApi.staff(filters, signal) });
  const mutation = useMutation({
    mutationFn: (data: FormData) => editor && editor !== "new" ? operatorApi.updateStaff(editor.staffId, updateStaffRequest(data)) : operatorApi.createStaff(createStaffRequest(data)),
    onSuccess: () => { setEditor(null); setMessage("Đã lưu nhân sự."); },
    onSettled: async () => { await cache.invalidateQueries({ queryKey: ["operator", "staff"] }); await cache.invalidateQueries({ queryKey: ["me"] }); },
  });
  const open = (staff: OperatorStaff | "new") => { mutation.reset(); setMessage(""); setEditor(staff); };
  return <section><h2>Nhân sự {operatorId ? "· Chỉ xem" : "nhà xe"}</h2>
    {message && <p role="status">{message}</p>}
    {!operatorId && <button disabled={mutation.isPending} onClick={() => open("new")}>Tạo nhân sự</button>}
    {!operatorId && editor && <form key={editor === "new" ? "new" : editor.staffId} className="card form-stack management-form" onSubmit={e => { e.preventDefault(); mutation.mutate(new FormData(e.currentTarget)); }}>
      <h3>{editor === "new" ? "Tạo nhân sự" : `Cập nhật ${editor.user.fullName}`}</h3>
      <fieldset disabled={mutation.isPending}>{editor === "new" ? <AccountFields /> : <><Field label="Mã nhân sự" name="staffCode" required pattern=".*\S.*" maxLength={50} defaultValue={editor.staffCode || ""} /><StatusSelect value={editor.membershipStatus} /></>}
        <RoleSelect value={editor === "new" ? "OPERATOR_STAFF" : editor.role} />
        {editor !== "new" && <p>Nhà xe phải luôn còn ít nhất một quản trị viên đang hoạt động và có thể đăng nhập. Thay đổi quyền của chính bạn có thể kết thúc quyền quản lý.</p>}
        {mutation.isError && <ErrorState error={mutation.error} />}
        <div className="operator-actions"><button>{mutation.isPending ? "Đang lưu…" : "Lưu nhân sự"}</button><button type="button" className="secondary" onClick={() => setEditor(null)}>Hủy</button></div>
      </fieldset></form>}
    <ManagementFilters params={params} set={set} staff />
    <QueryState query={query}>{result => <><StaffTable rows={result.data} edit={operatorId || mutation.isPending ? undefined : open} /><Pagination pagination={result.pagination} set={set} /></>}</QueryState>
  </section>;
}
