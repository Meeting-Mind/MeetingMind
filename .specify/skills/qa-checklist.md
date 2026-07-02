이 문서는 MeetingMind 기능 구현 후 품질 점검 절차를 제공하기 위한 Markdown 문서이다.

# QA Checklist Skill

도구에 종속되지 않는 MeetingMind QA 절차다. 기능 구현 후 권한, AI 컨텍스트, API, UI, 검증 상태를 확인할 때만 읽는다.

## When To Use

- `specs/*/tasks.md` 작업을 완료했을 때
- 권한, AI 컨텍스트, 회의 데이터 보존 정책에 영향을 주는 변경을 했을 때
- 프론트엔드 화면 또는 API 계약을 변경했을 때

## Checklist

1. Constitution Check
   - Meeting AI와 Project AI의 검색 범위가 섞이지 않았는가?
   - RAG/AI 컨텍스트 전에 권한 필터가 적용되는가?
   - 근거 없는 AI 응답이 추정으로 작성되지 않는가?

2. API Check
   - request validation이 있는가?
   - 오류 상태 코드가 호출자가 처리할 수 있게 분리되어 있는가?
   - secret이 응답/로그에 노출되지 않는가?

3. Frontend Check
   - mock fallback과 실제 API 상태가 혼동되지 않는가?
   - 사용자가 권한 없는 회의 정보에 접근하는 UI 경로가 없는가?
   - 업무형 협업 도구에 맞는 밀도와 문구를 유지하는가?

4. Verification Check
   - 관련 빌드/테스트 명령을 실행했는가?
   - 실행하지 못한 검증은 이유를 남겼는가?
   - `specs/*/tasks.md` 상태가 실제 작업 상태와 일치하는가?
