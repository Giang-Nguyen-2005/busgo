import { useNavigation } from "react-router-dom";
export function RouteProgress() {
  const navigation = useNavigation();
  return navigation.state !== "idle" ? <div className="route-progress" role="status">Đang mở trang…</div> : null;
}
