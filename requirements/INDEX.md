# MeetingMind Requirements Index

이 디렉터리는 Google Sheets 요구사항 정의서를 구현자가 읽기 쉬운 Markdown 기준선으로 나눈 것이다.
기능 구현 전에는 이 파일을 먼저 읽고, 아래 라우팅에 해당하는 문서만 추가로 읽는다.

원본 기준선: https://docs.google.com/spreadsheets/d/1EV3gZbBJd2PpelykwdhixXOtmiwriloXKcjmRzN352c/edit

## Canonical Rule

- 모든 제품/코드/스펙 용어는 `requirements/glossary.md`를 기준으로 한다.
- 권한 판단은 `requirements/permissions.md`를 기준으로 한다.
- 상태 enum과 전이는 `requirements/status-values.md`를 기준으로 한다.
- 성능, 토큰, 외부 API 목표는 `requirements/performance.md`를 기준으로 한다.
- 기능 요구사항 ID는 `requirements/functional-requirements.md`를 기준으로 한다.
- 기능 요구사항 전체 우선순위 상세 조건은 `requirements/functional-requirements-detail.md`를 기준으로 한다.
- 비기능 요구사항 ID는 `requirements/non-functional-requirements.md`를 기준으로 한다.
- 비기능 요구사항 전체 우선순위 상세 조건은 `requirements/non-functional-requirements-detail.md`를 기준으로 한다.
- 정책값은 `requirements/policies.md`를 기준으로 한다.

## Read Routing

| 작업 유형 | 반드시 읽을 문서 |
| --- | --- |
| 신규 기능 기획/스펙 작성 | `overview.md`, `glossary.md`, 관련 기능 요구사항 |
| 인증/세션/토큰 | `functional-requirements.md`와 `functional-requirements-detail.md`의 `FR-AUTH-*`, `policies.md`, `non-functional-requirements.md`와 `non-functional-requirements-detail.md`의 보안/데이터 항목 |
| 프로젝트/Space | `glossary.md`, `permissions.md`, `functional-requirements.md`와 `functional-requirements-detail.md`의 `FR-DASH-*`, `FR-PERM-*`, `FR-OWN-*` |
| 회의 생성/초대/ACL | `glossary.md`, `permissions.md`, `status-values.md`, `functional-requirements.md`와 `functional-requirements-detail.md`의 `FR-MREG-*`, `FR-ACL-*` |
| 회의방/LiveKit/WebRTC | `permissions.md`, `status-values.md`, `functional-requirements.md`와 `functional-requirements-detail.md`의 `FR-CALL-*`, `performance.md`의 외부 API 항목 |
| STT/Transcript/Speaker | `glossary.md`, `permissions.md`, `status-values.md`, `functional-requirements.md`와 `functional-requirements-detail.md`의 `FR-STT-*`, `non-functional-requirements.md`와 `non-functional-requirements-detail.md`의 `NFR-DATA-*` |
| Meeting AI | `permissions.md`, `performance.md`, `functional-requirements.md`와 `functional-requirements-detail.md`의 `FR-MBOT-*`, `non-functional-requirements.md`와 `non-functional-requirements-detail.md`의 `NFR-AI-*`, `NFR-AZ-*` |
| Project AI/RAG | `permissions.md`, `performance.md`, `functional-requirements.md`와 `functional-requirements-detail.md`의 `FR-PBOT-*`, `non-functional-requirements.md`와 `non-functional-requirements-detail.md`의 `NFR-AZ-*`, `NFR-DATA-*`, `NFR-COST-*` |
| 회의록/보고서 | `status-values.md`, `functional-requirements.md`와 `functional-requirements-detail.md`의 `FR-RPT-*`, `performance.md`의 AI/토큰 항목 |
| 태스크/칸반 | `status-values.md`, `functional-requirements.md`와 `functional-requirements-detail.md`의 `FR-TASK-*`, `FR-KAN-*` |
| 용어 설명/용어 사전 | `glossary.md`, `functional-requirements.md`와 `functional-requirements-detail.md`의 `FR-TERM-*`, `non-functional-requirements.md`와 `non-functional-requirements-detail.md`의 `NFR-COST-01`, `performance.md`의 용어/토큰 항목 |
| API 계약 변경 | `glossary.md`, `permissions.md`, `status-values.md`, `non-functional-requirements.md`와 `non-functional-requirements-detail.md`의 유지보수 항목 |
| 데이터 모델/마이그레이션 | `glossary.md`, `status-values.md`, `policies.md`, `non-functional-requirements.md`와 `non-functional-requirements-detail.md`의 데이터 항목 |

## Update Rule

Google Sheets 기준선이 바뀌면 먼저 이 디렉터리의 해당 Markdown을 갱신한다.
그 다음 `specs/<feature>/spec.md`, `plan.md`, `data-model.md`, `contracts/*`, `tasks.md`에 영향 여부를 반영한다.

요구사항 Markdown은 source of truth의 로컬 스냅샷이다. 구현자는 Google Sheets를 매번 전체 조회하지 말고 이 인덱스를 통해 필요한 범위만 읽는다.
