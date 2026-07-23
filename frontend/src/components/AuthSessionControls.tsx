import { useState } from "react";
import { Link } from "react-router-dom";
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
      setError(exception instanceof Error ? exception.message : "Unable to sign out. Please try again.");
      setLoading(false);
    }
  }

  return (
    <aside aria-label="User session" className="auth-session-controls">
      <div className="auth-session-user">
        <strong>{session.user.displayName}</strong>
        <span>{session.user.email}</span>
      </div>
      <div className="auth-session-actions">
        <Link to="/settings/account">Account settings</Link>
        <button disabled={loading} onClick={() => void handleLogout()} type="button">
          {loading ? "Signing out..." : "Sign out"}
        </button>
        <button
          className="danger"
          disabled={loading}
          onClick={() => setAllDeviceLogoutOpen(true)}
          type="button"
        >
          All devices
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
