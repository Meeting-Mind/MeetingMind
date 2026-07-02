이 문서는 MeetingMind Core Prototype의 미결정 질문과 확정된 해석을 기록하기 위한 Markdown 문서이다.

# Clarification Log: MeetingMind Core Prototype

## Questions

| ID | Priority | Question | Why It Matters | Status | Decision |
| --- | --- | --- | --- | --- | --- |
| Q-001 | High | 로그인은 Google OAuth 단독으로 시작할까, 자체 계정/JWT를 병행할까? | Backend 보안 구조와 Frontend 인증 흐름을 결정한다. | Open | |
| Q-002 | High | 회의 권한 등급은 host/editor/participant/viewer로 충분한가? | API 권한 모델과 UI 제어 범위를 결정한다. | Open | |
| Q-003 | Medium | STT 원문 기본 보존 기간은 7일, 30일, 영구 중 무엇인가? | 저장 비용, 개인정보, 삭제 작업 설계를 결정한다. | Open | |
| Q-004 | Medium | Project Knowledge는 누가 공식 승인하고 최신화하는가? | Project AI가 공식 지식과 회의 기록을 구분하는 기준이 된다. | Open | |
| Q-005 | Low | 보고서 파일 포맷은 Markdown, HTML, PDF, DOCX 중 무엇을 우선할까? | Report Agent 저장/다운로드 구현 방향을 결정한다. | Open | |

## Blocking Decisions

- Q-001은 Backend 인증/인가 모델과 Frontend 로그인 흐름 구현 전에 결정해야 한다.
- Q-002는 `MeetingParticipant.role`, 권한 필터, 회의 UI 제어 구현 전에 결정해야 한다.

## Current Assumptions

- 프로토타입 단계에서는 로그인/인가를 mock 상태로 표현한다.
- 실제 보안 구현 전에는 AI 컨텍스트에 민감 데이터를 넣지 않는다.
- Meeting AI를 먼저 안정화한 뒤 Project AI RAG를 구현한다.
