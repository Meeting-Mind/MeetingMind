import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { WorkspaceData } from "../types";

type ProjectMeeting = WorkspaceData["projectOverview"]["meetings"][number];
type WorkspaceDataSource = "workspace-api" | "workspace-api-partial" | "legacy-api" | "mock-fallback";
type CalendarView = "month" | "week" | "day";
type CalendarEvent = {
  id: string;
  spaceId: string;
  meetingId?: string;
  projectName: string;
  title: string;
  round: string;
  startsAt: Date;
  state: string;
};
type ProjectMemberOption = {
  email: string;
  name: string;
  spaceRole: "OWNER" | "ADMIN" | "MEMBER";
  status: "active" | "away";
};
type MeetingInviteMeta = {
  meetingId: string;
  title: string;
  joinCode: string;
  joinUrl: string;
};

const REFERENCE_DATE = new Date(2026, 6, 10);
const dashboardFilters = ["전체", "활성 프로젝트", "최근 업데이트"] as const;

function getTotalMeetings(spaces: WorkspaceData["workspaceHome"]["spaces"]) {
  return spaces.reduce((total, space) => {
    const match = space.meetings.match(/\d+/);
    return total + Number(match?.[0] ?? 0);
  }, 0);
}

function parseMemberCount(value: string) {
  const match = value.match(/\d+/);
  return Number(match?.[0] ?? 0);
}

function parseUpdatedRank(value: string) {
  if (value.includes("방금") || value.includes("오늘")) {
    return 300;
  }
  if (value.includes("어제")) {
    return 200;
  }

  const match = value.match(/(\d{2})\.(\d{2})/);
  if (!match) {
    return 0;
  }

  return Number(match[1]) * 100 + Number(match[2]);
}

function buildProjectOverviewHref(space: WorkspaceData["workspaceHome"]["spaces"][number]) {
  const params = new URLSearchParams({
    spaceId: space.id,
    project: space.name
  });

  return `/project-overview?${params.toString()}`;
}

function buildMeetingDestination(space: WorkspaceData["workspaceHome"]["spaces"][number], meeting: ProjectMeeting) {
  const path = meeting.state === "예정" ? "/live-meeting" : "/report-agent";
  const params = new URLSearchParams({
    spaceId: space.id,
    project: space.name,
    meetingId: meeting.id ?? meeting.index,
    meeting: meeting.title,
    round: meeting.index.replace("#", "")
  });
  if (meeting.id) {
    params.set("meetingId", meeting.id);
  }

  return `${path}?${params.toString()}`;
}

function parseMeetingDate(meeting: ProjectMeeting, fallbackIndex: number) {
  if (meeting.scheduledAt) {
    return new Date(meeting.scheduledAt);
  }

  const [month, day] = meeting.date.split(".").map((value) => Number(value));
  if (!month || !day) {
    return new Date(2026, 6, 10 + fallbackIndex);
  }

  return new Date(2026, month - 1, day, 10 + (fallbackIndex % 5), 0);
}

function formatDateLabel(date: Date) {
  return `${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`;
}

function formatTimeLabel(date: Date) {
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function sameDate(left: Date, right: Date) {
  return left.getFullYear() === right.getFullYear() && left.getMonth() === right.getMonth() && left.getDate() === right.getDate();
}

function getWeekStart(date: Date) {
  const next = new Date(date);
  next.setDate(date.getDate() - date.getDay());
  return next;
}

function buildCalendarDays(view: CalendarView, selectedDate: Date) {
  if (view === "day") {
    return [selectedDate];
  }

  if (view === "week") {
    const weekStart = getWeekStart(selectedDate);
    return Array.from({ length: 7 }, (_, index) => {
      const next = new Date(weekStart);
      next.setDate(weekStart.getDate() + index);
      return next;
    });
  }

  const monthStart = new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1);
  const daysInMonth = new Date(selectedDate.getFullYear(), selectedDate.getMonth() + 1, 0).getDate();
  return Array.from({ length: daysInMonth }, (_, index) => new Date(monthStart.getFullYear(), monthStart.getMonth(), index + 1));
}

