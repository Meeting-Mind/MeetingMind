import { useState } from "react";
import type { AuthSession } from "../auth/session";
import { AllDeviceLogoutModal } from "./AllDeviceLogoutModal";

export function AuthSessionControls({
  session,
  onLogout,
  onLogoutAll
}: {
  session: AuthSession;
  onLogout: () => Promise<void>;
  onLogoutAll: () => Promise<void>;
}) {
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [allDeviceLogoutOpen, setAllDeviceLogoutOpen] = useState(false);

  async function handleLogout() {
    if (loading) {
      return;
    }
    setError("");
    setLoading(true);
    try {
      await onLogout();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "로그아웃을 완료하지 못했습니다. 다시 시도해 주세요.");
      setLoading(false);
    }
  }

  return (
    <aside aria-label="사용자 세션" className="auth-session-controls">
      <div className="auth-session-user">
        <strong>{session.user.displayName}</strong>
        <span>{session.user.email}</span>
      </div>
      <div className="auth-session-actions">
        <button disabled={loading} onClick={() => void handleLogout()} type="button">
          {loading ? "로그아웃 중..." : "로그아웃"}
        </button>
        <button
          className="danger"
          disabled={loading}
          onClick={() => setAllDeviceLogoutOpen(true)}
          type="button"
        >
          모든 기기
        </button>
      </div>
      {error ? <p role="alert">{error}</p> : null}
      {allDeviceLogoutOpen ? (
        <AllDeviceLogoutModal
          onClose={() => setAllDeviceLogoutOpen(false)}
          onLogoutAll={onLogoutAll}
        />
      ) : null}
    </aside>
  );
}
