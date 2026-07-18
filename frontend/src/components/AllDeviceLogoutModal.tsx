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
      setError(exception instanceof Error ? exception.message : "재인증을 완료하지 못했습니다.");
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
        setError(exception instanceof Error ? exception.message : "모든 기기 로그아웃을 완료하지 못했습니다.");
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
          aria-label="모든 기기 로그아웃 닫기"
          className="auth-modal-close"
          disabled={loading}
          onClick={onClose}
          type="button"
        >
          ×
        </button>

        <p className="auth-modal-kicker">Account Security</p>
        <h2 id="logout-all-title">모든 기기에서 로그아웃</h2>
        <p className="auth-modal-copy">
          현재 브라우저를 포함해 로그인된 모든 기기의 세션을 종료합니다.
        </p>

        {!reauthenticationRequired ? (
          <button
            className="auth-modal-submit auth-modal-danger"
            disabled={loading}
            onClick={() => void handleInitialLogout()}
            type="button"
          >
            {loading ? "세션 종료 중..." : "모든 기기에서 로그아웃"}
          </button>
        ) : (
          <>
            <div className="auth-modal-session-notice" role="status">
              계정 보호를 위해 비밀번호 또는 Google 계정으로 다시 인증해 주세요.
            </div>
            <form className="auth-modal-form" onSubmit={handlePasswordReauthentication}>
              <label>
                비밀번호
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
                {loading ? "확인 중..." : "비밀번호 확인 후 로그아웃"}
              </button>
            </form>

            {clientId ? (
              <>
                <div className="auth-modal-divider">또는</div>
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
