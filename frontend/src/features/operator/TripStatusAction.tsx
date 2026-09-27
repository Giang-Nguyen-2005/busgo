import { useEffect, useRef, useState } from "react";
import type { TripStatus } from "../../types/operator";
import { nextTripAction } from "./operations";
import { useTripStatusMutation } from "./queries";
import { OperationsError } from "./OperationsShared";

export function TripStatusAction({ id, status }: { id: number; status: TripStatus }) {
  const action = nextTripAction(status);
  const mutation = useTripStatusMutation(id);
  const dialog = useRef<HTMLDialogElement>(null);
  const [message, setMessage] = useState("");
  // Never silently substitute a new action while a confirmation is open.
  useEffect(() => { dialog.current?.close(); }, [id, status]);
  return <div className="operator-trip-action">
    {message && <p role="status">{message}</p>}
    {mutation.isError && <OperationsError error={mutation.error} />}
    {action && <>
      <button disabled={mutation.isPending} onClick={() => { mutation.reset(); setMessage(""); dialog.current?.showModal(); }}>{action.label}</button>
      <dialog ref={dialog} className="operator-status-dialog" aria-labelledby="trip-status-title" aria-describedby="trip-status-impact" onCancel={e => { if (mutation.isPending) e.preventDefault(); }}>
        <h2 id="trip-status-title">{action.label}?</h2><p id="trip-status-impact">{action.impact}</p>
        <div className="operator-actions"><button autoFocus className="secondary" disabled={mutation.isPending} onClick={() => dialog.current?.close()}>Quay lại</button>
          <button disabled={mutation.isPending} onClick={() => mutation.mutate(action.status, {
            onSuccess: () => { dialog.current?.close(); setMessage("Đã cập nhật trạng thái chuyến."); },
            onError: () => dialog.current?.close(),
          })}>{mutation.isPending ? "Đang cập nhật…" : "Xác nhận"}</button></div>
      </dialog>
    </>}
  </div>;
}
