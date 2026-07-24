export interface LinkItem {
  label: string;
  href: string;
}

export interface TranscriptRow {
  time: string;
  speaker: string;
  text: string;
}

export interface LabeledItem {
  title: string;
  meta: string;
}

export interface MeetingOverview {
  title: string;
  subtitle: string;
  status: string[];
}

export interface WorkspaceSpace {
  id: string;
  name: string;
  members: string;
  meetings: string;
  updatedAt: string;
  description: string;
  href: string;
}

export interface WorkspaceTodayMeeting {
  title: string;
  project: string;
  time: string;
  attendees: string;
  note: string;
  href: string;
}

export interface MeetingAccessMember {
  name: string;
  role: string;
  access: string;
  note: string;
}

export interface ProjectOverviewMeeting {
  id?: string;
  index: string;
  title: string;
  date: string;
  state: string;
  description?: string | null;
  scheduledAt?: ApiDateTime;
  scheduledEndAt?: ApiDateTime;
  durationMinutes?: number;
  myRole?: MeetingRole | null;
}

export type ApiDateTime = string;
export type ApiDate = string;

export type SpaceRole = "OWNER" | "ADMIN" | "MEMBER";
export type MeetingStatus = "SCHEDULED" | "IN_PROGRESS" | "ENDED" | "CANCELED";
export type MeetingRole = "HOST" | "EDITOR" | "VIEWER";
export type ParticipantType = "member" | "guest";
export type ParticipantAccessStatus = "ACTIVE" | "REVOKED";
export type TranscriptStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
export type MeetingReportStatus = "CANDIDATE" | "DRAFT" | "CONFIRMED";
export type TaskCardStatus = "TODO" | "IN_PROGRESS" | "IN_REVIEW" | "DONE";
export type TaskCardPriority = "LOW" | "MEDIUM" | "HIGH";
export type DomainTermStatus = "ACTIVE" | "ARCHIVED";
export type AiSourceType =
  | "transcript"
  | "meetingSummary"
  | "decision"
  | "actionItem"
  | "report"
  | "projectKnowledge"
  | "glossary";

export interface SpaceSummary {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  role: SpaceRole;
  meetingCount: number;
  updatedAt: ApiDateTime;
}

export interface SpaceListResponse {
  spaces: SpaceSummary[];
}

export interface DomainTerm {
  id: string;
  term: string;
  definition: string;
  status: DomainTermStatus;
  updatedAt: ApiDateTime;
}

export interface DomainTermListResponse {
  terms: DomainTerm[];
}

export interface CreateDomainTermRequest {
  term: string;
  definition: string;
}

export interface UpdateDomainTermRequest {
  term?: string;
  definition?: string;
  status?: DomainTermStatus;
}

export interface DomainTermMutationResponse {
  id: string;
  status: DomainTermStatus;
  updatedAt: ApiDateTime;
}

export interface DeleteDomainTermResponse {
  deleted: boolean;
}

export interface CreateSpaceRequest {
  name: string;
  description?: string | null;
}

export interface CreateSpaceResponse {
  id: string;
  name: string;
  description: string | null;
  role: SpaceRole;
  createdAt: ApiDateTime;
}

export interface UpdateSpaceRequest {
  name?: string;
  description?: string | null;
  imageUrl?: string | null;
}

export interface UpdateSpaceResponse {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  updatedAt: ApiDateTime;
}

export interface DeleteSpaceResponse {
  deleted: boolean;
}

export interface LeaveSpaceResponse {
  left: boolean;
}

export interface MeetingSummary {
  id: string;
  spaceId: string;
  title: string;
  description: string | null;
  scheduledAt: ApiDateTime;
  scheduledEndAt: ApiDateTime;
  status: MeetingStatus;
  myRole: MeetingRole | null;
}

export interface MeetingListResponse {
  meetings: MeetingSummary[];
}

export interface MeetingDetailResponse {
  id: string;
  spaceId: string;
  title: string;
  description: string | null;
  status: MeetingStatus;
  scheduledAt: ApiDateTime;
  scheduledEndAt: ApiDateTime;
  startedAt: ApiDateTime | null;
  endedAt: ApiDateTime | null;
  myRole: MeetingRole | null;
  participants: MeetingParticipantSummary[];
}

export interface UpdateMeetingRequest {
  title?: string;
  description?: string;
  scheduledAt?: ApiDateTime;
  scheduledEndAt?: ApiDateTime;
  status?: MeetingStatus;
}

