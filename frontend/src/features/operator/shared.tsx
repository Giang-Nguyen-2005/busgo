import type { ReactNode } from "react";
import type { UseQueryResult } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import axios from "axios";
import { Empty, Loading } from "../../components/ui";
import { errorMessage } from "../../api/errors";
import type { ApiError, PagedResponse } from "../../types/api";
export const busStatuses = ["AVAILABLE", "MAINTENANCE", "INACTIVE"] as const;
export const tripStatuses = [
  "SCHEDULED",
  "BOARDING",
  "DEPARTED",
  "COMPLETED",
  "CANCELLED",
] as const;
const labels: Record<string, string> = {
  ACTIVE: "Hoạt động",
  INACTIVE: "Ngừng hoạt động",
  AVAILABLE: "Sẵn sàng",
  MAINTENANCE: "Bảo trì",
  SCHEDULED: "Đã lên lịch",
  BOARDING: "Đang đón khách",
  DEPARTED: "Đã khởi hành",
  COMPLETED: "Hoàn thành",
  CANCELLED: "Đã hủy",
};
export function OperatorStatusBadge({ status }: { status: string }) {
  return (
    <span className={`operator-badge operator-status-${status}`}>
      {labels[status] || status}
    </span>
  );
}
export function OperatorPageHeader({
  title,
  children,
}: {
  title: string;
  children?: ReactNode;
}) {
  return (
    <div className="operator-heading">
      <h1>{title}</h1>
      <div className="operator-actions">{children}</div>
    </div>
  );
}
export function OperatorError({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  const body = axios.isAxiosError<ApiError>(error)
    ? error.response?.data
    : undefined;
  return (
    <div className="notice danger" role="alert">
      <div>
        {body?.message || errorMessage(error)}
        {body?.code && (
          <small className="operator-error-code">{body.code}</small>
        )}
        {retry && (
          <button className="secondary" onClick={retry}>
            Thử lại
          </button>
        )}
      </div>
    </div>
  );
}
export function QueryState<T>({
  query,
  children,
}: {
  query: UseQueryResult<T, Error>;
  children: (data: T) => ReactNode;
}) {
  if (query.isPending) return <Loading />;
  if (query.isError)
    return <OperatorError error={query.error} retry={() => query.refetch()} />;
  return <>{children(query.data)}</>;
}
export function OperatorTable({
  headers,
  children,
  empty,
}: {
  headers: string[];
  children: ReactNode;
  empty?: boolean;
}) {
  if (empty) return <Empty title="Chưa có dữ liệu phù hợp" />;
  return (
    <div
      className="operator-table-scroll"
      tabIndex={0}
      role="region"
      aria-label="Bảng dữ liệu"
    >
      <table className="operator-table">
        <thead>
          <tr>
            {headers.map((h) => (
              <th scope="col" key={h}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
export function positiveId(value: string | null) {
  const n = Number(value);
  return Number.isSafeInteger(n) && n > 0 ? n : undefined;
}
export function useOperatorFilters() {
  const [params, setParams] = useSearchParams();
  const n = Number(params.get("page"));
  const page = Number.isSafeInteger(n) && n >= 0 && n <= 2147483647 ? n : 0;
  const size = [10, 20, 50, 100].includes(Number(params.get("size")))
    ? Number(params.get("size"))
    : 20;
  const set = (key: string, value: string) =>
    setParams((previous) => {
      const next = new URLSearchParams(previous);
      value ? next.set(key, value) : next.delete(key);
      if (key !== "page") next.delete("page");
      return next;
    });
  return { params, page, size, set };
}
export function Pagination({
  pagination,
  set,
}: {
  pagination: PagedResponse<unknown>["pagination"];
  set: (key: string, value: string) => void;
}) {
  return (
    <div className="operator-pagination">
      <span>
        {pagination.totalElements} kết quả · Trang {pagination.page + 1}/
        {Math.max(1, pagination.totalPages)}
      </span>
      <label>
        Số dòng{" "}
        <select
          value={pagination.size}
          onChange={(e) => set("size", e.target.value)}
        >
          {[10, 20, 50, 100].map((n) => (
            <option key={n}>{n}</option>
          ))}
        </select>
      </label>
      <button
        className="secondary"
        disabled={pagination.page === 0}
        onClick={() => set("page", String(pagination.page - 1))}
      >
        Trước
      </button>
      <button
        className="secondary"
        disabled={pagination.page + 1 >= pagination.totalPages}
        onClick={() => set("page", String(pagination.page + 1))}
      >
        Sau
      </button>
    </div>
  );
}
export function FormErrors({ messages }: { messages: (string | undefined)[] }) {
  const errors = messages.filter(Boolean);
  return errors.length ? (
    <div role="alert" className="notice danger">
      {errors.join(" · ")}
    </div>
  ) : null;
}
export function Confirm({
  text,
  pending,
  confirm,
  cancel,
}: {
  text: string;
  pending: boolean;
  confirm: () => void;
  cancel: () => void;
}) {
  return (
    <div
      className="operator-confirm"
      role="group"
      aria-label="Xác nhận thay đổi"
    >
      <p>{text}</p>
      <div className="operator-actions">
        <button type="button" disabled={pending} onClick={confirm}>
          {pending ? "Đang lưu…" : "Xác nhận"}
        </button>
        <button
          type="button"
          className="secondary"
          disabled={pending}
          onClick={cancel}
        >
          Quay lại
        </button>
      </div>
    </div>
  );
}
