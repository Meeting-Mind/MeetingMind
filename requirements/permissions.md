# Permission Matrix

권한 판단은 프로젝트 역할 `SpaceRole`과 회의 단위 접근 `MeetingParticipant`를 분리해서 적용한다.

## Role Definitions

| 역할 | 정의 |
| --- | --- |
| 오너 | 프로젝트의 최상위 책임 역할. 프로젝트 생성자이며 멤버/회의/보존 정책/회의 ACL override 권한을 가진다. |
| 관리자 | 오너가 위임한 프로젝트 운영 역할. 멤버/회의 관리 권한을 가진다. |
| 일반 멤버 | 프로젝트에 소속되어 기본 기능을 사용할 수 있는 역할. 회의 접근은 MeetingParticipant ACL이 있어야 가능하다. |
| 회의 게스트 | 특정 회의에 명시적으로 초대되어 해당 회의에만 접근 가능한 사용자. Space 전체 권한은 갖지 않는다. |

## Resource Permissions

| 기능 / 리소스 | 오너 | 관리자 | 일반 멤버 | 회의 게스트 |
| --- | --- | --- | --- | --- |
| Space 생성 | O | - | - | - |
| Space 초대 링크 발급 | O | O | X | X |
| 회의 참가 URL/코드 공유 | O | O | X | X |
| 회의 참가 신청 승인/거절 | O | O | X | X |
| 초대 수락 후 멤버 가입 | O | O | O | X |
| 회의 생성 | O | O | X | X |
| 회의 참여자 지정/변경 | O | O | X | X |
| 회의 입장 및 실시간 음성 전달 | O | O | O, 회의 권한 필요 | O, 해당 회의만 |
| 실시간 자막 열람 | O | O | O, 회의 권한 필요 | O, 해당 회의만 |
| 도메인 용어 사전 조회 | O | O | O | O, 해당 회의만 |
| 해당 회의 STT 원문 열람 | O | O | O, 회의 권한 필요 | O, 해당 회의만 |
| 해당 회의 STT 원문 다운로드 | O | O | O, 회의 권한 필요 | O, 해당 회의만 |
| STT 보관 정책 설정 | O | X | X | X |
| 해당 회의 AI 보고서 열람 | O | O | O, 회의 권한 필요 | O, 해당 회의만 |
| 해당 회의 AI 보고서 편집 | O | O | O, EDITOR/HOST 필요 | O, 해당 회의의 EDITOR/HOST만 |
| 해당 회의 AI 태스크 후보 추출 | O | O | O, EDITOR/HOST 필요 | O, 해당 회의의 EDITOR/HOST만 |
| 해당 회의 AI 태스크 후보 조회 | O | O | O, 회의 권한 필요 | O, 해당 회의만 |
| 태스크 후보의 프로젝트 카드 확정 | O | O | O, EDITOR/HOST 및 SpaceMember 필요 | X |
| Meeting AI 질의 | O | O | O, 회의 권한 필요 | O, 해당 회의만 |
| Project Knowledge 열람 | O | O | O | X |
| Project Knowledge 수정 | O | O | X | X |
| Project AI 질의 | O | O | O, 접근 권한 내 | X |

## Required Rules

- Project AI는 사용자가 참여 권한을 가진 회의만 검색 대상에 포함한다.
- 회의 게스트는 특정 회의의 `MeetingParticipant`로 등록된 사용자이며, 지정된 회의 밖의 STT, AI 보고서, Meeting AI, 회의 파일, 대화 내용에는 접근할 수 없다.
- `MeetingParticipant`는 특정 회의 접근권만 부여하며 프로젝트 전체 접근권을 만들지 않는다. 프로젝트 전체 접근권은 프로젝트 총괄이 `SpaceMember` 또는 Space invitation으로 명시 인가한 사용자에게만 부여한다.
- 사용자-facing 회의 참여 흐름은 회의 URL 또는 참가 코드로 `MeetingJoinRequest`를 만들고 active `HOST`가 승인하는 방식이다. Space `OWNER`/`ADMIN`은 회의 ACL 관리 override로 승인/거절할 수 있다.
- 참가 신청 승인 전에는 회의 접근권이 없으며, 승인 시 `VIEWER` MeetingParticipant만 생성한다. 신청자가 SpaceMember가 아니면 `participantType=guest`이며 SpaceMember를 만들지 않는다.
- MeetingParticipant 직접 추가는 OWNER/ADMIN/active HOST의 운영상 ACL 조정에만 사용하고 일반적인 신규 참여 흐름으로 노출하지 않는다.
- Project Knowledge는 `SpaceMember`인 오너/관리자/일반 멤버가 조회할 수 있으며, 회의 게스트는 기본 접근할 수 없다.
- 회의 참여자로 지정되는 즉시 해당 회의에 대한 STT, AI 보고서, Meeting AI 접근 권한이 부여된다.
- MeetingParticipant 권한이 해제되면 해당 회의 데이터와 AI 컨텍스트 접근도 즉시 차단된다.
- SpaceMember 제거는 프로젝트 전체 접근권을 제거한다. 기존 MeetingParticipant가 남아 있으면 해당 회의 범위 접근은 유지되고, 회의 접근 차단은 MeetingParticipant revoke로 처리한다.
- 회의 산출물 열람은 VIEWER 이상이 가능하지만, AI 보고서 편집과 발화자 이름 수정은 EDITOR/HOST 또는 Space OWNER/ADMIN override 권한이 필요하다.
- AI 태스크 후보 추출은 회의 산출물 생성 작업이므로 `OWNER`/`ADMIN` 또는 해당 회의의 active `HOST`/`EDITOR`만 수행한다.
- 태스크 후보 조회는 active 회의 접근 권한으로 허용하지만, 프로젝트 TaskCard 확정은 active `SpaceMember`이면서 회의 편집 권한이 있는 사용자만 수행한다. 회의 게스트는 프로젝트 칸반에 카드를 만들 수 없다.
- HOST가 회의방에서 일시 퇴장해도 MeetingParticipant role과 accessStatus는 유지된다.
- HOST가 회의를 종료하면 Meeting status를 `ENDED`로 전환한다.
- 마지막 active HOST의 role 강등, `REVOKED` 전환, participant 제거는 거부한다. 마지막 HOST를 없애려면 다른 참여자를 먼저 HOST로 승격해야 한다.
- 회의 삭제 권한은 기본 `OWNER` 또는 해당 회의 `HOST` 전용이다. `ADMIN` 삭제는 기본 권한이 아니며 명시적 예외 정책이 있을 때만 허용한다.
- STT 보관 정책 설정은 오너 권한으로 제한한다.
- 모든 AI 기능은 사용자 권한을 검증한 후 데이터를 조회해야 한다.
- 권한이 없는 회의 데이터는 검색 결과, AI 컨텍스트, AI 응답에 포함하지 않는다.
