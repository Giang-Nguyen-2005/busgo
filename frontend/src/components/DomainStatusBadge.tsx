import { statusPresentation, type StatusDomain } from "../utils/status";
export function DomainStatusBadge({ domain, status }: { domain: StatusDomain; status: string }) {
  const { label, tone } = statusPresentation(domain, status);
  return <span className={`domain-badge tone-${tone}`}>{label}</span>;
}
