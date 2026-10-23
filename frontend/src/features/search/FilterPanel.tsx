import { useEffect, useRef, useState, type ReactNode } from "react";
import { SlidersHorizontal, X } from "lucide-react";

export function FilterPanel({
  children,
  activeCount,
  onCloseReady,
  onOpen,
}: {
  children: ReactNode;
  activeCount: number;
  onCloseReady: (close: () => void) => void;
  onOpen: () => void;
}) {
  const [mobile, setMobile] = useState(
    () => window.matchMedia("(max-width: 760px)").matches,
  );
  const dialog = useRef<HTMLDialogElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const previousOverflow = useRef<string | null>(null);
  const unlock = () => {
    if (previousOverflow.current !== null) document.body.style.overflow = previousOverflow.current;
    previousOverflow.current = null;
  };
  const close = () => {
    dialog.current?.close();
    unlock();
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
      unlock();
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
          onOpen();
          previousOverflow.current = document.body.style.overflow;
          dialog.current?.showModal();
          dialog.current?.querySelector<HTMLButtonElement>(".icon-button")?.focus({ preventScroll: true });
          dialog.current?.querySelector(".filter-fields")?.scrollTo(0, 0);
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
        onKeyDown={(event) => {
          if (event.key !== "Tab") return;
          const controls = [...event.currentTarget.querySelectorAll<HTMLElement>(
            'button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex="0"]',
          )];
          const first = controls[0];
          const last = controls.at(-1);
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last?.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first?.focus();
          }
        }}
        onClose={() => {
          unlock();
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
