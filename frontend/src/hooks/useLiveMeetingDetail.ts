import { useEffect, useState } from "react";
import { fetchMeetingDetail, fetchMeetingParticipants } from "../api/workspace";
import type { AuthSession } from "../auth/session";
import type { MeetingRole, WorkspaceData } from "../types";

const ACCESS_LABELS: Record<MeetingRole, string> = {
  HOST: "편집 가능",
  EDITOR: "발표 가능",
  VIEWER: "참여 가능"
};

export function formatStartsAt(scheduledAt: string): string {
  const date = new Date(scheduledAt);
  if (Number.isNaN(date.getTime())) {
    return scheduledAt;
  }

  const parts = new Intl.DateTimeFormat("en-CA", {
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone: "Asia/Seoul"
  }).formatToParts(date);
  const hour = Number(parts.find((part) => part.type === "hour")?.value);
  const minute = parts.find((part) => part.type === "minute")?.value;

  if (!Number.isInteger(hour) || !minute) {
    return scheduledAt;
  }

  const period = hour < 12 ? "오전" : "오후";
  const displayHour = hour % 12 || 12;
  return `${period} ${String(displayHour).padStart(2, "0")}:${minute}`;
}

export type LiveMeetingDetailResult = {
  liveMeeting: WorkspaceData["liveMeeting"];
  error: string | null;
};

// meetingId가 있으면 실제 회의 데이터로 fallback(legacy /api/workspace 스냅샷)을 덮어씌운다.
// 실패 시 fallback을 유지하되 error를 채워, 호출부가 "임시 데이터 표시 중"임을 알릴 수 있게 한다.
export function useLiveMeetingDetail(
  session: AuthSession,
  meetingId: string | null,
  fallback: WorkspaceData["liveMeeting"]
): LiveMeetingDetailResult {
  const [liveMeeting, setLiveMeeting] = useState(fallback);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!meetingId) {
      setLiveMeeting(fallback);
      setError(null);
      return;
    }

    let active = true;

    Promise.all([fetchMeetingDetail(session, meetingId), fetchMeetingParticipants(session, meetingId)])
      .then(([detail, participantsResponse]) => {
        if (!active) {
          return;
        }

        const accessMembers = participantsResponse.participants.map((participant) => ({
          name: participant.displayName || participant.email || participant.userId,
          role: participant.role,
          access: ACCESS_LABELS[participant.role] ?? "참여 가능",
          note: participant.accessStatus === "ACTIVE" ? "회의 참여 중" : "접근 제한됨"
        }));

        setLiveMeeting((previous) => ({
          ...previous,
          startsAt: formatStartsAt(detail.scheduledAt),
          overview: { ...previous.overview, title: detail.title },
          accessMembers,
          participants: accessMembers.map((member) => member.name)
        }));
        setError(null);
      })
      .catch((cause: unknown) => {
        if (!active) {
          return;
        }
        const message = cause instanceof Error ? cause.message : "회의 정보를 불러오지 못했습니다.";
        setError(`실제 회의 정보를 불러오지 못해 임시 데이터를 표시 중입니다: ${message}`);
      });

    return () => {
      active = false;
    };
  }, [session, meetingId]);

  return { liveMeeting, error };
}
