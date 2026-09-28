import { Link } from "react-router-dom";
import { useAuth } from "../../features/auth/AuthProvider";
import { canManageOperator, operatorNavigation } from "../../features/auth/access";
import { OperatorPageHeader } from "../../features/operator/shared";
export function OperatorHomePage() {
  const { user } = useAuth();
  return <><OperatorPageHeader title="Điều hành nhà xe" /><p>{canManageOperator(user?.roles) ? "Quản lý hoạt động và nhân sự nhà xe." : "Xem chuyến xe, đặt vé và tình trạng vận hành. Tài khoản chỉ có quyền xem."}</p>
    <div className="operator-quick-actions">{operatorNavigation(user?.roles).filter(([path]) => path).map(([path, title]) => <Link className="card" key={path} to={"/operator" + path}><h2>{title}</h2></Link>)}
    {canManageOperator(user?.roles) && <><Link className="card" to="/operator/trips/new">Tạo chuyến</Link><Link className="card" to="/operator/routes/catalog">Đăng ký tuyến</Link></>}
    </div></>;
}