export interface UpdateMeetingResponse {
  id: string;
  title: string;
  description: string | null;
  scheduledAt: ApiDateTime;
  scheduledEndAt: ApiDateTime;
  status: MeetingStatus;
}

export interface DeleteMeetingResponse {
  deleted: boolean;
}

export interface CalendarEvent {
  id: string;
  spaceId: string;
  meetingId: string;
  title: string;
  startsAt: ApiDateTime;
  endsAt: ApiDateTime;
  status: MeetingStatus;
}

export interface CalendarEventsResponse {
  events: CalendarEvent[];
}

export interface MeetingParticipantSummary {
  id: string;
  userId: string;
  displayName?: string | null;
  email?: string | null;
  role: MeetingRole;
  participantType: ParticipantType;
  accessStatus: ParticipantAccessStatus;
}

export interface MeetingParticipantsResponse {
  participants: MeetingParticipantSummary[];
}

export interface AddMeetingParticipantRequest {
  userId: string;
  role: MeetingRole;
  participantType: ParticipantType;
}

export interface AddMeetingParticipantResponse {
  participantId: string;
  role: MeetingRole;
  accessStatus: ParticipantAccessStatus;
}

export interface UpdateMeetingParticipantRequest {
  role?: MeetingRole;
  accessStatus?: ParticipantAccessStatus;
}

export interface UpdateMeetingParticipantResponse {
  participantId: string;
  role: MeetingRole;
  accessStatus: ParticipantAccessStatus;
}

export type InvitationStatus = "PENDING" | "ACCEPTED" | "DECLINED" | "EXPIRED";
export type MeetingJoinRequestStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface CreateMeetingJoinRequestRequest {
  joinCodeOrUrl: string;
}

export interface CreateMeetingJoinRequestResponse {
  requestId: string;
  meetingId: string;
  status: MeetingJoinRequestStatus;
}

export interface MeetingJoinRequestSummary {
  id: string;
  userId: string;
  status: MeetingJoinRequestStatus;
  requestedAt: ApiDateTime;
}

export interface MeetingJoinRequestsResponse {
  requests: MeetingJoinRequestSummary[];
}

export interface ReviewMeetingJoinRequestResponse {
  requestId: string;
  status: MeetingJoinRequestStatus;
  participantId: string | null;
  participantType: ParticipantType | null;
}

export interface CreateMeetingInvitationResponse {
  invitationId: string;
  status: InvitationStatus;
  expiresAt: ApiDateTime;
  inviteToken: string;
}

export interface ResolveMeetingInvitationResponse {
  participantId: string | null;
  role: MeetingRole | null;
  participantType: ParticipantType | null;
  status: InvitationStatus;
}

export interface ResolveInvitationRequest {
  token: string;
}

export interface MeetingDetail extends MeetingSummary {
  startedAt: ApiDateTime | null;
  endedAt: ApiDateTime | null;
  participants: MeetingParticipantSummary[];
}

export interface CreateMeetingRequest {
  title: string;
  description?: string;
  scheduledAt: ApiDateTime;
  scheduledEndAt: ApiDateTime;
  participantUserIds?: string[];
}

export interface CreateMeetingResponse {
  id: string;
  status: MeetingStatus;
  joinCode: string;
  joinUrl: string;
}

export interface CreateInstantMeetingResponse {
  id: string;
  status: MeetingStatus;
  roomCode: string;
  joinCode: string;
  joinUrl: string;
}

export interface MeetingSpeakerSummary {
  id: string;
  label: string;
  displayName: string | null;
}

export interface TranscriptSegmentSummary {
  id: string;
  speakerId: string;
  startMs: number;
  endMs: number;
  text: string;
}

export interface StartMeetingTranscriptionRequest {
  mode: "realtime";
  trackId: string;
}

export interface MeetingTranscriptionStartResponse {
  meetingId: string;
  transcriptStatus: "PROCESSING";
  sessionId: string;
  egressId: string;
}

export interface MeetingTranscriptStatusResponse {
  meetingId: string;
  transcriptStatus: "COMPLETED" | "FAILED";
}

export interface MeetingDialogueResponse {
  meetingId: string;
  status: "PROCESSING" | "COMPLETED" | "FAILED";
  rows: Array<{
    segmentId: string;
    speakerId: string;
    speakerLabel: string;
    speakerName: string | null;
    startMs: number;
    endMs: number;
    text: string;
  }>;
  partials: Array<{
    speakerLabel: string;
    speakerName: string | null;
    text: string;
  }>;
}

