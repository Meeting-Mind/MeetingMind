import { useNavigate, useParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import type { WorkspaceData } from "../types";
import { DataState } from "../components/common/DataState";
import { AppShell } from "../components/layout/AppShell";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import { TargetDataGate } from "../components/layout/TargetDataGate";
import { isTargetDataReady } from "../components/layout/targetDataGateModel";
import { useMeetingContext } from "../hooks/useMeetingContext";
import type { WorkspaceDataSource } from "../app/workspaceTypes";
import { LiveMeetingPage } from "./LiveMeetingPage";

export function MeetingPrejoinRoutePage({
  data,
  onCreateProject,
  session,
  spaces,
  workspaceDataSource
}: {
  data: WorkspaceData["liveMeeting"];
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  session: AuthSession;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
}) {
  const navigate = useNavigate();
  const { spaceId = "", meetingId = "" } = useParams<{ spaceId: string; meetingId: string }>();
  const selectedSpace = spaces.find((space) => space.id === spaceId);
  const meetingContext = useMeetingContext(session, meetingId, spaceId);

  if (!isTargetDataReady(workspaceDataSource)) {
    return <TargetDataGate contentClassName="meeting-prejoin-route-main" dataSource={workspaceDataSource} onCreateProject={onCreateProject}>{null}</TargetDataGate>;
  }

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="meeting-prejoin-route-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState
          actionLabel="프로젝트 목록으로"
          onAction={() => navigate("/spaces")}
          state="notFound"
          title="프로젝트를 찾을 수 없습니다"
          description="회의 입장 정보가 속한 프로젝트가 없거나 접근 권한이 없습니다."
        />
      </AppShell>
    );
  }

  if (meetingContext.status === "loading" || !meetingContext.detail) {
    return (
      <AppShell
        contentClassName="meeting-prejoin-route-main"
        sidebar={<WorkspaceSidebar activeItem="meetings" contextOverride={selectedSpace.name} mode="project" projectName={selectedSpace.name} spaceId={spaceId} />}
      >
        <DataState state="loading" title="회의 입장 정보를 확인하는 중입니다" description="회의 권한과 프로젝트 범위를 확인하고 있습니다." />
      </AppShell>
    );
  }

  if (meetingContext.status === "error") {
    return (
      <AppShell
        contentClassName="meeting-prejoin-route-main"
        sidebar={<WorkspaceSidebar activeItem="meetings" contextOverride={selectedSpace.name} mode="project" projectName={selectedSpace.name} spaceId={spaceId} />}
      >
        <DataState
          actionLabel="회의 목록으로"
          onAction={() => navigate(`/spaces/${encodeURIComponent(spaceId)}/meetings`)}
          state="error"
          title="회의 입장 정보를 확인하지 못했습니다"
          description={meetingContext.error ?? "접근 권한을 확인한 뒤 다시 시도해 주세요."}
        />
      </AppShell>
    );
  }

  return (
    <LiveMeetingPage
      data={data}
      meetingContext={meetingContext.detail}
      projectNameOverride={selectedSpace.name}
      session={session}
      strictApi
    />
  );
}
