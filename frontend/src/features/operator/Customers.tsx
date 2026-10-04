import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { customersApi } from '../../api/customersApi';
import { useAuth } from '../auth/AuthProvider';
import { canManageOperator } from '../auth/access';
import { Empty } from '../../components/ui';
import { money, dateTime } from '../../utils/format';
import { OperatorPageHeader, OperatorTable, Pagination, QueryState, useOperatorFilters } from './shared';
import type { PagedResponse } from '../../types/api';
import type { CustomerAttendance, CustomerDetail, CustomerFilters, CustomerMoney, CustomerType, CustomerSort, OperatorCustomer } from '../../types/operatorCustomers';
import './customers.css';

const typeLabel = (type: CustomerType) => type === 'ACCOUNT' ? 'Khách có tài khoản' : 'Khách đặt qua điện thoại';
const stateLabel: Record<string, string> = { PENDING: 'Chưa thanh toán', CONFIRMED: 'Đã xác nhận', COMPLETED: 'Hoàn thành', CANCELLED: 'Đã hủy', PAID: 'Đã thu', REFUNDED: 'Đã hoàn tiền mô phỏng', FAILED: 'Thất bại', MOCK_ONLINE: 'Trực tuyến mô phỏng', PAY_ON_BOARD: 'Thu trên xe', QR_TRANSFER: 'Chuyển khoản QR mô phỏng', CUSTOMER_CANCELLED: 'Khách hủy', OPERATOR_CANCELLED: 'Nhà xe hủy', PAYMENT_TIMEOUT: 'Hết hạn thanh toán' };
export function attendanceText(a: CustomerAttendance) {
  return [[a.boarded, 'đã lên xe'], [a.noShow, 'vắng mặt'], [a.checkedIn, 'đã check-in'], [a.unrecorded, 'chưa ghi nhận']].filter(([n]) => Number(n) > 0).map(([n, label]) => `${n} ${label}`).join(' / ') || 'Chưa ghi nhận';
}
function Contact({ customer }: { customer: OperatorCustomer }) {
  return <><strong>{customer.displayName}</strong><span>{customer.phone}</span>{customer.email && <span>{customer.email}</span>}</>;
}
export function CustomerDirectoryContent({ data, set }: { data: PagedResponse<OperatorCustomer>; set: (key: string, value: string) => void }) {
  if (!data.data.length) return <><Empty title="Chưa có khách hàng phù hợp" /><Pagination pagination={data.pagination} set={set} /></>;
  return <>
    <div className="customer-desktop"><OperatorTable headers={['Khách hàng', 'Liên hệ', 'Loại', 'Tổng booking', 'Chuyến gần nhất', 'Thu mô phỏng', 'Hoạt động gần nhất']}>
      {data.data.map(c => <tr key={c.customerKey}><td><Link to={`/operator/customers/${encodeURIComponent(c.customerKey)}`}>{c.displayName}</Link></td><td className="customer-contact"><span>{c.phone}</span>{c.email && <span>{c.email}</span>}</td><td>{typeLabel(c.customerType)}</td><td>{c.totalBookings}</td><td>{c.latestJourney}</td><td>{money(c.money.grossMockPaid)}</td><td>{dateTime(c.latestBookingAt)}</td></tr>)}
    </OperatorTable></div>
    <div className="customer-mobile">{data.data.map(c => <article className="operator-panel customer-card" key={c.customerKey}><div className="customer-contact"><Contact customer={c} /></div><span className="operator-badge">{typeLabel(c.customerType)}</span><p>{c.totalBookings} booking · {c.latestJourney}</p><p>Thu mô phỏng: {money(c.money.grossMockPaid)}</p><p>{dateTime(c.latestBookingAt)}</p><Link to={`/operator/customers/${encodeURIComponent(c.customerKey)}`}>Xem lịch sử</Link></article>)}</div>
    <Pagination pagination={data.pagination} set={set} />
  </>;
}
function MoneySummary({ value }: { value: CustomerMoney }) {
  return <><div><dt>Thu mô phỏng</dt><dd>{money(value.grossMockPaid)}</dd></div><div><dt>Hoàn tiền mô phỏng</dt><dd>{money(value.mockRefunds)}</dd></div><div><dt>Thu ròng mô phỏng</dt><dd>{money(value.netMockPaid)}</dd></div></>;
}
export function CustomerDetailContent({ data, set }: { data: CustomerDetail; set: (key: string, value: string) => void }) {
  const c = data.summary;
  return <>
    <section className="customer-summary"><div className="customer-contact"><Contact customer={c} /></div><p><span className="operator-badge">{typeLabel(c.customerType)}</span></p><p>Hoạt động đặt vé gần nhất: {dateTime(c.latestBookingAt)} · {c.latestJourney}</p><p>{c.customerType === 'ACCOUNT' ? 'Liên hệ từ booking gần nhất; người đặt, hành khách và người trả tiền có thể khác nhau.' : 'Liên hệ của một booking PHONE, chưa xác minh danh tính. Các booking trùng số điện thoại được giữ riêng.'}</p></section>
    <dl className="customer-metrics"><div><dt>Tổng booking</dt><dd>{c.totalBookings}</dd></div><div><dt>Đã xác nhận</dt><dd>{c.confirmedBookings}</dd></div><div><dt>Đã hủy</dt><dd>{c.cancelledBookings}</dd></div><div><dt>Chuyến có ghi nhận lên xe</dt><dd>{c.boardedJourneys}</dd></div><MoneySummary value={c.money} /></dl>
    <p>WEB: {c.webBookings} · PHONE: {c.phoneBookings}</p><p>{attendanceText(c.attendance)}. Ghi nhận theo từng ghế, không xác minh người liên hệ đã đi.</p>
    <h2>Lịch sử đặt vé</h2>
    <div className="customer-history">{data.bookings.data.map(b => <article className="operator-panel customer-history-row" key={b.bookingId}>
      <div><Link to={`/operator/bookings/${b.bookingId}`}><strong>{b.bookingCode}</strong></Link> <span className="operator-badge">{b.source}</span><p>{b.routeName} · {b.pickup} → {b.dropoff}</p><p>Ghế: {b.seats} · {dateTime(b.createdAt)}</p><p>Liên hệ lúc đặt: {b.contactName} · {b.contactPhone}{b.contactEmail && ` · ${b.contactEmail}`}</p></div>
      <div><p>{stateLabel[b.bookingStatus] || b.bookingStatus} · {stateLabel[b.paymentMethod] || b.paymentMethod} · {stateLabel[b.paymentStatus] || b.paymentStatus}</p><p>Vé còn hiệu lực: {b.validTickets} · Vé vô hiệu: {b.voidTickets}</p><p>{attendanceText(b.attendance)}</p>{b.bookingStatus === 'CANCELLED' && <p>Hủy: {stateLabel[b.cancellationReason || ''] || 'Chưa ghi nhận lý do'}{b.cancelledAt && ` · ${dateTime(b.cancelledAt)}`}</p>}<dl className="customer-money"><MoneySummary value={b.money} /></dl></div>
      {(b.payments.length > 0 || b.refunds.length > 0) && <details><summary>Giao dịch mô phỏng</summary>{b.payments.map(p => <p key={p.id}>{stateLabel[p.method] || p.method} · {stateLabel[p.status] || p.status} · {money(p.amount)} · {dateTime(p.paidAt || p.createdAt)}</p>)}{b.refunds.map(r => <p key={r.id}>Hoàn tiền mô phỏng: {money(r.amount)} · {stateLabel[r.reason] || r.reason} · {dateTime(r.refundedAt)}</p>)}</details>}
    </article>)}</div>
    <Pagination pagination={data.bookings.pagination} set={set} />
  </>;
}
function Directory() {
  const { params, page, size, set } = useOperatorFilters();
  const [search, setSearch] = useState(params.get('q') || '');
  const filters: CustomerFilters = { q: params.get('q') || undefined, customerType: (params.get('customerType') || undefined) as CustomerType | undefined, sort: (params.get('sort') || 'LATEST') as CustomerSort, page, size };
  const query = useQuery({ queryKey: ['operator', 'customers', filters], queryFn: ({ signal }) => customersApi.directory(filters, signal) });
  return <><OperatorPageHeader title="Khách hàng" /><p>Danh bạ liên hệ từ đặt vé của nhà xe. Không gộp danh tính theo số điện thoại.</p>
    <form className="operator-filters customer-filters" onSubmit={e => { e.preventDefault(); set('q', search.trim()); }}><label className="field">Tìm tên, điện thoại, email hoặc mã booking<input maxLength={150} value={search} onChange={e => setSearch(e.target.value)} type="search" /></label><button type="submit">Tìm kiếm</button><label className="field">Loại khách<select value={filters.customerType || ''} onChange={e => set('customerType', e.target.value)}><option value="">Tất cả</option><option value="ACCOUNT">Khách có tài khoản</option><option value="OFFLINE_CONTACT">Khách đặt qua điện thoại</option></select></label><label className="field">Sắp xếp<select value={filters.sort} onChange={e => set('sort', e.target.value)}><option value="LATEST">Đặt vé gần nhất</option><option value="NAME">Tên A–Z</option><option value="BOOKINGS">Tổng booking</option><option value="MOCK_PAID">Thu mô phỏng</option></select></label></form>
    <QueryState query={query}>{data => <CustomerDirectoryContent data={data} set={set} />}</QueryState></>;
}
function Detail() {
  const { customerKey = '' } = useParams();
  const { page, size, set } = useOperatorFilters();
  const query = useQuery({ queryKey: ['operator', 'customers', customerKey, page, size], queryFn: ({ signal }) => customersApi.detail(customerKey, page, size, signal) });
  return <><OperatorPageHeader title="Lịch sử khách hàng"><Link to="/operator/customers">Danh bạ khách hàng</Link></OperatorPageHeader><QueryState query={query}>{data => <CustomerDetailContent data={data} set={set} />}</QueryState></>;
}
export function OperatorCustomersPage() { const auth = useAuth(); return canManageOperator(auth.user?.roles) ? <Directory /> : <Empty title="Bạn không có quyền truy cập khách hàng" />; }
export function OperatorCustomerDetailPage() { const auth = useAuth(); return canManageOperator(auth.user?.roles) ? <Detail /> : <Empty title="Bạn không có quyền truy cập khách hàng" />; }
