import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ConfirmDialog } from "../components/common/ConfirmDialog";
import { DataState } from "../components/common/DataState";
import { PageHeader } from "../components/common/PageHeader";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { SpaceLayout } from "../components/layout/SpaceLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { TeamMember, WorkspaceDataSource } from "../app/workspaceTypes";
import type { WorkspaceData } from "../types";

export function ProjectSettingsPage({
  currentUserEmail,
  meetingMutationError,
  meetingMutationLoading = false,
  onDeleteProject,
  onCreateProject,
  onUpdateProject,
  projectMembers,
  spaces,
  workspaceDataSource
}: {
  currentUserEmail: string;
  meetingMutationError?: string;
  meetingMutationLoading?: boolean;
  onDeleteProject?: (spaceId: string) => Promise<boolean>;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  onUpdateProject?: (spaceId: string, payload: { name: string; description: string }) => Promise<boolean>;
  projectMembers: Record<string, TeamMember[]>;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
}) {
  const navigate = useNavigate();
  const { spaceId = "" } = useParams<{ spaceId: string }>();
  const selectedSpace = spaces.find((space) => space.id === spaceId);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [saveError, setSaveError] = useState("");
  const [saveNotice, setSaveNotice] = useState("");
  const [deleteOpen, setDeleteOpen] = useState(false);

  useEffect(() => {
    if (!selectedSpace) {
      return;
    }
    setName(selectedSpace.name);
    setDescription(selectedSpace.description ?? "");
  }, [selectedSpace]);

  useEffect(() => {
    document.body.className = "app-theme project-settings-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="project-settings-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState
          actionLabel="프로젝트 목록으로"
          onAction={() => navigate("/spaces")}
          state="notFound"
          title="프로젝트를 찾을 수 없습니다"
          description="프로젝트 목록에서 접근 가능한 프로젝트를 선택해 주세요."
        />
      </AppShell>
    );
  }

  const members = projectMembers[selectedSpace.name] ?? [];
  const selectedSpaceId = selectedSpace.id;
  const currentMember = members.find((member) => member.email === currentUserEmail) ?? null;
  const canManage = currentMember?.spaceRole === "OWNER" || currentMember?.spaceRole === "ADMIN";
  const canDelete = currentMember?.spaceRole === "OWNER";

  async function handleSave(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canManage || !onUpdateProject || !name.trim()) {
      return;
    }
    setSaveError("");
    setSaveNotice("");
    const updated = await onUpdateProject(selectedSpaceId, { name: name.trim(), description: description.trim() });
    if (updated) {
      setSaveNotice("프로젝트 정보가 저장되었습니다.");
    } else {
      setSaveError("프로젝트 정보를 저장하지 못했습니다.");
    }
  }

  async function handleDelete() {
    if (!canDelete || !onDeleteProject) {
      return;
    }
    const deleted = await onDeleteProject(selectedSpaceId);
    if (deleted) {
      navigate("/spaces");
    }
  }

  return (
    <SpaceLayout
      activeItem="settings"
      contentClassName="project-settings-main"
      dataSource={workspaceDataSource}
      onCreateProject={onCreateProject}
      projectName={selectedSpace.name}
      spaceId={selectedSpace.id}
    >
      <PageHeader
        actions={(
          <Link className="mm-common-button mm-common-button--secondary" to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>
            프로젝트 홈
          </Link>
        )}
        breadcrumb={(
          <>
            <Link to="/spaces">프로젝트 목록</Link>
            <span aria-hidden="true">/</span>
            <Link to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>{selectedSpace.name}</Link>
            <span aria-hidden="true">/</span>
            <strong>Settings</strong>
          </>
        )}
        description="프로젝트 기본 정보와 권한을 관리합니다. 삭제처럼 되돌릴 수 없는 작업은 아래 Danger zone에서 별도로 확인합니다."
        eyebrow="Project settings"
        meta={currentMember ? <RoleBadge role={currentMember.spaceRole} scope="space" /> : null}
        title="프로젝트 설정"
      />

      <div className="project-settings-layout">
        <form aria-labelledby="project-settings-info-title" className="project-settings-surface" onSubmit={(event) => void handleSave(event)}>
          <div className="project-settings-surface-header">
            <div>
              <p className="project-home-section-kicker">Identity</p>
              <h2 id="project-settings-info-title">프로젝트 정보</h2>
            </div>
            <StatusBadge context="generic" label={canManage ? "관리 가능" : "읽기 전용"} status={canManage ? "ACTIVE" : "INACTIVE"} />
          </div>
          <label className="project-settings-field">
            <span>프로젝트 이름</span>
            <input disabled={!canManage || meetingMutationLoading} onChange={(event) => setName(event.target.value)} required type="text" value={name} />
          </label>
          <label className="project-settings-field">
            <span>설명</span>
            <textarea disabled={!canManage || meetingMutationLoading} onChange={(event) => setDescription(event.target.value)} rows={5} value={description} />
          </label>
          {!canManage ? <p className="project-settings-permission">프로젝트 정보 수정은 OWNER 또는 ADMIN만 할 수 있습니다.</p> : null}
          {saveError || meetingMutationError ? <p aria-live="polite" className="project-settings-error">{saveError || meetingMutationError}</p> : null}
          {saveNotice ? <p aria-live="polite" className="project-settings-notice">{saveNotice}</p> : null}
          <div className="project-settings-actions">
            <button className="mm-common-button mm-common-button--primary" disabled={!canManage || meetingMutationLoading || !name.trim()} type="submit">
              {meetingMutationLoading ? "저장 중..." : "변경 저장"}
            </button>
          </div>
        </form>

        <section aria-labelledby="project-settings-access-title" className="project-settings-surface">
          <div className="project-settings-surface-header">
            <div>
              <p className="project-home-section-kicker">Access</p>
              <h2 id="project-settings-access-title">권한과 소유권</h2>
            </div>
          </div>
          <p className="project-settings-copy">멤버 역할은 프로젝트 권한을, 회의 역할은 개별 회의 접근을 결정합니다. 두 범위는 서로 합쳐지지 않습니다.</p>
          <Link className="project-settings-link" to={`/spaces/${encodeURIComponent(selectedSpace.id)}/members`}>멤버와 소유권 관리 →</Link>
        </section>

        <section aria-labelledby="project-settings-danger-title" className="project-settings-surface project-settings-surface--danger">
          <div className="project-settings-surface-header">
            <div>
              <p className="project-home-section-kicker">Danger zone</p>
              <h2 id="project-settings-danger-title">프로젝트 삭제</h2>
            </div>
            <StatusBadge context="generic" label={canDelete ? "OWNER만 가능" : "차단됨"} status={canDelete ? "CANCELED" : "INACTIVE"} />
          </div>
          <p className="project-settings-copy">삭제하면 프로젝트와 연결된 업무 화면에서 더 이상 이 프로젝트를 열 수 없습니다. 이 작업은 되돌릴 수 없습니다.</p>
          <button className="mm-common-button mm-common-button--danger" disabled={!canDelete || meetingMutationLoading} onClick={() => setDeleteOpen(true)} type="button">프로젝트 삭제</button>
          {!canDelete ? <p className="project-settings-permission">프로젝트 삭제는 OWNER만 할 수 있습니다.</p> : null}
        </section>
      </div>

      <ConfirmDialog
        busy={meetingMutationLoading}
        confirmLabel="삭제하기"
        description={`프로젝트 '${selectedSpace.name}'를 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`}
        onCancel={() => setDeleteOpen(false)}
        onConfirm={() => void handleDelete()}
        open={deleteOpen}
        title="프로젝트를 삭제할까요?"
      />
    </SpaceLayout>
  );
}
