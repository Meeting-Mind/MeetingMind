import type {
  DomainTermStatus,
  MeetingReportStatus,
  MeetingStatus,
  ParticipantAccessStatus,
  TaskCardStatus,
  TranscriptStatus
} from "../../types";

export type StatusBadgeContext = "meeting" | "transcript" | "report" | "task" | "access" | "knowledge" | "generic";
export type StatusBadgeValue =
  | MeetingStatus
  | TranscriptStatus
  | MeetingReportStatus
  | TaskCardStatus
  | ParticipantAccessStatus
  | DomainTermStatus
  | string;
export type StatusPresentation = {
  label: string;
  tone: "neutral" | "info" | "positive" | "warning" | "danger";
};

const STATUS_LABELS: Record<string, StatusPresentation> = {
  SCHEDULED: { label: "예정", tone: "info" },
  IN_PROGRESS: { label: "진행 중", tone: "warning" },
  ENDED: { label: "종료", tone: "neutral" },
  CANCELED: { label: "취소", tone: "danger" },
  PENDING: { label: "대기 중", tone: "neutral" },
  PROCESSING: { label: "처리 중", tone: "warning" },
  COMPLETED: { label: "완료", tone: "positive" },
  FAILED: { label: "실패", tone: "danger" },
  CANDIDATE: { label: "후보", tone: "warning" },
  DRAFT: { label: "초안", tone: "info" },
  CONFIRMED: { label: "확정", tone: "positive" },
  TODO: { label: "할 일", tone: "neutral" },
  DONE: { label: "완료", tone: "positive" },
  ACTIVE: { label: "활성", tone: "positive" },
  REVOKED: { label: "회수됨", tone: "danger" },
  ARCHIVED: { label: "보관됨", tone: "neutral" }
};

export function getStatusBadgePresentation(status: StatusBadgeValue, label?: string): StatusPresentation {
  return label ? { label, tone: STATUS_LABELS[status]?.tone ?? "neutral" } : STATUS_LABELS[status] ?? { label: status, tone: "neutral" };
}
