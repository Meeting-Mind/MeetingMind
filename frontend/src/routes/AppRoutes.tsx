import type { ComponentProps, ReactNode } from "react";
import { Navigate, Route, Routes, useSearchParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import type { WorkspaceDataSource } from "../app/workspaceTypes";
import { ProtectedRoute } from "../components/layout/ProtectedRoute";
import { DomainTermsPage } from "../pages/DomainTermsPage";
import { LiveMeetingPage } from "../pages/LiveMeetingPage";
import { LiveRoomPage } from "../pages/LiveRoomPage";
import { MeetingDetailPage } from "../pages/MeetingDetailPage";
import { MeetingTranscriptPage } from "../pages/MeetingTranscriptPage";
import { MeetingTaskCandidatesPage } from "../pages/MeetingTaskCandidatesPage";
import { MeetingPrejoinRoutePage } from "../pages/MeetingPrejoinRoutePage";
import { MeetingLiveRoutePage } from "../pages/MeetingLiveRoutePage";
import { MeetingAiPage } from "../pages/MeetingAiPage";
import { MeetingAccessPage } from "../pages/MeetingAccessPage";
import { ProjectOverviewPage } from "../pages/ProjectOverviewPage";
import { ProjectHomePage } from "../pages/ProjectHomePage";
import { ProjectMeetingsPage } from "../pages/ProjectMeetingsPage";
import { ProjectAiPage } from "../pages/ProjectAiPage";
import { ProjectTasksPage } from "../pages/ProjectTasksPage";
import { ProjectKnowledgePage } from "../pages/ProjectKnowledgePage";
import { ProjectCalendarPage } from "../pages/ProjectCalendarPage";
import { ProjectSettingsPage } from "../pages/ProjectSettingsPage";
import { ReportAgentPage } from "../pages/ReportAgentPage";
import { SpaceInvitationPage } from "../pages/SpaceInvitationPage";
import { TeamMembersPage } from "../pages/TeamMembersPage";
import { WorkspaceHomePage } from "../pages/WorkspaceHomePage";
import { LandingPage } from "../pages/LandingPage";
import { MeetingContextLayout } from "../components/layout/MeetingContextLayout";
import { AccountSettingsPage } from "../pages/AccountSettingsPage";
import { TargetDataGate } from "../components/layout/TargetDataGate";
import type {
  DashboardSummaryResponse,
  WorkspaceData
} from "../types";

type WorkspaceHomeProps = ComponentProps<typeof WorkspaceHomePage>;
type ProjectOverviewProps = ComponentProps<typeof ProjectOverviewPage>;
type TeamMembersProps = ComponentProps<typeof TeamMembersPage>;

function LegacySpaceRedirect({ children, suffix = "" }: { children: ReactNode; suffix?: string }) {
  const [searchParams] = useSearchParams();
  const spaceId = searchParams.get("spaceId");
  if (!spaceId) {
    return children;
  }
  return <Navigate replace to={`/spaces/${encodeURIComponent(spaceId)}${suffix}`} />;
}

function LegacyMeetingRedirect({ children, target }: { children: ReactNode; target: "prejoin" | "live" | "report" | "ai" }) {
  const [searchParams] = useSearchParams();
  const spaceId = searchParams.get("spaceId");
  const meetingId = searchParams.get("meetingId");
  if (!spaceId || !meetingId) {
    return children;
  }
  const suffix = target === "prejoin" ? "/live/prejoin" : target === "live" ? "/live" : `/${target}`;
  return <Navigate replace to={`/spaces/${encodeURIComponent(spaceId)}/meetings/${encodeURIComponent(meetingId)}${suffix}`} />;
}

export type AppRoutesProps = {
  authSession: AuthSession | null;
  authBootstrapLoading: boolean;
  onRequestLogin: () => void;
  onLogout: () => Promise<void>;
  onLogoutAll: () => Promise<void>;
  data: WorkspaceData;
  workspaceDataSource: WorkspaceDataSource;
  dashboardSummary: DashboardSummaryResponse | null;
  projectAiSpaceIds: ProjectOverviewProps["projectAiSpaceIds"];
  projectMeetings: ProjectOverviewProps["projectMeetings"];
  projectMembers: ProjectOverviewProps["projectMembers"];
  projectTasks: ProjectOverviewProps["projectTasks"];
  projectKnowledge: ProjectOverviewProps["projectKnowledge"];
  meetingParticipants: ProjectOverviewProps["meetingParticipants"];
  projectRequests: TeamMembersProps["pendingRequests"];
  projectInvites: TeamMembersProps["inviteMeta"];
  latestMeetingInvites: ProjectOverviewProps["latestMeetingInvites"];
  meetingMutationError: string;
  meetingMutationLoading: boolean;
  meetingReadLoading: boolean;
  onCreateMeeting: WorkspaceHomeProps["onCreateMeeting"];
  onCreateProject: WorkspaceHomeProps["onCreateProject"];
  onDeleteProject: ProjectOverviewProps["onDeleteProject"];
  onUpdateProject: ProjectOverviewProps["onUpdateProject"];
  onDeleteMeeting: ProjectOverviewProps["onDeleteMeeting"];
  onUpdateMeeting: ProjectOverviewProps["onUpdateMeeting"];
  onUpdateMeetingStatus: ProjectOverviewProps["onUpdateMeetingStatus"];
  onAddMeetingParticipant: ProjectOverviewProps["onAddMeetingParticipant"];
  onUpdateMeetingParticipant: ProjectOverviewProps["onUpdateMeetingParticipant"];
  onCreateProjectTask: ProjectOverviewProps["onCreateProjectTask"];
  onMoveProjectTask: ProjectOverviewProps["onMoveProjectTask"];
  onUpdateProjectTask: ProjectOverviewProps["onUpdateProjectTask"];
  onDeleteProjectTask: ProjectOverviewProps["onDeleteProjectTask"];
  onCreateProjectKnowledge: ProjectOverviewProps["onCreateProjectKnowledge"];
  onUpdateProjectKnowledge: ProjectOverviewProps["onUpdateProjectKnowledge"];
  onDeleteProjectKnowledge: ProjectOverviewProps["onDeleteProjectKnowledge"];
  onCreateSpaceInvitation: TeamMembersProps["onCreateSpaceInvitation"];
  onApproveJoinRequest: TeamMembersProps["onApproveRequest"];
  onRejectJoinRequest: TeamMembersProps["onRejectRequest"];
  onUpdateSpaceMemberRole: TeamMembersProps["onUpdateMemberRole"];
  onRemoveSpaceMember: TeamMembersProps["onRemoveMember"];
  onTransferProjectOwner: TeamMembersProps["onTransferOwner"];
};

export function AppRoutes({
  authSession,
  authBootstrapLoading,
  onRequestLogin,
  onLogout,
  onLogoutAll,
  data,
  workspaceDataSource,
  dashboardSummary,
  projectAiSpaceIds,
  projectMeetings,
  projectMembers,
  projectTasks,
  projectKnowledge,
  meetingParticipants,
  projectRequests,
  projectInvites,
  latestMeetingInvites,
  meetingMutationError,
  meetingMutationLoading,
  meetingReadLoading,
  onCreateMeeting,
  onCreateProject,
  onDeleteProject,
  onUpdateProject,
  onDeleteMeeting,
  onUpdateMeeting,
  onUpdateMeetingStatus,
  onAddMeetingParticipant,
  onUpdateMeetingParticipant,
  onCreateProjectTask,
  onMoveProjectTask,
  onUpdateProjectTask,
  onDeleteProjectTask,
  onCreateProjectKnowledge,
  onUpdateProjectKnowledge,
  onDeleteProjectKnowledge,
  onCreateSpaceInvitation,
  onApproveJoinRequest,
  onRejectJoinRequest,
  onUpdateSpaceMemberRole,
  onRemoveSpaceMember,
  onTransferProjectOwner
}: AppRoutesProps) {
  const protectedRoute = (children: ReactNode) => (
    <ProtectedRoute loading={authBootstrapLoading} onRequestLogin={onRequestLogin} session={authSession}>
      {children}
    </ProtectedRoute>
  );

  const projectOverviewElement = protectedRoute(
    <ProjectOverviewPage
      currentUserId={authSession?.user.id ?? ""}
      currentUserEmail={authSession?.user.email ?? ""}
      data={data.projectOverview}
      session={authSession}
      projectAiSpaceIds={projectAiSpaceIds}
      meetingMutationError={meetingMutationError}
      meetingMutationLoading={meetingMutationLoading}
      meetingReadLoading={meetingReadLoading}
      latestMeetingInvites={latestMeetingInvites}
      onDeleteProject={onDeleteProject}
      onCreateMeeting={onCreateMeeting}
      onCreateProject={onCreateProject}
      onCreateProjectKnowledge={onCreateProjectKnowledge}
      onUpdateProject={onUpdateProject}
      onUpdateProjectKnowledge={onUpdateProjectKnowledge}
      onDeleteProjectKnowledge={onDeleteProjectKnowledge}
      onAddMeetingParticipant={onAddMeetingParticipant}
      onCreateProjectTask={onCreateProjectTask}
      projectMeetings={projectMeetings}
      projectMembers={projectMembers}
      projectTasks={projectTasks}
      projectKnowledge={projectKnowledge}
      meetingParticipants={meetingParticipants}
      onDeleteMeeting={onDeleteMeeting}
      onDeleteProjectTask={onDeleteProjectTask}
      onMoveProjectTask={onMoveProjectTask}
      onUpdateProjectTask={onUpdateProjectTask}
      onUpdateMeetingParticipant={onUpdateMeetingParticipant}
      onUpdateMeeting={onUpdateMeeting}
      onUpdateMeetingStatus={onUpdateMeetingStatus}
      spaces={data.workspaceHome.spaces}
    />
  );

  const projectHomeElement = protectedRoute(
    <ProjectHomePage
      currentUserEmail={authSession?.user.email ?? ""}
      dashboardSummary={dashboardSummary}
      projectAiSpaceIds={projectAiSpaceIds}
      projectKnowledge={projectKnowledge}
      projectMembers={projectMembers}
      projectMeetings={projectMeetings}
      projectTasks={projectTasks}
      session={authSession}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
      onCreateProject={onCreateProject}
    />
  );

  const projectMeetingsElement = protectedRoute(
    <ProjectMeetingsPage
      currentUserEmail={authSession?.user.email ?? ""}
      projectMembers={projectMembers}
      projectMeetings={projectMeetings}
      session={authSession}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
      meetingMutationError={meetingMutationError}
      meetingMutationLoading={meetingMutationLoading}
      onCreateMeeting={onCreateMeeting}
      onCreateProject={onCreateProject}
    />
  );

  const projectAiElement = protectedRoute(
    <ProjectAiPage
      currentUserEmail={authSession?.user.email ?? ""}
      projectAiSpaceIds={projectAiSpaceIds}
      projectKnowledge={projectKnowledge}
      projectMembers={projectMembers}
      session={authSession}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
      onCreateProject={onCreateProject}
    />
  );

  const projectTasksElement = protectedRoute(
    <ProjectTasksPage
      currentUserEmail={authSession?.user.email ?? ""}
      projectMembers={projectMembers}
      projectTasks={projectTasks}
      session={authSession}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
      meetingMutationError={meetingMutationError}
      meetingMutationLoading={meetingMutationLoading}
      onCreateProjectTask={onCreateProjectTask}
      onDeleteProjectTask={onDeleteProjectTask}
      onMoveProjectTask={onMoveProjectTask}
      onCreateProject={onCreateProject}
    />
  );

  const projectKnowledgeElement = protectedRoute(
    <ProjectKnowledgePage
      currentUserEmail={authSession?.user.email ?? ""}
      projectKnowledge={projectKnowledge}
      projectMembers={projectMembers}
      session={authSession}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
      meetingMutationError={meetingMutationError}
      meetingMutationLoading={meetingMutationLoading}
      onCreateProjectKnowledge={onCreateProjectKnowledge}
      onDeleteProjectKnowledge={onDeleteProjectKnowledge}
      onUpdateProjectKnowledge={onUpdateProjectKnowledge}
      onCreateProject={onCreateProject}
    />
  );

  const projectCalendarElement = protectedRoute(
    <ProjectCalendarPage
      currentUserEmail={authSession?.user.email ?? ""}
      projectMembers={projectMembers}
      session={authSession!}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
      onCreateProject={onCreateProject}
    />
  );

  const projectSettingsElement = protectedRoute(
    <ProjectSettingsPage
      currentUserEmail={authSession?.user.email ?? ""}
      meetingMutationError={meetingMutationError}
      meetingMutationLoading={meetingMutationLoading}
      onCreateProject={onCreateProject}
      onDeleteProject={onDeleteProject}
      onUpdateProject={onUpdateProject}
      projectMembers={projectMembers}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
    />
  );

  const accountSettingsElement = protectedRoute(
    authSession ? <AccountSettingsPage onLogout={onLogout} onLogoutAll={onLogoutAll} session={authSession} /> : null
  );

  const meetingDetailElement = protectedRoute(
    <MeetingDetailPage
      currentUserEmail={authSession?.user.email ?? ""}
      onCreateProject={onCreateProject}
      projectMembers={projectMembers}
      session={authSession}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
    />
  );

  const meetingTranscriptElement = protectedRoute(
    <MeetingTranscriptPage
      currentUserEmail={authSession?.user.email ?? ""}
      onCreateProject={onCreateProject}
      projectMembers={projectMembers}
      session={authSession}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
    />
  );

  const meetingTaskCandidatesElement = protectedRoute(
    <MeetingTaskCandidatesPage
      currentUserEmail={authSession?.user.email ?? ""}
      onCreateProject={onCreateProject}
      projectMembers={projectMembers}
      session={authSession}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
    />
  );

  const meetingReportElement = protectedRoute(
    <MeetingContextLayout
      activeItem="report"
      currentUserEmail={authSession?.user.email ?? ""}
      onCreateProject={onCreateProject}
      projectMembers={projectMembers}
      session={authSession}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
    >
      {(meeting) => (
        <ReportAgentPage
          data={data.reportAgent}
          embedded
          meeting={meeting}
          onCreateProject={onCreateProject}
          session={authSession}
        />
      )}
    </MeetingContextLayout>
  );

  const meetingAiElement = protectedRoute(
    <MeetingContextLayout
      activeItem="ai"
      currentUserEmail={authSession?.user.email ?? ""}
      onCreateProject={onCreateProject}
      projectMembers={projectMembers}
      session={authSession}
      spaces={data.workspaceHome.spaces}
      workspaceDataSource={workspaceDataSource}
    >
      {(meeting) => (
        <MeetingAiPage
          data={data.meetingAi}
          embedded
          meeting={meeting}
          onCreateProject={onCreateProject}
          session={authSession}
        />
      )}
    </MeetingContextLayout>
  );

  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/settings" element={accountSettingsElement} />
      <Route path="/settings/account" element={accountSettingsElement} />
      <Route path="/settings/security" element={accountSettingsElement} />
      <Route
        path="/spaces"
        element={protectedRoute(
          authSession ? (
            <WorkspaceHomePage
              actionItems={data.meetingAi.actions}
              currentUserEmail={authSession.user.email}
              data={data.workspaceHome}
              dataSource={workspaceDataSource}
              dashboardSummary={dashboardSummary}
              meetingMutationError={meetingMutationError}
              meetingMutationLoading={meetingMutationLoading || meetingReadLoading}
              latestMeetingInvites={latestMeetingInvites}
              onCreateMeeting={onCreateMeeting}
              onCreateProject={onCreateProject}
              projectMembers={projectMembers}
              session={authSession}
            />
          ) : null
        )}
      />
      <Route
        path="/meeting-access"
        element={protectedRoute(authSession ? <MeetingAccessPage session={authSession} /> : null)}
      />
      <Route
        path="/space-invitations/:spaceId/:invitationId"
        element={protectedRoute(authSession ? <SpaceInvitationPage session={authSession} /> : null)}
      />
      <Route
        path="/live-meeting"
        element={protectedRoute(authSession ? <LegacyMeetingRedirect target="prejoin"><LiveMeetingPage data={data.liveMeeting} session={authSession} /></LegacyMeetingRedirect> : null)}
      />
      <Route
        path="/live-room"
        element={protectedRoute(authSession ? <LegacyMeetingRedirect target="live"><LiveRoomPage liveMeeting={data.liveMeeting} meetingAi={data.meetingAi} session={authSession} /></LegacyMeetingRedirect> : null)}
      />
      <Route
        path="/spaces/:spaceId/meetings"
        element={projectMeetingsElement}
      />
      <Route
        path="/spaces/:spaceId/meetings/:meetingId"
        element={meetingDetailElement}
      />
      <Route
        path="/spaces/:spaceId/meetings/:meetingId/transcript"
        element={meetingTranscriptElement}
      />
      <Route
        path="/spaces/:spaceId/meetings/:meetingId/tasks"
        element={meetingTaskCandidatesElement}
      />
      <Route
      path="/spaces/:spaceId/meetings/:meetingId/live/prejoin"
        element={protectedRoute(authSession ? <MeetingPrejoinRoutePage data={data.liveMeeting} onCreateProject={onCreateProject} session={authSession} spaces={data.workspaceHome.spaces} workspaceDataSource={workspaceDataSource} /> : null)}
      />
      <Route
      path="/spaces/:spaceId/meetings/:meetingId/live"
        element={protectedRoute(authSession ? <MeetingLiveRoutePage data={data.liveMeeting} meetingAi={data.meetingAi} onCreateProject={onCreateProject} session={authSession} spaces={data.workspaceHome.spaces} workspaceDataSource={workspaceDataSource} /> : null)}
      />
      <Route
        path="/spaces/:spaceId"
        element={projectHomeElement}
      />
      <Route
        path="/project-overview"
        element={<LegacySpaceRedirect>{projectOverviewElement}</LegacySpaceRedirect>}
      />
      <Route
        path="/spaces/:spaceId/knowledge"
        element={projectKnowledgeElement}
      />
      <Route
        path="/spaces/:spaceId/calendar"
        element={projectCalendarElement}
      />
      <Route
        path="/spaces/:spaceId/tasks"
        element={projectTasksElement}
      />
      <Route
        path="/spaces/:spaceId/ai"
        element={projectAiElement}
      />
      <Route
        path="/spaces/:spaceId/settings"
        element={projectSettingsElement}
      />
      <Route
        path="/spaces/:spaceId/meetings/:meetingId/report"
        element={meetingReportElement}
      />
      <Route
        path="/spaces/:spaceId/meetings/:meetingId/ai"
        element={meetingAiElement}
      />
      <Route
        path="/spaces/:spaceId/members"
        element={protectedRoute(
          <TargetDataGate contentClassName="team-members-main" dataSource={workspaceDataSource} onCreateProject={onCreateProject}>
            <TeamMembersPage
              inviteMeta={projectInvites}
              onApproveRequest={onApproveJoinRequest}
              onCreateProject={onCreateProject}
              onCreateSpaceInvitation={onCreateSpaceInvitation}
              onRemoveMember={onRemoveSpaceMember}
              onRejectRequest={onRejectJoinRequest}
              onTransferOwner={onTransferProjectOwner}
              onUpdateMemberRole={onUpdateSpaceMemberRole}
              pendingRequests={projectRequests}
              projectMembers={projectMembers}
              spaces={data.workspaceHome.spaces}
            />
          </TargetDataGate>
        )}
      />
      <Route
        path="/spaces/:spaceId/terms"
        element={protectedRoute(
          authSession ? (
            <TargetDataGate contentClassName="domain-terms-main" dataSource={workspaceDataSource} onCreateProject={onCreateProject}>
              <DomainTermsPage
                currentUserEmail={authSession.user.email}
                onCreateProject={onCreateProject}
                projectMembers={projectMembers}
                session={authSession}
                spaces={data.workspaceHome.spaces}
              />
            </TargetDataGate>
          ) : null
        )}
      />
      <Route
        path="/team-members"
        element={protectedRoute(
          <LegacySpaceRedirect suffix="/members">
            <TeamMembersPage
              inviteMeta={projectInvites}
              onApproveRequest={onApproveJoinRequest}
              onCreateProject={onCreateProject}
              onCreateSpaceInvitation={onCreateSpaceInvitation}
              onRemoveMember={onRemoveSpaceMember}
              onRejectRequest={onRejectJoinRequest}
              onTransferOwner={onTransferProjectOwner}
              onUpdateMemberRole={onUpdateSpaceMemberRole}
              pendingRequests={projectRequests}
              projectMembers={projectMembers}
              spaces={data.workspaceHome.spaces}
            />
          </LegacySpaceRedirect>
        )}
      />
      <Route
        path="/terms"
        element={protectedRoute(
          authSession ? (
            <LegacySpaceRedirect suffix="/terms">
              <DomainTermsPage
                currentUserEmail={authSession.user.email}
                onCreateProject={onCreateProject}
                projectMembers={projectMembers}
                session={authSession}
                spaces={data.workspaceHome.spaces}
              />
            </LegacySpaceRedirect>
          ) : null
        )}
      />
      <Route
        path="/meeting-ai"
        element={protectedRoute(
          <LegacyMeetingRedirect target="ai"><MeetingAiPage data={data.meetingAi} onCreateProject={onCreateProject} session={authSession} /></LegacyMeetingRedirect>
        )}
      />
      <Route
        path="/report-agent"
        element={protectedRoute(
          <LegacyMeetingRedirect target="report"><ReportAgentPage data={data.reportAgent} onCreateProject={onCreateProject} session={authSession} /></LegacyMeetingRedirect>
        )}
      />
    </Routes>
  );
}
