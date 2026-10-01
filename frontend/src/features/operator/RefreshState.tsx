import type { UseQueryResult } from "@tanstack/react-query";
import { dateTime } from "../../utils/format";
export function RefreshState({ query, onRefresh }: { query: Pick<UseQueryResult, "isFetching" | "dataUpdatedAt" | "refetch" | "isPlaceholderData">; onRefresh?: () => void }) {
  return <div className="operator-refresh" role="status">
    <span>{query.isFetching ? "Đang cập nhật…" : query.isPlaceholderData ? "Đang hiển thị kết quả trước" : query.dataUpdatedAt ? `Cập nhật: ${dateTime(new Date(query.dataUpdatedAt).toISOString())}` : "Chưa cập nhật"}</span>
    <button className="secondary" disabled={query.isFetching} onClick={() => onRefresh ? onRefresh() : void query.refetch()}>Làm mới</button>
  </div>;
}
