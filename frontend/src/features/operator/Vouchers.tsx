import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient, post } from "../../api/client";
import { useAuth } from "../auth/AuthProvider";
import { canManageOperator } from "../auth/access";
import { Empty, ErrorState, Field, Loading } from "../../components/ui";
import { dateTime, money } from "../../utils/format";

type Voucher = { id: number; code: string; name: string; description?: string; discount_type: string; discount_value: number; max_discount?: number; minimum_spend: number; starts_at: string; ends_at: string; total_limit?: number; customer_limit?: number; operator_route_id?: number; state: string; effective_state: string; editable: boolean; reserved: number; redeemed: number };
type Page = { data: Voucher[]; pagination: { totalPages: number } };
const labels: Record<string, string> = { DRAFT: "Bản nháp", ACTIVE: "Đang áp dụng", PAUSED: "Tạm dừng", EXPIRED: "Hết hạn", EXHAUSTED: "Hết lượt", SCHEDULED: "Chưa đến ngày" };
export function VouchersPage() {
  const auth = useAuth(), cache = useQueryClient();
  const admin = canManageOperator(auth.user?.roles);
  const [notice, setNotice] = useState("");
  const [q, setQ] = useState(""), [state, setState] = useState(""), [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Voucher | null>(), [confirm, setConfirm] = useState<{ id: number; action: string }>();
  const list = useQuery({ queryKey: ["vouchers", auth.user?.id, q, state, page], queryFn: async () => (await apiClient.get<Page>("/operator/vouchers", { params: { q, state: state || undefined, page, size: 10 } })).data });
  const mutation = useMutation({ mutationFn: ({ id, action }: { id: number; action: string }) => post(`/operator/vouchers/${id}/${action}`, {}), onSuccess: () => { setConfirm(undefined); setNotice("Đã cập nhật trạng thái ưu đãi."); void cache.invalidateQueries({ queryKey: ["vouchers"] }); } });
  return <><div className="page-heading"><h1>Khuyến mãi của nhà xe</h1>{admin && <button onClick={() => setSelected(null)}>Tạo mã ưu đãi</button>}</div><p>Nhà xe tài trợ ưu đãi cho đặt vé trực tuyến. Mỗi đặt vé dùng một mã.</p>
    {notice && <p className="notice success" role="status">{notice}</p>}<div className="form-stack"><Field label="Tìm theo mã hoặc chiến dịch" value={q} maxLength={120} onChange={e => { setQ(e.target.value); setPage(0); }} /><label>Trạng thái cấu hình<select value={state} onChange={e => { setState(e.target.value); setPage(0); }}><option value="">Tất cả</option>{["DRAFT", "ACTIVE", "PAUSED"].map(s => <option key={s} value={s}>{labels[s]}</option>)}</select></label></div>
    {list.isPending ? <Loading /> : list.isError ? <ErrorState error={list.error} retry={() => list.refetch()} /> : <>{list.data.data.length === 0 && <Empty title="Chưa có mã ưu đãi" />}{list.data.data.map(v => <article className="card" key={v.id}><h2>{v.name}</h2><strong>{v.code}</strong> · {labels[v.effective_state]}<p>{v.discount_type === "PERCENT" ? `${v.discount_value}%` : money(v.discount_value)}{v.max_discount ? ` · Tối đa ${money(v.max_discount)}` : ""}</p><p>{dateTime(v.starts_at)} – {dateTime(v.ends_at)}</p><p>Đã dùng {v.redeemed} · Đang giữ {v.reserved} · Giới hạn {v.total_limit ?? "Không giới hạn"}</p><button className="secondary" onClick={() => setSelected(v)}>Xem chi tiết{admin && v.editable ? " / sửa" : ""}</button>{admin && <button className="secondary" onClick={() => setConfirm({ id: v.id, action: v.state === "ACTIVE" ? "pause" : "activate" })}>{v.state === "ACTIVE" ? "Tạm dừng" : "Kích hoạt"}</button>}</article>)}<div className="actions"><button disabled={page === 0} onClick={() => setPage(page - 1)}>Trước</button><span>Trang {page + 1}</span><button disabled={page + 1 >= list.data.pagination.totalPages} onClick={() => setPage(page + 1)}>Sau</button></div></>}
    {confirm && <section className="card" role="region" aria-label="Xác nhận thay đổi ưu đãi"><p>{confirm.action === "pause" ? "Tạm dừng áp dụng mới? Đặt vé đã tạo vẫn giữ ưu đãi." : "Kích hoạt ưu đãi theo thời hạn và giới hạn đã cấu hình?"}</p><button disabled={mutation.isPending} onClick={() => mutation.mutate(confirm)}>Xác nhận</button><button disabled={mutation.isPending} onClick={() => setConfirm(undefined)}>Quay lại</button>{mutation.isError && <ErrorState error={mutation.error} />}</section>}
    {selected !== undefined && <VoucherForm key={selected?.id ?? "new"} voucher={selected} editable={admin && (!selected || selected.editable)} close={() => setSelected(undefined)} saved={() => { setSelected(undefined); setNotice("Đã lưu ưu đãi."); }} />}
  </>;
}
function VoucherForm({ voucher: v, editable, close, saved }: { voucher: Voucher | null; editable: boolean; close: () => void; saved: () => void }) {
  const cache = useQueryClient();
  const [validation, setValidation] = useState("");
  const auth = useAuth();
  const routes = useQuery({ queryKey: ["voucher-routes", auth.user?.id], enabled: editable, queryFn: async () => {
    const collected: { id: number; route: { name: string } }[] = [];
    let page = 0, total = 1;
    while (page < total) { const response = await apiClient.get<{ data: typeof collected; pagination: { totalPages: number } }>("/operator/routes", { params: { page, size: 100 } }); collected.push(...response.data.data); total = response.data.pagination.totalPages; page++; }
    return collected;
  } });
  const localDate = (value?: string) => value ? new Date(new Date(value).getTime() - new Date(value).getTimezoneOffset() * 60000).toISOString().slice(0, 16) : "";
  const [type, setType] = useState(v?.discount_type ?? "PERCENT");
  const save = useMutation({ mutationFn: (body: object) => v ? apiClient.patch(`/operator/vouchers/${v.id}`, body) : post("/operator/vouchers", body), onSuccess: () => { void cache.invalidateQueries({ queryKey: ["vouchers"] }); saved(); } });
  // Local date inputs are explicitly converted to offset-bearing API instants.
  return <section className="card"><h2>{v?.name ?? "Tạo ưu đãi"}</h2>{v && !v.editable && <p>Đã có lịch sử đặt vé. Điều khoản giá được giữ nguyên.</p>}<form className="form-stack" onSubmit={e => {
    e.preventDefault(); const f = new FormData(e.currentTarget), n = (key: string) => f.get(key) ? Number(f.get(key)) : null;
    const starts = new Date(String(f.get("startsAt"))), ends = new Date(String(f.get("endsAt")));
    if (!Number.isFinite(starts.getTime()) || !Number.isFinite(ends.getTime()) || starts >= ends) { setValidation("Thời gian kết thúc phải sau thời gian bắt đầu."); return; }
    setValidation("");
    save.mutate({ code: f.get("code"), name: f.get("name"), description: f.get("description"), discountType: type, discountValue: n("discountValue"), maxDiscount: n("maxDiscount"), minimumSpend: n("minimumSpend"), startsAt: starts.toISOString(), endsAt: ends.toISOString(), totalLimit: n("totalLimit"), customerLimit: n("customerLimit"), operatorRouteId: n("operatorRouteId") });
  }}><fieldset disabled={!editable || save.isPending} className="form-stack"><Field label="Mã ưu đãi" name="code" required pattern="[A-Za-z0-9][A-Za-z0-9_-]{2,39}" maxLength={40} defaultValue={v?.code} /><Field label="Tên chiến dịch" name="name" required maxLength={120} defaultValue={v?.name} /><Field label="Mô tả" name="description" maxLength={500} defaultValue={v?.description} /><label>Loại ưu đãi<select value={type} onChange={e => setType(e.target.value)}><option value="PERCENT">Phần trăm</option><option value="FIXED_AMOUNT">Số tiền cố định</option></select></label><Field label="Giá trị ưu đãi" name="discountValue" type="number" min="0.01" max={type === "PERCENT" ? "100" : "9999999999.99"} step="0.01" required defaultValue={v?.discount_value} /><Field label="Giảm tối đa (không bắt buộc)" name="maxDiscount" type="number" min="0.01" step="0.01" defaultValue={v?.max_discount} /><Field label="Giá trị đặt vé tối thiểu" name="minimumSpend" type="number" min="0" step="0.01" required defaultValue={v?.minimum_spend ?? 0} /><Field label="Bắt đầu (giờ địa phương)" name="startsAt" type="datetime-local" required defaultValue={localDate(v?.starts_at)} /><Field label="Kết thúc (giờ địa phương)" name="endsAt" type="datetime-local" required defaultValue={localDate(v?.ends_at)} /><Field label="Tổng lượt tối đa (để trống: không giới hạn)" name="totalLimit" type="number" min="1" step="1" defaultValue={v?.total_limit} /><Field label="Số lượt mỗi khách" name="customerLimit" type="number" min="1" step="1" defaultValue={v?.customer_limit} /><label>Áp dụng cho tuyến<select name="operatorRouteId" defaultValue={v?.operator_route_id ?? ""}><option value="">Tất cả tuyến của nhà xe</option>{routes.data?.map(r => <option key={r.id} value={r.id}>{r.route.name}</option>)}{v?.operator_route_id && !routes.data?.some(r => r.id === v.operator_route_id) && <option value={v.operator_route_id}>Tuyến đã chọn</option>}</select></label>{routes.isError && <ErrorState error={routes.error} retry={() => routes.refetch()} />}{editable && <button disabled={save.isPending}>{save.isPending ? "Đang lưu…" : "Lưu ưu đãi"}</button>}</fieldset>{validation && <p role="alert">{validation}</p>}{save.isError && <ErrorState error={save.error} />}<button type="button" className="secondary" onClick={close}>Đóng</button></form></section>;
}
