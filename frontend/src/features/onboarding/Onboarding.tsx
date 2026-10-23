import { ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { apiClient, get, post } from "../../api/client";
import { ErrorState, Field, Loading, PasswordField } from "../../components/ui";
import type { PagedResponse } from "../../types/api";
import "./onboarding.css";

type Row = Record<string, string | number | null>;
type Application = Row & { invitations: Row[]; history: Row[] };
type Governance = Row & { applications: Row[]; invitations: Row[]; history: Row[] };
type Invitation = { applicationId: number; operatorId: number; invitationPath: string; expiresAt: string };
export const stateLabel = (state: string) => ({ SUBMITTED: "Đã gửi", UNDER_REVIEW: "Đang xem xét", APPROVED: "Đã phê duyệt", REJECTED: "Đã từ chối", ACTIVE: "Hoạt động", INACTIVE: "Chưa hoạt động", SUSPENDED: "Tạm đình chỉ", ONBOARDING: "Chờ nhận lời mời", ACTIVE_ROUTES: "Tuyến hoạt động", UPCOMING_TRIPS: "Chuyến sắp tới" }[state] || state);
const formValues = (form: HTMLFormElement) => Object.fromEntries(new FormData(form));
const labels: Record<string,string> = { name:"Tên nhà xe",legal_name:"Tên pháp lý",legal_code:"Mã số thuế / đăng ký",email:"Email doanh nghiệp",phone:"Điện thoại",address:"Địa chỉ",contact_name:"Người đại diện",admin_email:"Email quản trị viên",website:"Website",public_description:"Giới thiệu",status:"Trạng thái",submitted_at:"Ngày gửi",onboarded_at:"Hoàn tất đăng ký",legal_contact:"Người đại diện",activeAdminCount:"Quản trị viên hợp lệ",activeRoutes:"Tuyến hoạt động",upcomingTrips:"Chuyến sắp tới" };
function Facts({ data }: { data: Row }) { return <dl className="onboarding-facts">{Object.entries(labels).filter(([key]) => key in data).map(([key,label]) => <div key={key}><dt>{label}</dt><dd>{key === "status" ? stateLabel(String(data[key])) : String(data[key] ?? "Chưa có")}</dd></div>)}</dl>; }
function History({ rows }: { rows: Row[] }) { return <section className="card"><h2>Lịch sử đăng ký và trạng thái</h2>{rows.map(row => <p key={row.id}><strong>{row.event_type}</strong> · {row.created_at}<br />{row.old_state} → {row.new_state}{row.note && <><br />{row.note}</>}</p>)}{!rows.length && <p>Chưa có sự kiện đăng ký.</p>}</section>; }
function Invites({ rows }: { rows: Row[] }) { return <section className="card"><h2>Lời mời quản trị viên</h2>{rows.map(r => <p key={r.id}>{r.intended_email} · {r.accepted_at ? "Đã nhận" : r.revoked_at ? "Đã thu hồi" : new Date(String(r.expires_at)).getTime() <= Date.now() ? "Đã hết hạn" : "Đang chờ"}<br />Hết hạn: {r.expires_at}</p>)}{!rows.length && <p>Chưa phát hành lời mời.</p>}</section>; }

export function OperatorApplicationPage() {
  const submit=useMutation({mutationFn:(body:object)=>post<{id:number;status:string}>("/public/operator-applications",body)});
  if(submit.isSuccess) return <section className="card onboarding-public"><h1>Đã nhận đơn đăng ký</h1><p>Mã đơn: <strong>{submit.data.id}</strong> · Đã gửi</p><p>BusGo sẽ xem xét thông tin doanh nghiệp. Tài khoản quản trị sẽ được tạo khi bạn nhận và chấp nhận lời mời.</p><Link to="/">Về trang chủ</Link></section>;
  return <section className="card onboarding-public"><h1>Đăng ký nhà xe</h1><p>Gửi thông tin doanh nghiệp để tham gia BusGo. Các trường có * là bắt buộc.</p><form className="form-stack" onSubmit={e=>{e.preventDefault();submit.mutate(formValues(e.currentTarget));}}><fieldset disabled={submit.isPending} className="form-stack">
    <Field name="name" label="Tên nhà xe *" required maxLength={150} />
    <Field name="legalName" label="Tên công ty pháp lý (nếu khác)" maxLength={150} />
    <Field name="legalCode" label="Mã số thuế / đăng ký kinh doanh *" required minLength={3} maxLength={50} />
    <Field name="email" label="Email doanh nghiệp *" type="email" required maxLength={150} />
    <Field name="phone" label="Điện thoại doanh nghiệp *" type="tel" required minLength={6} maxLength={20} />
    <Field name="address" label="Địa chỉ doanh nghiệp *" required maxLength={255} />
    <Field name="contactName" label="Người đại diện / liên hệ *" required maxLength={100} />
    <Field name="adminEmail" label="Email quản trị viên đầu tiên *" type="email" required maxLength={150} description="Dùng email riêng chưa đăng ký tài khoản BusGo." />
    <Field name="website" label="Website (không bắt buộc)" type="url" maxLength={500} />
    <label className="field"><span>Giới thiệu công khai (không bắt buộc)</span><textarea name="publicDescription" maxLength={2000} rows={4}/></label>
    {submit.isError && <ErrorState error={submit.error}/>}<button>{submit.isPending?"Đang gửi…":"Gửi đơn đăng ký"}</button>
  </fieldset></form></section>;
}

export function OperatorInvitationPage() {
  const [token]=useState(()=>window.location.hash.slice(1));
  const [context,setContext]=useState<{operatorName:string;email:string;expiresAt:string}|null>(null);
  const [error,setError]=useState<unknown>(null);
  useEffect(()=>{let mounted=true;get<typeof context>(`/public/operator-invitations/${encodeURIComponent(token || "invalid")}/context`).then(r=>{if(mounted)setContext(r);}).catch(e=>{if(mounted)setError(e);});return()=>{mounted=false;};},[token]);
  const accept=useMutation({mutationFn:(body:object)=>post(`/public/operator-invitations/${encodeURIComponent(token)}/accept`,body),onSuccess:()=>window.history.replaceState(null,"","/operator-invitation"),gcTime:0});
  const [confirmation,setConfirmation]=useState("");
  if(accept.isSuccess) return <section className="card onboarding-public"><h1>Nhà xe đã được kích hoạt</h1><p>Bạn có thể đăng nhập bằng email được mời và mật khẩu vừa tạo.</p><Link className="button" to="/login?returnTo=%2Foperator">Đăng nhập nhà xe</Link></section>;
  return <section className="card onboarding-public"><h1>Nhận lời mời quản trị nhà xe</h1>{error ? <ErrorState error={error}/> : !context ? <Loading/> : <><p><strong>{context.operatorName}</strong><br/>{context.email}<br/>Hết hạn: {context.expiresAt}</p><form className="form-stack" onSubmit={e=>{e.preventDefault();const data=formValues(e.currentTarget);if(data.password!==data.confirmPassword){setConfirmation("Mật khẩu xác nhận chưa khớp.");return;}setConfirmation("");accept.mutate(data);}}><fieldset className="form-stack" disabled={accept.isPending}>
    <Field label="Họ và tên *" name="fullName" required maxLength={100} autoComplete="name"/>
    <PasswordField label="Mật khẩu *" name="password" required minLength={8} autoComplete="new-password" description="Ít nhất 8 ký tự, tối đa 72 byte UTF-8."/>
    <PasswordField label="Xác nhận mật khẩu *" name="confirmPassword" required minLength={8} autoComplete="new-password"/>
    {confirmation && <p role="alert">{confirmation}</p>}{accept.isError && <ErrorState error={accept.error}/>}<button>{accept.isPending?"Đang kích hoạt…":"Tạo tài khoản và kích hoạt nhà xe"}</button>
  </fieldset></form></>}</section>;
}

function PlatformWorkQueue() {
 const q=useQuery({queryKey:["admin","dispute-counters"],queryFn:({signal})=>get<{key:string;count:number}[]>("/admin/disputes/dashboard",undefined,signal)});
 return <section className="card platform-queue"><h2>Tranh chấp cần chú ý</h2>{q.isPending?<Loading/>:q.isError?<ErrorState error={q.error} retry={()=>q.refetch()}/>:<dl>{["SUBMITTED","UNDER_REVIEW","WAITING_INFORMATION"].map(key=><div key={key}><dt>{key==="WAITING_INFORMATION"?"Chờ bổ sung":stateLabel(key)}</dt><dd>{q.data.find(c=>c.key===key)?.count ?? "Chưa xác định"}</dd></div>)}</dl>}<Link to="/admin/disputes">Mở hàng đợi tranh chấp →</Link><p>Đối soát: chọn nhà xe và kỳ tại <Link to="/admin/finance">Tài chính</Link> để xem ngoại lệ của từng lần chạy.</p></section>;
}
export function PlatformDashboard() {
 const q=useQuery({queryKey:["admin","dashboard"],queryFn:()=>get<Record<string,number>>("/admin/dashboard")});
 return <section className="platform-overview"><header className="platform-hero"><ShieldCheck aria-hidden="true" size={28}/><p>BUSGO / QUẢN TRỊ NỀN TẢNG</p><h1>Tổng quan nền tảng</h1><p>Theo dõi đăng ký, trạng thái nhà xe và nguồn cung vận hành.</p><Link className="button" to="/admin/operator-applications">Xem đơn đăng ký nhà xe</Link></header>
 {q.isPending?<Loading/>:q.isError?<ErrorState error={q.error}/>:<div className="platform-sections">{[
 {title:"Đơn đăng ký đang xử lý",keys:["SUBMITTED","UNDER_REVIEW"],note:"Số đơn theo trạng thái xem xét."},
 {title:"Chờ hoàn tất đăng ký",keys:["ONBOARDING"],note:"Đơn đã duyệt có nhà xe chưa hoàn tất nhận lời mời."},
 {title:"Trạng thái nhà xe",keys:["ACTIVE","SUSPENDED"],note:"Số nhà xe theo trạng thái hoạt động; không phải số đơn đăng ký."},
 {title:"Nguồn cung nền tảng",keys:["ACTIVE_ROUTES","UPCOMING_TRIPS"],note:"Tuyến hoạt động của nhà xe hoạt động và chuyến đã lên lịch sắp tới."}
 ].map(group=><section className="card" key={group.title}><h2>{group.title}</h2><p>{group.note}</p><dl>{group.keys.map(key=><div key={key}><dt>{stateLabel(key)}</dt><dd>{q.data[key] ?? "Chưa xác định"}</dd></div>)}</dl></section>)}</div>}
 <PlatformWorkQueue/><nav className="platform-links" aria-label="Công việc nền tảng">{[["operator-applications","Đơn đăng ký"],["operators","Danh bạ nhà xe"],["disputes","Tranh chấp"],["finance","Tài chính / Đối soát"],["audit-events","Nhật ký kiểm toán"]].map(([path,title])=><Link key={path} to={`/admin/${path}`}>{title} →</Link>)}</nav></section>;
}

export function ApplicationsPage() {
 const [status,setStatus]=useState("");const [search,setSearch]=useState("");const [page,setPage]=useState(0);
 const q=useQuery({queryKey:["admin","applications",status,search,page],queryFn:async()=> (await apiClient.get<PagedResponse<Row>>("/admin/operator-applications",{params:{status:status||undefined,q:search||undefined,page,size:20}})).data});
 return <><h1>Đơn đăng ký nhà xe</h1><div className="admin-filters"><Field label="Tìm tên hoặc mã số thuế" value={search} maxLength={100} onChange={e=>{setSearch(e.target.value);setPage(0);}}/><label className="field"><span>Trạng thái</span><select value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}><option value="">Tất cả</option>{["SUBMITTED","UNDER_REVIEW","APPROVED","REJECTED"].map(s=><option key={s} value={s}>{stateLabel(s)}</option>)}</select></label></div>
 {q.isPending?<Loading/>:q.isError?<ErrorState error={q.error}/>:<><div className="onboarding-list">{q.data.data.map(a=><section className="card" key={a.id}><Link to={`/admin/operator-applications/${a.id}`}><strong>{a.name}</strong></Link><p>{a.legal_code} · <span className="onboarding-status">{stateLabel(String(a.status))}</span></p><p>{a.submitted_at}</p></section>)}{!q.data.data.length&&<p>Không có đơn đăng ký phù hợp.</p>}</div><nav className="admin-actions"><button disabled={page===0} onClick={()=>setPage(page-1)}>Trước</button><span>Trang {page+1} · {q.data.pagination.totalElements} đơn</span><button disabled={page+1>=q.data.pagination.totalPages} onClick={()=>setPage(page+1)}>Sau</button></nav></>}
 </>;
}

