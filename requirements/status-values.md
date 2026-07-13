# Status Values

상태 enum은 대상 entity 범위 안에서 해석한다. 같은 코드값이라도 대상이 다르면 별도 enum으로 취급한다.
Google Sheets 상태값 시트의 전체 컬럼을 보존한 로컬 스냅샷이다.

| 대상 | 상태명 | 코드값 | 정의 | 전이/사용 기준 | DB 필드 | 관련 요구사항 |
| --- | --- | --- | --- | --- | --- | --- |
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
| MeetingParticipant | 활성 | ACTIVE | 사용자가 해당 회의에 접근 가능한 상태. | 참여자 추가 또는 회의 초대 수락 시 기본 상태 | meeting_participants.access_status | FR-MREG-07, FR-ACL-01 |
| MeetingParticipant | 회수 | REVOKED | 사용자의 해당 회의 접근이 회수된 상태. | 회의 권한 회수 또는 SpaceMember 제거 시 전환 | meeting_participants.access_status | FR-ACL-02, FR-PERM-04 |
