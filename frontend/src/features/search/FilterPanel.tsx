import { useEffect, useRef, useState, type ReactNode } from "react";
import { SlidersHorizontal, X } from "lucide-react";

export function FilterPanel({
  children,
  activeCount,
  onCloseReady,
}: {
  children: ReactNode;
  activeCount: number;
  onCloseReady: (close: () => void) => void;
}) {
  const [mobile, setMobile] = useState(
    () => window.matchMedia("(max-width: 760px)").matches,
  );
  const dialog = useRef<HTMLDialogElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const close = () => {
    dialog.current?.close();
    trigger.current?.focus();
  };
  useEffect(() => {
    const media = window.matchMedia("(max-width: 760px)");
    const change = () => setMobile(media.matches);
    media.addEventListener("change", change);
    return () => media.removeEventListener("change", change);
  }, []);
  useEffect(() => {
    onCloseReady(close);
  });
  useEffect(
    () => () => {
      document.body.style.overflow = "";
    },
    [mobile],
  );
  if (!mobile)
    return (
      <aside className="card filters">
        <div className="filter-title">
          <SlidersHorizontal size={18} />
          <h2>Bộ lọc chuyến xe</h2>
        </div>
        {children}
      </aside>
    );
  return (
    <>
      <button
        ref={trigger}
        className="secondary filter-trigger"
        aria-haspopup="dialog"
        onClick={() => {
          dialog.current?.showModal();
          document.body.style.overflow = "hidden";
        }}
      >
        <SlidersHorizontal size={17} />
        Bộ lọc{activeCount > 0 && <span className="badge">{activeCount}</span>}
      </button>
      <dialog
        ref={dialog}
        className="filter-sheet"
        aria-labelledby="filter-title"
        onClose={() => {
          document.body.style.overflow = "";
        }}
        onClick={(event) => {
          if (event.target === dialog.current) close();
        }}
      >
        <div className="filter-sheet-content">
          <div className="split">
            <h2 id="filter-title">Bộ lọc chuyến xe</h2>
            <button
              type="button"
              className="icon-button"
              aria-label="Đóng bộ lọc"
              onClick={close}
            >
              <X size={20} />
            </button>
          </div>
          {children}
        </div>
      </dialog>
    </>
  );
}
