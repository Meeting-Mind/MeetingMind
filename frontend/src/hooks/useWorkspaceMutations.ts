import type { Dispatch, SetStateAction } from "react";
import type { NavigateFunction } from "react-router-dom";
import { addMeetingParticipant, updateMeetingParticipant } from "../api/meetingAccess";
import { createMeeting, deleteMeeting, updateMeeting } from "../api/meetings";
import { createProjectKnowledge, deleteProjectKnowledge, updateProjectKnowledge } from "../api/knowledge";
import {
  createSpace,
  createSpaceInvitation,
  deleteSpace,
  removeSpaceMember,
  transferSpaceOwner,
  updateSpace,
  updateSpaceMemberRole
} from "../api/spaces";
import { createTask, deleteTask, updateTask } from "../api/tasks";
import type { AuthSession } from "../auth/session";
import {
  buildInviteMeta,
  buildMeeting,
  buildMeetingKey,
  buildSpaceId,
  getSpaceRoleAccessLabel,
  meetingStatusLabel,
  meetingStateStatus
} from "../app/workspaceModel";
import type {
  CreateMeetingPayload,
  InviteMeta,
  JoinRequest,
  MeetingParticipantState,
  ProjectMeeting,
  ProjectTaskState,
  TeamMember,
  UpdateProjectPayload
} from "../app/workspaceTypes";
import type {
  CreateProjectKnowledgeRequest,
  ProjectKnowledgeItem,
  UpdateMeetingRequest,
  UpdateProjectKnowledgeRequest,
  WorkspaceData
} from "../types";

type WorkspaceMutationContext = {
  authSession: AuthSession | null;
  data: WorkspaceData;
  projectAiSpaceIds: string[];
  projectMeetings: Record<string, ProjectMeeting[]>;
  projectMembers: Record<string, TeamMember[]>;
  projectRequests: Record<string, JoinRequest[]>;
  navigate: NavigateFunction;
  setData: Dispatch<SetStateAction<WorkspaceData>>;
  setProjectAiSpaceIds: Dispatch<SetStateAction<string[]>>;
  setProjectInvites: Dispatch<SetStateAction<Record<string, InviteMeta>>>;
  setProjectKnowledge: Dispatch<SetStateAction<Record<string, ProjectKnowledgeItem[]>>>;
  setProjectMembers: Dispatch<SetStateAction<Record<string, TeamMember[]>>>;
  setProjectMeetings: Dispatch<SetStateAction<Record<string, ProjectMeeting[]>>>;
  setProjectRequests: Dispatch<SetStateAction<Record<string, JoinRequest[]>>>;
  setProjectTasks: Dispatch<SetStateAction<Record<string, ProjectTaskState[]>>>;
  setLatestMeetingInvites: Dispatch<SetStateAction<Record<string, { meetingId: string; title: string; joinCode: string; joinUrl: string }>>>;
  setMeetingParticipants: Dispatch<SetStateAction<Record<string, MeetingParticipantState[]>>>;
  setMeetingMutationError: Dispatch<SetStateAction<string>>;
  setMeetingMutationLoading: Dispatch<SetStateAction<boolean>>;
  refreshTargetMeetings: (session: AuthSession, spaceId: string, projectName: string) => Promise<boolean>;
  refreshTargetTasks: (session: AuthSession, spaceId: string, projectName: string) => Promise<void>;
  refreshProjectKnowledge: (session: AuthSession, spaceId: string) => Promise<void>;
  refreshTargetMembers: (session: AuthSession, spaceId: string, projectName: string) => Promise<void>;
};

