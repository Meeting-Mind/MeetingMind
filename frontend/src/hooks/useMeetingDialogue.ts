import { useEffect, useState } from "react";
import { fetchMeetingDialogue } from "../api/transcripts";
import type { AuthSession } from "../auth/session";
import type { MeetingDialogueResponse } from "../types";

export type MeetingDialogueState = {
  status: "loading" | "ready" | "error";
  data: MeetingDialogueResponse | null;
  error: string | null;
};

export function useMeetingDialogue(session: AuthSession | null, meetingId: string): MeetingDialogueState {
  const [state, setState] = useState<MeetingDialogueState>({
    status: "loading",
    data: null,
    error: null
  });

  useEffect(() => {
    if (!session || !meetingId) {
      setState({
        status: "error",
        data: null,
        error: "전사 기록을 확인하려면 로그인과 올바른 회의 주소가 필요합니다."
      });
      return;
    }

    let active = true;
    setState({ status: "loading", data: null, error: null });

    const poll = () => {
      fetchMeetingDialogue(session, meetingId)
        .then((data) => {
          if (!active) {
            return;
          }
          setState((previous) => {
            const previousData = previous.data;
            const shouldHoldPartials =
              previousData &&
              previousData.partials.length > 0 &&
              data.partials.length === 0 &&
              data.rows.length === previousData.rows.length &&
              data.status === "PROCESSING";

            return {
              status: "ready",
              data: shouldHoldPartials
                ? { ...data, partials: previousData.partials }
                : data,
              error: null
            };
          });
        })
        .catch((cause: unknown) => {
          if (!active) {
            return;
          }
          setState((previous) => ({
            status: "error",
            data: previous.data,
            error: cause instanceof Error ? cause.message : "전사 기록을 불러오지 못했습니다."
          }));
        });
    };

    poll();
    const intervalId = window.setInterval(poll, 2500);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, [meetingId, session]);

  return state;
}
