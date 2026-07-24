import type { AuthSession } from "../auth/session";
import { jsonHeaders, requestJson } from "./client";
import type {
  CreateProjectKnowledgeRequest,
  DeleteProjectKnowledgeResponse,
  KnowledgeGraphResponse,
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

export interface KnowledgeGraphFilters {
  meetingIds?: string[];
  nodeTypes?: string[];
}

export async function fetchKnowledgeGraph(
  _session: AuthSession,
  spaceId: string,
  filters: KnowledgeGraphFilters = {}
): Promise<KnowledgeGraphResponse> {
  const params = new URLSearchParams();
  filters.meetingIds?.forEach((id) => params.append("meetingIds", id));
  filters.nodeTypes?.forEach((type) => params.append("nodeTypes", type));
  const query = params.toString();
  return requestJson<KnowledgeGraphResponse>(`${`/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/graph`}${query ? `?${query}` : ""}`, {
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

export async function restoreProjectKnowledge(
  _session: AuthSession,
  spaceId: string,
  knowledgeId: string
): Promise<DeleteProjectKnowledgeResponse> {
  return requestJson<DeleteProjectKnowledgeResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/${encodeURIComponent(knowledgeId)}/restore`,
    { method: "POST", headers: jsonHeaders() }
  );
}
