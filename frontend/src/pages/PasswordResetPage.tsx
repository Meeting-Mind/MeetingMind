import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { confirmPasswordReset, requestPasswordReset } from "../auth/session";

function takeResetTokenFromFragment(): string {
  const token = new URLSearchParams(window.location.hash.replace(/^#/, "")).get("token")?.trim() ?? "";
  if (token) {
    window.history.replaceState(null, "", window.location.pathname);
  }
  return token;
}

export function PasswordResetPage() {
  const navigate = useNavigate();
  const [token] = useState(takeResetTokenFromFragment);
  const [email, setEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError("");
    try {
      await requestPasswordReset(email.trim());
      setNotice("등록된 이메일이라면 비밀번호 재설정 안내를 보냈습니다.");
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "비밀번호 재설정 요청을 완료하지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleConfirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (newPassword !== confirmation) {
      setError("새 비밀번호가 일치하지 않습니다.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      await confirmPasswordReset(token, newPassword);
      setNotice("비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요.");
      window.setTimeout(() => navigate("/", { replace: true }), 800);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "비밀번호를 변경하지 못했습니다.");
      setLoading(false);
    }
  }

  return (
    <main className="password-reset-page">
      <section aria-labelledby="password-reset-title" className="password-reset-panel">
        <Link className="password-reset-back" to="/">MeetingMind</Link>
        <h1 id="password-reset-title">{token ? "새 비밀번호 설정" : "비밀번호 재설정"}</h1>
        <p>{token ? "새 비밀번호를 설정하면 모든 로그인 세션이 종료됩니다." : "계정 이메일로 재설정 안내를 보냅니다."}</p>
        {token ? (
          <form onSubmit={handleConfirm}>
            <label>
              새 비밀번호
              <input autoComplete="new-password" disabled={loading} minLength={8} onChange={(event) => setNewPassword(event.target.value)} required type="password" value={newPassword} />
            </label>
            <label>
              새 비밀번호 확인
              <input autoComplete="new-password" disabled={loading} minLength={8} onChange={(event) => setConfirmation(event.target.value)} required type="password" value={confirmation} />
            </label>
            <button disabled={loading} type="submit">비밀번호 변경</button>
          </form>
        ) : (
          <form onSubmit={handleRequest}>
            <label>
              이메일
              <input autoComplete="email" disabled={loading} onChange={(event) => setEmail(event.target.value)} required type="email" value={email} />
            </label>
            <button disabled={loading} type="submit">재설정 안내 보내기</button>
          </form>
        )}
        {notice ? <p className="password-reset-notice" role="status">{notice}</p> : null}
        {error ? <p className="password-reset-error" role="alert">{error}</p> : null}
      </section>
    </main>
  );
}
