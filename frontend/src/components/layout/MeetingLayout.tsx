import type { ReactNode } from "react";
import { Link, NavLink } from "react-router-dom";
import { RoleBadge } from "../common/RoleBadge";
import { StatusBadge } from "../common/StatusBadge";
import { AppShell } from "./AppShell";
import { WorkspaceSidebar } from "../WorkspaceSidebar";
import type { MeetingDetailResponse, SpaceRole } from "../../types";
import type { WorkspaceDataSource } from "../../app/workspaceTypes";
import { TargetDataGate } from "./TargetDataGate";

type MeetingNavItem = "overview" | "preparation" | "live" | "transcript" | "tasks" | "report" | "ai";

function meetingBaseHref(spaceId: string, meetingId: string) {
  return `/spaces/${encodeURIComponent(spaceId)}/meetings/${encodeURIComponent(meetingId)}`;
}

function meetingStatusLabel(status: MeetingDetailResponse["status"]) {
  if (status === "IN_PROGRESS") {
    return "진행 중인 회의";
  }
  if (status === "ENDED") {
    return "종료된 회의";
  }
  if (status === "CANCELED") {
    return "취소된 회의";
  }
  return "예정된 회의";
}

export function MeetingLayout({
  children,
  meeting,
  meetingId,
  projectName,
  spaceId,
  spaceRole,
  activeItem = "overview",
  onCreateProject,
  dataSource
}: {
  children: ReactNode;
  meeting: MeetingDetailResponse | null;
  meetingId: string;
  projectName: string;
  spaceId: string;
  spaceRole?: SpaceRole | null;
  activeItem?: MeetingNavItem;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  dataSource?: WorkspaceDataSource;
}) {
  const baseHref = meetingBaseHref(spaceId, meeting?.id ?? meetingId);
  const liveHref = `${baseHref}/live`;
  const prejoinHref = `${baseHref}/live/prejoin`;
  const reportHref = `${baseHref}/report`;
  const aiHref = `${baseHref}/ai`;
  const primaryHref = meeting?.status === "IN_PROGRESS" ? liveHref : prejoinHref;
  const primaryLabel = meeting?.status === "IN_PROGRESS" ? "회의로 돌아가기" : "회의 준비하기";

  return (
    <TargetDataGate contentClassName="meeting-layout-main" dataSource={dataSource} onCreateProject={onCreateProject}>
      <AppShell
        contentClassName="meeting-layout-main"
        sidebar={(
          <WorkspaceSidebar
            activeItem="meetings"
            contextOverride={projectName}
            mode="project"
            onCreateProject={onCreateProject}
            projectName={projectName}
            spaceId={spaceId}
          />
        )}
      >
        <div className="meeting-layout-shell">
        <header className="meeting-layout-header">
          <div className="meeting-layout-breadcrumbs" aria-label="현재 위치">
            <Link to={`/spaces/${encodeURIComponent(spaceId)}`}>{projectName}</Link>
            <span aria-hidden="true">/</span>
            <Link to={`/spaces/${encodeURIComponent(spaceId)}/meetings`}>회의</Link>
            <span aria-hidden="true">/</span>
            <strong>{meeting?.title ?? "회의"}</strong>
          </div>
          <div className="meeting-layout-title-row">
            <div>
              <p className="meeting-layout-kicker">Meeting workspace</p>
              <h1>{meeting?.title ?? "회의 정보를 불러오는 중"}</h1>
              <p className="meeting-layout-description">{meeting?.description || "회의 권한과 일정을 확인하고 있습니다."}</p>
            </div>
            <div className="meeting-layout-header-meta">
              {meeting ? <StatusBadge context="meeting" status={meeting.status} /> : null}
              {spaceRole ? <RoleBadge role={spaceRole} scope="space" /> : null}
              {meeting?.myRole ? <RoleBadge role={meeting.myRole} scope="meeting" /> : null}
            </div>
          </div>
          {meeting && meeting.status !== "CANCELED" ? (
            <div className="meeting-layout-actions">
              <Link className="mm-common-button mm-common-button--primary" to={primaryHref}>{primaryLabel}</Link>
              <Link className="mm-common-button mm-common-button--secondary" to={reportHref}>회의록 보기</Link>
            </div>
          ) : null}
        </header>

        <nav aria-label="회의 메뉴" className="meeting-layout-nav">
          <NavLink className={`meeting-layout-nav-item ${activeItem === "overview" ? "active" : ""}`} end to={baseHref}>개요</NavLink>
          <NavLink className={`meeting-layout-nav-item ${activeItem === "preparation" ? "active" : ""}`} to={prejoinHref}>준비</NavLink>
          <NavLink className={`meeting-layout-nav-item ${activeItem === "live" ? "active" : ""}`} to={liveHref}>실시간</NavLink>
          <NavLink className={`meeting-layout-nav-item ${activeItem === "transcript" ? "active" : ""}`} to={`${baseHref}/transcript`}>Transcript</NavLink>
          <NavLink className={`meeting-layout-nav-item ${activeItem === "report" ? "active" : ""}`} to={reportHref}>회의록</NavLink>
          <NavLink className={`meeting-layout-nav-item ${activeItem === "tasks" ? "active" : ""}`} to={`${baseHref}/tasks`}>태스크 후보</NavLink>
          <NavLink className={`meeting-layout-nav-item ${activeItem === "ai" ? "active" : ""}`} to={aiHref}>Meeting AI</NavLink>
        </nav>

        {meeting ? (
          <div className="meeting-layout-status-line">
            <span>{meetingStatusLabel(meeting.status)}</span>
            <span>회의 권한과 기록 범위는 이 회의 안에서만 적용됩니다.</span>
          </div>
        ) : null}
        {children}
        </div>
      </AppShell>
    </TargetDataGate>
  );
}
