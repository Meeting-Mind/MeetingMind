import type { AuthSession } from "../auth/session";
import { bffFetch, resetCsrfToken } from "../auth/csrf";
import type {
  AddMeetingParticipantRequest,
  AddMeetingParticipantResponse,
  AiChatResponse,
  CalendarEventsResponse,
  ConfirmReportResponse,
  ConfirmTaskCandidateRequest,
  ConfirmTaskCandidateResponse,
  CreateMeetingJoinRequestRequest,
  CreateMeetingJoinRequestResponse,
  CreateMeetingRequest,
  CreateMeetingResponse,
  CreateProjectKnowledgeRequest,
  CreateSpaceRequest,
  CreateSpaceResponse,
  CreateSpaceInvitationRequest,
  CreateSpaceInvitationResponse,
  CreateTaskCardRequest,
  CreateTaskCardResponse,
  CreateDomainTermRequest,
  DashboardSummaryResponse,
  DeleteDomainTermResponse,
  DeleteMeetingResponse,
  DeleteProjectKnowledgeResponse,
  DeleteSpaceResponse,
  DeleteTaskCardResponse,
  DomainTermListResponse,
  DomainTermMutationResponse,
  MeetingDetailResponse,
  MeetingDialogueResponse,
  MeetingListResponse,
  MeetingTranscriptionStartResponse,
  MeetingTranscriptStatusResponse,
  MeetingParticipantsResponse,
  MeetingJoinRequestsResponse,
  OwnerTransferRequest,
  OwnerTransferResponse,
  ProjectKnowledgeListResponse,
  ProjectKnowledgeDetailResponse,
  ProjectKnowledgeMutationResponse,
  ProjectAiHistoryResponse,
  ReportCandidateResponse,
  ReportDetailResponse,
  ReportDownloadFormat,
  ReportListResponse,
  RemoveSpaceMemberResponse,
  ResolveInvitationRequest,
  RestoreReportResponse,
  ResolveSpaceInvitationResponse,
  ReviewMeetingJoinRequestResponse,
  SpaceDetail,
  SpaceListResponse,
  SpaceMembersResponse,
  StartMeetingTranscriptionRequest,
  TermExplanationResponse,
  TaskCandidateSummary,
  TaskCandidatesResponse,
  TaskCandidateGenerationResponse,
  TaskListResponse,
  UpdateReportRequest,
  UpdateReportResponse,
  UpdateMeetingParticipantRequest,
  UpdateMeetingParticipantResponse,
  UpdateMeetingRequest,
  UpdateMeetingResponse,
  UpdateProjectKnowledgeRequest,
  UpdateSpaceRequest,
  UpdateSpaceMemberRoleRequest,
  UpdateSpaceMemberRoleResponse,
  UpdateSpaceResponse,
  UpdateTaskCardRequest,
  UpdateTaskCardResponse,
  UpdateDomainTermRequest,
  WorkspaceData
} from "../types";

export async function fetchLegacyWorkspaceSnapshot(_session: AuthSession): Promise<Partial<WorkspaceData>> {
  return requestJson<Partial<WorkspaceData>>("/api/workspace", {
    headers: undefined
  });
}

export async function fetchSpaces(_session: AuthSession): Promise<SpaceListResponse> {
  return requestJson<SpaceListResponse>("/api/v1/spaces", {
    headers: undefined
  });
}

