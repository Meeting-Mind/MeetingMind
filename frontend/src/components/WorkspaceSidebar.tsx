import { useState, type FormEvent } from "react";
import { Link, NavLink } from "react-router-dom";

type SidebarItem = "none" | "catalog" | "project" | "calendar" | "meetings" | "tasks" | "ai" | "knowledge" | "members" | "terms" | "settings";
type CreateProjectPayload = {
  name: string;
  description: string;
};

function buildProjectOverviewHref(projectName?: string, spaceId?: string) {
  if (!projectName && !spaceId) {
    return "/spaces";
  }

  if (spaceId) {
    return `/spaces/${encodeURIComponent(spaceId)}`;
  }

  const params = new URLSearchParams();
  if (spaceId) {
    params.set("spaceId", spaceId);
  }
  if (projectName) {
    params.set("project", projectName);
  }

  return `/project-overview?${params.toString()}`;
}

function buildTeamMembersHref(projectName?: string, spaceId?: string) {
  if (!projectName && !spaceId) {
    return "/spaces";
  }

  if (spaceId) {
    return `/spaces/${encodeURIComponent(spaceId)}/members`;
  }

  const params = new URLSearchParams();
  if (spaceId) {
    params.set("spaceId", spaceId);
  }
  if (projectName) {
    params.set("project", projectName);
  }

  return `/team-members?${params.toString()}`;
}

function buildTermsHref(projectName?: string, spaceId?: string) {
  if (!projectName && !spaceId) {
    return "/spaces";
  }

  if (spaceId) {
    return `/spaces/${encodeURIComponent(spaceId)}/terms`;
  }

  const params = new URLSearchParams();
  if (spaceId) {
    params.set("spaceId", spaceId);
  }
  if (projectName) {
    params.set("project", projectName);
  }

  return `/terms?${params.toString()}`;
}

