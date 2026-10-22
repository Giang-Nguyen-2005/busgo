import { LoaderCircle } from "lucide-react";
import "./foundations.css";

export function LoadingIndicator({ label = "Đang tải thông tin…" }: { label?: string }) {
  return <div className="app-loading" role="status"><LoaderCircle aria-hidden="true" className="app-loading-icon" size={22} /><span>{label}</span></div>;
}

export function Skeleton({ variant = "line" }: { variant?: "line" | "heading" | "panel" | "circle" }) {
  return <span aria-hidden="true" className={`app-skeleton app-skeleton-${variant}`} />;
}

/** Decorative placeholders inside one descriptive live region; no invented business values. */
export function LoadingSkeleton({ label = "Đang tải thông tin…", variant = "list", rows = 3 }: {
  label?: string; variant?: "list" | "table" | "summary" | "panel"; rows?: number;
}) {
  return <div className={`app-loading-skeleton app-loading-skeleton-${variant}`} role="status" aria-label={label}>
    <span className="app-sr-only">{label}</span>
    {Array.from({ length: rows }, (_, index) => <div className="app-skeleton-row" key={index}><Skeleton variant="heading" /><Skeleton /><Skeleton /></div>)}
  </div>;
}
