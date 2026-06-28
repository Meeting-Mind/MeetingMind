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
