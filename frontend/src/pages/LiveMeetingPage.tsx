import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { fetchMeetingParticipants } from "../api/workspace";
import type { AuthSession } from "../auth/session";
import { useLiveMeetingDetail } from "../hooks/useLiveMeetingDetail";
import type { MeetingRole } from "../types";
import type { WorkspaceData } from "../types";

type WaitingParticipant = {
  name: string;
  status: "entered" | "checking" | "waiting";
};

const waitingParticipants: WaitingParticipant[] = [
  { name: "김진수", status: "entered" },
  { name: "박서윤", status: "entered" },
  { name: "정도윤", status: "waiting" }
];

function getParticipantTone(index: number) {
  return ["blue", "orange", "gold"][index % 3];
}

function getStatusLabel(status: WaitingParticipant["status"]) {
  if (status === "entered") {
    return "입장됨";
  }

  if (status === "checking") {
    return "확인 중";
  }

  return "대기중";
}

function getStatusClass(status: WaitingParticipant["status"]) {
  if (status === "entered") {
    return "green";
  }

  if (status === "checking") {
    return "blue";
  }

  return "amber";
}

function MicGlyph({ muted }: { muted: boolean }) {
  return (
    <svg aria-hidden="true" fill="none" height="26" viewBox="0 0 24 24" width="26">
      <path
        d="M12 3a3 3 0 0 1 3 3v5a3 3 0 1 1-6 0V6a3 3 0 0 1 3-3Z"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
      />
      <path
        d="M18 10a6 6 0 0 1-12 0M12 16v5M8 21h8"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
      />
      {muted ? <path d="M4 4l16 16" stroke="currentColor" strokeLinecap="round" strokeWidth="2.2" /> : null}
    </svg>
  );
}

function CameraGlyph({ disabled }: { disabled: boolean }) {
  return (
    <svg aria-hidden="true" fill="none" height="26" viewBox="0 0 24 24" width="26">
      <path
        d="M4 8.5A2.5 2.5 0 0 1 6.5 6H14a2 2 0 0 1 1.6.8l1.1 1.45A2 2 0 0 0 18.3 9H19a2 2 0 0 1 2 2v5.5a2.5 2.5 0 0 1-2.5 2.5h-12A2.5 2.5 0 0 1 4 16.5v-8Z"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
      />
      <circle cx="12" cy="13" r="3.2" stroke="currentColor" strokeWidth="2" />
      {disabled ? <path d="M4 4l16 16" stroke="currentColor" strokeLinecap="round" strokeWidth="2.2" /> : null}
    </svg>
  );
}

function MutedBadgeGlyph() {
  return (
    <svg aria-hidden="true" fill="none" height="14" viewBox="0 0 24 24" width="14">
      <path
        d="M12 3a3 3 0 0 1 3 3v5a3 3 0 1 1-6 0V6a3 3 0 0 1 3-3Z"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
      />
      <path
        d="M18 10a6 6 0 0 1-12 0M12 16v5M8 21h8M4 4l16 16"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
      />
    </svg>
  );
}

type AccessCheckState = "checking" | "allowed" | "denied";

