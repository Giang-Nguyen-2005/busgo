import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
export const adminTotalRequests = [
 { label: "Tổng nhà xe", params: { page: 0, size: 1 }, href: "/admin/operators" },
 { label: "Đang hoạt động", params: { page: 0, size: 1, status: "ACTIVE" as const }, href: "/admin/operators?status=ACTIVE" },
 { label: "Ngừng hoạt động", params: { page: 0, size: 1, status: "INACTIVE" as const }, href: "/admin/operators?status=INACTIVE" },
];
export const deactivateExplanation = "Ngừng hoạt động sẽ dừng quyền quản lý nhà xe và giao dịch mới của khách hàng. Chuyến xe, đặt vé, thanh toán và vé hiện có KHÔNG tự động bị hủy hoặc hoàn tiền.";
export const activateExplanation = "Kích hoạt nhà xe yêu cầu ít nhất một quản trị viên nhà xe đang hoạt động và có thể đăng nhập. Hệ thống kiểm tra điều kiện khi bạn xác nhận.";
export const ADMIN_SEARCH_DELAY = 350;
export function useAdminSearch() {
 const [params, setParams] = useSearchParams();
 const [draft, setDraft] = useState(params.get("q") || "");
 const urlText = params.get("q") || "";
 useEffect(() => setDraft(urlText), [urlText]);
 useEffect(() => {
  if (draft === urlText) return;
  const timer = setTimeout(() => setParams(previous => { const next = new URLSearchParams(previous); draft.trim() ? next.set("q", draft.trim()) : next.delete("q"); next.delete("page"); return next; }, { replace: true }), ADMIN_SEARCH_DELAY);
  return () => clearTimeout(timer);
 }, [draft, urlText, setParams]);
 return { draft, setDraft };
}
export const legacyCreatePath = (params: URLSearchParams) => params.get("create") === "1" ? "/admin/operators/new" : null;
