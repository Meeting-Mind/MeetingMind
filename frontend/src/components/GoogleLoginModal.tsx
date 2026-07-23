import { useCallback, useState, type FormEvent } from "react";
import { loginWithGoogle, loginWithPassword, signupWithPassword, type AuthSession } from "../auth/session";
import { GoogleCredentialButton } from "./GoogleCredentialButton";

export function GoogleLoginModal({
  isOpen,
  notice,
  onClose,
  onSuccess
}: {
  isOpen: boolean;
  notice?: string;
  onClose: () => void;
  onSuccess: (session: AuthSession) => void;
}) {
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();

  const handleGoogleCredential = useCallback(async (credential: string) => {
    setError("");
    setLoading(true);
    try {
      const session = await loginWithGoogle(credential);
      onSuccess(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to complete Google sign-in.");
    } finally {
      setLoading(false);
    }
  }, [onSuccess]);

  const handleGoogleError = useCallback((message: string) => {
    setError(message);
  }, []);

  async function handlePasswordSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setLoading(true);

    try {
      const session =
        mode === "signup"
          ? await signupWithPassword({ email: email.trim(), password, displayName: displayName.trim() })
          : await loginWithPassword({ email: email.trim(), password });
      onSuccess(session);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "Unable to process the sign-in request.");
    } finally {
      setLoading(false);
    }
  }

  if (!isOpen) {
    return null;
  }

  return (
    <div className="auth-modal-backdrop" role="presentation">
      <section aria-labelledby="auth-modal-title" aria-modal="true" className="auth-modal" role="dialog">
        <button aria-label="Close sign-in modal" className="auth-modal-close" onClick={onClose} type="button">
          ×
        </button>

        <p className="auth-modal-kicker">Sign In Required</p>
        <h2 id="auth-modal-title">Sign in to continue</h2>
        <p className="auth-modal-copy">Creating projects, joining meetings, and opening workspaces requires an authenticated session.</p>
        {notice ? <div className="auth-modal-session-notice" role="status">{notice}</div> : null}

        <div className="auth-modal-tabs" role="tablist" aria-label="Authentication mode">
          <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")} type="button">
            Sign in
          </button>
          <button className={mode === "signup" ? "active" : ""} onClick={() => setMode("signup")} type="button">
            Sign up
          </button>
        </div>

        <form className="auth-modal-form" onSubmit={handlePasswordSubmit}>
          {mode === "signup" ? (
            <label>
              Name
              <input
                autoComplete="name"
                onChange={(event) => setDisplayName(event.target.value)}
                required
                type="text"
                value={displayName}
              />
            </label>
          ) : null}
          <label>
            Email
            <input
              autoComplete="email"
              onChange={(event) => setEmail(event.target.value)}
              required
              type="email"
              value={email}
            />
          </label>
          <label>
            Password
            <input
              autoComplete={mode === "signup" ? "new-password" : "current-password"}
              minLength={8}
              onChange={(event) => setPassword(event.target.value)}
              required
              type="password"
              value={password}
            />
          </label>
          <button className="auth-modal-submit" disabled={loading} type="submit">
            {mode === "signup" ? "Create account" : "Sign in"}
          </button>
        </form>

        {!clientId ? (
          <div className="auth-modal-warning">
            <strong>Google sign-in is not configured.</strong>
            <span>Add `VITE_GOOGLE_CLIENT_ID` to `frontend/.env`.</span>
          </div>
        ) : null}

        {error ? <div className="auth-modal-warning">{error}</div> : null}

        <div className="auth-modal-divider">or</div>
        {clientId ? (
          <GoogleCredentialButton
            clientId={clientId}
            disabled={loading}
            onCredential={handleGoogleCredential}
            onError={handleGoogleError}
          />
        ) : null}
      </section>
    </div>
  );
}
