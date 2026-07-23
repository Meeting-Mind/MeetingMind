import type { AuthSession } from "../auth/session";
import { jsonHeaders, requestJson } from "./client";
import type {
  AddMeetingParticipantRequest,
  AddMeetingParticipantResponse,
  CreateMeetingJoinRequestRequest,
  CreateMeetingJoinRequestResponse,
  MeetingJoinRequestsResponse,
  MeetingParticipantsResponse,
  ReviewMeetingJoinRequestResponse,
  UpdateMeetingParticipantRequest,
  UpdateMeetingParticipantResponse
} from "../types";

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
    method: "POST", headers: jsonHeaders(), body: JSON.stringify(request)
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
    { method: "PATCH", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}

export async function createMeetingJoinRequest(
  _session: AuthSession,
  request: CreateMeetingJoinRequestRequest
): Promise<CreateMeetingJoinRequestResponse> {
  return requestJson<CreateMeetingJoinRequestResponse>("/api/v1/meetings/join-requests", {
    method: "POST", headers: jsonHeaders(), body: JSON.stringify(request)
  });
}

export async function fetchMeetingJoinRequests(_session: AuthSession, meetingId: string): Promise<MeetingJoinRequestsResponse> {
  return requestJson<MeetingJoinRequestsResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/join-requests`, {
    headers: undefined
  });
}

export async function approveMeetingJoinRequest(
  _session: AuthSession,
  meetingId: string,
  requestId: string
): Promise<ReviewMeetingJoinRequestResponse> {
  return requestJson<ReviewMeetingJoinRequestResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/join-requests/${encodeURIComponent(requestId)}/approve`,
    { method: "POST", headers: undefined }
  );
}

export async function rejectMeetingJoinRequest(
  _session: AuthSession,
  meetingId: string,
  requestId: string
): Promise<ReviewMeetingJoinRequestResponse> {
  return requestJson<ReviewMeetingJoinRequestResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/join-requests/${encodeURIComponent(requestId)}/reject`,
    { method: "POST", headers: undefined }
  );
}
