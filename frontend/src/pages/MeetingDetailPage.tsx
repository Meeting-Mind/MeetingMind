import { Link, useParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import { DataState } from "../components/common/DataState";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { MeetingLayout } from "../components/layout/MeetingLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import { useMeetingContext } from "../hooks/useMeetingContext";
import type { TeamMember, WorkspaceDataSource } from "../app/workspaceTypes";
import type { WorkspaceData } from "../types";

function formatDateTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "일정 미정";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Asia/Seoul"
  }).format(date);
}

function stageState(status: "SCHEDULED" | "IN_PROGRESS" | "ENDED" | "CANCELED", stage: "meeting" | "report") {
  if (status === "CANCELED") {
    return "cancelled";
  }
  if (stage === "meeting") {
    return status === "SCHEDULED" ? "current" : "done";
  }
  return status === "ENDED" ? "current" : "upcoming";
}

export function MeetingDetailPage({
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
  const { spaceId = "", meetingId = "" } = useParams<{ spaceId: string; meetingId: string }>();
  const selectedSpace = spaces.find((space) => space.id === spaceId);
  const context = useMeetingContext(session, meetingId, spaceId);

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="meeting-detail-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState
          actionLabel="프로젝트 목록으로"
          onAction={() => { window.location.href = "/spaces"; }}
          state="notFound"
          title="프로젝트를 찾을 수 없습니다"
          description="회의가 속한 프로젝트가 없거나 접근 권한이 없습니다. 프로젝트 목록에서 다시 선택해 주세요."
        />
      </AppShell>
    );
  }

  const member = (projectMembers[selectedSpace.name] ?? []).find((item) => item.email === currentUserEmail);

  if (context.status === "loading") {
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
        <DataState state="loading" title="회의 정보를 불러오는 중입니다" description="회의 권한과 일정을 확인하고 있습니다." />
      </MeetingLayout>
    );
  }

  if (context.status === "error" || !context.detail) {
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
          onAction={() => { window.location.href = `/spaces/${encodeURIComponent(spaceId)}/meetings`; }}
          state="error"
          title="회의 정보를 불러오지 못했습니다"
          description={context.error ?? "접근 권한을 확인한 뒤 다시 시도해 주세요."}
        />
      </MeetingLayout>
    );
  }

  const meeting = context.detail;
  const participants = context.participants;
  const reportHref = `/spaces/${encodeURIComponent(spaceId)}/meetings/${encodeURIComponent(meeting.id)}/report`;
  const aiHref = `/spaces/${encodeURIComponent(spaceId)}/meetings/${encodeURIComponent(meeting.id)}/ai`;
  const prejoinHref = `/spaces/${encodeURIComponent(spaceId)}/meetings/${encodeURIComponent(meeting.id)}/live/prejoin`;

  return (
    <MeetingLayout
      meeting={meeting}
      meetingId={meeting.id}
      onCreateProject={onCreateProject}
      projectName={selectedSpace.name}
      spaceId={spaceId}
      spaceRole={member?.spaceRole}
      dataSource={workspaceDataSource}
    >
      <main className="meeting-detail-content">
        <section className="meeting-detail-overview" aria-labelledby="meeting-detail-title">
          <div>
            <p className="meeting-detail-kicker">{formatDateTime(meeting.scheduledAt)} · {formatDateTime(meeting.scheduledEndAt)}까지</p>
            <h2 id="meeting-detail-title">이번 회의에서 확인할 것</h2>
            <p>{meeting.description || "회의가 시작되면 대화가 전사되고, 확인된 회의록과 후속 태스크로 이어집니다."}</p>
          </div>
          <div className="meeting-detail-overview-actions">
            <StatusBadge context="meeting" status={meeting.status} />
            <Link className="mm-common-button mm-common-button--primary" to={meeting.status === "IN_PROGRESS" ? `${prejoinHref.replace("/live/prejoin", "/live")}` : prejoinHref}>
              {meeting.status === "IN_PROGRESS" ? "회의로 돌아가기" : "회의 준비하기"}
            </Link>
          </div>
        </section>

        <section className="meeting-detail-grid" aria-label="회의 정보">
          <div className="meeting-detail-surface meeting-detail-timeline-surface">
            <div className="meeting-detail-section-heading">
              <div>
                <p className="meeting-detail-section-kicker">Flow</p>
                <h2>회의 결과 흐름</h2>
              </div>
              <span>회의 → 회의록 → 후속 작업</span>
            </div>
            <ol className="meeting-detail-timeline">
              <li className={stageState(meeting.status, "meeting")}>
                <span aria-hidden="true" />
                <div><strong>회의 진행</strong><small>참가자와 안건을 확인합니다.</small></div>
              </li>
              <li className={stageState(meeting.status, "report")}>
                <span aria-hidden="true" />
                <div><strong>회의록 확인</strong><small>대화에서 결정사항과 실행 항목을 정리합니다.</small></div>
              </li>
              <li className={meeting.status === "ENDED" ? "current" : "upcoming"}>
                <span aria-hidden="true" />
                <div><strong>다음 업무 연결</strong><small>확인된 내용은 프로젝트 태스크와 지식으로 이어집니다.</small></div>
              </li>
            </ol>
          </div>

          <div className="meeting-detail-surface meeting-detail-actions-surface">
            <div className="meeting-detail-section-heading">
              <div>
                <p className="meeting-detail-section-kicker">Next actions</p>
                <h2>다음 행동</h2>
              </div>
            </div>
            <div className="meeting-detail-action-list">
              <Link to={reportHref}><strong>회의록 열기</strong><span>결정사항과 실행 항목을 확인합니다.</span><b aria-hidden="true">→</b></Link>
              <Link to={aiHref}><strong>Meeting AI에게 묻기</strong><span>현재 회의 범위 안에서 근거를 찾습니다.</span><b aria-hidden="true">→</b></Link>
              <Link to={`/spaces/${encodeURIComponent(spaceId)}/meetings`}><strong>회의 목록으로 돌아가기</strong><span>프로젝트의 다른 회의를 확인합니다.</span><b aria-hidden="true">→</b></Link>
            </div>
          </div>
        </section>

        <section className="meeting-detail-surface meeting-detail-participants-surface" aria-labelledby="meeting-participants-title">
          <div className="meeting-detail-section-heading">
            <div>
              <p className="meeting-detail-section-kicker">Access</p>
              <h2 id="meeting-participants-title">참가자와 권한</h2>
            </div>
            <span>{participants.length}명 · 회의별 권한</span>
          </div>
          {participants.length ? (
            <div className="meeting-detail-participants-list">
              {participants.map((participant) => (
                <div className="meeting-detail-participant" key={participant.id}>
                  <div className="meeting-detail-participant-avatar" aria-hidden="true">{(participant.displayName || participant.email || "?").slice(0, 1).toUpperCase()}</div>
                  <div>
                    <strong>{participant.displayName || participant.email || "이름 미등록 사용자"}</strong>
                    <span>{participant.email || "이메일 정보 없음"}</span>
                  </div>
                  <RoleBadge role={participant.role} scope="meeting" />
                  <StatusBadge context="access" status={participant.accessStatus} />
                </div>
              ))}
            </div>
          ) : (
            <DataState state="empty" title="참가자 정보가 없습니다" description="참가자 정보가 준비되면 이 영역에 표시됩니다." />
          )}
        </section>
      </main>
    </MeetingLayout>
  );
}
