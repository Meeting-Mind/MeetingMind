import { requestJson } from "../../api/client";
import type { MeetingDialogueResponse } from "../../types";

/**
 * transcription feature 전용 조회 API.
 * BFF 쿠키 세션을 쓰므로 AuthSession 파라미터를 받지 않는다.
 */
export async function fetchDialogue(meetingId: string): Promise<MeetingDialogueResponse> {
  return requestJson<MeetingDialogueResponse>(
    `/api/v1/meetings/${encodeURIComponent(meetingId)}/dialogue`,
    { headers: undefined }
  );
}
