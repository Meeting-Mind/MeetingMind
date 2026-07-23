import { useState } from "react";
import { Link } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import { AllDeviceLogoutModal } from "../components/AllDeviceLogoutModal";
import { PageHeader } from "../components/common/PageHeader";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";

export function AccountSettingsPage({
  onLogout,
  onLogoutAll,
  session
}: {
  onLogout: () => Promise<void>;
  onLogoutAll: () => Promise<void>;
  session: AuthSession;
}) {
  const [allDeviceLogoutOpen, setAllDeviceLogoutOpen] = useState(false);
  const [logoutError, setLogoutError] = useState("");
  const [logoutLoading, setLogoutLoading] = useState(false);

  async function handleLogout() {
    if (logoutLoading) {
      return;
    }
    setLogoutError("");
    setLogoutLoading(true);
    try {
      await onLogout();
    } catch (cause: unknown) {
      setLogoutError(cause instanceof Error ? cause.message : "로그아웃하지 못했습니다.");
      setLogoutLoading(false);
    }
  }

  return (
    <AppShell
      contentClassName="account-settings-main"
      sidebar={<WorkspaceSidebar activeItem="catalog" />}
    >
      <PageHeader
        actions={(
          <Link className="mm-common-button mm-common-button--secondary" to="/spaces">
            워크스페이스로
          </Link>
        )}
        breadcrumb={(
          <>
            <Link to="/spaces">워크스페이스</Link>
            <span aria-hidden="true">/</span>
            <strong>계정 설정</strong>
          </>
        )}
        description="개인 계정과 로그인 세션을 관리합니다. 프로젝트 권한은 각 프로젝트 설정에서 관리합니다."
        eyebrow="Account settings"
        meta={<StatusBadge context="generic" label="세션 활성" status="ACTIVE" />}
        title="계정 설정"
      />

      <div className="account-settings-grid">
        <section aria-labelledby="account-profile-title" className="account-settings-surface">
          <div className="account-settings-surface-header">
            <div>
              <p className="account-settings-section-kicker">Profile</p>
              <h2 id="account-profile-title">프로필</h2>
            </div>
            <RoleBadge label="계정 사용자" role="MEMBER" scope="space" />
          </div>
          <dl className="account-settings-details">
            <div><dt>표시 이름</dt><dd>{session.user.displayName}</dd></div>
            <div><dt>이메일</dt><dd>{session.user.email}</dd></div>
            <div><dt>계정 상태</dt><dd><StatusBadge context="generic" label={session.user.status} status="ACTIVE" /></dd></div>
          </dl>
          <p className="account-settings-muted">프로필 변경과 이미지 저장 API가 준비되면 이 영역에 연결합니다.</p>
        </section>

        <section aria-labelledby="account-security-title" className="account-settings-surface">
          <div className="account-settings-surface-header">
            <div>
              <p className="account-settings-section-kicker">Security</p>
              <h2 id="account-security-title">보안과 세션</h2>
            </div>
            <StatusBadge context="generic" label="세션 활성" status="ACTIVE" />
          </div>
          <dl className="account-settings-details">
            <div><dt>유휴 만료</dt><dd>{new Date(session.session.idleExpiresAt).toLocaleString("ko-KR")}</dd></div>
            <div><dt>절대 만료</dt><dd>{new Date(session.session.expiresAt).toLocaleString("ko-KR")}</dd></div>
            <div><dt>로그인 유지</dt><dd>{session.session.rememberMe ? "사용" : "사용 안 함"}</dd></div>
          </dl>
          <div className="account-settings-actions">
            <button className="mm-common-button mm-common-button--secondary" disabled={logoutLoading} onClick={() => setAllDeviceLogoutOpen(true)} type="button">모든 기기 로그아웃</button>
            <button className="mm-common-button mm-common-button--danger" disabled={logoutLoading} onClick={() => void handleLogout()} type="button">{logoutLoading ? "로그아웃 중..." : "현재 기기 로그아웃"}</button>
          </div>
          {logoutError ? <p aria-live="polite" className="account-settings-error" role="alert">{logoutError}</p> : null}
        </section>
      </div>

      {allDeviceLogoutOpen ? (
        <AllDeviceLogoutModal
          onClose={() => setAllDeviceLogoutOpen(false)}
          onLogoutAll={onLogoutAll}
        />
      ) : null}
    </AppShell>
  );
}
