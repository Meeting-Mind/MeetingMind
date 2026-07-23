import type { AuthSession } from "../auth/session";
import { requestJson, jsonHeaders } from "./client";
import type { AiChatResponse, ProjectAiHistoryResponse } from "../types";

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
