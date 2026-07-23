import type { AuthSession } from "../auth/session";
import { requestJson } from "./client";
import type { DashboardSummaryResponse } from "../types";

export async function fetchDashboardSummary(_session: AuthSession): Promise<DashboardSummaryResponse> {
  return requestJson<DashboardSummaryResponse>("/api/v1/dashboard", { headers: undefined });
}
