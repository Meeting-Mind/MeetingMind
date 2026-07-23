import type {
  InviteMeta,
  JoinRequest,
  ProjectMeeting,
  ProjectTaskState,
  TeamMember
} from "./workspaceTypes";
import { mockData } from "../data/mockData";

export const initialWorkspaceData = mockData;

export const initialProjectMeetings: Record<string, ProjectMeeting[]> = {
  "FinPilot Renewal": [
    { index: "#01", title: "킥오프 - 프로젝트 범위 정의", date: "06.02", state: "완료" },
    { index: "#02", title: "ERD 설계 리뷰", date: "06.09", state: "완료" },
    { index: "#03", title: "API 구조 논의", date: "06.16", state: "보고서 생성됨" },
    { index: "#04", title: "보안 점검 (비공개)", date: "06.23", state: "예정" },
    { index: "#05", title: "RAG 검색 품질 리뷰", date: "06.27", state: "완료" },
    { index: "#06", title: "권한 정책 검토", date: "07.01", state: "보고서 생성됨" },
    { index: "#07", title: "문서 저장 구조 정리", date: "07.04", state: "완료" },
    { index: "#08", title: "실시간 회의 플로우 최종 점검", date: "07.08", state: "예정" }
  ],
  "Campus Admin Assistant": [
    { index: "#01", title: "운영 자동화 킥오프", date: "06.05", state: "완료" },
    { index: "#02", title: "관리자 권한 구조 논의", date: "06.12", state: "보고서 생성됨" },
    { index: "#03", title: "감사 로그 저장 정책", date: "06.18", state: "완료" },
    { index: "#04", title: "배포 전 체크리스트 검토", date: "06.25", state: "예정" },
    { index: "#05", title: "운영 이슈 대응 흐름 정리", date: "07.02", state: "예정" }
  ]
};

export const initialProjectMembers: Record<string, TeamMember[]> = {
  "FinPilot Renewal": [
    { name: "이미주", email: "miju@meetingmind.ai", role: "Product Manager", spaceRole: "OWNER", since: "2026.03 합류", access: "프로젝트 오너", rank: "팀 리드", status: "active" },
    { name: "김진수", email: "jinsu@meetingmind.ai", role: "Backend Lead", spaceRole: "ADMIN", since: "2026.02 합류", access: "프로젝트 관리자", rank: "Lead", status: "active" },
    { name: "박서윤", email: "seoyun@meetingmind.ai", role: "Product Designer", spaceRole: "MEMBER", since: "2026.04 합류", access: "회의 참여 / 문서 열람", rank: "Senior", status: "active" },
    { name: "최민호", email: "minho@meetingmind.ai", role: "Data Engineer", spaceRole: "MEMBER", since: "2026.01 합류", access: "기술 회의 참여", rank: "Senior", status: "away" }
  ],
  "Campus Admin Assistant": [
    { name: "정하늘", email: "haneul@meetingmind.ai", role: "Project Manager", spaceRole: "OWNER", since: "2026.02 합류", access: "프로젝트 오너", rank: "팀 리드", status: "active" },
    { name: "김도윤", email: "doyun@meetingmind.ai", role: "Frontend Developer", spaceRole: "ADMIN", since: "2026.03 합류", access: "프로젝트 관리자", rank: "Mid-level", status: "active" },
    { name: "이서진", email: "seojin@meetingmind.ai", role: "Backend Developer", spaceRole: "MEMBER", since: "2026.01 합류", access: "기술 회의 편집", rank: "Senior", status: "active" },
    { name: "박가은", email: "gaeun@meetingmind.ai", role: "QA Engineer", spaceRole: "MEMBER", since: "2026.04 합류", access: "문서 열람 / 회의 참여", rank: "Associate", status: "away" }
  ]
};

export const initialProjectRequests: Record<string, JoinRequest[]> = {
  "FinPilot Renewal": [
    { id: "fin-wait-01", name: "서다은", email: "daeun@meetingmind.ai", role: "Frontend Developer", meetingIndex: "#08", meetingTitle: "실시간 회의 플로우 최종 점검", requestedAt: "방금 전", source: "링크" }
  ],
  "Campus Admin Assistant": [
    { id: "caa-wait-01", name: "윤민재", email: "minjae@meetingmind.ai", role: "Operations Manager", meetingIndex: "#04", meetingTitle: "배포 전 체크리스트 검토", requestedAt: "12분 전", source: "코드" }
  ]
};

export const initialProjectInvites: Record<string, InviteMeta> = {
  "FinPilot Renewal": { link: "https://meetingmind.ai/invite/finpilot-renewal", code: "FIN-TEAM-0316" },
  "Campus Admin Assistant": { link: "https://meetingmind.ai/invite/campus-admin-assistant", code: "CAA-TEAM-0821" }
};

export const initialProjectTasks: Record<string, ProjectTaskState[]> = {
  "FinPilot Renewal": [
    {
      id: "task-fin-001",
      title: "ERD 수정안 문서화",
      description: "3회차 결정사항 기준으로 외래키와 권한 관계를 정리합니다.",
      status: "TODO",
      priority: "MEDIUM",
      labels: [],
      assignee: "김진수",
      dueDate: "2026-07-12",
      meetingKey: "FinPilot Renewal:#03",
      sourceCandidateId: null
    },
    {
      id: "task-fin-002",
      title: "접근 제어 UI 설계",
      description: "회의 ACL role과 override 상태를 화면에 반영합니다.",
      status: "IN_PROGRESS",
      priority: "MEDIUM",
      labels: [],
      assignee: "박서윤",
      dueDate: "2026-07-15",
      meetingKey: "FinPilot Renewal:#03",
      sourceCandidateId: null
    }
  ],
  "Campus Admin Assistant": [
    {
      id: "task-caa-001",
      title: "운영 자동화 예외 케이스 정리",
      description: "관리자 권한별 승인 흐름과 실패 케이스를 문서화합니다.",
      status: "TODO",
      priority: "MEDIUM",
      labels: [],
      assignee: "정하늘",
      dueDate: "2026-07-18",
      meetingKey: "Campus Admin Assistant:#02",
      sourceCandidateId: null
    }
  ]
};