export async function fetchDomainTerms(
  _session: AuthSession,
  spaceId: string,
  request: { keyword?: string; status?: "ACTIVE" | "ARCHIVED" } = {}
): Promise<DomainTermListResponse> {
  const params = new URLSearchParams();
  if (request.keyword?.trim()) {
    params.set("keyword", request.keyword.trim());
  }
  if (request.status) {
    params.set("status", request.status);
  }
  const query = params.size ? `?${params.toString()}` : "";
  return requestJson<DomainTermListResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/terms${query}`, {
    headers: undefined
  });
}

export async function createDomainTerm(
  _session: AuthSession,
  spaceId: string,
  request: CreateDomainTermRequest
): Promise<DomainTermMutationResponse> {
  return requestJson<DomainTermMutationResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/terms`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function updateDomainTerm(
  _session: AuthSession,
  spaceId: string,
  termId: string,
  request: UpdateDomainTermRequest
): Promise<DomainTermMutationResponse> {
  return requestJson<DomainTermMutationResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/terms/${encodeURIComponent(termId)}`,
    {
      method: "PATCH",
      headers: jsonHeaders(),
      body: JSON.stringify(request)
    }
  );
}

export async function archiveDomainTerm(
  _session: AuthSession,
  spaceId: string,
  termId: string
): Promise<DeleteDomainTermResponse> {
  return requestJson<DeleteDomainTermResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/terms/${encodeURIComponent(termId)}`,
    {
      method: "DELETE",
      headers: jsonHeaders()
    }
  );
}

export async function createSpace(_session: AuthSession, request: CreateSpaceRequest): Promise<CreateSpaceResponse> {
  return requestJson<CreateSpaceResponse>("/api/v1/spaces", {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function updateSpace(
  _session: AuthSession,
  spaceId: string,
  request: UpdateSpaceRequest
): Promise<UpdateSpaceResponse> {
  return requestJson<UpdateSpaceResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, {
    method: "PATCH",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function deleteSpace(_session: AuthSession, spaceId: string): Promise<DeleteSpaceResponse> {
  return requestJson<DeleteSpaceResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, {
    method: "DELETE",
    headers: undefined
  });
}

export async function fetchSpaceDetail(_session: AuthSession, spaceId: string): Promise<SpaceDetail> {
  return requestJson<SpaceDetail>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, {
    headers: undefined
  });
}

export async function fetchDashboardSummary(_session: AuthSession): Promise<DashboardSummaryResponse> {
  return requestJson<DashboardSummaryResponse>("/api/v1/dashboard", {
    headers: undefined
  });
}

export async function fetchCalendarEvents(
  _session: AuthSession,
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
    headers: undefined
  });
}

export async function fetchMeetings(_session: AuthSession, spaceId: string): Promise<MeetingListResponse> {
  return requestJson<MeetingListResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/meetings`, {
    headers: undefined
  });
}

export async function createMeeting(
  _session: AuthSession,
  spaceId: string,
  request: CreateMeetingRequest
): Promise<CreateMeetingResponse> {
  return requestJson<CreateMeetingResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/meetings`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function updateMeeting(
  _session: AuthSession,
  meetingId: string,
  request: UpdateMeetingRequest
): Promise<UpdateMeetingResponse> {
  return requestJson<UpdateMeetingResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}`, {
    method: "PATCH",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function deleteMeeting(_session: AuthSession, meetingId: string): Promise<DeleteMeetingResponse> {
  return requestJson<DeleteMeetingResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}`, {
    method: "DELETE",
    headers: undefined
  });
}

export async function fetchMeetingDetail(_session: AuthSession, meetingId: string): Promise<MeetingDetailResponse> {
  return requestJson<MeetingDetailResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}`, {
    headers: undefined
  });
}

export async function startMeetingTranscription(
  _session: AuthSession,
  meetingId: string,
  request: StartMeetingTranscriptionRequest
): Promise<MeetingTranscriptionStartResponse> {
  return requestJson<MeetingTranscriptionStartResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/transcription/start`,
    {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify(request)
    }
  );
}

export async function stopMeetingTranscription(
  _session: AuthSession,
  meetingId: string,
  sessionId: string
): Promise<MeetingTranscriptStatusResponse> {
  return requestJson<MeetingTranscriptStatusResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/transcription/${encodeURIComponent(sessionId)}/stop`,
    {
      method: "POST",
      headers: undefined
    }
  );
}

export async function fetchMeetingDialogue(_session: AuthSession, meetingId: string): Promise<MeetingDialogueResponse> {
  return requestJson<MeetingDialogueResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/dialogue`, {
    headers: undefined
  });
}

