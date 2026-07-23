# MeetingMind Design System Context

## Source Of Truth

사용자 흐름, 권한 UX, 라우트 계약은 `specs/001-meetingmind-core/frontend-refactor-plan.md`를 따른다. 이 문서는 프론트엔드 구현에서 사용하는 시각적 기준과 공통 UI 규칙을 보완한다.

## Current Stack

- React 18, Vite, TypeScript, CSS Modules가 아닌 전역 CSS
- Tailwind는 현재 사용하지 않는다. 기존 CSS와 토큰을 확장한다.
- 기본 글꼴: `SUIT Variable`, `Pretendard Variable`, `Apple SD Gothic Neo`, `Noto Sans KR`
- 공통 UI: `frontend/src/components/common`
- 공통 레이아웃: `frontend/src/components/layout`

## Color Tokens

`frontend/src/styles/tokens.css`의 값을 기준으로 사용한다.

| Purpose | Token | Usage |
| --- | --- | --- |
| Canvas | `--app-canvas` | 앱 전체 배경 |
| Surface | `--app-surface` | 작업 표면, 다이얼로그 |
| Soft surface | `--app-surface-soft` | 선택, 보조 영역 |
| Primary | `--app-accent` | 주요 행동, 현재 위치, 링크 |
| Primary strong | `--app-accent-strong` | hover, 강조 |
| Success | `--app-green` | 확정, 완료 |
| Warning | `--app-orange` | 주의, 진행 중 |
| Danger | `--app-danger` | 삭제, 실패 |
| Text | `--app-text`, `--app-text-strong` | 본문, 제목 |
| Muted | `--app-muted`, `--app-subtle` | 보조 정보 |

상태 색상을 제외하면 블루를 유일한 제품 강조색으로 사용한다. 임의의 보라색, 별도 그라데이션, 새 강조색은 추가하지 않는다.

## Type And Spacing

- 페이지 제목: `28px`~`38px`, 굵기 700, 줄간격 1.18
- 섹션 제목: `20px`~`28px`, 굵기 700
- 본문: `13px`~`15px`, 줄간격 1.55 이상
- 캡션과 메타: `11px`~`12px`
- 기본 간격: 4, 8, 12, 16, 20, 24, 32px
- 화면 작업 표면 반경: 12px, 입력·버튼: 8px, 작은 상태 배지: 6px

## Component Rules

- 버튼: `primary`, `secondary`, `danger` 세 계층만 사용한다. 화면의 primary action은 하나를 원칙으로 한다.
- 카드: 정보 그룹 자체가 독립된 행동 또는 상태를 가질 때만 쓴다. 카드 안에 카드로 중첩하지 않는다.
- 상태: `StatusBadge`, 역할: `RoleBadge`, 데이터 상태: `DataState`, 위험 행동: `ConfirmDialog`를 우선 사용한다.
- 포커스: `--app-focus-ring`을 사용하며 키보드 탐색에서 제거하지 않는다.
- 로딩: 최종 레이아웃 형태를 반영한 skeleton 또는 `DataState`를 사용한다.
- 오류: 원인을 짧게 설명하고 재시도 또는 상위 화면 이동을 제공한다.

## Layout Rules

- AppShell은 사이드바와 콘텐츠의 책임만 갖는다.
- 프로젝트 화면은 프로젝트 맥락, 페이지 제목, 주요 행동, 콘텐츠 순서로 구성한다.
- 회의 화면은 프로젝트 경로, 회의 상태, 회의 탭, 현재 콘텐츠 순서로 구성한다.
- 실시간 회의는 집중 레이아웃을 사용하며 일반 AppShell을 강제하지 않는다.
- 900px 이하에서 사이드바는 탐색 우선의 압축 레이아웃으로 전환한다. 모바일에서는 텍스트가 잘리지 않게 한 열 흐름을 우선한다.

## Motion

- 허용: hover 색 변화, 1px pressed feedback, 짧은 opacity/transform 전환
- 기본 시간: 120ms, 200ms, 320ms
- `prefers-reduced-motion`에서는 모션을 제거한다.
- 스크롤을 가로채거나 의미 없이 반복되는 모션은 사용하지 않는다.

## Landing Asset Rule

제품 소개 카드의 이미지는 하나의 제품군처럼 보여야 한다.

- 동일한 정면 데스크톱 UI 시점
- 네이비 사이드바, 화이트 작업면, 코발트 블루 포인트
- 이미지 위, 텍스트 아래의 일관된 카드 구조
- 실제 제품 흐름과 연결되는 화면만 사용
