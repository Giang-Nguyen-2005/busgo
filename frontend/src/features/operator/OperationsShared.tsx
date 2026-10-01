import type { ReactNode } from "react";
import type { UseQueryResult } from "@tanstack/react-query";
import { errorCode } from "../../api/errors";
import { Loading } from "../../components/ui";
import { operationErrorMessage, operationLabel } from "./operations";
export function OperationsError({ error, retry }: { error: unknown; retry?: () => void }) {
  return <div className="notice danger" role="alert">{operationErrorMessage(errorCode(error))}
    {retry && <button className="secondary" onClick={retry}>Thử lại</button>}
  </div>;
}
export function OperationsQueryState<T>({ query, children }: { query: UseQueryResult<T, Error>; children: (data: T) => ReactNode }) {
  if (query.isPending) return <Loading />;
  if (query.isError && (query.data === undefined || [401, 403].includes((query.error as { response?: { status: number } })?.response?.status || 0))) return <OperationsError error={query.error} retry={() => query.refetch()} />;
  return <>{query.isError && <div className="notice danger" role="alert">Cập nhật tạm thời thất bại. Đang hiển thị dữ liệu lần trước. <button onClick={() => void query.refetch()}>Thử lại</button></div>}{query.data !== undefined && children(query.data)}</>;
}
export function OperationsBadge({ status }: { status: string }) {
  return <span className={`operator-badge operator-status-${status}`}>{operationLabel(status)}</span>;
}
