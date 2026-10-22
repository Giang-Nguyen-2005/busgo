import type { ButtonHTMLAttributes } from "react";

export function IconButton({ label, className = "", children, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return <button type="button" {...props} className={`app-icon-button ${className}`} aria-label={label} title={label}>{children}</button>;
}
