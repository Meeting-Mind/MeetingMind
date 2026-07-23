import { useEffect } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import type { DashboardSummaryResponse, ProjectKnowledgeItem, WorkspaceData } from "../types";
import { DataState } from "../components/common/DataState";
import { PageHeader } from "../components/common/PageHeader";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { SpaceLayout } from "../components/layout/SpaceLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { ProjectMeeting, ProjectTaskState, TeamMember, WorkspaceDataSource } from "../app/workspaceTypes";

function buildMeetingHref(spaceId: string, projectName: string, meeting: ProjectMeeting) {
  if (meeting.id) {
    return `/spaces/${encodeURIComponent(spaceId)}/meetings/${encodeURIComponent(meeting.id)}`;
  }

  const path = meeting.state === "예정" ? "/live-meeting" : "/report-agent";
  const params = new URLSearchParams({
    spaceId,
    project: projectName,
    meeting: meeting.title,
    round: meeting.index.replace("#", "")
  });
  return `${path}?${params.toString()}`;
}

function meetingStatus(status: ProjectMeeting["state"]) {
  if (status === "완료" || status === "보고서 생성됨") {
    return "COMPLETED" as const;
  }
  if (status === "진행 중") {
    return "IN_PROGRESS" as const;
  }
  if (status === "취소") {
    return "CANCELED" as const;
  }
  return "SCHEDULED" as const;
}

function taskStatus(status: ProjectTaskState["status"]) {
  return status;
}

