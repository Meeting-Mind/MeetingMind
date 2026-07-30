# Glossary

모든 문서와 구현의 표준 용어는 이 문서를 기준으로 한다.
Google Sheets 용어집 시트의 전체 컬럼을 보존한 로컬 스냅샷이다.

| 분류 | 표준 용어 | 영문/코드명 | 정의 | 포함/관계 | DB/API 반영 | 사용하지 말 것 | 관련 요구사항 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 계정 | 사용자 | User | 서비스에 가입해 인증 가능한 계정 주체. 이메일 로그인 또는 소셜 로그인으로 식별된다. | User는 여러 AuthIdentity를 가질 수 있고 여러 Space에 멤버로 참여할 수 있다. | users, /users, /me | 멤버/참여자와 혼용 금지 | FR-AUTH-01, FR-AUTH-13 |
| 계정 | 인증 수단 | AuthIdentity | 사용자의 로그인 방식을 나타내는 식별 정보. 이메일/비밀번호, Google OAuth 같은 provider 정보를 포함한다. | 한 User가 여러 AuthIdentity를 가질 수 있다. | auth_identities, /auth/* | User 자체와 동일시 금지 | FR-AUTH-04, FR-AUTH-05 |
| 계정 | BFF 세션 | BffSession | 브라우저의 불투명 세션 쿠키를 서버 측 사용자·Token Bundle에 연결하는 Web BFF 인증 단위. | Redis에 저장되며 유휴/절대 만료와 revoke 상태를 가진다. | Spring Session Redis, /auth/session, /auth/logout | AuthSession, 브라우저 탭 상태와 혼용 금지 | FR-AUTH-09~10, FR-AUTH-18 |
| 계정 | 인증 세션 | AuthSession | Auth Service가 기기별 단일 refresh family와 access JWT `sid` 폐기를 관리하는 사용자 로그인 단위. | User에 속하고 하나 이상의 BffSession/TokenBundle과 연결될 수 있으며 revoke event의 aggregate다. | auth_sessions, internal /auth/refresh, /auth/revoke | BFF 브라우저 세션 또는 사용자 전체 계정과 혼용 금지 | FR-AUTH-07~09, FR-AUTH-18 |
| 계정 | 토큰 번들 | TokenBundle | BFF가 서비스 간 호출을 위해 보관하는 암호화된 access/refresh token과 만료·scope·audience 묶음. | BffSession이 식별자로 참조하며 브라우저에는 노출하지 않는다. | BFF Token Vault | 세션 쿠키 또는 Auth DB hash와 혼용 금지 | FR-AUTH-07~08, NFR-DATA-05 |
| 계정 | 액세스 토큰 | AccessToken | BFF가 내부 Resource Service 호출 인증에 사용하는 짧은 수명의 토큰. | RefreshToken으로 재발급되며 브라우저에는 노출하지 않는다. | internal Authorization header | BFF 세션과 동일시 금지 | POL-TOKEN-01 |
| 계정 | 리프레시 토큰 | RefreshToken | BFF가 Auth Service에서 AccessToken을 재발급받는 장기 토큰. | BFF는 암호문, Auth Service는 hash/revoke 상태를 저장한다. | TokenBundle, refreshTokenHash, revokedAt | 브라우저·로그·Auth Service DB에 원문 저장 금지 | NFR-SEC-02, POL-TOKEN-02 |
| 아키텍처 | 웹 BFF | WebBff | 브라우저 세션, CSRF, 내부 토큰, 프론트 전용 API 조합을 담당하고 내부 서비스로 요청을 전달하는 서버. | 브라우저의 유일한 API 진입점이며 업무 데이터의 원천 저장소가 아니다. | bff service, /api/v1/* | API Gateway, Auth Service와 혼용 금지 | FR-AUTH-06~10, NFR-SCAL-02 |
| 아키텍처 | 인증 서비스 | AuthService | 사용자 인증수단 검증, 내부 토큰 발급·회전·폐기와 공개키 제공을 담당하는 서비스. | User/AuthIdentity/AuthSession을 소유하고 Resource Service 업무 권한은 소유하지 않는다. | auth service, internal /auth/*, JWKS | Web BFF와 혼용 금지 | FR-AUTH-04~09, FR-AUTH-18 |
| 프로젝트 | 프로젝트 | Space | 회의, 멤버, 칸반, 프로젝트 AI, 문서를 묶는 협업 공간. 화면에서는 '프로젝트'로 표시한다. | Space는 SpaceMember, Meeting, TaskCard, ProjectDocument를 포함한다. | spaces, /spaces | Workspace/Organization 혼용 금지 | FR-DASH-01~05 |
| 프로젝트 | 프로젝트 멤버 | SpaceMember | 특정 프로젝트에 소속된 사용자와 그 프로젝트 역할의 연결. | User와 Space의 조인 엔티티. role은 OWNER/ADMIN/MEMBER 등. | space_members, /spaces/{spaceId}/members | User와 혼용 금지 | FR-DASH-02, FR-PERM-01 |
| 프로젝트 | 프로젝트 역할 | SpaceRole | 프로젝트 단위 권한 묶음. OWNER, ADMIN, MEMBER를 기본값으로 한다. | 회의 ACL보다 상위 계층에서 먼저 평가된다. | space_members.role | Permission과 혼용 금지 | FR-AUTH-15, NFR-AZ-06 |
| 프로젝트 | 오너 | Owner | 프로젝트의 최상위 책임 역할. 프로젝트 삭제, 멤버 역할 변경, 오너 이양, 회의 ACL override 권한을 가진다. | SpaceRole의 한 값. '회의 오너'라는 표현은 쓰지 않고 회의 단위는 Host로 구분한다. | role=OWNER 또는 ownerUserId 정책 중 하나로 확정 | 생성자/관리자/호스트와 혼용 금지 | FR-DASH-01, FR-DASH-05, FR-OWN-01~03 |
| 프로젝트 | 관리자 | Admin | 오너가 위임한 프로젝트 운영 역할. 멤버/회의 관리 권한을 가질 수 있으나 오너 이양·프로젝트 삭제 같은 최상위 행위는 정책으로 제한한다. | SpaceRole의 한 값. 서비스 전체 운영자나 시스템 관리자가 아니라 특정 Space 안의 프로젝트 관리자다. owner/admin override 범위는 권한 매트릭스에 명시한다. | role=ADMIN | 오너/서비스 운영자/시스템 관리자와 혼용 금지 | FR-PERM-03, FR-ACL-05 |
| 프로젝트 | 일반 멤버 | Member | 프로젝트에 소속되어 기본 기능을 사용할 수 있는 역할. | 회의 접근은 MeetingParticipant ACL이 있어야 가능하다. | role=MEMBER | 회의 참여자와 혼용 금지 | FR-PERM-01~05 |
| 프로젝트 | 초대 | Invitation | 프로젝트 또는 회의에 사용자를 참여시키기 위한 초대 기록. | 초대 수락 시 SpaceMember 또는 MeetingParticipant가 생성된다. | invitations, /invitations | 알림(Notification)과 혼용 금지 | FR-PERM-02, FR-PERM-05, FR-MREG-03 |
| 회의 | 회의 | Meeting | 프로젝트 안에서 일정, 참여자, 전사, 회의록, AI 질의응답을 가지는 업무 회의 단위. | Space에 속하며 MeetingParticipant, TranscriptSegment, MeetingReport를 포함한다. | meetings, /spaces/{spaceId}/meetings | 회의방/세션/회의록과 혼용 금지 | FR-MREG-01~07 |
| 회의 | 회의 일정 | MeetingSchedule | 회의의 예정 일시, 캘린더 표시 정보, 알림 기준이 되는 일정 정보. | Meeting의 속성 또는 별도 엔티티로 구현 가능. | meeting_schedules 또는 meetings.scheduled_at | 캘린더 이벤트와 혼용 시 주의 | FR-CAL-01~05 |
| 회의 | 회의 참여자 | MeetingParticipant | 특정 회의에 접근 권한을 가진 사람 또는 초대 대상. 프로젝트 멤버일 수 있으나 같은 개념은 아니다. | Meeting과 User/SpaceMember를 연결하며 meeting role을 가진다. | meeting_participants, /meetings/{meetingId}/participants | SpaceMember와 혼용 금지 | FR-MREG-02, FR-MREG-07, FR-ACL-01 |
| 회의 | 회의 역할 | MeetingRole | 회의 단위 권한 묶음. VIEWER, EDITOR, HOST를 기본값으로 한다. | MeetingParticipant.role로 관리한다. | meeting_participants.role | SpaceRole과 혼용 금지 | FR-ACL-04, POL-AUTHZ-03 |
| 회의 | 호스트 | Host | 회의 단위 최고 운영 역할. 회의 시작/종료, 참여자 관리, 기본 삭제 권한을 가진다. | MeetingRole의 최상위 값. 프로젝트 Owner와 다르다. | role=HOST | 오너와 혼용 금지 | FR-ACL-04, FR-CALL-06 |
| 회의 | 편집자 | Editor | 회의 전사, 발화자명, 회의록 등 회의 산출물을 수정할 수 있는 회의 역할. | Viewer 권한을 포함한다. | role=EDITOR | 프로젝트 Admin과 혼용 금지 | FR-ACL-04, FR-STT-04 |
| 회의 | 조회자 | Viewer | 회의 내용을 조회할 수 있으나 수정/삭제는 할 수 없는 회의 역할. | MeetingRole의 최소 권한. | role=VIEWER | 비로그인 공개 사용자로 오해 금지 | FR-ACL-04 |
| 회의 | 회의 게스트 | MeetingGuest | 특정 회의에 명시적으로 초대되어 해당 회의에만 접근 권한을 가진 사용자. SpaceMember가 아닐 수 있다. | MeetingParticipant로 관리되며 Space 전체 권한은 갖지 않는다. | meeting_participants, invitations | SpaceMember/일반 멤버와 혼용 금지 | FR-MREG-03, FR-ACL-01 |
| 회의 | 회의방 | MeetingRoom | 실시간 오디오/비디오 연결이 이루어지는 LiveKit/WebRTC 공간. | Meeting의 실시간 연결 표현. 회의 데이터 자체는 Meeting이다. | meeting_rooms 또는 providerRoomName | Meeting과 혼용 금지 | FR-CALL-01~06 |
| 회의 | 회의 상태 | MeetingStatus | 회의의 진행 상태. 예정, 진행 중, 종료, 취소 등을 표현한다. | Meeting에 속한다. | meetings.status | TaskStatus와 혼용 금지 | FR-MREG-06 |
| 음성/STT | 발화자 | MeetingSpeaker | 전사에서 발화 단위를 말한 사람으로 식별된 주체. User와 1:1이 아닐 수 있다. | TranscriptSegment가 speaker를 참조한다. | meeting_speakers, speakerId | Participant/User와 무조건 동일시 금지 | FR-STT-02, FR-STT-04 |
| 음성/STT | 다이알로그 | Dialogue | 화면에 표시되는 발화 흐름의 사용자 친화적 표현. | 여러 TranscriptSegment로 구성될 수 있다. | UI 용어. 저장 모델은 TranscriptSegment 권장 | Transcript와 혼용 주의 | FR-STT-03, FR-STT-05 |
| 음성/STT | 전사 | Transcript | 회의 음성에서 생성된 텍스트 전체 산출물. | Meeting에 속하며 여러 TranscriptSegment로 구성된다. | transcripts 또는 meetings.transcript_status | 회의록/Summary와 혼용 금지 | FR-STT-01, NFR-DATA-04 |
| 음성/STT | 전사 세그먼트 | TranscriptSegment | 발화자, 시작/종료 시각, 텍스트를 가진 전사의 원본 단위. | EmbeddingChunk의 원본 출처가 된다. | transcript_segments | EmbeddingChunk와 혼용 금지 | FR-STT-05, NFR-DATA-01 |
| AI/RAG | 임베딩 청크 | EmbeddingChunk | 검색/RAG 효율을 위해 여러 발화를 묶어 만든 벡터화 단위. | TranscriptSegment를 참조하고 scope/source metadata를 가진다. | embedding_chunks | 원본 전사로 취급 금지 | NFR-COST-02, NFR-DATA-01 |
| AI/RAG | 저장 그래프 엣지 | KnowledgeGraphEdge | Knowledge 화면에서 유지할 Space 단위 무방향 보조 연결. | 현재 권한 범위에서 양 끝 노드가 모두 보일 때 그래프 응답에 합친다. | knowledge_graph_edges | 업무 인과관계나 근거 출처로 해석 금지 | FR-KNOW-02, NFR-AI-02 |
| AI/RAG | 프로젝트 AI | ProjectAI | 프로젝트 내 접근 가능한 공식지식과 회의기록을 바탕으로 답변하는 AI 기능. | Space scope에서 동작하되 권한 필터를 먼저 적용한다. | /spaces/{spaceId}/ai/messages | MeetingAI와 혼용 금지 | FR-PBOT-01~05, NFR-AZ-04 |
| AI/RAG | 회의 AI | MeetingAI | 단일 회의 범위의 전사, 결정, 회의록을 바탕으로 답변하는 AI 기능. | Meeting scope에서만 동작한다. | /meetings/{meetingId}/ai/messages | ProjectAI와 혼용 금지 | FR-MBOT-01~04 |
| AI/RAG | 검색 범위 | SearchScope | AI 또는 검색이 참조할 수 있는 데이터 범위. PROJECT, MEETING 등으로 분리한다. | 권한 필터와 함께 평가한다. | scope, sourceScope | 프롬프트 문구만으로 제한했다고 표현 금지 | NFR-AZ-01~04 |
| AI/RAG | 출처 | SourceReference | AI 응답이나 청크가 근거로 삼은 회의, 시간, 발화자, 문서 위치 정보. | EmbeddingChunk와 AI 응답에 포함한다. | response.sources, source_ids, chunk_source_segments | 단순 URL과 혼용 금지. 별도 다형 FK 테이블로 저장하지 않는다. | FR-PBOT-03, FR-MBOT-03, NFR-AI-02 |
| 회의록 | 회의록 | MeetingReport | 전사 기반으로 생성·편집·확정되는 공식 회의 기록 문서. | Meeting에 속하며 버전과 확정 상태를 가진다. | meeting_reports, /meetings/{meetingId}/reports | 요약/Summary와 혼용 금지 | FR-RPT-01~07 |
| 회의록 | 요약 | Summary | 전사 또는 회의록 일부를 짧게 압축한 내용. | MeetingReport의 한 섹션 또는 AI 응답 일부가 될 수 있다. | summary 필드 | 회의록 전체와 혼용 금지 | FR-RPT-01 |
| 회의록 | 결정사항 | Decision | 회의 중 합의되거나 확정된 사항. | MeetingReport에 포함되며 SourceReference를 가진다. | decisions | 의견/논의사항과 혼용 금지 | FR-RPT-01, FR-MBOT-03 |
| 회의록 | 후속 작업 | ActionItem | 회의에서 도출된 실행 과제. 담당자와 마감일을 가질 수 있다. | TaskCandidate 또는 TaskCard로 전환될 수 있다. | action_items | TaskCard와 무조건 동일시 금지 | FR-RPT-01, FR-TASK-01 |
| 태스크 | 태스크 후보 | TaskCandidate | AI가 회의록/전사에서 추출했지만 사용자가 아직 확정하지 않은 작업 후보. | 확정 후 TaskCard로 등록된다. | task_candidates | TaskCard와 혼용 금지 | FR-TASK-01~02 |
| 태스크 | 태스크 카드 | TaskCard | 칸반 보드에서 관리되는 확정된 작업 단위. | Space 또는 Meeting에서 생성될 수 있고 담당자/마감일/상태를 가진다. | task_cards, /spaces/{spaceId}/tasks | ActionItem과 혼용 시 전환 규칙 명시 | FR-KAN-01~08, FR-TASK-03 |
| 태스크 | 담당자 | Assignee | TaskCard 또는 ActionItem을 수행할 책임자로 지정된 사용자. | 보통 SpaceMember/User를 참조한다. | assigneeId | Owner와 혼용 금지 | FR-KAN-05, FR-TASK-01 |
| 용어 | 용어 사전 | DomainDictionary | 등록 용어와 설명을 저장하고 자막/AI 설명에 우선 사용하는 사전. | Space 또는 전역 범위로 둘 수 있다. | domain_terms, /domain-terms | 유비쿼터스 랭귀지 문서와 혼용 금지 | FR-TERM-01~05, NFR-COST-01 |
| 용어 | 등록 용어 | DomainTerm | 용어 사전에 저장된 단어와 정의. Space가 직접 등록하며 같은 용어가 공용 사전에 있어도 Space 정의를 우선한다. | DomainDictionary에 속한다. | domain_terms | 미등록 용어, SharedDomainTerm과 혼용 금지 | FR-TERM-02, FR-TERM-05 |
| 용어 | 공용 용어 | SharedDomainTerm | 관리자가 분야별로 미리 등록해 모든 Space에 제공하는 전역 기본 용어와 정의. | GlossaryCategory에 속하며 Space를 소유자로 갖지 않는다. | shared_domain_terms | DomainTerm과 혼용 금지. Space가 등록한 용어로 취급하지 않는다. | FR-TERM-02, NFR-COST-01 |
| 용어 | 용어 분야 | GlossaryCategory | 공용 용어를 업무 분야 기준으로 묶은 분류. IT/소프트웨어, 금융, 의료처럼 산업이나 직무 단위로 나눈다. | SharedDomainTerm을 포함하고 Space 구독의 단위가 된다. | glossary_categories | 지식 폴더(KnowledgeFolder), 태그와 혼용 금지 | FR-TERM-02, FR-TERM-04 |
| 용어 | 분야 구독 | SpaceGlossaryCategory | Space가 어떤 용어 분야를 제공받을지 정한 설정. 구독하지 않은 분야의 용어는 조회 단계에서 제외한다. | Space와 GlossaryCategory의 조인 엔티티. 행이 없으면 해당 분야를 구독 중으로 본다. | space_glossary_categories | 개인 설정으로 오해 금지. Space 단위로만 적용한다. | FR-TERM-04, NFR-AZ-01 |
| 용어 | 사용자 정의 용어 분야 | SpaceCustomGlossaryCategory | Space 생성 시 `기타`로 직접 입력한 업무 분야명. 전역 공용 용어 분야를 새로 만들지 않으며 해당 Space의 분류 정보로만 저장한다. | Space에 속하며 한 Space 안에서 대소문자 무시 중복을 허용하지 않는다. | space_custom_glossary_categories | GlossaryCategory 또는 공용 카탈로그와 혼용 금지 | FR-DASH-01, FR-TERM-04 |
| 알림 | 알림 | Notification | 회의 초대, 일정 시작, 권한 변경 같은 이벤트를 사용자에게 전달하는 메시지. | User 또는 SpaceMember를 대상으로 한다. | notifications | Invitation과 혼용 금지 | FR-CAL-05, FR-MREG-03 |
| 감사 | 감사 로그 | AuditLog | 권한 부여/회수, 오너 이양, 데이터 삭제 등 주요 행위의 추적 기록. | actor, target, before/after, occurredAt을 가진다. | audit_logs | 일반 앱 로그와 혼용 금지 | FR-ACL-06, NFR-LOG-02 |
| 데이터 | 보존 정책 | RetentionPolicy | 음성 원본, STT 원문, 삭제 데이터의 보관 기간과 삭제 기준. | Space 또는 Meeting 단위 정책값으로 적용 가능. | retention_policy, retentionUntil | 백업 정책과 혼용 금지 | POL-RETAIN-01~02 |
| 시스템 | 후보 | Candidate | AI가 제안했지만 사용자가 아직 확정하지 않은 임시 산출물 상태. | MeetingReportCandidate, TaskCandidate 등에 사용. | status=CANDIDATE | Draft와 혼용 금지 | FR-RPT-02, FR-TASK-02 |