export interface TranscriptResponse {
  meetingId: string;
  language: string;
  status: TranscriptStatus;
  speakers: MeetingSpeakerSummary[];
  segments: TranscriptSegmentSummary[];
}

export interface ReportSummary {
  id: string;
  meetingId: string;
  status: MeetingReportStatus;
  title: string;
  summary: string;
  version: number;
  isCurrent: boolean;
  createdAt: ApiDateTime;
}

export interface ReportListResponse {
  reports: ReportSummary[];
}

export interface TaskCard {
  id: string;
  spaceId: string;
  meetingId: string | null;
  title: string;
  description: string | null;
  status: TaskCardStatus;
  priority: TaskCardPriority;
  labels: string[];
  assigneeId: string | null;
  dueDate: ApiDate | null;
  sourceCandidateId: string | null;
}

export interface TaskListResponse {
  tasks: TaskCard[];
}

export interface CreateTaskCardRequest {
  title: string;
  description?: string | null;
  assigneeId?: string | null;
  dueDate?: ApiDate | null;
  meetingId?: string | null;
  priority?: TaskCardPriority;
  labels?: string[];
}

export interface CreateTaskCardResponse {
  id: string;
  status: TaskCardStatus;
}

export interface UpdateTaskCardRequest {
  title?: string;
  description?: string | null;
  assigneeId?: string | null;
  dueDate?: ApiDate | null;
  status?: TaskCardStatus;
  priority?: TaskCardPriority;
  labels?: string[];
}

export interface UpdateTaskCardResponse {
  id: string;
  status: TaskCardStatus;
  priority: TaskCardPriority;
  labels: string[];
  updatedAt: ApiDateTime;
}

export interface DeleteTaskCardResponse {
  deleted: boolean;
}

export interface SpaceMemberSummary {
  id: string;
  userId: string;
  displayName: string | null;
  email: string | null;
  role: SpaceRole;
  joinedAt: ApiDateTime;
}

export interface SpaceMembersResponse {
  members: SpaceMemberSummary[];
}

export interface CreateSpaceInvitationRequest {
  email: string;
  role: Exclude<SpaceRole, "OWNER">;
}

export interface CreateSpaceInvitationResponse {
  invitationId: string;
  status: InvitationStatus;
  expiresAt: ApiDateTime;
  inviteToken: string;
}

export interface ResolveSpaceInvitationResponse {
  memberId?: string;
  invitationId?: string;
  role?: SpaceRole;
  status: InvitationStatus;
}

export interface PendingSpaceInvitation {
  invitationId: string;
  spaceId: string;
  spaceName: string;
  role: Exclude<SpaceRole, "OWNER">;
  expiresAt: ApiDateTime;
}

export interface SpaceInvitationAdmin {
  invitationId: string;
  email: string;
  role: Exclude<SpaceRole, "OWNER">;
  status: InvitationStatus;
  expiresAt: ApiDateTime;
}

export interface SpaceInvitationAdminResponse { invitations: SpaceInvitationAdmin[]; }

export interface SpaceInvitationListResponse {
  invitations: PendingSpaceInvitation[];
}

export interface UpdateSpaceMemberRoleRequest {
  role: Exclude<SpaceRole, "OWNER">;
}

export interface UpdateSpaceMemberRoleResponse {
  memberId: string;
  role: SpaceRole;
}

export interface RemoveSpaceMemberResponse {
  removed: boolean;
}

export interface OwnerTransferRequest {
  targetMemberId: string;
  previousOwnerRole: Exclude<SpaceRole, "OWNER">;
  confirmation: string;
}

export interface OwnerTransferResponse {
  ownerMemberId: string;
  previousOwnerMemberId: string;
  previousOwnerRole: Exclude<SpaceRole, "OWNER">;
}

export interface SpaceDetail {
  id: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  role: SpaceRole;
  upcomingMeetings: MeetingSummary[];
  recentReports: ReportSummary[];
  actionItems: TaskCard[];
  aiEntrypoints: Array<"project-ai" | "meeting-ai">;
}

export interface DashboardRecentActivity {
  id: string;
  spaceId: string;
  title: string;
  occurredAt: ApiDateTime;
  type: "meeting" | "report" | "task" | "space";
}

export interface DashboardSummaryResponse {
  todayMeetings: CalendarEvent[];
  recentActivities: DashboardRecentActivity[];
  spaces: SpaceSummary[];
  actionItems: TaskCard[];
  latestReports: Array<{
    id: string;
    spaceId: string;
    meetingId: string;
    meetingTitle: string;
    title: string;
    summary: string;
    version: number;
    confirmedAt: ApiDateTime;
  }>;
}

