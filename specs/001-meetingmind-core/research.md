이 문서는 MeetingMind Core Prototype의 조사 내용과 기술 선택 근거를 기록하기 위한 Markdown 문서이다.

# Research: MeetingMind Core Prototype

## Existing Services

- Otter.ai, CLOVA Note, Fireflies.ai는 STT, 요약, 검색, Action Item 기능을 제공한다.
- 공통 한계는 회의 단위 기록 중심이며 프로젝트 공식 지식과 회의별 접근 권한을 정교하게 결합하지 않는다는 점이다.

## Product Direction

MeetingMind는 STT 자체보다 회의 지식의 재사용에 집중한다.

- 회의별 상세 탐색: Meeting AI
- 프로젝트 전체 맥락 탐색: Project AI
- 공식 최신 상태: Project Knowledge
- 의사결정 근거: Meeting transcript/report

## Technical Notes

- Spring Boot는 권한 모델과 API 서버의 중심이 된다.
- FastAPI는 AI 호출, 추후 LangChain/RAG, embedding 작업을 담당한다.
- PostgreSQL/pgvector는 권한 관계와 vector 검색을 같은 데이터 기반에서 결합하기 쉽다.
- S3는 원문/보고서/첨부 파일처럼 크거나 보존 정책이 별도인 데이터를 분리하기 좋다.

## Authentication Decision Research

> 이 절의 선택은 Core Prototype 구현 당시 결정이다. 기업 운영 목표는 브라우저 무토큰 Web BFF와 내부 Auth Service로 진화했으며 근거와 대안은 `../002-bff-auth-msa/research.md`를 우선한다.

### Options Considered

- Google OAuth only: 빠른 prototype에는 유리하지만 Backend가 앱 고유 access token을 갖지 못해 Space/Meeting 권한, LiveKit token 발급, AI 컨텍스트 권한 선필터와 연결하기 어렵다.
- Own account/JWT only: 앱 내부 통제는 강하지만 비밀번호 저장, 가입, 재설정, 보안 운영 범위가 커져 현재 prototype의 최소 구현 원칙과 맞지 않는다.
- Google OAuth + MeetingMind access token: Frontend는 Google Identity Services로 ID token을 받고, Backend가 ID token을 검증한 뒤 MeetingMind access token을 발급한다. 외부 identity 검증과 앱 내부 권한 판단을 분리할 수 있다.
- Google OAuth + own account + access/refresh token: Google OAuth와 자체 이메일/비밀번호 로그인을 모두 지원하고 Backend가 access token과 refresh token을 발급한다.

### Prototype Decision

Google OAuth + own account + access/refresh token 병행안을 채택한다. 이유는 다음과 같다.

- Google 계정 사용자와 자체 계정 사용자를 모두 받을 수 있다.
- Backend가 `User`, `SpaceMember`, `MeetingParticipant` 권한 판단의 기준이 되는 subject를 안정적으로 갖는다.
- `Authorization: Bearer {accessToken}`로 Frontend, Backend, LiveKit token 발급, AI context 조립 경계를 일관되게 연결할 수 있다.
- refresh token으로 재로그인을 줄이되, server-side refresh token hash와 revoke 상태를 둬 세션 폐기 경계를 만든다.
- Frontend에서 Google credential payload를 decode하는 현재 흐름은 사용자 표시용으로만 유지하고, 신뢰 경계는 Backend 검증으로 이동한다.

### Deferred

- social provider 다중화
- 조직 도메인 allowlist
- 비밀번호 재설정 이메일
- refresh token rotation 고도화

## Risks

- AI 컨텍스트가 권한 범위를 넘어가면 제품 신뢰가 깨진다.
- mock 데이터가 오래 남으면 실제 보안/데이터 모델 설계가 지연될 수 있다.
- Project AI가 공식 지식과 회의 기록을 구분하지 못하면 답변의 신뢰도가 떨어진다.
- Frontend에서 decode한 Google credential을 인증 근거로 사용하면 위조/만료/대상 audience 검증이 빠질 수 있다.
- 자체 로그인 도입으로 password hash, brute-force 방어, refresh token 폐기 처리를 빠뜨리면 인증 우회나 계정 탈취 위험이 커진다.
