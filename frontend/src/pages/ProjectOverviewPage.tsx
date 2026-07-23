import { useEffect, useMemo, useRef, useState, type DragEvent, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { chatProjectAi, fetchProjectAiHistory } from "../api/ai";
import { fetchProjectKnowledgeDetail } from "../api/knowledge";
import type { AuthSession } from "../auth/session";
import { DataState } from "../components/common/DataState";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { SpaceLayout } from "../components/layout/SpaceLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type {
  CreateProjectKnowledgeRequest,
  ProjectKnowledgeItem,
  ProjectKnowledgeType,
  TaskCardPriority,
  UnsupportedReason,
  UpdateProjectKnowledgeRequest,
  WorkspaceData
} from "../types";

type ProjectChatMessage = {
  role: "user" | "ai";
  text: string;
  tags?: string[];
  unsupportedReason?: UnsupportedReason | null;
};

function unsupportedProjectMessage(reason: UnsupportedReason | null): string {
  switch (reason) {
    case "LOW_RELEVANCE":
      return "검색된 프로젝트 기록은 있지만 질문에 답할 만큼 관련성이 높지 않습니다.";
    case "MODEL_UNSUPPORTED":
      return "제공된 프로젝트 근거만으로는 답변을 확정할 수 없습니다.";
    case "UNVERIFIED_OUTPUT":
      return "응답의 근거를 확인하지 못해 답변을 제공할 수 없습니다.";
    case "NO_EVIDENCE":
    default:
      return "접근 가능한 프로젝트 기록에서 확인 가능한 근거가 없습니다.";
  }
}

const projectKnowledgeTypeLabels: Record<ProjectKnowledgeType, string> = {
  decision: "결정",
  external: "외부 자료",
  manual: "직접 등록",
  report: "회의록"
};

const projectKnowledgeEmbeddingLabels: Record<ProjectKnowledgeItem["embeddingStatus"], string> = {
  COMPLETED: "검색 가능",
  FAILED: "처리 실패",
  PENDING: "처리 대기",
  PROCESSING: "처리 중"
};

type ProjectMeeting = WorkspaceData["projectOverview"]["meetings"][number];
type MeetingParticipantState = {
  id: string;
  userId?: string;
  meetingKey: string;
  name: string;
  email: string;
  role: "HOST" | "EDITOR" | "VIEWER";
  accessStatus: "ACTIVE" | "REVOKED";
  participantType: "member" | "guest";
};
type ProjectTaskState = {
  id: string;
  title: string;
  description: string;
  status: "TODO" | "IN_PROGRESS" | "DONE";
  priority: TaskCardPriority;
  labels: string[];
  assignee: string;
  dueDate: string;
  meetingKey: string | null;
  sourceCandidateId: string | null;
};
type TeamMemberState = {
  userId?: string;
  name: string;
  email: string;
  role: string;
  spaceRole: "OWNER" | "ADMIN" | "MEMBER";
  since: string;
  access: string;
  rank: string;
  status: "active" | "away";
};
type MeetingSort = "recent" | "oldest" | "state";
type TaskStatus = ProjectTaskState["status"];
type TaskStatusFilter = TaskStatus | "ALL";
type TaskEditDraft = Pick<ProjectTaskState, "assignee" | "description" | "dueDate" | "status" | "priority" | "labels" | "title">;
type MeetingInviteMeta = {
  meetingId: string;
  title: string;
  joinCode: string;
  joinUrl: string;
};

type ProjectKnowledge = {
  finalDecision: string;
  ownerStructure: string;
  prompts: string[];
  context: string[];
  heroStatus: string;
  heroDescription: string;
};

function inferProjectTrack(projectName: string, description: string) {
  const source = `${projectName} ${description}`.toLowerCase();

  if (/(security|권한|보안|접근|정책)/.test(source)) {
    return {
      summary: "권한 구조와 접근 정책, 보안 검토 흐름을 중심으로 정리하는 프로젝트입니다.",
      prompts: ["이 프로젝트에서 먼저 확정해야 할 권한 정책은 뭐야?", "보안 관점에서 다음 회의 안건을 추천해줘."],
      stack: ["Security Policy", "Access Control", "Audit Log", "Approval Flow"],
      meetings: [
        "권한 구조 초안 정리",
        "역할별 접근 정책 검토",
        "보안 예외 케이스 리뷰",
        "최종 승인 플로우 확정"
      ]
    };
  }

  if (/(data|rag|검색|ai|llm|모델|지식)/.test(source)) {
    return {
      summary: "검색 품질과 데이터 구조, AI 응답 문맥을 함께 다듬는 프로젝트입니다.",
      prompts: ["이 프로젝트에서 검색 품질을 높이려면 뭘 먼저 봐야 해?", "다음 회의에서 다룰 AI 품질 안건을 정리해줘."],
      stack: ["RAG Search", "Data Pipeline", "Prompt Flow", "Knowledge Base"],
      meetings: [
        "데이터 구조 점검",
        "검색 문맥 연결 설계",
        "응답 품질 리뷰",
        "지식 저장 구조 정리"
      ]
    };
  }

  if (/(admin|운영|ops|workflow|프로세스|자동화)/.test(source)) {
    return {
      summary: "운영 흐름과 관리자 작업 방식, 자동화 정책을 함께 정리하는 프로젝트입니다.",
      prompts: ["운영 관점에서 먼저 정리해야 할 병목은 뭐야?", "관리자 자동화 흐름을 회의 기준으로 설명해줘."],
      stack: ["Ops Workflow", "Admin Console", "Automation Rule", "Monitoring"],
      meetings: [
        "운영 플로우 킥오프",
        "관리자 작업 시나리오 정리",
        "자동화 예외 조건 검토",
        "운영 대응 정책 확정"
      ]
    };
  }

  return {
    summary: "프로젝트 목표와 핵심 흐름, 협업 범위를 단계적으로 정리하는 프로젝트입니다.",
    prompts: ["이 프로젝트의 핵심 목표를 한 문장으로 정리해줘.", "다음 회의에서 우선순위 높게 볼 안건은 뭐야?"],
    stack: ["Project Scope", "Meeting Flow", "Docs", "Team Collaboration"],
    meetings: [
      "프로젝트 범위 정의",
      "핵심 흐름 리뷰",
      "세부 안건 정리",
      "다음 단계 확정"
    ]
  };
}

function buildGeneratedProjectKnowledge(projectName: string, description: string): ProjectKnowledge {
  const track = inferProjectTrack(projectName, description);

  return {
    finalDecision: `${projectName} 프로젝트는 우선 ${track.meetings[0]}와 ${track.meetings[1]} 중심으로 회의 흐름을 정리하고, 이후 세부 실행안을 확정합니다.`,
    ownerStructure: `${projectName} 담당 리드 — 프로젝트 범위 / 회의 흐름 정리`,
    prompts: track.prompts,
    context: track.stack,
    heroStatus: "진행 중",
    heroDescription: description.trim() || track.summary
  };
}

function getProjectKnowledge(projectName: string, description: string): ProjectKnowledge {
  const knowledgeMap: Record<string, ProjectKnowledge> = {
    "FinPilot Renewal": {
      finalDecision: "권한은 회의 단위로 분리하고, Project AI는 사용자 권한 범위 내 회의만 검색 대상으로 포함한다.",
      ownerStructure: "이미주 — 권한/검색 구조 리드",
      prompts: ["우리가 왜 PostgreSQL을 선택했어?", "내가 맡기로 한 업무가 뭐야?"],
      context: ["React · TypeScript", "Spring Boot · Security", "FastAPI · LangChain", "PostgreSQL · pgvector"],
      heroStatus: "진행 중",
      heroDescription: "리뉴얼 일정과 사용자 흐름 개선, 회의 지식 연결 구조를 동시에 정리하는 프로젝트입니다."
    },
    "Campus Admin Assistant": {
      finalDecision: "관리자 권한 구조는 기능별로 세분화하고, 운영 로그는 프로젝트 문맥과 분리 저장한다.",
      ownerStructure: "정하늘 — 운영 정책 / 권한 구조 리드",
      prompts: ["관리자 권한을 왜 세분화했어?", "최근 운영 자동화 논의 흐름을 정리해줘."],
      context: ["React · Admin UI", "Spring Boot · Security", "PostgreSQL · Audit Log", "AWS S3 · Docker"],
      heroStatus: "진행 중",
      heroDescription: "운영 자동화 기능과 관리자 권한 구조를 점검하고, 회의 결과를 운영 문서로 연결하는 프로젝트입니다."
    }
  };

  return knowledgeMap[projectName] ?? buildGeneratedProjectKnowledge(projectName, description);
}

function toDateTimeLocal(value?: string) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function getMeetingDescription(meeting: ProjectMeeting) {
  if (meeting.description?.trim()) {
    return meeting.description;
  }
  if (meeting.state === "완료") {
    return "데이터셋 구조 확인 및 결정사항 정리";
  }

  if (meeting.state === "예정") {
    return "STT 보관 정책 및 관리자 권한 최종 확정";
  }

  if (meeting.state === "진행 중") {
    return "현재 진행 중인 회의입니다.";
  }

  if (meeting.state === "취소") {
    return "취소된 회의입니다.";
  }

  return "권한 기반 RAG 검색 구조 설계 결정";
}

function getMeetingStateLabel(meeting: ProjectMeeting) {
  return meeting.state;
}

function getMeetingStateTone(meeting: ProjectMeeting) {
  if (meeting.state === "완료") {
    return "green";
  }
  if (meeting.state === "예정") {
    return "orange";
  }
  if (meeting.state === "취소") {
    return "gray";
  }
  return "violet";
}

function parseMeetingDateLabel(date: string) {
  const [month, day] = date.split(".").map((value) => Number(value));
  if (!month || !day) {
    return 0;
  }

  return month * 100 + day;
}

