import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { fetchDialogue } from "./api";
import { buildTranscriptEntries } from "./selectors";

/** 회의 전사 폴링 간격. WebSocket 전환 전까지의 임시 값. */
export const DIALOGUE_POLL_INTERVAL_MS = 2500;

interface UseMeetingDialogueOptions {
  enabled?: boolean;
  /** false면 폴링을 멈추고 캐시만 사용한다 (종료된 회의 등). */
  live?: boolean;
}

/**
 * 회의 dialogue를 조회한다.
 *
 * 기존에는 MeetingTranscript와 LiveMeeting이 각각 setInterval로 같은 엔드포인트를
 * 2.5초마다 호출했다. 이제 두 화면이 같은 queryKey를 공유하므로 요청이 중복되지 않고,
 * 화면 재진입 시 캐시가 즉시 표시된다.
 *
 * WebSocket 전환 시 이 훅의 내부만 교체하면 되고 호출부는 그대로 둔다.
 */
export function useMeetingDialogueQuery(meetingId: string, options: UseMeetingDialogueOptions = {}) {
  const { enabled = true, live = true } = options;
  const query = useQuery({
    queryKey: ["transcription", "dialogue", meetingId],
    queryFn: () => fetchDialogue(meetingId),
    enabled: enabled && meetingId.length > 0,
    refetchInterval: live ? DIALOGUE_POLL_INTERVAL_MS : false,
    // 폴링 중 이전 데이터를 유지해 화면이 깜빡이지 않게 한다.
    placeholderData: (previous) => previous,
    staleTime: 0
  });

  const entries = useMemo(() => buildTranscriptEntries(query.data), [query.data]);

  return {
    ...query,
    entries,
    status: query.data?.status ?? null
  };
}
