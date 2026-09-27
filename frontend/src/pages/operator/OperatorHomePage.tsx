import { Link } from "react-router-dom";
import { OperatorPageHeader } from "../../features/operator/shared";
export function OperatorHomePage() {
  return (
    <>
      <OperatorPageHeader title="Điều hành nhà xe" />
      <p>Quản lý đội xe, tuyến vận hành, giá vé và lịch khởi hành.</p>
      <div className="operator-quick-actions">
        {[
          ["trips", "Chuyến xe", "Xem lịch và chi tiết chuyến"],
          ["trips/new", "Tạo chuyến", "Chọn tuyến, xe và giờ khởi hành"],
          ["buses", "Đội xe", "Thêm xe và cập nhật trạng thái"],
          ["bus-types", "Loại xe", "Xem mẫu sơ đồ ghế"],
          ["routes", "Tuyến vận hành", "Quản lý tuyến và toàn bộ bảng giá"],
          ["routes/catalog", "Danh mục tuyến", "Đăng ký tuyến đang hoạt động"],
        ].map(([path, title, description]) => (
          <Link className="card" to={`/operator/${path}`} key={path}>
            <h2>{title}</h2>
            <p>{description}</p>
          </Link>
        ))}
      </div>
    </>
  );
}