export function LiveMeetingPage({
  data: fallbackData,
  session
}: {
  data: WorkspaceData["liveMeeting"];
  session: AuthSession;
}) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [isEntryModalOpen, setIsEntryModalOpen] = useState(true);
  const [hasEnteredPrejoin, setHasEnteredPrejoin] = useState(false);
  const [cameraEnabled, setCameraEnabled] = useState(true);
  const [micEnabled, setMicEnabled] = useState(true);
  const [isLoadingMedia, setIsLoadingMedia] = useState(true);
  const [mediaError, setMediaError] = useState("");
  const [hasCameraStream, setHasCameraStream] = useState(false);
  const [hasMicStream, setHasMicStream] = useState(false);
  const [accessCheckState, setAccessCheckState] = useState<AccessCheckState>("checking");
  const [accessRole, setAccessRole] = useState<MeetingRole | "MANAGER_OVERRIDE" | null>(null);
  const [accessMessage, setAccessMessage] = useState("회의 접근 권한을 확인하고 있습니다.");
  const spaceId = searchParams.get("spaceId");
  const meetingId = searchParams.get("meetingId");
  const projectName = searchParams.get("project") ?? "FinPilot Renewal";
  const { liveMeeting: data, error: liveMeetingError } = useLiveMeetingDetail(session, meetingId, fallbackData);
  const meetingTitle = searchParams.get("meeting") ?? data.overview.title;

  useEffect(() => {
    let active = true;

    if (!meetingId) {
      setAccessCheckState("denied");
      setAccessMessage("회의 ID가 없어 접근 권한을 확인할 수 없습니다.");
      return () => {
        active = false;
      };
    }

    setAccessCheckState("checking");
    setAccessMessage("회의 접근 권한을 확인하고 있습니다.");

    void fetchMeetingParticipants(session, meetingId)
      .then((response) => {
        if (!active) {
          return;
        }
        const participant = response.participants.find((item) => item.userId === session.user.id);
        setAccessRole(participant?.role ?? "MANAGER_OVERRIDE");
        setAccessCheckState("allowed");
        setAccessMessage("Backend에서 회의 접근 권한을 확인했습니다.");
      })
      .catch(() => {
        if (!active) {
          return;
        }
        setAccessRole(null);
        setAccessCheckState("denied");
        setAccessMessage("현재 이 회의에 접근할 수 없습니다. 참가 신청 또는 HOST 승인이 필요합니다.");
      });

    return () => {
      active = false;
    };
  }, [meetingId, session]);

  function handleCancelEntry() {
    const params = new URLSearchParams({ project: projectName });
    if (spaceId) {
      params.set("spaceId", spaceId);
    }
    navigate(`/project-overview?${params.toString()}`);
  }

  useEffect(() => {
    document.body.className = "app-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  useEffect(() => {
    return () => {
      if (streamRef.current) {
        streamRef.current.getTracks().forEach((track) => track.stop());
        streamRef.current = null;
      }
    };
  }, []);

  async function setupMedia() {
    if (!navigator.mediaDevices?.getUserMedia) {
      setMediaError("이 브라우저에서는 카메라 미리보기를 사용할 수 없습니다.");
      setIsLoadingMedia(false);
      setHasCameraStream(false);
      setHasMicStream(false);
      return;
    }

    setIsLoadingMedia(true);
    setMediaError("");

    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: true,
        audio: true
      });

      streamRef.current = stream;
      const videoTrack = stream.getVideoTracks()[0];
      const audioTrack = stream.getAudioTracks()[0];

      setHasCameraStream(Boolean(videoTrack));
      setHasMicStream(Boolean(audioTrack));
      setCameraEnabled(Boolean(videoTrack));
      setMicEnabled(Boolean(audioTrack));

      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
    } catch {
      try {
        const fallbackStream = await navigator.mediaDevices.getUserMedia({
          video: true,
          audio: false
        });

        streamRef.current = fallbackStream;
        const videoTrack = fallbackStream.getVideoTracks()[0];

        setHasCameraStream(Boolean(videoTrack));
        setHasMicStream(false);
        setCameraEnabled(Boolean(videoTrack));
        setMicEnabled(false);
        setMediaError("마이크 권한이 없어 카메라만 연결했습니다.");

        if (videoRef.current) {
          videoRef.current.srcObject = fallbackStream;
        }
      } catch {
        setHasCameraStream(false);
        setHasMicStream(false);
        setCameraEnabled(false);
        setMicEnabled(false);
        setMediaError("카메라 또는 마이크 권한을 확인한 뒤 다시 시도해주세요.");
      }
    } finally {
      setIsLoadingMedia(false);
    }
  }

  useEffect(() => {
    if (!hasEnteredPrejoin) {
      return;
    }

    void setupMedia();
  }, [hasEnteredPrejoin]);

  useEffect(() => {
    if (!videoRef.current || !streamRef.current) {
      return;
    }

    videoRef.current.srcObject = streamRef.current;
  }, [hasCameraStream]);

  function toggleCamera() {
    const track = streamRef.current?.getVideoTracks()[0];

    if (!track) {
      return;
    }

    const nextEnabled = !track.enabled;
    track.enabled = nextEnabled;
    setCameraEnabled(nextEnabled);
  }

  function toggleMic() {
    const track = streamRef.current?.getAudioTracks()[0];

    if (!track) {
      return;
    }

    const nextEnabled = !track.enabled;
    track.enabled = nextEnabled;
    setMicEnabled(nextEnabled);
  }

  function handleStartMeeting() {
    sessionStorage.setItem(
      "meetingmind-prejoin",
      JSON.stringify({
        micEnabled,
        cameraEnabled,
        name: session.user.displayName,
        identity: session.user.id,
        role: accessRole === "HOST" ? "호스트" : accessRole === "EDITOR" ? "편집자" : "참여자"
      })
    );

    navigate(`/live-room?${new URLSearchParams({ meetingId: meetingId ?? "" }).toString()}`);
  }

  const participantName = session.user.displayName || session.user.email;
  const previewInitial = participantName.slice(0, 1).toUpperCase();
  const accessRoleLabel = accessRole === "MANAGER_OVERRIDE" ? "OWNER/ADMIN override" : accessRole;
  const canManageParticipants = accessRole === "HOST" || accessRole === "MANAGER_OVERRIDE";

  if (accessCheckState !== "allowed") {
    const accessParams = new URLSearchParams();
    if (meetingId) {
      accessParams.set("meetingId", meetingId);
    }
    const accessHref = `/meeting-access${accessParams.toString() ? `?${accessParams.toString()}` : ""}`;

    return (
      <div className="meeting-prejoin-shell meeting-prejoin-gate-shell">
        <header className="meeting-prejoin-header">
          <Link className="meeting-prejoin-logo" to="/spaces">
            <span className="meeting-prejoin-logo-main">meeting</span>
            <span className="meeting-prejoin-logo-accent">mind</span>
          </Link>
          <div className="meeting-prejoin-profile"><span>{participantName}</span></div>
        </header>
        <main className="meeting-prejoin-gate-main">
          <section className={`meeting-prejoin-access-gate ${accessCheckState}`}>
            <span>{accessCheckState === "checking" ? "권한 확인 중" : "default-deny"}</span>
            <h1>{accessCheckState === "checking" ? "회의 접근 권한 확인" : "회의에 접근할 수 없습니다"}</h1>
            <p>{accessMessage}</p>
            <div>
              <Link className="secondary" to="/spaces">워크스페이스로</Link>
              {accessCheckState === "denied" ? <Link className="primary" to={accessHref}>참가 신청 확인</Link> : null}
            </div>
          </section>
        </main>
      </div>
    );
  }

  return (
    <div className="meeting-prejoin-shell">
      <header className="meeting-prejoin-header">
        <Link className="meeting-prejoin-logo" to="/">
          <span className="meeting-prejoin-logo-main">meeting</span>
          <span className="meeting-prejoin-logo-accent">mind</span>
        </Link>

        <div className="meeting-prejoin-profile">
          <span>회의 입장 준비</span>
          <div className="meeting-prejoin-user">
            <strong>{previewInitial}</strong>
            <span>{participantName}</span>
          </div>
        </div>
      </header>

      <main className="meeting-prejoin-main">
        <section className="meeting-prejoin-stage-panel">
          <div className="meeting-prejoin-breadcrumb">
            <span>live-meeting /</span>
            <strong>{meetingTitle}</strong>
          </div>

          <div className="meeting-prejoin-camera-stage">
            <div className="meeting-prejoin-stage-head">
              <span className="meeting-prejoin-preview-chip">카메라 미리보기</span>
            </div>

            {hasCameraStream ? (
              <video
                ref={videoRef}
                className={`meeting-prejoin-video ${cameraEnabled ? "" : "hidden"}`}
                autoPlay
                muted
                playsInline
              />
            ) : null}

            <div className="meeting-prejoin-video-overlay" />

            {!hasCameraStream || !cameraEnabled ? (
              <div className="meeting-prejoin-avatar-fallback">
                <span>{previewInitial}</span>
              </div>
            ) : null}

            {isLoadingMedia ? (
              <div className="meeting-prejoin-status-banner neutral">카메라와 마이크를 연결하는 중입니다.</div>
            ) : mediaError ? (
              <div className="meeting-prejoin-status-banner warning">{mediaError}</div>
            ) : (
              <div className="meeting-prejoin-status-banner success">카메라와 마이크가 정상 연결되었습니다.</div>
            )}

            <div className="meeting-prejoin-stage-owner">
              {!micEnabled ? (
                <span aria-label="음소거됨" className="meeting-prejoin-stage-owner-muted" title="음소거됨">
                  <MutedBadgeGlyph />
                </span>
              ) : null}
              <span>{participantName} ({accessRoleLabel})</span>
            </div>
          </div>

          <div className="meeting-prejoin-controls">
            <button
              className={`meeting-prejoin-control ${micEnabled ? "" : "off"}`}
              onClick={toggleMic}
              type="button"
              disabled={!hasMicStream}
              aria-label={micEnabled ? "마이크 음소거" : "마이크 켜기"}
              title={micEnabled ? "마이크 음소거" : "마이크 켜기"}
            >
              <span className="meeting-prejoin-control-icon">
                <MicGlyph muted={!micEnabled} />
              </span>
            </button>

            <button
              className={`meeting-prejoin-control camera ${cameraEnabled ? "" : "off"}`}
              onClick={toggleCamera}
              type="button"
              disabled={!hasCameraStream}
              aria-label={cameraEnabled ? "카메라 끄기" : "카메라 켜기"}
              title={cameraEnabled ? "카메라 끄기" : "카메라 켜기"}
            >
              <span className="meeting-prejoin-control-icon">
                <CameraGlyph disabled={!cameraEnabled} />
              </span>
            </button>

            <button className="meeting-prejoin-device-button" onClick={() => void setupMedia()} type="button">
              장치 설정
            </button>
          </div>

          <div className="meeting-prejoin-footer-row">
            <div className="meeting-prejoin-actions">
              {canManageParticipants ? (
                <Link className="meeting-prejoin-secondary" to="/team-members">참가 신청 관리</Link>
              ) : null}
              <button className="meeting-prejoin-primary" onClick={handleStartMeeting} type="button">
                {accessRole === "HOST" ? "회의 시작" : "회의 입장"} →
              </button>
            </div>
          </div>
        </section>

        <aside className="meeting-prejoin-sidebar">
          {liveMeetingError ? <div className="live-meeting-data-warning">{liveMeetingError}</div> : null}

          <section className="meeting-prejoin-info-card">
            <h1>{meetingTitle}</h1>
            <p>{data.overview.subtitle.split("·")[0]?.trim() ?? data.overview.subtitle}</p>

            <div className="meeting-prejoin-meta-grid">
              <article>
                <span>시작 예정</span>
                <strong>{data.startsAt}</strong>
              </article>
            </div>

            <div className="meeting-prejoin-access-note">
              <strong>참여 권한 확인됨</strong>
              <span>{accessRoleLabel} 권한으로 입장합니다</span>
            </div>
          </section>

          <section className="meeting-prejoin-info-card">
            <div className="meeting-prejoin-section-head">
              <strong>대기 참가자</strong>
              <span>회의 생성 시 설정된 권한</span>
            </div>

            <div className="meeting-prejoin-waiting-list">
              {waitingParticipants.map((participant, index) => (
                <article key={participant.name} className="meeting-prejoin-waiting-item">
                  <div className="meeting-prejoin-waiting-main">
                    <span className={`meeting-prejoin-waiting-avatar ${getParticipantTone(index)}`}>{participant.name.slice(0, 1)}</span>
                    <strong>{participant.name}</strong>
                  </div>
                  <span className={`meeting-prejoin-waiting-badge ${getStatusClass(participant.status)}`}>
                    {getStatusLabel(participant.status)}
                  </span>
                </article>
              ))}
            </div>
          </section>

          <section className="meeting-prejoin-info-card">
            <strong className="meeting-prejoin-checklist-title">시작 전 체크리스트</strong>

            <div className="meeting-prejoin-checklist">
              <div className={`meeting-prejoin-check-item ${hasMicStream ? "done" : "pending"}`}>
                <span className="meeting-prejoin-check-mark">{hasMicStream ? "✓" : ""}</span>
                <div>
                  <strong>마이크 연결 확인</strong>
                  <span>{hasMicStream ? "연결됨" : "권한 필요"}</span>
                </div>
              </div>

              <div className={`meeting-prejoin-check-item ${hasCameraStream ? "done" : "pending"}`}>
                <span className="meeting-prejoin-check-mark">{hasCameraStream ? "✓" : ""}</span>
                <div>
                  <strong>카메라 연결 확인</strong>
                  <span>{hasCameraStream ? "연결됨" : "권한 필요"}</span>
                </div>
              </div>

              <div className="meeting-prejoin-check-item done">
                <span className="meeting-prejoin-check-mark">✓</span>
                <div>
                  <strong>회의 접근 권한 확인</strong>
                  <span>{accessRoleLabel}</span>
                </div>
              </div>
            </div>
          </section>
        </aside>
      </main>

      {!hasEnteredPrejoin && isEntryModalOpen ? (
        <div className="meeting-entry-modal-backdrop" role="presentation">
          <div
            aria-describedby="meeting-entry-modal-description"
            aria-labelledby="meeting-entry-modal-title"
            aria-modal="true"
            className="meeting-entry-modal"
            role="dialog"
          >
            <span className="meeting-entry-modal-kicker">예정된 회의 입장</span>
            <h2 id="meeting-entry-modal-title">{meetingTitle}</h2>
            <p id="meeting-entry-modal-description">
              회의에 입장하시겠습니까?
              <br />
              입장 후 카메라와 마이크를 확인한 뒤 회의를 시작합니다.
            </p>

            <div className="meeting-entry-modal-meta">
              <span>Space: {projectName}</span>
              <span>시작 예정 {data.startsAt}</span>
            </div>

            <div className="meeting-entry-modal-actions">
              <button className="meeting-entry-modal-secondary" onClick={handleCancelEntry} type="button">
                취소
              </button>
              <button
                className="meeting-entry-modal-primary"
                onClick={() => {
                  setHasEnteredPrejoin(true);
                  setIsEntryModalOpen(false);
                }}
                type="button"
              >
                입장
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