export async function fetchMeetingParticipants(_session: AuthSession, meetingId: string): Promise<MeetingParticipantsResponse> {
  return requestJson<MeetingParticipantsResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/participants`, {
    headers: undefined
  });
}

export async function addMeetingParticipant(
  _session: AuthSession,
  meetingId: string,
  request: AddMeetingParticipantRequest
): Promise<AddMeetingParticipantResponse> {
  return requestJson<AddMeetingParticipantResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/participants`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function updateMeetingParticipant(
  _session: AuthSession,
  meetingId: string,
  participantId: string,
  request: UpdateMeetingParticipantRequest
): Promise<UpdateMeetingParticipantResponse> {
  return requestJson<UpdateMeetingParticipantResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/participants/${encodeURIComponent(participantId)}`,
    {
      method: "PATCH",
      headers: jsonHeaders(),
      body: JSON.stringify(request)
    }
  );
}

export async function createMeetingJoinRequest(
  _session: AuthSession,
  request: CreateMeetingJoinRequestRequest
): Promise<CreateMeetingJoinRequestResponse> {
  return requestJson<CreateMeetingJoinRequestResponse>("/api/v1/meetings/join-requests", {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function fetchMeetingJoinRequests(
  _session: AuthSession,
  meetingId: string
): Promise<MeetingJoinRequestsResponse> {
  return requestJson<MeetingJoinRequestsResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/join-requests`,
    { headers: undefined }
  );
}

export async function approveMeetingJoinRequest(
  _session: AuthSession,
  meetingId: string,
  requestId: string
): Promise<ReviewMeetingJoinRequestResponse> {
  return requestJson<ReviewMeetingJoinRequestResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/join-requests/${encodeURIComponent(requestId)}/approve`,
    {
      method: "POST",
      headers: undefined
    }
  );
}

export async function rejectMeetingJoinRequest(
  _session: AuthSession,
  meetingId: string,
  requestId: string
): Promise<ReviewMeetingJoinRequestResponse> {
  return requestJson<ReviewMeetingJoinRequestResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/join-requests/${encodeURIComponent(requestId)}/reject`,
    {
      method: "POST",
      headers: undefined
    }
  );
}

export async function chatMeetingAi(
  _session: AuthSession,
  meetingId: string,
  request: { question: string }
): Promise<AiChatResponse> {
  return requestJson<AiChatResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/ai/chat`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function explainMeetingTerm(
  _session: AuthSession,
  meetingId: string,
  request: { term: string }
): Promise<TermExplanationResponse> {
  return requestJson<TermExplanationResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/terms/explain`,
    {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify(request)
    }
  );
}

export async function chatProjectAi(
  _session: AuthSession,
  spaceId: string,
  request: { question: string }
): Promise<AiChatResponse> {
  return requestJson<AiChatResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/ai/chat`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function fetchProjectAiHistory(
  _session: AuthSession,
  spaceId: string
): Promise<ProjectAiHistoryResponse> {
  return requestJson<ProjectAiHistoryResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/ai/history`, {
    headers: undefined
  });
}

export async function generateReportCandidate(
  _session: AuthSession,
  meetingId: string
): Promise<ReportCandidateResponse> {
  return requestJson<ReportCandidateResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/generate`,
    {
    method: "POST",
      headers: undefined
    }
  );
}

export async function editMeetingReportWithAi(
  _session: AuthSession,
  meetingId: string,
  reportId: string,
  instruction: string
): Promise<ReportCandidateResponse> {
  return requestJson<ReportCandidateResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}/ai-edits`,
    {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify({ instruction })
    }
  );
}

export async function extractTaskCandidates(
  _session: AuthSession,
  meetingId: string
): Promise<TaskCandidateGenerationResponse> {
  return requestJson<TaskCandidateGenerationResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/task-candidates/generate`,
    {
      method: "POST",
      headers: undefined
    }
  );
}

export async function fetchMeetingReports(
  _session: AuthSession,
  meetingId: string,
  status?: "CANDIDATE" | "DRAFT" | "CONFIRMED"
): Promise<ReportListResponse> {
  const params = new URLSearchParams();
  if (status) {
    params.set("status", status);
  }
  const query = params.toString() ? `?${params.toString()}` : "";

  return requestJson<ReportListResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/reports${query}`, {
    headers: undefined
  });
}

