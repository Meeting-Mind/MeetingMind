import { useCallback, useState, type FormEvent } from "react";
import {
  reauthenticateWithGoogle,
  reauthenticateWithPassword,
  ReauthenticationRequiredError
} from "../auth/session";
import { GoogleCredentialButton } from "./GoogleCredentialButton";

export function AllDeviceLogoutModal({
  onClose,
  onLogoutAll
}: {
  onClose: () => void;
  onLogoutAll: () => Promise<void>;
}) {
  const [reauthenticationRequired, setReauthenticationRequired] = useState(false);
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();

  const completeLogoutAll = useCallback(async () => {
    await onLogoutAll();
  }, [onLogoutAll]);

  const completeReauthentication = useCallback(async (reauthenticate: () => Promise<void>) => {
    if (loading) {
      return;
    }
    setError("");
    setLoading(true);
    try {
      await reauthenticate();
      await completeLogoutAll();
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to complete re-authentication.");
    } finally {
      setLoading(false);
    }
  }, [completeLogoutAll, loading]);

  async function handleInitialLogout() {
    if (loading) {
      return;
    }
    setError("");
    setLoading(true);
    try {
      await completeLogoutAll();
    } catch (exception) {
      if (exception instanceof ReauthenticationRequiredError) {
        setReauthenticationRequired(true);
      } else {
        setError(exception instanceof Error ? exception.message : "Unable to sign out from all devices.");
      }
    } finally {
      setLoading(false);
    }
  }

  async function handlePasswordReauthentication(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await completeReauthentication(() => reauthenticateWithPassword(password));
  }

  const handleGoogleCredential = useCallback(async (credential: string) => {
    await completeReauthentication(() => reauthenticateWithGoogle(credential));
  }, [completeReauthentication]);

  const handleGoogleError = useCallback((message: string) => {
    setError(message);
  }, []);

  return (
    <div className="auth-modal-backdrop" role="presentation">
      <section aria-labelledby="logout-all-title" aria-modal="true" className="auth-modal" role="dialog">
        <button
          aria-label="Close sign out from all devices modal"
          className="auth-modal-close"
          disabled={loading}
          onClick={onClose}
          type="button"
        >
          ×
        </button>

        <p className="auth-modal-kicker">Account Security</p>
        <h2 id="logout-all-title">Sign out from all devices</h2>
        <p className="auth-modal-copy">
          End every active session, including the one in this browser.
        </p>

        {!reauthenticationRequired ? (
          <button
            className="auth-modal-submit auth-modal-danger"
            disabled={loading}
            onClick={() => void handleInitialLogout()}
            type="button"
          >
            {loading ? "Ending sessions..." : "Sign out from all devices"}
          </button>
        ) : (
          <>
            <div className="auth-modal-session-notice" role="status">
              Re-authenticate with your password or Google account to protect this action.
            </div>
            <form className="auth-modal-form" onSubmit={handlePasswordReauthentication}>
              <label>
                Password
                <input
                  autoComplete="current-password"
                  maxLength={128}
                  onChange={(event) => setPassword(event.target.value)}
                  required
                  type="password"
                  value={password}
                />
              </label>
              <button className="auth-modal-submit" disabled={loading} type="submit">
                {loading ? "Verifying..." : "Verify password and continue"}
              </button>
            </form>

            {clientId ? (
              <>
                <div className="auth-modal-divider">or</div>
                <GoogleCredentialButton
                  clientId={clientId}
                  disabled={loading}
                  onCredential={handleGoogleCredential}
                  onError={handleGoogleError}
                />
              </>
            ) : null}
          </>
        )}

        {error ? <div className="auth-modal-warning" role="alert">{error}</div> : null}
      </section>
    </div>
  );
}