export interface AiSource {
  sourceId: string;
  type: AiSourceType;
  title: string;
  text: string;
}

export interface ProjectAiMeetingContext {
  meetingId: string;
  title: string;
  summary: string;
}

export interface ProjectKnowledgeContext {
  sourceId: string;
  title: string;
  text: string;
}

export type ProjectKnowledgeType = "report" | "decision" | "manual" | "external";

export interface ProjectKnowledgeItem {
  id: string;
  spaceId: string;
  type: ProjectKnowledgeType;
  title: string;
  contentPreview: string;
  sourceMeetingId: string | null;
  embeddingStatus: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
  updatedAt: ApiDateTime;
}

export interface ProjectKnowledgeListResponse {
  items: ProjectKnowledgeItem[];
}

export interface KnowledgeGraphNode {
  id: string;
  sourceType: "projectKnowledge" | "transcript" | "meetingSummary" | "decision" | "actionItem" | "report" | "glossary";
  title: string;
  sourceMeetingId: string | null;
  embeddingStatus: "COMPLETED";
  entityId?: string | null;
  nodeType?: "MEETING" | "REPORT" | "DECISION" | "ACTION" | "TASK" | "PROJECT_KNOWLEDGE" | "TOPIC" | "PARTICIPANT" | null;
  connectionCount?: number;
  clusterIds?: string[];
  detailTarget?: { kind: string; id: string } | null;
}

export interface KnowledgeGraphCluster {
  id: string;
  label: string;
  sourceCount: number;
  nodes: KnowledgeGraphNode[];
  clusterBy?: string;
  nodeIds?: string[];
  nodeCount?: number;
  keywords?: string[];
  colorKey?: string;
}

export interface KnowledgeGraphResponse {
  clusters: KnowledgeGraphCluster[];
  edges: Array<{ from: string; to: string; similarity: number; id?: string; edgeType?: string; weight?: number }>;
  generatedAt: string;
  nodes?: KnowledgeGraphNode[];
  filters?: { appliedNodeTypes?: string[]; truncated?: boolean };
  partial?: boolean;
}

export interface ProjectKnowledgeDetailResponse {
  id: string;
  spaceId: string;
  type: ProjectKnowledgeType;
  title: string;
  content: string;
  sourceMeetingId: string | null;
  embeddingStatus: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
  updatedAt: ApiDateTime;
}

export interface CreateProjectKnowledgeRequest {
  type: ProjectKnowledgeType;
  title: string;
  content: string;
  sourceMeetingId?: string | null;
}

export interface UpdateProjectKnowledgeRequest {
  title?: string;
  content?: string;
}

export interface ProjectKnowledgeMutationResponse {
  id: string;
  status: "PUBLISHED" | "ARCHIVED";
  embeddingStatus: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
  embeddingJobId: string | null;
  updatedAt: ApiDateTime;
}

export interface DeleteProjectKnowledgeResponse {
  deleted: boolean;
}

export interface ProjectAiChatRequest {
  projectId: string;
  question: string;
  projectKnowledge: ProjectKnowledgeContext[];
  meetings: ProjectAiMeetingContext[];
}

export interface ProjectAiHistoryResponse {
  messages: Array<{
    id: string;
    role: "USER" | "ASSISTANT";
    content: string;
    createdAt: ApiDateTime;
  }>;
}

export interface MeetingAiChatRequest {
  projectId: string;
  meetingId: string;
  meetingTitle: string;
  question: string;
  transcript: TranscriptRow[];
  decisions: LabeledItem[];
  actions: LabeledItem[];
}

export interface AiChatResponse {
  answer: string;
  sources: AiSource[];
  unsupported: boolean;
  unsupportedReason: UnsupportedReason | null;
  model: string;
}

export interface TermExplanationResponse {
  term: string;
  explanation: string;
  sourceType: "glossary" | "transcript" | "decision" | "none";
  sources: AiSource[];
  unsupported: boolean;
  unsupportedReason: UnsupportedReason | null;
  model: string;
}

export type UnsupportedReason =
  | "NO_EVIDENCE"
  | "LOW_RELEVANCE"
  | "MODEL_UNSUPPORTED"
  | "UNVERIFIED_OUTPUT";

export interface ReportCandidateDecision {
  id: string;
  title: string;
  rationale: string | null;
  sourceIds: string[];
}

export interface ReportCandidateActionItem {
  id: string;
  title: string;
  assignee: string | null;
  dueDate: string | null;
  confirmationState: "candidate";
  sourceIds: string[];
}

