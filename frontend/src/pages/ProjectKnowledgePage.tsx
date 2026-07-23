import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import { DataState } from "../components/common/DataState";
import { ConfirmDialog } from "../components/common/ConfirmDialog";
import { PageHeader } from "../components/common/PageHeader";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { SpaceLayout } from "../components/layout/SpaceLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { CreateProjectKnowledgeRequest, ProjectKnowledgeItem, ProjectKnowledgeType, UpdateProjectKnowledgeRequest, WorkspaceData } from "../types";
import type { TeamMember, WorkspaceDataSource } from "../app/workspaceTypes";

const typeLabels: Record<ProjectKnowledgeType, string> = {
  decision: "결정",
  external: "외부 자료",
  manual: "직접 등록",
  report: "회의록"
};

const embeddingLabels: Record<ProjectKnowledgeItem["embeddingStatus"], string> = {
  COMPLETED: "검색 가능",
  FAILED: "처리 실패",
  PENDING: "처리 대기",
  PROCESSING: "처리 중"
};

export function ProjectKnowledgePage({
  currentUserEmail,
  projectKnowledge,
  projectMembers,
  session,
  spaces,
  workspaceDataSource,
  onCreateProject,
  onCreateProjectKnowledge,
  onUpdateProjectKnowledge,
  onDeleteProjectKnowledge,
  meetingMutationLoading = false,
  meetingMutationError = ""
}: {
  currentUserEmail: string;
  projectKnowledge: Record<string, ProjectKnowledgeItem[]>;
  projectMembers: Record<string, TeamMember[]>;
  session: AuthSession | null;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  onCreateProjectKnowledge?: (spaceId: string, request: CreateProjectKnowledgeRequest) => Promise<boolean>;
  onUpdateProjectKnowledge?: (spaceId: string, knowledgeId: string, request: UpdateProjectKnowledgeRequest) => Promise<boolean>;
  onDeleteProjectKnowledge?: (spaceId: string, knowledgeId: string) => Promise<boolean>;
  meetingMutationLoading?: boolean;
  meetingMutationError?: string;
}) {
  const { spaceId = "" } = useParams<{ spaceId: string }>();
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | ProjectKnowledgeItem["embeddingStatus"]>("ALL");
  const [formOpen, setFormOpen] = useState(false);
  const [editingItem, setEditingItem] = useState<ProjectKnowledgeItem | null>(null);
  const [knowledgeTitle, setKnowledgeTitle] = useState("");
  const [knowledgeContent, setKnowledgeContent] = useState("");
  const [knowledgeType, setKnowledgeType] = useState<ProjectKnowledgeType>("manual");
  const [deleteCandidate, setDeleteCandidate] = useState<ProjectKnowledgeItem | null>(null);
  const [knowledgeError, setKnowledgeError] = useState("");
  const selectedSpace = spaces.find((space) => space.id === spaceId);
  const items = selectedSpace ? projectKnowledge[selectedSpace.id] ?? [] : [];
  const members = selectedSpace ? projectMembers[selectedSpace.name] ?? [] : [];
  const currentMember = members.find((member) => member.email === currentUserEmail);
  const canManageKnowledge = currentMember?.spaceRole === "OWNER" || currentMember?.spaceRole === "ADMIN";

  useEffect(() => {
    document.body.className = "app-theme project-knowledge-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="project-knowledge-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState state="notFound" title="프로젝트를 찾을 수 없습니다" description="프로젝트 목록에서 접근 가능한 프로젝트를 선택해 주세요." />
      </AppShell>
    );
  }

  const normalizedQuery = query.trim().toLowerCase();
  const visibleItems = items.filter((item) => {
    const matchesStatus = statusFilter === "ALL" || item.embeddingStatus === statusFilter;
    const matchesQuery = !normalizedQuery || [item.title, item.contentPreview, typeLabels[item.type]].join(" ").toLowerCase().includes(normalizedQuery);
    return matchesStatus && matchesQuery;
  });
  function closeForm() {
    setFormOpen(false);
    setEditingItem(null);
    setKnowledgeTitle("");
    setKnowledgeContent("");
    setKnowledgeType("manual");
    setKnowledgeError("");
  }

  function openCreateForm() {
    closeForm();
    setFormOpen(true);
  }

  function openEditForm(item: ProjectKnowledgeItem) {
    setEditingItem(item);
    setKnowledgeTitle(item.title);
    setKnowledgeContent(item.contentPreview);
    setKnowledgeType(item.type);
    setKnowledgeError("");
    setFormOpen(true);
  }

  async function handleKnowledgeSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSpace || !knowledgeTitle.trim() || !knowledgeContent.trim() || meetingMutationLoading) {
      return;
    }
    setKnowledgeError("");
    try {
      const result = editingItem
        ? await onUpdateProjectKnowledge?.(selectedSpace.id, editingItem.id, { title: knowledgeTitle.trim(), content: knowledgeContent.trim() })
        : await onCreateProjectKnowledge?.(selectedSpace.id, { type: knowledgeType, title: knowledgeTitle.trim(), content: knowledgeContent.trim() });
      if (result) {
        closeForm();
      } else {
        setKnowledgeError("지식을 저장하지 못했습니다.");
      }
    } catch (error) {
      setKnowledgeError(error instanceof Error ? error.message : "지식을 저장하지 못했습니다.");
    }
  }

  async function handleKnowledgeDelete() {
    if (!selectedSpace || !deleteCandidate || !onDeleteProjectKnowledge || meetingMutationLoading) {
      return;
    }
    try {
      const deleted = await onDeleteProjectKnowledge(selectedSpace.id, deleteCandidate.id);
      if (!deleted) {
        setKnowledgeError("지식을 삭제하지 못했습니다.");
      }
    } catch (error) {
      setKnowledgeError(error instanceof Error ? error.message : "지식을 삭제하지 못했습니다.");
    } finally {
      setDeleteCandidate(null);
    }
  }

  return (
    <SpaceLayout
      activeItem="knowledge"
      contentClassName="project-knowledge-main"
      dataSource={workspaceDataSource}
      onCreateProject={onCreateProject}
      projectName={selectedSpace.name}
      spaceId={selectedSpace.id}
    >
      <PageHeader
        actions={(
          <>
          <button className="mm-common-button mm-common-button--primary" disabled={!canManageKnowledge || !onCreateProjectKnowledge} onClick={openCreateForm} type="button">지식 등록</button>
          <Link className="mm-common-button mm-common-button--secondary" to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>프로젝트 홈</Link>
          </>
        )}
        breadcrumb={(
          <>
            <Link to="/spaces">프로젝트 목록</Link>
            <span aria-hidden="true">/</span>
            <Link to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>{selectedSpace.name}</Link>
            <span aria-hidden="true">/</span>
            <strong>Knowledge</strong>
          </>
        )}
        description="Project AI가 참조할 수 있는 공식 기준과 결정사항을 관리합니다. 검색 가능 상태는 embedding 처리 결과를 따릅니다."
        eyebrow="Official source"
        meta={currentMember ? <RoleBadge role={currentMember.spaceRole} scope="space" /> : null}
        title="Project Knowledge"
      />
      {!canManageKnowledge ? <p className="project-knowledge-permission-note">공식 지식 등록·수정·삭제는 프로젝트 오너 또는 관리자만 할 수 있습니다.</p> : null}
      {meetingMutationError || knowledgeError ? <p aria-live="polite" className="project-knowledge-error" role="alert">{knowledgeError || meetingMutationError}</p> : null}

      <section aria-label="Project Knowledge 필터" className="project-knowledge-toolbar">
        <label>
          <span>지식 검색</span>
          <input aria-label="공식 지식 검색" onChange={(event) => setQuery(event.target.value)} placeholder="제목, 내용, 유형 검색" type="search" value={query} />
        </label>
        <label>
          <span>검색 상태</span>
          <select aria-label="embedding 상태 필터" onChange={(event) => setStatusFilter(event.target.value as typeof statusFilter)} value={statusFilter}>
            <option value="ALL">전체</option>
            <option value="COMPLETED">검색 가능</option>
            <option value="PROCESSING">처리 중</option>
            <option value="PENDING">처리 대기</option>
            <option value="FAILED">처리 실패</option>
          </select>
        </label>
      </section>

      <section aria-labelledby="project-knowledge-list-title" className="project-knowledge-list-surface">
        <div className="project-knowledge-list-header">
          <div>
            <p className="project-home-section-kicker">{visibleItems.length} results</p>
            <h2 id="project-knowledge-list-title">공식 지식 목록</h2>
          </div>
          <span>{session ? "Space 공식 출처" : "로그인이 필요합니다"}</span>
        </div>
        {visibleItems.length ? (
          <div className="project-knowledge-grid">
            {visibleItems.map((item) => (
              <article className="project-knowledge-card" key={item.id}>
                <div className="project-knowledge-card-top">
                  <span className="project-knowledge-type">{typeLabels[item.type]}</span>
                  <StatusBadge context="knowledge" label={embeddingLabels[item.embeddingStatus]} status={item.embeddingStatus} />
                </div>
                <h3>{item.title}</h3>
                <p>{item.contentPreview}</p>
                <footer><span>{item.sourceMeetingId ? "회의에서 연결됨" : "프로젝트 기준"}</span><span>{new Date(item.updatedAt).toLocaleDateString("ko-KR")}</span></footer>
                {canManageKnowledge ? <div className="project-knowledge-card-actions"><button onClick={() => openEditForm(item)} type="button">수정</button><button onClick={() => setDeleteCandidate(item)} type="button">삭제</button></div> : null}
              </article>
            ))}
          </div>
        ) : (
          <DataState
            actionLabel={items.length ? "필터 초기화" : undefined}
            onAction={items.length ? () => { setQuery(""); setStatusFilter("ALL"); } : undefined}
            state="empty"
            title={items.length ? "조건에 맞는 지식이 없습니다" : "아직 공식 지식이 없습니다"}
            description={items.length ? "검색어나 embedding 상태를 바꾸어 다시 확인해 주세요." : "관리자가 기준이나 결정사항을 등록하면 Project AI가 참조할 수 있습니다."}
          />
        )}
      </section>
      {formOpen ? (
        <div className="project-knowledge-dialog-backdrop" role="presentation">
          <section aria-labelledby="project-knowledge-dialog-title" aria-modal="true" className="project-knowledge-dialog" role="dialog">
            <div className="project-knowledge-dialog-header"><div><p className="project-home-section-kicker">Official source</p><h2 id="project-knowledge-dialog-title">{editingItem ? "공식 지식 수정" : "공식 지식 등록"}</h2></div><button aria-label="지식 입력 닫기" className="project-knowledge-dialog-close" onClick={closeForm} type="button">×</button></div>
            <form className="project-knowledge-dialog-form" onSubmit={handleKnowledgeSubmit}>
              <label><span>제목</span><input autoFocus onChange={(event) => setKnowledgeTitle(event.target.value)} required type="text" value={knowledgeTitle} /></label>
              <label><span>유형</span><select disabled={Boolean(editingItem)} onChange={(event) => setKnowledgeType(event.target.value as ProjectKnowledgeType)} value={knowledgeType}><option value="manual">직접 등록</option><option value="decision">결정</option><option value="external">외부 자료</option></select></label>
              <label><span>내용</span><textarea onChange={(event) => setKnowledgeContent(event.target.value)} placeholder="Project AI가 참고할 공식 내용을 적어주세요." required rows={7} value={knowledgeContent} /></label>
              <div className="project-knowledge-dialog-actions"><button className="mm-common-button mm-common-button--secondary" onClick={closeForm} type="button">취소</button><button className="mm-common-button mm-common-button--primary" disabled={meetingMutationLoading || !knowledgeTitle.trim() || !knowledgeContent.trim()} type="submit">{meetingMutationLoading ? "저장 중..." : "저장"}</button></div>
            </form>
          </section>
        </div>
      ) : null}
      <ConfirmDialog
        busy={meetingMutationLoading}
        confirmLabel="삭제"
        description={deleteCandidate ? `“${deleteCandidate.title}” 지식을 삭제하면 Project AI 검색 대상에서도 제외됩니다.` : "삭제할 지식을 선택해 주세요."}
        onCancel={() => setDeleteCandidate(null)}
        onConfirm={() => void handleKnowledgeDelete()}
        open={Boolean(deleteCandidate)}
        title="공식 지식을 삭제할까요?"
      />
    </SpaceLayout>
  );
}
