import { buildAuthHeaders, type AuthSession } from "../auth/session";
import type {
  AddMeetingParticipantRequest,
  AddMeetingParticipantResponse,
  AiChatResponse,
  CalendarEventsResponse,
  ConfirmReportResponse,
  ConfirmTaskCandidateRequest,
  ConfirmTaskCandidateResponse,
  CreateMeetingRequest,
  CreateMeetingResponse,
  CreateMeetingInvitationRequest,
  CreateMeetingInvitationResponse,
  CreateSpaceRequest,
  CreateSpaceResponse,
  CreateSpaceInvitationRequest,
  CreateSpaceInvitationResponse,
  CreateTaskCardRequest,
  CreateTaskCardResponse,
  DashboardSummaryResponse,
  DeleteMeetingResponse,
  DeleteSpaceResponse,
  DeleteTaskCardResponse,
  ExtractTaskCandidatesRequest,
  ExtractTaskCandidatesResponse,
  MeetingListResponse,
  MeetingParticipantsResponse,
  OwnerTransferRequest,
  OwnerTransferResponse,
  GenerateReportCandidateRequest,
  ReportCandidateResponse,
  ReportDownloadFormat,
  ReportListResponse,
  RemoveSpaceMemberResponse,
  ResolveInvitationRequest,
  ResolveMeetingInvitationResponse,
  ResolveSpaceInvitationResponse,
  SpaceDetail,
  SpaceListResponse,
  SpaceMembersResponse,
  TaskCandidatesResponse,
  TaskListResponse,
  UpdateReportRequest,
  UpdateReportResponse,
  UpdateMeetingParticipantRequest,
  UpdateMeetingParticipantResponse,
  UpdateMeetingRequest,
  UpdateMeetingResponse,
  UpdateSpaceRequest,
  UpdateSpaceMemberRoleRequest,
  UpdateSpaceMemberRoleResponse,
  UpdateSpaceResponse,
  UpdateTaskCardRequest,
  UpdateTaskCardResponse,
  WorkspaceData
} from "../types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.trim() || "";
const AI_API_BASE_URL = import.meta.env.VITE_AI_API_BASE_URL?.trim() || "http://localhost:8000";

export async function fetchLegacyWorkspaceSnapshot(session: AuthSession): Promise<Partial<WorkspaceData>> {
  return requestJson<Partial<WorkspaceData>>("/api/workspace", {
    headers: buildAuthHeaders(session)
  });
}

export async function fetchSpaces(session: AuthSession): Promise<SpaceListResponse> {
  return requestJson<SpaceListResponse>("/api/v1/spaces", {
    headers: buildAuthHeaders(session)
  });
}

