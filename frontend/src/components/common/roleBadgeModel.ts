import type { MeetingRole, SpaceRole } from "../../types";

export type RoleBadgeScope = "space" | "meeting" | "guest";
export type RoleBadgeValue = SpaceRole | MeetingRole | "GUEST" | "MANAGER_OVERRIDE" | string;
export type RoleBadgeTone = "owner" | "admin" | "member" | "host" | "editor" | "viewer" | "guest" | "override" | "neutral";
export type RolePresentation = { label: string; tone: RoleBadgeTone };

const ROLE_LABELS: Record<string, RolePresentation> = {
  OWNER: { label: "프로젝트 오너", tone: "owner" },
  ADMIN: { label: "프로젝트 관리자", tone: "admin" },
  MEMBER: { label: "프로젝트 멤버", tone: "member" },
  HOST: { label: "회의 호스트", tone: "host" },
  EDITOR: { label: "회의 편집자", tone: "editor" },
  VIEWER: { label: "회의 열람자", tone: "viewer" },
  GUEST: { label: "회의 게스트", tone: "guest" },
  MANAGER_OVERRIDE: { label: "관리자 예외 접근", tone: "override" }
};

export function getRoleBadgePresentation(role: RoleBadgeValue, label?: string): RolePresentation {
  return label ? { label, tone: ROLE_LABELS[role]?.tone ?? "neutral" } : ROLE_LABELS[role] ?? { label: role, tone: "neutral" };
}
