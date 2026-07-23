import type { AuthSession } from "../auth/session";
import { jsonHeaders, requestJson } from "./client";
import type {
  CreateSpaceRequest,
  CreateSpaceResponse,
  CreateSpaceInvitationRequest,
  CreateSpaceInvitationResponse,
  DeleteSpaceResponse,
  OwnerTransferRequest,
  OwnerTransferResponse,
  RemoveSpaceMemberResponse,
  ResolveInvitationRequest,
  ResolveSpaceInvitationResponse,
  SpaceDetail,
  SpaceListResponse,
  SpaceMembersResponse,
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

export async function deleteSpace(_session: AuthSession, spaceId: string): Promise<DeleteSpaceResponse> {
  return requestJson<DeleteSpaceResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, { method: "DELETE", headers: undefined });
}

export async function fetchSpaceDetail(_session: AuthSession, spaceId: string): Promise<SpaceDetail> {
  return requestJson<SpaceDetail>(`/api/v1/spaces/${encodeURIComponent(spaceId)}`, { headers: undefined });
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