export async function createSpace(session: AuthSession, request: CreateSpaceRequest): Promise<CreateSpaceResponse> {
  return requestJson<CreateSpaceResponse>("/api/v1/spaces", {
    method: "POST",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

export async function updateSpace(
  session: AuthSession,
  spaceId: string,
  request: UpdateSpaceRequest
): Promise<UpdateSpaceResponse> {
  return requestJson<UpdateSpaceResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, {
    method: "PATCH",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

export async function deleteSpace(session: AuthSession, spaceId: string): Promise<DeleteSpaceResponse> {
  return requestJson<DeleteSpaceResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, {
    method: "DELETE",
    headers: buildAuthHeaders(session)
  });
}

export async function fetchSpaceDetail(session: AuthSession, spaceId: string): Promise<SpaceDetail> {
  return requestJson<SpaceDetail>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, {
    headers: buildAuthHeaders(session)
  });
}

export async function fetchDashboardSummary(session: AuthSession): Promise<DashboardSummaryResponse> {
  return requestJson<DashboardSummaryResponse>("/api/v1/dashboard", {
    headers: buildAuthHeaders(session)
  });
}

export async function fetchCalendarEvents(
  session: AuthSession,
  request: { from: string; to: string; spaceId?: string }
): Promise<CalendarEventsResponse> {
  const params = new URLSearchParams({
    from: request.from,
    to: request.to
  });

  if (request.spaceId) {
    params.set("spaceId", request.spaceId);
  }

  return requestJson<CalendarEventsResponse>(`/api/v1/calendar/events?${params.toString()}`, {
    headers: buildAuthHeaders(session)
  });
}

export async function fetchMeetings(session: AuthSession, spaceId: string): Promise<MeetingListResponse> {
  return requestJson<MeetingListResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/meetings`, {
    headers: buildAuthHeaders(session)
  });
}

export async function createMeeting(
  session: AuthSession,
  spaceId: string,
  request: CreateMeetingRequest
): Promise<CreateMeetingResponse> {
  return requestJson<CreateMeetingResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/meetings`, {
    method: "POST",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

export async function updateMeeting(
  session: AuthSession,
  meetingId: string,
  request: UpdateMeetingRequest
): Promise<UpdateMeetingResponse> {
  return requestJson<UpdateMeetingResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}`, {
    method: "PATCH",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

export async function deleteMeeting(session: AuthSession, meetingId: string): Promise<DeleteMeetingResponse> {
  return requestJson<DeleteMeetingResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}`, {
    method: "DELETE",
    headers: buildAuthHeaders(session)
  });
}

export async function fetchMeetingParticipants(session: AuthSession, meetingId: string): Promise<MeetingParticipantsResponse> {
  return requestJson<MeetingParticipantsResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/participants`, {
    headers: buildAuthHeaders(session)
  });
}

export async function addMeetingParticipant(
  session: AuthSession,
  meetingId: string,
  request: AddMeetingParticipantRequest
): Promise<AddMeetingParticipantResponse> {
  return requestJson<AddMeetingParticipantResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/participants`, {
    method: "POST",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

export async function updateMeetingParticipant(
  session: AuthSession,
  meetingId: string,
  participantId: string,
  request: UpdateMeetingParticipantRequest
): Promise<UpdateMeetingParticipantResponse> {
  return requestJson<UpdateMeetingParticipantResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/participants/${encodeURIComponent(participantId)}`,
    {
      method: "PATCH",
      headers: jsonHeaders(session),
      body: JSON.stringify(request)
    }
  );
}

export async function createMeetingInvitation(
  session: AuthSession,
  meetingId: string,
  request: CreateMeetingInvitationRequest
): Promise<CreateMeetingInvitationResponse> {
  return requestJson<CreateMeetingInvitationResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/invitations`, {
    method: "POST",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

export async function acceptMeetingInvitation(
  session: AuthSession,
  meetingId: string,
  invitationId: string,
  request: ResolveInvitationRequest
): Promise<ResolveMeetingInvitationResponse> {
  return requestJson<ResolveMeetingInvitationResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/invitations/${encodeURIComponent(invitationId)}/accept`,
    {
      method: "POST",
      headers: jsonHeaders(session),
      body: JSON.stringify(request)
    }
  );
}

export async function declineMeetingInvitation(
  session: AuthSession,
  meetingId: string,
  invitationId: string,
  request: ResolveInvitationRequest
): Promise<ResolveMeetingInvitationResponse> {
  return requestJson<ResolveMeetingInvitationResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/invitations/${encodeURIComponent(invitationId)}/decline`,
    {
      method: "POST",
      headers: jsonHeaders(session),
      body: JSON.stringify(request)
    }
  );
}

export async function chatMeetingAi(
  session: AuthSession,
  meetingId: string,
  request: { question: string }
): Promise<AiChatResponse> {
  return requestJson<AiChatResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/ai/chat`, {
    method: "POST",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

export async function generateReportCandidate(
  request: GenerateReportCandidateRequest
): Promise<ReportCandidateResponse> {
  return requestAiJson<ReportCandidateResponse>("/api/meeting-ai/generate-report", {
    method: "POST",
    headers: plainJsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function extractTaskCandidates(
  request: ExtractTaskCandidatesRequest
): Promise<ExtractTaskCandidatesResponse> {
  return requestAiJson<ExtractTaskCandidatesResponse>("/api/meeting-ai/extract-tasks", {
    method: "POST",
    headers: plainJsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function fetchMeetingReports(
  session: AuthSession,
  meetingId: string,
  status?: "CANDIDATE" | "DRAFT" | "CONFIRMED"
): Promise<ReportListResponse> {
  const params = new URLSearchParams();
  if (status) {
    params.set("status", status);
  }
  const query = params.toString() ? `?${params.toString()}` : "";

  return requestJson<ReportListResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/reports${query}`, {
    headers: buildAuthHeaders(session)
  });
}

export async function confirmMeetingReport(
  session: AuthSession,
  meetingId: string,
  reportId: string
): Promise<ConfirmReportResponse> {
  return requestJson<ConfirmReportResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}/confirm`,
    {
      method: "POST",
      headers: buildAuthHeaders(session)
    }
  );
}

export async function updateMeetingReport(
  session: AuthSession,
  meetingId: string,
  reportId: string,
  request: UpdateReportRequest
): Promise<UpdateReportResponse> {
  return requestJson<UpdateReportResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}`,
    {
      method: "PATCH",
      headers: jsonHeaders(session),
      body: JSON.stringify(request)
    }
  );
}

export async function downloadMeetingReport(
  session: AuthSession,
  meetingId: string,
  reportId: string,
  format: ReportDownloadFormat
): Promise<Blob> {
  return requestBlob(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}/download?format=${encodeURIComponent(format)}`,
    {
      headers: buildAuthHeaders(session)
    }
  );
}

export async function fetchTaskCandidates(session: AuthSession, meetingId: string): Promise<TaskCandidatesResponse> {
  return requestJson<TaskCandidatesResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/task-candidates`, {
    headers: buildAuthHeaders(session)
  });
}

export async function confirmTaskCandidate(
  session: AuthSession,
  meetingId: string,
  candidateId: string,
  request: ConfirmTaskCandidateRequest
): Promise<ConfirmTaskCandidateResponse> {
  return requestJson<ConfirmTaskCandidateResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/task-candidates/${encodeURIComponent(candidateId)}/confirm`,
    {
      method: "POST",
      headers: jsonHeaders(session),
      body: JSON.stringify(request)
    }
  );
}

export async function fetchTasks(session: AuthSession, spaceId: string): Promise<TaskListResponse> {
  return requestJson<TaskListResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks`, {
    headers: buildAuthHeaders(session)
  });
}

export async function createTask(
  session: AuthSession,
  spaceId: string,
  request: CreateTaskCardRequest
): Promise<CreateTaskCardResponse> {
  return requestJson<CreateTaskCardResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks`, {
    method: "POST",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

export async function updateTask(
  session: AuthSession,
  spaceId: string,
  taskId: string,
  request: UpdateTaskCardRequest
): Promise<UpdateTaskCardResponse> {
  return requestJson<UpdateTaskCardResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks/${encodeURIComponent(taskId)}`, {
    method: "PATCH",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

export async function deleteTask(session: AuthSession, spaceId: string, taskId: string): Promise<DeleteTaskCardResponse> {
  return requestJson<DeleteTaskCardResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks/${encodeURIComponent(taskId)}`, {
    method: "DELETE",
    headers: buildAuthHeaders(session)
  });
}

export async function fetchSpaceMembers(session: AuthSession, spaceId: string): Promise<SpaceMembersResponse> {
  return requestJson<SpaceMembersResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/members`, {
    headers: buildAuthHeaders(session)
  });
}

export async function createSpaceInvitation(
  session: AuthSession,
  spaceId: string,
  request: CreateSpaceInvitationRequest
): Promise<CreateSpaceInvitationResponse> {
  return requestJson<CreateSpaceInvitationResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/invitations`, {
    method: "POST",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

export async function acceptSpaceInvitation(
  session: AuthSession,
  spaceId: string,
  invitationId: string,
  request: ResolveInvitationRequest
): Promise<ResolveSpaceInvitationResponse> {
  return requestJson<ResolveSpaceInvitationResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/invitations/${encodeURIComponent(invitationId)}/accept`,
    {
      method: "POST",
      headers: jsonHeaders(session),
      body: JSON.stringify(request)
    }
  );
}

export async function declineSpaceInvitation(
  session: AuthSession,
  spaceId: string,
  invitationId: string,
  request: ResolveInvitationRequest
): Promise<ResolveSpaceInvitationResponse> {
  return requestJson<ResolveSpaceInvitationResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/invitations/${encodeURIComponent(invitationId)}/decline`,
    {
      method: "POST",
      headers: jsonHeaders(session),
      body: JSON.stringify(request)
    }
  );
}

export async function updateSpaceMemberRole(
  session: AuthSession,
  spaceId: string,
  memberId: string,
  request: UpdateSpaceMemberRoleRequest
): Promise<UpdateSpaceMemberRoleResponse> {
  return requestJson<UpdateSpaceMemberRoleResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/members/${encodeURIComponent(memberId)}`,
    {
      method: "PATCH",
      headers: jsonHeaders(session),
      body: JSON.stringify(request)
    }
  );
}

