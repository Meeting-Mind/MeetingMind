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

export interface MeetingParticipantSummary {
  id: string;
  userId: string;
  displayName?: string | null;
  email?: string | null;
  role: MeetingRole;
  participantType: ParticipantType;
  accessStatus: ParticipantAccessStatus;
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

export interface AiChatResponse {
  answer: string;
  sources: AiSource[];
  unsupported: boolean;
  model: string;
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
    meetings: { index: string; title: string; date: string; state: string }[];
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
