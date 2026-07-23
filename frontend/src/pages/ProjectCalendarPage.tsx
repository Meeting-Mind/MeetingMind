import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { fetchCalendarEvents } from "../api/calendar";
import type { AuthSession } from "../auth/session";
import { DataState } from "../components/common/DataState";
import { PageHeader } from "../components/common/PageHeader";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { SpaceLayout } from "../components/layout/SpaceLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { CalendarEvent, MeetingStatus, WorkspaceData } from "../types";
import type { TeamMember, WorkspaceDataSource } from "../app/workspaceTypes";

const DAY_MS = 24 * 60 * 60 * 1000;

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function buildMonthDays(date: Date) {
  const first = startOfMonth(date);
  const gridStart = new Date(first);
  gridStart.setDate(first.getDate() - first.getDay());
  return Array.from({ length: 42 }, (_, index) => new Date(gridStart.getTime() + index * DAY_MS));
}

function dateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function eventDateKey(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : dateKey(date);
}

function formatMonth(date: Date) {
  return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long" }).format(date);
}

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "시간 미정";
  }
  return new Intl.DateTimeFormat("ko-KR", { hour: "2-digit", minute: "2-digit", hour12: false }).format(date);
}

function meetingStatus(status: MeetingStatus) {
  if (status === "IN_PROGRESS") {
    return { label: "진행 중", value: "IN_PROGRESS" as const };
  }
  if (status === "ENDED") {
    return { label: "완료", value: "COMPLETED" as const };
  }
  if (status === "CANCELED") {
    return { label: "취소", value: "CANCELED" as const };
  }
  return { label: "예정", value: "SCHEDULED" as const };
}

