import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { fetchCalendarEvents } from "../api/calendar";
import type { AuthSession } from "../auth/session";
import { PageHeader } from "../components/common/PageHeader";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { TargetDataGate } from "../components/layout/TargetDataGate";
import { isTargetDataReady } from "../components/layout/targetDataGateModel";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { WorkspaceDataSource } from "../app/workspaceTypes";
import type { CalendarEvent as ApiCalendarEvent, DashboardSummaryResponse, MeetingStatus, WorkspaceData } from "../types";

type CalendarView = "month" | "week" | "day";
type DisplayCalendarEvent = {
  id: string;
  spaceId: string;
  meetingId: string;
  projectName: string;
  title: string;
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
  return `/spaces/${encodeURIComponent(space.id)}`;
}

function meetingStatusLabel(status: MeetingStatus) {
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

function buildMeetingDestination(space: WorkspaceData["workspaceHome"]["spaces"][number], meeting: DisplayCalendarEvent) {
  const suffix = meeting.state === "예정" ? "/live/prejoin" : "/report";
  return `/spaces/${encodeURIComponent(space.id)}/meetings/${encodeURIComponent(meeting.meetingId)}${suffix}`;
}

function buildReportDestination(
  space: WorkspaceData["workspaceHome"]["spaces"][number],
  report: DashboardSummaryResponse["latestReports"][number]
) {
  return `/spaces/${encodeURIComponent(space.id)}/meetings/${encodeURIComponent(report.meetingId)}/report`;
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

function buildCalendarEvents(spaces: WorkspaceData["workspaceHome"]["spaces"], events: ApiCalendarEvent[]): DisplayCalendarEvent[] {
  return events.flatMap((event) => {
    const startsAt = new Date(event.startsAt);
    const space = spaces.find((candidate) => candidate.id === event.spaceId);
    if (Number.isNaN(startsAt.getTime()) || !space) {
      return [];
    }
    return [{
      id: event.id,
      spaceId: event.spaceId,
      meetingId: event.meetingId,
      projectName: space.name,
      title: event.title,
      startsAt,
      state: meetingStatusLabel(event.status)
    }];
  });
}

function calendarQueryRange(view: CalendarView, selectedDate: Date) {
  const days = buildCalendarDays(view, selectedDate);
  const first = days[0] ?? selectedDate;
  const last = days[days.length - 1] ?? selectedDate;
  return {
    from: new Date(first.getFullYear(), first.getMonth(), first.getDate()).toISOString(),
    to: new Date(last.getFullYear(), last.getMonth(), last.getDate(), 23, 59, 59, 999).toISOString()
  };
}

export function WorkspaceHomePage({
  actionItems,
  currentUserEmail,
  data,
  dataSource,
  dashboardSummary,
  latestMeetingInvites,
  meetingMutationError,
  meetingMutationLoading = false,
  onCreateMeeting,
  onCreateProject,
  projectMembers,
  session
}: {
  actionItems: WorkspaceData["meetingAi"]["actions"];
  currentUserEmail: string;
  data: WorkspaceData["workspaceHome"];
  dataSource: WorkspaceDataSource;
  dashboardSummary: DashboardSummaryResponse | null;
  latestMeetingInvites: Record<string, MeetingInviteMeta>;
  meetingMutationError?: string;
  meetingMutationLoading?: boolean;
  onCreateMeeting?: (
    projectName: string,
    payload?: { title?: string; description?: string; scheduledAt?: string; scheduledEndAt?: string; participantEmails?: string[] }
  ) => Promise<boolean>;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  projectMembers: Record<string, ProjectMemberOption[]>;
  session: AuthSession;
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
  const [meetingDescription, setMeetingDescription] = useState("");
  const [meetingSpaceId, setMeetingSpaceId] = useState(data.spaces[0]?.id ?? "");
  const [meetingDateTime, setMeetingDateTime] = useState("2026-07-10T10:00");
  const [meetingEndDateTime, setMeetingEndDateTime] = useState("2026-07-10T11:00");
  const [meetingParticipantEmails, setMeetingParticipantEmails] = useState<string[]>([]);
  const [calendarApiEvents, setCalendarApiEvents] = useState<ApiCalendarEvent[]>([]);
  const [meetingReminders, setMeetingReminders] = useState<ApiCalendarEvent[]>([]);
  const [calendarLoading, setCalendarLoading] = useState(false);
  const [calendarError, setCalendarError] = useState("");
  const [calendarReloadKey, setCalendarReloadKey] = useState(0);
  const [remindersOpen, setRemindersOpen] = useState(false);
  const totalMeetings = getTotalMeetings(data.spaces);
  const totalMembers = data.spaces.reduce((total, space) => total + parseMemberCount(space.members), 0);
  const calendarRange = useMemo(() => calendarQueryRange(calendarView, selectedDate), [calendarView, selectedDate]);
  const calendarEvents = useMemo(() => buildCalendarEvents(data.spaces, calendarApiEvents), [calendarApiEvents, data.spaces]);

  useEffect(() => {
    if (!meetingSpaceId && data.spaces[0]?.id) {
      setMeetingSpaceId(data.spaces[0].id);
    }
  }, [data.spaces, meetingSpaceId]);

  useEffect(() => {
    setMeetingParticipantEmails([]);
  }, [meetingSpaceId]);

  useEffect(() => {
    let active = true;
    setCalendarLoading(true);
    setCalendarError("");
    void fetchCalendarEvents(session, {
      from: calendarRange.from,
      to: calendarRange.to,
      spaceId: calendarSpaceId === "all" ? undefined : calendarSpaceId
    })
      .then((response) => {
        if (active) {
          setCalendarApiEvents(response.events);
        }
      })
      .catch((loadError) => {
        if (active) {
          setCalendarApiEvents([]);
          setCalendarError(loadError instanceof Error ? loadError.message : "회의 일정을 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (active) {
          setCalendarLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [calendarRange.from, calendarRange.to, calendarReloadKey, calendarSpaceId, session]);

  useEffect(() => {
    let active = true;
    const now = new Date();
    const tomorrow = new Date(now.getTime() + 24 * 60 * 60 * 1000);
    void fetchCalendarEvents(session, { from: now.toISOString(), to: tomorrow.toISOString() })
      .then((response) => {
        if (active) {
          setMeetingReminders(
            response.events
              .filter((event) => event.status === "SCHEDULED" && new Date(event.startsAt).getTime() >= now.getTime())
              .sort((left, right) => new Date(left.startsAt).getTime() - new Date(right.startsAt).getTime())
              .slice(0, 5)
          );
        }
      })
      .catch(() => {
        if (active) {
          setMeetingReminders([]);
        }
      });
    return () => {
      active = false;
    };
  }, [calendarReloadKey, session]);

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
    if (!targetSpace || !trimmedTitle || !meetingDateTime || !meetingEndDateTime || !onCreateMeeting || meetingMutationLoading) {
      return;
    }

    const created = await onCreateMeeting?.(targetSpace.name, {
      title: trimmedTitle,
      description: meetingDescription,
      scheduledAt: new Date(meetingDateTime).toISOString(),
      scheduledEndAt: new Date(meetingEndDateTime).toISOString(),
      participantEmails: meetingParticipantEmails
    });
    if (created) {
      setMeetingTitle("");
      setMeetingDescription("");
      setMeetingParticipantEmails([]);
      setCalendarReloadKey((value) => value + 1);
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
  const dashboardRecent = dashboardSummary
    ? dashboardSummary.recentActivities.map((activity) => ({
        title: activity.title,
        meta: new Date(activity.occurredAt).toLocaleString("ko-KR", { dateStyle: "short", timeStyle: "short" })
      }))
    : data.recent;
  const dashboardActionItems = dashboardSummary
    ? dashboardSummary.actionItems.map((task) => ({
        title: task.title,
        meta: task.dueDate ? `마감 ${task.dueDate}` : "마감일 미정"
      }))
    : actionItems;
  const dashboardLatestReports = dashboardSummary?.latestReports ?? [];
  const todayMeeting = dashboardSummary?.todayMeetings[0] ?? null;
  const todaySpace = todayMeeting ? data.spaces.find((space) => space.id === todayMeeting.spaceId) : null;
  const todayMeetingHref = todayMeeting && todaySpace
    ? buildMeetingDestination(todaySpace, {
        id: todayMeeting.id,
        spaceId: todayMeeting.spaceId,
        meetingId: todayMeeting.meetingId,
        projectName: todaySpace.name,
        title: todayMeeting.title,
        startsAt: new Date(todayMeeting.startsAt),
        state: meetingStatusLabel(todayMeeting.status)
      })
    : null;

  if (!isTargetDataReady(dataSource)) {
    return <TargetDataGate contentClassName="workspace-catalog-main workspace-catalog-home-main" dataSource={dataSource} onCreateProject={onCreateProject}>{null}</TargetDataGate>;
  }

  return (
    <AppShell
      contentClassName="workspace-catalog-main workspace-catalog-home-main"
      sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
    >
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
          <div className="workspace-catalog-top-actions">
            <div className="workspace-meeting-reminders">
              <button
                aria-expanded={remindersOpen}
                aria-label={`회의 알림 ${meetingReminders.length}건`}
                className="workspace-catalog-icon-button"
                onClick={() => setRemindersOpen((open) => !open)}
                title="회의 알림"
                type="button"
              >
                🔔
              </button>
              {remindersOpen ? (
                <div className="workspace-meeting-reminder-panel" role="status">
                  <strong>다가오는 회의</strong>
                  {meetingReminders.length ? meetingReminders.map((event) => (
                    <div key={`reminder-${event.id}`}>
                      <span>{formatTimeLabel(new Date(event.startsAt))}</span>
                      <span>{event.title}</span>
                    </div>
                  )) : <p>향후 24시간 내 예정 회의가 없습니다.</p>}
                </div>
              ) : null}
            </div>
          </div>
        </div>

        <PageHeader
          breadcrumb={<strong>워크스페이스</strong>}
          description="회의에서 정리된 결정과 태스크를 다음 업무로 연결합니다."
          eyebrow="Workspace"
          meta={<span className="workspace-home-count">프로젝트 {data.spaces.length}개</span>}
          title="지금 진행 중인 일을 한눈에 확인하세요"
        />

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
            <strong>{dashboardRecent.length}건</strong>
            <p>오늘 확인할 업데이트</p>
          </article>
        </section>

        <section className="workspace-dashboard-band">
          <div className="workspace-dashboard-primary">
            <div className="workspace-dashboard-section-head">
              <div>
                <span>Today</span>
                <strong>{todayMeeting?.title ?? "오늘 예정된 회의 없음"}</strong>
              </div>
              {todayMeetingHref ? <Link to={todayMeetingHref}>회의 열기</Link> : null}
            </div>
            {todayMeeting && todaySpace ? (
              <p>
                {todaySpace.name} · {formatTimeLabel(new Date(todayMeeting.startsAt))} ·
                <StatusBadge context="meeting" label={meetingStatusLabel(todayMeeting.status)} status={todayMeeting.status} />
              </p>
            ) : (
              <p>접근 가능한 오늘 회의가 없습니다.</p>
            )}
          </div>

          <div className="workspace-dashboard-activity">
            <div className="workspace-dashboard-section-head">
              <div>
                <span>Recent</span>
                <strong>최근 활동</strong>
              </div>
            </div>
            {dashboardRecent.length ? dashboardRecent.map((item) => (
              <div key={`${item.title}-${item.meta}`} className="workspace-dashboard-activity-row">
                <strong>{item.title}</strong>
                <span>{item.meta}</span>
              </div>
            )) : <p className="workspace-dashboard-empty">최근 활동이 없습니다.</p>}
          </div>
        </section>

        <section className="workspace-action-summary">
          <div className="workspace-dashboard-section-head">
            <div>
              <span>Action Items</span>
              <strong>미완료 작업 요약</strong>
            </div>
            <Link to="/spaces">프로젝트 목록</Link>
          </div>
          <div className="workspace-action-summary-list">
            {dashboardActionItems.length ? dashboardActionItems.map((item) => (
              <div key={`${item.title}-${item.meta}`} className="workspace-action-summary-row">
                <strong>{item.title}</strong>
                <span>{item.meta}</span>
              </div>
            )) : <p className="workspace-action-summary-empty">현재 미완료 작업이 없습니다.</p>}
          </div>
        </section>

        <section className="workspace-action-summary">
          <div className="workspace-dashboard-section-head">
            <div>
              <span>Latest Reports</span>
              <strong>최신 확정 회의록</strong>
            </div>
          </div>
          <div className="workspace-action-summary-list">
            {dashboardLatestReports.length ? dashboardLatestReports.map((report) => {
              const space = data.spaces.find((candidate) => candidate.id === report.spaceId);
              const content = <>
                <strong>{report.title}</strong>
                <span>{report.meetingTitle} · v{report.version} · {new Date(report.confirmedAt).toLocaleDateString("ko-KR")}</span>
              </>;
              return space ? (
                <Link key={report.id} className="workspace-action-summary-row" to={buildReportDestination(space, report)}>
                  {content}
                </Link>
              ) : (
                <div key={report.id} className="workspace-action-summary-row">{content}</div>
              );
            }) : <p className="workspace-action-summary-empty">확정된 회의록이 없습니다.</p>}
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

        {filteredSpaces.length ? (
          <section className="workspace-catalog-grid" aria-label="프로젝트 목록">
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
        ) : (
          <section className="workspace-catalog-empty" aria-live="polite">
            <strong>조건에 맞는 프로젝트가 없습니다.</strong>
            <p>검색어 또는 필터를 바꾸면 다른 프로젝트를 확인할 수 있습니다.</p>
            <button
              onClick={() => {
                setSearchQuery("");
                setFilterBy("전체");
                setSortBy("recent");
              }}
              type="button"
            >
              필터 초기화
            </button>
          </section>
        )}

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
              onChange={(event) => {
                const nextDate = new Date(event.target.value);
                if (!Number.isNaN(nextDate.getTime())) {
                  setSelectedDate(nextDate);
                }
              }}
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
                      return space ? (
                        <Link key={event.id} to={buildMeetingDestination(space, event)}>
                          <strong>{formatTimeLabel(event.startsAt)} {event.title}</strong>
                          <small>
                            {event.projectName} ·
                            <StatusBadge context="meeting" label={event.state} status={event.state} />
                          </small>
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

          {calendarLoading ? <p className="workspace-calendar-status">회의 일정을 불러오는 중입니다.</p> : null}
          {calendarError ? <p className="workspace-calendar-status error" role="alert">{calendarError}</p> : null}

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
              <input
                aria-label="회의 종료 일시"
                disabled={!canCreateCalendarMeeting || meetingMutationLoading}
                onChange={(event) => setMeetingEndDateTime(event.target.value)}
                type="datetime-local"
                value={meetingEndDateTime}
              />
              <textarea
                aria-label="회의 설명"
                disabled={!canCreateCalendarMeeting || meetingMutationLoading}
                onChange={(event) => setMeetingDescription(event.target.value)}
                placeholder="회의 설명"
                value={meetingDescription}
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
              <button disabled={!canCreateCalendarMeeting || meetingMutationLoading || !meetingTitle.trim() || !meetingSpaceId || !meetingDateTime || !meetingEndDateTime} type="submit">
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
    </AppShell>
  );
}