function getMeetingStateOrder(state: ProjectMeeting["state"]) {
  if (state === "예정") {
    return 0;
  }

  if (state === "보고서 생성됨") {
    return 1;
  }

  return 2;
}

function getAllowedMeetingStates(state: ProjectMeeting["state"]): ProjectMeeting["state"][] {
  if (state === "예정") {
    return ["예정", "진행 중", "취소"];
  }
  if (state === "진행 중") {
    return ["진행 중", "완료"];
  }
  return [state];
}

function buildProjectView(
  base: WorkspaceData["projectOverview"],
  projectMeetings: Record<string, ProjectMeeting[]>,
  spaces: WorkspaceData["workspaceHome"]["spaces"],
  spaceId?: string | null,
  projectName?: string | null
) {
  const selectedSpace = spaces.find((space) => space.id === spaceId) ?? spaces.find((space) => space.name === projectName) ?? spaces[0];

  if (!selectedSpace) {
    return null;
  }

  const memberCount = selectedSpace.members.match(/\d+/)?.[0] ?? "0";
  const updatedAt = selectedSpace.updatedAt.replace(" 업데이트", "");
  const knowledge = getProjectKnowledge(selectedSpace.name, selectedSpace.description);
  const meetings = projectMeetings[selectedSpace.name] ?? [];
  const meetingCount = String(meetings.length);

  return {
    ...base,
    selectedSpace,
    knowledge,
    meetings,
    overviewTitle: `${selectedSpace.name} 프로젝트`,
    overviewSubtitle: `Space 멤버 ${memberCount}명 · 진행 회의 ${meetingCount}건 · 최근 업데이트 ${updatedAt}`,
    metrics: [
      { ...base.metrics[0], value: meetingCount, note: "진행된 회의 수" },
      { ...base.metrics[1], value: "3회차", note: "최신 보고서" },
      { ...base.metrics[2], value: "7", note: "Action Item" },
      { ...base.metrics[3], value: "5", note: "최근 결정사항" }
    ]
  };
}

function buildMeetingKey(projectName: string, meetingIndex: string) {
  return `${projectName}:${meetingIndex}`;
}

function buildDefaultMeetingParticipants(members: TeamMemberState[], meetingKey: string): MeetingParticipantState[] {
  return members.slice(0, 3).map((member, index) => ({
    id: `${meetingKey}-${member.email}`,
    meetingKey,
    name: member.name,
    email: member.email,
    role: index === 0 ? "HOST" : index === 1 ? "EDITOR" : "VIEWER",
    accessStatus: "ACTIVE",
    participantType: "member"
  }));
}

