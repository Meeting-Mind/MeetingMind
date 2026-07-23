import type { ReactNode } from "react";
import { DataState } from "../common/DataState";
import { AppShell } from "./AppShell";
import { WorkspaceSidebar } from "../WorkspaceSidebar";
import type { WorkspaceDataSource } from "../../app/workspaceTypes";
import { isTargetDataReady } from "./targetDataGateModel";

export function TargetDataGate({
  children,
  contentClassName,
  dataSource,
  onCreateProject
}: {
  children: ReactNode;
  contentClassName: string;
  dataSource?: WorkspaceDataSource;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
}) {
  if (isTargetDataReady(dataSource)) {
    return <>{children}</>;
  }

  const isLoading = dataSource === "loading";
  return (
    <AppShell
      contentClassName={contentClassName}
      sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
    >
      <DataState
        actionLabel={isLoading ? undefined : "다시 시도"}
        onAction={isLoading ? undefined : () => window.location.reload()}
        state={isLoading ? "loading" : "error"}
        title={isLoading ? "프로젝트 데이터를 확인하는 중입니다" : "프로젝트 데이터를 불러오지 못했습니다"}
        description={isLoading ? "임시 데이터를 표시하지 않고 접근 범위를 확인하고 있습니다." : "임시 데이터는 표시하지 않습니다. 연결 상태를 확인한 뒤 다시 시도해 주세요."}
      />
    </AppShell>
  );
}
