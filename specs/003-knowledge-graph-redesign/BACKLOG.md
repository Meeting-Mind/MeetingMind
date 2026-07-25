# Frontend 리팩토링 백로그 (2026-07-25 기준, 우선순위순)

## P1. features/transcription 분리 + 폴링 → WebSocket
- `MeetingTranscript`(App.tsx 3147~)와 `LiveMeeting`(7057~)의 2.5초 `setInterval` 폴링 중복 제거.
- `features/transcription/{api,hooks,store,components}` — rows/partials 구분 유지 (AGENTS.md STT Rules).
- BFF WebSocket 게이트웨이 도입 시 sequence 기반 복구·이벤트 중복 제거·reconnect·heartbeat 구현.
- 이행 전 임시 개선: 폴링을 TanStack Query `refetchInterval`로 옮겨 캐시·중복 요청 정리.

## P2. App.tsx(9,710줄) 분해
- knowledge feature에서 만든 패턴(features/*, query 훅, zustand)을 템플릿으로 사용.
- 순서: (1) pages/로 라우트 컴포넌트 이동 → (2) routes/router.tsx로 라우터 분리 → (3) AuthContext·AppPreferences를 app/providers로 → (4) 공용 헬퍼 utils/로.
- AGENTS.md 주의: routes/AppRoutes.tsx 병행 구현은 런타임 미연결 — 통합은 별도 마이그레이션 태스크로 결정 후 진행.

## P3. types.ts(957줄) 도메인 분할
- types/{meeting,transcript,knowledge,space,ai,...}.ts — 규칙 문서와 일치시키기.

## P4. 스타일 단일화 (Tailwind + tokens)
- 신규 코드 app.css 금지(knowledge feature부터 적용 중).
- app.css 14,788줄은 화면 분해 시 해당 화면 몫만 Tailwind로 이관 후 삭제 — 빅뱅 금지.

## P5. legacy knowledge 화면 제거
- 새 그래프 화면 안정화 후 `/knowledge/legacy` 라우트와 ProjectKnowledge(1,190줄) + 관련 헬퍼 삭제.

## P6. 테스트 커버리지
- 현재 auth 2건 + knowledge 어댑터 4건. meeting/transcript 도메인 훅 테스트 추가.
- e2e: knowledge 그래프 스모크(노드 렌더·필터·프리뷰) 추가.