export function useWorkspaceMutations({
  authSession,
  data,
  projectAiSpaceIds,
  projectMeetings,
  projectMembers,
  projectRequests,
  navigate,
  setData,
  setProjectAiSpaceIds,
  setProjectInvites,
  setProjectKnowledge,
  setProjectMembers,
  setProjectMeetings,
  setProjectRequests,
  setProjectTasks,
  setLatestMeetingInvites,
  setMeetingParticipants,
  setMeetingMutationError,
  setMeetingMutationLoading,
  refreshTargetMeetings,
  refreshTargetTasks,
  refreshProjectKnowledge,
  refreshTargetMembers
}: WorkspaceMutationContext) {
  async function handleCreateProject({ name, description }: { name: string; description: string }) {
    const normalizedName = name.trim();
    if (!normalizedName) {
      return;
    }
    if (!authSession) {
      throw new Error("로그인이 필요합니다.");
    }
    if (data.workspaceHome.spaces.some((space) => space.name.trim().toLocaleLowerCase() === normalizedName.toLocaleLowerCase())) {
      throw new Error("같은 이름의 프로젝트가 이미 있습니다.");
    }

    const created = await createSpace(authSession, {
      name: normalizedName,
      description: description.trim() || null
    });
    const spaceId = created.id;

    const owner = authSession?.user;
    const seededMembers: TeamMember[] = owner
      ? [
          {
            userId: owner.id,
            name: owner.displayName,
            email: owner.email,
            role: "Owner",
            spaceRole: "OWNER",
            since: "방금 합류",
            access: getSpaceRoleAccessLabel("OWNER"),
            rank: "팀 리드",
            status: "active"
          }
        ]
      : [];

    setProjectMeetings((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectMembers((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? seededMembers }));
    setProjectRequests((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectInvites((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? buildInviteMeta(normalizedName) }));
    setProjectTasks((previous) => ({ ...previous, [normalizedName]: previous[normalizedName] ?? [] }));
    setProjectKnowledge((previous) => ({ ...previous, [spaceId]: previous[spaceId] ?? [] }));
    setProjectAiSpaceIds((previous) => (previous.includes(spaceId) ? previous : [...previous, spaceId]));

    setData((previous) => {
      const nextSpace = {
        id: spaceId,
        name: created.name,
        members: seededMembers.length ? "멤버 1명" : "멤버 0명",
        meetings: "진행 회의 0건",
        updatedAt: "방금 업데이트",
        description: created.description?.trim() || "새 프로젝트 설명이 아직 작성되지 않았습니다.",
        href: `/spaces/${encodeURIComponent(spaceId)}`
      };

      return {
        ...previous,
        workspaceHome: {
          ...previous.workspaceHome,
          spaces: [nextSpace, ...previous.workspaceHome.spaces],
          recent: [{ title: `${normalizedName} · 프로젝트 생성`, meta: "방금 전" }, ...previous.workspaceHome.recent].slice(0, 6)
        }
      };
    });
  }

  async function handleUpdateProject(spaceId: string, { name, description }: UpdateProjectPayload): Promise<boolean> {
    const normalizedName = name.trim();
    if (!normalizedName) {
      return false;
    }

    const currentSpace = data.workspaceHome.spaces.find((space) => space.id === spaceId);
    if (!currentSpace) {
      return false;
    }

    const previousName = currentSpace.name;
    if (projectAiSpaceIds.includes(spaceId)) {
      if (!authSession) {
        setMeetingMutationError("로그인이 필요합니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await updateSpace(authSession, spaceId, { name: normalizedName, description: description.trim() || null });
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "프로젝트 정보를 수정하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.map((space) =>
          space.id === spaceId
            ? {
                ...space,
                name: normalizedName,
                description: description.trim(),
                updatedAt: "방금 업데이트"
              }
            : space
        ),
        recent: [{ title: `${normalizedName} · 프로젝트 정보 수정`, meta: "방금 전" }, ...previous.workspaceHome.recent].slice(0, 6)
      }
    }));

    if (previousName !== normalizedName) {
      setProjectMeetings((previous) => {
        const next = { ...previous };
        next[normalizedName] = next[previousName] ?? [];
        delete next[previousName];
        return next;
      });
      setProjectMembers((previous) => {
        const next = { ...previous };
        next[normalizedName] = next[previousName] ?? [];
        delete next[previousName];
        return next;
      });
      setProjectRequests((previous) => {
        const next = { ...previous };
        next[normalizedName] = next[previousName] ?? [];
        delete next[previousName];
        return next;
      });
      setProjectInvites((previous) => {
        const next = { ...previous };
        next[normalizedName] = next[previousName] ?? buildInviteMeta(normalizedName);
        delete next[previousName];
        return next;
      });
      setProjectTasks((previous) => {
        const next = { ...previous };
        next[normalizedName] = (next[previousName] ?? []).map((task) => ({
          ...task,
          meetingKey: task.meetingKey?.replace(`${previousName}:`, `${normalizedName}:`) ?? null
        }));
        delete next[previousName];
        return next;
      });
      setMeetingParticipants((previous) => {
        const next: Record<string, MeetingParticipantState[]> = {};
        Object.entries(previous).forEach(([meetingKey, participants]) => {
          const targetKey = meetingKey.startsWith(`${previousName}:`)
            ? meetingKey.replace(`${previousName}:`, `${normalizedName}:`)
            : meetingKey;
          next[targetKey] = participants.map((participant) => ({
            ...participant,
            meetingKey: participant.meetingKey.startsWith(`${previousName}:`)
              ? participant.meetingKey.replace(`${previousName}:`, `${normalizedName}:`)
              : participant.meetingKey
          }));
        });
        return next;
      });
    }
    return true;
  }

  async function handleDeleteProject(spaceId: string): Promise<boolean> {
    const currentSpace = data.workspaceHome.spaces.find((space) => space.id === spaceId);
    if (!currentSpace) {
      return false;
    }

    if (projectAiSpaceIds.includes(spaceId)) {
      if (!authSession) {
        setMeetingMutationError("로그인이 필요합니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        const result = await deleteSpace(authSession, spaceId);
        if (!result.deleted) {
          setMeetingMutationError("프로젝트를 삭제하지 못했습니다.");
          return false;
        }
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "프로젝트를 삭제하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.filter((space) => space.id !== spaceId),
        recent: [{ title: `${currentSpace.name} · 프로젝트 삭제`, meta: "방금 전" }, ...previous.workspaceHome.recent].slice(0, 6)
      }
    }));

    setProjectMeetings((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setProjectMembers((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setProjectRequests((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setProjectInvites((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setLatestMeetingInvites((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setProjectTasks((previous) => {
      const next = { ...previous };
      delete next[currentSpace.name];
      return next;
    });
    setMeetingParticipants((previous) => {
      const next: Record<string, MeetingParticipantState[]> = {};
      Object.entries(previous).forEach(([meetingKey, participants]) => {
        if (
          !meetingKey.startsWith(`${currentSpace.name}:`) &&
          !meetingKey.startsWith(`target:${currentSpace.id}:`)
        ) {
          next[meetingKey] = participants;
        }
      });
      return next;
    });

    navigate("/spaces");
    return true;
  }

  async function handleCreateMeeting(projectName: string, payload?: CreateMeetingPayload): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (!targetSpace) {
      return false;
    }

    const usesTargetApi = projectAiSpaceIds.includes(targetSpace.id);
    if (usesTargetApi) {
      if (!authSession || !payload?.title || !payload.scheduledAt || !payload.scheduledEndAt) {
        setMeetingMutationError("로그인과 회의 제목, 예정 시작·종료 일시가 필요합니다.");
        return false;
      }
      const meetingTitle = payload.title;
      const scheduledAt = payload.scheduledAt;
      const selectedMembers = (projectMembers[projectName] ?? []).filter((member) =>
        payload.participantEmails?.includes(member.email)
      );
      if (payload.participantEmails?.some((email) => !selectedMembers.some((member) => member.email === email && member.userId))) {
        setMeetingMutationError("선택한 참여자의 Backend 사용자 정보를 찾을 수 없습니다.");
        return false;
      }
      const participantUserIds = selectedMembers
        .map((member) => member.userId)
        .filter((userId): userId is string => Boolean(userId) && userId !== authSession.user.id);
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        const created = await createMeeting(authSession, targetSpace.id, {
          title: meetingTitle,
          description: payload.description,
          scheduledAt,
          scheduledEndAt: payload.scheduledEndAt,
          participantUserIds
        });
        setLatestMeetingInvites((previous) => ({
          ...previous,
          [projectName]: {
            meetingId: created.id,
            title: meetingTitle,
            joinCode: created.joinCode,
            joinUrl: created.joinUrl
          }
        }));
        const detailComplete = await refreshTargetMeetings(authSession, targetSpace.id, projectName);
        if (!detailComplete) {
          setMeetingMutationError("회의는 생성됐지만 일부 상세 정보를 다시 불러오지 못했습니다.");
        }
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "회의를 생성하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    const existingMeetings = projectMeetings[projectName] ?? [];
    const nextMeeting = buildMeeting(projectName, targetSpace.description, existingMeetings.length + 1, payload);

    setProjectMeetings((previous) => {
      return {
        ...previous,
        [projectName]: [...(previous[projectName] ?? []), nextMeeting]
      };
    });

    if (payload?.participantEmails?.length) {
      const meetingKey = buildMeetingKey(projectName, nextMeeting.index);
      const selectedMembers = (projectMembers[projectName] ?? []).filter((member) =>
        payload.participantEmails?.includes(member.email)
      );

      setMeetingParticipants((previous) => ({
        ...previous,
        [meetingKey]: selectedMembers.map((member, index) => ({
          id: `${meetingKey}-${member.email}`,
          meetingKey,
          name: member.name,
          email: member.email,
          role: index === 0 ? "HOST" : "VIEWER",
          accessStatus: "ACTIVE",
          participantType: "member"
        }))
      }));
    }

    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.map((space) =>
          space.name === projectName
            ? {
                ...space,
                meetings: `진행 회의 ${Number(space.meetings.match(/\d+/)?.[0] ?? 0) + 1}건`,
                updatedAt: "방금 업데이트"
              }
            : space
        ),
        recent: [{ title: `${projectName} · 새 회의 생성`, meta: "방금 전" }, ...previous.workspaceHome.recent].slice(0, 6)
      }
    }));
    return true;
  }

  async function handleDeleteMeeting(projectName: string, meetingIndex: string): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (!targetSpace) {
      return false;
    }

    const meeting = (projectMeetings[projectName] ?? []).find((item) => item.index === meetingIndex);
    if (projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !meeting?.id) {
        setMeetingMutationError("삭제할 Backend 회의 정보를 찾을 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await deleteMeeting(authSession, meeting.id);
        await refreshTargetMeetings(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "회의를 삭제하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    const meetingKey = buildMeetingKey(projectName, meetingIndex);
    setProjectMeetings((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).filter((meeting) => meeting.index !== meetingIndex)
    }));
    setMeetingParticipants((previous) => {
      const next = { ...previous };
      delete next[meetingKey];
      return next;
    });
    setProjectTasks((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).map((task) =>
        task.meetingKey === meetingKey ? { ...task, meetingKey: null } : task
      )
    }));
    setData((previous) => ({
      ...previous,
      workspaceHome: {
        ...previous.workspaceHome,
        spaces: previous.workspaceHome.spaces.map((space) =>
          space.name === projectName
            ? {
                ...space,
                meetings: `진행 회의 ${Math.max(Number(space.meetings.match(/\d+/)?.[0] ?? 1) - 1, 0)}건`,
                updatedAt: "방금 업데이트"
              }
            : space
        ),
        recent: [{ title: `${targetSpace.name} · 회의 삭제`, meta: "방금 전" }, ...previous.workspaceHome.recent].slice(0, 6)
      }
    }));
    return true;
  }

  async function handleUpdateMeeting(
    projectName: string,
    meetingIndex: string,
    updates: UpdateMeetingRequest
  ): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    const meeting = (projectMeetings[projectName] ?? []).find((item) => item.index === meetingIndex);
    if (!targetSpace || !meeting) {
      return false;
    }

    if (projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !meeting.id) {
        setMeetingMutationError("수정할 Backend 회의 정보를 찾을 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await updateMeeting(authSession, meeting.id, updates);
        await refreshTargetMeetings(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "회의를 수정하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    setProjectMeetings((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).map((current) => {
        if (current.index !== meetingIndex) {
          return current;
        }
        const scheduledAt = updates.scheduledAt ?? current.scheduledAt;
        const scheduledEndAt = updates.scheduledEndAt ?? current.scheduledEndAt;
        const date = scheduledAt ? new Date(scheduledAt) : null;
        return {
          ...current,
          title: updates.title ?? current.title,
          description: updates.description ?? current.description,
          scheduledAt,
          scheduledEndAt,
          durationMinutes: scheduledAt && scheduledEndAt
            ? Math.max(1, Math.round((new Date(scheduledEndAt).getTime() - new Date(scheduledAt).getTime()) / 60_000))
            : current.durationMinutes,
          date: date
            ? `${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`
            : current.date,
          state: updates.status ? meetingStatusLabel(updates.status) : current.state
        };
      })
    }));
    return true;
  }

  async function handleUpdateMeetingStatus(
    projectName: string,
    meetingIndex: string,
    state: ProjectMeeting["state"]
  ): Promise<boolean> {
    const status = meetingStateStatus(state);
    if (!status) {
      setMeetingMutationError("지원하지 않는 회의 상태입니다.");
      return false;
    }
    return handleUpdateMeeting(projectName, meetingIndex, { status });
  }

  async function handleUpdateMeetingDetails(
    projectName: string,
    meetingIndex: string,
    updates: Pick<UpdateMeetingRequest, "title" | "description" | "scheduledAt" | "scheduledEndAt">
  ): Promise<boolean> {
    return handleUpdateMeeting(projectName, meetingIndex, updates);
  }

  async function handleAddMeetingParticipant(
    projectName: string,
    meetingIndex: string,
    participant: Pick<MeetingParticipantState, "email" | "name" | "role" | "participantType" | "userId">
  ): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    const meeting = (projectMeetings[projectName] ?? []).find((item) => item.index === meetingIndex);
    if (targetSpace && projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !meeting?.id || !participant.userId) {
        setMeetingMutationError("추가할 참여자의 Backend 사용자 정보를 찾을 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await addMeetingParticipant(authSession, meeting.id, {
          userId: participant.userId,
          role: participant.role,
          participantType: participant.participantType
        });
        await refreshTargetMeetings(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "회의 참여자를 추가하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    const meetingKey = buildMeetingKey(projectName, meetingIndex);
    setMeetingParticipants((previous) => {
      const currentParticipants = previous[meetingKey] ?? [];
      const existingParticipant = currentParticipants.find((item) => item.email === participant.email);
      const activeHostCount = currentParticipants.filter(
        (item) => item.role === "HOST" && item.accessStatus === "ACTIVE"
      ).length;
      const keepsLastHost =
        existingParticipant?.role === "HOST" &&
        existingParticipant.accessStatus === "ACTIVE" &&
        activeHostCount === 1 &&
        participant.role !== "HOST";
      const nextParticipant: MeetingParticipantState = {
        id: existingParticipant?.id ?? `${meetingKey}-${participant.email}`,
        meetingKey,
        name: participant.name,
        email: participant.email,
        role: keepsLastHost ? "HOST" : participant.role,
        accessStatus: "ACTIVE",
        participantType: participant.participantType
      };

      return {
        ...previous,
        [meetingKey]: existingParticipant
          ? currentParticipants.map((item) => (item.email === participant.email ? nextParticipant : item))
          : [...currentParticipants, nextParticipant]
      };
    });
    return true;
  }

  async function handleUpdateMeetingParticipant(
    projectName: string,
    meetingIndex: string,
    participantId: string,
    updates: Pick<MeetingParticipantState, "accessStatus" | "role">
  ): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    const meeting = (projectMeetings[projectName] ?? []).find((item) => item.index === meetingIndex);
    if (targetSpace && projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !meeting?.id) {
        setMeetingMutationError("수정할 회의 참여자 정보를 찾을 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await updateMeetingParticipant(authSession, meeting.id, participantId, updates);
        await refreshTargetMeetings(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "회의 참여자 권한을 변경하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    const meetingKey = buildMeetingKey(projectName, meetingIndex);
    setMeetingParticipants((previous) => {
      const currentParticipants = previous[meetingKey] ?? [];
      const activeHostCount = currentParticipants.filter(
        (participant) => participant.role === "HOST" && participant.accessStatus === "ACTIVE"
      ).length;

      return {
        ...previous,
        [meetingKey]: currentParticipants.map((participant) => {
          if (participant.id !== participantId) {
            return participant;
          }

          const wouldRemoveLastHost =
            participant.role === "HOST" &&
            participant.accessStatus === "ACTIVE" &&
            activeHostCount === 1 &&
            (updates.role !== "HOST" || updates.accessStatus !== "ACTIVE");

          return wouldRemoveLastHost ? participant : { ...participant, ...updates };
        })
      };
    });
    return true;
  }

  async function handleCreateProjectTask(
    projectName: string,
    task: Omit<ProjectTaskState, "id" | "sourceCandidateId">
  ): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (targetSpace && projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession) {
        setMeetingMutationError("로그인이 필요합니다.");
        return false;
      }
      const assigneeId = projectMembers[projectName]?.find((member) => member.name === task.assignee)?.userId ?? null;
      const meetingKeyParts = task.meetingKey?.split(":") ?? [];
      const meetingIndex = meetingKeyParts[meetingKeyParts.length - 1];
      const meetingId = meetingIndex
        ? projectMeetings[projectName]?.find((meeting) => meeting.index === meetingIndex)?.id ?? null
        : null;
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await createTask(authSession, targetSpace.id, {
          title: task.title,
          description: task.description,
          assigneeId,
          dueDate: task.dueDate || null,
          meetingId,
          priority: task.priority,
          labels: task.labels
        });
        await refreshTargetTasks(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "칸반 카드를 생성하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }
    setProjectTasks((previous) => {
      const currentTasks = previous[projectName] ?? [];
      return {
        ...previous,
        [projectName]: [
          ...currentTasks,
          {
            ...task,
            id: `task-${buildSpaceId(projectName)}-${String(currentTasks.length + 1).padStart(3, "0")}`,
            sourceCandidateId: null
          }
        ]
      };
    });
    return true;
  }

  async function handleMoveProjectTask(projectName: string, taskId: string, status: ProjectTaskState["status"]): Promise<boolean> {
    return handleUpdateProjectTask(projectName, taskId, { status });
  }

  async function handleUpdateProjectTask(
    projectName: string,
    taskId: string,
    updates: Partial<Pick<ProjectTaskState, "assignee" | "description" | "dueDate" | "status" | "priority" | "labels" | "title">>
  ): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (targetSpace && projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession) {
        setMeetingMutationError("로그인이 필요합니다.");
        return false;
      }
      const assigneeId = updates.assignee === undefined
        ? undefined
        : projectMembers[projectName]?.find((member) => member.name === updates.assignee)?.userId ?? null;
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await updateTask(authSession, targetSpace.id, taskId, {
          title: updates.title,
          description: updates.description,
          assigneeId,
          dueDate: updates.dueDate === undefined ? undefined : updates.dueDate || null,
          status: updates.status,
          priority: updates.priority,
          labels: updates.labels
        });
        await refreshTargetTasks(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "칸반 카드를 수정하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }
    setProjectTasks((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).map((task) => (task.id === taskId ? { ...task, ...updates } : task))
    }));
    return true;
  }

  async function handleDeleteProjectTask(projectName: string, taskId: string): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (targetSpace && projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession) {
        setMeetingMutationError("로그인이 필요합니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        const result = await deleteTask(authSession, targetSpace.id, taskId);
        if (!result.deleted) {
          setMeetingMutationError("칸반 카드를 삭제하지 못했습니다.");
          return false;
        }
        await refreshTargetTasks(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "칸반 카드를 삭제하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }
    setProjectTasks((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).filter((task) => task.id !== taskId)
    }));
    return true;
  }

  async function handleCreateProjectKnowledge(
    spaceId: string,
    request: CreateProjectKnowledgeRequest
  ): Promise<boolean> {
    if (!authSession) {
      setMeetingMutationError("로그인이 필요합니다.");
      return false;
    }
    setMeetingMutationError("");
    setMeetingMutationLoading(true);
    try {
      await createProjectKnowledge(authSession, spaceId, request);
      await refreshProjectKnowledge(authSession, spaceId);
      return true;
    } catch (error) {
      setMeetingMutationError(error instanceof Error ? error.message : "공식 지식을 등록하지 못했습니다.");
      return false;
    } finally {
      setMeetingMutationLoading(false);
    }
  }

  async function handleUpdateProjectKnowledge(
    spaceId: string,
    knowledgeId: string,
    request: UpdateProjectKnowledgeRequest
  ): Promise<boolean> {
    if (!authSession) {
      setMeetingMutationError("로그인이 필요합니다.");
      return false;
    }
    setMeetingMutationError("");
    setMeetingMutationLoading(true);
    try {
      await updateProjectKnowledge(authSession, spaceId, knowledgeId, request);
      await refreshProjectKnowledge(authSession, spaceId);
      return true;
    } catch (error) {
      setMeetingMutationError(error instanceof Error ? error.message : "공식 지식을 수정하지 못했습니다.");
      return false;
    } finally {
      setMeetingMutationLoading(false);
    }
  }

  async function handleDeleteProjectKnowledge(spaceId: string, knowledgeId: string): Promise<boolean> {
    if (!authSession) {
      setMeetingMutationError("로그인이 필요합니다.");
      return false;
    }
    setMeetingMutationError("");
    setMeetingMutationLoading(true);
    try {
      const result = await deleteProjectKnowledge(authSession, spaceId, knowledgeId);
      if (!result.deleted) {
        return false;
      }
      await refreshProjectKnowledge(authSession, spaceId);
      return true;
    } catch (error) {
      setMeetingMutationError(error instanceof Error ? error.message : "공식 지식을 삭제하지 못했습니다.");
      return false;
    } finally {
      setMeetingMutationLoading(false);
    }
  }

  async function handleCreateSpaceInvitation(
    spaceId: string,
    email: string,
    role: "ADMIN" | "MEMBER"
  ): Promise<{ invitationId: string; inviteUrl: string; expiresAt: string }> {
    if (!authSession) {
      throw new Error("로그인이 필요합니다.");
    }
    if (!projectAiSpaceIds.includes(spaceId)) {
      throw new Error("데모 Space에서는 이메일 초대를 생성할 수 없습니다.");
    }
    setMeetingMutationError("");
    setMeetingMutationLoading(true);
    try {
      const invitation = await createSpaceInvitation(authSession, spaceId, { email, role });
      const invitationPath = `/space-invitations/${encodeURIComponent(spaceId)}/${encodeURIComponent(invitation.invitationId)}`;
      return {
        invitationId: invitation.invitationId,
        inviteUrl: `${window.location.origin}${invitationPath}#token=${encodeURIComponent(invitation.inviteToken)}`,
        expiresAt: invitation.expiresAt
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : "Space 초대를 생성하지 못했습니다.";
      setMeetingMutationError(message);
      throw new Error(message);
    } finally {
      setMeetingMutationLoading(false);
    }
  }

  function handleApproveJoinRequest(projectName: string, requestId: string) {
    const request = projectRequests[projectName]?.find((item) => item.id === requestId);
    if (!request) {
      return;
    }

    setProjectRequests((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).filter((item) => item.id !== requestId)
    }));

    const meetingKey = buildMeetingKey(projectName, request.meetingIndex);
    setMeetingParticipants((previous) => ({
      ...previous,
      [meetingKey]: [
        ...(previous[meetingKey] ?? []).filter((participant) => participant.email !== request.email),
        {
          id: `${meetingKey}-${request.email}`,
          meetingKey,
          name: request.name,
          email: request.email,
          role: "VIEWER",
          accessStatus: "ACTIVE",
          participantType: "guest"
        }
      ]
    }));
  }

  function handleRejectJoinRequest(projectName: string, requestId: string) {
    setProjectRequests((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).filter((item) => item.id !== requestId)
    }));
  }

  async function handleUpdateSpaceMemberRole(
    projectName: string,
    memberId: string | undefined,
    memberEmail: string,
    spaceRole: Exclude<TeamMember["spaceRole"], "OWNER">
  ): Promise<boolean> {
    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (targetSpace && projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !memberId) {
        setMeetingMutationError("대상 멤버 정보를 확인할 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await updateSpaceMemberRole(authSession, targetSpace.id, memberId, { role: spaceRole });
        await refreshTargetMembers(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "멤버 역할을 변경하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }
    setProjectMembers((previous) => ({
      ...previous,
      [projectName]: (previous[projectName] ?? []).map((member) =>
        member.email === memberEmail && member.spaceRole !== "OWNER"
          ? { ...member, spaceRole, access: getSpaceRoleAccessLabel(spaceRole) }
          : member
      )
    }));
    return true;
  }

  async function handleRemoveSpaceMember(
    projectName: string,
    memberId: string | undefined,
    memberEmail: string
  ): Promise<boolean> {
    const member = projectMembers[projectName]?.find((item) => item.email === memberEmail);
    if (!member || member.spaceRole === "OWNER") {
      return false;
    }

    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (targetSpace && projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !memberId) {
        setMeetingMutationError("대상 멤버 정보를 확인할 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        const result = await removeSpaceMember(authSession, targetSpace.id, memberId);
        if (!result.removed) {
          setMeetingMutationError("멤버를 제거하지 못했습니다.");
          return false;
        }
        await refreshTargetMembers(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "멤버를 제거하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    setProjectMembers((previous) => {
      const nextMembers = (previous[projectName] ?? []).filter((item) => item.email !== memberEmail);

      setData((current) => ({
        ...current,
        workspaceHome: {
          ...current.workspaceHome,
          spaces: current.workspaceHome.spaces.map((space) =>
            space.name === projectName ? { ...space, members: `멤버 ${nextMembers.length}명`, updatedAt: "방금 업데이트" } : space
          )
        }
      }));

      return {
        ...previous,
        [projectName]: nextMembers
      };
    });

    setMeetingParticipants((previous) => {
      const next: Record<string, MeetingParticipantState[]> = {};
      Object.entries(previous).forEach(([meetingKey, participants]) => {
        next[meetingKey] = meetingKey.startsWith(`${projectName}:`)
          ? participants.map((participant) =>
              participant.email === memberEmail ? { ...participant, participantType: "guest" } : participant
            )
          : participants;
      });
      return next;
    });
    return true;
  }

  async function handleTransferProjectOwner(
    projectName: string,
    targetMemberId: string | undefined,
    targetMemberEmail: string,
    previousOwnerRole: Exclude<TeamMember["spaceRole"], "OWNER">,
    confirmation: string
  ): Promise<boolean> {
    if (confirmation !== "TRANSFER OWNER") {
      return false;
    }

    const targetSpace = data.workspaceHome.spaces.find((space) => space.name === projectName);
    if (targetSpace && projectAiSpaceIds.includes(targetSpace.id)) {
      if (!authSession || !targetMemberId) {
        setMeetingMutationError("새 owner 정보를 확인할 수 없습니다.");
        return false;
      }
      setMeetingMutationError("");
      setMeetingMutationLoading(true);
      try {
        await transferSpaceOwner(authSession, targetSpace.id, {
          targetMemberId,
          previousOwnerRole,
          confirmation
        });
        await refreshTargetMembers(authSession, targetSpace.id, projectName);
        return true;
      } catch (error) {
        setMeetingMutationError(error instanceof Error ? error.message : "Owner 권한을 이양하지 못했습니다.");
        return false;
      } finally {
        setMeetingMutationLoading(false);
      }
    }

    setProjectMembers((previous) => {
      const currentMembers = previous[projectName] ?? [];
      const currentOwner = currentMembers.find((member) => member.spaceRole === "OWNER");
      const targetMember = currentMembers.find((member) => member.email === targetMemberEmail);

      if (!currentOwner || !targetMember || targetMember.status !== "active" || targetMember.spaceRole === "OWNER") {
        return previous;
      }

      return {
        ...previous,
        [projectName]: currentMembers.map((member) => {
          if (member.email === targetMember.email) {
            return { ...member, spaceRole: "OWNER", access: getSpaceRoleAccessLabel("OWNER") };
          }

          if (member.email === currentOwner.email) {
            return { ...member, spaceRole: previousOwnerRole, access: getSpaceRoleAccessLabel(previousOwnerRole) };
          }

          return member;
        })
      };
    });
    return true;
  }
  return {
    handleCreateProject,
    handleUpdateProject,
    handleDeleteProject,
    handleCreateMeeting,
    handleDeleteMeeting,
    handleUpdateMeeting,
    handleUpdateMeetingStatus,
    handleUpdateMeetingDetails,
    handleAddMeetingParticipant,
    handleUpdateMeetingParticipant,
    handleCreateProjectTask,
    handleMoveProjectTask,
    handleUpdateProjectTask,
    handleDeleteProjectTask,
    handleCreateProjectKnowledge,
    handleUpdateProjectKnowledge,
    handleDeleteProjectKnowledge,
    handleCreateSpaceInvitation,
    handleApproveJoinRequest,
    handleRejectJoinRequest,
    handleUpdateSpaceMemberRole,
    handleRemoveSpaceMember,
    handleTransferProjectOwner,
  };
}