function getMeetingDestinationForSpace(space: WorkspaceData["workspaceHome"]["spaces"][number], meeting: ProjectMeeting) {
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

export function ProjectOverviewPage({
  currentUserId,
  currentUserEmail,
  data,
  session,
  projectAiSpaceIds,
  meetingMutationError,
  meetingMutationLoading = false,
  meetingReadLoading = false,
  latestMeetingInvites,
  onDeleteProject,
  onDeleteProjectKnowledge,
  meetingParticipants,
  onAddMeetingParticipant,
  projectMeetings,
  projectMembers,
  projectTasks,
  spaces,
  onCreateMeeting,
  onCreateProject,
  onCreateProjectKnowledge,
  onCreateProjectTask,
  onDeleteMeeting,
  onDeleteProjectTask,
  onMoveProjectTask,
  onUpdateProjectTask,
  onUpdateMeetingParticipant,
  onUpdateMeeting,
  onUpdateMeetingStatus,
  onUpdateProject,
  onUpdateProjectKnowledge,
  projectKnowledge
}: {
  currentUserId: string;
  currentUserEmail: string;
  data: WorkspaceData["projectOverview"];
  session: AuthSession | null;
  projectAiSpaceIds: string[];
  meetingMutationError?: string;
  meetingMutationLoading?: boolean;
  meetingReadLoading?: boolean;
  latestMeetingInvites: Record<string, MeetingInviteMeta>;
  onDeleteProject?: (spaceId: string) => Promise<boolean>;
  onDeleteProjectKnowledge?: (spaceId: string, knowledgeId: string) => Promise<boolean>;
  meetingParticipants: Record<string, MeetingParticipantState[]>;
  onAddMeetingParticipant?: (
    projectName: string,
    meetingIndex: string,
    participant: Pick<MeetingParticipantState, "email" | "name" | "role" | "participantType" | "userId">
  ) => Promise<boolean>;
  projectMeetings: Record<string, ProjectMeeting[]>;
  projectMembers: Record<string, TeamMemberState[]>;
  projectTasks: Record<string, ProjectTaskState[]>;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  onCreateMeeting?: (
    projectName: string,
    payload?: { title?: string; description?: string; scheduledAt?: string; scheduledEndAt?: string; participantEmails?: string[] }
  ) => Promise<boolean>;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  onCreateProjectKnowledge?: (spaceId: string, request: CreateProjectKnowledgeRequest) => Promise<boolean>;
  onCreateProjectTask?: (projectName: string, task: Omit<ProjectTaskState, "id" | "sourceCandidateId">) => Promise<boolean>;
  onDeleteMeeting?: (projectName: string, meetingIndex: string) => Promise<boolean>;
  onDeleteProjectTask?: (projectName: string, taskId: string) => Promise<boolean>;
  onMoveProjectTask?: (projectName: string, taskId: string, status: TaskStatus) => Promise<boolean>;
  onUpdateProjectTask?: (projectName: string, taskId: string, updates: TaskEditDraft) => Promise<boolean>;
  onUpdateMeetingParticipant?: (
    projectName: string,
    meetingIndex: string,
    participantId: string,
    updates: Pick<MeetingParticipantState, "accessStatus" | "role">
  ) => Promise<boolean>;
  onUpdateMeeting?: (
    projectName: string,
    meetingIndex: string,
    updates: { title?: string; description?: string; scheduledAt?: string; scheduledEndAt?: string }
  ) => Promise<boolean>;
  onUpdateMeetingStatus?: (
    projectName: string,
    meetingIndex: string,
    state: ProjectMeeting["state"]
  ) => Promise<boolean>;
  onUpdateProject?: (spaceId: string, payload: { name: string; description: string }) => Promise<boolean>;
  onUpdateProjectKnowledge?: (
    spaceId: string,
    knowledgeId: string,
    request: UpdateProjectKnowledgeRequest
  ) => Promise<boolean>;
  projectKnowledge: Record<string, ProjectKnowledgeItem[]>;
}) {
  useEffect(() => {
    document.body.className = "app-theme project-overview-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  const [searchParams] = useSearchParams();
  const { spaceId: routeSpaceId } = useParams<{ spaceId?: string }>();
  const spaceId = routeSpaceId ?? searchParams.get("spaceId");
  const projectName = searchParams.get("project");
  const viewData = useMemo(
    () => buildProjectView(data, projectMeetings, spaces, spaceId, projectName),
    [data, projectMeetings, projectName, spaceId, spaces]
  );
  const navigate = useNavigate();
  const chatScrollRef = useRef<HTMLDivElement | null>(null);
  const [messages, setMessages] = useState<ProjectChatMessage[]>([
    {
      role: "ai",
      text: viewData
        ? `${viewData.selectedSpace.name} 프로젝트 기준으로 답변할 수 있습니다. 궁금한 점을 물어보세요.`
        : "프로젝트 기준으로 답변할 수 있습니다. 궁금한 점을 물어보세요."
    }
  ]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [modelLabel, setModelLabel] = useState("");
  const [isMeetingsModalOpen, setIsMeetingsModalOpen] = useState(false);
  const [isProjectSettingsOpen, setIsProjectSettingsOpen] = useState(false);
  const [projectTitle, setProjectTitle] = useState("");
  const [projectDescription, setProjectDescription] = useState("");
  const [deleteConfirm, setDeleteConfirm] = useState("");
  const [meetingSearch, setMeetingSearch] = useState("");
  const [meetingSort, setMeetingSort] = useState<MeetingSort>("recent");
  const [selectedMeetingIndex, setSelectedMeetingIndex] = useState("");
  const [selectedMemberEmail, setSelectedMemberEmail] = useState("");
  const [selectedMemberRole, setSelectedMemberRole] = useState<MeetingParticipantState["role"]>("VIEWER");
  const [newMeetingTitle, setNewMeetingTitle] = useState("");
  const [newMeetingDescription, setNewMeetingDescription] = useState("");
  const [newMeetingAt, setNewMeetingAt] = useState("2026-07-10T10:00");
  const [newMeetingEndAt, setNewMeetingEndAt] = useState("2026-07-10T11:00");
  const [newMeetingParticipantEmails, setNewMeetingParticipantEmails] = useState<string[]>([]);
  const [meetingDeleteCandidate, setMeetingDeleteCandidate] = useState("");
  const [meetingDeleteConfirm, setMeetingDeleteConfirm] = useState("");
  const [taskTitle, setTaskTitle] = useState("");
  const [taskDescription, setTaskDescription] = useState("");
  const [taskAssignee, setTaskAssignee] = useState("");
  const [taskDueDate, setTaskDueDate] = useState("2026-07-20");
  const [taskMeetingIndex, setTaskMeetingIndex] = useState("");
  const [taskSearch, setTaskSearch] = useState("");
  const [taskAssigneeFilter, setTaskAssigneeFilter] = useState("ALL");
  const [taskStatusFilter, setTaskStatusFilter] = useState<TaskStatusFilter>("ALL");
  const [editingTaskId, setEditingTaskId] = useState("");
  const [taskEditDraft, setTaskEditDraft] = useState<TaskEditDraft>({
    title: "",
    description: "",
    assignee: "",
    dueDate: "",
    status: "TODO",
    priority: "MEDIUM",
    labels: []
  });
  const [knowledgeTitle, setKnowledgeTitle] = useState("");
  const [knowledgeContent, setKnowledgeContent] = useState("");
  const [knowledgeType, setKnowledgeType] = useState<ProjectKnowledgeType>("manual");
  const [editingKnowledgeId, setEditingKnowledgeId] = useState<string | null>(null);
  const [knowledgeLoading, setKnowledgeLoading] = useState(false);
  const [knowledgeError, setKnowledgeError] = useState("");
  const selectedSpaceId = viewData?.selectedSpace.id ?? "";
  const projectAiAvailable = projectAiSpaceIds.includes(selectedSpaceId);

  useEffect(() => {
    if (!viewData) {
      return;
    }

    setMessages([
      {
        role: "ai",
        text: `${viewData.selectedSpace.name} 프로젝트 기준으로 답변할 수 있습니다. 궁금한 점을 물어보세요.`
      }
    ]);
    setInput("");
    setError("");
    setModelLabel("");
    setProjectTitle(viewData.selectedSpace.name);
    setProjectDescription(viewData.selectedSpace.description);
    setDeleteConfirm("");
    setSelectedMeetingIndex(viewData.meetings[0]?.index ?? "");
    setTaskMeetingIndex(viewData.meetings[0]?.index ?? "");
    setSelectedMemberEmail("");
    setNewMeetingTitle("");
    setNewMeetingParticipantEmails([]);
    setMeetingDeleteCandidate("");
    setMeetingDeleteConfirm("");
    setTaskAssignee("");
    setTaskSearch("");
    setTaskAssigneeFilter("ALL");
    setTaskStatusFilter("ALL");
    setEditingTaskId("");
    setKnowledgeTitle("");
    setKnowledgeContent("");
    setKnowledgeType("manual");
    setEditingKnowledgeId(null);
    setKnowledgeLoading(false);
    setKnowledgeError("");
  }, [viewData]);

  useEffect(() => {
    let active = true;
    if (!session || !viewData || !projectAiAvailable) {
      return () => {
        active = false;
      };
    }

    void fetchProjectAiHistory(session, selectedSpaceId)
      .then((history) => {
        if (!active || history.messages.length === 0) {
          return;
        }
        setMessages(history.messages.map((message) => ({
          role: message.role === "USER" ? "user" : "ai",
          text: message.content
        })));
      })
      .catch(() => {
        // Chat remains available when the optional history read fails.
      });

    return () => {
      active = false;
    };
  }, [projectAiAvailable, selectedSpaceId, session, viewData]);

  useEffect(() => {
    if (!chatScrollRef.current) {
      return;
    }

    chatScrollRef.current.scrollTo({
      top: chatScrollRef.current.scrollHeight,
      behavior: "smooth"
    });
  }, [loading, messages]);

  if (!viewData) {
    return (
      <AppShell
        contentClassName="project-overview-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState
          actionLabel="프로젝트 목록으로 이동"
          onAction={() => navigate("/spaces")}
          state={spaceId ? "notFound" : "empty"}
        />
      </AppShell>
    );
  }

  const canSubmit = input.trim().length > 0 && !loading && Boolean(session) && projectAiAvailable;
  const selectedProjectName = viewData.selectedSpace.name;
  const latestMeetingInvite = latestMeetingInvites[selectedProjectName] ?? null;
  const members = projectMembers[selectedProjectName] ?? [];
  const currentSpaceMember = members.find((member) => member.email === currentUserEmail) ?? null;
  const hasManagerOverride = currentSpaceMember?.spaceRole === "OWNER" || currentSpaceMember?.spaceRole === "ADMIN";
  const canDeleteProject = currentSpaceMember?.spaceRole === "OWNER";
  const officialKnowledge = projectKnowledge[selectedSpaceId] ?? [];
  const accessibleMeetings = viewData.meetings.filter((meeting) => {
    if (projectAiAvailable) {
      return true;
    }
    if (hasManagerOverride) {
      return true;
    }
    const meetingKey = buildMeetingKey(selectedProjectName, meeting.index);
    const candidateParticipants = meetingParticipants[meetingKey] ?? buildDefaultMeetingParticipants(members, meetingKey);
    return candidateParticipants.some(
      (participant) => participant.email === currentUserEmail && participant.accessStatus === "ACTIVE"
    );
  });
  const nextMeeting = accessibleMeetings.find((meeting) => meeting.state !== "완료") ?? accessibleMeetings[0] ?? null;
  const contextMeeting = accessibleMeetings.find((meeting) => meeting.state === "보고서 생성됨") ?? accessibleMeetings[0] ?? null;
  const selectedMeeting = accessibleMeetings.find((meeting) => meeting.index === selectedMeetingIndex) ?? contextMeeting;
  const meetingStateCounts = {
    total: accessibleMeetings.length,
    scheduled: accessibleMeetings.filter((meeting) => meeting.state === "예정").length,
    inProgress: accessibleMeetings.filter((meeting) => meeting.state === "진행 중").length,
    completed: accessibleMeetings.filter((meeting) => meeting.state === "완료" || meeting.state === "보고서 생성됨").length
  };
  const selectedMeetingKey = selectedMeeting
    ? projectAiAvailable && selectedMeeting.id
      ? `target:${selectedSpaceId}:${selectedMeeting.id}`
      : buildMeetingKey(selectedProjectName, selectedMeeting.index)
    : "";
  const defaultParticipants = selectedMeetingKey && !projectAiAvailable
    ? buildDefaultMeetingParticipants(members, selectedMeetingKey)
    : [];
  const visibleParticipants = selectedMeetingKey
    ? meetingParticipants[selectedMeetingKey] ?? defaultParticipants
    : [];
  const hasStoredParticipants = selectedMeetingKey
    ? projectAiAvailable || Boolean(meetingParticipants[selectedMeetingKey])
    : false;
  const selectedProjectTasks = projectTasks[selectedProjectName] ?? [];

  const aclGrantCandidates = members.filter(
    (member) =>
      !visibleParticipants.some(
        (participant) => participant.userId === member.userId || participant.email === member.email
      )
  );
  const availableMember = aclGrantCandidates.find((member) => member.email === selectedMemberEmail) ?? null;
  const meetingInviteCandidates = members.filter(
    (member) => member.email !== currentUserEmail && member.status === "active"
  );
  const overrideMembers = members.filter((member) => member.spaceRole === "OWNER" || member.spaceRole === "ADMIN");
  const defaultDeniedMembers = selectedMeeting
    ? members.filter(
        (member) =>
          !overrideMembers.some((overrideMember) => overrideMember.email === member.email) &&
          !visibleParticipants.some(
            (participant) => participant.email === member.email && participant.accessStatus === "ACTIVE"
          )
      )
    : [];
  const normalizedMeetingQuery = meetingSearch.trim().toLowerCase();
  const filteredMeetings = accessibleMeetings
    .filter((meeting) => {
      if (!normalizedMeetingQuery) {
        return true;
      }

      return [meeting.index, meeting.title, meeting.date, meeting.state, getMeetingDescription(meeting)]
        .join(" ")
        .toLowerCase()
        .includes(normalizedMeetingQuery);
    })
    .sort((left, right) => {
      if (meetingSort === "oldest") {
        return parseMeetingDateLabel(left.date) - parseMeetingDateLabel(right.date);
      }

      if (meetingSort === "state") {
        const stateOrder = getMeetingStateOrder(left.state) - getMeetingStateOrder(right.state);
        return stateOrder !== 0 ? stateOrder : parseMeetingDateLabel(right.date) - parseMeetingDateLabel(left.date);
      }

      return parseMeetingDateLabel(right.date) - parseMeetingDateLabel(left.date);
    });
  const activeHostCount = visibleParticipants.filter(
    (participant) => participant.role === "HOST" && participant.accessStatus === "ACTIVE"
  ).length;
  const currentMeetingParticipant = visibleParticipants.find(
    (participant) =>
      participant.accessStatus === "ACTIVE" &&
      (participant.userId === currentUserId || participant.email === currentUserEmail)
  );
  const canCreateMeeting = hasManagerOverride;
  const currentMeetingRole = selectedMeeting?.myRole ?? currentMeetingParticipant?.role ?? null;
  const canManageMeetingAccess = hasManagerOverride || currentMeetingRole === "HOST";
  const canDeleteMeeting = currentSpaceMember?.spaceRole === "OWNER" || currentMeetingRole === "HOST";
  const meetingOperationLoading = meetingMutationLoading || meetingReadLoading;
  const meetingDeleteToken = selectedMeeting?.index ?? "";
  const canConfirmMeetingDelete =
    canDeleteMeeting && Boolean(selectedMeeting) && meetingDeleteCandidate === selectedMeeting?.index && meetingDeleteConfirm === meetingDeleteToken;
  const taskAssigneeOptions = Array.from(
    new Set(selectedProjectTasks.map((task) => task.assignee).filter((assignee) => assignee.trim().length > 0))
  );
  const normalizedTaskQuery = taskSearch.trim().toLowerCase();
  const filteredProjectTasks = selectedProjectTasks.filter((task) => {
    const matchesQuery =
      !normalizedTaskQuery ||
      [task.title, task.description, task.assignee, task.dueDate, task.sourceCandidateId ?? "", task.meetingKey ?? ""]
        .join(" ")
        .toLowerCase()
        .includes(normalizedTaskQuery);
    const matchesAssignee = taskAssigneeFilter === "ALL" || task.assignee === taskAssigneeFilter;
    const matchesStatus = taskStatusFilter === "ALL" || task.status === taskStatusFilter;

    return matchesQuery && matchesAssignee && matchesStatus;
  });
  const taskColumns: Array<{ key: TaskStatus; label: string }> = [
    { key: "TODO", label: "Todo" },
    { key: "IN_PROGRESS", label: "In progress" },
    { key: "DONE", label: "Done" }
  ];

  async function askProjectAi(question: string) {
    const trimmed = question.trim();
    if (!trimmed || loading) {
      return;
    }

    setMessages((previous) => [...previous, { role: "user", text: trimmed }]);
    setInput("");
    setError("");
    setLoading(true);

    try {
      if (!session) {
        throw new Error("로그인이 필요합니다.");
      }
      if (!projectAiAvailable) {
        throw new Error("프로젝트 AI 검색 데이터가 아직 준비되지 않았습니다.");
      }
      const result = await chatProjectAi(session, selectedSpaceId, { question: trimmed });
      const sourceTags = Array.from(new Set(result.sources.map((source) =>
        source.type === "projectKnowledge"
          ? `공식 지식 · ${source.title}`
          : `회의 기록 · ${source.title}`
      )));
      setModelLabel(result.model);
      setMessages((previous) => [
        ...previous,
        {
          role: "ai",
          text: result.unsupported ? unsupportedProjectMessage(result.unsupportedReason) : result.answer,
          tags: sourceTags.length
            ? sourceTags
            : result.unsupported
              ? [result.unsupportedReason === "LOW_RELEVANCE" ? "관련도 부족" : "근거 없음"]
              : undefined,
          unsupportedReason: result.unsupportedReason
        }
      ]);
    } catch (fetchError) {
      const message =
        fetchError instanceof Error ? fetchError.message : "프로젝트 AI 서비스에 연결하지 못했습니다.";
      setError(message);
      setMessages((previous) => [
        ...previous,
        {
          role: "ai",
          text: "Project AI 응답을 가져오지 못했습니다. 잠시 후 다시 시도해주세요."
        }
      ]);
    } finally {
      setLoading(false);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void askProjectAi(input);
  }

  async function handleProjectSettingsSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!viewData || !projectTitle.trim()) {
      return;
    }

    const updated = await onUpdateProject?.(viewData.selectedSpace.id, {
      name: projectTitle,
      description: projectDescription
    });
    if (updated) {
      setIsProjectSettingsOpen(false);
    }
  }

  async function handleProjectKnowledgeSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!hasManagerOverride || !knowledgeTitle.trim() || !knowledgeContent.trim() || knowledgeLoading) {
      return;
    }
    setKnowledgeError("");
    setKnowledgeLoading(true);
    try {
      const completed = editingKnowledgeId
        ? await onUpdateProjectKnowledge?.(selectedSpaceId, editingKnowledgeId, {
          title: knowledgeTitle.trim(),
          content: knowledgeContent.trim()
        })
        : await onCreateProjectKnowledge?.(selectedSpaceId, {
          type: knowledgeType,
          title: knowledgeTitle.trim(),
          content: knowledgeContent.trim()
        });
      if (!completed) {
        throw new Error(editingKnowledgeId ? "공식 지식을 수정하지 못했습니다." : "공식 지식을 등록하지 못했습니다.");
      }
      setKnowledgeTitle("");
      setKnowledgeContent("");
      setKnowledgeType("manual");
      setEditingKnowledgeId(null);
    } catch (submitError) {
      setKnowledgeError(submitError instanceof Error ? submitError.message : "공식 지식을 저장하지 못했습니다.");
    } finally {
      setKnowledgeLoading(false);
    }
  }

  async function handleEditProjectKnowledge(knowledgeId: string) {
    if (!session || !hasManagerOverride || knowledgeLoading) {
      return;
    }
    setKnowledgeError("");
    setKnowledgeLoading(true);
    try {
      const detail = await fetchProjectKnowledgeDetail(session, selectedSpaceId, knowledgeId);
      setKnowledgeTitle(detail.title);
      setKnowledgeContent(detail.content);
      setKnowledgeType(detail.type);
      setEditingKnowledgeId(detail.id);
    } catch (fetchError) {
      setKnowledgeError(fetchError instanceof Error ? fetchError.message : "공식 지식 본문을 불러오지 못했습니다.");
    } finally {
      setKnowledgeLoading(false);
    }
  }

  async function handleDeleteProjectKnowledge(knowledgeId: string) {
    if (!hasManagerOverride || knowledgeLoading) {
      return;
    }
    setKnowledgeError("");
    setKnowledgeLoading(true);
    try {
      const deleted = await onDeleteProjectKnowledge?.(selectedSpaceId, knowledgeId);
      if (!deleted) {
        throw new Error("공식 지식을 삭제하지 못했습니다.");
      }
      if (editingKnowledgeId === knowledgeId) {
        setKnowledgeTitle("");
        setKnowledgeContent("");
        setEditingKnowledgeId(null);
      }
    } catch (deleteError) {
      setKnowledgeError(deleteError instanceof Error ? deleteError.message : "공식 지식을 삭제하지 못했습니다.");
    } finally {
      setKnowledgeLoading(false);
    }
  }

  async function handleCreateMeeting(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canCreateMeeting || !newMeetingTitle.trim() || !newMeetingAt || !newMeetingEndAt || !onCreateMeeting || meetingOperationLoading) {
      return;
    }

    const created = await onCreateMeeting?.(selectedProjectName, {
      title: newMeetingTitle.trim(),
      description: newMeetingDescription,
      scheduledAt: new Date(newMeetingAt).toISOString(),
      scheduledEndAt: new Date(newMeetingEndAt).toISOString(),
      participantEmails: newMeetingParticipantEmails
    });
    if (created) {
      setNewMeetingTitle("");
      setNewMeetingDescription("");
      setNewMeetingParticipantEmails([]);
    }
  }

  async function handleDeleteProject() {
    if (!viewData) {
      return;
    }

    if (deleteConfirm !== viewData.selectedSpace.name) {
      return;
    }

    await onDeleteProject?.(viewData.selectedSpace.id);
  }

  async function handleMeetingDetailsSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedMeeting || !canManageMeetingAccess || selectedMeeting.state !== "예정") {
      return;
    }
    const form = new FormData(event.currentTarget);
    const title = String(form.get("meetingTitle") ?? "").trim();
    const scheduledAtValue = String(form.get("meetingScheduledAt") ?? "");
    const scheduledEndAtValue = String(form.get("meetingScheduledEndAt") ?? "");
    if (!title || !scheduledAtValue || !scheduledEndAtValue) {
      return;
    }
    await onUpdateMeeting?.(selectedProjectName, selectedMeeting.index, {
      title,
      description: String(form.get("meetingDescription") ?? ""),
      scheduledAt: new Date(scheduledAtValue).toISOString(),
      scheduledEndAt: new Date(scheduledEndAtValue).toISOString()
    });
  }

  function handleStartMeetingDelete(meetingIndex: string) {
    if (!canDeleteMeeting) {
      return;
    }
    setMeetingDeleteCandidate(meetingIndex);
    setMeetingDeleteConfirm("");
  }

  async function handleConfirmMeetingDelete() {
    if (!selectedMeeting || !canConfirmMeetingDelete) {
      return;
    }

    const deleted = await onDeleteMeeting?.(selectedProjectName, selectedMeeting.index);
    if (deleted) {
      setMeetingDeleteCandidate("");
      setMeetingDeleteConfirm("");
    }
  }

  async function handleAddParticipant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManageMeetingAccess || !selectedMeeting || !availableMember) {
      return;
    }

    const added = await onAddMeetingParticipant?.(selectedProjectName, selectedMeeting.index, {
      userId: availableMember.userId,
      email: availableMember.email,
      name: availableMember.name,
      role: selectedMemberRole,
      participantType: "member"
    });
    if (added) {
      setSelectedMemberEmail("");
      setSelectedMemberRole("VIEWER");
    }
  }

  function handleParticipantRoleChange(participant: MeetingParticipantState, role: MeetingParticipantState["role"]) {
    if (!selectedMeeting) {
      return;
    }

    if (isLastActiveHost(participant) && role !== "HOST") {
      return;
    }

    if (!hasStoredParticipants) {
      defaultParticipants.forEach((defaultParticipant) => {
        void onAddMeetingParticipant?.(selectedProjectName, selectedMeeting.index, {
          userId: defaultParticipant.userId,
          email: defaultParticipant.email,
          name: defaultParticipant.name,
          role: defaultParticipant.id === participant.id ? role : defaultParticipant.role,
          participantType: defaultParticipant.participantType
        });
      });
      return;
    }

    void onUpdateMeetingParticipant?.(selectedProjectName, selectedMeeting.index, participant.id, {
      accessStatus: participant.accessStatus,
      role
    });
  }

  function handleParticipantAccessChange(participant: MeetingParticipantState, accessStatus: MeetingParticipantState["accessStatus"]) {
    if (!selectedMeeting) {
      return;
    }

    if (isLastActiveHost(participant) && accessStatus !== "ACTIVE") {
      return;
    }

    if (!hasStoredParticipants) {
      defaultParticipants.forEach((defaultParticipant) => {
        void onAddMeetingParticipant?.(selectedProjectName, selectedMeeting.index, {
          userId: defaultParticipant.userId,
          email: defaultParticipant.email,
          name: defaultParticipant.name,
          role: defaultParticipant.role,
          participantType: defaultParticipant.participantType
        });
      });
    }
    void onUpdateMeetingParticipant?.(selectedProjectName, selectedMeeting.index, participant.id, {
      accessStatus,
      role: participant.role
    });
  }

  function isLastActiveHost(participant: MeetingParticipantState) {
    return participant.role === "HOST" && participant.accessStatus === "ACTIVE" && activeHostCount === 1;
  }

  async function handleCreateTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!taskTitle.trim()) {
      return;
    }

    const meetingKey = taskMeetingIndex ? buildMeetingKey(selectedProjectName, taskMeetingIndex) : null;
    const created = await onCreateProjectTask?.(selectedProjectName, {
      title: taskTitle.trim(),
      description: taskDescription.trim() || "상세 설명이 아직 작성되지 않았습니다.",
      status: "TODO",
      priority: "MEDIUM",
      labels: [],
      assignee: taskAssignee || "미지정",
      dueDate: taskDueDate,
      meetingKey
    });
    if (created) {
      setTaskTitle("");
      setTaskDescription("");
      setTaskAssignee("");
    }
  }

  function startTaskEdit(task: ProjectTaskState) {
    setEditingTaskId(task.id);
    setTaskEditDraft({
      title: task.title,
      description: task.description,
      assignee: task.assignee,
      dueDate: task.dueDate,
      status: task.status,
      priority: task.priority,
      labels: task.labels
    });
  }

  function handleTaskDragStart(event: DragEvent<HTMLElement>, taskId: string) {
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", taskId);
  }

  function handleTaskDrop(event: DragEvent<HTMLElement>, status: TaskStatus) {
    event.preventDefault();
    const taskId = event.dataTransfer.getData("text/plain");
    const task = selectedProjectTasks.find((item) => item.id === taskId);
    if (taskId && task?.status !== status) {
      void onMoveProjectTask?.(selectedProjectName, taskId, status);
    }
  }

  async function handleSaveTaskEdit(taskId: string) {
    if (!taskEditDraft.title.trim()) {
      return;
    }

    const updated = await onUpdateProjectTask?.(selectedProjectName, taskId, {
      ...taskEditDraft,
      title: taskEditDraft.title.trim(),
      description: taskEditDraft.description.trim() || "상세 설명이 아직 작성되지 않았습니다.",
      assignee: taskEditDraft.assignee.trim() || "미지정",
      labels: taskEditDraft.labels.map((label) => label.trim()).filter(Boolean)
    });
    if (updated) {
      setEditingTaskId("");
    }
  }

  return (
    <>
      <SpaceLayout
        onCreateProject={onCreateProject}
        projectName={viewData.selectedSpace.name}
        spaceId={viewData.selectedSpace.id}
      >
        <section className="project-overview-header">
          <div className="project-overview-copy">
            <p className="project-overview-breadcrumb">
              project-overview / <span>{viewData.selectedSpace.name}</span>
            </p>
            <h1>{viewData.overviewTitle}</h1>
            <div className="project-overview-subline">
              <StatusBadge
                className="project-overview-state"
                context="generic"
                label={viewData.knowledge.heroStatus}
                status={viewData.knowledge.heroStatus === "진행 중" ? "IN_PROGRESS" : "COMPLETED"}
              />
              {currentSpaceMember ? <RoleBadge role={currentSpaceMember.spaceRole} scope="space" /> : null}
              <p>{viewData.knowledge.heroDescription}</p>
            </div>
          </div>

          <div className="project-overview-actions">
            <button className="secondary" onClick={() => setIsProjectSettingsOpen(true)} type="button">
              프로젝트 설정
            </button>
            <button className="primary" onClick={() => setNewMeetingTitle(`${viewData.selectedSpace.name} 후속 회의`)} type="button">
              회의 정보 입력
            </button>
          </div>
        </section>

        <section className="project-overview-content">
          <div className="project-overview-center">
            {nextMeeting ? (
              <section className="project-next-banner">
                <div className="project-next-banner-mark">▶</div>
                <div className="project-next-banner-copy">
                  <span>다음으로 볼 회의</span>
                  <strong>
                    {nextMeeting.index.replace("#", "")}회차 - {nextMeeting.title} ({nextMeeting.date} 예정)
                  </strong>
                </div>
                <Link to={getMeetingDestinationForSpace(viewData.selectedSpace, nextMeeting)}>바로가기 →</Link>
              </section>
            ) : (
              <section className="project-next-banner empty">
                <div className="project-next-banner-mark">＋</div>
                <div className="project-next-banner-copy">
                  <span>아직 생성된 회의가 없습니다</span>
                  <strong>이 프로젝트의 첫 회의를 만들어 흐름을 시작하세요.</strong>
                </div>
                <button onClick={() => setNewMeetingTitle(`${viewData.selectedSpace.name} 첫 회의`)} type="button">회의 정보 입력</button>
              </section>
            )}

            <section className="project-list-section project-meetings-section" id="project-meetings">
              <div className="project-list-head">
                <div className="project-list-head-copy">
                  <span>Meetings</span>
                  <strong>회의 목록과 상태</strong>
                </div>
                <button className="project-list-open" onClick={() => setIsMeetingsModalOpen(true)} type="button">
                  전체보기 ›
                </button>
              </div>
              <div aria-label="회의 상태 요약" className="project-meeting-summary">
                <span><strong>{meetingStateCounts.total}</strong> 전체</span>
                <span><strong>{meetingStateCounts.scheduled}</strong> 예정</span>
                <span><strong>{meetingStateCounts.inProgress}</strong> 진행 중</span>
                <span><strong>{meetingStateCounts.completed}</strong> 완료</span>
              </div>
              <div className="project-flow-list">
                {accessibleMeetings.length ? (
                  accessibleMeetings.map((meeting, index) => (
                    <Link
                      key={meeting.index}
                      className="project-flow-row"
                      to={getMeetingDestinationForSpace(viewData.selectedSpace, meeting)}
                    >
                      <div className={`project-flow-index tone-${(index % 4) + 1}`}>{meeting.index.replace("#", "")}</div>
                      <div className="project-flow-copy">
                        <strong>{meeting.title}</strong>
                        <p>{getMeetingDescription(meeting)}</p>
                      </div>
                      <div className="project-flow-meta">
                        <span>{meeting.date}</span>
                        <StatusBadge
                          className={`project-flow-badge ${getMeetingStateTone(meeting)}`}
                          context="meeting"
                          label={getMeetingStateLabel(meeting)}
                          status={meeting.state}
                        />
                      </div>
                    </Link>
                  ))
                ) : (
                  <div className="project-flow-empty">
                    <strong>회차가 아직 없습니다</strong>
                    <p>접근 가능한 회의가 없거나 아직 회의가 생성되지 않았습니다.</p>
                  </div>
                )}
              </div>
            </section>

            <section className="project-list-section project-operations-section">
              <div className="project-list-head">
                <strong>회의 운영 / 접근 제어</strong>
                <span>{projectAiAvailable ? "PostgreSQL target API" : "데모 local state"}</span>
              </div>

              <form className="project-meeting-create-form" onSubmit={handleCreateMeeting}>
                <label>
                  <span>회의 제목</span>
                  <input
                    aria-label="새 회의 제목"
                    disabled={!canCreateMeeting || meetingOperationLoading}
                    onChange={(event) => setNewMeetingTitle(event.target.value)}
                    placeholder="예: 권한 정책 검토"
                    type="text"
                    value={newMeetingTitle}
                  />
                </label>
                <label>
                  <span>시작 일시</span>
                  <input
                    aria-label="새 회의 일시"
                    disabled={!canCreateMeeting || meetingOperationLoading}
                    onChange={(event) => setNewMeetingAt(event.target.value)}
                    type="datetime-local"
                    value={newMeetingAt}
                  />
                </label>
                <label>
                  <span>종료 일시</span>
                  <input
                    aria-label="새 회의 종료 일시"
                    disabled={!canCreateMeeting || meetingOperationLoading}
                    onChange={(event) => setNewMeetingEndAt(event.target.value)}
                    type="datetime-local"
                    value={newMeetingEndAt}
                  />
                </label>
                <label>
                  <span>설명</span>
                  <textarea
                    aria-label="새 회의 설명"
                    disabled={!canCreateMeeting || meetingOperationLoading}
                    onChange={(event) => setNewMeetingDescription(event.target.value)}
                    placeholder="예: 결정할 안건과 기대 결과"
                    value={newMeetingDescription}
                  />
                </label>
                <label>
                  <span>초기 참여자</span>
                  <select
                    aria-label="새 회의 초기 참여자"
                    disabled={!canCreateMeeting || meetingOperationLoading}
                    multiple
                    onChange={(event) =>
                      setNewMeetingParticipantEmails(
                        Array.from(event.currentTarget.selectedOptions, (option) => option.value)
                      )
                    }
                    value={newMeetingParticipantEmails}
                  >
                    {meetingInviteCandidates.map((member) => (
                      <option key={`meeting-create-member-${member.email}`} value={member.email}>
                        {member.name} · {member.spaceRole}
                      </option>
                    ))}
                  </select>
                </label>
                <button disabled={meetingOperationLoading || !canCreateMeeting || !newMeetingTitle.trim() || !newMeetingAt || !newMeetingEndAt} type="submit">회의 생성</button>
              </form>

              {meetingReadLoading ? <div className="project-operation-note">회의 상세와 참여자 정보를 불러오는 중입니다.</div> : null}
              {meetingMutationError ? <div className="meeting-ai-error">{meetingMutationError}</div> : null}
              {canCreateMeeting && latestMeetingInvite ? (
                <div className="project-meeting-invite-result">
                  <div>
                    <strong>{latestMeetingInvite.title} 참가 정보</strong>
                    <span>참가 코드는 생성 응답 직후 이 브라우저 메모리에서만 표시됩니다.</span>
                  </div>
                  <label>
                    <span>회의 참가 코드</span>
                    <input aria-label="회의 참가 코드" readOnly value={latestMeetingInvite.joinCode} />
                  </label>
                  <label>
                    <span>회의 참가 링크</span>
                    <input aria-label="회의 참가 링크" readOnly value={latestMeetingInvite.joinUrl} />
                  </label>
                  <button
                    onClick={() => void navigator.clipboard.writeText(latestMeetingInvite.joinCode)}
                    type="button"
                  >
                    코드 복사
                  </button>
                </div>
              ) : null}

              {selectedMeeting ? (
                <>
                  <div className="project-operation-toolbar">
                    <label>
                      <span>대상 회의</span>
                      <select
                        onChange={(event) => setSelectedMeetingIndex(event.target.value)}
                        value={selectedMeeting.index}
                      >
                        {accessibleMeetings.map((meeting) => (
                          <option key={`meeting-select-${meeting.index}`} value={meeting.index}>
                            {meeting.index} {meeting.title}
                          </option>
                        ))}
                      </select>
                    </label>

                    <label>
                      <span>회의 상태</span>
                      <select
                        disabled={meetingOperationLoading || !canManageMeetingAccess}
                        onChange={(event) =>
                          onUpdateMeetingStatus?.(
                            viewData.selectedSpace.name,
                            selectedMeeting.index,
                            event.target.value as ProjectMeeting["state"]
                          )
                        }
                        value={selectedMeeting.state}
                      >
                        {getAllowedMeetingStates(selectedMeeting.state).map((state) => (
                          <option key={`meeting-state-${state}`} value={state}>{state}</option>
                        ))}
                      </select>
                    </label>

                    <button
                      className="project-operation-danger"
                      disabled={meetingOperationLoading || !canDeleteMeeting}
                      onClick={() => handleStartMeetingDelete(selectedMeeting.index)}
                      type="button"
                    >
                      회의 삭제
                    </button>
                  </div>

                  <form
                    className="project-meeting-create-form"
                    key={`meeting-edit-${selectedMeeting.id ?? selectedMeeting.index}`}
                    onSubmit={handleMeetingDetailsSubmit}
                  >
                    <label>
                      <span>회의 제목 수정</span>
                      <input
                        defaultValue={selectedMeeting.title}
                        disabled={meetingOperationLoading || !canManageMeetingAccess || selectedMeeting.state !== "예정"}
                        name="meetingTitle"
                        type="text"
                      />
                    </label>
                    <label>
                      <span>예정 일시 수정</span>
                      <input
                        defaultValue={toDateTimeLocal(selectedMeeting.scheduledAt)}
                        disabled={meetingOperationLoading || !canManageMeetingAccess || selectedMeeting.state !== "예정"}
                        name="meetingScheduledAt"
                        type="datetime-local"
                      />
                    </label>
                    <label>
                      <span>예정 종료 수정</span>
                      <input
                        defaultValue={toDateTimeLocal(selectedMeeting.scheduledEndAt)}
                        disabled={meetingOperationLoading || !canManageMeetingAccess || selectedMeeting.state !== "예정"}
                        name="meetingScheduledEndAt"
                        type="datetime-local"
                      />
                    </label>
                    <label>
                      <span>회의 설명 수정</span>
                      <textarea
                        defaultValue={selectedMeeting.description ?? ""}
                        disabled={meetingOperationLoading || !canManageMeetingAccess || selectedMeeting.state !== "예정"}
                        name="meetingDescription"
                      />
                    </label>
                    <button
                      disabled={meetingOperationLoading || !canManageMeetingAccess || selectedMeeting.state !== "예정"}
                      type="submit"
                    >
                      회의 정보 저장
                    </button>
                  </form>

                  {meetingDeleteCandidate === selectedMeeting.index ? (
                    <div className="project-delete-confirm">
                      <div>
                        <strong>회의 삭제 확인</strong>
                        <p>삭제 권한은 기본 OWNER 또는 해당 회의 HOST로 제한됩니다. 계속하려면 {meetingDeleteToken} 를 입력하세요.</p>
                      </div>
                      <input
                        aria-label="회의 삭제 확인값"
                        onChange={(event) => setMeetingDeleteConfirm(event.target.value)}
                        placeholder={meetingDeleteToken}
                        type="text"
                        value={meetingDeleteConfirm}
                      />
                      <button disabled={meetingOperationLoading || !canConfirmMeetingDelete} onClick={handleConfirmMeetingDelete} type="button">
                        삭제 확정
                      </button>
                    </div>
                  ) : null}

                  <div className="project-acl-note">
                    default-deny 기준 운영 ACL 조정 화면입니다. 일반 신규 참여는 URL/코드 참가 신청과 HOST 승인을 사용합니다.
                    명시 참여자만 회의 접근 대상으로 보이고, OWNER/ADMIN은 ACL 없이 override 접근으로 표시합니다.
                    마지막 active HOST의 강등과 회수는 UI에서 예방하고 Backend 정책으로 최종 차단합니다.
                  </div>

                  <div className="project-acl-overrides">
                    <strong>owner/admin override</strong>
                    <div>
                      {overrideMembers.map((member) => (
                        <span key={`override-${member.email}`}>{member.name} · {member.spaceRole}</span>
                      ))}
                    </div>
                  </div>

                  {defaultDeniedMembers.length ? (
                    <div className="project-acl-denied">
                      <strong>default-deny</strong>
                      <span>
                        {defaultDeniedMembers.map((member) => member.name).join(", ")} 은 이 회의의 ACTIVE participant가 아니므로 회의 데이터와 AI 컨텍스트에서 제외됩니다.
                      </span>
                    </div>
                  ) : null}

                  <form className="project-acl-add" onSubmit={handleAddParticipant}>
                    <label>
                      <span>멤버</span>
                      <select
                        disabled={!canManageMeetingAccess || meetingOperationLoading}
                        onChange={(event) => setSelectedMemberEmail(event.target.value)}
                        value={selectedMemberEmail}
                      >
                        <option value="">멤버 선택</option>
                        {aclGrantCandidates.map((member) => (
                          <option key={`member-option-${member.email}`} value={member.email}>
                            {member.name} · {member.role}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      <span>회의 role</span>
                      <select
                        disabled={!canManageMeetingAccess || meetingOperationLoading}
                        onChange={(event) => setSelectedMemberRole(event.target.value as MeetingParticipantState["role"])}
                        value={selectedMemberRole}
                      >
                        <option value="VIEWER">VIEWER</option>
                        <option value="EDITOR">EDITOR</option>
                        <option value="HOST">HOST</option>
                      </select>
                    </label>
                    <button disabled={!canManageMeetingAccess || meetingOperationLoading || !selectedMemberEmail} type="submit">운영 ACL 부여</button>
                  </form>

                  <div className="project-acl-list">
                    {visibleParticipants.map((participant) => (
                      <div key={participant.id} className="project-acl-row">
                        <div>
                          <strong>{participant.name}</strong>
                          <span>
                            {participant.email}
                            {isLastActiveHost(participant) ? " · 마지막 active HOST 보호" : ""}
                          </span>
                        </div>
                        <select
                          aria-label={`${participant.name} 회의 role`}
                          disabled={!canManageMeetingAccess || meetingOperationLoading || isLastActiveHost(participant)}
                          onChange={(event) =>
                            handleParticipantRoleChange(participant, event.target.value as MeetingParticipantState["role"])
                          }
                          value={participant.role}
                        >
                          <option value="VIEWER">VIEWER</option>
                          <option value="EDITOR">EDITOR</option>
                          <option value="HOST">HOST</option>
                        </select>
                        <button
                          className={participant.accessStatus === "ACTIVE" ? "secondary" : "project-acl-restore"}
                          disabled={!canManageMeetingAccess || meetingOperationLoading || isLastActiveHost(participant)}
                          onClick={() =>
                            handleParticipantAccessChange(
                              participant,
                              participant.accessStatus === "ACTIVE" ? "REVOKED" : "ACTIVE"
                            )
                          }
                          type="button"
                        >
                          {participant.accessStatus === "ACTIVE" ? "회수" : "복구"}
                        </button>
                      </div>
                    ))}
                  </div>
                </>
              ) : (
                <div className="project-flow-empty">
                  <strong>회의 ACL을 설정할 회의가 없습니다</strong>
                  <p>먼저 이 프로젝트의 회의를 생성하세요.</p>
                </div>
              )}
            </section>

            <section className="project-list-section project-kanban-section" id="project-tasks">
              <div className="project-list-head">
                <strong>프로젝트 칸반</strong>
                <span>카드 {filteredProjectTasks.length}/{selectedProjectTasks.length}개</span>
              </div>

              <div className="project-task-filters">
                <label>
                  <span>검색</span>
                  <input
                    aria-label="태스크 검색"
                    onChange={(event) => setTaskSearch(event.target.value)}
                    placeholder="제목, 설명, 출처 후보 ID"
                    type="text"
                    value={taskSearch}
                  />
                </label>
                <label>
                  <span>담당자</span>
                  <select
                    aria-label="태스크 담당자 필터"
                    onChange={(event) => setTaskAssigneeFilter(event.target.value)}
                    value={taskAssigneeFilter}
                  >
                    <option value="ALL">전체</option>
                    {taskAssigneeOptions.map((assignee) => (
                      <option key={`task-assignee-${assignee}`} value={assignee}>
                        {assignee}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <span>상태</span>
                  <select
                    aria-label="태스크 상태 필터"
                    onChange={(event) => setTaskStatusFilter(event.target.value as TaskStatusFilter)}
                    value={taskStatusFilter}
                  >
                    <option value="ALL">전체</option>
                    <option value="TODO">TODO</option>
                    <option value="IN_PROGRESS">IN_PROGRESS</option>
                    <option value="DONE">DONE</option>
                  </select>
                </label>
              </div>

              <form className="project-task-form" onSubmit={handleCreateTask}>
                <input
                  aria-label="태스크 제목"
                  onChange={(event) => setTaskTitle(event.target.value)}
                  placeholder="태스크 제목"
                  type="text"
                  value={taskTitle}
                />
                <input
                  aria-label="담당자"
                  onChange={(event) => setTaskAssignee(event.target.value)}
                  placeholder="담당자"
                  type="text"
                  value={taskAssignee}
                />
                <input
                  aria-label="마감일"
                  onChange={(event) => setTaskDueDate(event.target.value)}
                  type="date"
                  value={taskDueDate}
                />
                <select
                  aria-label="연결 회의"
                  onChange={(event) => setTaskMeetingIndex(event.target.value)}
                  value={taskMeetingIndex}
                >
                  <option value="">회의 연결 없음</option>
                  {accessibleMeetings.map((meeting) => (
                    <option key={`task-meeting-${meeting.index}`} value={meeting.index}>
                      {meeting.index} {meeting.title}
                    </option>
                  ))}
                </select>
                <textarea
                  aria-label="태스크 설명"
                  onChange={(event) => setTaskDescription(event.target.value)}
                  placeholder="설명"
                  value={taskDescription}
                />
                <button disabled={!taskTitle.trim()} type="submit">카드 생성</button>
              </form>

              <div className="project-kanban-board">
                {taskColumns.map((column) => (
                  <div
                    key={column.key}
                    className="project-kanban-column"
                    onDragOver={(event) => event.preventDefault()}
                    onDrop={(event) => handleTaskDrop(event, column.key)}
                  >
                    <div className="project-kanban-column-head">
                      <strong>{column.label}</strong>
                      <span>{filteredProjectTasks.filter((task) => task.status === column.key).length}</span>
                    </div>
                    <div className="project-kanban-cards">
                      {filteredProjectTasks
                        .filter((task) => task.status === column.key)
                        .map((task) => (
                          <article
                            key={task.id}
                            className="project-kanban-card"
                            draggable={editingTaskId !== task.id}
                            onDragStart={(event) => handleTaskDragStart(event, task.id)}
                          >
                            {editingTaskId === task.id ? (
                              <div className="project-kanban-edit">
                                <input
                                  aria-label="카드 제목 편집"
                                  onChange={(event) => setTaskEditDraft((previous) => ({ ...previous, title: event.target.value }))}
                                  value={taskEditDraft.title}
                                />
                                <textarea
                                  aria-label="카드 설명 편집"
                                  onChange={(event) =>
                                    setTaskEditDraft((previous) => ({ ...previous, description: event.target.value }))
                                  }
                                  value={taskEditDraft.description}
                                />
                                <input
                                  aria-label="카드 담당자 편집"
                                  onChange={(event) =>
                                    setTaskEditDraft((previous) => ({ ...previous, assignee: event.target.value }))
                                  }
                                  value={taskEditDraft.assignee}
                                />
                                <input
                                  aria-label="카드 마감일 편집"
                                  onChange={(event) =>
                                    setTaskEditDraft((previous) => ({ ...previous, dueDate: event.target.value }))
                                  }
                                  type="date"
                                  value={taskEditDraft.dueDate}
                                />
                                <select
                                  aria-label="카드 상태 편집"
                                  onChange={(event) =>
                                    setTaskEditDraft((previous) => ({
                                      ...previous,
                                      status: event.target.value as TaskStatus
                                    }))
                                  }
                                  value={taskEditDraft.status}
                                >
                                  <option value="TODO">TODO</option>
                                  <option value="IN_PROGRESS">IN_PROGRESS</option>
                                  <option value="DONE">DONE</option>
                                </select>
                                <select
                                  aria-label="카드 우선순위 편집"
                                  onChange={(event) =>
                                    setTaskEditDraft((previous) => ({
                                      ...previous,
                                      priority: event.target.value as TaskCardPriority
                                    }))
                                  }
                                  value={taskEditDraft.priority}
                                >
                                  <option value="HIGH">높음</option>
                                  <option value="MEDIUM">보통</option>
                                  <option value="LOW">낮음</option>
                                </select>
                                <input
                                  aria-label="카드 라벨 편집"
                                  onChange={(event) =>
                                    setTaskEditDraft((previous) => ({
                                      ...previous,
                                      labels: event.target.value.split(",")
                                    }))
                                  }
                                  placeholder="라벨, 쉼표로 구분"
                                  value={taskEditDraft.labels.join(", ")}
                                />
                                <div className="project-kanban-edit-actions">
                                  <button disabled={!taskEditDraft.title.trim()} onClick={() => handleSaveTaskEdit(task.id)} type="button">
                                    저장
                                  </button>
                                  <button className="secondary" onClick={() => setEditingTaskId("")} type="button">
                                    취소
                                  </button>
                                </div>
                              </div>
                            ) : (
                              <>
                                <strong>{task.title}</strong>
                                <p>{task.description}</p>
                                <div className="project-kanban-meta">
                                  <span>{task.assignee}</span>
                                  <span>{task.dueDate}</span>
                                  <span>{task.priority === "HIGH" ? "높음" : task.priority === "LOW" ? "낮음" : "보통"}</span>
                                </div>
                                {task.labels.length ? (
                                  <div className="project-kanban-labels">
                                    {task.labels.map((label) => <span key={`${task.id}-${label}`}>{label}</span>)}
                                  </div>
                                ) : null}
                                <div className="project-kanban-source">
                                  {task.sourceCandidateId ? `sourceCandidateId ${task.sourceCandidateId}` : "수동 생성 카드"}
                                </div>
                                <div className="project-kanban-card-actions">
                                  <select
                                    aria-label={`${task.title} 상태 변경`}
                                    onChange={(event) =>
                                      void onMoveProjectTask?.(viewData.selectedSpace.name, task.id, event.target.value as TaskStatus)
                                    }
                                    value={task.status}
                                  >
                                    <option value="TODO">TODO</option>
                                    <option value="IN_PROGRESS">IN_PROGRESS</option>
                                    <option value="DONE">DONE</option>
                                  </select>
                                  <button onClick={() => startTaskEdit(task)} type="button">
                                    편집
                                  </button>
                                  <button onClick={() => void onDeleteProjectTask?.(viewData.selectedSpace.name, task.id)} type="button">
                                    삭제
                                  </button>
                                </div>
                              </>
                            )}
                          </article>
                        ))}
                      {!filteredProjectTasks.some((task) => task.status === column.key) ? (
                        <div className="project-kanban-empty">조건에 맞는 카드가 없습니다.</div>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
            </section>

          </div>

          <aside className="project-overview-side">
            <section aria-label="Project AI" className="project-side-block ask project-ai-surface" id="project-ai">
              <div className="project-ask-head">
                <div className="project-ask-icon">✦</div>
                <div className="project-ask-title">
                  <strong>프로젝트에게 물어보기</strong>
                  <span>{modelLabel ? `모델 ${modelLabel}` : `${viewData.selectedSpace.name} 접근 범위 내 응답`}</span>
                </div>
              </div>

              <div aria-label="Project AI 추천 질문" className="project-ask-prompts">
                {viewData.knowledge.prompts.map((prompt) => (
                  <button disabled={!session || loading || !projectAiAvailable} key={prompt} onClick={() => void askProjectAi(prompt)} type="button">
                    {prompt}
                  </button>
                ))}
              </div>

              <div aria-label="Project AI 검색 범위" className="project-ai-source-panel">
                <div>
                  <strong>Project Knowledge</strong>
                  <span>Backend가 공식 지식만 선필터</span>
                </div>
                <div>
                  <strong>Meeting record</strong>
                  <span>Backend가 접근 가능한 회의만 선필터</span>
                </div>
                <p>응답 근거가 없으면 확인 불가로 표시됩니다.</p>
              </div>

              <div aria-label="Project AI 대화" className="project-chat-history" ref={chatScrollRef} role="log">
                {messages.map((message, index) => (
                  <div key={`${message.role}-${index}`} className={`project-chat-bubble ${message.role} ${message.unsupportedReason ? "is-unsupported" : ""}`}>
                    <p>{message.text}</p>
                    {message.tags?.length ? (
                      <div aria-label="Project AI 답변 근거" className="project-chat-tags" role="list">
                        {message.tags.map((tag) => (
                          <span key={`${message.role}-${index}-${tag}`} role="listitem">{tag}</span>
                        ))}
                      </div>
                    ) : null}
                  </div>
                ))}
                {loading ? <div aria-live="polite" className="project-chat-bubble ai is-loading">답변을 정리하고 있습니다...</div> : null}
              </div>

              {!projectAiAvailable ? <p aria-live="polite" className="project-chat-error">프로젝트 AI 검색 데이터가 아직 준비되지 않았습니다.</p> : null}
              {error ? <p aria-live="assertive" className="project-chat-error">{error}</p> : null}

              <form className="project-chat-form" onSubmit={handleSubmit}>
                <input
                  aria-label="프로젝트 질문 입력"
                  onChange={(event) => setInput(event.target.value)}
                  placeholder="접근 가능한 회의와 공식 지식에 대해 질문하세요..."
                  type="text"
                  value={input}
                />
                <button disabled={!canSubmit} type="submit">
                  {loading ? "생성 중" : "전송"}
                </button>
              </form>
            </section>

            <section aria-label="공식 프로젝트 지식" className="project-side-block project-knowledge-panel knowledge-surface" id="project-knowledge">
              <div className="project-list-head">
                <div>
                  <span className="project-section-kicker">Knowledge</span>
                  <strong>공식 프로젝트 지식</strong>
                </div>
                <span className="project-knowledge-count">{officialKnowledge.length}건</span>
              </div>
              <p className="project-knowledge-intro">Project AI가 참조할 수 있는 승인된 기준과 결정입니다.</p>
              <div aria-label="공식 지식 목록" className="project-knowledge-list" role="list">
                {officialKnowledge.length ? officialKnowledge.map((knowledge) => (
                  <article className="project-knowledge-item" key={knowledge.id} role="listitem">
                    <div className="project-knowledge-item-head">
                      <strong>{knowledge.title}</strong>
                      <StatusBadge
                        className={`project-knowledge-status is-${knowledge.embeddingStatus.toLowerCase()}`}
                        context="knowledge"
                        label={projectKnowledgeEmbeddingLabels[knowledge.embeddingStatus]}
                        status={knowledge.embeddingStatus}
                      />
                    </div>
                    <span className="project-knowledge-type">{projectKnowledgeTypeLabels[knowledge.type]}</span>
                    <p>{knowledge.contentPreview}</p>
                    {hasManagerOverride ? (
                      <div className="project-knowledge-actions">
                        <button aria-label={`${knowledge.title} 편집`} disabled={knowledgeLoading} onClick={() => void handleEditProjectKnowledge(knowledge.id)} type="button">편집</button>
                        <button aria-label={`${knowledge.title} 삭제`} disabled={knowledgeLoading} onClick={() => void handleDeleteProjectKnowledge(knowledge.id)} type="button">삭제</button>
                      </div>
                    ) : null}
                  </article>
                )) : (
                  <div className="project-knowledge-empty" role="status">
                    <strong>아직 공식 지식이 없습니다.</strong>
                    <p>{hasManagerOverride ? "첫 기준이나 결정사항을 등록하면 Project AI가 검색할 수 있습니다." : "OWNER 또는 ADMIN이 등록한 기준과 결정사항이 여기에 표시됩니다."}</p>
                  </div>
                )}
              </div>
              {hasManagerOverride ? (
                <form aria-label="공식 프로젝트 지식 등록" className="project-knowledge-form" onSubmit={handleProjectKnowledgeSubmit}>
                  <div className="project-knowledge-form-heading">
                    <strong>{editingKnowledgeId ? "공식 지식 편집" : "새 공식 지식"}</strong>
                    <span>{editingKnowledgeId ? "변경 내용을 저장하세요." : "Project AI가 참조할 기준을 남기세요."}</span>
                  </div>
                  <label>
                    <span>종류</span>
                    <select
                      disabled={knowledgeLoading || Boolean(editingKnowledgeId)}
                      onChange={(event) => setKnowledgeType(event.target.value as ProjectKnowledgeType)}
                      value={knowledgeType}
                    >
                      <option value="manual">직접 등록</option>
                      <option value="decision">결정</option>
                      <option value="report">회의록</option>
                      <option value="external">외부 자료</option>
                    </select>
                  </label>
                  <label>
                    <span>제목</span>
                    <input
                      disabled={knowledgeLoading}
                      onChange={(event) => setKnowledgeTitle(event.target.value)}
                      placeholder="예: 3분기 출시 기준"
                      value={knowledgeTitle}
                    />
                  </label>
                  <label>
                    <span>내용</span>
                    <textarea
                      disabled={knowledgeLoading}
                      onChange={(event) => setKnowledgeContent(event.target.value)}
                      placeholder="Project AI가 참조할 공식 지식"
                      rows={4}
                      value={knowledgeContent}
                    />
                  </label>
                  <div className="project-knowledge-actions">
                    <button disabled={knowledgeLoading || !knowledgeTitle.trim() || !knowledgeContent.trim()} type="submit">
                      {knowledgeLoading ? "저장 중..." : editingKnowledgeId ? "수정 저장" : "공식 지식 등록"}
                    </button>
                    {editingKnowledgeId ? (
                      <button
                        disabled={knowledgeLoading}
                        onClick={() => {
                          setKnowledgeTitle("");
                          setKnowledgeContent("");
                          setKnowledgeType("manual");
                          setEditingKnowledgeId(null);
                        }}
                        type="button"
                      >
                        취소
                      </button>
                    ) : null}
                  </div>
                </form>
              ) : <p className="project-knowledge-empty">공식 지식은 OWNER 또는 ADMIN이 관리합니다.</p>}
              {knowledgeError ? <p className="project-chat-error">{knowledgeError}</p> : null}
            </section>
          </aside>
        </section>
      </SpaceLayout>

      {isMeetingsModalOpen ? (
        <div className="project-meetings-modal-backdrop" role="presentation">
          <section
            aria-labelledby="project-meetings-modal-title"
            aria-modal="true"
            className="project-meetings-modal"
            role="dialog"
          >
            <div className="project-meetings-modal-top">
              <div>
                <p className="project-meetings-modal-kicker">Meetings</p>
                <h3 id="project-meetings-modal-title">{viewData.selectedSpace.name} 회차 전체보기</h3>
              </div>
              <button
                aria-label="회차 목록 모달 닫기"
                className="project-meetings-modal-close"
                onClick={() => setIsMeetingsModalOpen(false)}
                type="button"
              >
                ×
              </button>
            </div>

            <div className="project-meetings-modal-toolbar">
              <label className="project-meetings-modal-search">
                <span>⌕</span>
                <input
                  aria-label="회차 찾기"
                  onChange={(event) => setMeetingSearch(event.target.value)}
                  placeholder="회차 제목, 상태, 날짜로 찾기"
                  type="text"
                  value={meetingSearch}
                />
              </label>

              <label className="project-meetings-modal-sort">
                <span>정렬</span>
                <select onChange={(event) => setMeetingSort(event.target.value as MeetingSort)} value={meetingSort}>
                  <option value="recent">최신 회의순</option>
                  <option value="oldest">오래된 회의순</option>
                  <option value="state">상태순</option>
                </select>
              </label>
            </div>

            <div className="project-meetings-modal-summary">
              <span>접근 가능 {accessibleMeetings.length}건</span>
              <span>검색 결과 {filteredMeetings.length}건</span>
              <span>예정 {accessibleMeetings.filter((meeting) => meeting.state === "예정").length}건</span>
            </div>

            <div className="project-meetings-modal-list">
              {filteredMeetings.length ? filteredMeetings.map((meeting, index) => (
                <Link
                  key={`modal-${meeting.index}`}
                  className="project-meetings-modal-row"
                  onClick={() => setIsMeetingsModalOpen(false)}
                  to={getMeetingDestinationForSpace(viewData.selectedSpace, meeting)}
                >
                  <div className={`project-flow-index tone-${(index % 4) + 1}`}>{meeting.index.replace("#", "")}</div>
                  <div className="project-flow-copy">
                    <strong>{meeting.title}</strong>
                    <p>{getMeetingDescription(meeting)}</p>
                  </div>
                  <div className="project-flow-meta">
                    <span>{meeting.date}</span>
                    <label className={`project-flow-badge ${getMeetingStateTone(meeting)}`}>{getMeetingStateLabel(meeting)}</label>
                  </div>
                </Link>
              )) : (
                <div className="project-meetings-modal-empty">
                  <strong>조건에 맞는 회의가 없습니다.</strong>
                  <p>검색어를 바꾸거나 정렬 기준을 조정해 보세요.</p>
                </div>
              )}
            </div>
          </section>
        </div>
      ) : null}

      {isProjectSettingsOpen ? (
        <div className="project-meetings-modal-backdrop settings-modal-backdrop" role="presentation">
          <section
            aria-labelledby="project-settings-modal-title"
            aria-describedby="project-settings-modal-description"
            aria-modal="true"
            className="project-meetings-modal project-settings-modal settings-surface"
            role="dialog"
          >
            <div className="project-meetings-modal-top">
              <div>
                <p className="project-meetings-modal-kicker">Settings / Project scope</p>
                <h3 id="project-settings-modal-title">프로젝트 정보</h3>
                <p id="project-settings-modal-description" className="project-settings-intro">
                  프로젝트 이름과 설명을 관리합니다. 변경은 이 프로젝트의 다음 화면부터 반영됩니다.
                </p>
              </div>
              <button
                aria-label="프로젝트 설정 닫기"
                className="project-meetings-modal-close"
                onClick={() => setIsProjectSettingsOpen(false)}
                type="button"
              >
                ×
              </button>
            </div>

            {!hasManagerOverride ? (
              <p className="project-settings-permission" role="status">프로젝트 정보 수정은 OWNER 또는 ADMIN만 할 수 있습니다.</p>
            ) : null}

            <form aria-busy={meetingMutationLoading} aria-label="프로젝트 정보 수정" className="workspace-project-modal-form" onSubmit={handleProjectSettingsSubmit}>
              <label className="workspace-project-field">
                <span>프로젝트명</span>
                <input disabled={!hasManagerOverride || meetingMutationLoading} onChange={(event) => setProjectTitle(event.target.value)} type="text" value={projectTitle} />
              </label>
              <label className="workspace-project-field">
                <span>설명</span>
                <textarea disabled={!hasManagerOverride || meetingMutationLoading} onChange={(event) => setProjectDescription(event.target.value)} value={projectDescription} />
              </label>
              <div className="workspace-project-modal-actions">
                <button className="secondary" onClick={() => setIsProjectSettingsOpen(false)} type="button">
                  취소
                </button>
                <button className="primary" disabled={!hasManagerOverride || meetingMutationLoading || !projectTitle.trim()} type="submit">
                  {meetingMutationLoading ? "저장 중..." : "변경 저장"}
                </button>
              </div>
            </form>

            {meetingMutationError ? <p aria-live="assertive" className="project-settings-error" role="alert">{meetingMutationError}</p> : null}

            <section aria-label="프로젝트 삭제" className="project-settings-danger">
              <span className="project-settings-section-kicker">Danger zone</span>
              <strong>프로젝트 삭제</strong>
              <p>OWNER만 수행할 수 있습니다. 프로젝트 이름을 입력하면 프로젝트 목록과 접근 범위에서 제거됩니다.</p>
              <input
                aria-label="삭제 확인 프로젝트명"
                disabled={!canDeleteProject || meetingMutationLoading}
                onChange={(event) => setDeleteConfirm(event.target.value)}
                placeholder={viewData.selectedSpace.name}
                type="text"
                value={deleteConfirm}
              />
              <button disabled={!canDeleteProject || meetingMutationLoading || deleteConfirm !== viewData.selectedSpace.name} onClick={handleDeleteProject} type="button">
                {meetingMutationLoading ? "삭제 중..." : "프로젝트 삭제"}
              </button>
            </section>
          </section>
        </div>
      ) : null}
    </>
  );
}
