이 문서는 MeetingMind 기능 목록 초안과 현재 기획/구현 상태를 비교하기 위한 기록이다.

# Feature Implementation Comparison

작성일: 2026-07-08

## 기준 전제

- 현재 제품 판단은 `Space 1개 = Project 1개`로 둔다.
- 요구사항 기준선은 `requirements/INDEX.md`에서 라우팅되는 로컬 Markdown 문서다.
- 모든 용어는 `requirements/glossary.md`, 권한은 `requirements/permissions.md`, 상태값은 `requirements/status-values.md`를 기준으로 둔다.
- 현재 저장소는 완성 제품이 아니라 Core Prototype 단계다.
- Frontend는 데모 화면과 일부 실제 API 연결을 제공한다.
- Backend는 Workspace demo API, LiveKit token API, Auth prototype API를 제공한다.
- AI 서버는 Meeting AI, Project AI, 보고서 생성, 태스크 추출, 용어 설명 prototype API를 제공한다.
- PostgreSQL, pgvector, 실제 STT 파이프라인, 실제 권한 기반 RAG 영속화는 아직 구현되지 않았다.

## 전체 판단

| 영역 | 기획 반영 | 현재 구현 상태 |
| --- | --- | --- |
| 인증/인가 | 반영됨 | 인증은 상당 부분 구현, 인가는 아직 미완 |
| 프로젝트/Space | 반영됨 | Space 1개 = Project 1개 전제로 데모 구현 |
| 회의 관리 | 반영됨 | 회의 생성은 프론트 상태 기반, 초대/삭제/권한은 미완 |
| AI/RAG | 핵심 기획과 일치 | AI 서버 prototype은 구현, 프론트 연결은 일부 미완 |
| 화상 회의 | 반영됨 | LiveKit 연결 기반은 있음, 실제 STT 파이프라인은 없음 |
| 데이터 영속화 | 기획됨 | 아직 DB/pgvector 없음, 대부분 mock 또는 in-memory |

## 기능별 비교

| 기능 | 기획/스펙 상태 | 현재 구현 상태 |
| --- | --- | --- |
| 공통: 인증/인가 | Google OAuth + 자체 로그인, access/refresh token으로 결정됨 | Backend Auth API와 Frontend 보호 route 있음. 단, Space/Meeting 권한 인가는 아직 정책/DB 기반 아님 |
| 프로젝트 생성/수정/삭제 | 프로젝트/Space API target으로 계획됨 | 생성은 프론트 state에 구현됨. 수정/삭제는 없음 |
| 캘린더 기능 | 현재 core spec에는 명확히 없음 | 미구현 |
| 회의 생성/초대/삭제 | Meeting API target으로 계획됨 | 회의 생성은 프론트 state에 있음. 초대/삭제는 미구현 |
| 칸반보드 | 현재 core spec에는 Action Item은 있으나 칸반보드는 명확히 없음 | 미구현 |
| 프로젝트별 챗봇 RAG | 기획 핵심과 일치 | AI 서버 `/api/project-ai/chat` prototype 있음. 현재 프론트는 아직 구형 `/api/meeting-ai/ask` 사용 |
| 권한 관리 | SpaceMember, MeetingParticipant, Owner/Admin/Member, 회의 게스트 기준 확정 | 팀원/초대 UI mock은 있음. 실제 권한 정책/검증은 미구현 |
| 오너 권한 이양 | 현재 스펙에 명확히 없음 | 미구현 |
| AI 회의록 생성/수정 | Report Agent와 generate-report 계획 있음 | AI 서버 `/api/meeting-ai/generate-report` 있음. ReportAgent UI는 로컬 시뮬레이션 중심 |
| 회의별 챗봇 RAG | 기획 핵심과 일치 | AI 서버 `/api/meeting-ai/chat` 있음. 현재 Meeting AI 화면은 구형 `/api/meeting-ai/ask` 사용 |
| AI 회의록 기반 태스크 생성 | 기획/AI task에 있음 | AI 서버 `/api/meeting-ai/extract-tasks` 있음. 저장/칸반 반영은 미구현 |
| 실시간 화상 회의 | LiveKit으로 계획됨 | LiveKit token API와 프론트 LiveRoom 연결 있음 |
| STT 기반 다이알로그 | 실제 STT는 기존 Core Prototype에서 Out of Scope로 문서화됨 | 미구현. 화면상 transcript/mock 문맥만 있음 |
| 용어사전 RAG/LLM | AI prototype에 있음 | AI 서버 `/api/meeting-ai/explain-term` 있음. 프론트 실시간 연결은 미완 |

## 현재 구현 단계 요약

현재 단계는 다음과 같이 정리한다.

> MeetingMind Core Prototype은 화면 흐름, 인증 기반, LiveKit 연결, AI/RAG 서버 prototype은 존재하지만, DB/권한/실제 STT/프론트-AI 완전 연결은 아직 남은 상태다.

## 기능 목록에서 기획 문서에 보강할 항목

현재 기능 목록 초안 중 아래 항목은 기존 `specs/001-meetingmind-core` 문서에 명확히 추가하거나 범위를 분리하는 것이 좋다.

- 프로젝트 수정/삭제
- 캘린더 기능
- 칸반보드
- 회의 초대/삭제
- 오너 권한 이양
- STT 기반 다이알로그의 실제 범위
- 용어사전이 프로젝트 용어사전인지, 회의 중 선택 텍스트 설명인지, 또는 둘 다인지 구분

## 요구사항 기준선 반영 상태

- 반영됨: `requirements/*` Markdown 분할, 용어집, 권한 매트릭스, 상태값, 정책, 성능/토큰 목표.
- 반영됨: Q-002 회의 권한 등급은 `HOST`, `EDITOR`, `VIEWER`로 정리했고 회의 게스트는 특정 회의의 MeetingParticipant로 정의했다.
- 반영됨: STT 보존 정책은 7일/30일/영구 선택, 기본 30일로 정리했다.
- 남음: Backend/Frontend/AI/Data 코드와 세부 API 계약이 새 요구사항 기준선과 완전히 일치하는지 T102-T105에서 점검해야 한다.

## 권장 구현 우선순위

1. 인증/인가 + Space/Project 권한 모델 확정
2. 프로젝트 CRUD
3. 회의 CRUD
4. AI 회의록 생성 + 회의별 챗봇 연결
5. 태스크 후보 생성 후 칸반 반영
6. 프로젝트별 챗봇 RAG 연결
7. 캘린더, 오너 이양, 세부 권한 관리
8. 실제 STT 파이프라인 + 용어사전 실시간화

## 현재 기능 목록과 기존 Core Prototype의 차이

기존 Core Prototype은 MeetingMind의 핵심 방향인 Space 기반 프로젝트 관리, 회의 단위 접근 모델, Meeting AI, Project AI, Report Agent, LiveKit 연결을 잡는 데 초점이 있다.

새 기능 목록 초안은 여기에 실제 제품 운영에 필요한 CRUD, 캘린더, 칸반보드, 회의 초대/삭제, 오너 이양, STT 실시간 다이알로그를 추가한다. 따라서 다음 기획 정리 단계에서는 Core Prototype을 유지하되, 새 기능을 별도 milestone으로 나누는 것이 적절하다.
