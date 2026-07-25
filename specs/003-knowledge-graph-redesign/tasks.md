# Tasks: Knowledge 그래프 리디자인

## Milestone 1 — 그래프 화면 수직 슬라이스 (frontend)

- [x] T1. 상태관리 기반: `@tanstack/react-query` + `zustand` + `d3-force` 의존성 추가, `lib/queryClient.ts`, main.tsx Provider 연결
  - files: package.json, src/lib/queryClient.ts, src/main.tsx
- [x] T2. `features/knowledge/` 어댑터·타입: `buildGraphView` + 단위 테스트
  - files: features/knowledge/{types,api,api.test}.ts — 검증: vitest 4건
- [x] T3. zustand 스토어 + query 훅
  - files: features/knowledge/{store,hooks}.ts
- [x] T4. GraphCanvas (d3-force + canvas 글로우 + pan/zoom/드래그/hover + 테마 옵저버)
  - files: features/knowledge/GraphCanvas.tsx
- [x] T5. 설정 패널 + 노트 프리뷰 + 페이지 조립 (시안 B 레이아웃)
  - files: features/knowledge/{GraphSettingsPanel,NotePreviewPanel,KnowledgeGraphPage,index}.tsx
- [x] T6. 라우트 교체: `/knowledge` → KnowledgeGraphPage, `/knowledge/legacy` → 기존 ProjectKnowledge
  - files: src/App.tsx
- [ ] T7. 검증: 로컬 `npm install` 후 `tsc --noEmit` + `vitest run src` + 수동 확인
  - 완료 기준: 타입 오류 0, 테스트 통과, 라이트/다크 정상 렌더

## Milestone 2 — Note/Link 백엔드 (backend, shared contract 선행)

- [ ] T8. contracts: Note/Link API 계약 확정 (data-model.md 기반, 백엔드 owner와 합의)
- [ ] T9. Note 테이블 + 기존 knowledge 데이터 마이그레이션
- [ ] T10. 위키링크 파서 + Link upsert + 백링크 API
- [ ] T11. 그래프 API v2 (edgeType, 로컬 그래프, 페이지네이션)
- [ ] T12. 프론트 어댑터 교체 + 백링크 패널 확장

## Milestone 3 — 구조 리팩토링 계속 (BACKLOG.md 참조)
