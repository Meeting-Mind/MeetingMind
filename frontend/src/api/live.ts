import type { AuthSession } from "../auth/session";
import { requestJson } from "./client";

export type MeetingLiveKitTokenResponse = {
  serverUrl: string;
  participantToken: string;
  roomName: string;
  identity: string;
  name: string;
};

export async function fetchMeetingLiveKitToken(
  _session: AuthSession,
  meetingId: string
): Promise<MeetingLiveKitTokenResponse> {
  return requestJson<MeetingLiveKitTokenResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/livekit-token`,
    { method: "POST" }
  );
}
