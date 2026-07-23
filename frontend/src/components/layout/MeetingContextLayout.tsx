import type { ReactNode } from "react";
import { useNavigate, useParams } from "react-router-dom";
import type { AuthSession } from "../../auth/session";
import type { TeamMember, WorkspaceDataSource } from "../../app/workspaceTypes";
import { DataState } from "../common/DataState";
import { AppShell } from "./AppShell";
import { MeetingLayout } from "./MeetingLayout";
import { WorkspaceSidebar } from "../WorkspaceSidebar";
import { useMeetingContext } from "../../hooks/useMeetingContext";
import type { MeetingDetailResponse, WorkspaceData } from "../../types";

type MeetingSurface = "report" | "ai";

export function MeetingContextLayout({
  activeItem,
  children,
  currentUserEmail,
  onCreateProject,
  projectMembers,
  session,
  spaces,
  workspaceDataSource
}: {
  activeItem: MeetingSurface;
  children: ReactNode | ((meeting: MeetingDetailResponse) => ReactNode);
  currentUserEmail: string;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  projectMembers: Record<string, TeamMember[]>;
  session: AuthSession | null;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
}) {
  const navigate = useNavigate();
  const { spaceId = "", meetingId = "" } = useParams<{ spaceId: string; meetingId: string }>();
  const selectedSpace = spaces.find((space) => space.id === spaceId);
  const meetingContext = useMeetingContext(session, meetingId, spaceId);

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="meeting-context-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState
          actionLabel="프로젝트 목록으로"
          onAction={() => navigate("/spaces")}
          state="notFound"
          title="프로젝트를 찾을 수 없습니다"
          description="회의 결과가 속한 프로젝트가 없거나 접근 권한이 없습니다."
        />
      </AppShell>
    );
  }

  const member = (projectMembers[selectedSpace.name] ?? []).find((item) => item.email === currentUserEmail);
  const layoutProps = {
    meetingId,
    onCreateProject,
    projectName: selectedSpace.name,
    spaceId,
    spaceRole: member?.spaceRole,
    dataSource: workspaceDataSource
  };

  if (meetingContext.status === "loading" || !meetingContext.detail) {
    return (
      <MeetingLayout {...layoutProps} meeting={null}>
        <DataState state="loading" title="회의 정보를 불러오는 중입니다" description="회의 결과의 권한과 범위를 확인하고 있습니다." />
      </MeetingLayout>
    );
  }

  if (meetingContext.status === "error") {
    return (
      <MeetingLayout {...layoutProps} meeting={null}>
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
    <MeetingLayout {...layoutProps} activeItem={activeItem} meeting={meetingContext.detail}>
      {typeof children === "function" ? children(meetingContext.detail) : children}
    </MeetingLayout>
  );
}
