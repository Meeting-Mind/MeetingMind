import type { AuthSession } from "../auth/session";
import { bffFetch } from "../auth/csrf";
import { jsonHeaders, requestJson } from "./client";
import type {
  CreateSpaceRequest,
  CreateSpaceResponse,
  CreateSpaceInvitationRequest,
  CreateSpaceInvitationResponse,
  DeleteSpaceResponse,
  LeaveSpaceResponse,
  OwnerTransferRequest,
  OwnerTransferResponse,
  RemoveSpaceMemberResponse,
  ResolveInvitationRequest,
  ResolveSpaceInvitationResponse,
  SpaceDetail,
  SpaceAiUsageResponse,
  SpaceListResponse,
  SpaceMembersResponse,
  SpaceInvitationListResponse,
  SpaceInvitationAdminResponse,
  UpdateSpaceRequest,
  UpdateSpaceMemberRoleRequest,
  UpdateSpaceMemberRoleResponse,
  UpdateSpaceResponse
} from "../types";

export async function fetchSpaces(_session: AuthSession): Promise<SpaceListResponse> {
  return requestJson<SpaceListResponse>("/api/v1/spaces", { headers: undefined });
}

export async function createSpace(_session: AuthSession, request: CreateSpaceRequest): Promise<CreateSpaceResponse> {
  return requestJson<CreateSpaceResponse>("/api/v1/spaces", {
    method: "POST", headers: jsonHeaders(), body: JSON.stringify(request)
  });
}

export async function updateSpace(_session: AuthSession, spaceId: string, request: UpdateSpaceRequest): Promise<UpdateSpaceResponse> {
  return requestJson<UpdateSpaceResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, {
    method: "PATCH", headers: jsonHeaders(), body: JSON.stringify(request)
  });
}

export async function uploadSpaceImage(_session: AuthSession, spaceId: string, file: File): Promise<string> {
  const body = new FormData();
  body.append("file", file);
  const response = await bffFetch(`/api/v1/spaces/${encodeURIComponent(spaceId)}/image`, {
    method: "POST",
    body
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `프로젝트 이미지 업로드 실패 (${response.status})`);
  }
  const payload = (await response.json()) as { imageUrl?: unknown };
  if (typeof payload.imageUrl !== "string" || !payload.imageUrl) {
    throw new Error("프로젝트 이미지 업로드 응답이 올바르지 않습니다.");
  }
  return payload.imageUrl;
}

export async function deleteSpace(_session: AuthSession, spaceId: string): Promise<DeleteSpaceResponse> {
  return requestJson<DeleteSpaceResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, { method: "DELETE", headers: undefined });
}

export async function leaveSpace(_session: AuthSession, spaceId: string): Promise<LeaveSpaceResponse> {
  return requestJson<LeaveSpaceResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/leave`, {
    method: "POST",
    headers: jsonHeaders()
  });
}

export async function fetchSpaceDetail(_session: AuthSession, spaceId: string): Promise<SpaceDetail> {
  return requestJson<SpaceDetail>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, { headers: undefined });
}

export async function fetchSpaceAiUsage(
  _session: AuthSession,
  spaceId: string,
  window: "day" | "week" | "month" = "month"
): Promise<SpaceAiUsageResponse> {
  return requestJson<SpaceAiUsageResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/ai/usage?window=${encodeURIComponent(window)}`,
    { headers: undefined }
  );
}

export async function fetchSpaceMembers(_session: AuthSession, spaceId: string): Promise<SpaceMembersResponse> {
  return requestJson<SpaceMembersResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/members`, { headers: undefined });
}

export async function createSpaceInvitation(
  _session: AuthSession,
  spaceId: string,
  request: CreateSpaceInvitationRequest
): Promise<CreateSpaceInvitationResponse> {
  return requestJson<CreateSpaceInvitationResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/invitations`, {
    method: "POST", headers: jsonHeaders(), body: JSON.stringify(request)
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
    { method: "POST", headers: jsonHeaders(), body: JSON.stringify(request) }
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
    { method: "POST", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}

export async function fetchPendingSpaceInvitations(_session: AuthSession): Promise<SpaceInvitationListResponse> {
  return requestJson<SpaceInvitationListResponse>("/api/v1/spaces/invitations/pending", { headers: undefined });
}

export async function acceptPendingSpaceInvitation(_session: AuthSession, spaceId: string, invitationId: string): Promise<ResolveSpaceInvitationResponse> {
  return requestJson<ResolveSpaceInvitationResponse>(
    `/api/v1/spaces/invitations/${encodeURIComponent(spaceId)}/${encodeURIComponent(invitationId)}/accept`,
    { method: "POST", headers: undefined }
  );
}

export async function declinePendingSpaceInvitation(_session: AuthSession, spaceId: string, invitationId: string): Promise<ResolveSpaceInvitationResponse> {
  return requestJson<ResolveSpaceInvitationResponse>(
    `/api/v1/spaces/invitations/${encodeURIComponent(spaceId)}/${encodeURIComponent(invitationId)}/decline`,
    { method: "POST", headers: undefined }
  );
}

export async function fetchSpaceInvitations(_session: AuthSession, spaceId: string): Promise<SpaceInvitationAdminResponse> {
  return requestJson<SpaceInvitationAdminResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/invitations`, { headers: undefined });
}

export async function resendSpaceInvitation(_session: AuthSession, spaceId: string, invitationId: string): Promise<CreateSpaceInvitationResponse> {
  return requestJson<CreateSpaceInvitationResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/invitations/${encodeURIComponent(invitationId)}/resend`, { method: "POST", headers: undefined });
}

export async function cancelSpaceInvitation(_session: AuthSession, spaceId: string, invitationId: string): Promise<void> {
  await requestJson<void>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/invitations/${encodeURIComponent(invitationId)}`, { method: "DELETE", headers: undefined });
}

export async function updateSpaceMemberRole(
  _session: AuthSession,
  spaceId: string,
  memberId: string,
  request: UpdateSpaceMemberRoleRequest
): Promise<UpdateSpaceMemberRoleResponse> {
  return requestJson<UpdateSpaceMemberRoleResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/members/${encodeURIComponent(memberId)}`,
    { method: "PATCH", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}

export async function removeSpaceMember(_session: AuthSession, spaceId: string, memberId: string): Promise<RemoveSpaceMemberResponse> {
  return requestJson<RemoveSpaceMemberResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/members/${encodeURIComponent(memberId)}`,
    { method: "DELETE", headers: undefined }
  );
}

export async function transferSpaceOwner(
  _session: AuthSession,
  spaceId: string,
  request: OwnerTransferRequest
): Promise<OwnerTransferResponse> {
  return requestJson<OwnerTransferResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/owner-transfer`, {
    method: "POST",
    headers: jsonHeaders(),
    body: JSON.stringify({
      targetMemberId: request.targetMemberId,
      previousOwnerRole: request.previousOwnerRole,
      confirmationText: request.confirmation
    })
  });
}
