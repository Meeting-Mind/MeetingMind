import type { WorkspaceDataSource } from "../../app/workspaceTypes";

export function isTargetDataReady(dataSource?: WorkspaceDataSource) {
  return !dataSource || dataSource === "workspace-api" || dataSource === "workspace-api-partial";
}
