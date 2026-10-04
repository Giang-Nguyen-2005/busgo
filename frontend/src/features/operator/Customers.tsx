import { DomainStatusBadge } from "../../components/DomainStatusBadge";
import { paymentMethodLabel, paymentStatusLabel } from "../../utils/format";
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
const stateLabel: Record<string, string> = { CUSTOMER_CANCELLED: 'Khách hủy', OPERATOR_CANCELLED: 'Nhà xe hủy', PAYMENT_TIMEOUT: 'Hết hạn thanh toán' };
export function attendanceText(a: CustomerAttendance) {
  return [[a.boarded, 'đã lên xe'], [a.noShow, 'vắng mặt'], [a.checkedIn, 'đã check-in'], [a.unrecorded, 'chưa ghi nhận']].filter(([n]) => Number(n) > 0).map(([n, label]) => `${n} ${label}`).join(' / ') || 'Chưa ghi nhận';
}
function Contact({ customer }: { customer: OperatorCustomer }) {
  return <><Link className="customer-name" to={`/operator/customers/${encodeURIComponent(customer.customerKey)}`}>{customer.displayName}</Link><span>{customer.phone}</span>{customer.email && <span>{customer.email}</span>}</>;
}
export function CustomerDirectoryContent({ data, set }: { data: PagedResponse<OperatorCustomer>; set: (key: string, value: string) => void }) {
  if (!data.data.length) return <><Empty title="Chưa có khách hàng phù hợp" /><Pagination pagination={data.pagination} set={set} /></>;
  return <>
    <div className="customer-desktop"><OperatorTable headers={['Khách hàng', 'Liên hệ', 'Loại', 'Tổng booking', 'Chuyến gần nhất', 'Thu mô phỏng', 'Hoạt động gần nhất']}>
      {data.data.map(c => <tr key={c.customerKey}><td><Link className="customer-name" to={`/operator/customers/${encodeURIComponent(c.customerKey)}`}>{c.displayName}</Link><small className="customer-secondary">{c.customerType === 'ACCOUNT' ? 'Tài khoản BusGo' : 'Liên hệ offline'}</small></td><td className="customer-contact"><span className="customer-phone">{c.phone}</span>{c.email && <span>{c.email}</span>}</td><td><span className="customer-secondary">{typeLabel(c.customerType)}</span></td><td><strong className="customer-value">{c.totalBookings}</strong></td><td><span className="customer-journey">{c.latestJourney}</span></td><td><strong className="customer-value customer-currency">{money(c.money.grossMockPaid)}</strong></td><td className="customer-secondary">{dateTime(c.latestBookingAt)}</td></tr>)}
    </OperatorTable></div>
    <div className="customer-mobile">{data.data.map(c => <article className="operator-panel customer-card" key={c.customerKey}><div className="customer-contact"><Contact customer={c} /></div><p className="customer-secondary">{typeLabel(c.customerType)}</p><p className="customer-journey">{c.latestJourney}</p><dl className="customer-money"><div><dt>Booking</dt><dd>{c.totalBookings}</dd></div><div><dt>Thu mô phỏng</dt><dd>{money(c.money.grossMockPaid)}</dd></div></dl><p className="customer-secondary">Đặt gần nhất · {dateTime(c.latestBookingAt)}</p><Link to={`/operator/customers/${encodeURIComponent(c.customerKey)}`}>Xem lịch sử</Link></article>)}</div>
    <Pagination pagination={data.pagination} set={set} />
  </>;
}
function MoneySummary({ value }: { value: CustomerMoney }) {
  return <><div className="customer-metric-primary"><dt>Thu mô phỏng</dt><dd>{money(value.grossMockPaid)}</dd></div><div><dt>Hoàn tiền mô phỏng</dt><dd>{money(value.mockRefunds)}</dd></div><div><dt>Thu ròng mô phỏng</dt><dd>{money(value.netMockPaid)}</dd></div></>;
}
export function CustomerDetailContent({ data, set }: { data: CustomerDetail; set: (key: string, value: string) => void }) {
  const c = data.summary;
  return <>
    <section className="customer-summary"><div className="customer-summary-heading"><div className="customer-contact"><Contact customer={c} /></div><span className="customer-secondary">{typeLabel(c.customerType)}</span></div><p className="customer-journey">{c.latestJourney}</p><p className="customer-secondary">Đặt vé gần nhất · {dateTime(c.latestBookingAt)}</p><p className="customer-summary-helper">{c.customerType === 'ACCOUNT' ? 'Liên hệ từ booking gần nhất; người đặt, hành khách và người trả tiền có thể khác nhau.' : 'Liên hệ của một booking PHONE, chưa xác minh danh tính. Các booking trùng số điện thoại được giữ riêng.'}</p></section>
    <div className="customer-metric-groups"><section><h2>Đặt vé</h2><dl className="customer-metrics"><div className="customer-metric-primary"><dt>Tổng booking</dt><dd>{c.totalBookings}</dd></div><div><dt>Đã xác nhận</dt><dd>{c.confirmedBookings}</dd></div><div><dt>Đã hủy</dt><dd>{c.cancelledBookings}</dd></div></dl><p className="fine-print">Trực tuyến: {c.webBookings} · Qua điện thoại: {c.phoneBookings}</p></section>
    <section><h2>Hành trình</h2><dl className="customer-metrics"><div className="customer-metric-primary"><dt>Chuyến có ghi nhận lên xe</dt><dd>{c.boardedJourneys}</dd></div></dl><p>{attendanceText(c.attendance)}</p><p className="fine-print">Ghi nhận theo từng ghế, không xác minh người liên hệ đã đi.</p></section>
    <section><h2>Thanh toán</h2><dl className="customer-metrics"><MoneySummary value={c.money} /></dl></section></div>
    <h2>Lịch sử đặt vé</h2>
    <div className="customer-history">{data.bookings.data.map(b => <article className="operator-panel customer-history-row" key={b.bookingId}>
      <header className="customer-booking-heading"><div><Link className="customer-booking-code" to={`/operator/bookings/${b.bookingId}`}>{b.bookingCode}</Link><span className="customer-source">{b.source === 'PHONE' ? 'PHONE · Qua điện thoại' : b.source === 'WEB' ? 'WEB · Trực tuyến' : b.source}</span></div><DomainStatusBadge domain="booking" status={b.bookingStatus} /></header>
      <div><p className="customer-history-journey">{b.pickup} → {b.dropoff}</p><p className="customer-secondary">{b.routeName}</p><dl className="customer-history-facts"><div><dt>Ghế</dt><dd>{b.seats}</dd></div><div><dt>Tạo đặt vé</dt><dd>{dateTime(b.createdAt)}</dd></div></dl><div className="customer-history-contact"><span>{b.contactName}</span><span>{b.contactPhone}</span>{b.contactEmail && <span>{b.contactEmail}</span>}</div></div>
      <div className="customer-history-states"><div className="customer-payment-state"><DomainStatusBadge domain="payment" status={b.paymentStatus} /><span className="customer-secondary">{paymentMethodLabel(b.paymentMethod)}</span></div><dl className="customer-history-facts"><div><dt>Vé</dt><dd><span>Vé còn hiệu lực: {b.validTickets}</span><span className="customer-secondary">Vé vô hiệu: {b.voidTickets}</span></dd></div><div><dt>Điểm danh</dt><dd>{attendanceText(b.attendance)}</dd></div></dl>{b.bookingStatus === 'CANCELLED' && <p className="customer-cancellation">{stateLabel[b.cancellationReason || ''] || 'Chưa ghi nhận lý do'}{b.cancelledAt && <span className="customer-secondary">{dateTime(b.cancelledAt)}</span>}</p>}</div>
      <dl className="customer-money customer-history-money"><MoneySummary value={b.money} /></dl>
      {(b.payments.length > 0 || b.refunds.length > 0) && <details><summary>Giao dịch mô phỏng</summary>{b.payments.map(p => <p key={p.id}>{paymentMethodLabel(p.method)} · {paymentStatusLabel(p.status)} · {money(p.amount)} · {dateTime(p.paidAt || p.createdAt)}</p>)}{b.refunds.map(r => <p key={r.id}>Hoàn tiền mô phỏng: {money(r.amount)} · {stateLabel[r.reason] || r.reason} · {dateTime(r.refundedAt)}</p>)}</details>}
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
