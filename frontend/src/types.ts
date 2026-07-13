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
  scheduledAt?: ApiDateTime;
  durationMinutes?: number;
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
export type TaskCardStatus = "TODO" | "IN_PROGRESS" | "DONE";
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
  role: SpaceRole;
  meetingCount: number;
  updatedAt: ApiDateTime;
}

export interface SpaceListResponse {
  spaces: SpaceSummary[];
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
}

export interface UpdateSpaceResponse {
  id: string;
  name: string;
  description: string | null;
  updatedAt: ApiDateTime;
}

export interface DeleteSpaceResponse {
  deleted: boolean;
}

export interface MeetingSummary {
  id: string;
  spaceId: string;
  title: string;
  scheduledAt: ApiDateTime;
  status: MeetingStatus;
  myRole: MeetingRole;
}

export interface MeetingListResponse {
  meetings: MeetingSummary[];
}

export interface UpdateMeetingRequest {
  title?: string;
  scheduledAt?: ApiDateTime;
  status?: MeetingStatus;
}

export interface UpdateMeetingResponse {
  id: string;
  title: string;
  scheduledAt: ApiDateTime;
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

export interface CreateMeetingInvitationRequest {
  email: string;
  meetingRole: MeetingRole;
  participantType: ParticipantType;
}

export interface CreateMeetingInvitationResponse {
  invitationId: string;
  status: InvitationStatus;
  expiresAt: ApiDateTime;
}

export interface ResolveInvitationRequest {
  token: string;
}

export interface ResolveMeetingInvitationResponse {
  participantId?: string;
  invitationId?: string;
  role?: MeetingRole;
  participantType?: ParticipantType;
  status: InvitationStatus;
}

export interface MeetingDetail extends MeetingSummary {
  startedAt: ApiDateTime | null;
  endedAt: ApiDateTime | null;
  participants: MeetingParticipantSummary[];
}

export interface CreateMeetingRequest {
  title: string;
  scheduledAt: ApiDateTime;
  participantUserIds?: string[];
}

export interface CreateMeetingResponse {
  id: string;
  status: MeetingStatus;
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
}

export interface UpdateTaskCardResponse {
  id: string;
  status: TaskCardStatus;
  updatedAt: ApiDateTime;
}

export interface DeleteTaskCardResponse {
  deleted: boolean;
}

export interface SpaceMemberSummary {
  id: string;
  userId: string;
  displayName: string;
  email: string;
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
}

export interface ResolveSpaceInvitationResponse {
  memberId?: string;
  invitationId?: string;
  role?: SpaceRole;
  status: InvitationStatus;
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

export interface ProjectAiChatRequest {
  projectId: string;
  question: string;
  projectKnowledge: ProjectKnowledgeContext[];
  meetings: ProjectAiMeetingContext[];
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
  model: string;
}

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
