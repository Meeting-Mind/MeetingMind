# Policies

Google Sheets 정책(Policy) 시트의 전체 컬럼을 보존한 로컬 스냅샷이다.

| 정책ID | 분류 | 정책값 | 근거·비고 | 관련 요구사항ID |
| --- | --- | --- | --- | --- |
| POL-PW-01 | 비밀번호 | 최소 8자, 영대소문자·숫자·특수문자 중 3종 이상 조합 | 계정 탈취 방지 기본 기준 | FR-AUTH-03 |
| POL-PW-02 | 비밀번호 | 최근 3회 재사용 금지, 강제 만료주기 미적용(권장) | 보안·UX 균형 | FR-AUTH-03, FR-AUTH-12 |
| POL-TOKEN-01 | 인증 토큰 | 서비스별 audience의 RS256 access JWT, 만료 10분, 브라우저 미노출 | Auth Service 장애 격리와 탈취 피해 제한의 균형. `sid` revoke event와 Resource Service 로컬 denylist로 로그아웃 차단을 보완 | FR-AUTH-07, FR-AUTH-09, FR-AUTH-16 |
| POL-TOKEN-02 | 인증 토큰 | refresh 만료 14일, 1회용 회전, 재사용 시 해당 AuthSession family 전체 폐기, 동시 refresh grace 없음 | BFF single-flight를 정상 동시성 경계로 사용한다. BFF는 복호화 가능한 암호문, Auth Service는 hash/lineage/revoke 상태만 저장 | FR-AUTH-07, FR-AUTH-08 |
| POL-SESSION-01 | 세션 | 브라우저는 Secure/HttpOnly/SameSite 세션 쿠키만 보유하고 access/refresh 원문은 보유하지 않음 | XSS 토큰 탈취 방지와 BFF 인증 경계 | FR-AUTH-10, NFR-DATA-05, NFR-SEC-02 |
| POL-SESSION-02 | 세션 | 기본 유휴 60분/절대 12시간, Remember me는 7일 sliding 유휴/14일 절대 만료 | 기업 보안과 장시간 협업 UX 균형. sliding 갱신도 최초 로그인 후 14일 절대 상한을 연장하지 않음 | FR-AUTH-09, FR-AUTH-10, FR-AUTH-18 |
| POL-SESSION-03 | 세션 | 모든 기기 로그아웃은 최근 10분 이내 인증 또는 local/Google 재인증을 요구 | 탈취된 BFF 세션이 정상 기기의 세션까지 임의 폐기하는 것을 방지 | FR-AUTH-18 |
| POL-PW-03 | 비밀번호 | 재설정 token은 hash만 저장하는 1회성 15분 token이며 계정당 시간당 3회, IP당 시간당 10회로 제한 | 계정 enumeration과 대량 발송을 막고 token 원문 보관을 금지 | FR-AUTH-11 |
| POL-PW-04 | 비밀번호 | 변경 성공 시 현재 세션을 포함한 모든 AuthSession을 폐기하고 Google-only 계정은 local credential 추가 전 변경을 거부 | 탈취된 세션의 유지 위험을 줄이고 provider credential 경계를 명확히 한다 | FR-AUTH-12 |
| POL-PROFILE-01 | 프로필 | 프로필 사진은 BFF multipart upload 후 opaque object key로 저장하며 JPEG/PNG/WebP, 매직 바이트 검증, 최대 5 MiB만 허용 | browser URL 임의 주입과 content-type 위조를 막고 storage provider를 교체 가능하게 한다 | FR-AUTH-13 |
| POL-WITHDRAW-01 | 계정 탈퇴 | 단독 활성 Space OWNER는 이양 또는 Space 삭제 전 탈퇴 불가, 계정·세션 즉시 비활성화, 30일 뒤 Core 표시 이름·사진 익명화 | 업무/감사 기록의 referential integrity와 개인정보 최소화를 함께 보장 | FR-AUTH-17 |
| POL-INTERNAL-01 | 서비스 인증 | BFF/Auth/Resource 내부 호출은 mTLS SPIFFE workload identity, NetworkPolicy와 목적지 allowlist를 함께 적용 | shared client secret 없이 workload 단위 인증·암호화·최소 권한 적용 | NFR-SEC-04, NFR-AZ-05 |
| POL-DEPLOY-01 | 배포 | AWS EKS 단일 리전 Multi-AZ, 점진적 MSA 전환 | 수평 확장과 서비스 장애 격리 | NFR-SCAL-02, NFR-AVAIL-01 |
| POL-REALTIME-01 | 실시간 회의 | LiveKit Cloud 사용 | 미디어 평면을 애플리케이션 EKS 장애 경계와 분리 | NFR-AVAIL-02, NFR-PERF-04 |
| POL-AUTHZ-01 | 권한 | 접근 제어 default-deny(화이트리스트) | 원칙5, 보안 | NFR-AZ-05, FR-ACL-03 |
| POL-AUTHZ-02 | 권한 | 회의 삭제 권한 기본 OWNER/HOST 전용, ADMIN 삭제는 명시적 예외 정책이 있을 때만 허용 | 데이터 유실 방지 | FR-ACL-07 |
| POL-AUTHZ-03 | 권한 | 회의별 role 위계 VIEWER < EDITOR < HOST | 권한 매핑 단순화 | FR-ACL-04 |
| POL-RETAIN-01 | 데이터 보존 | 음성 원본 장기 보관 안 함(종료 후 단기 폐기) | 민감정보 보호·비용 | NFR-DATA-03 |
| POL-RETAIN-02 | 데이터 보존 | STT 원문 보존 7/30일/영구 선택(기본 30일) | retentionPolicy | NFR-DATA-04, FR-MREG-06 |
| POL-PROJ-01 | 프로젝트 | 삭제 시 soft delete + N일 유예 (N 확정 필요) | 복구 가능성 | FR-DASH-05 |
| POL-UPLOAD-01 | 파일 업로드 | 회의 채팅 MVP는 TXT, Markdown, PDF, PNG, JPEG만 허용하고 첨부당 기본 10 MiB, 메시지당 최대 10개를 서버에서 검증 | 텍스트 추출·RAG와 이미지 확장 범위를 분리하고 과도한 업로드를 차단 | FR-CALL-01, FR-MBOT-01, NFR-SEC-06, NFR-DATA-01 |
