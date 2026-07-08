import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { buildAuthHeaders, readStoredAuthSession, saveAuthSession, type AuthSession } from "./auth/session";
import { GoogleLoginModal } from "./components/GoogleLoginModal";
import { LandingPage } from "./pages/LandingPage";
import { LiveMeetingPage } from "./pages/LiveMeetingPage";
import { LiveRoomPage } from "./pages/LiveRoomPage";
import { MeetingAiPage } from "./pages/MeetingAiPage";
import { ProjectOverviewPage } from "./pages/ProjectOverviewPage";
import { ReportAgentPage } from "./pages/ReportAgentPage";
import { TeamMembersPage } from "./pages/TeamMembersPage";
import { WorkspaceHomePage } from "./pages/WorkspaceHomePage";
import { mockData } from "./data/mockData";
import type { WorkspaceData } from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.trim() || "";

type ProjectMeeting = WorkspaceData["projectOverview"]["meetings"][number];
type TeamMember = {
  name: string;
  email: string;
  role: string;
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
    { name: "이미주", email: "miju@meetingmind.ai", role: "Product Manager", since: "2026.03 합류", access: "프로젝트 관리자", rank: "팀 리드", status: "active" },
    { name: "김진수", email: "jinsu@meetingmind.ai", role: "Backend Lead", since: "2026.02 합류", access: "설계 회의 편집", rank: "Lead", status: "active" },
    { name: "박서윤", email: "seoyun@meetingmind.ai", role: "Product Designer", since: "2026.04 합류", access: "회의 참여 / 문서 열람", rank: "Senior", status: "active" },
    { name: "최민호", email: "minho@meetingmind.ai", role: "Data Engineer", since: "2026.01 합류", access: "기술 회의 참여", rank: "Senior", status: "away" }
  ],
  "Campus Admin Assistant": [
    { name: "정하늘", email: "haneul@meetingmind.ai", role: "Project Manager", since: "2026.02 합류", access: "프로젝트 관리자", rank: "팀 리드", status: "active" },
    { name: "김도윤", email: "doyun@meetingmind.ai", role: "Frontend Developer", since: "2026.03 합류", access: "회의 참여 / 산출물 편집", rank: "Mid-level", status: "active" },
    { name: "이서진", email: "seojin@meetingmind.ai", role: "Backend Developer", since: "2026.01 합류", access: "기술 회의 편집", rank: "Senior", status: "active" },
    { name: "박가은", email: "gaeun@meetingmind.ai", role: "QA Engineer", since: "2026.04 합류", access: "문서 열람 / 회의 참여", rank: "Associate", status: "away" }
  ]
};

const initialProjectRequests: Record<string, JoinRequest[]> = {
  "FinPilot Renewal": [
    { id: "fin-wait-01", name: "서다은", email: "daeun@meetingmind.ai", role: "Frontend Developer", requestedAt: "방금 전", source: "링크" }
  ],
  "Campus Admin Assistant": [
    { id: "caa-wait-01", name: "윤민재", email: "minjae@meetingmind.ai", role: "Operations Manager", requestedAt: "12분 전", source: "코드" }
  ]
};

const initialProjectInvites: Record<string, InviteMeta> = {
  "FinPilot Renewal": { link: "https://meetingmind.ai/invite/finpilot-renewal", code: "FIN-TEAM-0316" },
  "Campus Admin Assistant": { link: "https://meetingmind.ai/invite/campus-admin-assistant", code: "CAA-TEAM-0821" }
};

function buildInviteMeta(projectName: string): InviteMeta {
  const slug = projectName.toLowerCase().replace(/[^a-z0-9가-힣]+/g, "-").replace(/^-+|-+$/g, "");
  const codeSeed = (projectName.replace(/[^A-Za-z0-9가-힣]/g, "").slice(0, 3).toUpperCase() || "NEW").padEnd(3, "X");

  return {
    link: `https://meetingmind.ai/invite/${encodeURIComponent(slug || "new-project")}`,
    code: `${codeSeed}-TEAM-${String(projectName.length).padStart(4, "0")}`
  };
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

function buildMeeting(projectName: string, description: string, count: number): ProjectMeeting {
  const meetingDate = new Date(2026, 5, 27 + (count - 1) * 7);

  return {
    index: `#${String(count).padStart(2, "0")}`,
    title: inferMeetingTitle(projectName, description, count),
    date: `${String(meetingDate.getMonth() + 1).padStart(2, "0")}.${String(meetingDate.getDate()).padStart(2, "0")}`,
    state: "예정"
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
  }, [location.pathname, onRequestLogin, session]);

  if (!session) {
    return <Navigate replace state={{ requestedPath: location.pathname }} to="/" />;
  }

  return <>{children}</>;
}