export function ApplicationDetailPage() {
 const id=Number(useParams().applicationId);const cache=useQueryClient();const [note,setNote]=useState("");const [invite,setInvite]=useState<Invitation|null>(null);const [copied,setCopied]=useState(false);
 const q=useQuery({queryKey:["admin","application",id],queryFn:()=>get<Application>(`/admin/operator-applications/${id}`)});
 const act=useMutation({mutationFn:async(action:string)=>{setInvite(null);setCopied(false);const result=await post<Invitation|Application>(`/admin/operator-applications/${id}/${action}`,action==="reject"?{reason:note}:{note});if(action==="approve"||action==="invitation/reissue")setInvite(result as Invitation);},onSuccess:()=>cache.invalidateQueries({queryKey:["admin"]}),gcTime:0});
 if(q.isPending)return <Loading/>;if(q.isError)return <ErrorState error={q.error}/>;const a=q.data;
 const pending=a.invitations.length>0&&!a.invitations.some(i=>i.accepted_at);
 return <><Link to="/admin/operator-applications">← Đơn đăng ký</Link><h1>{a.name}</h1><section className="card"><Facts data={a}/></section>{a.operator_id&&<p><Link to={`/admin/operators/${a.operator_id}`}>Xem nhà xe đã tạo</Link></p>}
 {invite&&<section className="card notice success" role="status"><h2>Sao chép lời mời ngay</h2><p>Liên kết chỉ hiển thị lần này. Chia sẻ riêng với quản trị viên được mời. Hết hạn: {invite.expiresAt}</p><input aria-label="Liên kết mời một lần" readOnly value={window.location.origin+invite.invitationPath}/><button onClick={()=>navigator.clipboard.writeText(window.location.origin+invite.invitationPath).then(()=>setCopied(true)).catch(()=>setCopied(false))}>{copied?"Đã sao chép":"Sao chép liên kết"}</button><button className="secondary" onClick={()=>setInvite(null)}>Ẩn liên kết</button></section>}
 <section className="card form-stack"><Field label="Ghi chú nội bộ / lý do từ chối" description="Lý do từ chối sẽ được gửi cho người đăng ký. Các ghi chú xem xét khác chỉ hiển thị nội bộ." value={note} maxLength={1000} onChange={e=>setNote(e.target.value)}/><div className="admin-actions">
 {a.status==="SUBMITTED"&&<button disabled={act.isPending} onClick={()=>act.mutate("review")}>Bắt đầu xem xét</button>}
 {a.status==="UNDER_REVIEW"&&<button disabled={act.isPending} onClick={()=>{if(window.confirm("Phê duyệt và tạo lời mời?"))act.mutate("approve");}}>Phê duyệt và tạo lời mời</button>}
 {["SUBMITTED","UNDER_REVIEW"].includes(String(a.status))&&<button className="secondary" disabled={act.isPending||!note.trim()} onClick={()=>{if(window.confirm("Từ chối đơn với lý do đã nhập?"))act.mutate("reject");}}>Từ chối đơn</button>}
 {a.status==="APPROVED"&&pending&&<button disabled={act.isPending} onClick={()=>{if(window.confirm("Thu hồi và cấp lại lời mời?"))act.mutate("invitation/reissue");}}>Thu hồi và cấp lại lời mời</button>}</div>{act.isError&&<ErrorState error={act.error}/>}</section><Invites rows={a.invitations}/><History rows={a.history}/></>;
}

