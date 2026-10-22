import type { ReactNode } from "react";
import "./foundations.css";

export function PageHeader({ title, description, actions, className = "", actionsClassName = "" }: {
  title: string; description?: ReactNode; actions?: ReactNode; className?: string; actionsClassName?: string;
}) {
  return <div className={`app-page-header ${className}`}>
    <div><h1>{title}</h1>{description && <p>{description}</p>}</div>
    {actions && <div className={actionsClassName}>{actions}</div>}
  </div>;
}
