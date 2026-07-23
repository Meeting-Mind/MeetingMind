import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import { DataState } from "../components/common/DataState";
import { PageHeader } from "../components/common/PageHeader";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { SpaceLayout } from "../components/layout/SpaceLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { ProjectMeeting, TeamMember, WorkspaceDataSource } from "../app/workspaceTypes";
import type { WorkspaceData } from "../types";

function meetingHref(spaceId: string, projectName: string, meeting: ProjectMeeting) {
  if (meeting.id) {
    return `/spaces/${encodeURIComponent(spaceId)}/meetings/${encodeURIComponent(meeting.id)}`;
  }

  const path = meeting.state === "예정" ? "/live-meeting" : "/report-agent";
  return `${path}?${new URLSearchParams({
    spaceId,
    project: projectName,
    meeting: meeting.title,
    round: meeting.index.replace("#", "")
  }).toString()}`;
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

export function ProjectMeetingsPage({
  currentUserEmail,
  projectMembers,
  projectMeetings,
  session,
  spaces,
  workspaceDataSource,
  onCreateProject,
  meetingMutationError,
  meetingMutationLoading,
  onCreateMeeting
}: {
  currentUserEmail: string;
  projectMembers: Record<string, TeamMember[]>;
  projectMeetings: Record<string, ProjectMeeting[]>;
  session: AuthSession | null;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  meetingMutationError?: string;
  meetingMutationLoading?: boolean;
  onCreateMeeting?: (
    projectName: string,
    payload?: { title?: string; description?: string; scheduledAt?: string; scheduledEndAt?: string; participantEmails?: string[] }
  ) => Promise<boolean>;
}) {
  const { spaceId = "" } = useParams<{ spaceId: string }>();
  const [query, setQuery] = useState("");
  const [stateFilter, setStateFilter] = useState<"ALL" | ProjectMeeting["state"]>("ALL");
  const [createOpen, setCreateOpen] = useState(false);
  const [newMeetingTitle, setNewMeetingTitle] = useState("");
  const [newMeetingDescription, setNewMeetingDescription] = useState("");
  const [newMeetingStart, setNewMeetingStart] = useState("");
  const [newMeetingEnd, setNewMeetingEnd] = useState("");
  const [createError, setCreateError] = useState("");
  const selectedSpace = spaces.find((space) => space.id === spaceId);

  useEffect(() => {
    document.body.className = "app-theme project-meetings-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  const meetings = selectedSpace ? projectMeetings[selectedSpace.name] ?? [] : [];
  const normalizedQuery = query.trim().toLowerCase();
  const filteredMeetings = meetings.filter((meeting) => {
    const matchesState = stateFilter === "ALL" || meeting.state === stateFilter;
    const matchesQuery = !normalizedQuery || [meeting.index, meeting.title, meeting.date, meeting.state].join(" ").toLowerCase().includes(normalizedQuery);
    return matchesState && matchesQuery;
  });

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="project-meetings-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState state="notFound" title="프로젝트를 찾을 수 없습니다" description="프로젝트 목록에서 접근 가능한 프로젝트를 선택해 주세요." />
      </AppShell>
    );
  }

  const member = (projectMembers[selectedSpace.name] ?? []).find((item) => item.email === currentUserEmail);
  const canCreateMeeting = member?.spaceRole === "OWNER" || member?.spaceRole === "ADMIN";

  function resetCreateForm() {
    setNewMeetingTitle("");
    setNewMeetingDescription("");
    setNewMeetingStart("");
    setNewMeetingEnd("");
    setCreateError("");
  }

  async function handleCreateMeeting(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSpace || !onCreateMeeting || meetingMutationLoading || !newMeetingTitle.trim()) {
      return;
    }

    setCreateError("");
    try {
      const created = await onCreateMeeting(selectedSpace.name, {
        title: newMeetingTitle.trim(),
        description: newMeetingDescription.trim() || undefined,
        scheduledAt: newMeetingStart ? new Date(newMeetingStart).toISOString() : undefined,
        scheduledEndAt: newMeetingEnd ? new Date(newMeetingEnd).toISOString() : undefined
      });
      if (created) {
        setCreateOpen(false);
        resetCreateForm();
      }
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : "회의를 생성하지 못했습니다.");
    }
  }

  return (
    <SpaceLayout
      activeItem="meetings"
      contentClassName="project-meetings-main"
      dataSource={workspaceDataSource}
      onCreateProject={onCreateProject}
      projectName={selectedSpace.name}
      spaceId={selectedSpace.id}
    >
      <PageHeader
        actions={(
          <>
          <button
            className="mm-common-button mm-common-button--primary"
            disabled={!canCreateMeeting || !onCreateMeeting}
            onClick={() => {
              setCreateError("");
              setCreateOpen(true);
            }}
            type="button"
          >
            회의 만들기
          </button>
          <Link className="mm-common-button mm-common-button--secondary" to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>프로젝트 홈</Link>
          </>
        )}
        breadcrumb={(
          <>
            <Link to="/spaces">프로젝트 목록</Link>
            <span aria-hidden="true">/</span>
            <Link to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>{selectedSpace.name}</Link>
            <span aria-hidden="true">/</span>
            <strong>회의</strong>
          </>
        )}
        description="회의를 시작하고, 진행 상태와 다음 산출물로 이동합니다."
        eyebrow="Meetings"
        meta={member ? <RoleBadge role={member.spaceRole} scope="space" /> : null}
        title="회의 목록"
      />

      {!canCreateMeeting ? <p className="project-meetings-permission-note">회의 생성은 프로젝트 오너 또는 관리자만 할 수 있습니다.</p> : null}
      {meetingMutationError || createError ? <p aria-live="polite" className="project-meetings-error" role="alert">{createError || meetingMutationError}</p> : null}

      <section className="project-meetings-toolbar" aria-label="회의 필터">
        <label>
          <span>회의 검색</span>
          <input aria-label="회의 검색" onChange={(event) => setQuery(event.target.value)} placeholder="제목, 회차, 상태 검색" type="search" value={query} />
        </label>
        <label>
          <span>상태</span>
          <select aria-label="회의 상태 필터" onChange={(event) => setStateFilter(event.target.value as typeof stateFilter)} value={stateFilter}>
            <option value="ALL">전체</option>
            <option value="예정">예정</option>
            <option value="진행 중">진행 중</option>
            <option value="보고서 생성됨">보고서 생성됨</option>
            <option value="완료">완료</option>
            <option value="취소">취소</option>
          </select>
        </label>
      </section>

      <section aria-labelledby="project-meetings-list-title" className="project-meetings-list-surface">
        <div className="project-meetings-list-header">
          <div>
            <p className="project-home-section-kicker">{filteredMeetings.length} results</p>
            <h2 id="project-meetings-list-title">프로젝트 회의</h2>
          </div>
          <span>{session ? "접근 권한이 확인된 회의만 표시" : "로그인이 필요합니다"}</span>
        </div>
        {filteredMeetings.length ? (
          <div className="project-meetings-list">
            {filteredMeetings.map((meeting) => (
              <Link className="project-meetings-row" key={meeting.id ?? meeting.index} to={meetingHref(selectedSpace.id, selectedSpace.name, meeting)}>
                <span className="project-meetings-row-index">{meeting.index}</span>
                <span className="project-meetings-row-copy">
                  <strong>{meeting.title}</strong>
                  <small>{meeting.date} · {meeting.description || "회의 설명 없음"}</small>
                </span>
                <span className="project-meetings-row-meta">
                  <StatusBadge context="meeting" label={meeting.state} status={meetingStatus(meeting.state)} />
                  <span>열기 →</span>
                </span>
              </Link>
            ))}
          </div>
        ) : (
          <DataState
            actionLabel={meetings.length ? "필터 초기화" : undefined}
            onAction={meetings.length ? () => { setQuery(""); setStateFilter("ALL"); } : undefined}
            state="empty"
            title={meetings.length ? "조건에 맞는 회의가 없습니다" : "아직 회의가 없습니다"}
            description={meetings.length ? "검색어나 상태를 바꾸어 다시 확인해 주세요." : "회의를 만들면 이 프로젝트의 기록 흐름이 시작됩니다."}
          />
        )}
      </section>

      {createOpen ? (
        <div className="project-meetings-dialog-backdrop" role="presentation">
          <section aria-labelledby="project-meetings-dialog-title" aria-modal="true" className="project-meetings-dialog" role="dialog">
            <div className="project-meetings-dialog-header">
              <div>
                <p className="project-home-section-kicker">New meeting</p>
                <h2 id="project-meetings-dialog-title">회의 만들기</h2>
              </div>
              <button aria-label="회의 만들기 닫기" className="project-meetings-dialog-close" onClick={() => { setCreateOpen(false); resetCreateForm(); }} type="button">×</button>
            </div>
            <form className="project-meetings-dialog-form" onSubmit={handleCreateMeeting}>
              <label>
                <span>회의 제목</span>
                <input autoFocus onChange={(event) => setNewMeetingTitle(event.target.value)} placeholder="예: 주간 제품 리뷰" required type="text" value={newMeetingTitle} />
              </label>
              <label>
                <span>설명 <small>선택</small></span>
                <textarea onChange={(event) => setNewMeetingDescription(event.target.value)} placeholder="회의 목적이나 안건을 적어주세요." rows={3} value={newMeetingDescription} />
              </label>
              <div className="project-meetings-dialog-grid">
                <label>
                  <span>시작 <small>선택</small></span>
                  <input onChange={(event) => setNewMeetingStart(event.target.value)} type="datetime-local" value={newMeetingStart} />
                </label>
                <label>
                  <span>종료 <small>선택</small></span>
                  <input onChange={(event) => setNewMeetingEnd(event.target.value)} type="datetime-local" value={newMeetingEnd} />
                </label>
              </div>
              <div className="project-meetings-dialog-actions">
                <button className="mm-common-button mm-common-button--secondary" onClick={() => { setCreateOpen(false); resetCreateForm(); }} type="button">취소</button>
                <button className="mm-common-button mm-common-button--primary" disabled={meetingMutationLoading || !newMeetingTitle.trim()} type="submit">{meetingMutationLoading ? "생성 중..." : "회의 만들기"}</button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </SpaceLayout>
  );
}
