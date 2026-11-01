import type { Dropoff, Pickup, PrebookingInformation, Seat } from "../../types/customer";
import { dateTime } from "../../utils/format";

export function PrebookingDetails({ information, pickup, dropoff, vehicleName, seats, snapshot = false }: {
  information?: PrebookingInformation;
  pickup: Pickup;
  dropoff: Dropoff;
  vehicleName?: string;
  seats?: Pick<Seat, "floor">[];
  snapshot?: boolean;
}) {
  const floors = [...new Set(seats?.map(s => s.floor))].sort((a, b) => a - b);
  const policy = information?.policy;
  const rules = policy && [policy.fullCancellation, policy.partialCancellation, policy.modification, policy.refund, policy.eligibilityNotice].filter(Boolean);
  return <details className="prebooking-information">
    <summary>Thông tin cần biết trước khi đặt vé</summary>
    {snapshot && <p className="fine-print">Thông tin tham khảo từ bước chọn ghế. Giờ dưới đây lấy từ lượt giữ chỗ hiện tại; giá và điều kiện giao dịch do hệ thống kiểm tra lại.</p>}
    <div className="prebooking-stops">
      {([
        ["Điểm đón đã chọn", pickup.name, pickup.departureTime, information?.pickup],
        ["Điểm trả đã chọn", dropoff.name, dropoff.arrivalTime, information?.dropoff],
      ] as const).map(([label, name, time, context]) => <section key={label}>
        <h3>{label}</h3><strong>{name}</strong><p>{time ? `${dateTime(time)} · Giờ theo lịch, Việt Nam` : "Chưa có giờ theo lịch."}</p>
        <p>Khu vực: {[context?.district, context?.province].filter(Boolean).join(", ") || "Chưa cập nhật."}</p>
        <p className="muted">Chưa có địa chỉ và hướng dẫn lên/xuống xe được xác nhận công khai. Tên bến, thành phố hoặc khu vực không xác nhận vị trí đón/trả chính xác.</p>
      </section>)}
    </div>
    <section><h3>Loại xe và sơ đồ chỗ</h3><strong>{vehicleName || "Chưa cập nhật tên loại xe."}</strong>
      <p>{information?.vehicle?.seatCount ? `${information.vehicle.seatCount} chỗ theo cấu hình loại xe` : "Chưa cập nhật số chỗ theo loại xe."}</p>
      <p>{information?.vehicle?.description?.trim() || "Chưa có mô tả cấu hình."}</p>
      {seats?.length ? <p>Sơ đồ chuyến: {seats.length} vị trí · Tầng {floors.join(", ")}. Trạng thái từng chỗ được kiểm tra riêng theo chặng.</p> : <p>Chưa có thông tin tầng/sơ đồ trong phần tóm tắt này.</p>}
      <p className="muted">Mô tả loại xe không xác nhận tiện nghi chưa được công bố. Số chỗ cấu hình không phải số chỗ còn trống.</p>
    </section>
    <section><h3>Đổi, hủy và hoàn tiền mô phỏng</h3>
      {rules?.length ? <>{policy?.customerCutoffAt && <p><strong>Mốc yêu cầu của khách theo giờ đón đã chọn: {dateTime(policy.customerCutoffAt)} · Giờ Việt Nam</strong></p>}<ul>{rules.map((rule, index) => <li key={index}>{rule}</li>)}</ul></> : <p>Chưa có bản tóm tắt chính sách áp dụng. Không thể xác nhận quyền đổi/hủy từ thông tin hiện có; xem điều kiện do hệ thống cung cấp tại chi tiết đặt vé.</p>}
    </section>
  </details>;
}
