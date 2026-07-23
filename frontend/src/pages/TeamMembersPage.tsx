import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { DataState } from "../components/common/DataState";
import { PageHeader } from "../components/common/PageHeader";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { WorkspaceData } from "../types";

type TeamMember = {
  memberId?: string;
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
  onCreateProject,
  onCreateSpaceInvitation
}: {
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  projectMembers: Record<string, TeamMember[]>;
  pendingRequests: Record<string, JoinRequest[]>;
  inviteMeta: Record<string, InviteMeta>;
  onApproveRequest?: (projectName: string, requestId: string) => void;
  onRejectRequest?: (projectName: string, requestId: string) => void;
  onRemoveMember?: (projectName: string, memberId: string | undefined, memberEmail: string) => Promise<boolean>;
  onTransferOwner?: (
    projectName: string,
    targetMemberId: string | undefined,
    targetMemberEmail: string,
    previousOwnerRole: Exclude<TeamMember["spaceRole"], "OWNER">,
    confirmation: string
  ) => Promise<boolean>;
  onUpdateMemberRole?: (
    projectName: string,
    memberId: string | undefined,
    memberEmail: string,
    role: Exclude<TeamMember["spaceRole"], "OWNER">
  ) => Promise<boolean>;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  onCreateSpaceInvitation?: (
    spaceId: string,
    email: string,
    role: "ADMIN" | "MEMBER"
  ) => Promise<{ invitationId: string; inviteUrl: string; expiresAt: string }>;
}) {
  useEffect(() => {
    document.body.className = "app-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  const [searchParams] = useSearchParams();
  const { spaceId: routeSpaceId } = useParams<{ spaceId?: string }>();
  const spaceId = routeSpaceId ?? searchParams.get("spaceId");
  const projectParam = searchParams.get("project");
  const selectedSpace = spaces.find((space) => space.id === spaceId) ?? spaces.find((space) => space.name === projectParam) ?? (spaceId || projectParam ? undefined : spaces[0]);
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
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<"ADMIN" | "MEMBER">("MEMBER");
  const [inviteUrl, setInviteUrl] = useState("");
  const [inviteError, setInviteError] = useState("");
  const [isCreatingInvite, setIsCreatingInvite] = useState(false);
  const [memberActionError, setMemberActionError] = useState("");
  const [isMutatingMembers, setIsMutatingMembers] = useState(false);
  const canTransferOwner = Boolean(transferTargetEmail) && transferConfirm === "TRANSFER OWNER";

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="team-members-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState state="notFound" title="프로젝트를 찾을 수 없습니다" description="프로젝트 목록에서 접근 가능한 프로젝트를 선택해 주세요." />
      </AppShell>
    );
  }

  async function handleCopyInvite(value: string) {
    try {
      await navigator.clipboard.writeText(value);
    } catch {
      // Ignore clipboard errors in unsupported environments.
    }
  }

  async function handleCreateInvite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedSpace || !inviteEmail.trim() || !onCreateSpaceInvitation || isCreatingInvite) {
      return;
    }
    setInviteError("");
    setIsCreatingInvite(true);
    try {
      const created = await onCreateSpaceInvitation(selectedSpace.id, inviteEmail.trim(), inviteRole);
      setInviteUrl(created.inviteUrl);
      setInviteEmail("");
    } catch (error) {
      setInviteError(error instanceof Error ? error.message : "Space 초대를 생성하지 못했습니다.");
    } finally {
      setIsCreatingInvite(false);
    }
  }

  async function handleOwnerTransfer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const target = transferCandidates.find((member) => member.email === transferTargetEmail);
    if (!canTransferOwner || !target || !onTransferOwner || isMutatingMembers) {
      return;
    }

    setMemberActionError("");
    setIsMutatingMembers(true);
    try {
      const transferred = await onTransferOwner(projectName, target.memberId, target.email, previousOwnerRole, transferConfirm);
      if (transferred) {
        setTransferTargetEmail("");
        setPreviousOwnerRole("ADMIN");
        setTransferConfirm("");
      } else {
        setMemberActionError("Owner 권한을 이양하지 못했습니다.");
      }
    } catch (error) {
      setMemberActionError(error instanceof Error ? error.message : "Owner 권한을 이양하지 못했습니다.");
    } finally {
      setIsMutatingMembers(false);
    }
  }

  async function handleMemberRoleChange(member: TeamMember, role: Exclude<TeamMember["spaceRole"], "OWNER">) {
    if (!onUpdateMemberRole || isMutatingMembers) {
      return;
    }
    setMemberActionError("");
    setIsMutatingMembers(true);
    try {
      if (!await onUpdateMemberRole(projectName, member.memberId, member.email, role)) {
        setMemberActionError("멤버 역할을 변경하지 못했습니다.");
      }
    } catch (error) {
      setMemberActionError(error instanceof Error ? error.message : "멤버 역할을 변경하지 못했습니다.");
    } finally {
      setIsMutatingMembers(false);
    }
  }

  async function handleRemoveMember(member: TeamMember) {
    if (!onRemoveMember || isMutatingMembers) {
      return;
    }
    setMemberActionError("");
    setIsMutatingMembers(true);
    try {
      if (!await onRemoveMember(projectName, member.memberId, member.email)) {
        setMemberActionError("멤버를 제거하지 못했습니다.");
      }
    } catch (error) {
      setMemberActionError(error instanceof Error ? error.message : "멤버를 제거하지 못했습니다.");
    } finally {
      setIsMutatingMembers(false);
    }
  }

  return (
    <AppShell
      contentClassName="team-members-main"
      sidebar={(
        <WorkspaceSidebar
          activeItem="members"
          contextOverride={projectName}
          mode="project"
          onCreateProject={onCreateProject}
          projectName={projectName}
          spaceId={selectedSpace?.id}
        />
      )}
    >
      <PageHeader
        actions={(
          <div aria-label="Space 멤버 초대" className="team-members-invite-panel">
            <div className="team-members-panel-heading">
              <strong>Space 초대</strong>
              <span>프로젝트 범위로 초대합니다.</span>
            </div>
            {onCreateSpaceInvitation ? (
              <form aria-label="Space 초대 생성" onSubmit={handleCreateInvite}>
                <label>
                  <span>이메일</span>
                  <input
                    aria-label="초대 이메일"
                    onChange={(event) => setInviteEmail(event.target.value)}
                    placeholder="name@company.com"
                    type="email"
                    value={inviteEmail}
                  />
                </label>
                <label>
                  <span>역할</span>
                  <select aria-label="초대 역할" onChange={(event) => setInviteRole(event.target.value as "ADMIN" | "MEMBER")} value={inviteRole}>
                    <option value="MEMBER">MEMBER</option>
                    <option value="ADMIN">ADMIN</option>
                  </select>
                </label>
                <button className="team-members-invite-button" disabled={isCreatingInvite || !inviteEmail.trim()} type="submit">
                  {isCreatingInvite ? "생성 중..." : "초대 링크 생성"}
                </button>
              </form>
            ) : (
              <button className="team-members-invite-button" onClick={() => void handleCopyInvite(invite.link)} type="button">
                Space 초대 링크 복사
              </button>
            )}
            {inviteError ? <p aria-live="assertive" role="alert">{inviteError}</p> : null}
            {inviteUrl ? (
              <button className="team-members-invite-button secondary" onClick={() => void handleCopyInvite(inviteUrl)} type="button">
                생성된 링크 복사
              </button>
            ) : null}
            <button
              aria-label={`Space 초대 코드 ${invite.code} 복사`}
              className="team-members-invite-code"
              onClick={() => void handleCopyInvite(invite.code)}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  void handleCopyInvite(invite.code);
                }
              }}
              type="button"
            >
              <span>Space 초대 코드</span>
              <strong>{invite.code}</strong>
            </button>
          </div>
        )}
        breadcrumb={(
          <>
            <Link to="/spaces">프로젝트 목록</Link>
            <span aria-hidden="true">/</span>
            <Link to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>{selectedSpace.name}</Link>
            <span aria-hidden="true">/</span>
            <strong>Members</strong>
          </>
        )}
        description="Space 멤버의 역할과 접근 범위를 관리하고, 회의 참가 요청과 소유권 변경을 검토합니다."
        eyebrow="Access control"
        meta={(
          <div aria-label="멤버 요약" className="team-members-summary">
            <span><strong>{members.length}</strong> 전체</span>
            <span><strong>{activeCount}</strong> 활성</span>
            <span><strong>{awayCount}</strong> 부재</span>
            <span><strong>{requests.length}</strong> 승인 대기</span>
          </div>
        )}
        title="Members"
      />

      <section aria-label="초대 방식" className="team-members-invitation-split">
        <article>
          <span className="team-members-section-kicker">Space access</span>
          <strong>Space invitation</strong>
          <p>프로젝트 멤버로 추가되어 Project Knowledge와 프로젝트 화면에 접근합니다.</p>
          <button onClick={() => void handleCopyInvite(invite.link)} type="button">Space 링크 복사</button>
        </article>
        <article>
          <span className="team-members-section-kicker">Meeting access</span>
          <strong>Meeting join request</strong>
          <p>URL 또는 코드 신청을 승인하면 해당 회의의 VIEWER 권한만 부여됩니다. Space 멤버는 생성되지 않습니다.</p>
          <Link to="/meeting-access">회의 참가 화면</Link>
        </article>
      </section>

      <section aria-label="회의 참가 승인 대기" className="team-members-approval-panel">
        <div className="team-members-approval-head">
          <div>
            <span className="team-members-section-kicker">Review queue</span>
            <strong>회의 참가 승인 대기</strong>
            <p>회의 URL 또는 참가 코드로 신청한 사용자는 승인 후 해당 회의에만 접근할 수 있습니다.</p>
          </div>
          <span className="team-members-count-badge">{requests.length}건</span>
        </div>

        {requests.length ? (
          <div className="team-members-request-list" role="list">
            {requests.map((request, index) => (
              <article key={request.id} className="team-members-request-card" role="listitem">
                <div className={`team-member-avatar tone-${(index % 3) + 1}`} aria-hidden="true">{request.name.slice(0, 1)}</div>
                <div className="team-members-request-copy">
                  <strong>{request.name}</strong>
                  <span>{request.email}</span>
                  <p>
                    {request.meetingIndex} {request.meetingTitle} · {request.source} 요청 · {request.requestedAt}
                  </p>
                </div>
                <div className="team-members-request-actions">
                  <button onClick={() => onRejectRequest?.(projectName, request.id)} type="button">거절</button>
                  <button className="primary" onClick={() => onApproveRequest?.(projectName, request.id)} type="button">승인</button>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className="team-members-request-empty" role="status">
            <strong>승인 대기 중인 요청이 없습니다.</strong>
            <p>새 참가 요청이 들어오면 이곳에서 회의 접근 권한을 검토할 수 있습니다.</p>
          </div>
        )}
      </section>

      <section aria-label="Owner 권한 이양" className="team-members-owner-transfer">
        <div>
          <span className="team-members-section-kicker">Ownership</span>
          <strong>Owner transfer</strong>
          <p>
            현재 owner {currentOwner?.name ?? "없음"} · 활성 SpaceMember만 대상입니다. 확인 문구 없이 이양되지 않습니다.
          </p>
        </div>
        <form aria-label="Owner 권한 이양" onSubmit={handleOwnerTransfer}>
          <label>
            <span>새 owner</span>
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
          </label>
          <label>
            <span>기존 owner 권한</span>
            <select
              aria-label="기존 owner 강등 role"
              onChange={(event) => setPreviousOwnerRole(event.target.value as Exclude<TeamMember["spaceRole"], "OWNER">)}
              value={previousOwnerRole}
            >
              <option value="ADMIN">ADMIN으로 변경</option>
              <option value="MEMBER">MEMBER로 변경</option>
            </select>
          </label>
          <label>
            <span>확인 문구</span>
            <input
              aria-label="owner transfer 확인 문구"
              onChange={(event) => setTransferConfirm(event.target.value)}
              placeholder="TRANSFER OWNER"
              type="text"
              value={transferConfirm}
            />
          </label>
          <button disabled={!canTransferOwner || isMutatingMembers} type="submit">이양</button>
        </form>
        {memberActionError ? <p aria-live="assertive" role="alert">{memberActionError}</p> : null}
      </section>

      <section aria-label="프로젝트 멤버 목록" className="team-members-directory">
        <div className="team-members-directory-head">
          <div>
            <span className="team-members-section-kicker">Directory</span>
            <h2>프로젝트 멤버</h2>
            <p>Space role과 회의 접근 범위를 확인하고 관리합니다.</p>
          </div>
          <span className="team-members-count-badge">{members.length}명</span>
        </div>

        <div aria-label="멤버 필터" className="team-members-toolbar">
          <div className="team-members-tabs" role="tablist" aria-label="멤버 상태 필터">
            <button aria-selected="true" className="active" role="tab" type="button">All</button>
            <button aria-selected="false" role="tab" type="button">Active</button>
            <button aria-selected="false" role="tab" type="button">Absent</button>
          </div>

          <div className="team-members-toolbar-right">
            <label className="team-members-search">
              <span aria-hidden="true">⌕</span>
              <input type="text" placeholder="멤버 이름, 역할, 권한 검색" aria-label="팀 멤버 검색" />
            </label>
          </div>
        </div>

        <div aria-label="멤버 표" className="team-members-table" role="table">
          <div className="team-members-table-head" role="row">
            <div className="team-members-col check" role="columnheader"><span aria-hidden="true" className="team-members-check checked">−</span></div>
            <div className="team-members-col member" role="columnheader">멤버</div>
            <div className="team-members-col role" role="columnheader">역할</div>
            <div className="team-members-col access" role="columnheader">권한</div>
            <div className="team-members-col project" role="columnheader">프로젝트</div>
            <div className="team-members-col actions" role="columnheader">관리</div>
          </div>

          <div className="team-members-table-body" role="rowgroup">
            {members.length ? (
              members.map((member, index) => (
                <article key={member.email} className={`team-members-row ${member.status === "active" ? "selected" : ""}`} role="row">
                  <div className="team-members-col check" role="cell">
                    <span aria-label={member.status === "active" ? "활성 멤버" : "부재 중 멤버"} className={`team-members-check ${member.status === "active" ? "checked" : ""}`}>
                      {member.status === "active" ? "✓" : ""}
                    </span>
                  </div>

                  <div className="team-members-col member" role="cell">
                    <div className={`team-member-avatar large tone-${(index % 3) + 1}`} aria-hidden="true">{member.name.slice(0, 1)}</div>
                    <div className="team-members-primary">
                      <strong>{member.name}</strong>
                      <span>{member.email}</span>
                    </div>
                  </div>

                  <div className="team-members-col role" role="cell">
                    <div className="team-members-primary">
                      <strong>{member.role}</strong>
                      <span>{member.since}</span>
                    </div>
                  </div>

                  <div className="team-members-col access" role="cell">
                    <div className="team-members-primary">
                      <RoleBadge role={member.spaceRole} scope="space" />
                      <span>{member.status === "active" ? `${member.access} · 활성 SpaceMember` : "부재 중 · 열람 중심"}</span>
                    </div>
                  </div>

                  <div className="team-members-col project" role="cell">
                    <div className="team-members-project-chip">{selectedSpace?.name}</div>
                    <div className="team-members-primary compact">
                      <strong>{member.rank}</strong>
                      <StatusBadge
                        context="generic"
                        label={member.status === "active" ? "활성 멤버" : "부재 중"}
                        status={member.status === "active" ? "ACTIVE" : "ARCHIVED"}
                      />
                    </div>
                  </div>

                  <div className="team-members-col actions" role="cell">
                    <select
                      aria-label={`${member.name} Space role 변경`}
                      disabled={member.spaceRole === "OWNER" || isMutatingMembers}
                      onChange={(event) => void handleMemberRoleChange(
                        member,
                        event.target.value as Exclude<TeamMember["spaceRole"], "OWNER">
                      )}
                      value={member.spaceRole === "OWNER" ? "OWNER" : member.spaceRole}
                    >
                      <option value="OWNER" disabled>OWNER</option>
                      <option value="ADMIN">ADMIN</option>
                      <option value="MEMBER">MEMBER</option>
                    </select>
                    <button
                      aria-label={`${member.name} 프로젝트에서 제거`}
                      disabled={member.spaceRole === "OWNER" || isMutatingMembers}
                      onClick={() => void handleRemoveMember(member)}
                      type="button"
                    >
                      제거
                    </button>
                  </div>
                </article>
              ))
            ) : (
              <div className="team-members-table-empty" role="status">
                <strong>아직 프로젝트 멤버가 없습니다.</strong>
                <p>Space invitation을 수락한 사용자가 이 목록에 표시됩니다.</p>
              </div>
            )}
          </div>
        </div>

        <section aria-label="멤버 목록 요약" className="team-members-footer">
          <span>전체 {members.length}명 · 활성 {activeCount}명 · 부재 {awayCount}명</span>
          <div className="team-members-pagination" aria-label="멤버 페이지 이동">
            <button aria-label="첫 페이지" type="button">{"‹‹"}</button>
            <button aria-label="이전 페이지" type="button">{"‹"}</button>
            <button aria-current="page" className="active" type="button">1</button>
            <button type="button">2</button>
            <button type="button">3</button>
            <button aria-label="다음 페이지" type="button">{"›"}</button>
          </div>
        </section>
      </section>
    </AppShell>
  );
}
