import { Link, useNavigate, useParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import { DataState } from "../components/common/DataState";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { MeetingLayout } from "../components/layout/MeetingLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { TeamMember, WorkspaceDataSource } from "../app/workspaceTypes";
import { useMeetingContext } from "../hooks/useMeetingContext";
import { useMeetingTaskCandidates } from "../hooks/useMeetingTaskCandidates";
import type { WorkspaceData } from "../types";

function candidateStatusLabel(status: "candidate" | "registered" | "dismissed") {
  if (status === "registered") {
    return "칸반 등록됨";
  }
  if (status === "dismissed") {
    return "등록 제외됨";
  }
  return "검토 대기";
}

export function MeetingTaskCandidatesPage({
  currentUserEmail,
  projectMembers,
  session,
  spaces,
  workspaceDataSource,
  onCreateProject
}: {
  currentUserEmail: string;
  projectMembers: Record<string, TeamMember[]>;
  session: AuthSession | null;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
}) {
  const navigate = useNavigate();
  const { spaceId = "", meetingId = "" } = useParams<{ spaceId: string; meetingId: string }>();
  const selectedSpace = spaces.find((space) => space.id === spaceId);
  const meetingContext = useMeetingContext(session, meetingId, spaceId);
  const taskCandidates = useMeetingTaskCandidates(session, meetingId);

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="meeting-task-candidates-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState
          actionLabel="프로젝트 목록으로"
          onAction={() => navigate("/spaces")}
          state="notFound"
          title="프로젝트를 찾을 수 없습니다"
          description="태스크 후보가 속한 프로젝트가 없거나 접근 권한이 없습니다."
        />
      </AppShell>
    );
  }

  const member = (projectMembers[selectedSpace.name] ?? []).find((item) => item.email === currentUserEmail);

  if (meetingContext.status === "loading" || !meetingContext.detail) {
    return (
      <MeetingLayout
        meeting={null}
        meetingId={meetingId}
        onCreateProject={onCreateProject}
        projectName={selectedSpace.name}
        spaceId={spaceId}
        spaceRole={member?.spaceRole}
        dataSource={workspaceDataSource}
      >
        <DataState state="loading" title="회의 정보를 불러오는 중입니다" description="태스크 후보의 회의 범위와 권한을 확인하고 있습니다." />
      </MeetingLayout>
    );
  }

  if (meetingContext.status === "error") {
    return (
      <MeetingLayout
        meeting={null}
        meetingId={meetingId}
        onCreateProject={onCreateProject}
        projectName={selectedSpace.name}
        spaceId={spaceId}
        spaceRole={member?.spaceRole}
        dataSource={workspaceDataSource}
      >
        <DataState
          actionLabel="회의 목록으로"
          onAction={() => navigate(`/spaces/${encodeURIComponent(spaceId)}/meetings`)}
          state="error"
          title="회의 정보를 불러오지 못했습니다"
          description={meetingContext.error ?? "접근 권한을 확인한 뒤 다시 시도해 주세요."}
        />
      </MeetingLayout>
    );
  }

  return (
    <MeetingLayout
      activeItem="tasks"
      meeting={meetingContext.detail}
      meetingId={meetingId}
      onCreateProject={onCreateProject}
      projectName={selectedSpace.name}
      spaceId={spaceId}
      spaceRole={member?.spaceRole}
      dataSource={workspaceDataSource}
    >
      <main className="meeting-task-candidates-content">
        <header className="meeting-task-candidates-page-header">
          <div>
            <p className="meeting-detail-section-kicker">Task candidates</p>
            <h2>회의에서 나온 실행 항목</h2>
            <p>회의 근거에서 추출된 후보를 검토하고, 필요한 내용만 프로젝트 칸반에 등록합니다.</p>
          </div>
          <button
            className="mm-common-button mm-common-button--secondary"
            disabled={taskCandidates.action !== "idle" || taskCandidates.status === "loading"}
            onClick={() => void taskCandidates.extract()}
            type="button"
          >
            {taskCandidates.action === "extracting" ? "추출 중..." : "태스크 후보 다시 추출"}
          </button>
        </header>

        <section className="meeting-task-candidates-guidance" aria-label="태스크 후보 검토 기준">
          <div>
            <span className="meeting-task-candidates-guidance-label">검토 기준</span>
            <strong>후보를 확인한 뒤 등록하세요</strong>
            <p>후보는 회의 기록을 바탕으로 만들어지며, 확정 전에는 프로젝트 칸반에 표시되지 않습니다.</p>
          </div>
          <div className="meeting-task-candidates-scope">
            <StatusBadge context="task" label="회의 근거" status="CANDIDATE" />
            <span>현재 회의에서만 생성된 후보</span>
          </div>
        </section>

        {taskCandidates.error ? (
          <div className="meeting-task-candidates-feedback meeting-task-candidates-feedback--error" role="alert">
            {taskCandidates.error}
          </div>
        ) : null}
        {taskCandidates.notice ? (
          <div className="meeting-task-candidates-feedback meeting-task-candidates-feedback--notice" role="status">
            {taskCandidates.notice}
          </div>
        ) : null}

        {!taskCandidates.canConfirm && taskCandidates.status === "ready" ? (
          <section className="meeting-task-candidates-permission" aria-label="태스크 확정 권한">
            <RoleBadge role={meetingContext.detail.myRole ?? "VIEWER"} scope="meeting" />
            <p>현재 회의 역할은 후보를 확인할 수 있지만 프로젝트 칸반 등록은 할 수 없습니다. 회의 HOST 또는 EDITOR에게 확정을 요청하세요.</p>
          </section>
        ) : null}

        <section className="meeting-task-candidates-surface" aria-labelledby="meeting-task-candidates-title">
          <div className="meeting-task-candidates-surface-head">
            <div>
              <p className="meeting-detail-section-kicker">Review queue</p>
              <h3 id="meeting-task-candidates-title">검토 대기열</h3>
            </div>
            <span>{taskCandidates.candidates.length}개 후보</span>
          </div>

          {taskCandidates.status === "loading" ? (
            <DataState state="loading" title="태스크 후보를 불러오는 중입니다" description="회의 기록에서 생성된 후보를 준비하고 있습니다." />
          ) : taskCandidates.status === "error" ? (
            <DataState state="error" title="태스크 후보를 불러오지 못했습니다" description={taskCandidates.error || "잠시 후 다시 시도해 주세요."} />
          ) : taskCandidates.candidates.length ? (
            <div className="meeting-task-candidates-list">
              {taskCandidates.candidates.map((candidate) => {
                const isActive = taskCandidates.activeCandidateId === candidate.id;
                const isEditable = taskCandidates.canConfirm && candidate.status === "candidate" && taskCandidates.action === "idle";
                return (
                  <article className={`meeting-task-candidate-card is-${candidate.status}`} key={candidate.id}>
                    <div className="meeting-task-candidate-card-head">
                      <div>
                        <StatusBadge context="task" label={candidateStatusLabel(candidate.status)} status={candidate.status === "candidate" ? "CANDIDATE" : candidate.status === "registered" ? "CONFIRMED" : "DISMISSED"} />
                        <span className="meeting-task-candidate-source">근거 {candidate.sourceIds[0] || "확인 필요"}</span>
                      </div>
                      {candidate.status === "registered" && candidate.taskId ? (
                        <Link to={`/spaces/${encodeURIComponent(spaceId)}/tasks`}>칸반에서 보기</Link>
                      ) : null}
                    </div>

                    <div className="meeting-task-candidate-fields">
                      <label>
                        <span>제목</span>
                        <input
                          disabled={!isEditable}
                          onChange={(event) => taskCandidates.updateCandidate(candidate.id, { title: event.target.value })}
                          value={candidate.title}
                        />
                      </label>
                      <label>
                        <span>설명</span>
                        <textarea
                          disabled={!isEditable}
                          onChange={(event) => taskCandidates.updateCandidate(candidate.id, { description: event.target.value })}
                          placeholder="확정할 때 필요한 설명을 추가하세요"
                          rows={3}
                          value={candidate.description}
                        />
                      </label>
                      <div className="meeting-task-candidate-fields-grid">
                        <label>
                          <span>담당자</span>
                          <select
                            disabled={!isEditable}
                            onChange={(event) => taskCandidates.updateCandidate(candidate.id, { assigneeId: event.target.value || null })}
                            value={candidate.assigneeId || ""}
                          >
                            <option value="">미지정</option>
                            {taskCandidates.assignees.map((assignee) => (
                              <option key={assignee.id} value={assignee.id}>{assignee.displayName}</option>
                            ))}
                          </select>
                        </label>
                        <label>
                          <span>마감일</span>
                          <input
                            disabled={!isEditable}
                            onChange={(event) => taskCandidates.updateCandidate(candidate.id, { dueDate: event.target.value })}
                            type="date"
                            value={candidate.dueDate}
                          />
                        </label>
                      </div>
                    </div>

                    {candidate.status === "candidate" ? (
                      <div className="meeting-task-candidate-card-actions">
                        <button
                          className="mm-common-button mm-common-button--primary"
                          disabled={!isEditable || !candidate.title.trim()}
                          onClick={() => void taskCandidates.confirm(candidate.id)}
                          type="button"
                        >
                          {isActive && taskCandidates.action === "confirming" ? "등록 중..." : "칸반에 등록"}
                        </button>
                        <button
                          className="mm-common-button mm-common-button--secondary"
                          disabled={!isEditable}
                          onClick={() => void taskCandidates.dismiss(candidate.id)}
                          type="button"
                        >
                          {isActive && taskCandidates.action === "dismissing" ? "제외 중..." : "등록 제외"}
                        </button>
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </div>
          ) : (
            <DataState
              actionLabel="태스크 후보 추출"
              onAction={() => void taskCandidates.extract()}
              state="empty"
              title="아직 태스크 후보가 없습니다"
              description="현재 회의의 전사와 회의록에서 실행 항목을 추출하면 이곳에서 검토할 수 있습니다."
            />
          )}
        </section>
      </main>
    </MeetingLayout>
  );
}
