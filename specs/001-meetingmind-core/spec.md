이 문서는 MeetingMind Core Prototype의 기능 목표와 요구사항을 정의하기 위한 Markdown 문서이다.

# Feature Specification: MeetingMind Core Prototype

## Summary

MeetingMind의 핵심 프로토타입은 Space 기반 프로젝트 관리, 회의 단위 접근 모델, Meeting AI, Project AI, AI 보고서 편집 흐름을 하나의 제품 경험으로 정리한다.

## Why

- 기존 AI 회의록 서비스는 회의 단위 기록에 머무르며 프로젝트 전체 맥락을 보존하기 어렵다.
- MeetingMind는 회의별 논의와 프로젝트 공식 지식을 분리해 축적하고, AI가 권한 범위 안에서 탐색하도록 한다.
- 현재 저장소의 React/Spring/FastAPI 프로토타입을 앞으로의 에이전틱 코딩 기준점으로 고정한다.

## Users

- Primary: 프로젝트 리더, 회의 주최자, 팀원
- Secondary: 새로 합류한 팀원, 보고서 검토자, 운영 관리자

## Scope

### In Scope

- Workspace에서 참여 중인 Space와 최근 업데이트를 확인한다.
- Space 내 회의 목록, 문서, Action Item, Project AI 진입점을 제공한다.
- 회의방 입장 전 참여자와 권한 상태를 확인한다.
- Google OAuth와 자체 회원가입/로그인 기반의 최소 인증 흐름을 제공한다.
- LiveKit 토큰을 발급해 실시간 회의방 연결 준비를 한다.
- Meeting AI는 현재 회의 맥락만 사용해 질문에 답한다.
- Report Agent는 AI 생성 회의 보고서 편집 흐름을 제공한다.
- 현재는 mock 데이터와 최소 API를 허용하되 실제 구현 전환 지점을 명확히 문서화한다.

### Out of Scope

- 조직 관리, 초대 승인 정책, 감사 로그까지 포함한 완전한 사용자/관리자 인가
- 실제 STT 파이프라인
- PostgreSQL/pgvector 영속화
- S3 파일 저장
- Project AI의 실제 멀티 회의 RAG
- 운영 배포 자동화

## Functional Requirements

- FR-001: 사용자는 워크스페이스 홈에서 참여 중인 Space와 오늘 회의를 볼 수 있어야 한다.
- FR-002: 사용자는 Space 개요에서 회의 목록, 최신 문서, Project AI 질문 예시를 볼 수 있어야 한다.
- FR-003: 사용자는 회의 입장 전 참여자 상태와 접근 권한을 확인할 수 있어야 한다.
- FR-004: Backend는 LiveKit 접속을 위한 제한 시간 토큰을 발급해야 한다.
- FR-005: Meeting AI는 요청으로 전달된 transcript, decisions, actions만 컨텍스트로 사용해야 한다.
- FR-006: AI 응답은 한국어로 간결하게 작성하고 근거가 없으면 확인 불가라고 답해야 한다.
- FR-007: 프론트엔드는 backend API 실패 시 데모용 mock 데이터로 동작할 수 있어야 한다.
- FR-008: Report Agent 화면은 보고서 초안, 결정사항, 편집 대화 흐름을 보여야 한다.
- FR-009: 사용자는 Google OAuth 또는 자체 이메일/비밀번호 계정으로 로그인할 수 있어야 한다.
- FR-010: 랜딩 페이지를 제외한 앱 화면은 로그인된 사용자만 접근할 수 있어야 한다.

## Non-Functional Requirements

- NFR-001: secret은 환경변수 또는 로컬 `.env`에서만 읽고 저장소에 커밋하지 않는다.
- NFR-002: AI 컨텍스트는 권한 필터링된 데이터만 포함해야 한다.
- NFR-003: UI는 업무형 협업 도구로 빠르게 스캔 가능한 밀도를 유지한다.
- NFR-004: 모든 API 입력은 서버 측에서 검증한다.
- NFR-005: 프로토타입 mock 데이터와 실제 데이터 소스의 경계를 코드/문서에서 유지한다.

## Data and Permission Rules

- Space 멤버십은 프로젝트 접근의 기본 단위다.
- Meeting 접근 권한은 Space 멤버십보다 더 좁은 회차 단위 권한이다.
- Meeting AI 컨텍스트는 단일 회의 데이터로 제한한다.
- Project AI 컨텍스트는 Project Knowledge와 사용자가 접근 가능한 회의 데이터만 포함한다.
- STT 원문과 보고서는 보존 정책 대상이다.

## Acceptance Criteria

- AC-001: 세 서비스의 역할과 책임이 문서화되어 있다.
- AC-002: 스펙, 계획, 작업 목록이 권한 기반 AI 원칙과 충돌하지 않는다.
- AC-003: 향후 구현자가 mock 데이터 제거, 인증 추가, RAG 도입의 순서를 이해할 수 있다.
- AC-004: 최소 검증 명령이 문서화되어 있다.

## Open Questions

- Q-001: Google OAuth와 자체 회원가입/로그인을 병행하고 access/refresh token을 사용하기로 결정했다.
- Q-002: 회의별 권한 등급은 host/editor/participant/viewer로 충분한지 결정해야 한다.
- Q-003: STT 원문 기본 보존 기간의 제품 기본값을 정해야 한다.
- Q-004: Project Knowledge의 승인/갱신 주체를 정해야 한다.
