# Plan: Knowledge 그래프 리디자인

## 기술 결정

### 새 의존성 (AGENT.md 구현 판단 순서 5 — 사유 문서화)

| 패키지 | 사유 | 대안 검토 |
| --- | --- | --- |
| `@tanstack/react-query` | server state 캐시·재검증·중복 fetch 제거. 현재 useState+useEffect 수동 fetch가 화면마다 중복되고 캐시가 없음 | 자체 구현: 캐시 무효화·포커스 재검증까지 만들면 유지보수 비용이 라이브러리 도입보다 큼 |
| `zustand` | 그래프 화면 client state(필터·선택·힘 파라미터) 전역 관리. 기존 App.tsx는 40여 개 useState 난립 | Context+useReducer: 렌더 최적화(selector) 부재로 캔버스 프레임마다 리렌더 유발 |
| `d3-force` | 옵시디언과 동급의 힘 기반 레이아웃 엔진. 검증된 물리 파라미터(alpha, velocityDecay) | 자체 구현: 시안 단계에서 직접 구현해봤으나 안정화·수렴 품질이 d3-force 대비 낮음 |

`d3-zoom`/`d3-selection`은 **추가하지 않는다** — pan/zoom/drag는 PointerEvent로 직접 처리(GraphCanvas.tsx), DOM 의존성 최소화.

### 렌더링
- 단일 `<canvas>` 2D 렌더링 (SVG DOM 노드 수백 개 회피, 60fps 목표).
- 노드 글로우: `createRadialGradient`, 색은 전부 `tokens.css` CSS 변수에서 `getComputedStyle`로 읽음.
- 테마 전환: `html[data-theme]` MutationObserver로 팔레트 재로딩.
- 라벨: 월드 좌표가 아닌 스크린 좌표로 그려 줌 배율과 무관하게 선명.

### 상태 경계
- server state: `useKnowledgeGraphQuery` (TanStack Query, key `["knowledge","graph",spaceId]`).
- client state: `useKnowledgeGraphStore` (zustand — hiddenKinds, showOrphans, search, selectedId, forces, display).
- 시뮬레이션 상태(x/y/vx/vy)는 React 밖(ref)에서 관리, 필터 변경 시 노드 객체 재사용으로 좌표 보존.

### 파일 구조
```
features/knowledge/
├── KnowledgeGraphPage.tsx   # 시안 B 레이아웃 조립
├── GraphCanvas.tsx          # d3-force + canvas + 인터랙션
├── GraphSettingsPanel.tsx   # 필터/그룹/표시/힘
├── NotePreviewPanel.tsx     # 우측 프리뷰
├── api.ts                   # 어댑터 buildGraphView + fetch
├── api.test.ts              # 어댑터 단위 테스트 (vitest)
├── hooks.ts                 # query 훅
├── store.ts                 # zustand
└── types.ts                 # VM 타입, kind→토큰 매핑
```

## 마이그레이션
- 기존 `ProjectKnowledge`(App.tsx 내 1,190줄)는 `/knowledge/legacy`로 이전 유지. 새 화면 안정화 후 제거.
- `main.tsx`에 QueryClientProvider 추가 — 이후 다른 feature도 이 기반 사용.

## 검증
- `buildGraphView` 단위 테스트 4건 (중복 노드, dangling edge, self-loop, explicit 분류, orphan).
- `tsc --noEmit` 통과.
- 수동: 라이트/다크 전환, 노드 드래그, 필터 토글 시 좌표 유지, 빈 상태/오류 상태.
