import { useCallback, useEffect, useState } from "react";
import { fetchLegacyWorkspaceSnapshot } from "../api/legacyWorkspace";
import { fetchDashboardSummary } from "../api/dashboard";
import { fetchMeetingDetail, fetchMeetings } from "../api/meetings";
import { fetchMeetingParticipants } from "../api/meetingAccess";
import { fetchProjectKnowledge } from "../api/knowledge";
import { fetchSpaceMembers, fetchSpaces } from "../api/spaces";
import { fetchTasks } from "../api/tasks";
import type { AuthSession } from "../auth/session";
import { initialWorkspaceData } from "../app/initialWorkspaceState";
import {
  buildTargetMeetingKey,
  getSpaceRoleAccessLabel,
  mapSpaceMember,
  mapTaskCard,
  mapWorkspaceSpace,
  toMeetingParticipantState,
  toProjectMeeting
} from "../app/workspaceModel";
import type {
  InviteMeta,
  JoinRequest,
  MeetingInviteMeta,
  MeetingParticipantState,
  ProjectMeeting,
  ProjectTaskState,
  TeamMember,
  WorkspaceDataSource
} from "../app/workspaceTypes";
import type {
  DashboardSummaryResponse,
  MeetingDetailResponse,
  MeetingParticipantSummary,
  ProjectKnowledgeItem,
  WorkspaceData
} from "../types";

type WorkspaceControllerInitialState = {
  data?: WorkspaceData;
  projectMeetings?: Record<string, ProjectMeeting[]>;
  projectMembers?: Record<string, TeamMember[]>;
  projectRequests?: Record<string, JoinRequest[]>;
  projectInvites?: Record<string, InviteMeta>;
  projectTasks?: Record<string, ProjectTaskState[]>;
};

