import type {
  MeetingParticipantSummary,
  MeetingStatus,
  MeetingSummary,
  SpaceMemberSummary,
  SpaceSummary,
  TaskCard,
  TaskCardPriority,
  WorkspaceData
} from "../types";

export type WorkspaceDataSource = "loading" | "workspace-api" | "workspace-api-partial" | "legacy-api" | "mock-fallback";
export type ProjectMeeting = WorkspaceData["projectOverview"]["meetings"][number];

export type CreateMeetingPayload = {
  title?: string;
  description?: string;
  scheduledAt?: string;
  scheduledEndAt?: string;
  participantEmails?: string[];
};

export type UpdateProjectPayload = {
  name: string;
  description: string;
};

export type MeetingParticipantState = {
  id: string;
  userId?: string;
  meetingKey: string;
  name: string;
  email: string;
  role: "HOST" | "EDITOR" | "VIEWER";
  accessStatus: "ACTIVE" | "REVOKED";
  participantType: "member" | "guest";
};

export type ProjectTaskState = {
  id: string;
  title: string;
  description: string;
  status: "TODO" | "IN_PROGRESS" | "DONE";
  priority: TaskCardPriority;
  labels: string[];
  assignee: string;
  dueDate: string;
  meetingKey: string | null;
  sourceCandidateId: string | null;
};

export type TeamMember = {
  memberId?: string;
  userId?: string;
  name: string;
  email: string;
  role: string;
  spaceRole: "OWNER" | "ADMIN" | "MEMBER";
  since: string;
  access: string;
  rank: string;
  status: "active" | "away";
};

export type JoinRequest = {
  id: string;
  name: string;
  email: string;
  role: string;
  meetingIndex: string;
  meetingTitle: string;
  requestedAt: string;
  source: "링크" | "코드";
};

export type InviteMeta = {
  link: string;
  code: string;
};

export type MeetingInviteMeta = {
  meetingId: string;
  title: string;
  joinCode: string;
  joinUrl: string;
};

export function buildInviteMeta(projectName: string): InviteMeta {
  const slug = projectName.toLowerCase().replace(/[^a-z0-9가-힣]+/g, "-").replace(/^-+|-+$/g, "");
  const codeSeed = (projectName.replace(/[^A-Za-z0-9가-힣]/g, "").slice(0, 3).toUpperCase() || "NEW").padEnd(3, "X");

  return {
    link: `https://meetingmind.ai/invite/${encodeURIComponent(slug || "new-project")}`,
    code: `${codeSeed}-TEAM-${String(projectName.length).padStart(4, "0")}`
  };
}

export function buildSpaceId(projectName: string) {
  const slug = projectName
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9가-힣]+/g, "-")
    .replace(/^-+|-+$/g, "");

  return `space-${slug || "new-project"}`;
}

export function buildMeetingId(projectName: string, count: number) {
  return `${buildSpaceId(projectName)}-meeting-${String(count).padStart(2, "0")}`;
}

export function buildMeetingKey(projectName: string, meetingIndex: string) {
  return `${projectName}:${meetingIndex}`;
}

export function getSpaceRoleAccessLabel(spaceRole: TeamMember["spaceRole"]) {
  if (spaceRole === "OWNER") {
    return "프로젝트 오너";
  }

  if (spaceRole === "ADMIN") {
    return "프로젝트 관리자";
  }

  return "회의 참여 / 문서 열람";
}

export function inferMeetingTitle(projectName: string, description: string, index: number) {
  const source = `${projectName} ${description}`.toLowerCase();
  const defaultTitles = ["킥오프 회의", "프로젝트 범위 정의", "핵심 흐름 리뷰", "세부 안건 정리", "다음 단계 확정"];

  if (/(security|권한|보안|접근|정책)/.test(source)) {
    return ["킥오프 회의", "보안 정책 정리", "권한 구조 리뷰", "접근 제어 점검", "운영 승인 플로우 검토"][index - 1] ?? `${index}회차 후속 논의`;
  }

  if (/(data|rag|검색|ai|llm|모델|지식)/.test(source)) {
    return ["킥오프 회의", "데이터 구조 리뷰", "검색 흐름 정리", "응답 품질 검토", "문맥 연결 점검"][index - 1] ?? `${index}회차 후속 논의`;
  }

  if (/(admin|운영|ops|workflow|프로세스|자동화)/.test(source)) {
    return ["킥오프 회의", "운영 흐름 정리", "관리자 시나리오 리뷰", "자동화 정책 검토", "예외 처리 확정"][index - 1] ?? `${index}회차 후속 논의`;
  }

  return defaultTitles[index - 1] ?? `${index}회차 후속 논의`;
}