export function OperatorGovernance({id}:{id:number}) {
 const cache=useQueryClient();const [reason,setReason]=useState("");const [confirm,setConfirm]=useState(false);
 const q=useQuery({queryKey:["admin","operators",id,"governance"],queryFn:()=>get<Governance>(`/admin/operators/${id}/governance`)});
 const change=useMutation({mutationFn:(action:string)=>post(`/admin/operators/${id}/${action}`,{reason}),onSuccess:()=>{setReason("");setConfirm(false);return cache.invalidateQueries({queryKey:["admin"]});}});
 if(q.isPending)return <Loading/>;if(q.isError)return <ErrorState error={q.error}/>;const o=q.data;
 return <><section className="card"><h2>Đăng ký và pháp lý</h2><Facts data={o}/>{o.applications.map(a=><p key={a.id}><Link to={`/admin/operator-applications/${a.id}`}>Đơn đăng ký #{a.id}</Link></p>)}{!o.applications.length&&<p>Nhà xe được tạo trước quy trình đăng ký công khai.</p>}</section><Invites rows={o.invitations}/><section className="card form-stack"><h2>Trạng thái nền tảng: {stateLabel(String(o.status))}</h2><p>Đình chỉ ngăn bán vé và tạo nguồn cung mới. Đặt vé hiện có, các quyền hủy hợp lệ và vận hành chuyến được giữ lại.</p>{["ACTIVE","SUSPENDED"].includes(String(o.status))&&<><Field label="Lý do thay đổi trạng thái *" value={reason} maxLength={1000} onChange={e=>{setReason(e.target.value);setConfirm(false);}}/><button disabled={!reason.trim()||change.isPending} onClick={()=>setConfirm(true)}>{o.status==="ACTIVE"?"Đình chỉ nhà xe":"Kích hoạt lại nhà xe"}</button></>}{confirm&&<section className="admin-confirm" role="region" aria-label="Xác nhận trạng thái"><h3>Xác nhận {o.status==="ACTIVE"?"đình chỉ":"kích hoạt lại"} nhà xe</h3><p>{reason}</p><button disabled={change.isPending||!reason.trim()} onClick={()=>change.mutate(o.status==="ACTIVE"?"suspend":"reactivate")}>Xác nhận thay đổi</button><button disabled={change.isPending} onClick={()=>setConfirm(false)}>Quay lại</button></section>}{change.isError&&<ErrorState error={change.error}/>}</section><History rows={o.history}/></>;
}