export function ProjectCalendarPage({
  currentUserEmail,
  projectMembers,
  session,
  spaces,
  workspaceDataSource,
  onCreateProject
}: {
  currentUserEmail: string;
  projectMembers: Record<string, TeamMember[]>;
  session: AuthSession;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
}) {
  const navigate = useNavigate();
  const { spaceId = "" } = useParams<{ spaceId: string }>();
  const selectedSpace = spaces.find((space) => space.id === spaceId);
  const [selectedDate, setSelectedDate] = useState(() => new Date());
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState("");
  const monthDays = useMemo(() => buildMonthDays(selectedDate), [selectedDate]);
  const currentMember = selectedSpace ? (projectMembers[selectedSpace.name] ?? []).find((member) => member.email === currentUserEmail) : null;

  useEffect(() => {
    document.body.className = "app-theme project-calendar-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  useEffect(() => {
    if (!selectedSpace) {
      return;
    }

    const from = monthDays[0]?.toISOString() ?? selectedDate.toISOString();
    const lastDay = monthDays[monthDays.length - 1] ?? selectedDate;
    const to = new Date(lastDay.getTime() + DAY_MS).toISOString();
    let active = true;
    setStatus("loading");
    setError("");

    void fetchCalendarEvents(session, { from, to, spaceId: selectedSpace.id })
      .then((response) => {
        if (!active) {
          return;
        }
        setEvents(response.events);
        setStatus("ready");
      })
      .catch((cause: unknown) => {
        if (!active) {
          return;
        }
        setEvents([]);
        setError(cause instanceof Error ? cause.message : "캘린더를 불러오지 못했습니다.");
        setStatus("error");
      });

    return () => {
      active = false;
    };
  }, [monthDays, selectedDate, selectedSpace, session]);

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="project-calendar-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState
          actionLabel="프로젝트 목록으로"
          onAction={() => navigate("/spaces")}
          state="notFound"
          title="프로젝트를 찾을 수 없습니다"
          description="프로젝트 목록에서 접근 가능한 프로젝트를 선택해 주세요."
        />
      </AppShell>
    );
  }

  const eventsByDate = new Map<string, CalendarEvent[]>();
  events.forEach((event) => {
    const key = eventDateKey(event.startsAt);
    eventsByDate.set(key, [...(eventsByDate.get(key) ?? []), event]);
  });

  function shiftMonth(offset: number) {
    setSelectedDate((current) => new Date(current.getFullYear(), current.getMonth() + offset, 1));
  }

  return (
    <SpaceLayout
      activeItem="calendar"
      contentClassName="project-calendar-main"
      dataSource={workspaceDataSource}
      onCreateProject={onCreateProject}
      projectName={selectedSpace.name}
      spaceId={selectedSpace.id}
    >
      <PageHeader
        actions={(
          <Link className="mm-common-button mm-common-button--secondary" to={`/spaces/${encodeURIComponent(selectedSpace.id)}/meetings`}>회의 목록</Link>
        )}
        breadcrumb={(
          <>
            <Link to="/spaces">프로젝트 목록</Link>
            <span aria-hidden="true">/</span>
            <Link to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>{selectedSpace.name}</Link>
            <span aria-hidden="true">/</span>
            <strong>캘린더</strong>
          </>
        )}
        description="접근 권한이 확인된 회의 일정만 프로젝트 시간 흐름에 맞춰 표시합니다."
        eyebrow="Schedule"
        meta={currentMember ? <RoleBadge role={currentMember.spaceRole} scope="space" /> : null}
        title="프로젝트 캘린더"
      />

      <section aria-label="캘린더 도구" className="project-calendar-toolbar">
        <button aria-label="이전 달" className="mm-common-button mm-common-button--secondary" onClick={() => shiftMonth(-1)} type="button">이전</button>
        <strong aria-live="polite">{formatMonth(selectedDate)}</strong>
        <button aria-label="다음 달" className="mm-common-button mm-common-button--secondary" onClick={() => shiftMonth(1)} type="button">다음</button>
        <button className="mm-common-button mm-common-button--secondary" onClick={() => setSelectedDate(new Date())} type="button">오늘</button>
      </section>

      {status === "error" ? (
        <DataState
          actionLabel="다시 시도"
          onAction={() => setSelectedDate((current) => new Date(current))}
          state="error"
          title="캘린더를 불러오지 못했습니다"
          description={error}
        />
      ) : status === "loading" ? (
        <DataState state="loading" title="일정을 불러오는 중입니다" description="프로젝트 회의 일정을 확인하고 있습니다." />
      ) : (
        <section aria-label={`${formatMonth(selectedDate)} 회의 일정`} className="project-calendar-surface">
          <div className="project-calendar-weekdays" aria-hidden="true">
            {['일', '월', '화', '수', '목', '금', '토'].map((day) => <span key={day}>{day}</span>)}
          </div>
          <div className="project-calendar-grid">
            {monthDays.map((day) => {
              const dayEvents = eventsByDate.get(dateKey(day)) ?? [];
              const isCurrentMonth = day.getMonth() === selectedDate.getMonth();
              return (
                <div className={`project-calendar-day ${isCurrentMonth ? "" : "is-outside"}`.trim()} key={dateKey(day)}>
                  <time dateTime={dateKey(day)}>{day.getDate()}</time>
                  <div className="project-calendar-day-events">
                    {dayEvents.slice(0, 3).map((event) => {
                      const statusMeta = meetingStatus(event.status);
                      return (
                        <Link className="project-calendar-event" key={event.id} to={`/spaces/${encodeURIComponent(selectedSpace.id)}/meetings/${encodeURIComponent(event.meetingId)}`}>
                          <span>{formatTime(event.startsAt)}</span>
                          <strong>{event.title}</strong>
                          <StatusBadge context="meeting" label={statusMeta.label} status={statusMeta.value} />
                        </Link>
                      );
                    })}
                    {dayEvents.length > 3 ? <span className="project-calendar-more">+{dayEvents.length - 3}개 더 있음</span> : null}
                  </div>
                </div>
              );
            })}
          </div>
          {!events.length ? <DataState state="empty" title="표시할 회의가 없습니다" description="이 프로젝트에 접근 가능한 회의 일정이 생기면 이곳에 표시됩니다." /> : null}
        </section>
      )}
    </SpaceLayout>
  );
}
