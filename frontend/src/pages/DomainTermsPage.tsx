import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import {
  archiveDomainTerm,
  createDomainTerm,
  fetchDomainTerms,
  updateDomainTerm
} from "../api/workspace";
import type { AuthSession } from "../auth/session";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { DomainTerm, DomainTermStatus, WorkspaceData } from "../types";

type SpaceMember = {
  email: string;
  spaceRole: "OWNER" | "ADMIN" | "MEMBER";
};

type EditDraft = {
  term: string;
  definition: string;
  status: DomainTermStatus;
};

type TermFilter = "ALL" | DomainTermStatus;

function formatUpdatedAt(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "업데이트 시간 없음";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

export function DomainTermsPage({
  currentUserEmail,
  onCreateProject,
  projectMembers,
  session,
  spaces
}: {
  currentUserEmail: string;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  projectMembers: Record<string, SpaceMember[]>;
  session: AuthSession;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
}) {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedSpaceId = searchParams.get("spaceId");
  const requestedProjectName = searchParams.get("project");
  const selectedSpace =
    spaces.find((space) => space.id === requestedSpaceId) ??
    spaces.find((space) => space.name === requestedProjectName) ??
    spaces[0] ??
    null;
  const [terms, setTerms] = useState<DomainTerm[]>([]);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState<TermFilter>("ACTIVE");
  const [newTerm, setNewTerm] = useState("");
  const [newDefinition, setNewDefinition] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState<EditDraft | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isMutating, setIsMutating] = useState(false);
  const [error, setError] = useState("");

  const currentMember = selectedSpace
    ? projectMembers[selectedSpace.name]?.find((member) => member.email === currentUserEmail)
    : undefined;
  const canManage = currentMember?.spaceRole === "OWNER" || currentMember?.spaceRole === "ADMIN";

  const loadTerms = useCallback(async () => {
    if (!selectedSpace) {
      setTerms([]);
      return;
    }

    setIsLoading(true);
    setError("");
    try {
      const response = await fetchDomainTerms(session, selectedSpace.id, {
        keyword,
        status: statusFilter === "ALL" ? undefined : statusFilter
      });
      setTerms(response.terms);
    } catch (loadError) {
      setTerms([]);
      setError(loadError instanceof Error ? loadError.message : "용어사전을 불러오지 못했습니다.");
    } finally {
      setIsLoading(false);
    }
  }, [keyword, selectedSpace, session, statusFilter]);

  useEffect(() => {
    document.body.className = "app-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  useEffect(() => {
    void loadTerms();
  }, [loadTerms]);

  const activeTermCount = useMemo(
    () => terms.filter((term) => term.status === "ACTIVE").length,
    [terms]
  );

  function selectSpace(spaceId: string) {
    const nextSpace = spaces.find((space) => space.id === spaceId);
    setSearchParams(nextSpace ? { spaceId: nextSpace.id, project: nextSpace.name } : {});
    setEditingId(null);
    setEditDraft(null);
  }

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSpace || !canManage || !newTerm.trim() || !newDefinition.trim() || isMutating) {
      return;
    }

    setIsMutating(true);
    setError("");
    try {
      await createDomainTerm(session, selectedSpace.id, {
        term: newTerm.trim(),
        definition: newDefinition.trim()
      });
      setNewTerm("");
      setNewDefinition("");
      await loadTerms();
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : "용어를 등록하지 못했습니다.");
    } finally {
      setIsMutating(false);
    }
  }

  function beginEdit(term: DomainTerm) {
    setEditingId(term.id);
    setEditDraft({ term: term.term, definition: term.definition, status: term.status });
    setError("");
  }

  async function saveEdit(termId: string) {
    if (!selectedSpace || !canManage || !editDraft || isMutating) {
      return;
    }

    setIsMutating(true);
    setError("");
    try {
      await updateDomainTerm(session, selectedSpace.id, termId, {
        term: editDraft.term.trim(),
        definition: editDraft.definition.trim(),
        status: editDraft.status
      });
      setEditingId(null);
      setEditDraft(null);
      await loadTerms();
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : "용어를 수정하지 못했습니다.");
    } finally {
      setIsMutating(false);
    }
  }

  async function toggleArchived(term: DomainTerm) {
    if (!selectedSpace || !canManage || isMutating) {
      return;
    }

    setIsMutating(true);
    setError("");
    try {
      if (term.status === "ACTIVE") {
        await archiveDomainTerm(session, selectedSpace.id, term.id);
      } else {
        await updateDomainTerm(session, selectedSpace.id, term.id, { status: "ACTIVE" });
      }
      await loadTerms();
    } catch (mutationError) {
      setError(mutationError instanceof Error ? mutationError.message : "용어 상태를 변경하지 못했습니다.");
    } finally {
      setIsMutating(false);
    }
  }

  return (
    <div className="workspace-catalog-shell">
      <WorkspaceSidebar
        activeItem="terms"
        contextOverride={selectedSpace?.name}
        mode="project"
        onCreateProject={onCreateProject}
        projectName={selectedSpace?.name}
        spaceId={selectedSpace?.id}
      />

      <main className="workspace-catalog-main project-detail-main domain-terms-main">
        <div className="workspace-catalog-topbar">
          <div className="workspace-catalog-top-actions" aria-hidden="true">
            <button className="workspace-catalog-icon-button" type="button">🔔</button>
          </div>
        </div>

        <section className="domain-terms-header">
          <div>
            <p className="project-overview-breadcrumb">Project <span>Domain Dictionary</span></p>
            <h1>용어사전</h1>
            <p>프로젝트에서 반복되는 용어와 설명을 관리합니다.</p>
          </div>
          <label className="domain-terms-space-picker">
            <span>프로젝트</span>
            <select onChange={(event) => selectSpace(event.target.value)} value={selectedSpace?.id ?? ""}>
              {spaces.map((space) => (
                <option key={space.id} value={space.id}>{space.name}</option>
              ))}
            </select>
          </label>
        </section>

        {!selectedSpace ? (
          <section className="domain-terms-empty"><strong>참여 중인 프로젝트가 없습니다.</strong></section>
        ) : (
          <>
            <section className="domain-terms-toolbar" aria-label="용어사전 필터">
              <form
                className="domain-terms-search"
                onSubmit={(event) => {
                  event.preventDefault();
                  setKeyword(keywordInput.trim());
                }}
              >
                <input
                  aria-label="용어 검색"
                  onChange={(event) => setKeywordInput(event.target.value)}
                  placeholder="용어 또는 설명 검색"
                  value={keywordInput}
                />
                <button type="submit">검색</button>
              </form>
              <label className="domain-terms-filter">
                <span>상태</span>
                <select onChange={(event) => setStatusFilter(event.target.value as TermFilter)} value={statusFilter}>
                  <option value="ACTIVE">활성</option>
                  <option value="ARCHIVED">비활성</option>
                  <option value="ALL">전체</option>
                </select>
              </label>
              <span className="domain-terms-count">활성 {activeTermCount}개</span>
            </section>

            {canManage ? (
              <form className="domain-terms-create" onSubmit={handleCreate}>
                <label>
                  <span>용어</span>
                  <input
                    disabled={isMutating}
                    onChange={(event) => setNewTerm(event.target.value)}
                    placeholder="예: RAG"
                    value={newTerm}
                  />
                </label>
                <label>
                  <span>설명</span>
                  <input
                    disabled={isMutating}
                    onChange={(event) => setNewDefinition(event.target.value)}
                    placeholder="프로젝트에서 사용하는 의미를 입력하세요"
                    value={newDefinition}
                  />
                </label>
                <button disabled={isMutating || !newTerm.trim() || !newDefinition.trim()} type="submit">
                  {isMutating ? "저장 중..." : "용어 등록"}
                </button>
              </form>
            ) : (
              <p className="domain-terms-readonly">용어사전 조회 권한이 있습니다. 등록과 수정은 Space OWNER 또는 ADMIN만 할 수 있습니다.</p>
            )}

            {error ? <p className="workspace-form-error" role="alert">{error}</p> : null}

            <section className="domain-terms-list" aria-busy={isLoading}>
              {isLoading ? <p className="domain-terms-loading">용어사전을 불러오는 중입니다.</p> : null}
              {!isLoading && terms.length === 0 ? <p className="domain-terms-empty">조건에 맞는 용어가 없습니다.</p> : null}
              {terms.map((term) => {
                const isEditing = editingId === term.id && editDraft;
                return (
                  <article className="domain-term-row" key={term.id}>
                    {isEditing ? (
                      <div className="domain-term-edit-fields">
                        <input
                          aria-label="수정할 용어"
                          disabled={isMutating}
                          onChange={(event) => setEditDraft({ ...editDraft, term: event.target.value })}
                          value={editDraft.term}
                        />
                        <textarea
                          aria-label="수정할 용어 설명"
                          disabled={isMutating}
                          onChange={(event) => setEditDraft({ ...editDraft, definition: event.target.value })}
                          value={editDraft.definition}
                        />
                        <select
                          aria-label="용어 상태"
                          disabled={isMutating}
                          onChange={(event) => setEditDraft({ ...editDraft, status: event.target.value as DomainTermStatus })}
                          value={editDraft.status}
                        >
                          <option value="ACTIVE">활성</option>
                          <option value="ARCHIVED">비활성</option>
                        </select>
                      </div>
                    ) : (
                      <div className="domain-term-copy">
                        <div>
                          <strong>{term.term}</strong>
                          <span className={`domain-term-status ${term.status === "ACTIVE" ? "active" : "archived"}`}>
                            {term.status === "ACTIVE" ? "활성" : "비활성"}
                          </span>
                        </div>
                        <p>{term.definition}</p>
                        <small>{formatUpdatedAt(term.updatedAt)} 업데이트</small>
                      </div>
                    )}
                    {canManage ? (
                      <div className="domain-term-actions">
                        {isEditing ? (
                          <>
                            <button disabled={isMutating || !editDraft.term.trim() || !editDraft.definition.trim()} onClick={() => void saveEdit(term.id)} type="button">저장</button>
                            <button disabled={isMutating} onClick={() => { setEditingId(null); setEditDraft(null); }} type="button">취소</button>
                          </>
                        ) : (
                          <>
                            <button disabled={isMutating} onClick={() => beginEdit(term)} type="button">수정</button>
                            <button disabled={isMutating} onClick={() => void toggleArchived(term)} type="button">
                              {term.status === "ACTIVE" ? "비활성화" : "활성화"}
                            </button>
                          </>
                        )}
                      </div>
                    ) : null}
                  </article>
                );
              })}
            </section>
          </>
        )}
      </main>
    </div>
  );
}
