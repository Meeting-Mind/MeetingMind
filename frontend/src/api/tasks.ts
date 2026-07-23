import type { AuthSession } from "../auth/session";
import { jsonHeaders, requestJson } from "./client";
import type {
  ConfirmTaskCandidateRequest,
  ConfirmTaskCandidateResponse,
  CreateTaskCardRequest,
  CreateTaskCardResponse,
  DeleteTaskCardResponse,
  TaskCandidateGenerationResponse,
  TaskCandidateSummary,
  TaskCandidatesResponse,
  TaskListResponse,
  UpdateTaskCardRequest,
  UpdateTaskCardResponse
} from "../types";

export async function extractTaskCandidates(_session: AuthSession, meetingId: string): Promise<TaskCandidateGenerationResponse> {
  return requestJson<TaskCandidateGenerationResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/task-candidates/generate`,
    { method: "POST", headers: undefined }
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
    { method: "POST", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}

export async function dismissTaskCandidate(
  _session: AuthSession,
  meetingId: string,
  candidateId: string
): Promise<TaskCandidateSummary> {
  return requestJson<TaskCandidateSummary>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/task-candidates/${encodeURIComponent(candidateId)}/dismiss`,
    { method: "POST", headers: jsonHeaders() }
  );
}

export async function fetchTasks(_session: AuthSession, spaceId: string): Promise<TaskListResponse> {
  return requestJson<TaskListResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks`, {
    headers: undefined
  });
}

export async function createTask(
  _session: AuthSession,
  spaceId: string,
  request: CreateTaskCardRequest
): Promise<CreateTaskCardResponse> {
  return requestJson<CreateTaskCardResponse>(`/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks`, {
    method: "POST", headers: jsonHeaders(), body: JSON.stringify(request)
  });
}

export async function updateTask(
  _session: AuthSession,
  spaceId: string,
  taskId: string,
  request: UpdateTaskCardRequest
): Promise<UpdateTaskCardResponse> {
  return requestJson<UpdateTaskCardResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks/${encodeURIComponent(taskId)}`,
    { method: "PATCH", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}

export async function deleteTask(_session: AuthSession, spaceId: string, taskId: string): Promise<DeleteTaskCardResponse> {
  return requestJson<DeleteTaskCardResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/tasks/${encodeURIComponent(taskId)}`,
    { method: "DELETE", headers: undefined }
  );
}
