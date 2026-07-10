import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import type { WorkspaceData } from "../types";

type ChangeCommit = {
  id: string;
  title: string;
  author: string;
  relativeTime: string;
  scope: string;
  note: string;
  details: string[];
};

type ReportDecision = {
  item: string;
  decision: string;
  note: string;
};

type EditableSection = "summary" | "subjects" | "content" | "decisions" | "results" | "actions";

type ReportPatch = {
  summary?: string;
  subjectLines?: string[];
  contentLines?: string[];
  decisions?: ReportDecision[];
  resultLines?: string[];
  actions?: string[];
};

type PendingChange = {
  section: EditableSection;
  patch: ReportPatch;
  commitTitle: string;
  commitNote: string;
  commitDetails: string[];
};

type ReportCandidateDraft = {
  id: string;
  summary: string;
  markdown: string;
  status: "candidate" | "confirmed";
  sources: string[];
};

type TaskCandidateDraft = {
  id: string;
  title: string;
  assignee: string;
  dueDate: string;
  status: "candidate" | "registered";
  sourceIds: string[];
};

type ChatMessage = {
  id: string;
  role: "user" | "ai";
  text: string;
  sources?: string[];
  relatedRound?: string;
  section?: EditableSection;
  pendingChange?: PendingChange | null;
  applied?: boolean;
  reverted?: boolean;
};

type ReportView = {
  breadcrumb: string;
  title: string;
  dateLine: string;
  summary: string;
  owner: string;
  location: string;
  attendees: string;
  startsAt: string;
  endsAt: string;
  subjectLines: string[];
  contentLines: string[];
  resultLines: string[];
  writer: string;
  writtenDate: string;
  decisions: ReportDecision[];
  actions: string[];
  chat: ChatMessage[];
  commits: ChangeCommit[];
};

function createChatMessage(message: Omit<ChatMessage, "id">): ChatMessage {
  return {
    id: `${message.role}-${Math.random().toString(36).slice(2, 10)}`,
    ...message
  };
}

function buildReportMarkdown(report: ReportView) {
  return [
    `# ${report.title}`,
    "",
    "## 요약",
    report.summary,
    "",
    "## 회의 주제",
    ...report.subjectLines.map((line) => `- ${line}`),
    "",
    "## 결정 사항",
    ...report.decisions.map((decision) => `- ${decision.item}: ${decision.decision} (${decision.note})`),
    "",
    "## Action Item",
    ...report.actions.map((action) => `- ${action}`)
  ].join("\n");
}

function buildTaskCandidatesFromReport(report: ReportView): TaskCandidateDraft[] {
  return report.actions.map((action, index) => {
    const [assignee, ...titleParts] = action.split("—");
    const title = titleParts.join("—").trim() || action.trim();

    return {
      id: `candidate-local-${index + 1}`,
      title,
      assignee: assignee.trim() || "미지정",
      dueDate: "",
      status: "candidate",
      sourceIds: [`${report.breadcrumb} Action Item ${index + 1}`]
    };
  });
}

function inferReportTrack(projectName: string, meetingTitle: string) {
  const source = `${projectName} ${meetingTitle}`.toLowerCase();

  if (/(security|권한|보안|접근|정책)/.test(source)) {
    return {
      summary:
        "역할 기반 접근 정책과 보안 예외 처리 범위를 정리하고, 회의 권한을 어떤 단위로 분리할지 합의했습니다.",
      owner: "이미주",
      decisions: [
        { item: "권한 단위", decision: "역할별 접근 정책 적용", note: "예외 권한은 별도 승인" },
        { item: "보안 점검", decision: "민감 기능은 추가 리뷰 후 배포", note: "체크리스트 반영" },
        { item: "다음 회의", decision: "운영 시나리오 기준 재검토", note: "07월 초 예정" }
      ],
      actions: [
        "보안 예외 케이스 문서화",
        "권한 요청 승인 플로우 정리",
        "감사 로그 확인 항목 추가"
      ]
    };
  }

  if (/(rag|검색|ai|llm|지식|data|데이터)/.test(source)) {
    return {
      summary:
        "검색 범위와 문맥 연결 구조를 재정리하고, 데이터 구조와 응답 품질 기준을 회차별 문서 흐름에 맞게 연결했습니다.",
      owner: "김진수",
      decisions: [
        { item: "검색 범위", decision: "회차 단위 문맥 우선 검색", note: "프로젝트 전체 검색은 후순위" },
        { item: "데이터 구조", decision: "문서 저장 구조 재정리", note: "검색 성능 우선" },
        { item: "응답 품질", decision: "출처 태그를 기본 포함", note: "관련 회차 노출" }
      ],
      actions: [
        "검색 품질 체크리스트 정리",
        "문맥 연결 규칙 점검",
        "답변 출처 표시 정책 반영"
      ]
    };
  }

  if (/(운영|admin|workflow|자동화|ops|프로세스)/.test(source)) {
    return {
      summary:
        "운영 자동화 흐름과 관리자 작업 단계를 다시 정리하고, 예외 처리 구간을 어떤 기준으로 분기할지 결정했습니다.",
      owner: "정하늘",
      decisions: [
        { item: "운영 흐름", decision: "관리자 단계별 분기 재정리", note: "자동화 조건 반영" },
        { item: "예외 처리", decision: "수동 검토가 필요한 구간 분리", note: "운영 리스크 기준" },
        { item: "다음 회의", decision: "배포 전 운영 점검", note: "체크리스트 예정" }
      ],
      actions: [
        "운영 시나리오별 분기표 작성",
        "자동화 실패 대응안 정리",
        "관리자 승인 조건 문서화"
      ]
    };
  }

  return {
    summary:
      "프로젝트 목표와 현재 회차에서 다룬 핵심 흐름을 정리하고, 다음 단계에서 확정해야 할 실행 항목을 합의했습니다.",
    owner: "프로젝트 리드",
    decisions: [
      { item: "프로젝트 범위", decision: "현재 회차 기준 핵심 안건 정리", note: "다음 회의로 이관" },
      { item: "실행 순서", decision: "문서화 후 세부 검토 진행", note: "우선순위 재확인" },
      { item: "다음 단계", decision: "후속 회차에서 확정", note: "팀원 의견 반영" }
    ],
    actions: ["후속 안건 정리", "핵심 결정사항 문서화", "다음 회의 자료 준비"]
  };
}