export async function confirmMeetingReport(
  _session: AuthSession,
  meetingId: string,
  reportId: string
): Promise<ConfirmReportResponse> {
  return requestJson<ConfirmReportResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}/confirm`,
    {
      method: "POST",
      headers: undefined
    }
  );
}

export async function updateMeetingReport(
  _session: AuthSession,
  meetingId: string,
  reportId: string,
  request: UpdateReportRequest
): Promise<UpdateReportResponse> {
  return requestJson<UpdateReportResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}`,
    {
      method: "PATCH",
      headers: jsonHeaders(),
      body: JSON.stringify(request)
    }
  );
}

export async function downloadMeetingReport(
  _session: AuthSession,
  meetingId: string,
  reportId: string,
  format: ReportDownloadFormat
): Promise<Blob> {
  return requestBlob(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}/download?format=${encodeURIComponent(format)}`,
    {
      headers: undefined
    }
  );
}

export async function fetchMeetingReportDetail(
  _session: AuthSession,
  meetingId: string,
  reportId: string
): Promise<ReportDetailResponse> {
  return requestJson<ReportDetailResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}`,
    { headers: undefined }
  );
}

export async function restoreMeetingReport(
  _session: AuthSession,
  meetingId: string,
  reportId: string
): Promise<RestoreReportResponse> {
  return requestJson<RestoreReportResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}/restore`,
    { method: "POST", headers: jsonHeaders() }
  );
}

export async function fetchTaskCandidates(_session: AuthSession, meetingId: string): Promise<TaskCandidatesResponse> {
  return requestJson<TaskCandidatesResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/task-candidates`, {
    headers: undefined
  });
}

export async function confirmTaskCandidate(
  _session: AuthSession,
  meetingId: string,
  candidateId: string,
  request: ConfirmTaskCandidateRequest
): Promise<ConfirmTaskCandidateResponse> {
  return requestJson<ConfirmTaskCandidateResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/task-candidates/${encodeURIComponent(candidateId)}/confirm`,
    {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify(request)
    }
  );
}

export async function dismissTaskCandidate(
  _session: AuthSession,
  meetingId: string,
  candidateId: string
): Promise<TaskCandidateSummary> {
  return requestJson<TaskCandidateSummary>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/task-candidates/${encodeURIComponent(candidateId)}/dismiss`,
    {
      method: "POST",
      headers: jsonHeaders()
    }
  );
}

export async function fetchTasks(_session: AuthSession, spaceId: string): Promise<TaskListResponse> {
  return requestJson<TaskListResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks`, {
    headers: undefined
  });
}

export async function fetchProjectKnowledge(
  _session: AuthSession,
  spaceId: string
): Promise<ProjectKnowledgeListResponse> {
  return requestJson<ProjectKnowledgeListResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge`, {
    headers: undefined
  });
}

export async function fetchProjectKnowledgeDetail(
  _session: AuthSession,
  spaceId: string,
  knowledgeId: string
): Promise<ProjectKnowledgeDetailResponse> {
  return requestJson<ProjectKnowledgeDetailResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/${encodeURIComponent(knowledgeId)}`,
    { headers: undefined }
  );
}

export async function createProjectKnowledge(
  _session: AuthSession,
  spaceId: string,
  request: CreateProjectKnowledgeRequest
): Promise<ProjectKnowledgeMutationResponse> {
  return requestJson<ProjectKnowledgeMutationResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function updateProjectKnowledge(
  _session: AuthSession,
  spaceId: string,
  knowledgeId: string,
  request: UpdateProjectKnowledgeRequest
): Promise<ProjectKnowledgeMutationResponse> {
  return requestJson<ProjectKnowledgeMutationResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/${encodeURIComponent(knowledgeId)}`,
    {
      method: "PATCH",
      headers: jsonHeaders(),
      body: JSON.stringify(request)
    }
  );
}