function buildCalendarEvents(
  spaces: WorkspaceData["workspaceHome"]["spaces"],
  projectMeetings: Record<string, ProjectMeeting[]>
) {
  return spaces.flatMap((space) =>
    (projectMeetings[space.name] ?? []).map((meeting, index) => ({
      id: meeting.id ?? `${space.id}-${meeting.index}`,
      spaceId: space.id,
      meetingId: meeting.id,
      projectName: space.name,
      title: meeting.title,
      round: meeting.index.replace("#", ""),
      startsAt: parseMeetingDate(meeting, index),
      state: meeting.state
    }))
  );
}

export function WorkspaceHomePage({
  actionItems,
  currentUserEmail,
  data,
  dataSource,
  latestMeetingInvites,
  meetingMutationError,
  meetingMutationLoading = false,
  onCreateMeeting,
  onCreateProject,
  projectMembers,
  projectMeetings
}: {
  actionItems: WorkspaceData["meetingAi"]["actions"];
  currentUserEmail: string;
  data: WorkspaceData["workspaceHome"];
  dataSource: WorkspaceDataSource;
  latestMeetingInvites: Record<string, MeetingInviteMeta>;
  meetingMutationError?: string;
  meetingMutationLoading?: boolean;
  onCreateMeeting?: (
    projectName: string,
    payload?: { title?: string; scheduledAt?: string; participantEmails?: string[] }
  ) => Promise<boolean>;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  projectMembers: Record<string, ProjectMemberOption[]>;
  projectMeetings: Record<string, ProjectMeeting[]>;
}) {
  useEffect(() => {
    document.body.className = "app-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  const [searchQuery, setSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState<"recent" | "name" | "members">("recent");
  const [filterBy, setFilterBy] = useState<(typeof dashboardFilters)[number]>("전체");
  const [calendarView, setCalendarView] = useState<CalendarView>("month");
  const [selectedDate, setSelectedDate] = useState(REFERENCE_DATE);
  const [calendarSpaceId, setCalendarSpaceId] = useState("all");
  const [meetingTitle, setMeetingTitle] = useState("");
  const [meetingSpaceId, setMeetingSpaceId] = useState(data.spaces[0]?.id ?? "");
  const [meetingDateTime, setMeetingDateTime] = useState("2026-07-10T10:00");
  const [meetingParticipantEmails, setMeetingParticipantEmails] = useState<string[]>([]);
  const totalMeetings = getTotalMeetings(data.spaces);
  const totalMembers = data.spaces.reduce((total, space) => total + parseMemberCount(space.members), 0);
  const calendarEvents = useMemo(() => buildCalendarEvents(data.spaces, projectMeetings), [data.spaces, projectMeetings]);

  useEffect(() => {
    if (!meetingSpaceId && data.spaces[0]?.id) {
      setMeetingSpaceId(data.spaces[0].id);
    }
  }, [data.spaces, meetingSpaceId]);

  useEffect(() => {
    setMeetingParticipantEmails([]);
  }, [meetingSpaceId]);

  const filteredSpaces = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase();
    const searched = normalizedQuery
      ? data.spaces.filter((space) =>
          `${space.name} ${space.description} ${space.members} ${space.meetings} ${space.updatedAt}`.toLowerCase().includes(normalizedQuery)
        )
      : data.spaces;

    const filtered =
      filterBy === "활성 프로젝트"
        ? searched.filter((space) => parseMemberCount(space.members) > 0)
        : filterBy === "최근 업데이트"
          ? searched.filter((space) => parseUpdatedRank(space.updatedAt) >= 200)
          : searched;

    const next = [...filtered];

    if (sortBy === "name") {
      next.sort((a, b) => a.name.localeCompare(b.name, "ko"));
    } else if (sortBy === "members") {
      next.sort((a, b) => parseMemberCount(b.members) - parseMemberCount(a.members));
    } else {
      next.sort((a, b) => parseUpdatedRank(b.updatedAt) - parseUpdatedRank(a.updatedAt));
    }

    return next;
  }, [data.spaces, filterBy, searchQuery, sortBy]);

  const visibleEvents = useMemo(
    () => calendarEvents.filter((event) => calendarSpaceId === "all" || event.spaceId === calendarSpaceId),
    [calendarEvents, calendarSpaceId]
  );
  const upcomingEvents = useMemo(
    () =>
      [...visibleEvents]
        .filter((event) => event.startsAt >= REFERENCE_DATE)
        .sort((left, right) => left.startsAt.getTime() - right.startsAt.getTime())
        .slice(0, 4),
    [visibleEvents]
  );
  const calendarDays = buildCalendarDays(calendarView, selectedDate);

  async function handleCreateCalendarMeeting(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const targetSpace = data.spaces.find((space) => space.id === meetingSpaceId);
    const trimmedTitle = meetingTitle.trim();
    if (!targetSpace || !trimmedTitle || !meetingDateTime || !onCreateMeeting || meetingMutationLoading) {
      return;
    }

    const created = await onCreateMeeting?.(targetSpace.name, {
      title: trimmedTitle,
      scheduledAt: new Date(meetingDateTime).toISOString(),
      participantEmails: meetingParticipantEmails
    });
    if (created) {
      setMeetingTitle("");
      setMeetingParticipantEmails([]);
    }
  }

  const calendarMeetingSpace = data.spaces.find((space) => space.id === meetingSpaceId) ?? null;
  const calendarMeetingCandidates = calendarMeetingSpace
    ? (projectMembers[calendarMeetingSpace.name] ?? []).filter(
        (member) => member.email !== currentUserEmail && member.status === "active"
      )
    : [];
  const calendarCurrentMember = calendarMeetingSpace
    ? (projectMembers[calendarMeetingSpace.name] ?? []).find((member) => member.email === currentUserEmail)
    : null;
  const canCreateCalendarMeeting =
    calendarCurrentMember?.spaceRole === "OWNER" || calendarCurrentMember?.spaceRole === "ADMIN";
  const latestMeetingInvite = calendarMeetingSpace
    ? latestMeetingInvites[calendarMeetingSpace.name] ?? null
    : null;

  return (
    <div className="workspace-catalog-shell workspace-catalog-home-shell">
      <WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />

      <main className="workspace-catalog-main workspace-catalog-home-main">
        <div className="workspace-catalog-topbar">
          <div className="workspace-catalog-data-source" title="개발용 데이터 소스">
            {dataSource === "workspace-api"
              ? "Workspace API"
              : dataSource === "workspace-api-partial"
                ? "Workspace API partial"
                : dataSource === "legacy-api"
                  ? "API snapshot"
                  : "Mock fallback"}
          </div>
          <div className="workspace-catalog-top-actions" aria-hidden="true">
            <button className="workspace-catalog-icon-button">🔔</button>
          </div>
        </div>

        <section className="dashboard-summary-grid">
          <article>
            <span>프로젝트</span>
            <strong>{data.spaces.length}개</strong>
            <p>참여 중인 Space 기준</p>
          </article>
          <article>
            <span>회의</span>
            <strong>{totalMeetings}건</strong>
            <p>접근 가능한 일정</p>
          </article>
          <article>
            <span>멤버</span>
            <strong>{totalMembers}명</strong>
            <p>프로젝트 멤버 합산</p>
          </article>
          <article>
            <span>최근 활동</span>
            <strong>{data.recent.length}건</strong>
            <p>오늘 확인할 업데이트</p>
          </article>
        </section>

        <section className="workspace-dashboard-band">
          <div className="workspace-dashboard-primary">
            <div className="workspace-dashboard-section-head">
              <div>
                <span>Today</span>
                <strong>{data.todayMeeting.title}</strong>
              </div>
              <Link to={data.todayMeeting.href}>회의 대기실</Link>
            </div>
            <p>{data.todayMeeting.project} · {data.todayMeeting.time} · {data.todayMeeting.attendees}</p>
            <p>{data.todayMeeting.note}</p>
          </div>

          <div className="workspace-dashboard-activity">
            <div className="workspace-dashboard-section-head">
              <div>
                <span>Recent</span>
                <strong>최근 활동</strong>
              </div>
            </div>
            {data.recent.map((item) => (
              <div key={`${item.title}-${item.meta}`} className="workspace-dashboard-activity-row">
                <strong>{item.title}</strong>
                <span>{item.meta}</span>
              </div>
            ))}
          </div>
        </section>

        <section className="workspace-action-summary">
          <div className="workspace-dashboard-section-head">
            <div>
              <span>Action Items</span>
              <strong>미완료 작업 요약</strong>
            </div>
            <Link to="/project-overview">프로젝트 개요</Link>
          </div>
          <div className="workspace-action-summary-list">
            {actionItems.map((item) => (
              <div key={`${item.title}-${item.meta}`} className="workspace-action-summary-row">
                <strong>{item.title}</strong>
                <span>{item.meta}</span>
              </div>
            ))}
          </div>
        </section>

        <section className="workspace-catalog-controls">
          <div className="workspace-catalog-search">
            <span className="workspace-catalog-search-icon">⌕</span>
            <input
              aria-label="프로젝트 검색"
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="프로젝트명, 설명, 업데이트로 검색"
              type="text"
              value={searchQuery}
            />
          </div>

          <div className="workspace-catalog-filters">
            <span className="workspace-catalog-filter-label">정렬:</span>
            <select
              aria-label="정렬 기준"
              className="workspace-catalog-sort workspace-catalog-sort-select"
              onChange={(event) => setSortBy(event.target.value as "recent" | "name" | "members")}
              value={sortBy}
            >
              <option value="recent">최근 업데이트순</option>
              <option value="name">이름순</option>
              <option value="members">멤버 많은순</option>
            </select>
            <select
              aria-label="프로젝트 필터"
              className="workspace-catalog-sort workspace-catalog-sort-select"
              onChange={(event) => setFilterBy(event.target.value as (typeof dashboardFilters)[number])}
              value={filterBy}
            >
              {dashboardFilters.map((filter) => (
                <option key={filter} value={filter}>{filter}</option>
              ))}
            </select>
          </div>
        </section>

        <div className="workspace-catalog-heading">
          <strong>프로젝트 {filteredSpaces.length}개</strong>
        </div>

        <section className="workspace-catalog-grid">
          {filteredSpaces.map((space, index) => (
            <Link key={space.id} className="workspace-catalog-card" to={buildProjectOverviewHref(space)}>
              <div className="workspace-catalog-card-time">
                <span className="workspace-catalog-time-dot" />
                <strong>{space.updatedAt}</strong>
              </div>

              <h2>{space.name}</h2>
              <div className={`workspace-catalog-tag tone-${(index % 3) + 1}`}>{space.meetings}</div>
              <p>{space.description}</p>

              <div className="workspace-catalog-card-footer">
                <div className="workspace-catalog-avatars" aria-hidden="true">
                  <span className={`workspace-catalog-avatar tone-${(index % 3) + 1}`}>{space.name.slice(0, 1)}</span>
                  <strong>{space.members}</strong>
                </div>
                <span className="workspace-catalog-date">Project AI</span>
              </div>
            </Link>
          ))}
        </section>

        <section className="workspace-calendar-shell">
          <div className="workspace-dashboard-section-head">
            <div>
              <span>Calendar</span>
              <strong>회의 일정</strong>
            </div>
            <div className="workspace-calendar-view-tabs">
              {(["month", "week", "day"] as CalendarView[]).map((view) => (
                <button key={view} className={calendarView === view ? "active" : ""} onClick={() => setCalendarView(view)} type="button">
                  {view === "month" ? "월" : view === "week" ? "주" : "일"}
                </button>
              ))}
            </div>
          </div>

          <div className="workspace-calendar-toolbar">
            <select
              aria-label="캘린더 프로젝트 필터"
              onChange={(event) => setCalendarSpaceId(event.target.value)}
              value={calendarSpaceId}
            >
              <option value="all">전체 프로젝트</option>
              {data.spaces.map((space) => (
                <option key={space.id} value={space.id}>{space.name}</option>
              ))}
            </select>
            <input
              aria-label="캘린더 기준 날짜"
              onChange={(event) => setSelectedDate(new Date(event.target.value))}
              type="date"
              value={`${selectedDate.getFullYear()}-${String(selectedDate.getMonth() + 1).padStart(2, "0")}-${String(selectedDate.getDate()).padStart(2, "0")}`}
            />
          </div>

          <div className={`workspace-calendar-grid ${calendarView}`}>
            {calendarDays.map((day) => {
              const dayEvents = visibleEvents.filter((event) => sameDate(event.startsAt, day));
              return (
                <div key={day.toISOString()} className={`workspace-calendar-day ${sameDate(day, REFERENCE_DATE) ? "today" : ""}`}>
                  <span>{formatDateLabel(day)}</span>
                  {dayEvents.length ? (
                    dayEvents.map((event) => {
                      const space = data.spaces.find((item) => item.id === event.spaceId);
                      const meeting = projectMeetings[event.projectName]?.find((item) => (item.id ?? `${event.spaceId}-${item.index}`) === event.id);
                      return space && meeting ? (
                        <Link key={event.id} to={buildMeetingDestination(space, meeting)}>
                          <strong>{formatTimeLabel(event.startsAt)} {event.title}</strong>
                          <small>{event.projectName} · {event.state}</small>
                        </Link>
                      ) : null;
                    })
                  ) : (
                    <em>일정 없음</em>
                  )}
                </div>
              );
            })}
          </div>

          <div className="workspace-calendar-bottom">
            <div className="workspace-calendar-upcoming">
              <strong>다가오는 회의</strong>
              {upcomingEvents.length ? (
                upcomingEvents.map((event) => (
                  <div key={`upcoming-${event.id}`} className="workspace-calendar-upcoming-row">
                    <span>{formatDateLabel(event.startsAt)} {formatTimeLabel(event.startsAt)}</span>
                    <strong>{event.projectName} · {event.title}</strong>
                  </div>
                ))
              ) : (
                <p>선택한 범위에 예정 회의가 없습니다.</p>
              )}
            </div>

            <form className="workspace-calendar-create" onSubmit={handleCreateCalendarMeeting}>
              <strong>회의 일정 생성</strong>
              <select
                aria-label="회의 생성 프로젝트"
                disabled={meetingMutationLoading}
                onChange={(event) => setMeetingSpaceId(event.target.value)}
                value={meetingSpaceId}
              >
                {data.spaces.map((space) => (
                  <option key={space.id} value={space.id}>{space.name}</option>
                ))}
              </select>
              <input
                aria-label="회의 제목"
                disabled={!canCreateCalendarMeeting || meetingMutationLoading}
                onChange={(event) => setMeetingTitle(event.target.value)}
                placeholder="회의 제목"
                type="text"
                value={meetingTitle}
              />
              <input
                aria-label="회의 시작 일시"
                disabled={!canCreateCalendarMeeting || meetingMutationLoading}
                onChange={(event) => setMeetingDateTime(event.target.value)}
                type="datetime-local"
                value={meetingDateTime}
              />
              <select
                aria-label="회의 초기 참여자"
                disabled={!canCreateCalendarMeeting || meetingMutationLoading}
                multiple
                onChange={(event) =>
                  setMeetingParticipantEmails(
                    Array.from(event.currentTarget.selectedOptions, (option) => option.value)
                  )
                }
                value={meetingParticipantEmails}
              >
                {calendarMeetingCandidates.map((member) => (
                  <option key={`calendar-member-${member.email}`} value={member.email}>{member.name}</option>
                ))}
              </select>
              <button disabled={!canCreateCalendarMeeting || meetingMutationLoading || !meetingTitle.trim() || !meetingSpaceId || !meetingDateTime} type="submit">
                {meetingMutationLoading ? "저장 중" : "일정 추가"}
              </button>
              {meetingMutationError ? <div className="meeting-ai-error">{meetingMutationError}</div> : null}
              {canCreateCalendarMeeting && latestMeetingInvite ? (
                <div className="workspace-calendar-invite-result">
                  <strong>{latestMeetingInvite.title} 참가 정보</strong>
                  <label>
                    <span>회의 참가 코드</span>
                    <input aria-label="캘린더 회의 참가 코드" readOnly value={latestMeetingInvite.joinCode} />
                  </label>
                  <button
                    onClick={() => void navigator.clipboard.writeText(latestMeetingInvite.joinCode)}
                    type="button"
                  >
                    코드 복사
                  </button>
                </div>
              ) : null}
            </form>
          </div>
        </section>

        <section className="workspace-catalog-footer">
          <span>
            검색 결과 {filteredSpaces.length}개 · 회의 {totalMeetings}건
          </span>
        </section>
      </main>
    </div>
  );
}
