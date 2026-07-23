import {
  getStatusBadgePresentation,
  type StatusBadgeContext,
  type StatusBadgeValue
} from "./statusBadgeModel";

export type { StatusBadgeContext, StatusBadgeValue } from "./statusBadgeModel";

export function StatusBadge({
  status,
  context = "generic",
  label,
  className = ""
}: {
  status: StatusBadgeValue;
  context?: StatusBadgeContext;
  label?: string;
  className?: string;
}) {
  const presentation = getStatusBadgePresentation(status, label);

  return (
    <span
      aria-label={`${context} 상태: ${presentation.label}`}
      className={`mm-status-badge mm-status-badge--${presentation.tone} ${className}`.trim()}
      data-context={context}
      data-status={status}
      role="status"
    >
      <span aria-hidden="true" className="mm-status-badge-dot" />
      {presentation.label}
    </span>
  );
}
