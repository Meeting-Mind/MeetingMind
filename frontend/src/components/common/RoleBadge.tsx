import {
  getRoleBadgePresentation,
  type RoleBadgeScope,
  type RoleBadgeValue
} from "./roleBadgeModel";

export type { RoleBadgeScope, RoleBadgeValue } from "./roleBadgeModel";

export function RoleBadge({
  role,
  scope,
  label,
  className = ""
}: {
  role: RoleBadgeValue;
  scope: RoleBadgeScope;
  label?: string;
  className?: string;
}) {
  const presentation = getRoleBadgePresentation(role, label);

  return (
    <span
      aria-label={`${scope === "space" ? "프로젝트" : "회의"} 역할: ${presentation.label}`}
      className={`mm-role-badge mm-role-badge--${presentation.tone} ${className}`.trim()}
      data-role={role}
      data-scope={scope}
      role="status"
    >
      <span aria-hidden="true" className="mm-role-badge-mark" />
      {presentation.label}
    </span>
  );
}
