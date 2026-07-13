import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { TranscriptRow, WorkspaceData } from "../types";

const AI_API_BASE_URL = import.meta.env.VITE_AI_API_BASE_URL ?? "http://localhost:8000";

type ProjectChatMessage = {
  role: "user" | "ai";
  text: string;
  tags?: string[];
};

type MeetingAiAskResponse = {
  answer: string;
  model: string;
};

type ProjectMeeting = WorkspaceData["projectOverview"]["meetings"][number];
type MeetingParticipantState = {
  id: string;
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
  assignee: string;
  dueDate: string;
  meetingKey: string | null;
  sourceCandidateId: string | null;
};
type TeamMemberState = {
  name: string;
  email: string;
  role: string;
  since: string;
  access: string;
  rank: string;
  status: "active" | "away";
};
type MeetingSort = "recent" | "oldest" | "state";
type TaskStatus = ProjectTaskState["status"];

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

function getMeetingDescription(meeting: ProjectMeeting) {
  if (meeting.state === "완료") {
    return "데이터셋 구조 확인 및 결정사항 정리";
  }

  if (meeting.state === "예정") {
    return "STT 보관 정책 및 관리자 권한 최종 확정";
  }

  return "권한 기반 RAG 검색 구조 설계 결정";
}

function getMeetingStateLabel(meeting: ProjectMeeting) {
  return meeting.state === "완료" ? "완료" : meeting.state === "예정" ? "예정" : "보고서 생성됨";
}

function getMeetingStateTone(meeting: ProjectMeeting) {
  return meeting.state === "완료" ? "green" : meeting.state === "예정" ? "orange" : "violet";
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
    meeting: meeting.title,
    round: meeting.index.replace("#", "")
  });
  if (meeting.id) {
    params.set("meetingId", meeting.id);
  }

  return `${path}?${params.toString()}`;
}

