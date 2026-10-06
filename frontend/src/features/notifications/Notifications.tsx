import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { apiClient, get } from '../../api/client';
import { dateTime } from '../../utils/format';
import { notificationsApi, notificationTarget, type Notification, type Preferences } from './notificationsApi';
import './notifications.css';

function useInbox(operator: boolean, page: number, size: number) {
  const auth = useAuth();
  const api = notificationsApi(operator);
  const client = useQueryClient();
  const key = ['notifications', auth.user?.id, operator];
  const list = useQuery({ queryKey: [...key, 'list', page, size], queryFn: ({ signal }) => api.list(page, size, signal), refetchInterval: 15000 });
  const count = useQuery({ queryKey: [...key, 'count'], queryFn: ({ signal }) => api.count(signal), refetchInterval: 15000 });
  const mutation = useMutation({ mutationFn: async (id: number | null) => { if (id === null) await api.all(); else await api.read(id); }, onSuccess: () => client.invalidateQueries({ queryKey: key }) });
  return { list, count, mutation };
}
function NotificationItem({ item, operator, busy, onRead, onOpen }: { item: Notification; operator: boolean; busy: boolean; onRead: () => Promise<unknown>; onOpen?: () => void }) {
  const navigate = useNavigate();
  const target = notificationTarget(item.navigationTarget, operator);
  const open = async () => {
    try { await onRead(); onOpen?.(); if (target) navigate(target); } catch { /* Parent renders the mutation error. */ }
  };
  return <article className={`notification-item ${item.readAt ? '' : 'is-unread'}`}>
    <strong>{item.title}</strong><p>{item.message}</p>
    <small>{dateTime(item.createdAt)} · {item.readAt ? 'Đã đọc' : 'Chưa đọc'}</small>
    <div className="notification-actions">
      {!item.readAt && <button disabled={busy} onClick={() => { void onRead().catch(() => {}); }}>Đánh dấu đã đọc</button>}
      {target && <button disabled={busy} onClick={() => { void open(); }}>Xem đặt vé {item.bookingCode}</button>}
    </div>
  </article>;
}
export function NotificationBell({ operator = false }: { operator?: boolean }) {
  const [open, setOpen] = useState(false);
  const { list, count, mutation } = useInbox(operator, 0, 5);
  return <div className="notification-bell" onKeyDown={event => { if (event.key === 'Escape') setOpen(false); }}>
    <button className="icon-button" aria-label={`Thông báo, ${count.data ?? 0} chưa đọc`} aria-expanded={open} aria-controls="notification-panel" onClick={() => setOpen(!open)}>
      <Bell size={20} />{(count.data ?? 0) > 0 && <span className="notification-badge">{count.data! > 99 ? '99+' : count.data}</span>}
    </button>
    {open && <section id="notification-panel" className="notification-panel" aria-label="Thông báo gần đây">
      <div className="notification-actions"><strong>Thông báo</strong><button onClick={() => setOpen(false)} aria-label="Đóng thông báo">Đóng</button></div>
      {list.isPending && <p>Đang tải…</p>}
      {(list.isError || count.isError) && <p role="alert">Không thể tải thông báo. <button onClick={() => { void list.refetch(); void count.refetch(); }}>Thử lại</button></p>}
      {mutation.isError && <p role="alert">Không thể đánh dấu đã đọc.</p>}
      {list.data?.data.length === 0 && <p>Bạn chưa có thông báo.</p>}
      {list.data?.data.map(item => <NotificationItem key={item.id} item={item} operator={operator} busy={mutation.isPending} onRead={() => mutation.mutateAsync(item.id)} onOpen={() => setOpen(false)} />)}
      <Link to={operator ? '/operator/notifications' : '/notifications'} onClick={() => setOpen(false)}>Xem tất cả thông báo</Link>
    </section>}
  </div>;
}
function DeliveryHistory({ id, operator }: { id: number; operator: boolean }) {
  const api = notificationsApi(operator);
  const query = useQuery({ queryKey: ['notification-deliveries', operator, id], queryFn: ({ signal }) => api.deliveries(id, signal) });
  const retry = useMutation({ mutationFn: () => api.retry(id), onSuccess: () => query.refetch() });
  const labels: Record<string, string> = { PENDING: 'Đang chờ', SENT: 'Đã gửi', FAILED: 'Gửi thất bại', SKIPPED: 'Đã bỏ qua' };
  return <div className="notification-delivery">
    {query.isPending && <p>Đang tải lịch sử gửi…</p>}
    {(query.isError || retry.isError) && <p role="alert">Không thể tải hoặc thử lại email.</p>}
    {query.data?.length === 0 && <p>Không có email cho thông báo này.</p>}
    {query.data?.map(d => <p key={d.id}>Email: {labels[d.status] ?? d.status} · {d.attemptCount}/5 lần gửi {d.status === 'FAILED' && d.attemptCount < 5 && <button disabled={retry.isPending} onClick={() => retry.mutate()}>Thử gửi lại</button>}</p>)}
  </div>;
}
export function NotificationsPage() {
  const operator = useLocation().pathname.startsWith('/operator/');
  const [page, setPage] = useState(0);
  const [delivery, setDelivery] = useState<number | null>(null);
  const { list, count, mutation } = useInbox(operator, page, 20);
  return <div className="notifications-page">
    <div className="page-heading"><div><h1>Thông báo</h1><p>{count.data ?? 0} thông báo chưa đọc</p></div>
      <button disabled={mutation.isPending || !count.data} onClick={() => mutation.mutate(null)}>Đánh dấu tất cả đã đọc</button></div>
    {!operator && <Link to="/notification-preferences">Cài đặt email thông báo</Link>}
    {list.isPending && <p>Đang tải…</p>}
    {(list.isError || count.isError || mutation.isError) && <p role="alert">Không thể cập nhật thông báo. <button onClick={() => { void list.refetch(); void count.refetch(); }}>Thử lại</button></p>}
    {list.data?.data.length === 0 && <p>Bạn chưa có thông báo.</p>}
    {list.data?.data.map(item => <div key={item.id}><NotificationItem item={item} operator={operator} busy={mutation.isPending} onRead={() => mutation.mutateAsync(item.id)} />
      <button onClick={() => setDelivery(delivery === item.id ? null : item.id)} aria-expanded={delivery === item.id}>Lịch sử gửi email</button>
      {delivery === item.id && <DeliveryHistory id={item.id} operator={operator} />}</div>)}
    <div className="notification-actions"><button disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</button>
      <span>Trang {page + 1}</span><button disabled={!list.data || page + 1 >= list.data.pagination.totalPages} onClick={() => setPage(page + 1)}>Trang sau</button></div>
  </div>;
}
export function NotificationPreferencesPage() {
  const auth = useAuth();
  const query = useQuery({ queryKey: ['notification-preferences', auth.user?.id], queryFn: ({ signal }) => get<Preferences>('/notification-preferences', undefined, signal) });
  const [draft, setDraft] = useState<Preferences | null>(null);
  const client = useQueryClient();
  const save = useMutation({ mutationFn: (p: Preferences) => apiClient.put('/notification-preferences', p), onSuccess: () => client.invalidateQueries({ queryKey: ['notification-preferences', auth.user?.id] }) });
  const value = draft ?? query.data;
  return <div className="notifications-page"><h1>Cài đặt thông báo</h1><p>Thông báo quan trọng trong ứng dụng luôn được bật. Chọn email bạn muốn nhận.</p>
    {query.isPending && <p>Đang tải…</p>}{(query.isError || save.isError) && <p role="alert">Không thể tải hoặc lưu cài đặt. <button onClick={() => { void query.refetch(); }}>Thử lại</button></p>}
    {save.isSuccess && <p role="status">Đã lưu cài đặt.</p>}
    {value && <form onSubmit={event => { event.preventDefault(); save.mutate(value); }}>
      {([['bookingPaymentEmail', 'Đặt vé và thanh toán'], ['bookingChangeEmail', 'Thay đổi và hủy vé'], ['tripReminderEmail', 'Nhắc giờ đón xe']] as const).map(([key, label]) =>
        <label className="notification-preference" key={key}><input type="checkbox" checked={value[key]} disabled={save.isPending} onChange={event => { setDraft({ ...value, [key]: event.target.checked }); save.reset(); }} />{label}</label>)}
      <button className="button" disabled={save.isPending}>Lưu cài đặt</button>
    </form>}<p><Link to="/notifications">Về thông báo</Link></p></div>;
}
