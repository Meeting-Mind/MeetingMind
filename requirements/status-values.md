# Status Values

상태 enum은 대상 entity 범위 안에서 해석한다. 같은 코드값이라도 대상이 다르면 별도 enum으로 취급한다.
Google Sheets 상태값 시트의 전체 컬럼을 보존한 로컬 스냅샷이다.

| 대상 | 상태명 | 코드값 | 정의 | 전이/사용 기준 | DB 필드 | 관련 요구사항 |
| --- | --- | --- | --- | --- | --- | --- |
| BffSession | 활성 | ACTIVE | 유휴·절대 만료 전이며 BFF가 인증 요청을 처리할 수 있는 서버 세션. | 로그인/가입 성공 직후 | Redis BffSession.status | FR-AUTH-06, FR-AUTH-10 |
| BffSession | 로그아웃 처리 중 | LOGOUT_PENDING | 브라우저 세션은 차단했고 Auth revoke 또는 비동기 정리를 재처리하는 상태. | downstream revoke 일시 실패 시 짧게 사용 | Redis BffSession.status 또는 revoke 작업 | FR-AUTH-09, FR-AUTH-18 |
| BffSession | 폐기 | REVOKED | 현재/모든 기기 로그아웃, 계정 비활성화 또는 보안 사건으로 더 이상 사용할 수 없는 상태. | 명시적 revoke 시 | Redis BffSession.status | FR-AUTH-09, FR-AUTH-18 |
| BffSession | 만료 | EXPIRED | 유휴 또는 절대 만료에 도달해 더 이상 사용할 수 없는 상태. | idleExpiresAt/absoluteExpiresAt 중 먼저 도달 시 | Redis BffSession.status | FR-AUTH-10, FR-AUTH-16 |
| AuthSession.revokeReason | 현재 로그아웃 | CURRENT_LOGOUT | 사용자가 현재 브라우저/기기 세션을 명시적으로 종료했다. | 현재 session revoke 시 | auth_sessions.revoke_reason | FR-AUTH-09 |
| AuthSession.revokeReason | 모든 기기 로그아웃 | ALL_DEVICE_LOGOUT | 최근 인증 또는 재인증 뒤 사용자 소유의 모든 AuthSession을 종료했다. | revoke-all 시 | auth_sessions.revoke_reason | FR-AUTH-18 |
| AuthSession.revokeReason | 사용자 비활성화 | USER_DISABLED | 계정 비활성화 또는 탈퇴로 세션을 종료했다. | User status 비활성 전이 시 | auth_sessions.revoke_reason | FR-AUTH-13 |
| AuthSession.revokeReason | Refresh 재사용 | REFRESH_REUSE | 이미 사용된 refresh credential이 다시 제시되어 해당 AuthSession family를 폐기했다. | reuse 탐지 트랜잭션 시 | auth_sessions.revoke_reason | FR-AUTH-08 |
| AuthSession.revokeReason | 관리자 폐기 | ADMIN_REVOKE | 승인된 운영자 보안 조치로 세션을 폐기했다. | 감사 가능한 관리자 명령 시 | auth_sessions.revoke_reason | FR-AUTH-09 |
| AuthSession.revokeReason | 만료 | EXPIRED | refresh 절대 만료에 도달해 세션을 종료했다. | AuthSession.expiresAt 도달 시 | auth_sessions.revoke_reason | FR-AUTH-10, FR-AUTH-16 |
| Meeting | 예정 | SCHEDULED | 회의 일정이 생성되었지만 아직 시작되지 않은 상태. | 생성 직후 기본 상태 | meetings.status | FR-MREG-06 |
| Meeting | 진행 중 | IN_PROGRESS | 실시간 회의방이 열리고 회의가 진행 중인 상태 | LiveKit/WebRTC 연결 시작 시 | meetings.status | FR-CALL-01, FR-MREG-06 |
| Meeting | 종료 | ENDED | 회의가 종료되어 전사/회의록 후처리 대상으로 전환된 상태. | 호스트 종료 또는 시스템 종료 처리 | meetings.status | FR-CALL-06 |
| Meeting | 취소 | CANCELED | 예정 회의가 진행 전 취소된 상태. | 일정 취소 시 | meetings.status | FR-CAL-04 |
| Transcript | 대기 | PENDING | 전사 작업이 아직 시작되지 않은 상태. | 음성 입력 또는 회의 종료 직후 | transcripts.status | FR-STT-01 |
| Transcript | 처리 중 | PROCESSING | STT 전사 또는 후처리가 진행 중인 상태. | STT 요청 시작 후 | transcripts.status | FR-STT-01 |
| Transcript | 완료 | COMPLETED | 전사 세그먼트가 저장되어 조회/검색 가능한 상태. | 모든 세그먼트 저장 완료 | transcripts.status | FR-STT-05 |
| Transcript | 실패 | FAILED | 전사 처리 실패. 재시도 또는 사용자 안내가 필요하다. | STT provider 오류 등 | transcripts.status | NFR-AVAIL-02 |
| MeetingReport | 후보 | CANDIDATE | AI가 생성했으나 사용자가 확정하지 않은 회의록 후보. | AI 생성 직후 | meeting_reports.status | FR-RPT-02 |
| MeetingReport | 초안 | DRAFT | 사용자가 편집 중인 회의록. | 후보 수락 또는 수동 작성 시작 | meeting_reports.status | FR-RPT-04~05 |
| MeetingReport | 확정 | CONFIRMED | 프로젝트 문서로 저장된 공식 회의록. | 사용자 확정 시 | meeting_reports.status | FR-RPT-03 |
| TaskCandidate | 후보 | CANDIDATE | AI가 추출했지만 아직 칸반에 등록되지 않은 태스크 후보. | 태스크 추출 직후 | task_candidates.status | FR-TASK-01~02 |
| TaskCandidate | 확정 | CONFIRMED | 사용자가 검토하고 TaskCard로 등록한 태스크 후보. | TaskCard 생성과 같은 전이에서 변경 | task_candidates.status | FR-TASK-02~03 |
| TaskCandidate | 제외 | DISMISSED | 사용자가 칸반 등록 대상에서 제외한 태스크 후보. | 후보 제외 시 | task_candidates.status | FR-TASK-02 |
| TaskCard | 할 일 | TODO | 아직 시작하지 않은 칸반 카드. | 카드 생성 기본 상태 | task_cards.status | FR-KAN-01~04 |
| TaskCard | 진행 중 | IN_PROGRESS | 현재 처리 중인 칸반 카드. | 드래그 또는 상태 변경 | task_cards.status | FR-KAN-04 |
| TaskCard | 완료 | DONE | 작업이 완료된 칸반 카드. | 드래그 또는 상태 변경 | task_cards.status | FR-KAN-04 |
| Invitation | 대기 | PENDING | 초대가 발송되었지만 응답 전인 상태. | 초대 생성 직후 | invitations.status | FR-PERM-02 |
| Invitation | 수락 | ACCEPTED | 초대 대상이 초대를 수락한 상태. | 수락 시 SpaceMember/MeetingParticipant 생성 | invitations.status | FR-PERM-05 |
| Invitation | 거절 | DECLINED | 초대 대상이 초대를 거절한 상태. | 거절 시 초대 무효 | invitations.status | FR-PERM-05 |
| Invitation | 만료 | EXPIRED | 초대 유효기간이 지나 더 이상 사용할 수 없는 상태. | 만료시간 도달 시 | invitations.status | FR-PERM-05 |
| MeetingJoinRequest | 대기 | PENDING | 사용자가 회의 URL 또는 참가 코드로 신청했지만 아직 검토되지 않은 상태. | 참가 신청 생성 직후 | meeting_join_requests.status | FR-MREG-02~03 |
| MeetingJoinRequest | 승인 | APPROVED | HOST 또는 OWNER/ADMIN override가 신청을 승인한 상태. | 승인과 함께 MeetingParticipant 생성 | meeting_join_requests.status | FR-MREG-02 |
| MeetingJoinRequest | 거절 | REJECTED | HOST 또는 OWNER/ADMIN override가 신청을 거절한 상태. | 거절 처리 후 | meeting_join_requests.status | FR-MREG-02 |
| MeetingParticipant | 활성 | ACTIVE | 사용자가 해당 회의에 접근 가능한 상태. | 참가 신청 승인 또는 수동 ACL 부여 시 기본 상태 | meeting_participants.access_status | FR-MREG-02, FR-MREG-07, FR-ACL-01 |
| MeetingParticipant | 회수 | REVOKED | 사용자의 해당 회의 접근이 회수된 상태. | 회의 권한을 명시적으로 회수할 때 전환 | meeting_participants.access_status | FR-ACL-02 |