function buildGeneratedReport(projectName: string, meetingTitle: string, round: string): ReportView {
  const track = inferReportTrack(projectName, meetingTitle);
  const reportTitle = `${round}회차 회의 보고서 — ${meetingTitle}`;

  return {
    breadcrumb: `${round}회차 — ${meetingTitle}`,
    title: reportTitle,
    dateLine: `2026.06.${String(10 + Number(round || "3")).padStart(2, "0")} · 참여자 4명 · AI 자동 생성`,
    summary: track.summary,
    owner: track.owner,
    location: "3층 프로젝트 워룸",
    attendees: "4명",
    startsAt: "14:00",
    endsAt: "15:20",
    subjectLines: [
      `${projectName} 프로젝트 현재 회차 핵심 이슈 점검`,
      `${meetingTitle} 관련 구조와 실행 우선순위 정리`
    ],
    contentLines: [
      `1. ${meetingTitle} 배경 및 현재 상태 공유`,
      "2. 회의 단위 문맥 관리와 문서 흐름 정리",
      "3. 후속 회차에서 확정할 실행 항목 분류"
    ],
    resultLines: [
      "1. 이번 회차 기준 핵심 안건을 확정하고 문서 구조를 재정리함",
      "2. 권한, 문맥, 실행 흐름을 후속 작업 기준으로 정리함",
      "3. 다음 회차 검토 대상과 담당자 기준을 합의함"
    ],
    writer: track.owner,
    writtenDate: "2026년 6월 28일",
    decisions: track.decisions,
    actions: track.actions.map((item, index) => `${["김진수", "박서윤", "정도윤"][index % 3]} — ${item}`),
    chat: [
      createChatMessage({ role: "user", text: "결정사항을 표 형태로 정리해줘." }),
      createChatMessage({
        role: "ai",
        text: `${meetingTitle} 회차 기준으로 결정사항 섹션을 표 형태로 다시 정리했습니다.`,
        sources: [`${round}회차 STT`, "결정사항 메모"],
        relatedRound: `${round}회차`,
        section: "decisions",
        applied: true
      }),
      createChatMessage({ role: "user", text: "이번 회의 흐름이 더 잘 보이게 다듬어줘." }),
      createChatMessage({
        role: "ai",
        text: `${projectName} 프로젝트 맥락에 맞춰 회의 요약과 Action Item 문장을 정리했습니다.`,
        sources: [`${round}회차 요약`, `${projectName} 프로젝트 문맥`],
        relatedRound: `${round}회차`,
        section: "summary",
        applied: true
      })
    ],
    commits: [
      {
        id: "012e5da",
        title: `${meetingTitle} 결정사항 표 정리`,
        author: "MIJUUUUU",
        relativeTime: "2분 전",
        scope: "AI 편집",
        note: `${meetingTitle} 회차의 주요 결정을 표 구조로 다시 배치했습니다.`,
        details: ["결정사항 3건 테이블화", "비고 열 추가", "후속 회차 메모 반영"]
      },
      {
        id: "4bad80d",
        title: `${projectName} 보고서 초안 생성`,
        author: "SYSTEM",
        relativeTime: "8분 전",
        scope: "시스템",
        note: `회의 문맥과 프로젝트 요약을 기반으로 ${meetingTitle} 보고서 초안을 생성했습니다.`,
        details: ["회의 요약 생성", "Action Item 구조화", "프로젝트명 기준 메타데이터 반영"]
      },
      {
        id: "149db85",
        title: `${meetingTitle} 실행 항목 담당자 정리`,
        author: "MIJUUUUU",
        relativeTime: "14분 전",
        scope: "AI 편집",
        note: `실행 항목마다 담당자와 후속 문맥을 정리했습니다.`,
        details: ["담당자 라벨 추가", "우선순위 표현 정리", "다음 회차 연결 문장 반영"]
      }
    ]
  };
}