export function WorkspaceSidebar({
  activeItem,
  projectName,
  spaceId,
  disableMembers = false,
  mode = "catalog",
  contextOverride,
  onCreateProject
}: {
  activeItem: SidebarItem;
  projectName?: string;
  spaceId?: string;
  disableMembers?: boolean;
  mode?: "catalog" | "project";
  contextOverride?: string;
  onCreateProject?: (payload: CreateProjectPayload) => Promise<void>;
}) {
  const projectHref = buildProjectOverviewHref(projectName, spaceId);
  const teamMembersHref = buildTeamMembersHref(projectName, spaceId);
  const termsHref = buildTermsHref(projectName, spaceId);
  const primaryItem = mode === "project" ? "project" : "catalog";
  const contextText = contextOverride ?? (mode === "project" ? projectName : "3회차 진행중");
  const [isProjectModalOpen, setIsProjectModalOpen] = useState(false);
  const [projectTitle, setProjectTitle] = useState("");
  const [projectDescription, setProjectDescription] = useState("");
  const [createError, setCreateError] = useState("");
  const [isCreating, setIsCreating] = useState(false);

  async function handleCreateProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!projectTitle.trim() || !onCreateProject || isCreating) {
      return;
    }

    setCreateError("");
    setIsCreating(true);
    try {
      await onCreateProject({
        name: projectTitle.trim(),
        description: projectDescription.trim()
      });
      setIsProjectModalOpen(false);
      setProjectTitle("");
      setProjectDescription("");
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : "프로젝트를 생성하지 못했습니다.");
    } finally {
      setIsCreating(false);
    }
  }

  return (
    <>
      <aside aria-label="MeetingMind 작업공간" className="workspace-catalog-sidebar">
        <Link className="workspace-catalog-logo" to="/">
          <span className="workspace-catalog-logo-main">meeting</span>
          <span className="workspace-catalog-logo-accent">mind</span>
        </Link>

        <div className="workspace-catalog-sidebar-section">
          <p className="workspace-catalog-section-label">워크스페이스</p>
          <button
            className="workspace-catalog-create"
            onClick={() => {
              setCreateError("");
              setIsProjectModalOpen(true);
            }}
            type="button"
          >
            <span aria-hidden="true" className="workspace-catalog-create-mark">+</span>
            <span>새 프로젝트 만들기</span>
          </button>
        </div>

        <nav className="workspace-catalog-nav" aria-label="워크스페이스 메뉴">
          <p className="workspace-catalog-section-label">탐색</p>
          <NavLink
            className={`workspace-catalog-nav-item ${activeItem === primaryItem ? "active" : ""}`}
            to={mode === "project" ? projectHref : "/spaces"}
          >
            <span className={`workspace-catalog-nav-icon ${activeItem === primaryItem ? "active" : ""}`} />
            <span>{mode === "project" ? "프로젝트 개요" : "회의 카탈로그"}</span>
          </NavLink>
          {mode === "project" && spaceId ? (
            <NavLink
              className={`workspace-catalog-nav-item ${activeItem === "calendar" ? "active" : ""}`}
              to={`/spaces/${encodeURIComponent(spaceId)}/calendar`}
            >
              <span className={`workspace-catalog-nav-icon ${activeItem === "calendar" ? "active" : ""}`} />
              <span>캘린더</span>
            </NavLink>
          ) : null}
          {mode === "project" && spaceId ? (
            <NavLink
              className={`workspace-catalog-nav-item ${activeItem === "meetings" ? "active" : ""}`}
              to={`/spaces/${encodeURIComponent(spaceId)}/meetings`}
            >
              <span className={`workspace-catalog-nav-icon ${activeItem === "meetings" ? "active" : ""}`} />
              <span>회의</span>
            </NavLink>
          ) : null}
          {mode === "project" && spaceId ? (
            <NavLink
              className={`workspace-catalog-nav-item ${activeItem === "tasks" ? "active" : ""}`}
              to={`/spaces/${encodeURIComponent(spaceId)}/tasks`}
            >
              <span className={`workspace-catalog-nav-icon ${activeItem === "tasks" ? "active" : ""}`} />
              <span>태스크</span>
            </NavLink>
          ) : null}
          {mode === "project" && spaceId ? (
            <NavLink
              className={`workspace-catalog-nav-item ${activeItem === "ai" ? "active" : ""}`}
              to={`/spaces/${encodeURIComponent(spaceId)}/ai`}
            >
              <span className={`workspace-catalog-nav-icon ${activeItem === "ai" ? "active" : ""}`} />
              <span>Project AI</span>
            </NavLink>
          ) : null}
          {mode === "project" && spaceId ? (
            <NavLink
              className={`workspace-catalog-nav-item ${activeItem === "knowledge" ? "active" : ""}`}
              to={`/spaces/${encodeURIComponent(spaceId)}/knowledge`}
            >
              <span className={`workspace-catalog-nav-icon ${activeItem === "knowledge" ? "active" : ""}`} />
              <span>Knowledge</span>
            </NavLink>
          ) : null}
          {disableMembers ? (
            <span className="workspace-catalog-nav-item disabled" aria-disabled="true">
              <span className="workspace-catalog-nav-icon disabled" />
              <span>팀 멤버</span>
            </span>
          ) : (
            <NavLink className={`workspace-catalog-nav-item ${activeItem === "members" ? "active" : ""}`} to={teamMembersHref}>
              <span className={`workspace-catalog-nav-icon ${activeItem === "members" ? "active" : ""}`} />
              <span>팀 멤버</span>
            </NavLink>
          )}
          <NavLink className={`workspace-catalog-nav-item ${activeItem === "terms" ? "active" : ""}`} to={termsHref}>
            <span className={`workspace-catalog-nav-icon ${activeItem === "terms" ? "active" : ""}`} />
            <span>용어사전</span>
          </NavLink>
          {mode === "project" && spaceId ? (
            <NavLink
              className={`workspace-catalog-nav-item ${activeItem === "settings" ? "active" : ""}`}
              to={`/spaces/${encodeURIComponent(spaceId)}/settings`}
            >
              <span className={`workspace-catalog-nav-icon ${activeItem === "settings" ? "active" : ""}`} />
              <span>설정</span>
            </NavLink>
          ) : null}
          <NavLink className="workspace-catalog-nav-item" to="/meeting-access">
            <span className="workspace-catalog-nav-icon" />
            <span>회의 참가</span>
          </NavLink>
        </nav>

        {contextText ? (
          <section aria-label="현재 작업공간" className="workspace-catalog-context">
            <p className="workspace-catalog-section-label">현재 작업공간</p>
            <strong>{contextText}</strong>
            <span>접근 가능한 회의와 프로젝트 지식</span>
          </section>
        ) : null}

        <div className="workspace-catalog-support">
          <span className="workspace-catalog-support-mark" aria-hidden="true">?</span>
          <div>
            <strong>작업 범위 안내</strong>
            <p>회의와 지식은 프로젝트 권한 범위에서만 표시됩니다.</p>
          </div>
        </div>
      </aside>

      {isProjectModalOpen ? (
        <div className="workspace-project-modal-backdrop" role="presentation">
          <section
            aria-labelledby="workspace-project-modal-title"
            aria-modal="true"
            className="workspace-project-modal"
            role="dialog"
          >
            <div className="workspace-project-modal-top">
              <div>
                <p className="workspace-project-modal-kicker">New Space</p>
                <h3 id="workspace-project-modal-title">Create a new collaboration space.</h3>
              </div>
              <button
                aria-label="Close new space modal"
                className="workspace-project-modal-close"
                disabled={isCreating}
                onClick={() => setIsProjectModalOpen(false)}
                type="button"
              >
                ×
              </button>
            </div>

            <form className="workspace-project-modal-form" onSubmit={handleCreateProject}>
              <div className="workspace-project-field">
                <div id="workspace-project-title-label">Project name</div>
                <input
                  aria-labelledby="workspace-project-title-label"
                  disabled={isCreating}
                  onChange={(event) => setProjectTitle(event.target.value)}
                  placeholder="e.g. Q3 Launch"
                  type="text"
                  value={projectTitle}
                />
              </div>

              <div className="workspace-project-field">
                <div id="workspace-project-description-label">Description</div>
                <textarea
                  aria-labelledby="workspace-project-description-label"
                  disabled={isCreating}
                  onChange={(event) => setProjectDescription(event.target.value)}
                  placeholder="What is this project about?"
                  value={projectDescription}
                />
              </div>

              {createError ? <p aria-live="polite" className="workspace-form-error">{createError}</p> : null}

              <div className="workspace-project-modal-actions">
                <button className="secondary" disabled={isCreating} onClick={() => setIsProjectModalOpen(false)} type="button">
                  Cancel
                </button>
                <button className="primary" disabled={!projectTitle.trim() || isCreating} type="submit">
                  {isCreating ? "Creating..." : "Create Space"}
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </>
  );
}
