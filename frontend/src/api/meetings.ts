import type { AuthSession } from "../auth/session";
import { jsonHeaders, requestJson } from "./client";
import type {
  CreateMeetingRequest,
  CreateMeetingResponse,
  CreateInstantMeetingResponse,
  DeleteMeetingResponse,
  MeetingDetailResponse,
  MeetingListResponse,
  UpdateMeetingRequest,
  UpdateMeetingResponse
} from "../types";

type RawMeetingDetailResponse = Omit<MeetingDetailResponse, "participants"> & {
  participants?: Array<{
    participantId?: string;
    id?: string;
    userId: string;
    displayName?: string | null;
    role: MeetingDetailResponse["participants"][number]["role"];
    participantType: MeetingDetailResponse["participants"][number]["participantType"];
    accessStatus: MeetingDetailResponse["participants"][number]["accessStatus"];
  }>;
};

export async function fetchMeetings(_session: AuthSession, spaceId: string): Promise<MeetingListResponse> {
  return requestJson<MeetingListResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/meetings`, {
    headers: undefined
  });
}

export async function fetchAccessibleMeetings(_session: AuthSession): Promise<MeetingListResponse> {
  return requestJson<MeetingListResponse>("/api/v1/meetings", { headers: undefined });
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

export async function createInstantMeeting(
  _session: AuthSession,
  spaceId: string
): Promise<CreateInstantMeetingResponse> {
  return requestJson<CreateInstantMeetingResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/instant-meetings`, {
    method: "POST",
    headers: jsonHeaders()
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
  const payload = await requestJson<RawMeetingDetailResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}`, {
    headers: undefined
  });

  return {
    ...payload,
    participants: (payload.participants ?? []).map((participant) => ({
      id: participant.id ?? participant.participantId ?? participant.userId,
      userId: participant.userId,
      displayName: participant.displayName ?? null,
      role: participant.role,
      participantType: participant.participantType,
      accessStatus: participant.accessStatus
    }))
  };
}
