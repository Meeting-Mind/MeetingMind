import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { WorkspaceData } from "../types";

type TeamMember = {
  name: string;
  email: string;
  role: string;
  since: string;
  access: string;
  rank: string;
  status: "active" | "away";
};

type JoinRequest = {
  id: string;
  name: string;
  email: string;
  role: string;
  requestedAt: string;
  source: "링크" | "코드";
};

type InviteMeta = {
  link: string;
  code: string;
};

export function TeamMembersPage({
  spaces,
  projectMembers,
  pendingRequests,
  inviteMeta,
  onApproveRequest,
  onRejectRequest,
  onCreateProject
}: {
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  projectMembers: Record<string, TeamMember[]>;
  pendingRequests: Record<string, JoinRequest[]>;
  inviteMeta: Record<string, InviteMeta>;
  onApproveRequest?: (projectName: string, requestId: string) => void;
  onRejectRequest?: (projectName: string, requestId: string) => void;
  onCreateProject?: (payload: { name: string; description: string }) => void;
}) {
  useEffect(() => {
    document.body.className = "app-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  const [searchParams] = useSearchParams();
  const projectName = searchParams.get("project") ?? spaces[0]?.name ?? "";
  const selectedSpace = spaces.find((space) => space.name === projectName) ?? spaces[0];
  const members = projectMembers[projectName] ?? [];
  const requests = pendingRequests[projectName] ?? [];
  const invite = inviteMeta[projectName] ?? {
    link: "https://meetingmind.ai/invite/new-project",
    code: "NEW-TEAM-0000"
  };
  const activeCount = members.filter((member) => member.status === "active").length;
  const awayCount = members.length - activeCount;

  async function handleCopyInvite(value: string) {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      // Ignore clipboard errors in unsupported environments.
    }
  }

  return (
    <div className="workspace-catalog-shell">
      <WorkspaceSidebar
        activeItem="members"
        contextOverride={projectName}
        mode="catalog"
        onCreateProject={onCreateProject}
        projectName={projectName}
      />

      <main className="workspace-catalog-main project-detail-main">
        <div className="workspace-catalog-topbar">
          <div className="workspace-catalog-top-actions" aria-hidden="true">
            <button className="workspace-catalog-icon-button">🔔</button>
          </div>
        </div>

        <section className="team-members-page-head">
          <div>
            <h1>Members</h1>
            <p>{projectName} 프로젝트 멤버와 권한, 담당 영역, 최근 참여 맥락을 확인합니다.</p>
          </div>
          <div className="team-members-invite-panel">
            <button className="team-members-invite-button" onClick={() => void handleCopyInvite(invite.link)} type="button">
              팀 초대 링크
            </button>
            <div
              className="team-members-invite-code"
              onClick={() => void handleCopyInvite(invite.code)}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  void handleCopyInvite(invite.code);
                }
              }}
              role="button"
              tabIndex={0}
            >
              <span>팀 초대 코드</span>
              <strong>{invite.code}</strong>
            </div>
          </div>
        </section>

        <section className="team-members-approval-panel">
          <div className="team-members-approval-head">
            <div>
              <strong>승인 대기 요청</strong>
              <p>초대 링크 또는 초대 코드로 접속한 사용자는 승인 후 프로젝트에 접근할 수 있습니다.</p>
            </div>
            <span>{requests.length}건</span>
          </div>

          {requests.length ? (
            <div className="team-members-request-list">
              {requests.map((request, index) => (
                <article key={request.id} className="team-members-request-card">
                  <div className={`team-member-avatar tone-${(index % 3) + 1}`}>{request.name.slice(0, 1)}</div>
                  <div className="team-members-request-copy">
                    <strong>{request.name}</strong>
                    <span>{request.email}</span>
                    <p>
                      {request.role} · {request.source} 요청 · {request.requestedAt}
                    </p>
                  </div>
                  <div className="team-members-request-actions">
                    <button onClick={() => onRejectRequest?.(projectName, request.id)} type="button">
                      거절
                    </button>
                    <button className="primary" onClick={() => onApproveRequest?.(projectName, request.id)} type="button">
                      승인
                    </button>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="team-members-request-empty">
              <strong>아직 승인 대기 중인 요청이 없습니다</strong>
              <p>
                팀 초대 링크 <strong>{invite.link}</strong> 또는 초대 코드 <strong>{invite.code}</strong> 로 접속한 요청은
                여기에서 승인할 수 있습니다.
              </p>
            </div>
          )}
        </section>

        <section className="team-members-toolbar">
          <div className="team-members-tabs">
            <button className="active">All</button>
            <button>Active</button>
            <button>Absent</button>
          </div>

          <div className="team-members-toolbar-right">
            <div className="team-members-search">
              <span>⌕</span>
              <input type="text" defaultValue="멤버 이름, 역할, 권한 검색" aria-label="팀 멤버 검색" />
            </div>
          </div>
        </section>

        <section className="team-members-table">
          <div className="team-members-table-head">
            <div className="team-members-col check">
              <span className="team-members-check checked">−</span>
            </div>
            <div className="team-members-col member">멤버</div>
            <div className="team-members-col role">역할</div>
            <div className="team-members-col access">권한</div>
            <div className="team-members-col project">직급</div>
          </div>

          <div className="team-members-table-body">
            {members.length ? (
              members.map((member, index) => (
                <article key={member.email} className={`team-members-row ${member.status === "active" ? "selected" : ""}`}>
                  <div className="team-members-col check">
                    <span className={`team-members-check ${member.status === "active" ? "checked" : ""}`}>
                      {member.status === "active" ? "✓" : ""}
                    </span>
                  </div>

                  <div className="team-members-col member">
                    <div className={`team-member-avatar large tone-${(index % 3) + 1}`}>{member.name.slice(0, 1)}</div>
                    <div className="team-members-primary">
                      <strong>{member.name}</strong>
                      <span>{member.email}</span>
                    </div>
                  </div>

                  <div className="team-members-col role">
                    <div className="team-members-primary">
                      <strong>{member.role}</strong>
                      <span>{member.since}</span>
                    </div>
                  </div>

                  <div className="team-members-col access">
                    <div className="team-members-primary">
                      <strong>{member.access}</strong>
                      <span>{member.status === "active" ? "현재 회의 접근 가능" : "부재 중 · 열람 중심"}</span>
                    </div>
                  </div>

                  <div className="team-members-col project">
                    <div className="team-members-project-chip">{selectedSpace?.name}</div>
                    <div className="team-members-primary compact">
                      <strong>{member.rank}</strong>
                      <span>{member.status === "active" ? "활성 멤버" : "부재 중"}</span>
                    </div>
                  </div>
                </article>
              ))
            ) : (
              <div className="team-members-table-empty">
                <strong>아직 프로젝트 멤버가 없습니다</strong>
                <p>팀 초대 링크나 초대 코드로 접속한 요청을 승인하면 멤버 목록에 추가됩니다.</p>
              </div>
            )}
          </div>
        </section>

        <section className="team-members-footer">
          <span>
            전체 {members.length}명
            {" · "}
            활성 {activeCount}명
            {" · "}
            부재 {awayCount}명
          </span>
          <div className="team-members-pagination">
            <button>{"‹‹"}</button>
            <button>{"‹"}</button>
            <button className="active">1</button>
            <button>2</button>
            <button>3</button>
            <button>{"›"}</button>
          </div>
        </section>
      </main>
    </div>
  );
}