export function App() {
  const location = useLocation();
  const navigate = useNavigate();
  const [authSession, setAuthSession] = useState<AuthSession | null>(() => readStoredAuthSession());
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [data, setData] = useState<WorkspaceData>(mockData);
  const [projectMeetings, setProjectMeetings] = useState<Record<string, ProjectMeeting[]>>(initialProjectMeetings);
  const [projectMembers, setProjectMembers] = useState<Record<string, TeamMember[]>>(initialProjectMembers);
  const [projectRequests, setProjectRequests] = useState<Record<string, JoinRequest[]>>(initialProjectRequests);
  const [projectInvites, setProjectInvites] = useState<Record<string, InviteMeta>>(initialProjectInvites);
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

  function handleCreateProject({ name, description }: { name: string; description: string }) {
    const normalizedName = name.trim();
    if (!normalizedName) {
      return;
    }

    setProjectMeetings((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectMembers((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectRequests((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectInvites((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? buildInviteMeta(normalizedName) }));

    setData((previous) => {
      const existingIndex = previous.workspaceHome.spaces.findIndex((space) => space.name === normalizedName);
      const nextSpace = {
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

  function handleCreateMeeting(projectName: string) {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (!targetSpace) {
      return;
    }

    setProjectMeetings((previous) => {
      const existingMeetings = previous[projectName] ?? [];
      return {
        ...previous,
        [projectName]: [...existingMeetings, buildMeeting(projectName, targetSpace.description, existingMeetings.length + 1)]
      };
    });

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

  function handleApproveJoinRequest(projectName: string, requestId: string) {
    const request = projectRequests[projectName]?.find((item) => item.id === requestId);
    if (!request) {
      return;
    }

    setProjectRequests((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).filter((item) => item.id !== requestId)
    }));

    setProjectMembers((previous) => {
      const nextMembers = [
        ...(previous[projectName] ?? []),
        {
          name: request.name,
          email: request.email,
          role: request.role,
          since: "2026.06 승인",
          access: "회의 참여 / 문서 열람",
          rank: "Member",
          status: "active" as const
        }
      ];

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
  }

  function handleRejectJoinRequest(projectName: string, requestId: string) {
    setProjectRequests((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).filter((item) => item.id !== requestId)
    }));
  }

  useEffect(() => {
    if (!authSession) {
      return;
    }

    let active = true;

    fetch(`${API_BASE_URL}/api/workspace`, {
      headers: buildAuthHeaders(authSession)
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`API request failed with ${response.status}`);
        }
        return response.json() as Promise<Partial<WorkspaceData>>;
      })
      .then((nextData) => {
        if (active) {
          setData({
            workspaceHome: nextData.workspaceHome ?? mockData.workspaceHome,
            liveMeeting: nextData.liveMeeting ?? mockData.liveMeeting,
            meetingAi: nextData.meetingAi ?? mockData.meetingAi,
            projectOverview: nextData.projectOverview ?? mockData.projectOverview,
            reportAgent: nextData.reportAgent ?? mockData.reportAgent
          });
        }
      })
      .catch(() => {
        // Keep the local mock data when the API is not running yet.
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
              <WorkspaceHomePage data={data.workspaceHome} onCreateProject={handleCreateProject} />
            </ProtectedRoute>
          }
        />
        <Route
          path="/live-meeting"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              <LiveMeetingPage data={data.liveMeeting} />
            </ProtectedRoute>
          }
        />
        <Route
          path="/live-room"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              <LiveRoomPage liveMeeting={data.liveMeeting} meetingAi={data.meetingAi} />
            </ProtectedRoute>
          }
        />
        <Route
          path="/project-overview"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              <ProjectOverviewPage
                data={data.projectOverview}
                onCreateMeeting={handleCreateMeeting}
                onCreateProject={handleCreateProject}
                projectMeetings={projectMeetings}
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
                onRejectRequest={handleRejectJoinRequest}
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
              <MeetingAiPage data={data.meetingAi} />
            </ProtectedRoute>
          }
        />
        <Route
          path="/report-agent"
          element={
            <ProtectedRoute onRequestLogin={openAuthModal} session={authSession}>
              <ReportAgentPage data={data.reportAgent} />
            </ProtectedRoute>
          }
        />
      </Routes>
      <GoogleLoginModal isOpen={authModalOpen} onClose={() => setAuthModalOpen(false)} onSuccess={handleAuthSuccess} />
    </>
  );
}
