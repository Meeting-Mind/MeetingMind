import type { AuthSession } from "../auth/session";
import { jsonHeaders, requestJson } from "./client";
import type {
  CreateDomainTermRequest,
  DeleteDomainTermResponse,
  DomainTermListResponse,
  DomainTermMutationResponse,
  GlossaryCategoryListResponse,
  TermExplanationResponse,
  UpdateDomainTermRequest
} from "../types";

export async function fetchGlossaryCategories(_session: AuthSession): Promise<GlossaryCategoryListResponse> {
  return requestJson<GlossaryCategoryListResponse>("/api/v1/glossary/categories", { headers: undefined });
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

export async function explainMeetingTerm(
  _session: AuthSession,
  meetingId: string,
  request: { term: string }
): Promise<TermExplanationResponse> {
  return requestJson<TermExplanationResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/terms/explain`,
    { method: "POST", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}
