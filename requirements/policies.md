# Policies

Google Sheets 정책(Policy) 시트의 전체 컬럼을 보존한 로컬 스냅샷이다.

| 정책ID | 분류 | 정책값 | 근거·비고 | 관련 요구사항ID |
| --- | --- | --- | --- | --- |
| POL-PW-01 | 비밀번호 | 최소 8자, 영대소문자·숫자·특수문자 중 3종 이상 조합 | 계정 탈취 방지 기본 기준 | FR-AUTH-03 |
| POL-PW-02 | 비밀번호 | 최근 3회 재사용 금지, 강제 만료주기 미적용(권장) | 보안·UX 균형 | FR-AUTH-03, FR-AUTH-12 |
| POL-TOKEN-01 | 인증 토큰 | access 만료 1시간 | 세션 탈취 위험 최소화 | FR-AUTH-07 |
| POL-TOKEN-02 | 인증 토큰 | refresh 만료 14일, 회전(rotation) 적용 | 장기 세션 관리 | FR-AUTH-07, FR-AUTH-08 |
| POL-SESSION-01 | 세션 | 토큰 저장: FE sessionStorage, refresh 원문 서버 미저장 | XSS/유출 대응 | NFR-DATA-05, NFR-SEC-02 |
| POL-AUTHZ-01 | 권한 | 접근 제어 default-deny(화이트리스트) | 원칙5, 보안 | NFR-AZ-05, FR-ACL-03 |
| POL-AUTHZ-02 | 권한 | 회의 삭제 권한 기본 OWNER/HOST 전용, ADMIN 삭제는 명시적 예외 정책이 있을 때만 허용 | 데이터 유실 방지 | FR-ACL-07 |
| POL-AUTHZ-03 | 권한 | 회의별 role 위계 VIEWER < EDITOR < HOST | 권한 매핑 단순화 | FR-ACL-04 |
| POL-RETAIN-01 | 데이터 보존 | 음성 원본 장기 보관 안 함(종료 후 단기 폐기) | 민감정보 보호·비용 | NFR-DATA-03 |
| POL-RETAIN-02 | 데이터 보존 | STT 원문 보존 7/30일/영구 선택(기본 30일) | retentionPolicy | NFR-DATA-04, FR-MREG-06 |
| POL-PROJ-01 | 프로젝트 | 삭제 시 soft delete + N일 유예 (N 확정 필요) | 복구 가능성 | FR-DASH-05 |
| POL-UPLOAD-01 | 파일 업로드 | 허용 형식·최대 용량 (후속 확정) | STT 오디오 업로드용 | FR-STT 계열(후속) |
