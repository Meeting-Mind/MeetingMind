# Non-Functional Requirements

이 문서는 비기능 요구사항 ID와 핵심 요건을 빠르게 찾기 위한 요약 카탈로그다.
전체 우선순위 요구사항의 상세 기준, 측정 방법, 적용/예외 조건, 검증 주기는 `non-functional-requirements-detail.md`를 함께 확인한다.

## 보안 (Security)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-SEC-01 | 비밀번호는 단방향 해시로 저장한다 | P0 |
| NFR-SEC-02 | refresh 토큰 원문을 저장하지 않는다 | P0 |
| NFR-SEC-03 | 소셜 ID token을 서버에서 검증한다 | P0 |
| NFR-SEC-04 | 통신 구간을 암호화한다 | P0 |
| NFR-SEC-05 | secret은 환경변수/.env로 관리한다 | P0 |
| NFR-SEC-06 | 모든 API 입력을 서버측에서 검증한다 | P0 |
| NFR-SEC-07 | 에러 응답에 내부정보를 노출하지 않는다 | P1 |
| NFR-SEC-08 | CSRF/CORS를 통제한다 | P1 |
## 권한 / 접근 제어 (Authorization)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-AZ-01 | 권한 필터를 검색·컨텍스트 조립 이전에 적용한다 | P0 |
| NFR-AZ-02 | 권한 통과 데이터만 AI 컨텍스트에 포함한다 | P0 |
| NFR-AZ-03 | 회의 권한을 Space보다 좁게 적용한다 | P0 |
| NFR-AZ-04 | Meeting AI/Project AI 검색범위를 분리한다 | P0 |
| NFR-AZ-05 | 접근 제어는 default-deny 화이트리스트로 동작한다 | P0 |
| NFR-AZ-06 | 권한은 프로젝트(RBAC)·회의(ACL) 2계층으로 분리 평가한다 | P0 |
| NFR-AZ-07 | 회의 권한은 조회/수정/삭제를 role에 매핑해 관리한다 | P1 |
## 성능 (Performance)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-PERF-01 | API 응답시간 목표를 만족한다 | P1 |
| NFR-PERF-02 | 챗봇/AI 응답을 목표시간 내 반환한다 | P1 |
| NFR-PERF-03 | STT 자막을 저지연으로 제공한다 | P1 |
| NFR-PERF-04 | 화상회의를 저지연·안정 스트리밍한다 | P1 |
| NFR-PERF-05 | 목록/캘린더 초기 로딩 시간을 관리한다 | P2 |
## 비용 / 효율 (Cost)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-COST-01 | 등록 용어는 LLM을 호출하지 않는다 | P1 |
| NFR-COST-02 | 짧은 발화는 묶어 임베딩한다 | P1 |
| NFR-COST-03 | 불필요한 LLM 호출을 최소화한다 | P2 |
## 확장성 / 가용성 (Scalability & Availability)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-SCAL-01 | 다중 프로젝트·동시 회의를 지원한다 | P1 |
| NFR-SCAL-02 | 수평 확장 가능한 구조를 유지한다 | P2 |
| NFR-AVAIL-01 | 서비스 가용성 목표를 만족한다 | P2 |
| NFR-AVAIL-02 | 외부 API 실패 시 graceful degradation한다 | P1 |
## 사용성 / 접근성 (Usability)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-UX-01 | 업무형 협업 도구의 정보 밀도를 유지한다 | P1 |
| NFR-UX-02 | 주요 해상도에 반응형으로 대응한다 | P2 |
| NFR-UX-03 | 로딩/에러/빈 상태 피드백을 제공한다 | P1 |
| NFR-UX-04 | 접근성을 고려한다 | P2 |
| NFR-UX-05 | 한국어 업무형 톤을 유지한다 | P1 |
## 신뢰성 / 유지보수 (Reliability & Maintainability)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-REL-01 | 외부 호출에 타임아웃·재시도를 적용한다 | P1 |
| NFR-REL-02 | 데이터 유실을 방지한다 | P1 |
| NFR-MNT-01 | API 계약을 문서화·표준화한다 | P1 |
| NFR-MNT-02 | mock/실데이터 경계를 유지한다 | P1 |
| NFR-MNT-03 | mock/실 API 응답 shape를 일관 유지한다 | P1 |
## 호환성 (Compatibility)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-CMP-01 | 최신 브라우저에서 WebRTC가 동작한다 | P1 |
## 데이터 / 무결성 (Data)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-DATA-01 | 원본과 임베딩 청크를 분리한다 | P1 |
| NFR-DATA-02 | 청크 출처 메타데이터를 유지한다 | P1 |
| NFR-DATA-03 | 음성 원본을 기본 장기 보관하지 않는다 | P0 |
| NFR-DATA-04 | STT 보존정책을 적용한다 | P1 |
| NFR-DATA-05 | 토큰 저장 위치를 규정한다 | P1 |
## AI 품질 / 안전 (AI Safety)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-AI-01 | 컨텍스트 기반으로만 응답한다 | P0 |
| NFR-AI-02 | 응답에 출처를 제공한다 | P1 |
| NFR-AI-03 | 컨텍스트 밖 내용을 응답하지 않는다 | P0 |
| NFR-AI-04 | 한국어로 간결하게 응답한다 | P1 |
## 로깅 / 감사 · 컴플라이언스 (Audit & Compliance)

| ID | 요건 | Priority |
| --- | --- | --- |
| NFR-LOG-01 | 주요 이벤트를 로깅한다 | P2 |
| NFR-LOG-02 | 권한 변경을 감사 추적한다 | P2 |
| NFR-COMP-01 | 개인정보를 최소 수집한다 | P1 |
| NFR-COMP-02 | 삭제/탈퇴 요청에 대응한다 | P2 |
