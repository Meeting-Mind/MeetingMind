import { useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { WorkspaceData } from "../types";

type TeamMember = {
  name: string;
  email: string;
  role: string;
  spaceRole: "OWNER" | "ADMIN" | "MEMBER";
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
  meetingIndex: string;
  meetingTitle: string;
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
  onRemoveMember,
  onTransferOwner,
  onUpdateMemberRole,
  onCreateProject
}: {
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  projectMembers: Record<string, TeamMember[]>;
  pendingRequests: Record<string, JoinRequest[]>;
  inviteMeta: Record<string, InviteMeta>;
  onApproveRequest?: (projectName: string, requestId: string) => void;
  onRejectRequest?: (projectName: string, requestId: string) => void;
  onRemoveMember?: (projectName: string, memberEmail: string) => void;
  onTransferOwner?: (
    projectName: string,
    targetMemberEmail: string,
    previousOwnerRole: Exclude<TeamMember["spaceRole"], "OWNER">,
    confirmation: string
  ) => void;
  onUpdateMemberRole?: (
    projectName: string,
    memberEmail: string,
    role: Exclude<TeamMember["spaceRole"], "OWNER">
  ) => void;
  onCreateProject?: (payload: { name: string; description: string }) => void;
}) {
  useEffect(() => {
    document.body.className = "app-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  const [searchParams] = useSearchParams();
  const spaceId = searchParams.get("spaceId");
  const projectParam = searchParams.get("project");
  const selectedSpace = spaces.find((space) => space.id === spaceId) ?? spaces.find((space) => space.name === projectParam) ?? spaces[0];
  const projectName = selectedSpace?.name ?? "";
  const members = projectMembers[projectName] ?? [];
  const requests = pendingRequests[projectName] ?? [];
  const invite = inviteMeta[projectName] ?? {
    link: "https://meetingmind.ai/invite/new-project",
    code: "NEW-TEAM-0000"
  };
  const activeCount = members.filter((member) => member.status === "active").length;
  const awayCount = members.length - activeCount;
  const currentOwner = members.find((member) => member.spaceRole === "OWNER") ?? null;
  const transferCandidates = members.filter((member) => member.status === "active" && member.spaceRole !== "OWNER");
  const [transferTargetEmail, setTransferTargetEmail] = useState("");
  const [previousOwnerRole, setPreviousOwnerRole] = useState<Exclude<TeamMember["spaceRole"], "OWNER">>("ADMIN");
  const [transferConfirm, setTransferConfirm] = useState("");
  const canTransferOwner = Boolean(transferTargetEmail) && transferConfirm === "TRANSFER OWNER";

  async function handleCopyInvite(value: string) {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      // Ignore clipboard errors in unsupported environments.
    }
  }

  function handleOwnerTransfer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canTransferOwner) {
      return;
    }

    onTransferOwner?.(projectName, transferTargetEmail, previousOwnerRole, transferConfirm);
    setTransferTargetEmail("");
    setPreviousOwnerRole("ADMIN");
    setTransferConfirm("");
  }

  return (
    <div className="workspace-catalog-shell">
      <WorkspaceSidebar
        activeItem="members"
        contextOverride={projectName}
        mode="catalog"
        onCreateProject={onCreateProject}
        projectName={projectName}
        spaceId={selectedSpace?.id}
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
              Space 초대 링크
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
              <span>Space 초대 코드</span>
              <strong>{invite.code}</strong>
            </div>
          </div>
        </section>

        <section className="team-members-invitation-split">
          <article>
            <strong>Space invitation</strong>
            <p>프로젝트 멤버로 추가되어 Project Knowledge와 프로젝트 화면에 접근합니다.</p>
            <button onClick={() => void handleCopyInvite(invite.link)} type="button">Space 링크 복사</button>
          </article>
          <article>
            <strong>Meeting join request</strong>
            <p>URL/코드 신청을 승인하면 해당 회의의 VIEWER 권한만 부여됩니다. SpaceMember는 생성되지 않습니다.</p>
            <Link to="/meeting-access">회의 참가 화면</Link>
          </article>
        </section>

        <section className="team-members-approval-panel">
          <div className="team-members-approval-head">
            <div>
              <strong>회의 참가 승인 대기</strong>
              <p>회의 URL 또는 참가 코드로 신청한 사용자는 승인 후 해당 회의에만 접근할 수 있습니다.</p>
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
                      {request.meetingIndex} {request.meetingTitle} · {request.source} 요청 · {request.requestedAt}
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
                회의 URL 또는 참가 코드로 제출된 요청은 여기에서 승인할 수 있습니다. 승인은 프로젝트 멤버십을 만들지 않습니다.
              </p>
            </div>
          )}
        </section>

        <section className="team-members-owner-transfer">
          <div>
            <strong>Owner transfer</strong>
            <p>
              현재 owner {currentOwner?.name ?? "없음"} · 활성 SpaceMember만 대상입니다. 확인 문구 없이 이양되지 않습니다.
            </p>
          </div>
          <form onSubmit={handleOwnerTransfer}>
            <select
              aria-label="새 owner 선택"
              onChange={(event) => setTransferTargetEmail(event.target.value)}
              value={transferTargetEmail}
            >
              <option value="">대상 선택</option>
              {transferCandidates.map((member) => (
                <option key={`owner-target-${member.email}`} value={member.email}>
                  {member.name} · {member.spaceRole}
                </option>
              ))}
            </select>
            <select
              aria-label="기존 owner 강등 role"
              onChange={(event) => setPreviousOwnerRole(event.target.value as Exclude<TeamMember["spaceRole"], "OWNER">)}
              value={previousOwnerRole}
            >
              <option value="ADMIN">기존 owner를 ADMIN으로 변경</option>
              <option value="MEMBER">기존 owner를 MEMBER로 변경</option>
            </select>
            <input
              aria-label="owner transfer 확인 문구"
              onChange={(event) => setTransferConfirm(event.target.value)}
              placeholder="TRANSFER OWNER"
              type="text"
              value={transferConfirm}
            />
            <button disabled={!canTransferOwner} type="submit">이양</button>
          </form>
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
            <div className="team-members-col actions">관리</div>
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
                      <span>{member.status === "active" ? `${member.spaceRole} · 활성 SpaceMember` : "부재 중 · 열람 중심"}</span>
                    </div>
                  </div>

                  <div className="team-members-col project">
                    <div className="team-members-project-chip">{selectedSpace?.name}</div>
                    <div className="team-members-primary compact">
                      <strong>{member.rank}</strong>
                      <span>{member.status === "active" ? "활성 멤버" : "부재 중"}</span>
                    </div>
                  </div>

                  <div className="team-members-col actions">
                    <select
                      aria-label={`${member.name} Space role 변경`}
                      disabled={member.spaceRole === "OWNER"}
                      onChange={(event) =>
                        onUpdateMemberRole?.(
                          projectName,
                          member.email,
                          event.target.value as Exclude<TeamMember["spaceRole"], "OWNER">
                        )
                      }
                      value={member.spaceRole === "OWNER" ? "OWNER" : member.spaceRole}
                    >
                      <option value="OWNER" disabled>OWNER</option>
                      <option value="ADMIN">ADMIN</option>
                      <option value="MEMBER">MEMBER</option>
                    </select>
                    <button
                      disabled={member.spaceRole === "OWNER"}
                      onClick={() => onRemoveMember?.(projectName, member.email)}
                      type="button"
                    >
                      제거
                    </button>
                  </div>
                </article>
              ))
            ) : (
              <div className="team-members-table-empty">
                <strong>아직 프로젝트 멤버가 없습니다</strong>
                <p>Space invitation을 수락한 사용자만 프로젝트 멤버 목록에 추가됩니다.</p>
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