export function ProjectOverviewPage({
  data,
  onDeleteProject,
  meetingParticipants,
  onAddMeetingParticipant,
  projectMeetings,
  projectMembers,
  projectTasks,
  spaces,
  onCreateMeeting,
  onCreateProject,
  onCreateProjectTask,
  onDeleteMeeting,
  onDeleteProjectTask,
  onMoveProjectTask,
  onUpdateMeetingParticipant,
  onUpdateMeetingStatus,
  onUpdateProject
}: {
  data: WorkspaceData["projectOverview"];
  onDeleteProject?: (spaceId: string) => void;
  meetingParticipants: Record<string, MeetingParticipantState[]>;
  onAddMeetingParticipant?: (
    projectName: string,
    meetingIndex: string,
    participant: Pick<MeetingParticipantState, "email" | "name" | "role" | "participantType">
  ) => void;
  projectMeetings: Record<string, ProjectMeeting[]>;
  projectMembers: Record<string, TeamMemberState[]>;
  projectTasks: Record<string, ProjectTaskState[]>;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  onCreateMeeting?: (projectName: string) => void;
  onCreateProject?: (payload: { name: string; description: string }) => void;
  onCreateProjectTask?: (projectName: string, task: Omit<ProjectTaskState, "id" | "sourceCandidateId">) => void;
  onDeleteMeeting?: (projectName: string, meetingIndex: string) => void;
  onDeleteProjectTask?: (projectName: string, taskId: string) => void;
  onMoveProjectTask?: (projectName: string, taskId: string, status: TaskStatus) => void;
  onUpdateMeetingParticipant?: (
    projectName: string,
    meetingIndex: string,
    participantId: string,
    updates: Pick<MeetingParticipantState, "accessStatus" | "role">
  ) => void;
  onUpdateMeetingStatus?: (projectName: string, meetingIndex: string, state: ProjectMeeting["state"]) => void;
  onUpdateProject?: (spaceId: string, payload: { name: string; description: string }) => void;
}) {
  useEffect(() => {
    document.body.className = "app-theme project-overview-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  const [searchParams] = useSearchParams();
  const spaceId = searchParams.get("spaceId");
  const projectName = searchParams.get("project");
  const viewData = buildProjectView(data, projectMeetings, spaces, spaceId, projectName);
  const chatScrollRef = useRef<HTMLDivElement | null>(null);
  const projectPromptFallback = viewData
    ? `${viewData.selectedSpace.name} 프로젝트 기준으로 일정, 결정사항, 다음 회의 흐름을 정리해드릴게요.`
    : "프로젝트 기준으로 일정, 결정사항, 다음 회의 흐름을 정리해드릴게요.";
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
  const [taskTitle, setTaskTitle] = useState("");
  const [taskDescription, setTaskDescription] = useState("");
  const [taskAssignee, setTaskAssignee] = useState("");
  const [taskDueDate, setTaskDueDate] = useState("2026-07-20");
  const [taskMeetingIndex, setTaskMeetingIndex] = useState("");

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
    setTaskAssignee("");
  }, [viewData?.selectedSpace.name]);

  const payloadSource = useMemo(() => {
    if (!viewData) {
      return {
        transcript: [] as TranscriptRow[],
        decisions: [],
        actions: []
      };
    }

    const nextMeeting = viewData.meetings.find((meeting) => meeting.state !== "완료") ?? viewData.meetings[0] ?? null;
    const transcript: TranscriptRow[] = viewData.meetings.map((meeting, index) => ({
      time: `06:${String(2 + index * 4).padStart(2, "0")}:00`,
      speaker: `${meeting.index.replace("#", "")}회차`,
      text:
        meeting.state === "완료"
          ? `${meeting.title} 회의에서 구조 점검과 주요 결정사항을 정리했습니다.`
          : meeting.state === "예정"
            ? `${meeting.title} 회의에서 보안 정책과 관리자 권한 최종안을 확정할 예정입니다.`
            : `${meeting.title} 회의에서 권한 기반 RAG 검색 구조와 보고서 생성 흐름을 논의했습니다.`
    }));

    return {
      transcript,
      decisions: [
        {
          title: viewData.knowledge.finalDecision,
          meta: `${viewData.selectedSpace.name} 프로젝트 결정`
        }
      ],
      actions: [
        {
          title: `${viewData.selectedSpace.name} 다음 회의 안건 정리`,
          meta: nextMeeting ? `${nextMeeting.date} 예정` : "아직 예정된 회의 없음"
        },
        {
          title: `${viewData.selectedSpace.name} 최근 결정사항 검토`,
          meta: viewData.selectedSpace.updatedAt
        }
      ]
    };
  }, [viewData]);

  useEffect(() => {
    if (!chatScrollRef.current) {
      return;
    }

    chatScrollRef.current.scrollTo({
      top: chatScrollRef.current.scrollHeight,
      behavior: "smooth"
    });
  }, [loading, messages]);

  const filteredMeetings = useMemo(() => {
    const normalizedQuery = meetingSearch.trim().toLowerCase();
    const sourceMeetings = viewData?.meetings ?? [];
    const nextMeetings = sourceMeetings.filter((meeting) => {
      if (!normalizedQuery) {
        return true;
      }

      return [meeting.index, meeting.title, meeting.date, meeting.state, getMeetingDescription(meeting)]
        .join(" ")
        .toLowerCase()
        .includes(normalizedQuery);
    });

    return [...nextMeetings].sort((left, right) => {
      if (meetingSort === "oldest") {
        return parseMeetingDateLabel(left.date) - parseMeetingDateLabel(right.date);
      }

      if (meetingSort === "state") {
        const stateOrder = getMeetingStateOrder(left.state) - getMeetingStateOrder(right.state);
        return stateOrder !== 0 ? stateOrder : parseMeetingDateLabel(right.date) - parseMeetingDateLabel(left.date);
      }

      return parseMeetingDateLabel(right.date) - parseMeetingDateLabel(left.date);
    });
  }, [meetingSearch, meetingSort, viewData]);

  if (!viewData) {
    return null;
  }

  const nextMeeting = viewData.meetings.find((meeting) => meeting.state !== "완료") ?? viewData.meetings[0] ?? null;
  const contextMeeting = viewData.meetings.find((meeting) => meeting.state === "보고서 생성됨") ?? viewData.meetings[0] ?? null;
  const contextMeetingTag = contextMeeting ? `관련 ${contextMeeting.index.replace("#", "")}회차` : "프로젝트 초기 상태";
  const canSubmit = input.trim().length > 0 && !loading;
  const selectedProjectName = viewData.selectedSpace.name;
  const selectedMeeting = viewData.meetings.find((meeting) => meeting.index === selectedMeetingIndex) ?? contextMeeting;
  const selectedMeetingKey = selectedMeeting ? buildMeetingKey(selectedProjectName, selectedMeeting.index) : "";
  const members = projectMembers[selectedProjectName] ?? [];
  const defaultParticipants = selectedMeetingKey ? buildDefaultMeetingParticipants(members, selectedMeetingKey) : [];
  const visibleParticipants = selectedMeetingKey
    ? meetingParticipants[selectedMeetingKey] ?? defaultParticipants
    : [];
  const selectedProjectTasks = projectTasks[selectedProjectName] ?? [];
  const availableMember = members.find((member) => member.email === selectedMemberEmail) ?? members[0] ?? null;
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
      const response = await fetch(`${AI_API_BASE_URL}/api/meeting-ai/ask`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          question: trimmed,
          transcript: payloadSource.transcript,
          decisions: payloadSource.decisions,
          actions: payloadSource.actions
        })
      });

      if (!response.ok) {
        const detail = await response.text();
        throw new Error(detail || `프로젝트 AI 요청 실패 (${response.status})`);
      }

      const result = (await response.json()) as MeetingAiAskResponse;
      setModelLabel(result.model);
      setMessages((previous) => [
        ...previous,
        {
          role: "ai",
          text: result.answer,
          tags: [contextMeetingTag, "출처 프로젝트 문맥"]
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
          text: `${projectPromptFallback} 현재는 AI 서버 연결을 확인해야 합니다.`,
          tags: [contextMeetingTag, "출처 프로젝트 요약"]
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

  function handleProjectSettingsSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!viewData || !projectTitle.trim()) {
      return;
    }

    onUpdateProject?.(viewData.selectedSpace.id, {
      name: projectTitle,
      description: projectDescription
    });
    setIsProjectSettingsOpen(false);
  }

  function handleDeleteProject() {
    if (!viewData) {
      return;
    }

    if (deleteConfirm !== viewData.selectedSpace.name) {
      return;
    }

    onDeleteProject?.(viewData.selectedSpace.id);
  }

  function handleAddParticipant(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedMeeting || !availableMember) {
      return;
    }

    onAddMeetingParticipant?.(selectedProjectName, selectedMeeting.index, {
      email: availableMember.email,
      name: availableMember.name,
      role: selectedMemberRole,
      participantType: "member"
    });
    setSelectedMemberEmail("");
    setSelectedMemberRole("VIEWER");
  }

  function handleParticipantRoleChange(participant: MeetingParticipantState, role: MeetingParticipantState["role"]) {
    if (!selectedMeeting) {
      return;
    }

    onAddMeetingParticipant?.(selectedProjectName, selectedMeeting.index, {
      email: participant.email,
      name: participant.name,
      role,
      participantType: participant.participantType
    });
  }

  function handleParticipantAccessChange(participant: MeetingParticipantState, accessStatus: MeetingParticipantState["accessStatus"]) {
    if (!selectedMeeting) {
      return;
    }

    onAddMeetingParticipant?.(selectedProjectName, selectedMeeting.index, {
      email: participant.email,
      name: participant.name,
      role: participant.role,
      participantType: participant.participantType
    });
    onUpdateMeetingParticipant?.(selectedProjectName, selectedMeeting.index, participant.id, {
      accessStatus,
      role: participant.role
    });
  }

  function handleCreateTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!taskTitle.trim()) {
      return;
    }

    const meetingKey = taskMeetingIndex ? buildMeetingKey(selectedProjectName, taskMeetingIndex) : null;
    onCreateProjectTask?.(selectedProjectName, {
      title: taskTitle.trim(),
      description: taskDescription.trim() || "상세 설명이 아직 작성되지 않았습니다.",
      status: "TODO",
      assignee: taskAssignee || "미지정",
      dueDate: taskDueDate,
      meetingKey
    });
    setTaskTitle("");
    setTaskDescription("");
    setTaskAssignee("");
  }

  return (
    <div className="workspace-catalog-shell project-overview-shell">
      <WorkspaceSidebar
        activeItem="none"
        mode="catalog"
        onCreateProject={onCreateProject}
        projectName={viewData.selectedSpace.name}
        spaceId={viewData.selectedSpace.id}
      />

      <main className="workspace-catalog-main project-overview-main">
        <section className="project-overview-header">
          <div className="project-overview-copy">
            <p className="project-overview-breadcrumb">
              project-overview / <span>{viewData.selectedSpace.name}</span>
            </p>
            <h1>{viewData.overviewTitle}</h1>
            <div className="project-overview-subline">
              <span className="project-overview-state">{viewData.knowledge.heroStatus}</span>
              <p>{viewData.knowledge.heroDescription}</p>
            </div>
          </div>

          <div className="project-overview-actions">
            <button className="secondary" onClick={() => setIsProjectSettingsOpen(true)} type="button">
              프로젝트 설정
            </button>
            <button className="primary" onClick={() => onCreateMeeting?.(viewData.selectedSpace.name)} type="button">
              + 새 회의 만들기
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
                <button onClick={() => onCreateMeeting?.(viewData.selectedSpace.name)} type="button">회의 만들기</button>
              </section>
            )}

            <section className="project-list-section">
              <div className="project-list-head">
                <strong>회차별 진행 흐름</strong>
                <button className="project-list-open" onClick={() => setIsMeetingsModalOpen(true)} type="button">
                  전체보기 ›
                </button>
              </div>
              <div className="project-flow-list">
                {viewData.meetings.length ? (
                  viewData.meetings.map((meeting, index) => (
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
                        <label className={`project-flow-badge ${getMeetingStateTone(meeting)}`}>{getMeetingStateLabel(meeting)}</label>
                      </div>
                    </Link>
                  ))
                ) : (
                  <div className="project-flow-empty">
                    <strong>회차가 아직 없습니다</strong>
                    <p>우측 상단의 새 회의 만들기 버튼으로 첫 회의를 생성해보세요.</p>
                  </div>
                )}
              </div>
            </section>

            <section className="project-list-section project-operations-section">
              <div className="project-list-head">
                <strong>회의 운영 / 접근 제어</strong>
                <span>local state · target API 대기</span>
              </div>

              {selectedMeeting ? (
                <>
                  <div className="project-operation-toolbar">
                    <label>
                      <span>대상 회의</span>
                      <select
                        onChange={(event) => setSelectedMeetingIndex(event.target.value)}
                        value={selectedMeeting.index}
                      >
                        {viewData.meetings.map((meeting) => (
                          <option key={`meeting-select-${meeting.index}`} value={meeting.index}>
                            {meeting.index} {meeting.title}
                          </option>
                        ))}
                      </select>
                    </label>

                    <label>
                      <span>회의 상태</span>
                      <select
                        onChange={(event) =>
                          onUpdateMeetingStatus?.(
                            viewData.selectedSpace.name,
                            selectedMeeting.index,
                            event.target.value as ProjectMeeting["state"]
                          )
                        }
                        value={selectedMeeting.state}
                      >
                        <option value="예정">예정</option>
                        <option value="완료">완료</option>
                        <option value="보고서 생성됨">보고서 생성됨</option>
                      </select>
                    </label>

                    <button
                      className="project-operation-danger"
                      onClick={() => onDeleteMeeting?.(viewData.selectedSpace.name, selectedMeeting.index)}
                      type="button"
                    >
                      회의 삭제
                    </button>
                  </div>

                  <div className="project-acl-note">
                    default-deny 기준 화면입니다. 명시 참여자만 회의 접근 대상으로 보이며, 실제 판정과 감사 기록은 백엔드 연결 후 처리합니다.
                  </div>

                  <form className="project-acl-add" onSubmit={handleAddParticipant}>
                    <label>
                      <span>멤버</span>
                      <select
                        onChange={(event) => setSelectedMemberEmail(event.target.value)}
                        value={selectedMemberEmail}
                      >
                        <option value="">멤버 선택</option>
                        {members.map((member) => (
                          <option key={`member-option-${member.email}`} value={member.email}>
                            {member.name} · {member.role}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label>
                      <span>회의 role</span>
                      <select
                        onChange={(event) => setSelectedMemberRole(event.target.value as MeetingParticipantState["role"])}
                        value={selectedMemberRole}
                      >
                        <option value="VIEWER">VIEWER</option>
                        <option value="EDITOR">EDITOR</option>
                        <option value="HOST">HOST</option>
                      </select>
                    </label>
                    <button disabled={!selectedMemberEmail} type="submit">권한 부여</button>
                  </form>

                  <div className="project-acl-list">
                    {visibleParticipants.map((participant) => (
                      <div key={participant.id} className="project-acl-row">
                        <div>
                          <strong>{participant.name}</strong>
                          <span>{participant.email}</span>
                        </div>
                        <select
                          aria-label={`${participant.name} 회의 role`}
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

            <section className="project-list-section project-kanban-section">
              <div className="project-list-head">
                <strong>프로젝트 칸반</strong>
                <span>카드 {selectedProjectTasks.length}개</span>
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
                  {viewData.meetings.map((meeting) => (
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
                  <div key={column.key} className="project-kanban-column">
                    <div className="project-kanban-column-head">
                      <strong>{column.label}</strong>
                      <span>{selectedProjectTasks.filter((task) => task.status === column.key).length}</span>
                    </div>
                    <div className="project-kanban-cards">
                      {selectedProjectTasks
                        .filter((task) => task.status === column.key)
                        .map((task) => (
                          <article key={task.id} className="project-kanban-card">
                            <strong>{task.title}</strong>
                            <p>{task.description}</p>
                            <div className="project-kanban-meta">
                              <span>{task.assignee}</span>
                              <span>{task.dueDate}</span>
                            </div>
                            <div className="project-kanban-card-actions">
                              <select
                                aria-label={`${task.title} 상태 변경`}
                                onChange={(event) =>
                                  onMoveProjectTask?.(viewData.selectedSpace.name, task.id, event.target.value as TaskStatus)
                                }
                                value={task.status}
                              >
                                <option value="TODO">TODO</option>
                                <option value="IN_PROGRESS">IN_PROGRESS</option>
                                <option value="DONE">DONE</option>
                              </select>
                              <button onClick={() => onDeleteProjectTask?.(viewData.selectedSpace.name, task.id)} type="button">
                                삭제
                              </button>
                            </div>
                          </article>
                        ))}
                    </div>
                  </div>
                ))}
              </div>
            </section>

          </div>

          <aside className="project-overview-side">
            <section className="project-side-block ask">
              <div className="project-ask-head">
                <div className="project-ask-icon">✦</div>
                <div className="project-ask-title">
                  <strong>프로젝트에게 물어보기</strong>
                  <span>{modelLabel ? `모델 ${modelLabel}` : `${viewData.selectedSpace.name} 문맥 기반 응답`}</span>
                </div>
              </div>

              <div className="project-ask-prompts">
                {viewData.knowledge.prompts.map((prompt) => (
                  <button key={prompt} onClick={() => void askProjectAi(prompt)} type="button">
                    {prompt}
                  </button>
                ))}
              </div>

              <div ref={chatScrollRef} className="project-chat-history">
                {messages.map((message, index) => (
                  <div key={`${message.role}-${index}`} className={`project-chat-bubble ${message.role}`}>
                    <p>{message.text}</p>
                    {message.tags?.length ? (
                      <div className="project-chat-tags">
                        {message.tags.map((tag) => (
                          <span key={`${message.role}-${index}-${tag}`}>{tag}</span>
                        ))}
                      </div>
                    ) : null}
                  </div>
                ))}
                {loading ? <div className="project-chat-bubble ai">답변을 정리하고 있습니다...</div> : null}
              </div>

              {error ? <p className="project-chat-error">{error}</p> : null}

              <form className="project-chat-form" onSubmit={handleSubmit}>
                <input
                  aria-label="프로젝트 질문 입력"
                  onChange={(event) => setInput(event.target.value)}
                  placeholder="무엇이든 물어보세요..."
                  type="text"
                  value={input}
                />
                <button disabled={!canSubmit} type="submit">
                  {loading ? "생성 중" : "전송"}
                </button>
              </form>
            </section>
          </aside>
        </section>
      </main>

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
              <span>전체 {viewData.meetings.length}건</span>
              <span>검색 결과 {filteredMeetings.length}건</span>
              <span>예정 {viewData.meetings.filter((meeting) => meeting.state === "예정").length}건</span>
            </div>

            <div className="project-meetings-modal-list">
              {filteredMeetings.map((meeting, index) => (
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
              ))}
            </div>
          </section>
        </div>
      ) : null}

      {isProjectSettingsOpen ? (
        <div className="project-meetings-modal-backdrop" role="presentation">
          <section
            aria-labelledby="project-settings-modal-title"
            aria-modal="true"
            className="project-meetings-modal project-settings-modal"
            role="dialog"
          >
            <div className="project-meetings-modal-top">
              <div>
                <p className="project-meetings-modal-kicker">Project Settings</p>
                <h3 id="project-settings-modal-title">프로젝트 정보</h3>
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

            <form className="workspace-project-modal-form" onSubmit={handleProjectSettingsSubmit}>
              <label className="workspace-project-field">
                <span>프로젝트명</span>
                <input onChange={(event) => setProjectTitle(event.target.value)} type="text" value={projectTitle} />
              </label>
              <label className="workspace-project-field">
                <span>설명</span>
                <textarea onChange={(event) => setProjectDescription(event.target.value)} value={projectDescription} />
              </label>
              <div className="workspace-project-modal-actions">
                <button className="secondary" onClick={() => setIsProjectSettingsOpen(false)} type="button">
                  취소
                </button>
                <button className="primary" disabled={!projectTitle.trim()} type="submit">
                  저장
                </button>
              </div>
            </form>

            <section className="project-settings-danger">
              <strong>프로젝트 삭제</strong>
              <p>오너 전용 작업입니다. 현재 프론트엔드에서는 local state에서 제외하고, 실제 삭제는 target API 연결 후 서버 권한 검증을 따릅니다.</p>
              <input
                aria-label="삭제 확인 프로젝트명"
                onChange={(event) => setDeleteConfirm(event.target.value)}
                placeholder={viewData.selectedSpace.name}
                type="text"
                value={deleteConfirm}
              />
              <button disabled={deleteConfirm !== viewData.selectedSpace.name} onClick={handleDeleteProject} type="button">
                프로젝트 삭제
              </button>
            </section>
          </section>
        </div>
      ) : null}
    </div>
  );
}
