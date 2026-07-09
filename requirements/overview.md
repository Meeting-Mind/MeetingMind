# Requirements Overview

## Document Metadata

| Field | Value |
| --- | --- |
| 문서명 | MeetingMind 기능요건·비기능요건 정의서 (상세) |
| 프로젝트 | MeetingMind |
| 버전 | v2.0 (상세) |
| 작성일 | 2026-07-08 |
| 문서 상태 | Draft |
| 기준 | 화면별 기능목록 + 기획서/요구사항정의서/Constitution |
| 기능요건 수 | 96건 |
| 비기능요건 수 | 51건 |

## Priority

| Priority | Meaning |
| --- | --- |
| P0 | 필수. MVP 핵심이며 없으면 제품 성립 불가 |
| P1 | 핵심. 제품 가치 핵심 기능 |
| P2 | 향후. 개선 또는 확장 항목 |

## Product Boundary

MeetingMind는 회의 기록을 프로젝트 지식 자산으로 전환하는 AI 협업 플랫폼이다.
핵심 범위는 Space 기반 프로젝트 관리, 회의 회차 단위 접근 제어, Meeting AI, Project AI, 보고서/태스크 생성, 권한 기반 RAG다.

## Local Snapshot Structure

- `glossary.md`: 표준 용어와 코드명
- `permissions.md`: SpaceRole, MeetingParticipant, 회의 게스트 권한
- `status-values.md`: 주요 entity status enum
- `functional-requirements.md`: FR 전체 목록
- `non-functional-requirements.md`: NFR 전체 목록
- `policies.md`: 정책값
- `performance.md`: 성능, 토큰, 외부 API 목표
