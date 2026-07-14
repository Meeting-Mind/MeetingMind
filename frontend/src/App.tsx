import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { createMeeting as createMeetingRequest, createSpace, fetchLegacyWorkspaceSnapshot, fetchSpaces } from "./api/workspace";
import { readStoredAuthSession, saveAuthSession, type AuthSession } from "./auth/session";
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
import type { WorkspaceData } from "./types";

type ProjectMeeting = WorkspaceData["projectOverview"]["meetings"][number];
type WorkspaceDataSource = "legacy-api" | "mock-fallback";
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
type TeamMember = {
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

function ProtectedRoute({
  children,
  onRequestLogin,
  session
}: {
  children: ReactNode;
  onRequestLogin: () => void;
  session: AuthSession | null;
}) {
  const location = useLocation();

  useEffect(() => {
    if (!session) {
      onRequestLogin();
    }
  }, [location.pathname, location.search, onRequestLogin, session]);

  if (!session) {
    return <Navigate replace state={{ requestedPath: `${location.pathname}${location.search}` }} to="/" />;
  }

  return <>{children}</>;
}

export function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const [authSession, setAuthSession] = useState<AuthSession | null>(() => readStoredAuthSession());
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [data, setData] = useState<WorkspaceData>(mockData);
  const [workspaceDataSource, setWorkspaceDataSource] = useState<WorkspaceDataSource>("mock-fallback");
  const [projectMeetings, setProjectMeetings] = useState<Record<string, ProjectMeeting[]>>(initialProjectMeetings);
  const [projectMembers, setProjectMembers] = useState<Record<string, TeamMember[]>>(initialProjectMembers);
  const [projectRequests, setProjectRequests] = useState<Record<string, JoinRequest[]>>(initialProjectRequests);
  const [projectInvites, setProjectInvites] = useState<Record<string, InviteMeta>>(initialProjectInvites);
  const [meetingParticipants, setMeetingParticipants] = useState<Record<string, MeetingParticipantState[]>>({});
  const [projectTasks, setProjectTasks] = useState<Record<string, ProjectTaskState[]>>(initialProjectTasks);
  const [projectAiSpaceIds, setProjectAiSpaceIds] = useState<string[]>([]);
  const openAuthModal = useCallback(() => setAuthModalOpen(true), []);

  function handleAuthSuccess(session: AuthSession) {
    saveAuthSession(session);
    setAuthSession(session);
    setAuthModalOpen(false);

    const requestedPath = (location.state as { requestedPath?: string } | null)?.requestedPath;
    if (requestedPath && requestedPath !== "/") {
      navigate(requestedPath, { replace: true });
    }
  }

  async function handleCreateProject({ name, description }: { name: string; description: string }) {
    const normalizedName = name.trim();
    if (!normalizedName) {
      return;
    }

    const existingSpace = data.workspaceHome.spaces.find((space) => space.name === normalizedName);
    let spaceId = existingSpace?.id ?? buildSpaceId(normalizedName);

    if (!existingSpace && authSession) {
      try {
        const response = await createSpace(authSession, { name: normalizedName, description: description.trim() || null });
        spaceId = response.id;
      } catch (error) {
        console.error("[App] createSpace failed, falling back to local-only space", error);
      }
    }

    setProjectMeetings((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectMembers((previous) => ({
      ...previous,
      [normalizedName]:
        previous[normalizedName] ??
        (authSession
          ? [
              {
                name: authSession.user.displayName || authSession.user.email,
                email: authSession.user.email,
                role: "프로젝트 오너",
                spaceRole: "OWNER",
                since: "방금 합류",
                access: getSpaceRoleAccessLabel("OWNER"),
                rank: "팀 리드",
                status: "active"
              }
            ]
          : [])
    }));
    setProjectRequests((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectInvites((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? buildInviteMeta(normalizedName) }));
    setProjectTasks((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));

    setData((previous) => {
      const existingIndex = previous.workspaceHome.spaces.findIndex((space) => space.name === normalizedName);
      const nextSpace = {
        id: spaceId,
        name: normalizedName,
        members: "멤버 0명",
        meetings: "진행 회의 0건",
        updatedAt: "방금 업데이트",
        description: description.trim() || "새 프로젝트 설명이 아직 작성되지 않았습니다.",
        href: "/project-overview"
      };

      const nextSpaces =
        existingIndex >= 0
          ? previous.workspaceHome.spaces.map((space, index) => (index === existingIndex ? nextSpace : space))
          : [nextSpace, ...previous.workspaceHome.spaces];

      return {
        ...previous,
        workspaceHome: {
          ...previous.workspaceHome,
          spaces: nextSpaces,
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
    setProjectTasks((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setMeetingParticipants((previous) => {
      const next: Record<string, MeetingParticipantState[]> = {};
      Object.entries(previous).forEach(([meetingKey, participants]) => {
        if (!meetingKey.startsWith(`${currentSpace.name}:`)) {
          next[meetingKey] = participants;
        }
      });
      return next;
    });

    navigate("/spaces");
  }

  async function handleCreateMeeting(projectName: string, payload?: CreateMeetingPayload) {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (!targetSpace) {
      return;
    }

    const existingMeetings = projectMeetings[projectName] ?? [];
    const nextMeeting = buildMeeting(projectName, targetSpace.description, existingMeetings.length + 1, payload);

    if (authSession) {
      try {
        const response = await createMeetingRequest(authSession, targetSpace.id, {
          title: nextMeeting.title,
          scheduledAt: nextMeeting.scheduledAt ?? new Date().toISOString()
          // ponytail: participantUserIds skipped, mock members have no real backend userId to map from email yet
        });
        nextMeeting.id = response.id;
      } catch (error) {
        console.error("[App] createMeeting failed, falling back to local-only meeting", error);
      }
    }

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
  }

  function handleDeleteMeeting(projectName: string, meetingIndex: string) {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (!targetSpace) {
      return;
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
  }

  function handleUpdateMeetingStatus(projectName: string, meetingIndex: string, state: ProjectMeeting["state"]) {
    setProjectMeetings((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).map((meeting) =>
        meeting.index === meetingIndex ? { ...meeting, state } : meeting
      )
    }));
  }

  function handleAddMeetingParticipant(
    projectName: string,
    meetingIndex: string,
    participant: Pick<MeetingParticipantState, "email" | "name" | "role" | "participantType">
  ) {
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
  }

  function handleUpdateMeetingParticipant(
    projectName: string,
    meetingIndex: string,
    participantId: string,
    updates: Pick<MeetingParticipantState, "accessStatus" | "role">
  ) {
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
      return;
    }

    let active = true;

    fetchLegacyWorkspaceSnapshot(authSession)
      .then((nextData) => {
        if (active) {
          setData({
            workspaceHome: nextData.workspaceHome ?? mockData.workspaceHome,
            liveMeeting: nextData.liveMeeting ?? mockData.liveMeeting,
            meetingAi: nextData.meetingAi ?? mockData.meetingAi,
            projectOverview: nextData.projectOverview ?? mockData.projectOverview,
            reportAgent: nextData.reportAgent ?? mockData.reportAgent
          });
          setWorkspaceDataSource("legacy-api");
        }
      })
      .catch(() => {
        // Keep the local mock data when the API is not running yet.
        if (active) {
          setWorkspaceDataSource("mock-fallback");
        }
      });

    fetchSpaces(authSession)
      .then((response) => {
        if (active) {
          setProjectAiSpaceIds(response.spaces.map((space) => space.id));
        }
      })
      .catch(() => {
        if (active) {
          setProjectAiSpaceIds([]);
        }
      });

    return () => {
      active = false;
    };
  }, [authSession]);

  return (
    <>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route
          path="/spaces"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              <WorkspaceHomePage
                actionItems={data.meetingAi.actions}
                data={data.workspaceHome}
                dataSource={workspaceDataSource}
                onCreateMeeting={handleCreateMeeting}
                onCreateProject={handleCreateProject}
                projectMeetings={projectMeetings}
              />
            </ProtectedRoute>
          }
        />
        <Route
          path="/meeting-access"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              {authSession ? <MeetingAccessPage session={authSession} /> : null}
            </ProtectedRoute>
          }
        />
        <Route
          path="/live-meeting"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              {authSession ? <LiveMeetingPage data={data.liveMeeting} session={authSession} /> : null}
            </ProtectedRoute>
          }
        />
        <Route
          path="/live-room"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              {authSession ? <LiveRoomPage liveMeeting={data.liveMeeting} meetingAi={data.meetingAi} session={authSession} /> : null}
            </ProtectedRoute>
          }
        />
        <Route
          path="/project-overview"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              <ProjectOverviewPage
                currentUserEmail={authSession?.user.email ?? ""}
                data={data.projectOverview}
                session={authSession}
                projectAiSpaceIds={projectAiSpaceIds}
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
                onUpdateMeetingStatus={handleUpdateMeetingStatus}
                spaces={data.workspaceHome.spaces}
              />
            </ProtectedRoute>
          }
        />
        <Route
          path="/team-members"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
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
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              <MeetingAiPage data={data.meetingAi} session={authSession} />
            </ProtectedRoute>
          }
        />
        <Route
          path="/report-agent"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              <ReportAgentPage data={data.reportAgent} session={authSession} />
            </ProtectedRoute>
          }
        />
      </Routes>
      <GoogleLoginModal isOpen={authModalOpen} onClose={() => setAuthModalOpen(false)} onSuccess={handleAuthSuccess} />
    </>
  );
}