export function ProjectHomePage({
  currentUserEmail,
  dashboardSummary,
  projectAiSpaceIds,
  projectKnowledge,
  projectMembers,
  projectMeetings,
  projectTasks,
  session,
  spaces,
  workspaceDataSource,
  onCreateProject
}: {
  currentUserEmail: string;
  dashboardSummary: DashboardSummaryResponse | null;
  projectAiSpaceIds: string[];
  projectKnowledge: Record<string, ProjectKnowledgeItem[]>;
  projectMembers: Record<string, TeamMember[]>;
  projectMeetings: Record<string, ProjectMeeting[]>;
  projectTasks: Record<string, ProjectTaskState[]>;
  session: AuthSession | null;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
}) {
  const { spaceId = "" } = useParams<{ spaceId: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    document.body.className = "app-theme project-home-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  const selectedSpace = spaces.find((space) => space.id === spaceId);
  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="project-home-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState
          actionLabel="프로젝트 목록으로 이동"
          onAction={() => navigate("/spaces")}
          state="notFound"
        />
      </AppShell>
    );
  }

  const meetings = projectMeetings[selectedSpace.name] ?? [];
  const tasks = projectTasks[selectedSpace.name] ?? [];
  const openTasks = tasks.filter((task) => task.status !== "DONE");
  const nextMeeting = meetings.find((meeting) => meeting.state !== "완료" && meeting.state !== "취소") ?? null;
  const members = projectMembers[selectedSpace.name] ?? [];
  const currentMember = members.find((member) => member.email === currentUserEmail);
  const latestReport = dashboardSummary?.latestReports.find((report) => report.spaceId === selectedSpace.id) ?? null;
  const knowledgeCount = (projectKnowledge[selectedSpace.id] ?? []).length;
  const projectAiAvailable = projectAiSpaceIds.includes(selectedSpace.id);
  return (
    <SpaceLayout
      contentClassName="project-home-main"
      dataSource={workspaceDataSource}
      onCreateProject={onCreateProject}
      projectName={selectedSpace.name}
      spaceId={selectedSpace.id}
    >
      <PageHeader
        actions={(
          <>
          {nextMeeting ? (
            <Link className="mm-common-button mm-common-button--primary" to={buildMeetingHref(selectedSpace.id, selectedSpace.name, nextMeeting)}>
              다음 회의 열기
            </Link>
          ) : (
            <Link className="mm-common-button mm-common-button--primary" to={`/spaces/${encodeURIComponent(selectedSpace.id)}/meetings`}>
              회의 만들기
            </Link>
          )}
          <Link className="mm-common-button mm-common-button--secondary" to={`/spaces/${encodeURIComponent(selectedSpace.id)}/settings`}>
            프로젝트 설정
          </Link>
          </>
        )}
        breadcrumb={(
          <>
            <Link to="/spaces">프로젝트 목록</Link>
            <span aria-hidden="true">/</span>
            <strong>{selectedSpace.name}</strong>
          </>
        )}
        description={selectedSpace.description || "프로젝트 설명이 아직 작성되지 않았습니다."}
        eyebrow="Project Home"
        meta={currentMember ? <RoleBadge role={currentMember.spaceRole} scope="space" /> : null}
        title={selectedSpace.name}
      />

      <section aria-label="프로젝트 요약" className="project-home-summary-grid">
        <article>
          <span>회의</span>
          <strong>{meetings.length}</strong>
          <p>접근 가능한 회의</p>
        </article>
        <article>
          <span>열린 작업</span>
          <strong>{openTasks.length}</strong>
          <p>완료 전 Action Item</p>
        </article>
        <article>
          <span>확정 회의록</span>
          <strong>{latestReport ? `v${latestReport.version}` : "-"}</strong>
          <p>{latestReport ? "가장 최근 확정본" : "확정된 회의록 없음"}</p>
        </article>
        <article>
          <span>공식 지식</span>
          <strong>{knowledgeCount}</strong>
          <p>Project AI 검색 기준</p>
        </article>
      </section>

      <div className="project-home-content-grid">
        <main className="project-home-main-column">
          <section aria-labelledby="project-home-next-title" className="project-home-next-surface">
            <div>
              <p className="project-home-section-kicker">Next action</p>
              <h2 id="project-home-next-title">{nextMeeting ? "다음 회의에서 이어갈 일" : "다음 업무를 시작하세요"}</h2>
              <p>
                {nextMeeting
                  ? `${nextMeeting.title} · ${nextMeeting.date} · ${nextMeeting.state}`
                  : "회의를 만들고, 결정사항과 태스크를 프로젝트 지식으로 연결하세요."}
              </p>
            </div>
            {nextMeeting ? <Link className="project-home-inline-link" to={buildMeetingHref(selectedSpace.id, selectedSpace.name, nextMeeting)}>회의 상세 보기 →</Link> : null}
          </section>

          <section aria-labelledby="project-home-meetings-title" className="project-home-section">
            <div className="project-home-section-header">
              <div>
                <p className="project-home-section-kicker">Meetings</p>
                <h2 id="project-home-meetings-title">최근 회의</h2>
              </div>
              <Link className="project-home-inline-link" to={`/spaces/${encodeURIComponent(selectedSpace.id)}/meetings`}>전체 보기 →</Link>
            </div>
            {meetings.length ? (
              <div className="project-home-list">
                {meetings.slice(0, 4).map((meeting) => (
                  <Link className="project-home-list-row" key={meeting.id ?? meeting.index} to={buildMeetingHref(selectedSpace.id, selectedSpace.name, meeting)}>
                    <span className="project-home-list-index">{meeting.index}</span>
                    <span className="project-home-list-copy">
                      <strong>{meeting.title}</strong>
                      <small>{meeting.date}</small>
                    </span>
                    <StatusBadge context="meeting" label={meeting.state} status={meetingStatus(meeting.state)} />
                  </Link>
                ))}
              </div>
            ) : (
              <DataState state="empty" title="아직 회의가 없습니다" description="회의를 만들면 이 프로젝트 흐름에 기록됩니다." />
            )}
          </section>

          <section aria-labelledby="project-home-tasks-title" className="project-home-section">
            <div className="project-home-section-header">
              <div>
                <p className="project-home-section-kicker">Action items</p>
                <h2 id="project-home-tasks-title">열린 작업</h2>
              </div>
              <Link className="project-home-inline-link" to={`/spaces/${encodeURIComponent(selectedSpace.id)}/tasks`}>칸반 열기 →</Link>
            </div>
            {openTasks.length ? (
              <div className="project-home-task-list">
                {openTasks.slice(0, 5).map((task) => (
                  <Link className="project-home-task-row" key={task.id} to={`/spaces/${encodeURIComponent(selectedSpace.id)}/tasks`}>
                    <span className="project-home-task-mark" />
                    <span>
                      <strong>{task.title}</strong>
                      <small>{task.assignee} · {task.dueDate || "마감일 미정"}</small>
                    </span>
                    <StatusBadge context="task" label={task.status === "IN_PROGRESS" ? "진행 중" : "대기"} status={taskStatus(task.status)} />
                  </Link>
                ))}
              </div>
            ) : (
              <DataState state="empty" title="열린 작업이 없습니다" description="회의에서 확정한 태스크가 여기에 나타납니다." />
            )}
          </section>
        </main>

        <aside className="project-home-side-column">
          <section aria-labelledby="project-home-ai-title" className="project-home-ai-surface">
            <div className="project-home-side-header">
              <div>
                <p className="project-home-section-kicker">Project AI</p>
                <h2 id="project-home-ai-title">프로젝트 맥락으로 질문</h2>
              </div>
              <StatusBadge context="generic" label={projectAiAvailable ? "검색 가능" : "준비 중"} status={projectAiAvailable ? "COMPLETED" : "PENDING"} />
            </div>
            <p>공식 Project Knowledge와 접근 가능한 회의만 검색합니다.</p>
            <Link className="project-home-ai-link" to={`/spaces/${encodeURIComponent(selectedSpace.id)}/ai`}>
              {session && projectAiAvailable ? "Project AI 열기" : "검색 범위 확인"} →
            </Link>
          </section>

          <section aria-labelledby="project-home-report-title" className="project-home-side-section">
            <div className="project-home-side-header">
              <div>
                <p className="project-home-section-kicker">Latest report</p>
                <h2 id="project-home-report-title">최근 확정 회의록</h2>
              </div>
            </div>
            {latestReport ? (
              <Link className="project-home-report" to={`/spaces/${encodeURIComponent(selectedSpace.id)}/meetings/${encodeURIComponent(latestReport.meetingId)}/report`}>
                <strong>{latestReport.title}</strong>
                <span>{latestReport.meetingTitle} · v{latestReport.version}</span>
                <small>{new Date(latestReport.confirmedAt).toLocaleDateString("ko-KR")}</small>
              </Link>
            ) : (
              <p className="project-home-muted">접근 가능한 확정 회의록이 없습니다.</p>
            )}
          </section>
        </aside>
      </div>
    </SpaceLayout>
  );
}
