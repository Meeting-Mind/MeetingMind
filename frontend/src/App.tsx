import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import {
  addMeetingParticipant,
  createMeeting,
  createSpace,
  deleteMeeting,
  fetchMeetingDetail,
  fetchMeetingParticipants,
  fetchLegacyWorkspaceSnapshot,
  fetchMeetings,
  fetchSpaceMembers,
  fetchSpaces,
  updateMeeting,
  updateMeetingParticipant
} from "./api/workspace";
import { bootstrapAuthSession, logoutCurrentSession, type AuthSession } from "./auth/session";
import { subscribeToSessionInvalid } from "./auth/sessionInvalidation";
import { AuthSessionControls } from "./components/AuthSessionControls";
import { GoogleLoginModal } from "./components/GoogleLoginModal";
import { LandingPage } from "./pages/LandingPage";
import { LiveMeetingPage } from "./pages/LiveMeetingPage";
import { LiveRoomPage } from "./pages/LiveRoomPage";
import { MeetingAiPage } from "./pages/MeetingAiPage";
import { MeetingAccessPage } from "./pages/MeetingAccessPage";
import { ProjectOverviewPage } from "./pages/ProjectOverviewPage";
import { ReportAgentPage } from "./pages/ReportAgentPage";
import { TeamMembersPage } from "./pages/TeamMembersPage";
import { WorkspaceHomePage } from "./pages/WorkspaceHomePage";
import { mockData } from "./data/mockData";
import type {
  MeetingDetailResponse,
  MeetingParticipantSummary,
  MeetingStatus,
  MeetingSummary,
  SpaceMemberSummary,
  SpaceSummary,
  UpdateMeetingRequest,
  WorkspaceData
} from "./types";

type ProjectMeeting = WorkspaceData["projectOverview"]["meetings"][number];
type WorkspaceDataSource = "workspace-api" | "workspace-api-partial" | "legacy-api" | "mock-fallback";
type CreateMeetingPayload = {
  title?: string;
  scheduledAt?: string;
  participantEmails?: string[];
};
type UpdateProjectPayload = {
  name: string;
  description: string;
};
type MeetingParticipantState = {
  id: string;
  userId?: string;
  meetingKey: string;
  name: string;
  email: string;
  role: "HOST" | "EDITOR" | "VIEWER";
  accessStatus: "ACTIVE" | "REVOKED";
  participantType: "member" | "guest";
};
type ProjectTaskState = {
  id: string;
  title: string;
  description: string;
  status: "TODO" | "IN_PROGRESS" | "DONE";
  assignee: string;
  dueDate: string;
  meetingKey: string | null;
  sourceCandidateId: string | null;
};

const SESSION_EXPIRED_NOTICE = "로그인이 만료되었습니다. 다시 로그인해 주세요.";

