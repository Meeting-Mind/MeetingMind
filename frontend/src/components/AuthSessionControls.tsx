import { useEffect, useState, type FormEvent } from "react";
import type { AuthSession, AuthUser } from "../auth/session";
import { GoogleReauthenticationButton } from "./GoogleReauthenticationButton";

export function AuthSessionControls({
  accountManagementAvailable,
  session,
  onLogout,
  onLogoutAll,
  onChangePassword,
  onProfileUpdate,
  onProfileImageUpdate,
  onWithdraw
}: {
  accountManagementAvailable: boolean;
  session: AuthSession;
  onLogout: () => Promise<void>;
  onLogoutAll: (credentials: { password?: string; googleCredential?: string }) => Promise<void>;
  onChangePassword: (passwords: { currentPassword: string; newPassword: string }) => Promise<void>;
  onProfileUpdate: (displayName: string) => Promise<AuthUser>;
  onProfileImageUpdate: (image: File) => Promise<AuthUser>;
  onWithdraw: (credentials: { password?: string; googleCredential?: string }) => Promise<void>;
}) {
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [displayName, setDisplayName] = useState(session.user.displayName);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [profileImage, setProfileImage] = useState<File | null>(null);
  const [logoutAllPassword, setLogoutAllPassword] = useState("");
  const [withdrawalConfirmation, setWithdrawalConfirmation] = useState("");
  const [withdrawalPassword, setWithdrawalPassword] = useState("");

  useEffect(() => {
    setDisplayName(session.user.displayName);
  }, [session.user.displayName]);

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

  async function runAccountAction(action: () => Promise<void>) {
    if (loading) {
      return;
    }
    setError("");
    setLoading(true);
    try {
      await action();
      setLoading(false);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "계정 요청을 완료하지 못했습니다. 다시 시도해 주세요.");
      setLoading(false);
    }
  }

  function handleProfileUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void runAccountAction(async () => {
      const user = await onProfileUpdate(displayName.trim());
      setDisplayName(user.displayName);
      setSettingsOpen(false);
    });
  }

  function handlePasswordChange(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void runAccountAction(async () => {
      await onChangePassword({ currentPassword, newPassword });
    });
  }

  function handleProfileImage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!profileImage) {
      setError("업로드할 프로필 사진을 선택해 주세요.");
      return;
    }
    void runAccountAction(async () => {
      await onProfileImageUpdate(profileImage);
      setProfileImage(null);
    });
  }

  function handleLogoutAll(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void runAccountAction(async () => {
      await onLogoutAll({
        password: logoutAllPassword || undefined
      });
    });
  }

  function handleGoogleLogoutAll(googleCredential: string) {
    void runAccountAction(() => onLogoutAll({ googleCredential }));
  }

  function handleWithdrawal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (withdrawalConfirmation !== "DELETE") {
      setError("확인 문자열 DELETE를 입력해 주세요.");
      return;
    }
    void runAccountAction(async () => {
      await onWithdraw({ password: withdrawalPassword || undefined });
    });
  }

  function handleGoogleWithdrawal(googleCredential: string) {
    if (withdrawalConfirmation !== "DELETE") {
      setError("확인 문자열 DELETE를 입력해 주세요.");
      return;
    }
    void runAccountAction(() => onWithdraw({ googleCredential }));
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
      {accountManagementAvailable ? (
        <button
          aria-expanded={settingsOpen}
          disabled={loading}
          onClick={() => setSettingsOpen((open) => !open)}
          type="button"
        >
          계정
        </button>
      ) : null}
      {accountManagementAvailable && settingsOpen ? (
        <div className="auth-session-settings">
          <form onSubmit={handleProfileUpdate}>
            <label>
              표시 이름
              <input
                disabled={loading}
                maxLength={100}
                onChange={(event) => setDisplayName(event.target.value)}
                required
                value={displayName}
              />
            </label>
            <button disabled={loading} type="submit">이름 저장</button>
          </form>
          <form onSubmit={handleProfileImage}>
            <label>
              프로필 사진
              <input
                accept="image/jpeg,image/png,image/webp"
                disabled={loading}
                onChange={(event) => setProfileImage(event.target.files?.item(0) ?? null)}
                type="file"
              />
            </label>
            <button disabled={loading} type="submit">사진 저장</button>
          </form>
          <form onSubmit={handlePasswordChange}>
            <label>
              현재 비밀번호
              <input
                autoComplete="current-password"
                disabled={loading}
                onChange={(event) => setCurrentPassword(event.target.value)}
                required
                type="password"
                value={currentPassword}
              />
            </label>
            <label>
              새 비밀번호
              <input
                autoComplete="new-password"
                disabled={loading}
                minLength={8}
                onChange={(event) => setNewPassword(event.target.value)}
                required
                type="password"
                value={newPassword}
              />
            </label>
            <button disabled={loading} type="submit">비밀번호 변경</button>
          </form>
          <form onSubmit={handleLogoutAll}>
            <label>
              비밀번호 재인증
              <input
                autoComplete="current-password"
                disabled={loading}
                onChange={(event) => setLogoutAllPassword(event.target.value)}
                type="password"
                value={logoutAllPassword}
              />
            </label>
            <button disabled={loading} type="submit">모든 기기 로그아웃</button>
            <GoogleReauthenticationButton
              disabled={loading}
              onCredential={handleGoogleLogoutAll}
              onError={setError}
            />
          </form>
          <form onSubmit={handleWithdrawal}>
            <label>
              탈퇴 확인
              <input
                disabled={loading}
                onChange={(event) => setWithdrawalConfirmation(event.target.value)}
                placeholder="DELETE"
                required
                value={withdrawalConfirmation}
              />
            </label>
            <label>
              비밀번호 재인증
              <input
                autoComplete="current-password"
                disabled={loading}
                onChange={(event) => setWithdrawalPassword(event.target.value)}
                type="password"
                value={withdrawalPassword}
              />
            </label>
            <button disabled={loading} type="submit">계정 탈퇴</button>
            <GoogleReauthenticationButton
              disabled={loading}
              onCredential={handleGoogleWithdrawal}
              onError={setError}
            />
          </form>
        </div>
      ) : null}
      {error ? <p role="alert">{error}</p> : null}
    </aside>
  );
}
