import type { AuthSession } from "../auth/session";
import { requestJson } from "./client";
import type { WorkspaceData } from "../types";

export async function fetchLegacyWorkspaceSnapshot(_session: AuthSession): Promise<Partial<WorkspaceData>> {
  return requestJson<Partial<WorkspaceData>>("/api/workspace", {
    headers: undefined
  });
}