export async function deleteProjectKnowledge(
  _session: AuthSession,
  spaceId: string,
  knowledgeId: string
): Promise<DeleteProjectKnowledgeResponse> {
  return requestJson<DeleteProjectKnowledgeResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/${encodeURIComponent(knowledgeId)}`,
    {
      method: "DELETE",
      headers: jsonHeaders()
    }
  );
}

export async function createTask(
  _session: AuthSession,
  spaceId: string,
  request: CreateTaskCardRequest
): Promise<CreateTaskCardResponse> {
  return requestJson<CreateTaskCardResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function updateTask(
  _session: AuthSession,
  spaceId: string,
  taskId: string,
  request: UpdateTaskCardRequest
): Promise<UpdateTaskCardResponse> {
  return requestJson<UpdateTaskCardResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks/${encodeURIComponent(taskId)}`, {
    method: "PATCH",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function deleteTask(_session: AuthSession, spaceId: string, taskId: string): Promise<DeleteTaskCardResponse> {
  return requestJson<DeleteTaskCardResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks/${encodeURIComponent(taskId)}`, {
    method: "DELETE",
    headers: undefined
  });
}

export async function fetchSpaceMembers(_session: AuthSession, spaceId: string): Promise<SpaceMembersResponse> {
  return requestJson<SpaceMembersResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/members`, {
    headers: undefined
  });
}

export async function createSpaceInvitation(
  _session: AuthSession,
  spaceId: string,
  request: CreateSpaceInvitationRequest
): Promise<CreateSpaceInvitationResponse> {
  return requestJson<CreateSpaceInvitationResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/invitations`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

export async function acceptSpaceInvitation(
  _session: AuthSession,
  spaceId: string,
  invitationId: string,
  request: ResolveInvitationRequest
): Promise<ResolveSpaceInvitationResponse> {
  return requestJson<ResolveSpaceInvitationResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/invitations/${encodeURIComponent(invitationId)}/accept`,
    {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify(request)
    }
  );
}

export async function declineSpaceInvitation(
  _session: AuthSession,
  spaceId: string,
  invitationId: string,
  request: ResolveInvitationRequest
): Promise<ResolveSpaceInvitationResponse> {
  return requestJson<ResolveSpaceInvitationResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/invitations/${encodeURIComponent(invitationId)}/decline`,
    {
      method: "POST",
      headers: jsonHeaders(),
      body: JSON.stringify(request)
    }
  );
}

export async function updateSpaceMemberRole(
  _session: AuthSession,
  spaceId: string,
  memberId: string,
  request: UpdateSpaceMemberRoleRequest
): Promise<UpdateSpaceMemberRoleResponse> {
  return requestJson<UpdateSpaceMemberRoleResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/members/${encodeURIComponent(memberId)}`,
    {
      method: "PATCH",
      headers: jsonHeaders(),
      body: JSON.stringify(request)
    }
  );
}

export async function removeSpaceMember(_session: AuthSession, spaceId: string, memberId: string): Promise<RemoveSpaceMemberResponse> {
  return requestJson<RemoveSpaceMemberResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/members/${encodeURIComponent(memberId)}`, {
    method: "DELETE",
    headers: undefined
  });
}

export async function transferSpaceOwner(
  _session: AuthSession,
  spaceId: string,
  request: OwnerTransferRequest
): Promise<OwnerTransferResponse> {
  return requestJson<OwnerTransferResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/owner-transfer`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify(request)
  });
}

function jsonHeaders(): HeadersInit {
  return {
    "Content-Type": "application/json"
  };
}

async function requestJson<T>(path: string, init: RequestInit): Promise<T> {
  const response = await bffFetch(path, init);

  if (!response.ok) {
    if (response.status === 403) {
      resetCsrfToken();
    }
    const message = await readErrorMessage(response);
    throw new Error(message || `API request failed with ${response.status}`);
  }

  return (await response.json()) as T;
}

async function requestBlob(path: string, init: RequestInit): Promise<Blob> {
  const response = await bffFetch(path, init);

  if (!response.ok) {
    if (response.status === 403) {
      resetCsrfToken();
    }
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
