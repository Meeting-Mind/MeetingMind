import { useState } from "react";
import type { AuthSession } from "../auth/session";

export function AuthSessionControls({
  session,
  onLogout
}: {
  session: AuthSession;
  onLogout: () => Promise<void>;
}) {
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

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
      <button disabled={loading} onClick={() => void handleLogout()} type="button">
        {loading ? "로그아웃 중..." : "로그아웃"}
      </button>
      {error ? <p role="alert">{error}</p> : null}
    </aside>
  );
}
