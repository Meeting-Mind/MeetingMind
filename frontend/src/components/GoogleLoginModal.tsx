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
      setError(exception instanceof Error ? exception.message : "Google 로그인을 완료하지 못했습니다.");
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
      setError(exception instanceof Error ? exception.message : "로그인 요청을 처리하지 못했습니다.");
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
        <button aria-label="로그인 모달 닫기" className="auth-modal-close" onClick={onClose} type="button">
          ×
        </button>

        <p className="auth-modal-kicker">Sign In Required</p>
        <h2 id="auth-modal-title">로그인 후 이용할 수 있습니다</h2>
        <p className="auth-modal-copy">프로젝트 생성, 회의 입장, 워크스페이스 접근은 로그인 후 진행됩니다.</p>
        {notice ? <div className="auth-modal-session-notice" role="status">{notice}</div> : null}

        <div className="auth-modal-tabs" role="tablist" aria-label="인증 방식">
          <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")} type="button">
            로그인
          </button>
          <button className={mode === "signup" ? "active" : ""} onClick={() => setMode("signup")} type="button">
            회원가입
          </button>
        </div>

        <form className="auth-modal-form" onSubmit={handlePasswordSubmit}>
          {mode === "signup" ? (
            <label>
              이름
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
            이메일
            <input
              autoComplete="email"
              onChange={(event) => setEmail(event.target.value)}
              required
              type="email"
              value={email}
            />
          </label>
          <label>
            비밀번호
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
            {mode === "signup" ? "계정 만들기" : "로그인"}
          </button>
        </form>

        {!clientId ? (
          <div className="auth-modal-warning">
            <strong>Google 로그인 설정이 필요합니다.</strong>
            <span>`frontend/.env`에 `VITE_GOOGLE_CLIENT_ID`를 추가해주세요.</span>
          </div>
        ) : null}

        {error ? <div className="auth-modal-warning">{error}</div> : null}

        <div className="auth-modal-divider">또는</div>
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