function readSessionExpiredReturnTo(search: string): string | null {
  const params = new URLSearchParams(search);
  if (params.get("auth") !== "session-expired") {
    return null;
  }
  const returnTo = params.get("returnTo");
  if (!returnTo || !returnTo.startsWith("/") || returnTo.startsWith("//")) {
    return "/spaces";
  }
  return returnTo;
}
type TeamMember = {
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
type JoinRequest = {
  id: string;
  name: string;
  email: string;
  role: string;
  meetingIndex: string;
  meetingTitle: string;
  requestedAt: string;
  source: "링크" | "코드";
};
type InviteMeta = {
  link: string;
  code: string;
};
type MeetingInviteMeta = {
  meetingId: string;
  title: string;
  joinCode: string;
  joinUrl: string;
};

const initialProjectMeetings: Record<string, ProjectMeeting[]> = {
  "FinPilot Renewal": [
    { index: "#01", title: "킥오프 - 프로젝트 범위 정의", date: "06.02", state: "완료" },
    { index: "#02", title: "ERD 설계 리뷰", date: "06.09", state: "완료" },
    { index: "#03", title: "API 구조 논의", date: "06.16", state: "보고서 생성됨" },
    { index: "#04", title: "보안 점검 (비공개)", date: "06.23", state: "예정" },
    { index: "#05", title: "RAG 검색 품질 리뷰", date: "06.27", state: "완료" },
    { index: "#06", title: "권한 정책 검토", date: "07.01", state: "보고서 생성됨" },
    { index: "#07", title: "문서 저장 구조 정리", date: "07.04", state: "완료" },
    { index: "#08", title: "실시간 회의 플로우 최종 점검", date: "07.08", state: "예정" }
  ],
  "Campus Admin Assistant": [
    { index: "#01", title: "운영 자동화 킥오프", date: "06.05", state: "완료" },
    { index: "#02", title: "관리자 권한 구조 논의", date: "06.12", state: "보고서 생성됨" },
    { index: "#03", title: "감사 로그 저장 정책", date: "06.18", state: "완료" },
    { index: "#04", title: "배포 전 체크리스트 검토", date: "06.25", state: "예정" },
    { index: "#05", title: "운영 이슈 대응 흐름 정리", date: "07.02", state: "예정" }
  ]
};

const initialProjectMembers: Record<string, TeamMember[]> = {
  "FinPilot Renewal": [
    { name: "이미주", email: "miju@meetingmind.ai", role: "Product Manager", spaceRole: "OWNER", since: "2026.03 합류", access: "프로젝트 오너", rank: "팀 리드", status: "active" },
    { name: "김진수", email: "jinsu@meetingmind.ai", role: "Backend Lead", spaceRole: "ADMIN", since: "2026.02 합류", access: "프로젝트 관리자", rank: "Lead", status: "active" },
    { name: "박서윤", email: "seoyun@meetingmind.ai", role: "Product Designer", spaceRole: "MEMBER", since: "2026.04 합류", access: "회의 참여 / 문서 열람", rank: "Senior", status: "active" },
    { name: "최민호", email: "minho@meetingmind.ai", role: "Data Engineer", spaceRole: "MEMBER", since: "2026.01 합류", access: "기술 회의 참여", rank: "Senior", status: "away" }
  ],
  "Campus Admin Assistant": [
    { name: "정하늘", email: "haneul@meetingmind.ai", role: "Project Manager", spaceRole: "OWNER", since: "2026.02 합류", access: "프로젝트 오너", rank: "팀 리드", status: "active" },
    { name: "김도윤", email: "doyun@meetingmind.ai", role: "Frontend Developer", spaceRole: "ADMIN", since: "2026.03 합류", access: "프로젝트 관리자", rank: "Mid-level", status: "active" },
    { name: "이서진", email: "seojin@meetingmind.ai", role: "Backend Developer", spaceRole: "MEMBER", since: "2026.01 합류", access: "기술 회의 편집", rank: "Senior", status: "active" },
    { name: "박가은", email: "gaeun@meetingmind.ai", role: "QA Engineer", spaceRole: "MEMBER", since: "2026.04 합류", access: "문서 열람 / 회의 참여", rank: "Associate", status: "away" }
  ]
};

const initialProjectRequests: Record<string, JoinRequest[]> = {
  "FinPilot Renewal": [
    { id: "fin-wait-01", name: "서다은", email: "daeun@meetingmind.ai", role: "Frontend Developer", meetingIndex: "#08", meetingTitle: "실시간 회의 플로우 최종 점검", requestedAt: "방금 전", source: "링크" }
  ],
  "Campus Admin Assistant": [
    { id: "caa-wait-01", name: "윤민재", email: "minjae@meetingmind.ai", role: "Operations Manager", meetingIndex: "#04", meetingTitle: "배포 전 체크리스트 검토", requestedAt: "12분 전", source: "코드" }
  ]
};

const initialProjectInvites: Record<string, InviteMeta> = {
  "FinPilot Renewal": { link: "https://meetingmind.ai/invite/finpilot-renewal", code: "FIN-TEAM-0316" },
  "Campus Admin Assistant": { link: "https://meetingmind.ai/invite/campus-admin-assistant", code: "CAA-TEAM-0821" }
};

const initialProjectTasks: Record<string, ProjectTaskState[]> = {
  "FinPilot Renewal": [
    {
      id: "task-fin-001",
      title: "ERD 수정안 문서화",
      description: "3회차 결정사항 기준으로 외래키와 권한 관계를 정리합니다.",
      status: "TODO",
      assignee: "김진수",
      dueDate: "2026-07-12",
      meetingKey: "FinPilot Renewal:#03",
      sourceCandidateId: null
    },
    {
      id: "task-fin-002",
      title: "접근 제어 UI 설계",
      description: "회의 ACL role과 override 상태를 화면에 반영합니다.",
      status: "IN_PROGRESS",
      assignee: "박서윤",
      dueDate: "2026-07-15",
      meetingKey: "FinPilot Renewal:#03",
      sourceCandidateId: null
    }
  ],
  "Campus Admin Assistant": [
    {
      id: "task-caa-001",
      title: "운영 자동화 예외 케이스 정리",
      description: "관리자 권한별 승인 흐름과 실패 케이스를 문서화합니다.",
      status: "TODO",
      assignee: "정하늘",
      dueDate: "2026-07-18",
      meetingKey: "Campus Admin Assistant:#02",
      sourceCandidateId: null
    }
  ]
};

function buildInviteMeta(projectName: string): InviteMeta {
  const slug = projectName.toLowerCase().replace(/[^a-z0-9가-힣]+/g, "-").replace(/^-+|-+$/g, "");
  const codeSeed = (projectName.replace(/[^A-Za-z0-9가-힣]/g, "").slice(0, 3).toUpperCase() || "NEW").padEnd(3, "X");

  return {
    link: `https://meetingmind.ai/invite/${encodeURIComponent(slug || "new-project")}`,
    code: `${codeSeed}-TEAM-${String(projectName.length).padStart(4, "0")}`
  };
}

function buildSpaceId(projectName: string) {
  const slug = projectName
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9가-힣]+/g, "-")
    .replace(/^-+|-+$/g, "");

  return `space-${slug || "new-project"}`;
}

function buildMeetingId(projectName: string, count: number) {
  return `${buildSpaceId(projectName)}-meeting-${String(count).padStart(2, "0")}`;
}

function buildMeetingKey(projectName: string, meetingIndex: string) {
  return `${projectName}:${meetingIndex}`;
}

function getSpaceRoleAccessLabel(spaceRole: TeamMember["spaceRole"]) {
  if (spaceRole === "OWNER") {
    return "프로젝트 오너";
  }

  if (spaceRole === "ADMIN") {
    return "프로젝트 관리자";
  }

  return "회의 참여 / 문서 열람";
}

function inferMeetingTitle(projectName: string, description: string, index: number) {
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

function buildMeeting(projectName: string, description: string, count: number, payload?: CreateMeetingPayload): ProjectMeeting {
  const fallbackDate = new Date(2026, 5, 27 + (count - 1) * 7);
  const scheduledDate = payload?.scheduledAt ? new Date(payload.scheduledAt) : fallbackDate;

  return {
    id: buildMeetingId(projectName, count),
    index: `#${String(count).padStart(2, "0")}`,
    title: payload?.title?.trim() || inferMeetingTitle(projectName, description, count),
    date: `${String(scheduledDate.getMonth() + 1).padStart(2, "0")}.${String(scheduledDate.getDate()).padStart(2, "0")}`,
    state: "예정",
    scheduledAt: scheduledDate.toISOString(),
    durationMinutes: 60
  };
}

function formatDateLabel(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "업데이트 정보 없음";
  }
  return `${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")} 업데이트`;
}

