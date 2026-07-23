import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  approveMeetingJoinRequest,
  createMeetingJoinRequest,
  fetchMeetingJoinRequests,
  fetchMeetingParticipants,
  rejectMeetingJoinRequest
} from "../api/meetingAccess";
import type { AuthSession } from "../auth/session";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import type { MeetingJoinRequestSummary, MeetingRole } from "../types";

type AccessState = "idle" | "checking" | "submitting" | "pending" | "allowed" | "denied";

export function MeetingAccessPage({ session }: { session: AuthSession }) {
  const [searchParams] = useSearchParams();
  const [joinCodeOrUrl, setJoinCodeOrUrl] = useState(searchParams.get("joinCode") ?? "");
  const [meetingId, setMeetingId] = useState(searchParams.get("meetingId") ?? "");
  const [requestId, setRequestId] = useState("");
  const [accessState, setAccessState] = useState<AccessState>(meetingId ? "checking" : "idle");
  const [accessRole, setAccessRole] = useState<MeetingRole | "MANAGER_OVERRIDE" | null>(null);
  const [message, setMessage] = useState("");
  const [joinRequests, setJoinRequests] = useState<MeetingJoinRequestSummary[]>([]);
  const [requestsLoading, setRequestsLoading] = useState(false);
  const [reviewingRequestId, setReviewingRequestId] = useState("");

  const loadJoinRequests = useCallback(async (targetMeetingId: string) => {
    setRequestsLoading(true);
    try {
      const response = await fetchMeetingJoinRequests(session, targetMeetingId);
      setJoinRequests(response.requests);
    } catch {
      setJoinRequests([]);
    } finally {
      setRequestsLoading(false);
    }
  }, [session]);

  const checkAccess = useCallback(async (targetMeetingId: string) => {
    if (!targetMeetingId) {
      setAccessState("denied");
      setMessage("확인할 회의 ID가 없습니다. 회의 URL 또는 참가 코드를 먼저 제출해주세요.");
      return;
    }

    setAccessState("checking");
    setMessage("");

    try {
      const response = await fetchMeetingParticipants(session, targetMeetingId);
      const participant = response.participants.find((item) => item.userId === session.user.id);
      const nextRole = participant?.role ?? "MANAGER_OVERRIDE";
      setAccessRole(nextRole);
      setAccessState("allowed");
      setMessage("회의 접근 권한이 확인되었습니다.");
      if (nextRole === "HOST" || nextRole === "MANAGER_OVERRIDE") {
        await loadJoinRequests(targetMeetingId);
      } else {
        setJoinRequests([]);
      }
    } catch {
      setAccessRole(null);
      setAccessState(requestId ? "pending" : "denied");
      setMessage(
        requestId
          ? "아직 승인되지 않았습니다. HOST가 승인한 뒤 다시 확인해주세요."
          : "현재 이 회의에 접근할 수 없습니다. 참가 신청이 필요합니다."
      );
    }
  }, [loadJoinRequests, requestId, session]);

  useEffect(() => {
    document.body.className = "app-theme meeting-access-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  useEffect(() => {
    if (meetingId) {
      void checkAccess(meetingId);
    }
  }, [checkAccess, meetingId]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = joinCodeOrUrl.trim();
    if (!normalized || accessState === "submitting") {
      return;
    }

    setAccessState("submitting");
    setMessage("");

    try {
      const response = await createMeetingJoinRequest(session, { joinCodeOrUrl: normalized });
      setMeetingId(response.meetingId);
      setRequestId(response.requestId);
      setAccessRole(null);
      setAccessState("pending");
      setMessage("참가 신청을 보냈습니다. 승인 전에는 회의 데이터에 접근할 수 없습니다.");
    } catch (error) {
      setAccessState("denied");
      setAccessRole(null);
      setMessage(error instanceof Error ? error.message : "참가 신청을 처리하지 못했습니다.");
    }
  }

  async function handleReview(requestIdToReview: string, action: "approve" | "reject") {
    if (!meetingId || reviewingRequestId) {
      return;
    }

    setReviewingRequestId(requestIdToReview);
    try {
      if (action === "approve") {
        await approveMeetingJoinRequest(session, meetingId, requestIdToReview);
      } else {
        await rejectMeetingJoinRequest(session, meetingId, requestIdToReview);
      }
      await loadJoinRequests(meetingId);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "참가 신청을 처리하지 못했습니다.");
    } finally {
      setReviewingRequestId("");
    }
  }

  const canReviewRequests = accessRole === "HOST" || accessRole === "MANAGER_OVERRIDE";
  const accessStatusValue = accessState === "allowed" ? "ACTIVE" : accessState === "denied" ? "REVOKED" : accessState === "idle" ? "DRAFT" : "PENDING";
  const liveMeetingHref = meetingId
    ? `/live-meeting?${new URLSearchParams({ meetingId }).toString()}`
    : "/live-meeting";

  return (
    <div className="meeting-access-shell">
      <header className="meeting-access-header">
        <Link className="meeting-access-logo" to="/spaces">
          <span>meeting</span>
          <strong>mind</strong>
        </Link>
        <div className="meeting-access-user">
          <span>{session.user.displayName}</span>
          <strong>{session.user.email}</strong>
        </div>
      </header>

      <main className="meeting-access-main">
        <section className="meeting-access-intro">
          <p className="meeting-access-kicker">Meeting access</p>
          <h1>회의 참가 권한 확인</h1>
          <p>회의 URL 또는 참가 코드를 제출하면 HOST 승인 후 해당 회의에만 접근할 수 있습니다.</p>
        </section>

        <div className="meeting-access-grid">
          <section className="meeting-access-tool" aria-labelledby="meeting-access-form-title">
            <div className="meeting-access-section-head">
              <div>
                <span>참가 신청</span>
                <h2 id="meeting-access-form-title">회의 URL 또는 코드</h2>
              </div>
              <StatusBadge
                className={`meeting-access-state ${accessState}`}
                context="access"
                label={getStateLabel(accessState)}
                status={accessStatusValue}
              />
            </div>

            <form className="meeting-access-form" onSubmit={handleSubmit}>
              <label htmlFor="meeting-access-code">URL / 참가 코드</label>
              <div>
                <input
                  autoComplete="off"
                  id="meeting-access-code"
                  maxLength={2048}
                  onChange={(event) => setJoinCodeOrUrl(event.target.value)}
                  placeholder="회의 URL 또는 참가 코드 입력"
                  value={joinCodeOrUrl}
                />
                <button disabled={!joinCodeOrUrl.trim() || accessState === "submitting"} type="submit">
                  {accessState === "submitting" ? "신청 중" : "참가 신청"}
                </button>
              </div>
            </form>

            {message ? <p className={`meeting-access-message ${accessState}`}>{message}</p> : null}

            {meetingId ? (
              <div className="meeting-access-reference">
                <div>
                  <span>Meeting ID</span>
                  <strong>{meetingId}</strong>
                </div>
                {requestId ? (
                  <div>
                    <span>Request ID</span>
                    <strong>{requestId}</strong>
                  </div>
                ) : null}
              </div>
            ) : null}

            <div className="meeting-access-actions">
              {meetingId ? (
                <button
                  className="secondary"
                  disabled={accessState === "checking"}
                  onClick={() => void checkAccess(meetingId)}
                  type="button"
                >
                  접근 권한 다시 확인
                </button>
              ) : null}
              {accessState === "allowed" ? (
                <Link className="primary" to={liveMeetingHref}>회의 입장 준비</Link>
              ) : null}
            </div>
          </section>

          <aside className="meeting-access-summary">
            <div className={`meeting-access-verdict ${accessState}`}>
              <span>현재 판정</span>
              <strong>{accessState === "allowed" ? "접근 가능" : accessState === "pending" ? "승인 대기" : "접근 전 확인 필요"}</strong>
              {accessRole ? (
                <RoleBadge role={accessRole} scope="meeting" />
              ) : (
                <p>승인 전에는 회의 데이터와 AI 컨텍스트가 차단됩니다.</p>
              )}
            </div>

            <div className="meeting-access-boundary">
              <strong>권한 범위</strong>
              <dl>
                <div>
                  <dt>Meeting</dt>
                  <dd>{accessState === "allowed" ? "허용" : "차단"}</dd>
                </div>
                <div>
                  <dt>Project / Space</dt>
                  <dd>별도 SpaceMember 필요</dd>
                </div>
                <div>
                  <dt>Project AI</dt>
                  <dd>회의 참가 승인만으로는 미부여</dd>
                </div>
              </dl>
            </div>
          </aside>
        </div>

        {accessState === "allowed" && canReviewRequests ? (
          <section className="meeting-access-review" aria-labelledby="meeting-access-review-title">
            <div className="meeting-access-section-head">
              <div>
                <span>HOST / manager</span>
                <h2 id="meeting-access-review-title">회의 참가 신청</h2>
              </div>
              <button disabled={requestsLoading} onClick={() => void loadJoinRequests(meetingId)} type="button">
                새로고침
              </button>
            </div>

            {requestsLoading ? (
              <p className="meeting-access-review-empty">신청 목록을 불러오고 있습니다.</p>
            ) : joinRequests.filter((request) => request.status === "PENDING").length ? (
              <div className="meeting-access-review-list">
                {joinRequests
                  .filter((request) => request.status === "PENDING")
                  .map((request) => (
                    <article key={request.id}>
                      <div>
                        <strong>{request.userId}</strong>
                        <span>{new Date(request.requestedAt).toLocaleString("ko-KR")}</span>
                      </div>
                      <div>
                        <button
                          disabled={Boolean(reviewingRequestId)}
                          onClick={() => void handleReview(request.id, "reject")}
                          type="button"
                        >
                          거절
                        </button>
                        <button
                          className="primary"
                          disabled={Boolean(reviewingRequestId)}
                          onClick={() => void handleReview(request.id, "approve")}
                          type="button"
                        >
                          {reviewingRequestId === request.id ? "처리 중" : "VIEWER 승인"}
                        </button>
                      </div>
                    </article>
                  ))}
              </div>
            ) : (
              <p className="meeting-access-review-empty">현재 승인 대기 중인 참가 신청이 없습니다.</p>
            )}
          </section>
        ) : null}
      </main>
    </div>
  );
}

function getStateLabel(state: AccessState) {
  if (state === "checking") return "확인 중";
  if (state === "submitting") return "신청 중";
  if (state === "pending") return "승인 대기";
  if (state === "allowed") return "접근 가능";
  if (state === "denied") return "접근 차단";
  return "미확인";
}
