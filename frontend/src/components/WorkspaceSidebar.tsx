import { useState, type FormEvent } from "react";
import { Link, NavLink } from "react-router-dom";

type SidebarItem = "none" | "catalog" | "project" | "members" | "terms";
type CreateProjectPayload = {
  name: string;
  description: string;
};

function buildProjectOverviewHref(projectName?: string, spaceId?: string) {
  if (!projectName && !spaceId) {
    return "/project-overview";
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
    return "/team-members";
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
    return "/terms";
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
      <aside className="workspace-catalog-sidebar">
        <Link className="workspace-catalog-logo" to="/">
          <span className="workspace-catalog-logo-main">meeting</span>
          <span className="workspace-catalog-logo-accent">mind</span>
        </Link>

        <button
          className="workspace-catalog-create"
          onClick={() => {
            setCreateError("");
            setIsProjectModalOpen(true);
          }}
          type="button"
        >
          + 새 프로젝트 만들기
        </button>

        <nav className="workspace-catalog-nav" aria-label="워크스페이스 메뉴">
          <NavLink
            className={`workspace-catalog-nav-item ${activeItem === primaryItem ? "active" : ""}`}
            to={mode === "project" ? projectHref : "/spaces"}
          >
            <span className={`workspace-catalog-nav-icon ${activeItem === primaryItem ? "active" : ""}`} />
            <span>{mode === "project" ? "프로젝트 개요" : "회의 카탈로그"}</span>
          </NavLink>
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
          <NavLink className="workspace-catalog-nav-item" to="/meeting-access">
            <span className="workspace-catalog-nav-icon" />
            <span>회의 참가</span>
          </NavLink>
        </nav>

        {contextText ? <div className="workspace-catalog-context">{contextText}</div> : null}

        <div className="workspace-catalog-plan">
          <div className="workspace-catalog-plan-mark" />
          <p>
            당신의 <strong>MeetingMind</strong>
            <br />
            구독이 곧 만료됩니다
          </p>
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
                <p className="workspace-project-modal-kicker">New Project</p>
                <h3 id="workspace-project-modal-title">새 프로젝트 만들기</h3>
              </div>
              <button
                aria-label="새 프로젝트 모달 닫기"
                className="workspace-project-modal-close"
                disabled={isCreating}
                onClick={() => setIsProjectModalOpen(false)}
                type="button"
              >
                ×
              </button>
            </div>

            <form className="workspace-project-modal-form" onSubmit={handleCreateProject}>
              <label className="workspace-project-field">
                <span>프로젝트명</span>
                <input
                  disabled={isCreating}
                  onChange={(event) => setProjectTitle(event.target.value)}
                  placeholder="예: FinPilot Renewal"
                  type="text"
                  value={projectTitle}
                />
              </label>

              <label className="workspace-project-field">
                <span>설명</span>
                <textarea
                  disabled={isCreating}
                  onChange={(event) => setProjectDescription(event.target.value)}
                  placeholder="프로젝트 목표, 범위, 현재 논의 중인 흐름을 적어주세요."
                  value={projectDescription}
                />
              </label>

              {createError ? <p aria-live="polite" className="workspace-form-error">{createError}</p> : null}

              <div className="workspace-project-modal-actions">
                <button className="secondary" disabled={isCreating} onClick={() => setIsProjectModalOpen(false)} type="button">
                  취소
                </button>
                <button className="primary" disabled={!projectTitle.trim() || isCreating} type="submit">
                  {isCreating ? "생성 중..." : "프로젝트 생성"}
                </button>
              </div>
            </form>
          </section>
        </div>
      ) : null}
    </>
  );
}
