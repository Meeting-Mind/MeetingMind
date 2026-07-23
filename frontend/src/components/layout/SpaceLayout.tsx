import type { ReactNode } from "react";
import { TargetDataGate } from "./TargetDataGate";
import { AppShell } from "./AppShell";
import { WorkspaceSidebar } from "../WorkspaceSidebar";
import type { WorkspaceDataSource } from "../../app/workspaceTypes";

export function SpaceLayout({
  children,
  projectName,
  spaceId,
  onCreateProject,
  contentClassName = "project-overview-main",
  activeItem = "project",
  dataSource
}: {
  children: ReactNode;
  projectName: string;
  spaceId: string;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  contentClassName?: string;
  activeItem?: "project" | "calendar" | "meetings" | "tasks" | "ai" | "knowledge" | "members" | "terms" | "settings";
  dataSource?: WorkspaceDataSource;
}) {
  return (
    <TargetDataGate contentClassName={contentClassName} dataSource={dataSource} onCreateProject={onCreateProject}>
      <AppShell
        contentClassName={contentClassName}
        sidebar={(
          <WorkspaceSidebar
            activeItem={activeItem}
            contextOverride={projectName}
            mode="project"
            onCreateProject={onCreateProject}
            projectName={projectName}
            spaceId={spaceId}
          />
        )}
      >
        {children}
      </AppShell>
    </TargetDataGate>
  );
}
