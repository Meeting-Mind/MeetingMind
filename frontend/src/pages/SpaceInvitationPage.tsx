import { useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { acceptSpaceInvitation, declineSpaceInvitation } from "../api/spaces";
import type { AuthSession } from "../auth/session";
import { StatusBadge } from "../components/common/StatusBadge";

type InvitationResolution = "ready" | "submitting" | "accepted" | "declined" | "error";

function tokenFromFragment(hash: string): string {
  const fragment = hash.startsWith("#") ? hash.slice(1) : hash;
  return new URLSearchParams(fragment).get("token")?.trim() ?? "";
}

export function SpaceInvitationPage({ session }: { session: AuthSession }) {
  const { invitationId = "", spaceId = "" } = useParams();
  const location = useLocation();
  const [token] = useState(() => tokenFromFragment(location.hash));
  const [resolution, setResolution] = useState<InvitationResolution>("ready");
  const [message, setMessage] = useState("");

  useEffect(() => {
    document.body.className = "app-theme space-invitation-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  useEffect(() => {
    if (location.hash) {
      window.history.replaceState(null, "", `${location.pathname}${location.search}`);
    }
  }, [location.hash, location.pathname, location.search]);

  async function resolveInvitation(action: "accept" | "decline") {
    if (!spaceId || !invitationId || !token || resolution === "submitting") {
      setResolution("error");
      setMessage("초대 링크가 올바르지 않거나 토큰이 없습니다. 초대장을 다시 받아주세요.");
      return;
    }

    setResolution("submitting");
    setMessage("");

    try {
      if (action === "accept") {
        await acceptSpaceInvitation(session, spaceId, invitationId, { token });
        setResolution("accepted");
        setMessage("Space 초대를 수락했습니다. 이제 Space 목록에서 프로젝트에 접근할 수 있습니다.");
      } else {
        await declineSpaceInvitation(session, spaceId, invitationId, { token });
        setResolution("declined");
        setMessage("Space 초대를 거절했습니다.");
      }
    } catch (error) {
      setResolution("error");
      setMessage(error instanceof Error ? error.message : "Space 초대를 처리하지 못했습니다.");
    }
  }

  const isComplete = resolution === "accepted" || resolution === "declined";
  const resolutionStatus = resolution === "accepted" ? "COMPLETED" : resolution === "declined" ? "ARCHIVED" : resolution === "error" ? "FAILED" : resolution === "submitting" ? "PROCESSING" : "PENDING";
  const resolutionLabel = resolution === "accepted"
    ? "수락됨"
    : resolution === "declined"
      ? "거절됨"
      : resolution === "error"
        ? "처리 실패"
        : resolution === "submitting"
          ? "처리 중"
          : "응답 대기";

  return (
    <div className="space-invitation-shell">
      <header className="space-invitation-header">
        <Link className="space-invitation-logo" to="/spaces">
          <span>meeting</span>
          <strong>mind</strong>
        </Link>
        <div className="space-invitation-user">
          <span>{session.user.displayName}</span>
          <strong>{session.user.email}</strong>
        </div>
      </header>

      <main className="space-invitation-main">
        <section className="space-invitation-card" aria-labelledby="space-invitation-title">
          <p className="space-invitation-kicker">Space invitation</p>
          <h1 id="space-invitation-title">Space 초대 응답</h1>
          <p>현재 로그인한 이메일로 발급된 Space 초대인지 확인한 뒤 수락하거나 거절할 수 있습니다.</p>
          <StatusBadge context="generic" label={resolutionLabel} status={resolutionStatus} />

          {message ? <p className={`space-invitation-message ${resolution}`} role={resolution === "error" ? "alert" : undefined}>{message}</p> : null}

          {isComplete ? (
            <Link className="space-invitation-primary" to="/spaces">Space 목록으로 이동</Link>
          ) : (
            <div className="space-invitation-actions">
              <button
                className="secondary"
                disabled={resolution === "submitting" || !token}
                onClick={() => void resolveInvitation("decline")}
                type="button"
              >
                거절
              </button>
              <button
                className="primary"
                disabled={resolution === "submitting" || !token}
                onClick={() => void resolveInvitation("accept")}
                type="button"
              >
                {resolution === "submitting" ? "처리 중..." : "초대 수락"}
              </button>
            </div>
          )}

          {!token ? <p className="space-invitation-token-warning">초대 token이 없습니다. 원본 초대 링크로 다시 접속해주세요.</p> : null}
        </section>
      </main>
    </div>
  );
}
