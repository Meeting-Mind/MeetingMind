import type { AuthSession } from "../auth/session";
import { requestJson } from "./client";
import type { CalendarEventsResponse } from "../types";

export async function fetchCalendarEvents(
  _session: AuthSession,
  request: { from: string; to: string; spaceId?: string }
): Promise<CalendarEventsResponse> {
  const params = new URLSearchParams({ from: request.from, to: request.to });
  if (request.spaceId) {
    params.set("spaceId", request.spaceId);
  }
  return requestJson<CalendarEventsResponse>(`/api/v1/calendar/events?${params.toString()}`, { headers: undefined });
}