function buildReportView(
  data: WorkspaceData["reportAgent"],
  projectName: string | null,
  meetingTitle: string | null,
  round: string | null
) {
  const normalizedProject = projectName?.trim() || "FinPilot Renewal";
  const normalizedMeeting = meetingTitle?.trim() || "API 구조 논의";
  const normalizedRound = round?.trim() || "3";

  if (normalizedProject === "FinPilot Renewal" && normalizedMeeting === "API 구조 논의" && normalizedRound === "3") {
    return {
      breadcrumb: `3회차 — API 구조 논의`,
      title: data.reportTitle,
      dateLine: data.reportDate,
      summary:
        "ERD의 외래키 제약 누락 문제를 검토하고 3정규형 재검토를 결정했습니다. 권한 관리 구조를 회의 단위로 분리하고, RAG 검색 범위를 권한 체계에 맞춰 연동하기로 합의했습니다.",
      owner: "이미주",
      location: "3층 전략 회의실",
      attendees: "4명",
      startsAt: "14:00",
      endsAt: "15:30",
      subjectLines: ["ERD 구조 재검토 및 저장 흐름 정리", "권한 기반 RAG 검색 구조 및 문서 저장 정책 정리"],
      contentLines: [
        "1. 외래키 제약 누락과 3정규형 재검토 필요성 공유",
        "2. 권한 구조를 회의 단위로 분리하는 방안 검토",
        "3. RAG 검색 범위와 문서 저장 구조 연결 방식 논의"
      ],
      resultLines: [
        "1. ERD 수정과 정규화 재검토를 우선 반영하기로 결정",
        "2. 회의 단위 권한 구조를 적용하고 예외 권한은 별도 승인하기로 합의",
        "3. 검색 범위는 회차 문맥 우선, 프로젝트 전체는 후순위로 정리"
      ],
      writer: "이미주",
      writtenDate: "2026년 6월 28일",
      decisions: data.decisions,
      actions: [
        "김진수 — ERD 외래키 제약 수정",
        "박서연 — 정규화 재검토 문서화",
        "정도윤 — RAG 권한 연동 설계안 작성"
      ],
      chat: data.chat.map((message, index) =>
        createChatMessage({
          role: message.role,
          text: message.text,
          sources: message.role === "ai" ? ["03회차 STT", "결정사항 초안"] : undefined,
          relatedRound: "03회차",
          section: message.role === "ai" && index === 1 ? "decisions" : undefined,
          applied: message.role === "ai"
        })
      ),
      commits: [
        {
          id: "012e5da",
          title: "결정사항을 표 형태로 변환",
          author: "MIJUUUUU",
          relativeTime: "2분 전",
          scope: "AI 편집",
          note: "결정사항 섹션을 문장형에서 표 구조로 재정리했습니다.",
          details: ["결정 사항 블록을 3열 표로 재구성", "항목 / 결정 내용 / 비고 순서로 정렬", "보고서 본문 미리보기에 즉시 반영"]
        },
        {
          id: "4bad80d",
          title: "보고서 초안 자동 생성됨",
          author: "SYSTEM",
          relativeTime: "8분 전",
          scope: "시스템",
          note: "회의 STT, 결정사항, Action Item을 기반으로 v3 초안을 만들었습니다.",
          details: ["회의 요약 초안 생성", "결정사항 3건 구조화", "Action Item 담당자 라벨링"]
        },
        {
          id: "149db85",
          title: "Action Item에 담당자 추가",
          author: "MIJUUUUU",
          relativeTime: "14분 전",
          scope: "AI 편집",
          note: "담당자 식별이 바로 되도록 Action Item에 owner 정보를 붙였습니다.",
          details: ["김진수 / 박서연 / 정도윤 담당자 표기", "후속 작업 추적용 라벨 정리", "보고서 하단 실행 항목 섹션 업데이트"]
        }
      ]
    } satisfies ReportView;
  }

  return buildGeneratedReport(normalizedProject, normalizedMeeting, normalizedRound);
}

