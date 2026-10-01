import type { UseQueryResult } from "@tanstack/react-query";
export function blockingQueryError(query: { isError: boolean; data: unknown; error: unknown }) {
  const status = (query.error as { response?: { status?: number } } | null)?.response?.status;
  return query.isError && (query.data === undefined || (status !== undefined && status < 500));
}
export function RefreshNotice({ query }: { query: Pick<UseQueryResult, "isError" | "isFetching" | "refetch"> }) {
  return query.isError ? <div className="notice warning" role="status">Không thể cập nhật. Đang hiển thị thông tin lần tải trước. <button type="button" className="text-button" onClick={() => void query.refetch()}>Thử lại</button></div> : query.isFetching ? <p className="fine-print" role="status">Đang cập nhật…</p> : null;
}
