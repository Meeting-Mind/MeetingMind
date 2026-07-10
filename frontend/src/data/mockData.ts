import type { WorkspaceData } from "../types";

export const mockData: WorkspaceData = {
  workspaceHome: {
    overview: {
      title: "내 워크스페이스",
      subtitle: "참여 중인 프로젝트를 확인하고, 이어서 봐야 할 회의 흐름으로 바로 이동합니다.",
      status: ["2개 프로젝트", "최근 업데이트 2건"]
    },
    todayMeeting: {
      title: "3회차 API 구조 논의",
      project: "FinPilot Renewal",
      time: "오늘 오후 2:00",
      attendees: "참여 예정 8명",
      note: "권한 모델과 보고서 저장 흐름을 정리할 예정입니다.",
      href: "/live-meeting"
    },
    spaces: [
      {
        id: "space-finpilot-renewal",
        name: "FinPilot Renewal",
        members: "멤버 8명",
        meetings: "진행 회의 3건",
        updatedAt: "어제 21:08 업데이트",
        description: "리뉴얼 일정과 사용자 흐름 개선안을 논의하는 협업 공간입니다.",
        href: "/project-overview"
      },
      {
        id: "space-campus-admin-assistant",
        name: "Campus Admin Assistant",
        members: "멤버 5명",
        meetings: "진행 회의 2건",
        updatedAt: "06.19 업데이트",
        description: "운영 자동화 기능과 관리자 권한 구조를 검토하는 프로젝트입니다.",
        href: "/project-overview"
      }
    ],
    recent: [
      { title: "FinPilot Renewal · 액션 아이템 2건 갱신", meta: "오늘 11:10" },
      { title: "Campus Admin Assistant · 킥오프 회의록", meta: "06.19" }
    ]
  },
  liveMeeting: {
    overview: {
      title: "3회차 API 설계 회의",
      subtitle: "Space: FinPilot Renewal · 2026.06.21 · 14:00 시작 예정 · 초대된 참여자 8명",
      status: ["Ready", "입장 프리뷰", "권한 보호"]
    },
    roomCode: "MM-03A",
    startsAt: "14:00",
    participants: [
      "김진수 · 접속 완료",
      "박서윤 · 오디오 체크 중",
      "최민호 · 접속 완료",
      "서다은 · 곧 입장"
    ],
    accessMembers: [
      { name: "이미주", role: "Host · Product Manager", access: "편집 가능", note: "회의 시작, 참여자 권한 변경" },
      { name: "김진수", role: "Backend Lead", access: "발표 가능", note: "화면 공유, 문서 업로드" },
      { name: "박서윤", role: "Product Designer", access: "참여 가능", note: "음성 참여, 채팅, 자막 확인" },
      { name: "최민호", role: "Data Engineer", access: "참여 대기", note: "초대됨, 시작 후 입장 승인" }
    ],
    checklist: [
      { title: "카메라 권한 허용", meta: "브라우저에서 정상 인식됨" },
      { title: "마이크 입력 확인", meta: "입력 레벨 정상" },
      { title: "도메인 용어사전 연결", meta: "프로젝트 용어 42개 로드" },
      { title: "화면 공유 준비", meta: "발표자가 시작 후 선택 가능" }
    ]
  },
  meetingAi: {
    overview: {
      title: "3회차 전용 Meeting AI",
      subtitle: "현재 회의의 STT 원문, AI 보고서, 결정사항, Action Item만 검색",
      status: ["Scoped Search", "Project 전체 제외"]
    },
    transcript: [
      { time: "06:10:03", speaker: "김진수", text: "ERD 구조를 수정해야 합니다..." },
      { time: "06:12:21", speaker: "이미주", text: "권한 관리는 회의 단위로 분리하는 것이..." },
      { time: "06:14:08", speaker: "박서윤", text: "API 명세는 오늘 확정하죠..." }
    ],
    decisions: [
      { title: "회의/프로젝트 권한 분리", meta: "06:12:21" },
      { title: "RAG 범위 사용자 권한 기준 적용", meta: "06:13:02" }
    ],
    actions: [
      { title: "박서윤 · 접근 제어 UI 설계", meta: "Owner" },
      { title: "김진수 · ERD 수정안 문서화", meta: "Owner" }
    ],
    chat: [
      { role: "user", text: "김진수가 API 구조에 대해 정확히 어떤 의견을 냈어?" },
      { role: "ai", text: "김진수는 ERD의 외래키 제약 누락을 지적하며 구조 수정을 제안했습니다. 출처: 06:10:03 STT 원문" },
      { role: "user", text: "10분 이후에 논의된 내용을 요약해줘." },
      { role: "ai", text: "10분 이후엔 권한 분리 구조와 RAG 검색 범위 연동이 논의됐습니다. 출처: 06:12:21-06:12:55" }
    ],
    suggestions: [
      { label: "최종 합의된 내용만 보여줘", href: "#" },
      { label: "김진수 발언만 모아줘", href: "#" },
      { label: "회의 후반부 요약해줘", href: "#" }
    ]
  },
  projectOverview: {
    overview: {
      title: "FinPilot Renewal - 프로젝트 개요",
      subtitle: "Space 멤버 8명 · 진행 회의 3건 · 최신 보고서 1건",
      status: ["Project AI", "권한 기반 검색"]
    },
    metrics: [
      { label: "진행 회의", value: "3", note: "이번 주 1건 예정" },
      { label: "최신 보고서", value: "1", note: "3회차 생성 완료" },
      { label: "열린 Action Item", value: "5", note: "내 담당 2건" },
      { label: "최근 결정사항", value: "2", note: "오늘 1건 갱신" }
    ],
    techStack: "React, Spring Boot, FastAPI, PostgreSQL, pgvector",
    meetings: [
      { index: "#01", title: "킥오프 - 프로젝트 범위 정의", date: "06.02", state: "완료" },
      { index: "#02", title: "ERD 설계 리뷰", date: "06.09", state: "완료" },
      { index: "#03", title: "API 구조 논의", date: "06.16", state: "보고서 생성됨" },
      { index: "#04", title: "보안 점검 (비공개)", date: "06.23", state: "예정" }
    ],
    documents: [
      { title: "3회차 AI 보고서", meta: "오늘 15:24" },
      { title: "권한 모델 정리본", meta: "어제 21:08" },
      { title: "ERD v2", meta: "06.19" }
    ],
    questions: [
      { label: "왜 PostgreSQL을 선택했어?", href: "/meeting-ai" },
      { label: "내가 맡기로 한 업무가 뭐야?", href: "/meeting-ai" },
      { label: "지금까지 API 설계 논의 흐름 정리", href: "/meeting-ai" }
    ]
  },
  reportAgent: {
    overview: {
      title: "3회차 AI 보고서 편집",
      subtitle: "자동 생성된 회의 보고서를 대화형 Agent로 후처리하는 화면",
      status: ["Report Draft", "Project 문서 저장 가능"]
    },
    reportTitle: "3회차 회의 보고서 - API 구조 논의",
    reportDate: "2026.06.16 · 참여자 4명 · AI 자동 생성",
    decisions: [
      { item: "ERD 수정", decision: "외래키 제약 추가, 3정규형 재검토", note: "박서연 검토" },
      { item: "권한 구조", decision: "회의 단위 권한 분리 적용", note: "RAG 범위 연동" }
    ],
    changes: [
      { title: "report.decisions", meta: "표 형식 반영" },
      { title: "report.summary", meta: "문체 조정" },
      { title: "report.actions", meta: "담당자 라벨 유지" }
    ],
    chat: [
      { role: "user", text: "결정사항을 표 형태로 정리해줘." },
      { role: "ai", text: "결정사항 섹션을 표로 변환했습니다. 우측 미리보기에 반영됐어요." },
      { role: "user", text: "교수님 제출용 문체로 변경해줘." },
      { role: "ai", text: "전체 문체를 격식체로 다시 작성 중입니다..." }
    ]
  }
};
