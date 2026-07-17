# Feature Implementation Comparison

최종 점검일: 2026-07-15

이 문서는 요구사항 대비 현재 구현 경계를 빠르게 확인하는 요약이다. 상세 실행 상태는 `tasks.md`, 검증 이력은 `implement.md`, API 계약은 `contracts/*`를 기준으로 한다.

## 기준

- `Space 1개 = Project 1개`로 해석한다.
- 요구사항은 `requirements/INDEX.md`에서 라우팅되는 문서를 기준으로 한다.
- 구현 상태는 `연동`, `부분 연동`, `화면/프로토타입`, `미구현`으로 구분한다.
- PostgreSQL migration은 target schema 기준선이며 runtime repository 구현을 의미하지 않는다.
- mock과 in-memory 데이터는 운영 영속 데이터로 간주하지 않는다.

## 전체 상태

| 영역 | 상태 | 현재 구현 경계 |
| --- | --- | --- |
| 인증/인가 | 현재 호환 구현, 목표 재설계 | Backend signup/login/Google/refresh/logout과 refresh hash가 PostgreSQL에 영속화되고 Frontend 보호 route가 있다. Frontend token 저장·자동 refresh/logout gap은 `specs/002-bff-auth-msa`의 별도 BFF 서버 세션 전환으로 해소할 계획이다. |
| 프로젝트/Space | 부분 연동 | Backend 목록·생성과 Frontend 연결이 있다. 수정·삭제·상세·대시보드 API는 로컬 상태 또는 미구현이다. |
| 회의/권한 | 연동 | PostgreSQL 기반 회의 생성·목록·상세·수정·soft delete와 Frontend 상세/participant ACL, 참가 신청/승인, AI·LiveKit 권한 검증 경로가 있다. hard purge·복구·삭제 유예 운영은 남아 있다. |
| AI/RAG | 부분 연동 | Meeting AI, Project AI, 보고서 candidate, 태스크 candidate가 Backend 권한 선필터 뒤에서 AI internal API를 호출한다. 실제 pgvector와 embedding worker는 없다. |
| 화상 회의 | 화면/프로토타입 | LiveKit 화면과 token API가 있다. Frontend는 아직 인증된 meeting token 경로로 완전히 전환되지 않았다. |
| STT/용어 | 화면/프로토타입 | mock transcript와 AI 용어 설명 prototype이 있다. 실제 STT 수집·저장·다운로드와 Backend 경유 용어 설명은 없다. |
| 데이터 영속화 | 부분 연동 | Flyway V1~V11과 Auth/Workspace/Meeting/산출물 JDBC runtime이 연결됐다. pgvector retriever, embedding worker와 실제 STT pipeline은 남아 있다. |

## 기능별 상태

| 기능 | 상태 | 구현 내용과 남은 경계 |
| --- | --- | --- |
| 이메일·Google 인증 | 현재 호환 구현, 목표 재설계 | Backend token 발급·갱신·폐기와 PostgreSQL 저장, Frontend 로그인은 구현됐다. 목표에서는 Google 검증은 유지하되 token을 BFF 내부에만 두고 브라우저는 서버 세션을 사용한다. |
| 프로젝트 생성/수정/삭제 | 부분 연동 | 생성·목록은 Backend API를 사용한다. 수정·삭제는 Frontend 로컬 흐름이며 Backend endpoint가 없다. |
| 캘린더 | 부분 연동 | ACL-filtered Space meeting 목록으로 월/주/일 일정과 생성·오류·회의 이동을 연결했다. 전용 Backend 일정 조회 API와 알림은 없다. |
| 회의 생성/참가/삭제 | 연동 | 생성·목록·상세·수정·soft delete API, Frontend 초기 참여자/참가 코드·URL/participant ACL, URL·코드 참가 신청·HOST 승인이 연결됐다. hard purge·복구·유예 기간은 후속이다. |
| 칸반보드 | 부분 연동 | Frontend 보드와 태스크 candidate 확정 시 Backend TaskCard 생성이 있다. 일반 카드 CRUD·목록 영속화는 남아 있다. |
| 프로젝트별 챗봇 | 부분 연동 | Frontend→Backend→AI 경로와 SpaceMember/Meeting ACL 선필터가 구현됐다. 실제 ProjectKnowledge API·pgvector 검색·대화 이력은 남아 있다. |
| 프로젝트 권한 관리 | 부분 연동 | SpaceRole/MeetingRole 정책과 default-deny 검증 계층은 있다. 멤버 초대·역할 변경·제거·오너 이양 API는 없다. |
| AI 회의록 | 부분 연동 | Backend 권한 검증 뒤 candidate 생성과 current confirmed report 전환이 구현됐다. 수정·이력·다운로드·영속 저장은 남아 있다. |
| 회의별 챗봇 | 연동 | 단일 meeting source 검증, 출처 반환, 근거 없음 처리와 Backend 경유 호출이 구현됐다. 실제 STT repository 연동은 남아 있다. |
| AI 태스크 생성 | 부분 연동 | 단일 meeting source 기반 candidate 생성·조회·검토·TaskCard 확정이 구현됐다. TTL·제외 API·일반 Kanban 연동은 남아 있다. |
| 실시간 화상 회의 | 부분 연동 | LiveKit 송수신과 미디어 제어 화면이 있다. 인증 없는 legacy token 경로 제거와 meetingId 기반 입장 연결이 필요하다. |
| STT 다이알로그 | 미구현 | 화면의 transcript는 mock이다. provider 연동, 발화자 식별, 저장·보존·다운로드가 필요하다. |
| 용어사전 RAG/LLM | 화면/프로토타입 | glossary 우선·미등록 용어 AI 설명과 근거 제한 prototype이 있다. Backend 권한 경유와 관리 API는 남아 있다. |

## 현재 단계 판단

현재 저장소는 **PostgreSQL 기반 권한·회의 CRUD가 연결된 AI 통합 prototype** 단계다. meeting/project scope, source 검증, candidate 승인과 핵심 Meeting CRUD는 연결됐지만 운영 제품 전환에는 실제 STT, pgvector/embedding worker, 남은 관리 CRUD와 서비스 간 인증이 필요하다.

## 다음 우선순위

1. AI internal API 서비스 인증과 public prototype 운영 차단
2. 프로젝트·멤버·Kanban의 남은 CRUD 완성
3. Meeting hard purge·복구·삭제 유예 운영 정책 결정
4. 실제 STT 저장·조회와 Meeting AI context 연결
5. pgvector retriever와 비동기 embedding worker
6. persistent audit, token budget, 관측성 보강
