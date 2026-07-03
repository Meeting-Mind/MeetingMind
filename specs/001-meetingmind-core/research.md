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

## Risks

- AI 컨텍스트가 권한 범위를 넘어가면 제품 신뢰가 깨진다.
- mock 데이터가 오래 남으면 실제 보안/데이터 모델 설계가 지연될 수 있다.
- Project AI가 공식 지식과 회의 기록을 구분하지 못하면 답변의 신뢰도가 떨어진다.