export function ReportAgentPage({ data }: { data: WorkspaceData["reportAgent"] }) {
  const [searchParams] = useSearchParams();
  const projectName = searchParams.get("project");
  const meetingTitle = searchParams.get("meeting");
  const round = searchParams.get("round");
  const reportView = useMemo(
    () => buildReportView(data, projectName, meetingTitle, round),
    [data, meetingTitle, projectName, round]
  );
  const [reportState, setReportState] = useState<ReportView>(reportView);
  const [chatInput, setChatInput] = useState("");
  const [saveLabel, setSaveLabel] = useState("● 자동 저장됨 · 방금 전");
  const [isCommitListOpen, setIsCommitListOpen] = useState(false);
  const [selectedCommitId, setSelectedCommitId] = useState<string | null>(null);
  const [reportCandidate, setReportCandidate] = useState<ReportCandidateDraft | null>(null);
  const [taskCandidates, setTaskCandidates] = useState<TaskCandidateDraft[]>([]);
  const changeCommits = reportState.commits;

  const selectedCommit = changeCommits.find((commit) => commit.id === selectedCommitId) ?? null;

  useEffect(() => {
    document.body.className = "app-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  useEffect(() => {
    setReportState(reportView);
    setChatInput("");
    setSaveLabel("● 자동 저장됨 · 방금 전");
    setIsCommitListOpen(false);
    setSelectedCommitId(null);
    setReportCandidate(null);
    setTaskCandidates([]);
  }, [projectName, meetingTitle, reportView, round]);

  function buildCommitId(seed: number) {
    return `${Math.abs(seed).toString(16).slice(0, 7)}`.padEnd(7, "0");
  }

  function buildPendingChange(section: EditableSection, prompt: string): { answer: string; pendingChange: PendingChange } {
    const relatedRound = reportState.breadcrumb.split(" — ")[0];

    switch (section) {
      case "summary":
        return {
          answer: "회의 요약을 더 명확하게 다듬는 변경안을 준비했습니다.",
          pendingChange: {
            section,
            patch: {
              summary: `${reportState.summary} 특히 이번 회차에서는 후속 실행 항목과 책임 범위를 더 분명하게 정리한 점이 핵심입니다.`
            },
            commitTitle: "회의 요약 문장 정리",
            commitNote: "회의 요약을 한 문단 더 압축하고 핵심 포인트를 강조했습니다.",
            commitDetails: [
              `${relatedRound} 핵심 포인트 보강`,
              "요약 문장 길이 정리",
              "실행 항목 연결 문장 추가"
            ]
          }
        };
      case "subjects":
        return {
          answer: "회의 주제를 보고서 양식에 더 맞게 재정리하는 변경안을 준비했습니다.",
          pendingChange: {
            section,
            patch: {
              subjectLines: [
                ...reportState.subjectLines,
                "후속 회차 검토 기준과 책임 범위 재정의"
              ]
            },
            commitTitle: "회의 주제 항목 보강",
            commitNote: "회의 주제에 후속 회차 검토 관점을 추가했습니다.",
            commitDetails: ["회의 주제 1개 추가", "검토 기준 문장 정리", "보고서 상단 메타와 정합성 유지"]
          }
        };
      case "content":
        return {
          answer: "회의 내용을 단계 중심으로 다시 보이도록 정리하는 변경안을 준비했습니다.",
          pendingChange: {
            section,
            patch: {
              contentLines: [...reportState.contentLines, "4. 실행 순서와 문서 저장 기준을 별도 체크포인트로 정리"]
            },
            commitTitle: "회의 내용 단계 보강",
            commitNote: "회의 내용에 실행 순서 확인 단계를 추가했습니다.",
            commitDetails: ["회의 내용 4단계 추가", "실행 순서 표현 보강", "문서 저장 체크포인트 반영"]
          }
        };
      case "decisions":
        return {
          answer: "결정 사항 표를 더 읽기 쉽게 보완하는 변경안을 준비했습니다.",
          pendingChange: {
            section,
            patch: {
              decisions: reportState.decisions.map((decision, index) =>
                index === 0 ? { ...decision, note: `${decision.note} / 우선 적용` } : decision
              )
            },
            commitTitle: "결정 사항 표 정리",
            commitNote: "결정 사항 표의 핵심 우선순위를 비고에 반영했습니다.",
            commitDetails: ["첫 번째 결정 사항 비고 보강", "우선 적용 태그 반영", "표 가독성 개선"]
          }
        };
      case "results":
        return {
          answer: "회의 결과를 더 공식적인 보고서 어조로 정리하는 변경안을 준비했습니다.",
          pendingChange: {
            section,
            patch: {
              resultLines: [...reportState.resultLines, "4. 후속 회차에서 확정할 항목을 보고서 기준으로 재점검하기로 함"]
            },
            commitTitle: "회의 결과 정리",
            commitNote: "회의 결과에 후속 검토 관점을 추가했습니다.",
            commitDetails: ["회의 결과 1개 추가", "보고서 어조로 표현 통일", "후속 회차 연결 문장 반영"]
          }
        };
      case "actions":
      default:
        return {
          answer: "Action Item에 후속 담당 작업을 추가하는 변경안을 준비했습니다.",
          pendingChange: {
            section: "actions",
            patch: {
              actions: [...reportState.actions, "이미주 — 최종 보고서 구조 검토 및 승인"]
            },
            commitTitle: "Action Item 후속 작업 추가",
            commitNote: "Action Item에 최종 보고서 검토 작업을 추가했습니다.",
            commitDetails: ["후속 담당 항목 추가", "최종 승인 흐름 반영", "실행 항목 추적성 강화"]
          }
        };
    }
  }

  function buildPromptDrivenChange(prompt: string): { answer: string; pendingChange?: PendingChange; section?: EditableSection } {
    const normalized = prompt.toLowerCase();

    if (normalized.includes("표") || normalized.includes("결정")) {
      const next = buildPendingChange("decisions", prompt);
      return { ...next, section: "decisions" };
    }

    if (normalized.includes("요약")) {
      const next = buildPendingChange("summary", prompt);
      return { ...next, section: "summary" };
    }

    if (normalized.includes("action") || normalized.includes("액션")) {
      const next = buildPendingChange("actions", prompt);
      return { ...next, section: "actions" };
    }

    if (normalized.includes("결과")) {
      const next = buildPendingChange("results", prompt);
      return { ...next, section: "results" };
    }

    if (normalized.includes("주제")) {
      const next = buildPendingChange("subjects", prompt);
      return { ...next, section: "subjects" };
    }

    if (normalized.includes("내용") || normalized.includes("흐름")) {
      const next = buildPendingChange("content", prompt);
      return { ...next, section: "content" };
    }

    return {
      answer: `${reportState.breadcrumb} 기준으로 이해했습니다. '요약', '주제', '내용', '결정', '결과', 'Action Item' 중 하나를 말하면 바로 변경안을 준비할 수 있습니다.`
    };
  }

  function applyPendingChange(messageId: string) {
    const target = reportState.chat.find((message) => message.id === messageId);

    if (!target?.pendingChange) {
      return;
    }

    const commitId = buildCommitId(Date.now());
    const pendingChange = target.pendingChange;
    const patch = pendingChange.patch;

    setReportState((current) => ({
      ...current,
      ...patch,
      chat: current.chat.map((message) =>
        message.id === messageId
          ? {
              ...message,
              pendingChange: null,
              applied: true,
              reverted: false
            }
          : message
      ),
      commits: [
        {
          id: commitId,
          title: pendingChange.commitTitle,
          author: "MIJUUUUU",
          relativeTime: "방금 전",
          scope: "AI 편집",
          note: pendingChange.commitNote,
          details: pendingChange.commitDetails
        },
        ...current.commits
      ]
    }));
    setSaveLabel("● 자동 저장됨 · 방금 전");
  }

  function revertPendingChange(messageId: string) {
    setReportState((current) => ({
      ...current,
      chat: current.chat.map((message) =>
        message.id === messageId
          ? {
              ...message,
              pendingChange: null,
              reverted: true,
              applied: false
            }
          : message
      )
    }));
  }

  function requestSectionEdit(section: EditableSection) {
    const next = buildPendingChange(section, `${section} 직접 수정`);

    setReportState((current) => ({
      ...current,
      chat: [
        ...current.chat,
        createChatMessage({
          role: "user",
          text: `${section === "summary" ? "회의 요약" : section === "subjects" ? "회의 주제" : section === "content" ? "회의 내용" : section === "decisions" ? "결정 사항" : section === "results" ? "회의 결과" : "Action Item"} 섹션을 다듬어줘.`
        }),
        createChatMessage({
          role: "ai",
          text: next.answer,
          sources: [current.breadcrumb, "보고서 본문"],
          relatedRound: current.breadcrumb.split(" — ")[0],
          section,
          pendingChange: next.pendingChange
        })
      ]
    }));
  }

  function handleProjectDocumentSave() {
    const key = "meetingmind.savedReports";
    const previous = localStorage.getItem(key);
    const parsed = previous ? (JSON.parse(previous) as ReportView[]) : [];
    const next = [
      {
        ...reportState,
        commits: [],
        chat: []
      },
      ...parsed.filter((item) => item.title !== reportState.title)
    ];
    localStorage.setItem(key, JSON.stringify(next));
    setSaveLabel("● 프로젝트 문서로 저장됨 · 방금 전");
  }

  function handleGenerateReportCandidate() {
    setReportCandidate({
      id: `report-candidate-${Date.now()}`,
      summary: reportState.summary,
      markdown: buildReportMarkdown(reportState),
      status: "candidate",
      sources: [reportState.breadcrumb, "현재 회의 보고서 본문", "Action Item"]
    });
    setSaveLabel("● 회의록 candidate 생성됨 · 확정 대기");
  }

  function handleConfirmReportCandidate() {
    if (!reportCandidate) {
      return;
    }

    setReportCandidate({ ...reportCandidate, status: "confirmed" });
    handleProjectDocumentSave();
    setSaveLabel("● candidate 확정됨 · backend confirm 연결 대기");
  }

  function handleExtractTaskCandidates() {
    setTaskCandidates(buildTaskCandidatesFromReport(reportState));
    setSaveLabel("● 태스크 candidate 추출됨 · 검토 대기");
  }

  function handleUpdateTaskCandidate(candidateId: string, updates: Partial<Pick<TaskCandidateDraft, "assignee" | "dueDate" | "title">>) {
    setTaskCandidates((current) =>
      current.map((candidate) => (candidate.id === candidateId ? { ...candidate, ...updates } : candidate))
    );
  }

  function handleRegisterTaskCandidate(candidateId: string) {
    setTaskCandidates((current) =>
      current.map((candidate) => (candidate.id === candidateId ? { ...candidate, status: "registered" } : candidate))
    );
    setSaveLabel("● 태스크 candidate 승인됨 · 칸반 API 연결 대기");
  }

  function handleExportPdf() {
    const printWindow = window.open("", "_blank", "width=1100,height=900");
    if (!printWindow) {
      return;
    }

    const subjectHtml = reportState.subjectLines.map((line) => `<div class="doc-line">- ${line}</div>`).join("");
    const contentHtml = reportState.contentLines.map((line) => `<div class="doc-line">${line}</div>`).join("");
    const resultHtml = reportState.resultLines.map((line) => `<div class="doc-line">${line}</div>`).join("");

    printWindow.document.write(`
      <html lang="ko">
        <head>
          <title>${reportState.title}</title>
          <style>
            body { margin: 0; background: #f4f2fb; font-family: "Malgun Gothic", "Apple SD Gothic Neo", sans-serif; color: #20233a; }
            .page { width: 1120px; margin: 24px auto; background: white; padding: 32px; box-sizing: border-box; }
            .title { font-size: 30px; font-weight: 800; margin-bottom: 20px; }
            table { width: 100%; border-collapse: collapse; table-layout: fixed; }
            th, td { border: 1px solid #7d7f8f; padding: 12px 14px; vertical-align: top; font-size: 16px; line-height: 1.7; }
            th { width: 130px; background: #f7f7fb; font-weight: 700; text-align: center; }
            .narrow { width: 90px; }
            .doc-block { min-height: 200px; white-space: pre-line; }
            .doc-line { margin-bottom: 6px; }
            .doc-block.tall { min-height: 320px; }
            .doc-block.medium { min-height: 180px; }
            @media print {
              body { background: white; }
              .page { margin: 0; width: auto; padding: 0; }
            }
          </style>
        </head>
        <body>
          <div class="page">
            <div class="title">${reportState.title}</div>
            <table>
              <tr>
                <th>회 의 명</th>
                <td colspan="5">${reportState.title}</td>
              </tr>
              <tr>
                <th>회의일자</th>
                <td>${reportState.writtenDate}</td>
                <th class="narrow">시 간</th>
                <td colspan="3">${reportState.startsAt} ~ ${reportState.endsAt}</td>
              </tr>
              <tr>
                <th>회의장소</th>
                <td colspan="5">${reportState.location}</td>
              </tr>
              <tr>
                <th>참석인원</th>
                <td>${reportState.attendees}</td>
                <th class="narrow">주 관 자</th>
                <td colspan="3">${reportState.owner}</td>
              </tr>
              <tr>
                <th>회의주제</th>
                <td colspan="5"><div class="doc-block medium">${subjectHtml}</div></td>
              </tr>
              <tr>
                <th>회의내용</th>
                <td colspan="5"><div class="doc-block tall">${contentHtml}</div></td>
              </tr>
              <tr>
                <th>회의결과</th>
                <td colspan="5"><div class="doc-block">${resultHtml}</div></td>
              </tr>
              <tr>
                <th>작성일자</th>
                <td>${reportState.writtenDate}</td>
                <th class="narrow">작성자</th>
                <td colspan="3">${reportState.writer}</td>
              </tr>
            </table>
          </div>
        </body>
      </html>
    `);
    printWindow.document.close();
    printWindow.focus();
    printWindow.print();
  }

  function applyAgentPrompt(prompt: string) {
    const normalized = prompt.trim();
    if (!normalized) {
      return;
    }

    const result = buildPromptDrivenChange(normalized);

    setReportState((current) => ({
      ...current,
      chat: [
        ...current.chat,
        createChatMessage({ role: "user", text: normalized }),
        createChatMessage({
          role: "ai",
          text: result.answer,
          sources: result.pendingChange ? [current.breadcrumb, "보고서 본문"] : ["보고서 편집 가이드"],
          relatedRound: current.breadcrumb.split(" — ")[0],
          section: result.section,
          pendingChange: result.pendingChange ?? null
        })
      ]
    }));
    setChatInput("");
    setSaveLabel("● 자동 저장됨 · 방금 전");
  }

  return (
    <div className="report-agent-page">
      <div className="report-agent-frame">
        <header className="report-agent-header">
          <div className="report-agent-header-left">
            <Link className="report-agent-logo" to="/">
              <span className="report-agent-logo-main">meeting</span>
              <span className="report-agent-logo-accent">mind</span>
            </Link>
            <p className="report-agent-breadcrumb">
              report-agent / <span>{reportView.breadcrumb}</span>
            </p>
          </div>

          <div className="report-agent-header-actions">
            <span className="report-agent-save-pill">{saveLabel}</span>
            <button onClick={handleExportPdf} type="button">
              PDF로 내보내기
            </button>
            <button className="primary" onClick={handleProjectDocumentSave} type="button">
              프로젝트 문서로 저장
            </button>
          </div>
        </header>

        <main className="report-agent-layout">
          <section className="report-agent-main">
            <div className="report-agent-meta-row">
              <button className="report-agent-commit-trigger" onClick={() => setIsCommitListOpen(true)} type="button">
                <span className="report-agent-commit-trigger-title">Commits</span>
                <span className="report-agent-commit-trigger-meta">
                  {changeCommits[0]?.id} · {changeCommits.length} commits
                </span>
              </button>
              <span className="report-agent-owner">담당: {reportView.owner}</span>
            </div>

            <article className="report-agent-sheet">
              <header className="report-agent-sheet-head">
                <div className="report-agent-doc-table">
                  <div className="report-agent-doc-row">
                    <div className="report-agent-doc-label">회 의 명</div>
                    <div className="report-agent-doc-value report-agent-doc-value-wide">{reportState.title}</div>
                  </div>
                  <div className="report-agent-doc-row report-agent-doc-row-3">
                    <div className="report-agent-doc-label">회의일자</div>
                    <div className="report-agent-doc-value">{reportState.writtenDate}</div>
                    <div className="report-agent-doc-label small">시 간</div>
                    <div className="report-agent-doc-value">
                      {reportState.startsAt} ~ {reportState.endsAt}
                    </div>
                  </div>
                  <div className="report-agent-doc-row">
                    <div className="report-agent-doc-label">회의장소</div>
                    <div className="report-agent-doc-value report-agent-doc-value-wide">{reportState.location}</div>
                  </div>
                  <div className="report-agent-doc-row report-agent-doc-row-3">
                    <div className="report-agent-doc-label">참석인원</div>
                    <div className="report-agent-doc-value">{reportState.attendees}</div>
                    <div className="report-agent-doc-label small">주 관 자</div>
                    <div className="report-agent-doc-value">{reportState.owner}</div>
                  </div>
                </div>
              </header>

              <div className="report-agent-sheet-body">
                <section className="report-agent-section">
                  <div className="report-agent-section-head">
                    <h3>회의 주제</h3>
                    <button className="report-agent-inline-edit" onClick={() => requestSectionEdit("subjects")} type="button">
                      직접 수정
                    </button>
                  </div>
                  <div className="report-agent-doc-block">
                    {reportState.subjectLines.map((line) => (
                      <p key={line}>- {line}</p>
                    ))}
                  </div>
                </section>

                <section className="report-agent-section">
                  <div className="report-agent-section-head">
                    <h3>회의 요약</h3>
                    <button className="report-agent-inline-edit" onClick={() => requestSectionEdit("summary")} type="button">
                      직접 수정
                    </button>
                  </div>
                  <p>{reportState.summary}</p>
                </section>

                <section className="report-agent-section">
                  <div className="report-agent-section-head">
                    <h3>회의 내용</h3>
                    <button className="report-agent-inline-edit" onClick={() => requestSectionEdit("content")} type="button">
                      직접 수정
                    </button>
                  </div>
                  <div className="report-agent-doc-block">
                    {reportState.contentLines.map((line) => (
                      <p key={line}>{line}</p>
                    ))}
                  </div>
                </section>

                <section className="report-agent-section">
                  <div className="report-agent-section-head">
                    <h3>결정 사항</h3>
                    <button className="report-agent-inline-edit" onClick={() => requestSectionEdit("decisions")} type="button">
                      직접 수정
                    </button>
                  </div>
                  <div className="report-agent-table-wrap">
                    <table className="report-agent-table">
                      <thead>
                        <tr>
                          <th>항목</th>
                          <th>결정 내용</th>
                          <th>비고</th>
                        </tr>
                      </thead>
                      <tbody>
                        {reportState.decisions.map((decision) => (
                          <tr key={decision.item}>
                            <td>{decision.item}</td>
                            <td>{decision.decision}</td>
                            <td>{decision.note}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </section>

                <section className="report-agent-section">
                  <div className="report-agent-section-head">
                    <h3>회의 결과</h3>
                    <button className="report-agent-inline-edit" onClick={() => requestSectionEdit("results")} type="button">
                      직접 수정
                    </button>
                  </div>
                  <div className="report-agent-doc-block">
                    {reportState.resultLines.map((line) => (
                      <p key={line}>{line}</p>
                    ))}
                  </div>
                </section>

                <section className="report-agent-section">
                  <div className="report-agent-section-head">
                    <h3>Action Item</h3>
                    <button className="report-agent-inline-edit" onClick={() => requestSectionEdit("actions")} type="button">
                      직접 수정
                    </button>
                  </div>
                  <ul className="report-agent-action-list">
                    {reportState.actions.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </section>

                <section className="report-agent-sheet-foot">
                  <div className="report-agent-doc-row report-agent-doc-row-3">
                    <div className="report-agent-doc-label">작성일자</div>
                    <div className="report-agent-doc-value">{reportState.writtenDate}</div>
                    <div className="report-agent-doc-label small">작성자</div>
                    <div className="report-agent-doc-value">{reportState.writer}</div>
                  </div>
                </section>
              </div>
            </article>
          </section>

          <aside className="report-agent-side">
            <section className="report-agent-chat-card">
              <div className="report-agent-chat-head">
                <div className="report-agent-chat-icon">✦</div>
                <div>
                  <strong>보고서 편집 Agent</strong>
                  <p>현재 보고서를 대화로 수정합니다</p>
                </div>
              </div>

              <div className="report-agent-chat-thread">
                {reportState.chat.map((message) => (
                  <div key={message.id} className={`report-agent-bubble ${message.role}`}>
                    <p>{message.text}</p>
                    {message.sources?.length ? (
                      <div className="report-agent-bubble-tags">
                        {message.relatedRound ? <span>{message.relatedRound}</span> : null}
                        {message.sources.map((source) => (
                          <span key={source}>{source}</span>
                        ))}
                      </div>
                    ) : null}
                    {message.role === "ai" && message.pendingChange ? (
                      <div className="report-agent-bubble-actions">
                        <button onClick={() => applyPendingChange(message.id)} type="button">
                          이 변경 적용
                        </button>
                        <button className="ghost" onClick={() => revertPendingChange(message.id)} type="button">
                          되돌리기
                        </button>
                      </div>
                    ) : null}
                    {message.role === "ai" && message.applied ? <div className="report-agent-bubble-applied">변경 적용됨</div> : null}
                    {message.role === "ai" && message.reverted ? <div className="report-agent-bubble-applied">변경 취소됨</div> : null}
                  </div>
                ))}
              </div>

              <form
                className="report-agent-chat-input"
                onSubmit={(event) => {
                  event.preventDefault();
                  applyAgentPrompt(chatInput);
                }}
              >
                <input
                  aria-label="보고서 Agent 입력"
                  onChange={(event) => setChatInput(event.target.value)}
                  placeholder="메시지를 입력하세요..."
                  type="text"
                  value={chatInput}
                />
                <button type="submit">→</button>
              </form>
            </section>

            <section className="report-agent-candidate-card">
              <div className="report-agent-candidate-head">
                <div>
                  <strong>Candidate Review</strong>
                  <p>저장성 결과는 확정 전 후보로만 다룹니다</p>
                </div>
                <span>frontend local</span>
              </div>

              <div className="report-agent-candidate-actions">
                <button onClick={handleGenerateReportCandidate} type="button">회의록 candidate 생성</button>
                <button onClick={handleExtractTaskCandidates} type="button">태스크 후보 추출</button>
              </div>

              {reportCandidate ? (
                <div className="report-agent-report-candidate">
                  <div className="report-agent-report-candidate-top">
                    <strong>회의록 후보</strong>
                    <span>{reportCandidate.status === "candidate" ? "candidate" : "confirmed local"}</span>
                  </div>
                  <p>{reportCandidate.summary}</p>
                  <div className="report-agent-bubble-tags">
                    {reportCandidate.sources.map((source) => (
                      <span key={source}>{source}</span>
                    ))}
                  </div>
                  <button
                    disabled={reportCandidate.status === "confirmed"}
                    onClick={handleConfirmReportCandidate}
                    type="button"
                  >
                    프로젝트 문서로 확정
                  </button>
                </div>
              ) : null}

              {taskCandidates.length ? (
                <div className="report-agent-task-candidates">
                  {taskCandidates.map((candidate) => (
                    <div key={candidate.id} className="report-agent-task-candidate">
                      <div className="report-agent-task-candidate-top">
                        <strong>{candidate.status === "candidate" ? "검토 대기" : "등록 승인"}</strong>
                        <span>{candidate.sourceIds[0]}</span>
                      </div>
                      <label>
                        <span>제목</span>
                        <input
                          onChange={(event) => handleUpdateTaskCandidate(candidate.id, { title: event.target.value })}
                          value={candidate.title}
                        />
                      </label>
                      <div className="report-agent-task-candidate-grid">
                        <label>
                          <span>담당자</span>
                          <input
                            onChange={(event) => handleUpdateTaskCandidate(candidate.id, { assignee: event.target.value })}
                            value={candidate.assignee}
                          />
                        </label>
                        <label>
                          <span>마감일</span>
                          <input
                            onChange={(event) => handleUpdateTaskCandidate(candidate.id, { dueDate: event.target.value })}
                            type="date"
                            value={candidate.dueDate}
                          />
                        </label>
                      </div>
                      <button
                        disabled={candidate.status === "registered"}
                        onClick={() => handleRegisterTaskCandidate(candidate.id)}
                        type="button"
                      >
                        칸반 등록 승인
                      </button>
                    </div>
                  ))}
                </div>
              ) : null}

              <p className="report-agent-candidate-gap">
                실제 저장, 확정, 칸반 등록은 Backend report/task-candidate API 연결 후 서버 권한 검증과 감사 로그로 처리합니다.
              </p>
            </section>
          </aside>
        </main>
      </div>

      {isCommitListOpen ? (
        <div aria-hidden="true" className="report-agent-commit-modal-backdrop" onClick={() => setIsCommitListOpen(false)}>
          <section
            aria-labelledby="report-agent-commit-list-modal-title"
            aria-modal="true"
            className="report-agent-commit-modal report-agent-commit-list-modal"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <div className="report-agent-commit-modal-top">
              <div>
                <p className="report-agent-commit-modal-kicker">Commits</p>
                <h3 id="report-agent-commit-list-modal-title">최근 변경 목록</h3>
              </div>
              <button
                aria-label="커밋 목록 모달 닫기"
                className="report-agent-commit-modal-close"
                onClick={() => setIsCommitListOpen(false)}
                type="button"
              >
                ×
              </button>
            </div>

            <div className="report-agent-commit-list">
              {changeCommits.map((commit) => (
                <button
                  key={commit.id}
                  className="report-agent-change-item"
                  onClick={() => {
                    setIsCommitListOpen(false);
                    setSelectedCommitId(commit.id);
                  }}
                  type="button"
                >
                  <span className="report-agent-change-dot" />
                  <div className="report-agent-change-copy">
                    <strong>{commit.title}</strong>
                    <p>
                      {commit.author} committed {commit.relativeTime}
                    </p>
                  </div>
                  <div className="report-agent-change-meta">
                    <span>{commit.id}</span>
                    <em>{commit.scope}</em>
                  </div>
                </button>
              ))}
            </div>
          </section>
        </div>
      ) : null}

      {selectedCommit ? (
        <div aria-hidden="true" className="report-agent-commit-modal-backdrop" onClick={() => setSelectedCommitId(null)}>
          <section
            aria-labelledby="report-agent-commit-modal-title"
            aria-modal="true"
            className="report-agent-commit-modal"
            onClick={(event) => event.stopPropagation()}
            role="dialog"
          >
            <div className="report-agent-commit-modal-top">
              <div>
                <p className="report-agent-commit-modal-kicker">Commit Detail</p>
                <h3 id="report-agent-commit-modal-title">{selectedCommit.title}</h3>
              </div>
              <button
                aria-label="최근 변경 사항 모달 닫기"
                className="report-agent-commit-modal-close"
                onClick={() => setSelectedCommitId(null)}
                type="button"
              >
                ×
              </button>
            </div>

            <div className="report-agent-commit-modal-summary">
              <span>{selectedCommit.author}</span>
              <span>{selectedCommit.id}</span>
              <span>{selectedCommit.relativeTime}</span>
              <span>{selectedCommit.scope}</span>
            </div>

            <div className="report-agent-commit-modal-body">
              <div className="report-agent-commit-message-card">
                <strong>변경 설명</strong>
                <p>{selectedCommit.note}</p>
              </div>

              <div className="report-agent-commit-diff-card">
                <strong>반영 내용</strong>
                <ul>
                  {selectedCommit.details.map((detail) => (
                    <li key={detail}>+ {detail}</li>
                  ))}
                </ul>
              </div>
            </div>
          </section>
        </div>
      ) : null}
    </div>
  );
}
