import type { AuthSession } from "../auth/session";
import { jsonHeaders, requestBlob, requestJson } from "./client";
import type {
  ConfirmReportResponse,
  ReportCandidateResponse,
  ReportDetailResponse,
  ReportDownloadFormat,
  ReportListResponse,
  RestoreReportResponse,
  UpdateReportRequest,
  UpdateReportResponse
} from "../types";

export async function generateReportCandidate(_session: AuthSession, meetingId: string): Promise<ReportCandidateResponse> {
  return requestJson<ReportCandidateResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/generate`, {
    method: "POST",
    headers: undefined
  });
}

export async function editMeetingReportWithAi(
  _session: AuthSession,
  meetingId: string,
  reportId: string,
  instruction: string
): Promise<ReportCandidateResponse> {
  return requestJson<ReportCandidateResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}/ai-edits`,
    { method: "POST", headers: jsonHeaders(), body: JSON.stringify({ instruction }) }
  );
}

export async function fetchMeetingReports(
  _session: AuthSession,
  meetingId: string,
  status?: "CANDIDATE" | "DRAFT" | "CONFIRMED"
): Promise<ReportListResponse> {
  const params = new URLSearchParams();
  if (status) {
    params.set("status", status);
  }
  const query = params.toString() ? `?${params.toString()}` : "";
  return requestJson<ReportListResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/reports${query}`, {
    headers: undefined
  });
}

export async function confirmMeetingReport(
  _session: AuthSession,
  meetingId: string,
  reportId: string
): Promise<ConfirmReportResponse> {
  return requestJson<ConfirmReportResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}/confirm`,
    { method: "POST", headers: undefined }
  );
}

export async function updateMeetingReport(
  _session: AuthSession,
  meetingId: string,
  reportId: string,
  request: UpdateReportRequest
): Promise<UpdateReportResponse> {
  return requestJson<UpdateReportResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}`,
    { method: "PATCH", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}

export async function downloadMeetingReport(
  _session: AuthSession,
  meetingId: string,
  reportId: string,
  format: ReportDownloadFormat
): Promise<Blob> {
  return requestBlob(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}/download?format=${encodeURIComponent(format)}`,
    { headers: undefined }
  );
}

export async function fetchMeetingReportDetail(
  _session: AuthSession,
  meetingId: string,
  reportId: string
): Promise<ReportDetailResponse> {
  return requestJson<ReportDetailResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}`,
    { headers: undefined }
  );
}

export async function restoreMeetingReport(
  _session: AuthSession,
  meetingId: string,
  reportId: string
): Promise<RestoreReportResponse> {
  return requestJson<RestoreReportResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/reports/${encodeURIComponent(reportId)}/restore`,
    { method: "POST", headers: jsonHeaders() }
  );
}
