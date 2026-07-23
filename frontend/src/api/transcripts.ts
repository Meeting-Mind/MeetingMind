import type { AuthSession } from "../auth/session";
import { jsonHeaders, requestJson } from "./client";
import type {
  MeetingDialogueResponse,
  MeetingTranscriptStatusResponse,
  MeetingTranscriptionStartResponse,
  StartMeetingTranscriptionRequest
} from "../types";

export async function startMeetingTranscription(
  _session: AuthSession,
  meetingId: string,
  request: StartMeetingTranscriptionRequest
): Promise<MeetingTranscriptionStartResponse> {
  return requestJson<MeetingTranscriptionStartResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/transcription/start`,
    { method: "POST", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}

export async function stopMeetingTranscription(
  _session: AuthSession,
  meetingId: string,
  sessionId: string
): Promise<MeetingTranscriptStatusResponse> {
  return requestJson<MeetingTranscriptStatusResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/transcription/${encodeURIComponent(sessionId)}/stop`,
    { method: "POST", headers: undefined }
  );
}

export async function stopActiveMeetingTranscription(
  _session: AuthSession,
  meetingId: string
): Promise<MeetingTranscriptStatusResponse> {
  return requestJson<MeetingTranscriptStatusResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/transcription/stop`,
    { method: "POST", headers: undefined }
  );
}

export async function fetchMeetingDialogue(_session: AuthSession, meetingId: string): Promise<MeetingDialogueResponse> {
  return requestJson<MeetingDialogueResponse>(`/api/v1/meetings/${encodeURIComponent(meetingId)}/dialogue`, {
    headers: undefined
  });
}
