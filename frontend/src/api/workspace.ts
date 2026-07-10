import { buildAuthHeaders, type AuthSession } from "../auth/session";
import type {
  CreateMeetingRequest,
  CreateMeetingResponse,
  CreateSpaceRequest,
  CreateSpaceResponse,
  MeetingListResponse,
  SpaceDetail,
  SpaceListResponse,
  WorkspaceData
} from "../types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.trim() || "";

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

export async function fetchSpaceDetail(session: AuthSession, spaceId: string): Promise<SpaceDetail> {
  return requestJson<SpaceDetail>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, {
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

function jsonHeaders(session: AuthSession): HeadersInit {
  return {
    ...buildAuthHeaders(session),
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

async function readErrorMessage(response: Response) {
  try {
    const payload = (await response.json()) as { message?: string };
    return payload.message;
  } catch {
    return response.text();
  }
}