export interface StoredReportCandidate {
  id: string;
  meetingId: string;
  status: "CANDIDATE";
  title: string;
  summary: string;
  markdown: string;
  decisions: ReportCandidateDecision[];
  actionItems: ReportCandidateActionItem[];
  sourceIds: string[];
  createdBy: string;
  version: number;
  isCurrent: false;
  createdAt: ApiDateTime;
}

export interface ReportCandidateResponse {
  candidate: StoredReportCandidate | null;
  sources: AiSource[];
  unsupported: boolean;
  model: string;
}

export interface ExtractTaskCandidatesRequest {
  projectId: string;
  meetingId: string;
  title: string;
  transcript: TranscriptRow[];
  summary: string;
  participants: Array<{
    name: string;
    role: MeetingRole;
  }>;
}

export interface AiTaskCandidate {
  title: string;
  assignee: string | null;
  dueDate: ApiDate | null;
  sourceIds: string[];
  confirmationState: "candidate";
}

export interface ExtractTaskCandidatesResponse {
  tasks: AiTaskCandidate[];
  sources: AiSource[];
  unsupported: boolean;
  model: string;
}

export interface ConfirmReportResponse {
  id: string;
  status: "CONFIRMED";
  version: number;
  isCurrent: boolean;
  confirmedAt: ApiDateTime;
}

export interface UpdateReportRequest {
  title?: string;
  summary?: string;
  markdown?: string;
}

export interface UpdateReportResponse {
  id: string;
  status: "DRAFT";
  version: number;
}

export interface ReportDetailResponse {
  id: string;
  meetingId: string;
  status: "CANDIDATE" | "DRAFT" | "CONFIRMED";
  title: string;
  summary: string;
  markdown: string | null;
  version: number;
  isCurrent: boolean;
  createdAt: ApiDateTime;
  confirmedAt: ApiDateTime | null;
  sourceIds: string[];
}

export interface RestoreReportResponse {
  id: string;
  status: "DRAFT";
  version: number;
  sourceReportId: string;
}

export type ReportDownloadFormat = "markdown" | "pdf" | "docx";

export interface TaskCandidateSummary {
  id: string;
  meetingId: string;
  title: string;
  assigneeName: string | null;
  suggestedAssigneeId: string | null;
  dueDate: ApiDate | null;
  status: "CANDIDATE" | "CONFIRMED" | "DISMISSED";
  sourceIds: string[];
  createdBy: string;
  createdAt: ApiDateTime;
}

export interface TaskCandidateGenerationResponse {
  candidates: TaskCandidateSummary[];
  assignees: TaskAssigneeOption[];
  canConfirm: boolean;
  sources: AiSource[];
  unsupported: boolean;
  model: string;
}

export interface TaskCandidatesResponse {
  candidates: TaskCandidateSummary[];
  assignees: TaskAssigneeOption[];
  canConfirm: boolean;
}

export interface TaskAssigneeOption {
  id: string;
  displayName: string;
}

export interface ConfirmTaskCandidateRequest {
  title: string;
  description?: string | null;
  assigneeId?: string | null;
  dueDate?: ApiDate | null;
  status: TaskCardStatus;
}

export interface ConfirmTaskCandidateResponse {
  taskId: string;
  sourceCandidateId: string;
}

export interface WorkspaceData {
  workspaceHome: {
    overview: MeetingOverview;
    todayMeeting: WorkspaceTodayMeeting;
    spaces: WorkspaceSpace[];
    recent: LabeledItem[];
  };
  liveMeeting: {
    overview: MeetingOverview;
    roomCode: string;
    startsAt: string;
    participants: string[];
    checklist: LabeledItem[];
    accessMembers: MeetingAccessMember[];
  };
  meetingAi: {
    overview: MeetingOverview;
    transcript: TranscriptRow[];
    decisions: LabeledItem[];
    actions: LabeledItem[];
    chat: { role: "user" | "ai"; text: string }[];
    suggestions: LinkItem[];
  };
  projectOverview: {
    overview: MeetingOverview;
    metrics: { label: string; value: string; note: string }[];
    techStack: string;
    meetings: ProjectOverviewMeeting[];
    documents: LabeledItem[];
    questions: LinkItem[];
  };
  reportAgent: {
    overview: MeetingOverview;
    reportTitle: string;
    reportDate: string;
    decisions: { item: string; decision: string; note: string }[];
    changes: LabeledItem[];
    chat: { role: "user" | "ai"; text: string }[];
  };
}