export function buildMeeting(projectName: string, description: string, count: number, payload?: CreateMeetingPayload): ProjectMeeting {
  const fallbackDate = new Date(2026, 5, 27 + (count - 1) * 7);
  const scheduledDate = payload?.scheduledAt ? new Date(payload.scheduledAt) : fallbackDate;

  return {
    id: buildMeetingId(projectName, count),
    index: `#${String(count).padStart(2, "0")}`,
    title: payload?.title?.trim() || inferMeetingTitle(projectName, description, count),
    date: `${String(scheduledDate.getMonth() + 1).padStart(2, "0")}.${String(scheduledDate.getDate()).padStart(2, "0")}`,
    state: "예정",
    scheduledAt: scheduledDate.toISOString(),
    scheduledEndAt: payload?.scheduledEndAt ?? new Date(scheduledDate.getTime() + 60 * 60 * 1000).toISOString(),
    description: payload?.description?.trim() || null,
    durationMinutes: 60
  };
}

export function formatDateLabel(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "업데이트 정보 없음";
  }
  return `${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")} 업데이트`;
}

export function meetingStatusLabel(status: MeetingStatus): ProjectMeeting["state"] {
  if (status === "IN_PROGRESS") {
    return "진행 중";
  }
  if (status === "ENDED") {
    return "완료";
  }
  if (status === "CANCELED") {
    return "취소";
  }
  return "예정";
}

export function meetingStateStatus(state: ProjectMeeting["state"]): MeetingStatus | null {
  if (state === "예정") {
    return "SCHEDULED";
  }
  if (state === "진행 중") {
    return "IN_PROGRESS";
  }
  if (state === "완료" || state === "보고서 생성됨") {
    return "ENDED";
  }
  if (state === "취소") {
    return "CANCELED";
  }
  return null;
}

export function toProjectMeeting(meeting: MeetingSummary, index: number): ProjectMeeting {
  const scheduledAt = new Date(meeting.scheduledAt);
  const scheduledEndAt = new Date(meeting.scheduledEndAt);
  const durationMinutes = !Number.isNaN(scheduledAt.getTime()) && !Number.isNaN(scheduledEndAt.getTime())
    ? Math.max(1, Math.round((scheduledEndAt.getTime() - scheduledAt.getTime()) / 60_000))
    : 60;
  return {
    id: meeting.id,
    index: `#${String(index + 1).padStart(2, "0")}`,
    title: meeting.title,
    date: Number.isNaN(scheduledAt.getTime())
      ? "일정 미정"
      : `${String(scheduledAt.getMonth() + 1).padStart(2, "0")}.${String(scheduledAt.getDate()).padStart(2, "0")}`,
    state: meetingStatusLabel(meeting.status),
    scheduledAt: meeting.scheduledAt,
    scheduledEndAt: meeting.scheduledEndAt,
    description: meeting.description,
    durationMinutes,
    myRole: meeting.myRole
  };
}

export function buildTargetMeetingKey(spaceId: string, meetingId: string) {
  return `target:${spaceId}:${meetingId}`;
}

export function toMeetingParticipantState(
  meetingKey: string,
  participant: MeetingParticipantSummary
): MeetingParticipantState {
  return {
    id: participant.id,
    userId: participant.userId,
    meetingKey,
    name: participant.displayName?.trim() || participant.email?.trim() || "이름 미등록 사용자",
    email: participant.email?.trim() || "",
    role: participant.role,
    accessStatus: participant.accessStatus,
    participantType: participant.participantType
  };
}

export function mapSpaceMember(member: SpaceMemberSummary): TeamMember {
  const displayName = member.displayName?.trim() || member.email?.trim() || "이름 미등록 멤버";
  return {
    memberId: member.id,
    userId: member.userId,
    name: displayName,
    email: member.email?.trim() || `unknown-${member.userId}@meetingmind.local`,
    role: member.role === "OWNER" ? "Owner" : member.role === "ADMIN" ? "Admin" : "Member",
    spaceRole: member.role,
    since: formatDateLabel(member.joinedAt),
    access: getSpaceRoleAccessLabel(member.role),
    rank: member.role === "OWNER" ? "팀 리드" : member.role === "ADMIN" ? "관리자" : "팀원",
    status: "active"
  };
}

export function mapWorkspaceSpace(space: SpaceSummary, meetingCount: number, memberCount: number) {
  return {
    id: space.id,
    name: space.name,
    members: `멤버 ${memberCount}명`,
    meetings: `진행 회의 ${meetingCount}건`,
    updatedAt: formatDateLabel(space.updatedAt),
    description: space.description?.trim() || "프로젝트 설명이 아직 작성되지 않았습니다.",
    href: `/spaces/${encodeURIComponent(space.id)}`
  };
}

export function mapTaskCard(
  task: TaskCard,
  projectName: string,
  meetings: ProjectMeeting[],
  members: TeamMember[]
): ProjectTaskState {
  const meeting = meetings.find((candidate) => candidate.id === task.meetingId);
  const assignee = members.find((member) => member.userId === task.assigneeId);
  return {
    id: task.id,
    title: task.title,
    description: task.description ?? "상세 설명이 아직 작성되지 않았습니다.",
    status: task.status,
    priority: task.priority,
    labels: task.labels,
    assignee: assignee?.name ?? "미지정",
    dueDate: task.dueDate ?? "",
    meetingKey: meeting ? buildMeetingKey(projectName, meeting.index) : null,
    sourceCandidateId: task.sourceCandidateId
  };
}