export function useWorkspaceController(
  authSession: AuthSession | null,
  initialState: WorkspaceControllerInitialState = {}
) {
  const [data, setData] = useState<WorkspaceData>(initialState.data ?? initialWorkspaceData);
  const [workspaceDataSource, setWorkspaceDataSource] = useState<WorkspaceDataSource>("loading");
  const [dashboardSummary, setDashboardSummary] = useState<DashboardSummaryResponse | null>(null);
  const [projectMeetings, setProjectMeetings] = useState<Record<string, ProjectMeeting[]>>(initialState.projectMeetings ?? {});
  const [projectMembers, setProjectMembers] = useState<Record<string, TeamMember[]>>(initialState.projectMembers ?? {});
  const [projectRequests, setProjectRequests] = useState<Record<string, JoinRequest[]>>(initialState.projectRequests ?? {});
  const [projectInvites, setProjectInvites] = useState<Record<string, InviteMeta>>(initialState.projectInvites ?? {});
  const [latestMeetingInvites, setLatestMeetingInvites] = useState<Record<string, MeetingInviteMeta>>({});
  const [meetingParticipants, setMeetingParticipants] = useState<Record<string, MeetingParticipantState[]>>({});
  const [projectTasks, setProjectTasks] = useState<Record<string, ProjectTaskState[]>>(initialState.projectTasks ?? {});
  const [projectKnowledge, setProjectKnowledge] = useState<Record<string, ProjectKnowledgeItem[]>>({});
  const [projectAiSpaceIds, setProjectAiSpaceIds] = useState<string[]>([]);
  const [meetingMutationError, setMeetingMutationError] = useState("");
  const [meetingMutationLoading, setMeetingMutationLoading] = useState(false);
  const [meetingReadLoading, setMeetingReadLoading] = useState(false);

  const refreshTargetMeetings = useCallback(async (session: AuthSession, spaceId: string, projectName: string) => {
    const response = await fetchMeetings(session, spaceId);
    const detailResults = await Promise.allSettled(
      response.meetings.map(async (meeting) => {
        const [detail, participantsResponse] = await Promise.all([
          fetchMeetingDetail(session, meeting.id),
          fetchMeetingParticipants(session, meeting.id)
        ]);
        return { detail, participants: participantsResponse.participants };
      })
    );
    const details = new Map<string, MeetingDetailResponse>();
    const participantsByMeetingId = new Map<string, MeetingParticipantSummary[]>();
    detailResults.forEach((result) => {
      if (result.status === "fulfilled") {
        details.set(result.value.detail.id, result.value.detail);
        participantsByMeetingId.set(result.value.detail.id, result.value.participants);
      }
    });
    const meetings = response.meetings.map((meeting, index) =>
      toProjectMeeting(details.get(meeting.id) ?? meeting, index)
    );
    setProjectMeetings((previous) => ({ ...previous, [projectName]: meetings }));
    setMeetingParticipants((previous) => {
      const targetPrefix = `target:${spaceId}:`;
      const next = Object.fromEntries(
        Object.entries(previous).filter(([key]) => !key.startsWith(targetPrefix))
      );
      details.forEach((detail) => {
        const meetingKey = buildTargetMeetingKey(spaceId, detail.id);
        next[meetingKey] = (participantsByMeetingId.get(detail.id) ?? []).map((participant) =>
          toMeetingParticipantState(meetingKey, participant)
        );
      });
      return next;
    });
    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.map((space) =>
          space.id === spaceId ? { ...space, meetings: `진행 회의 ${meetings.length}건` } : space
        )
      }
    }));
    return detailResults.every((result) => result.status === "fulfilled");
  }, []);

  const refreshTargetTasks = useCallback(async (session: AuthSession, spaceId: string, projectName: string) => {
    const response = await fetchTasks(session, spaceId);
    setProjectTasks((previous) => ({
      ...previous,
      [projectName]: response.tasks.map((task) =>
        mapTaskCard(task, projectName, projectMeetings[projectName] ?? [], projectMembers[projectName] ?? [])
      )
    }));
  }, [projectMeetings, projectMembers]);

  const refreshProjectKnowledge = useCallback(async (session: AuthSession, spaceId: string) => {
    const response = await fetchProjectKnowledge(session, spaceId);
    setProjectKnowledge((previous) => ({ ...previous, [spaceId]: response.items }));
  }, []);

  const refreshTargetMembers = useCallback(async (session: AuthSession, spaceId: string, projectName: string) => {
    const response = await fetchSpaceMembers(session, spaceId);
    const members = response.members.map(mapSpaceMember);
    setProjectMembers((previous) => ({ ...previous, [projectName]: members }));
    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.map((space) =>
          space.id === spaceId ? { ...space, members: `멤버 ${members.length}명` } : space
        )
      }
    }));
  }, []);

  useEffect(() => {
    if (!authSession) {
      setProjectAiSpaceIds([]);
      setMeetingReadLoading(false);
      setWorkspaceDataSource("loading");
      return;
    }

    const session = authSession;
    let active = true;

    async function loadWorkspace() {
      const [legacyResult, spacesResult, dashboardResult] = await Promise.allSettled([
        fetchLegacyWorkspaceSnapshot(session),
        fetchSpaces(session),
        fetchDashboardSummary(session)
      ]);
      if (!active) {
        return;
      }

      const legacyData = legacyResult.status === "fulfilled" ? legacyResult.value : null;
      const baseData: WorkspaceData = {
        workspaceHome: legacyData?.workspaceHome ?? initialWorkspaceData.workspaceHome,
        liveMeeting: legacyData?.liveMeeting ?? initialWorkspaceData.liveMeeting,
        meetingAi: legacyData?.meetingAi ?? initialWorkspaceData.meetingAi,
        projectOverview: legacyData?.projectOverview ?? initialWorkspaceData.projectOverview,
        reportAgent: legacyData?.reportAgent ?? initialWorkspaceData.reportAgent
      };

      if (spacesResult.status === "rejected") {
        setData(baseData);
        setDashboardSummary(null);
        setProjectAiSpaceIds([]);
        setWorkspaceDataSource(legacyData ? "legacy-api" : "mock-fallback");
        return;
      }

      const resources = await Promise.all(
        spacesResult.value.spaces.map(async (space) => {
          const [meetingsResult, membersResult, tasksResult, knowledgeResult] = await Promise.allSettled([
            fetchMeetings(session, space.id),
            fetchSpaceMembers(session, space.id),
            fetchTasks(session, space.id),
            fetchProjectKnowledge(session, space.id)
          ]);
          return { space, meetingsResult, membersResult, tasksResult, knowledgeResult };
        })
      );
      if (!active) {
        return;
      }

      const hasPartialFailure = dashboardResult.status === "rejected" || resources.some(
        ({ meetingsResult, membersResult, tasksResult, knowledgeResult }) =>
          meetingsResult.status === "rejected"
          || membersResult.status === "rejected"
          || tasksResult.status === "rejected"
          || knowledgeResult.status === "rejected"
      );
      if (resources.some(({ membersResult }) => membersResult.status === "rejected")) {
        setMeetingMutationError("일부 프로젝트의 멤버 목록을 불러오지 못했습니다. 참여자 지정 기능이 제한될 수 있습니다.");
      }
      setData({
        ...baseData,
        workspaceHome: {
          ...baseData.workspaceHome,
          spaces: resources.map(({ space, meetingsResult, membersResult }) =>
            mapWorkspaceSpace(
              space,
              meetingsResult.status === "fulfilled" ? meetingsResult.value.meetings.length : space.meetingCount,
              membersResult.status === "fulfilled" ? membersResult.value.members.length : 1
            )
          )
        }
      });
      setDashboardSummary(dashboardResult.status === "fulfilled" ? dashboardResult.value : null);
      setProjectMeetings((previous) => {
        const next = { ...previous };
        resources.forEach(({ space, meetingsResult }) => {
          next[space.name] =
            meetingsResult.status === "fulfilled" ? meetingsResult.value.meetings.map(toProjectMeeting) : [];
        });
        return next;
      });
      setProjectMembers((previous) => {
        const next = { ...previous };
        resources.forEach(({ space, membersResult }) => {
          next[space.name] =
            membersResult.status === "fulfilled"
              ? membersResult.value.members.map(mapSpaceMember)
              : [
                  {
                    userId: session.user.id,
                    name: session.user.displayName,
                    email: session.user.email,
                    role: space.role === "OWNER" ? "Owner" : space.role === "ADMIN" ? "Admin" : "Member",
                    spaceRole: space.role,
                    since: "이미 합류",
                    access: getSpaceRoleAccessLabel(space.role),
                    rank: space.role === "OWNER" ? "팀 리드" : space.role === "ADMIN" ? "관리자" : "팀원",
                    status: "active"
                  }
                ];
        });
        return next;
      });
      setProjectTasks((previous) => {
        const next = { ...previous };
        resources.forEach(({ space, meetingsResult, membersResult, tasksResult }) => {
          const meetings = meetingsResult.status === "fulfilled" ? meetingsResult.value.meetings.map(toProjectMeeting) : [];
          const members = membersResult.status === "fulfilled" ? membersResult.value.members.map(mapSpaceMember) : [];
          next[space.name] = tasksResult.status === "fulfilled"
            ? tasksResult.value.tasks.map((task) => mapTaskCard(task, space.name, meetings, members))
            : [];
        });
        return next;
      });
      setProjectKnowledge((previous) => {
        const next = { ...previous };
        resources.forEach(({ space, knowledgeResult }) => {
          next[space.id] = knowledgeResult.status === "fulfilled" ? knowledgeResult.value.items : [];
        });
        return next;
      });
      setProjectAiSpaceIds(spacesResult.value.spaces.map((space) => space.id));
      setMeetingReadLoading(true);
      const detailResults = await Promise.allSettled(
        resources
          .filter(({ meetingsResult }) => meetingsResult.status === "fulfilled")
          .map(({ space }) => refreshTargetMeetings(session, space.id, space.name))
      );
      if (!active) {
        return;
      }
      const hasDetailFailure = detailResults.some(
        (result) => result.status === "rejected" || (result.status === "fulfilled" && !result.value)
      );
      setMeetingReadLoading(false);
      if (hasDetailFailure) {
        setMeetingMutationError("일부 회의 상세 정보를 불러오지 못했습니다. 회의를 다시 선택해 주세요.");
      }
      setWorkspaceDataSource(hasPartialFailure || hasDetailFailure ? "workspace-api-partial" : "workspace-api");
    }

    void loadWorkspace();

    return () => {
      active = false;
    };
  }, [authSession, refreshTargetMeetings]);

  return {
    data,
    setData,
    workspaceDataSource,
    setWorkspaceDataSource,
    dashboardSummary,
    setDashboardSummary,
    projectMeetings,
    setProjectMeetings,
    projectMembers,
    setProjectMembers,
    projectRequests,
    setProjectRequests,
    projectInvites,
    setProjectInvites,
    latestMeetingInvites,
    setLatestMeetingInvites,
    meetingParticipants,
    setMeetingParticipants,
    projectTasks,
    setProjectTasks,
    projectKnowledge,
    setProjectKnowledge,
    projectAiSpaceIds,
    setProjectAiSpaceIds,
    meetingMutationError,
    setMeetingMutationError,
    meetingMutationLoading,
    setMeetingMutationLoading,
    meetingReadLoading,
    setMeetingReadLoading,
    refreshTargetMeetings,
    refreshTargetTasks,
    refreshProjectKnowledge,
    refreshTargetMembers
  };
}
