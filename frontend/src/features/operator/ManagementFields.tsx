import { Field } from "../../components/ui";
import { activeStatuses, managementLabel, staffRoles } from "./management";
import type { OperatorContactRequest } from "../../types/admin";

export function StatusSelect({ value = "ACTIVE", name = "status" }: { value?: string; name?: string }) {
  return <label className="field">Trạng thái<select name={name} defaultValue={value}>{activeStatuses.map(s => <option key={s} value={s}>{managementLabel(s)}</option>)}</select></label>;
}
export function RoleSelect({ value = "OPERATOR_STAFF" }: { value?: string }) {
  return <label className="field">Vai trò<select name="role" defaultValue={value}>{staffRoles.map(s => <option key={s} value={s}>{managementLabel(s)}</option>)}</select></label>;
}
export function ContactFields({ values }: { values?: Partial<OperatorContactRequest> }) {
  return <div className="management-grid"><Field label="Tên nhà xe" name="name" required pattern=".*\S.*" maxLength={150} defaultValue={values?.name} />
    <Field label="Điện thoại nhà xe" name="phone" type="tel" maxLength={20} defaultValue={values?.phone} />
    <Field label="Email nhà xe" name="email" type="email" maxLength={150} defaultValue={values?.email} />
    <Field label="Địa chỉ" name="address" maxLength={255} defaultValue={values?.address} /></div>;
}
export function AccountFields({ prefix = "" }: { prefix?: string }) {
  return <><p className="notice">Tài khoản được tạo trực tiếp, không có email mời. Mật khẩu ban đầu phải được chia sẻ qua kênh riêng ngoài hệ thống.</p>
    <div className="management-grid"><Field label="Họ và tên" name={prefix + "fullName"} required pattern=".*\S.*" maxLength={100} />
      <Field label="Email đăng nhập" name={prefix + "email"} type="email" required maxLength={150} autoComplete="off" />
      <Field label="Số điện thoại" name={prefix + "phone"} type="tel" required pattern=".*\S.*" maxLength={20} />
      <Field label="Mã nhân sự" name={prefix + "staffCode"} required pattern=".*\S.*" maxLength={50} />
      <Field label="Mật khẩu ban đầu" name={prefix + "password"} type="password" required minLength={8} autoComplete="new-password" onChange={e => e.currentTarget.setCustomValidity(new TextEncoder().encode(e.currentTarget.value).length > 72 ? "Mật khẩu tối đa 72 byte UTF-8." : "")} />
    </div><small>Ít nhất 8 ký tự, tối đa 72 byte UTF-8.</small></>;
}
export function ManagementFilters({ params, set, staff = false }: { params: URLSearchParams; set: (key: string, value: string) => void; staff?: boolean }) {
  return <div className="operator-filters"><Field label="Tìm kiếm" value={params.get("q") || ""} maxLength={100} onChange={e => set("q", e.target.value)} />
    <label className="field">{staff ? "Trạng thái thành viên" : "Trạng thái nhà xe"}<select value={activeStatuses.find(s => s === params.get("status")) || ""} onChange={e => set("status", e.target.value)}><option value="">Tất cả</option>{activeStatuses.map(s => <option key={s} value={s}>{managementLabel(s)}</option>)}</select></label>
    {staff && <label className="field">Vai trò<select value={staffRoles.find(r => r === params.get("role")) || ""} onChange={e => set("role", e.target.value)}><option value="">Tất cả</option>{staffRoles.map(r => <option key={r} value={r}>{managementLabel(r)}</option>)}</select></label>}</div>;
}
