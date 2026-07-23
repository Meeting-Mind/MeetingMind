import { useEffect, useState } from "react";
import { fetchMeetingDetail } from "../api/meetings";
import { fetchMeetingParticipants } from "../api/meetingAccess";
import type { AuthSession } from "../auth/session";
import type { MeetingDetailResponse, MeetingParticipantSummary } from "../types";

export type MeetingContextState = {
  status: "loading" | "ready" | "error";
  detail: MeetingDetailResponse | null;
  participants: MeetingParticipantSummary[];
  error: string | null;
};

export function useMeetingContext(
  session: AuthSession | null,
  meetingId: string,
  expectedSpaceId: string
): MeetingContextState {
  const [state, setState] = useState<MeetingContextState>({
    status: "loading",
    detail: null,
    participants: [],
    error: null
  });

  useEffect(() => {
    if (!session || !meetingId || !expectedSpaceId) {
      setState({
        status: "error",
        detail: null,
        participants: [],
        error: "회의를 확인하려면 로그인과 올바른 회의 주소가 필요합니다."
      });
      return;
    }

    let active = true;
    setState({ status: "loading", detail: null, participants: [], error: null });

    Promise.all([fetchMeetingDetail(session, meetingId), fetchMeetingParticipants(session, meetingId)])
      .then(([detail, participantsResponse]) => {
        if (!active) {
          return;
        }

        if (detail.spaceId !== expectedSpaceId) {
          throw new Error("회의가 요청한 프로젝트에 속하지 않습니다.");
        }

        setState({
          status: "ready",
          detail,
          participants: participantsResponse.participants.length ? participantsResponse.participants : detail.participants,
          error: null
        });
      })
      .catch((cause: unknown) => {
        if (!active) {
          return;
        }
        setState({
          status: "error",
          detail: null,
          participants: [],
          error: cause instanceof Error ? cause.message : "회의 정보를 불러오지 못했습니다."
        });
      });

    return () => {
      active = false;
    };
  }, [expectedSpaceId, meetingId, session]);

  return state;
}