function meetingStatusLabel(status: MeetingStatus): ProjectMeeting["state"] {
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

function meetingStateStatus(state: ProjectMeeting["state"]): MeetingStatus | null {
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

function toProjectMeeting(meeting: MeetingSummary, index: number): ProjectMeeting {
  const scheduledAt = new Date(meeting.scheduledAt);
  return {
    id: meeting.id,
    index: `#${String(index + 1).padStart(2, "0")}`,
    title: meeting.title,
    date: Number.isNaN(scheduledAt.getTime())
      ? "일정 미정"
      : `${String(scheduledAt.getMonth() + 1).padStart(2, "0")}.${String(scheduledAt.getDate()).padStart(2, "0")}`,
    state: meetingStatusLabel(meeting.status),
    scheduledAt: meeting.scheduledAt,
    durationMinutes: 60,
    myRole: meeting.myRole
  };
}

function buildTargetMeetingKey(spaceId: string, meetingId: string) {
  return `target:${spaceId}:${meetingId}`;
}

function toMeetingParticipantState(
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

function mapSpaceMember(member: SpaceMemberSummary): TeamMember {
  const displayName = member.displayName?.trim() || member.email?.trim() || "이름 미등록 멤버";
  return {
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

function mapWorkspaceSpace(space: SpaceSummary, meetingCount: number, memberCount: number) {
  return {
    id: space.id,
    name: space.name,
    members: `멤버 ${memberCount}명`,
    meetings: `진행 회의 ${meetingCount}건`,
    updatedAt: formatDateLabel(space.updatedAt),
    description: space.description?.trim() || "프로젝트 설명이 아직 작성되지 않았습니다.",
    href: "/project-overview"
  };
}

function ProtectedRoute({
  children,
  loading,
  onRequestLogin,
  session
}: {
  children: ReactNode;
  loading: boolean;
  onRequestLogin: () => void;
  session: AuthSession | null;
}) {
  const location = useLocation();

  useEffect(() => {
    if (!loading && !session) {
      onRequestLogin();
    }
  }, [loading, location.pathname, location.search, onRequestLogin, session]);

  if (loading) {
    return null;
  }

  if (!session) {
    return <Navigate replace state={{ requestedPath: `${location.pathname}${location.search}` }} to="/" />;
  }

  return <>{children}</>;
}

export function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const sessionExpiredReturnTo = readSessionExpiredReturnTo(location.search);
  const [authSession, setAuthSession] = useState<AuthSession | null>(null);
  const [authBootstrapLoading, setAuthBootstrapLoading] = useState(true);
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [data, setData] = useState<WorkspaceData>(mockData);
  const [workspaceDataSource, setWorkspaceDataSource] = useState<WorkspaceDataSource>("mock-fallback");
  const [projectMeetings, setProjectMeetings] = useState<Record<string, ProjectMeeting[]>>(initialProjectMeetings);
  const [projectMembers, setProjectMembers] = useState<Record<string, TeamMember[]>>(initialProjectMembers);
  const [projectRequests, setProjectRequests] = useState<Record<string, JoinRequest[]>>(initialProjectRequests);
  const [projectInvites, setProjectInvites] = useState<Record<string, InviteMeta>>(initialProjectInvites);
  const [latestMeetingInvites, setLatestMeetingInvites] = useState<Record<string, MeetingInviteMeta>>({});
  const [meetingParticipants, setMeetingParticipants] = useState<Record<string, MeetingParticipantState[]>>({});
  const [projectTasks, setProjectTasks] = useState<Record<string, ProjectTaskState[]>>(initialProjectTasks);
  const [projectAiSpaceIds, setProjectAiSpaceIds] = useState<string[]>([]);
  const [meetingMutationError, setMeetingMutationError] = useState("");
  const [meetingMutationLoading, setMeetingMutationLoading] = useState(false);
  const [meetingReadLoading, setMeetingReadLoading] = useState(false);
  const openAuthModal = useCallback(() => setAuthModalOpen(true), []);

  useEffect(() => {
    let active = true;
    void bootstrapAuthSession()
      .then((session) => {
        if (active) {
          setAuthSession(session);
        }
      })
      .catch(() => {
        if (active) {
          setAuthSession(null);
        }
      })
      .finally(() => {
        if (active) {
          setAuthBootstrapLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let redirecting = false;
    return subscribeToSessionInvalid(() => {
      if (redirecting) {
        return;
      }
      redirecting = true;
      const currentPath = `${window.location.pathname}${window.location.search}`;
      const returnTo = currentPath.startsWith("/") && !currentPath.startsWith("//") && currentPath !== "/"
        ? currentPath
        : "/spaces";
      const params = new URLSearchParams({ auth: "session-expired", returnTo });
      window.location.replace(`/?${params.toString()}`);
    });
  }, []);

  useEffect(() => {
    if (!authBootstrapLoading && !authSession && sessionExpiredReturnTo) {
      setAuthModalOpen(true);
    }
  }, [authBootstrapLoading, authSession, sessionExpiredReturnTo]);

  const refreshTargetMeetings = useCallback(async (session: AuthSession, spaceId: string, projectName: string) => {
    const response = await fetchMeetings(session, spaceId);
    const detailResults = await Promise.allSettled(
      response.meetings.map(async (meeting) => {
        const [detail, participantsResponse] = await Promise.all([
          fetchMeetingDetail(session, meeting.id),
          fetchMeetingParticipants(session, meeting.id)
        ]);
        return { detail, participants: participantsResponse.participants };
      })
    );
    const details = new Map<string, MeetingDetailResponse>();
    const participantsByMeetingId = new Map<string, MeetingParticipantSummary[]>();
    detailResults.forEach((result) => {
      if (result.status === "fulfilled") {
        details.set(result.value.detail.id, result.value.detail);
        participantsByMeetingId.set(result.value.detail.id, result.value.participants);
      }
    });
    const meetings = response.meetings.map((meeting, index) =>
      toProjectMeeting(details.get(meeting.id) ?? meeting, index)
    );
    setProjectMeetings((previous) => ({ ...previous, [projectName]: meetings }));
    setMeetingParticipants((previous) => {
      const targetPrefix = `target:${spaceId}:`;
      const next = Object.fromEntries(
        Object.entries(previous).filter(([key]) => !key.startsWith(targetPrefix))
      );
      details.forEach((detail) => {
        const meetingKey = buildTargetMeetingKey(spaceId, detail.id);
        next[meetingKey] = (participantsByMeetingId.get(detail.id) ?? []).map((participant) =>
          toMeetingParticipantState(meetingKey, participant)
        );
      });
      return next;
    });
    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.map((space) =>
          space.id === spaceId ? { ...space, meetings: `진행 회의 ${meetings.length}건` } : space
        )
      }
    }));
    return detailResults.every((result) => result.status === "fulfilled");
  }, []);

  function handleAuthSuccess(session: AuthSession) {
    setAuthSession(session);
    setLatestMeetingInvites({});
    setAuthModalOpen(false);

    const requestedPath =
      (location.state as { requestedPath?: string } | null)?.requestedPath ?? sessionExpiredReturnTo;
    if (requestedPath && requestedPath !== "/") {
      navigate(requestedPath, { replace: true });
    }
  }

  function handleAuthModalClose() {
    setAuthModalOpen(false);
    if (sessionExpiredReturnTo) {
      navigate("/", { replace: true, state: null });
    }
  }

  async function handleLogout() {
    await logoutCurrentSession();
    setAuthSession(null);
    setAuthModalOpen(false);
    setData(mockData);
    setWorkspaceDataSource("mock-fallback");
    setProjectMeetings(initialProjectMeetings);
    setProjectMembers(initialProjectMembers);
    setProjectRequests(initialProjectRequests);
    setProjectInvites(initialProjectInvites);
    setLatestMeetingInvites({});
    setMeetingParticipants({});
    setProjectTasks(initialProjectTasks);
    setProjectAiSpaceIds([]);
    setMeetingMutationError("");
    setMeetingMutationLoading(false);
    setMeetingReadLoading(false);
    window.location.replace("/");
  }

  async function handleCreateProject({ name, description }: { name: string; description: string }) {
    const normalizedName = name.trim();
    if (!normalizedName) {
      return;
    }
    if (!authSession) {
      throw new Error("로그인이 필요합니다.");
    }
    if (data.workspaceHome.spaces.some((space) => space.name.trim().toLocaleLowerCase() === normalizedName.toLocaleLowerCase())) {
      throw new Error("같은 이름의 프로젝트가 이미 있습니다.");
    }

    const created = await createSpace(authSession, {
      name: normalizedName,
      description: description.trim() || null
    });
    const spaceId = created.id;

    const owner = authSession?.user;
    const seededMembers: TeamMember[] = owner
      ? [
          {
            userId: owner.id,
            name: owner.displayName,
            email: owner.email,
            role: "Owner",
            spaceRole: "OWNER",
            since: "방금 합류",
            access: getSpaceRoleAccessLabel("OWNER"),
            rank: "팀 리드",
            status: "active"
          }
        ]
      : [];

    setProjectMeetings((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectMembers((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? seededMembers }));
    setProjectRequests((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectInvites((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? buildInviteMeta(normalizedName) }));
    setProjectTasks((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectAiSpaceIds((previous) => (previous.includes(spaceId) ? previous : [...previous, spaceId]));

    setData((previous) => {
      const nextSpace = {
        id: spaceId,
        name: created.name,
        members: seededMembers.length ? "멤버 1명" : "멤버 0명",
        meetings: "진행 회의 0건",
        updatedAt: "방금 업데이트",
        description: created.description?.trim() || "새 프로젝트 설명이 아직 작성되지 않았습니다.",
        href: "/project-overview"
      };

      return {
        ...previous,
        workspaceHome: {
          ...previous.workspaceHome,
          spaces: [nextSpace, ...previous.workspaceHome.spaces],
          recent: [{ title: `${normalizedName} · 프로젝트 생성`, meta: "방금 전" }, ...previous.workspaceHome.recent].slice(0, 6)
        }
      };
    });
  }

  function handleUpdateProject(spaceId: string, { name, description }: UpdateProjectPayload) {
    const normalizedName = name.trim();
    if (!normalizedName) {
      return;
    }

    const currentSpace = data.workspaceHome.spaces.find((space) => space.id === spaceId);
    if (!currentSpace) {
      return;
    }

    const previousName = currentSpace.name;

    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.map((space) =>
          space.id === spaceId
            ? {
                ...space,
                name: normalizedName,
                description: description.trim(),
                updatedAt: "방금 업데이트"
              }
            : space
        ),
        recent: [{ title: `${normalizedName} · 프로젝트 정보 수정`, meta: "방금 전" }, ...previous.workspaceHome.recent].slice(0, 6)
      }
    }));

    if (previousName !== normalizedName) {
      setProjectMeetings((previous) => {
        const next = { ...previous };
        next[normalizedName] = next[previousName] ?? [];
        delete next[previousName];
        return next;
      });
      setProjectMembers((previous) => {
        const next = { ...previous };
        next[normalizedName] = next[previousName] ?? [];
        delete next[previousName];
        return next;
      });
      setProjectRequests((previous) => {
        const next = { ...previous };
        next[normalizedName] = next[previousName] ?? [];
        delete next[previousName];
        return next;
      });
      setProjectInvites((previous) => {
        const next = { ...previous };
        next[normalizedName] = next[previousName] ?? buildInviteMeta(normalizedName);
        delete next[previousName];
        return next;
      });
      setProjectTasks((previous) => {
        const next = { ...previous };
        next[normalizedName] = (next[previousName] ?? []).map((task) => ({
          ...task,
          meetingKey: task.meetingKey?.replace(`${previousName}:`, `${normalizedName}:`) ?? null
        }));
        delete next[previousName];
        return next;
      });
      setMeetingParticipants((previous) => {
        const next: Record<string, MeetingParticipantState[]> = {};
        Object.entries(previous).forEach(([meetingKey, participants]) => {
          const targetKey = meetingKey.startsWith(`${previousName}:`)
            ? meetingKey.replace(`${previousName}:`, `${normalizedName}:`)
            : meetingKey;
          next[targetKey] = participants.map((participant) => ({
            ...participant,
            meetingKey: participant.meetingKey.startsWith(`${previousName}:`)
              ? participant.meetingKey.replace(`${previousName}:`, `${normalizedName}:`)
              : participant.meetingKey
          }));
        });
        return next;
      });
    }
  }

  function handleDeleteProject(spaceId: string) {
    const currentSpace = data.workspaceHome.spaces.find((space) => space.id === spaceId);
    if (!currentSpace) {
      return;
    }

    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.filter((space) => space.id !== spaceId),
        recent: [{ title: `${currentSpace.name} · 프로젝트 삭제`, meta: "방금 전" }, ...previous.workspaceHome.recent].slice(0, 6)
      }
    }));

    setProjectMeetings((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setProjectMembers((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setProjectRequests((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setProjectInvites((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setLatestMeetingInvites((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setProjectTasks((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setMeetingParticipants((previous) => {
      const next: Record<string, MeetingParticipantState[]> = {};
      Object.entries(previous).forEach(([meetingKey, participants]) => {
        if (
          !meetingKey.startsWith(`${currentSpace.name}:`) &&
          !meetingKey.startsWith(`target:${currentSpace.id}:`)
        ) {
          next[meetingKey] = participants;
        }
      });
      return next;
    });

    navigate("/spaces");
  }

  async function handleCreateMeeting(projectName: string, payload?: CreateMeetingPayload): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (!targetSpace) {
      return false;
    }

    const usesTargetApi = projectAiSpaceIds.includes(targetSpace.id);
    if (usesTargetApi) {
      if (!authSession || !payload?.title || !payload.scheduledAt) {
        setMeetingMutationError("로그인과 회의 제목, 예정 일시가 필요합니다.");
        return false;
      }
      const meetingTitle = payload.title;
      const scheduledAt = payload.scheduledAt;
      const selectedMembers = (projectMembers[projectName] ?? []).filter((member) =>
        payload.participantEmails?.includes(member.email)
      );
      if (payload.participantEmails?.some((email) => !selectedMembers.some((member) => member.email === email && member.userId))) {
        setMeetingMutationError("선택한 참여자의 Backend 사용자 정보를 찾을 수 없습니다.");
        return false;
      }
      const participantUserIds = selectedMembers
        .map((member) => member.userId)
        .filter((userId): userId is string => Boolean(userId) && userId !== authSession.user.id);
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        const created = await createMeeting(authSession, targetSpace.id, {
          title: meetingTitle,
          scheduledAt,
          participantUserIds
        });
        setLatestMeetingInvites((previous) => ({
          ...previous,
          [projectName]: {
            meetingId: created.id,
            title: meetingTitle,
            joinCode: created.joinCode,
            joinUrl: created.joinUrl
          }
        }));
        const detailComplete = await refreshTargetMeetings(authSession, targetSpace.id, projectName);
        if (!detailComplete) {
          setMeetingMutationError("회의는 생성됐지만 일부 상세 정보를 다시 불러오지 못했습니다.");
        }
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "회의를 생성하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    const existingMeetings = projectMeetings[projectName] ?? [];
    const nextMeeting = buildMeeting(projectName, targetSpace.description, existingMeetings.length + 1, payload);

    setProjectMeetings((previous) => {
      return {
        ...previous,
        [projectName]: [...(previous[projectName] ?? []), nextMeeting]
      };
    });

    if (payload?.participantEmails?.length) {
      const meetingKey = buildMeetingKey(projectName, nextMeeting.index);
      const selectedMembers = (projectMembers[projectName] ?? []).filter((member) =>
        payload.participantEmails?.includes(member.email)
      );

      setMeetingParticipants((previous) => ({
        ...previous,
        [meetingKey]: selectedMembers.map((member, index) => ({
          id: `${meetingKey}-${member.email}`,
          meetingKey,
          name: member.name,
          email: member.email,
          role: index === 0 ? "HOST" : "VIEWER",
          accessStatus: "ACTIVE",
          participantType: "member"
        }))
      }));
    }

    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.map((space) =>
          space.name === projectName
            ? {
                ...space,
                meetings: `진행 회의 ${Number(space.meetings.match(/\d+/)?.[0] ?? 0) + 1}건`,
                updatedAt: "방금 업데이트"
              }
            : space
        ),
        recent: [{ title: `${projectName} · 새 회의 생성`, meta: "방금 전" }, ...previous.workspaceHome.recent].slice(0, 6)
      }
    }));
    return true;
  }

  async function handleDeleteMeeting(projectName: string, meetingIndex: string): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (!targetSpace) {
      return false;
    }

    const meeting = (projectMeetings[projectName] ?? []).find((item) => item.index === meetingIndex);
    if (projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !meeting?.id) {
        setMeetingMutationError("삭제할 Backend 회의 정보를 찾을 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await deleteMeeting(authSession, meeting.id);
        await refreshTargetMeetings(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "회의를 삭제하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    const meetingKey = buildMeetingKey(projectName, meetingIndex);
    setProjectMeetings((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).filter((meeting) => meeting.index !== meetingIndex)
    }));
    setMeetingParticipants((previous) => {
      const next = { ...previous };
      delete next[meetingKey];
      return next;
    });
    setProjectTasks((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).map((task) =>
        task.meetingKey === meetingKey ? { ...task, meetingKey: null } : task
      )
    }));
    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.map((space) =>
          space.name === projectName
            ? {
                ...space,
                meetings: `진행 회의 ${Math.max(Number(space.meetings.match(/\d+/)?.[0] ?? 1) - 1, 0)}건`,
                updatedAt: "방금 업데이트"
              }
            : space
        ),
        recent: [{ title: `${targetSpace.name} · 회의 삭제`, meta: "방금 전" }, ...previous.workspaceHome.recent].slice(0, 6)
      }
    }));
    return true;
  }

  async function handleUpdateMeeting(
    projectName: string,
    meetingIndex: string,
    updates: UpdateMeetingRequest
  ): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    const meeting = (projectMeetings[projectName] ?? []).find((item) => item.index === meetingIndex);
    if (!targetSpace || !meeting) {
      return false;
    }

    if (projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !meeting.id) {
        setMeetingMutationError("수정할 Backend 회의 정보를 찾을 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await updateMeeting(authSession, meeting.id, updates);
        await refreshTargetMeetings(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "회의를 수정하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    setProjectMeetings((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).map((current) => {
        if (current.index !== meetingIndex) {
          return current;
        }
        const scheduledAt = updates.scheduledAt ?? current.scheduledAt;
        const date = scheduledAt ? new Date(scheduledAt) : null;
        return {
          ...current,
          title: updates.title ?? current.title,
          scheduledAt,
          date: date
            ? `${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`
            : current.date,
          state: updates.status ? meetingStatusLabel(updates.status) : current.state
        };
      })
    }));
    return true;
  }

  async function handleUpdateMeetingStatus(
    projectName: string,
    meetingIndex: string,
    state: ProjectMeeting["state"]
  ): Promise<boolean> {
    const status = meetingStateStatus(state);
    if (!status) {
      setMeetingMutationError("지원하지 않는 회의 상태입니다.");
      return false;
    }
    return handleUpdateMeeting(projectName, meetingIndex, { status });
  }

  async function handleUpdateMeetingDetails(
    projectName: string,
    meetingIndex: string,
    updates: Pick<UpdateMeetingRequest, "title" | "scheduledAt">
  ): Promise<boolean> {
    return handleUpdateMeeting(projectName, meetingIndex, updates);
  }

  async function handleAddMeetingParticipant(
    projectName: string,
    meetingIndex: string,
    participant: Pick<MeetingParticipantState, "email" | "name" | "role" | "participantType" | "userId">
  ): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    const meeting = (projectMeetings[projectName] ?? []).find((item) => item.index === meetingIndex);
    if (targetSpace && projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !meeting?.id || !participant.userId) {
        setMeetingMutationError("추가할 참여자의 Backend 사용자 정보를 찾을 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await addMeetingParticipant(authSession, meeting.id, {
          userId: participant.userId,
          role: participant.role,
          participantType: participant.participantType
        });
        await refreshTargetMeetings(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "회의 참여자를 추가하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    const meetingKey = buildMeetingKey(projectName, meetingIndex);
    setMeetingParticipants((previous) => {
      const currentParticipants = previous[meetingKey] ?? [];
      const existingParticipant = currentParticipants.find((item) => item.email === participant.email);
      const activeHostCount = currentParticipants.filter(
        (item) => item.role === "HOST" && item.accessStatus === "ACTIVE"
      ).length;
      const keepsLastHost =
        existingParticipant?.role === "HOST" &&
        existingParticipant.accessStatus === "ACTIVE" &&
        activeHostCount === 1 &&
        participant.role !== "HOST";
      const nextParticipant: MeetingParticipantState = {
        id: existingParticipant?.id ?? `${meetingKey}-${participant.email}`,
        meetingKey,
        name: participant.name,
        email: participant.email,
        role: keepsLastHost ? "HOST" : participant.role,
        accessStatus: "ACTIVE",
        participantType: participant.participantType
      };

      return {
        ...previous,
        [meetingKey]: existingParticipant
          ? currentParticipants.map((item) => (item.email === participant.email ? nextParticipant : item))
          : [...currentParticipants, nextParticipant]
      };
    });
    return true;
  }

  async function handleUpdateMeetingParticipant(
    projectName: string,
    meetingIndex: string,
    participantId: string,
    updates: Pick<MeetingParticipantState, "accessStatus" | "role">
  ): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    const meeting = (projectMeetings[projectName] ?? []).find((item) => item.index === meetingIndex);
    if (targetSpace && projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !meeting?.id) {
        setMeetingMutationError("수정할 회의 참여자 정보를 찾을 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await updateMeetingParticipant(authSession, meeting.id, participantId, updates);
        await refreshTargetMeetings(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "회의 참여자 권한을 변경하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    const meetingKey = buildMeetingKey(projectName, meetingIndex);
    setMeetingParticipants((previous) => {
      const currentParticipants = previous[meetingKey] ?? [];
      const activeHostCount = currentParticipants.filter(
        (participant) => participant.role === "HOST" && participant.accessStatus === "ACTIVE"
      ).length;

      return {
        ...previous,
        [meetingKey]: currentParticipants.map((participant) => {
          if (participant.id !== participantId) {
            return participant;
          }

          const wouldRemoveLastHost =
            participant.role === "HOST" &&
            participant.accessStatus === "ACTIVE" &&
            activeHostCount === 1 &&
            (updates.role !== "HOST" || updates.accessStatus !== "ACTIVE");

          return wouldRemoveLastHost ? participant : { ...participant, ...updates };
        })
      };
    });
    return true;
  }

  function handleCreateProjectTask(
    projectName: string,
    task: Omit<ProjectTaskState, "id" | "sourceCandidateId">
  ) {
    setProjectTasks((previous) => {
      const currentTasks = previous[projectName] ?? [];
      return {
        ...previous,
        [projectName]: [
          ...currentTasks,
          {
            ...task,
            id: `task-${buildSpaceId(projectName)}-${String(currentTasks.length + 1).padStart(3, "0")}`,
            sourceCandidateId: null
          }
        ]
      };
    });
  }

  function handleMoveProjectTask(projectName: string, taskId: string, status: ProjectTaskState["status"]) {
    setProjectTasks((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).map((task) => (task.id === taskId ? { ...task, status } : task))
    }));
  }

  function handleUpdateProjectTask(
    projectName: string,
    taskId: string,
    updates: Pick<ProjectTaskState, "assignee" | "description" | "dueDate" | "status" | "title">
  ) {
    setProjectTasks((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).map((task) =>
        task.id === taskId ? { ...task, ...updates } : task
      )
    }));
  }

  function handleDeleteProjectTask(projectName: string, taskId: string) {
    setProjectTasks((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).filter((task) => task.id !== taskId)
    }));
  }

  function handleApproveJoinRequest(projectName: string, requestId: string) {
    const request = projectRequests[projectName]?.find((item) => item.id === requestId);
    if (!request) {
      return;
    }

    setProjectRequests((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).filter((item) => item.id !== requestId)
    }));

    const meetingKey = buildMeetingKey(projectName, request.meetingIndex);
    setMeetingParticipants((previous) => ({
      ...previous,
      [meetingKey]: [
        ...(previous[meetingKey] ?? []).filter((participant) => participant.email !== request.email),
        {
          id: `${meetingKey}-${request.email}`,
          meetingKey,
          name: request.name,
          email: request.email,
          role: "VIEWER",
          accessStatus: "ACTIVE",
          participantType: "guest"
        }
      ]
    }));
  }

  function handleRejectJoinRequest(projectName: string, requestId: string) {
    setProjectRequests((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).filter((item) => item.id !== requestId)
    }));
  }

  function handleUpdateSpaceMemberRole(
    projectName: string,
    memberEmail: string,
    spaceRole: Exclude<TeamMember["spaceRole"], "OWNER">
  ) {
    setProjectMembers((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).map((member) =>
        member.email === memberEmail && member.spaceRole !== "OWNER"
          ? { ...member, spaceRole, access: getSpaceRoleAccessLabel(spaceRole) }
          : member
      )
    }));
  }

  function handleRemoveSpaceMember(projectName: string, memberEmail: string) {
    const member = projectMembers[projectName]?.find((item) => item.email === memberEmail);
    if (!member || member.spaceRole === "OWNER") {
      return;
    }

    setProjectMembers((previous) => {
      const nextMembers = (previous[projectName] ?? []).filter((item) => item.email !== memberEmail);

      setData((current) => ({
        ...current,
        workspaceHome: {
          ...current.workspaceHome,
          spaces: current.workspaceHome.spaces.map((space) =>
            space.name === projectName ? { ...space, members: `멤버 ${nextMembers.length}명`, updatedAt: "방금 업데이트" } : space
          )
        }
      }));

      return {
        ...previous,
        [projectName]: nextMembers
      };
    });

    setMeetingParticipants((previous) => {
      const next: Record<string, MeetingParticipantState[]> = {};
      Object.entries(previous).forEach(([meetingKey, participants]) => {
        next[meetingKey] = meetingKey.startsWith(`${projectName}:`)
          ? participants.map((participant) =>
              participant.email === memberEmail ? { ...participant, participantType: "guest" } : participant
            )
          : participants;
      });
      return next;
    });
  }

  function handleTransferProjectOwner(
    projectName: string,
    targetMemberEmail: string,
    previousOwnerRole: Exclude<TeamMember["spaceRole"], "OWNER">,
    confirmation: string
  ) {
    if (confirmation !== "TRANSFER OWNER") {
      return;
    }

    setProjectMembers((previous) => {
      const currentMembers = previous[projectName] ?? [];
      const currentOwner = currentMembers.find((member) => member.spaceRole === "OWNER");
      const targetMember = currentMembers.find((member) => member.email === targetMemberEmail);

      if (!currentOwner || !targetMember || targetMember.status !== "active" || targetMember.spaceRole === "OWNER") {
        return previous;
      }

      return {
        ...previous,
        [projectName]: currentMembers.map((member) => {
          if (member.email === targetMember.email) {
            return { ...member, spaceRole: "OWNER", access: getSpaceRoleAccessLabel("OWNER") };
          }

          if (member.email === currentOwner.email) {
            return { ...member, spaceRole: previousOwnerRole, access: getSpaceRoleAccessLabel(previousOwnerRole) };
          }

          return member;
        })
      };
    });
  }

  useEffect(() => {
    if (!authSession) {
      setProjectAiSpaceIds([]);
      setMeetingReadLoading(false);
      return;
    }

    const session = authSession;
    let active = true;

    async function loadWorkspace() {
      const [legacyResult, spacesResult] = await Promise.allSettled([
        fetchLegacyWorkspaceSnapshot(session),
        fetchSpaces(session)
      ]);
      if (!active) {
        return;
      }

      const legacyData = legacyResult.status === "fulfilled" ? legacyResult.value : null;
      const baseData: WorkspaceData = {
        workspaceHome: legacyData?.workspaceHome ?? mockData.workspaceHome,
        liveMeeting: legacyData?.liveMeeting ?? mockData.liveMeeting,
        meetingAi: legacyData?.meetingAi ?? mockData.meetingAi,
        projectOverview: legacyData?.projectOverview ?? mockData.projectOverview,
        reportAgent: legacyData?.reportAgent ?? mockData.reportAgent
      };

      if (spacesResult.status === "rejected") {
        setData(baseData);
        setProjectAiSpaceIds([]);
        setWorkspaceDataSource(legacyData ? "legacy-api" : "mock-fallback");
        return;
      }

      const resources = await Promise.all(
        spacesResult.value.spaces.map(async (space) => {
          const [meetingsResult, membersResult] = await Promise.allSettled([
            fetchMeetings(session, space.id),
            fetchSpaceMembers(session, space.id)
          ]);
          return { space, meetingsResult, membersResult };
        })
      );
      if (!active) {
        return;
      }

      const hasPartialFailure = resources.some(
        ({ meetingsResult, membersResult }) => meetingsResult.status === "rejected" || membersResult.status === "rejected"
      );
      if (resources.some(({ membersResult }) => membersResult.status === "rejected")) {
        setMeetingMutationError("일부 프로젝트의 멤버 목록을 불러오지 못했습니다. 참여자 지정 기능이 제한될 수 있습니다.");
      }
      setData({
        ...baseData,
        workspaceHome: {
          ...baseData.workspaceHome,
          spaces: resources.map(({ space, meetingsResult, membersResult }) =>
            mapWorkspaceSpace(
              space,
              meetingsResult.status === "fulfilled" ? meetingsResult.value.meetings.length : space.meetingCount,
              membersResult.status === "fulfilled" ? membersResult.value.members.length : 1
            )
          )
        }
      });
      setProjectMeetings((previous) => {
        const next = { ...previous };
        resources.forEach(({ space, meetingsResult }) => {
          next[space.name] =
            meetingsResult.status === "fulfilled" ? meetingsResult.value.meetings.map(toProjectMeeting) : [];
        });
        return next;
      });
      setProjectMembers((previous) => {
        const next = { ...previous };
        resources.forEach(({ space, membersResult }) => {
          next[space.name] =
            membersResult.status === "fulfilled"
              ? membersResult.value.members.map(mapSpaceMember)
              : [
                  {
                    userId: session.user.id,
                    name: session.user.displayName,
                    email: session.user.email,
                    role: space.role === "OWNER" ? "Owner" : space.role === "ADMIN" ? "Admin" : "Member",
                    spaceRole: space.role,
                    since: "이미 합류",
                    access: getSpaceRoleAccessLabel(space.role),
                    rank: space.role === "OWNER" ? "팀 리드" : space.role === "ADMIN" ? "관리자" : "팀원",
                    status: "active"
                  }
                ];
        });
        return next;
      });
      setProjectAiSpaceIds(spacesResult.value.spaces.map((space) => space.id));
      setMeetingReadLoading(true);
      const detailResults = await Promise.allSettled(
        resources
          .filter(({ meetingsResult }) => meetingsResult.status === "fulfilled")
          .map(({ space }) => refreshTargetMeetings(session, space.id, space.name))
      );
      if (!active) {
        return;
      }
      const hasDetailFailure = detailResults.some(
        (result) => result.status === "rejected" || (result.status === "fulfilled" && !result.value)
      );
      setMeetingReadLoading(false);
      if (hasDetailFailure) {
        setMeetingMutationError("일부 회의 상세 정보를 불러오지 못했습니다. 회의를 다시 선택해 주세요.");
      }
      setWorkspaceDataSource(hasPartialFailure || hasDetailFailure ? "workspace-api-partial" : "workspace-api");
    }

    void loadWorkspace();

    return () => {
      active = false;
    };
  }, [authSession, refreshTargetMeetings]);

  return (
    <>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route
          path="/spaces"
          element={
            <ProtectedRoute loading={authBootstrapLoading} onRequestLogin={openAuthModal} session={authSession}>
              <WorkspaceHomePage
                actionItems={data.meetingAi.actions}
                currentUserEmail={authSession?.user.email ?? ""}
                data={data.workspaceHome}
                dataSource={workspaceDataSource}
                meetingMutationError={meetingMutationError}
                meetingMutationLoading={meetingMutationLoading || meetingReadLoading}
                latestMeetingInvites={latestMeetingInvites}
                onCreateMeeting={handleCreateMeeting}
                onCreateProject={handleCreateProject}
                projectMembers={projectMembers}
                projectMeetings={projectMeetings}
              />
            </ProtectedRoute>
          }
        />
        <Route
          path="/meeting-access"
          element={
            <ProtectedRoute loading={authBootstrapLoading} onRequestLogin={openAuthModal} session={authSession}>
              {authSession ? <MeetingAccessPage session={authSession} /> : null}
            </ProtectedRoute>
          }
        />
        <Route
          path="/live-meeting"
          element={
            <ProtectedRoute loading={authBootstrapLoading} onRequestLogin={openAuthModal} session={authSession}>
              {authSession ? <LiveMeetingPage data={data.liveMeeting} session={authSession} /> : null}
            </ProtectedRoute>
          }
        />
        <Route
          path="/live-room"
          element={
            <ProtectedRoute loading={authBootstrapLoading} onRequestLogin={openAuthModal} session={authSession}>
              {authSession ? <LiveRoomPage liveMeeting={data.liveMeeting} meetingAi={data.meetingAi} session={authSession} /> : null}
            </ProtectedRoute>
          }
        />
        <Route
          path="/project-overview"
          element={
            <ProtectedRoute loading={authBootstrapLoading} onRequestLogin={openAuthModal} session={authSession}>
              <ProjectOverviewPage
                currentUserId={authSession?.user.id ?? ""}
                currentUserEmail={authSession?.user.email ?? ""}
                data={data.projectOverview}
                session={authSession}
                projectAiSpaceIds={projectAiSpaceIds}
                meetingMutationError={meetingMutationError}
                meetingMutationLoading={meetingMutationLoading}
                meetingReadLoading={meetingReadLoading}
                latestMeetingInvites={latestMeetingInvites}
                onDeleteProject={handleDeleteProject}
                onCreateMeeting={handleCreateMeeting}
                onCreateProject={handleCreateProject}
                onUpdateProject={handleUpdateProject}
                onAddMeetingParticipant={handleAddMeetingParticipant}
                onCreateProjectTask={handleCreateProjectTask}
                projectMeetings={projectMeetings}
                projectMembers={projectMembers}
                projectTasks={projectTasks}
                meetingParticipants={meetingParticipants}
                onDeleteMeeting={handleDeleteMeeting}
                onDeleteProjectTask={handleDeleteProjectTask}
                onMoveProjectTask={handleMoveProjectTask}
                onUpdateProjectTask={handleUpdateProjectTask}
                onUpdateMeetingParticipant={handleUpdateMeetingParticipant}
                onUpdateMeeting={handleUpdateMeetingDetails}
                onUpdateMeetingStatus={handleUpdateMeetingStatus}
                spaces={data.workspaceHome.spaces}
              />
            </ProtectedRoute>
          }
        />
        <Route
          path="/team-members"
          element={
            <ProtectedRoute loading={authBootstrapLoading} onRequestLogin={openAuthModal} session={authSession}>
              <TeamMembersPage
                inviteMeta={projectInvites}
                onApproveRequest={handleApproveJoinRequest}
                onCreateProject={handleCreateProject}
                onRemoveMember={handleRemoveSpaceMember}
                onRejectRequest={handleRejectJoinRequest}
                onTransferOwner={handleTransferProjectOwner}
                onUpdateMemberRole={handleUpdateSpaceMemberRole}
                pendingRequests={projectRequests}
                projectMembers={projectMembers}
                spaces={data.workspaceHome.spaces}
              />
            </ProtectedRoute>
          }
        />
        <Route
          path="/meeting-ai"
          element={
            <ProtectedRoute loading={authBootstrapLoading} onRequestLogin={openAuthModal} session={authSession}>
              <MeetingAiPage data={data.meetingAi} session={authSession} />
            </ProtectedRoute>
          }
        />
        <Route
          path="/report-agent"
          element={
            <ProtectedRoute loading={authBootstrapLoading} onRequestLogin={openAuthModal} session={authSession}>
              <ReportAgentPage data={data.reportAgent} session={authSession} />
            </ProtectedRoute>
          }
        />
      </Routes>
      {authSession ? <AuthSessionControls onLogout={handleLogout} session={authSession} /> : null}
      <GoogleLoginModal
        isOpen={authModalOpen}
        notice={sessionExpiredReturnTo ? SESSION_EXPIRED_NOTICE : undefined}
        onClose={handleAuthModalClose}
        onSuccess={handleAuthSuccess}
      />
    </>
  );
}