export async function removeSpaceMember(session: AuthSession, spaceId: string, memberId: string): Promise<RemoveSpaceMemberResponse> {
  return requestJson<RemoveSpaceMemberResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/members/${encodeURIComponent(memberId)}`, {
    method: "DELETE",
    headers: buildAuthHeaders(session)
  });
}

export async function transferSpaceOwner(
  session: AuthSession,
  spaceId: string,
  request: OwnerTransferRequest
): Promise<OwnerTransferResponse> {
  return requestJson<OwnerTransferResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/owner-transfer`, {
    method: "POST",
    headers: jsonHeaders(session),
    body: JSON.stringify(request)
  });
}

function jsonHeaders(session: AuthSession): HeadersInit {
  return {
    ...buildAuthHeaders(session),
    "Content-Type": "application/json"
  };
}

function plainJsonHeaders(): HeadersInit {
  return {
    "Content-Type": "application/json"
  };
}

async function requestJson<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, init);

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message || `API request failed with ${response.status}`);
  }

  return (await response.json()) as T;
}

async function requestAiJson<T>(path: string, init: RequestInit): Promise<T> {
  const response = await fetch(`${AI_API_BASE_URL}${path}`, init);

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message || `AI request failed with ${response.status}`);
  }

  return (await response.json()) as T;
}

async function requestBlob(path: string, init: RequestInit): Promise<Blob> {
  const response = await fetch(`${API_BASE_URL}${path}`, init);

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message || `API request failed with ${response.status}`);
  }

  return response.blob();
}

async function readErrorMessage(response: Response) {
  try {
    const text = await response.text();
    if (!text) {
      return "";
    }

    try {
      const payload = JSON.parse(text) as { message?: string };
      return payload.message || text;
    } catch {
      return text;
    }
  } catch {
    return "";
  }
}
