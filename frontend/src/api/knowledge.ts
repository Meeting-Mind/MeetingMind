import type { AuthSession } from "../auth/session";
import { jsonHeaders, requestJson } from "./client";
import type {
  CreateProjectKnowledgeRequest,
  DeleteProjectKnowledgeResponse,
  ProjectKnowledgeDetailResponse,
  ProjectKnowledgeListResponse,
  ProjectKnowledgeMutationResponse,
  UpdateProjectKnowledgeRequest
} from "../types";

export async function fetchProjectKnowledge(_session: AuthSession, spaceId: string): Promise<ProjectKnowledgeListResponse> {
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
    method: "POST", headers: jsonHeaders(), body: JSON.stringify(request)
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
    { method: "PATCH", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}

export async function deleteProjectKnowledge(
  _session: AuthSession,
  spaceId: string,
  knowledgeId: string
): Promise<DeleteProjectKnowledgeResponse> {
  return requestJson<DeleteProjectKnowledgeResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/${encodeURIComponent(knowledgeId)}`,
    { method: "DELETE", headers: jsonHeaders() }
  );
}
