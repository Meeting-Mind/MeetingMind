# Tasks: Knowledge 그래프 리디자인

## Milestone 1 — 그래프 화면 수직 슬라이스 (frontend) ✅ 완료

- [x] T1. 상태관리 기반: `@tanstack/react-query` + `zustand` + `d3-force` 의존성 추가, `lib/queryClient.ts`, main.tsx Provider 연결
- [x] T2. `features/knowledge/` 어댑터·타입: `buildGraphView` + 단위 테스트 4건
- [x] T3. zustand 스토어 + query 훅
- [x] T4. GraphCanvas (d3-force + canvas 글로우 + pan/zoom/드래그/hover + 테마 옵저버)
- [x] T5. 설정 패널 + 노트 프리뷰 + 페이지 조립 (시안 B 레이아웃)
- [x] T6. 라우트 교체: `/knowledge` → KnowledgeGraphPage
- [x] T7. 검증: `tsc --noEmit` 통과 (오류 0)
  - 미실행: `vitest`는 개발자 로컬(macOS)에서 실행 필요. Cowork 원격 VM은 rolldown 네이티브 바이너리 미호환.

## Milestone 2 — legacy 전면 교체 ✅ 완료

- [x] T8. 편집 기능 이전: `KnowledgeEditorDialog`(등록/수정), 프리뷰 패널 수정·보관 버튼, 보관 실행취소 토스트
  - 권한: `spaceDetail.role`이 OWNER/ADMIN일 때만 편집 UI 노출 (constitution 3·5)
- [x] T9. legacy 제거: `ProjectKnowledge` + 전용 헬퍼 1,542줄, `/knowledge/legacy` 라우트, 고아 import(API 6·타입 3·아이콘 5) 삭제
  - App.tsx 9,708줄 → 8,067줄

## Milestone 3 — transcription 슬라이스 ✅ 1단계 완료

- [x] T10. `features/transcription/` 신설: api / selectors / hooks / types
- [x] T11. MeetingTranscript·LiveMeeting의 개별 `setInterval(2500)` 제거 → 공유 `useMeetingDialogueQuery`
  - 두 화면이 같은 queryKey를 공유해 중복 요청 제거, 화면 재진입 시 캐시 즉시 표시
  - partial key를 배열 index → `partial:{speakerLabel}`로 변경 (순서 변동 시 DOM 재사용 오류 수정)
  - 확정된 발화와 동일한 partial 중복 표시 제거
- [x] T12. 검증: selectors 단위 테스트 6건 작성, `tsc --noEmit` 통과
- [ ] T13. **WebSocket 전환** — 백엔드 BFF 게이트웨이 선행 필요
  - sequence 기반 복구, 이벤트 중복 제거, heartbeat, reconnect
  - 훅 내부만 교체하면 되도록 호출부는 이미 분리해 둠

## Milestone 4 — 후속 (순서 고정)

> **선행 조건: `git pull origin dev` 병합 완료.**
> App.tsx·types.ts는 dev 브랜치와 충돌 가능성이 높아 병합 전 구조 리팩터링을 하지 않는다.
> (AGENTS.md 병렬 작업 지침: 같은 파일 동시 수정 금지)

- [ ] T14. App.tsx 8,067줄 → `pages/` + `routes/router.tsx` 분해
  - 선결정: `routes/AppRoutes.tsx` 병행 구현 통합 여부
- [ ] T15. types.ts 957줄 → `types/{meeting,transcript,knowledge,space,ai}.ts`
- [ ] T16. app.css 14,788줄 → 화면 분해와 함께 Tailwind 점진 이관 (빅뱅 금지)
- [ ] T17. knowledge 그래프 e2e 스모크 (노드 렌더·필터·프리뷰·CRUD)

## Milestone 5 — Note/Link 백엔드 (shared contract 선행)

- [ ] T18. contracts 확정 (data-model.md 기반, 백엔드 owner 합의)
- [ ] T19. **그래프 링크 0 문제 수정** — 원인 규명 완료
  - `ai/app/repository.py` `knowledge_graph(similarity_threshold=0.78)` 하드코딩
  - `KnowledgeGraphRequest`에 threshold 필드가 없어 호출부에서 조정 불가
  - 노트 수가 적거나 주제가 다르면 어떤 쌍도 0.78을 넘지 못해 `edges=[]`
  - 수정안: threshold를 요청 파라미터로 승격 (ai → backend DTO → 프론트 슬라이더)
  - 보류 사유: `ai/app/main.py`, `ai/app/repository.py`에 다른 미커밋 작업이 있어 병합 후 진행
- [ ] T20. Note 테이블 + 마이그레이션, 위키링크 파서 + Link upsert, 백링크 API
- [ ] T21. 그래프 API v2 (edgeType, 로컬 그래프, 페이지네이션) + 프론트 어댑터 교체
