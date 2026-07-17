import { useEffect, useRef, useState, type FormEvent } from "react";
import { loginWithGoogle, loginWithPassword, signupWithPassword, type AuthSession } from "../auth/session";

type GoogleCredentialResponse = {
  credential?: string;
};

type GoogleAuthUser = {
  email: string;
  name: string;
  picture?: string;
};

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string;
            callback: (response: GoogleCredentialResponse) => void;
          }) => void;
          renderButton: (
            element: HTMLElement,
            options: {
              theme?: "outline" | "filled_blue" | "filled_black";
              size?: "large" | "medium" | "small";
              shape?: "rectangular" | "pill" | "circle" | "square";
              text?: "signin_with" | "signup_with" | "continue_with" | "signin";
              width?: number;
            }
          ) => void;
        };
      };
    };
  }
}

function parseCredential(credential: string): GoogleAuthUser | null {
  const [, payload] = credential.split(".");
  if (!payload) {
    return null;
  }

  try {
    const decoded = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    return {
      email: decoded.email,
      name: decoded.name,
      picture: decoded.picture
    };
  } catch {
    return null;
  }
}

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
  const buttonRef = useRef<HTMLDivElement | null>(null);
  const [scriptReady, setScriptReady] = useState(Boolean(window.google));
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();

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

  useEffect(() => {
    if (window.google) {
      setScriptReady(true);
      return;
    }

    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.onload = () => setScriptReady(true);
    script.onerror = () => setError("Google 로그인 스크립트를 불러오지 못했습니다.");
    document.head.appendChild(script);

    return () => {
      script.remove();
    };
  }, []);

  useEffect(() => {
    if (!isOpen || !scriptReady || !clientId || !buttonRef.current || !window.google) {
      return;
    }

    setError("");
    buttonRef.current.innerHTML = "";

    window.google.accounts.id.initialize({
      client_id: clientId,
      callback: async (response) => {
        if (!response.credential) {
          setError("Google 로그인 응답을 확인하지 못했습니다.");
          return;
        }

        const user = parseCredential(response.credential);
        if (!user) {
          setError("로그인 정보를 해석하지 못했습니다.");
          return;
        }

        setError("");
        setLoading(true);
        try {
          const session = await loginWithGoogle(response.credential);
          onSuccess(session);
        } catch (exception) {
          setError(exception instanceof Error ? exception.message : `${user.email} Google 로그인을 완료하지 못했습니다.`);
        } finally {
          setLoading(false);
        }
      }
    });

    window.google.accounts.id.renderButton(buttonRef.current, {
      theme: "outline",
      size: "large",
      shape: "pill",
      text: "continue_with",
      width: 320
    });
  }, [clientId, isOpen, onSuccess, scriptReady]);

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
        <div className="auth-modal-button" ref={buttonRef} />
      </section>
    </div>
  );
}
