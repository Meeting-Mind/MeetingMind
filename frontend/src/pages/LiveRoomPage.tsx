import {
  ConnectionState,
  Room,
  RoomEvent,
  Track,
  type Participant,
  type TrackPublication
} from "livekit-client";
import { useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import type { WorkspaceData } from "../types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.trim() || "";
const PREJOIN_STORAGE_KEY = "meetingmind-prejoin";

type RoomTokenResponse = {
  serverUrl: string;
  participantToken: string;
  roomName: string;
  identity: string;
  name: string;
};

type ParticipantCard = {
  sid: string;
  identity: string;
  name: string;
  role: string;
  isLocal: boolean;
  isCameraEnabled: boolean;
  isMicrophoneEnabled: boolean;
  isScreenShareEnabled: boolean;
  cameraPublication?: TrackPublication;
  screenSharePublication?: TrackPublication;
  audioPublication?: TrackPublication;
};

type ParticipantProfile = {
  identity: string;
  name: string;
  role: string;
  cameraEnabled: boolean;
  micEnabled: boolean;
};

function MicGlyph({ off = false }: { off?: boolean }) {
  return (
    <svg aria-hidden="true" fill="none" height="20" viewBox="0 0 24 24" width="20">
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
      {off ? <path d="M4 4l16 16" stroke="currentColor" strokeLinecap="round" strokeWidth="2.2" /> : null}
    </svg>
  );
}

function CameraGlyph({ off = false }: { off?: boolean }) {
  return (
    <svg aria-hidden="true" fill="none" height="20" viewBox="0 0 24 24" width="20">
      <path
        d="M4 8.5A2.5 2.5 0 0 1 6.5 6H14a2 2 0 0 1 1.6.8l1.1 1.45A2 2 0 0 0 18.3 9H19a2 2 0 0 1 2 2v5.5a2.5 2.5 0 0 1-2.5 2.5h-12A2.5 2.5 0 0 1 4 16.5v-8Z"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="2"
      />
      <circle cx="12" cy="13" r="3.2" stroke="currentColor" strokeWidth="2" />
      {off ? <path d="M4 4l16 16" stroke="currentColor" strokeLinecap="round" strokeWidth="2.2" /> : null}
    </svg>
  );
}

function ShareGlyph() {
  return (
    <svg aria-hidden="true" fill="none" height="20" viewBox="0 0 24 24" width="20">
      <path
        d="M5 6.5A1.5 1.5 0 0 1 6.5 5h11A1.5 1.5 0 0 1 19 6.5v7a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 13.5v-7Z"
        stroke="currentColor"
        strokeLinejoin="round"
        strokeWidth="2"
      />
      <path d="M12 19v-7m0 0-3 3m3-3 3 3" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" />
    </svg>
  );
}

function SparkGlyph() {
  return (
    <svg aria-hidden="true" fill="none" height="20" viewBox="0 0 24 24" width="20">
      <path d="m12 3 1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z" fill="currentColor" />
    </svg>
  );
}

function loadParticipantProfile(search: string): ParticipantProfile {
  const params = new URLSearchParams(search);
  const savedRaw = sessionStorage.getItem(PREJOIN_STORAGE_KEY);
  const saved = savedRaw ? (JSON.parse(savedRaw) as Partial<ParticipantProfile>) : {};
  const generatedIdentity =
    typeof crypto !== "undefined" && "randomUUID" in crypto
      ? `meetingmind-${crypto.randomUUID().slice(0, 8)}`
      : `meetingmind-${Date.now()}`;

  const profile: ParticipantProfile = {
    identity: params.get("identity") ?? saved.identity ?? generatedIdentity,
    name: params.get("name") ?? saved.name ?? "이미주",
    role: params.get("role") ?? saved.role ?? "호스트",
    cameraEnabled: saved.cameraEnabled ?? true,
    micEnabled: saved.micEnabled ?? true
  };

  sessionStorage.setItem(PREJOIN_STORAGE_KEY, JSON.stringify(profile));
  return profile;
}

function buildParticipantCards(
  room: Room,
  roleLookup: Map<string, string>,
  localProfile: ParticipantProfile
) {
  const participants: Participant[] = [room.localParticipant, ...room.remoteParticipants.values()];

  return participants
    .map((participant) => {
      const isLocal = participant.isLocal;
      const cameraPublication = participant.getTrackPublication(Track.Source.Camera);
      const audioPublication = participant.getTrackPublication(Track.Source.Microphone);
      const screenSharePublication = participant.getTrackPublication(Track.Source.ScreenShare);
      const name = isLocal
        ? localProfile.name
        : participant.name?.trim() || participant.identity || "참가자";
      const role = isLocal
        ? localProfile.role
        : roleLookup.get(name) ?? roleLookup.get(participant.identity) ?? "참가자";

      return {
        sid: participant.sid,
        identity: participant.identity,
        name,
        role,
        isLocal,
        isCameraEnabled: participant.isCameraEnabled,
        isMicrophoneEnabled: participant.isMicrophoneEnabled,
        isScreenShareEnabled: participant.isScreenShareEnabled,
        cameraPublication,
        screenSharePublication,
        audioPublication
      } satisfies ParticipantCard;
    })
    .sort((left, right) => {
      if (left.isLocal && !right.isLocal) {
        return -1;
      }

      if (!left.isLocal && right.isLocal) {
        return 1;
      }

      return left.name.localeCompare(right.name, "ko");
    });
}

function getInitial(name: string) {
  return name.trim().slice(0, 1) || "?";
}

function VideoTrackView({
  className,
  mirror = false,
  publication
}: {
  className: string;
  mirror?: boolean;
  publication?: TrackPublication;
}) {
  const videoRef = useRef<HTMLVideoElement | null>(null);

  useEffect(() => {
    const element = videoRef.current;
    const track = publication?.videoTrack;

    if (!element || !track) {
      return;
    }

    track.attach(element);
    return () => {
      track.detach(element);
    };
  }, [publication]);

  return <video ref={videoRef} autoPlay className={className} muted playsInline style={mirror ? { transform: "scaleX(-1)" } : undefined} />;
}

function AudioTrackSink({ publication }: { publication?: TrackPublication }) {
  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    const element = audioRef.current;
    const track = publication?.audioTrack;

    if (!element || !track) {
      return;
    }

    track.attach(element);
    void element.play().catch(() => {});

    return () => {
      track.detach(element);
    };
  }, [publication]);

  return <audio ref={audioRef} autoPlay hidden />;
}

function ParticipantTile({
  active = false,
  participant
}: {
  active?: boolean;
  participant: ParticipantCard;
}) {
  return (
    <article className={`lk-live-room-tile ${active ? "is-active" : ""}`}>
      <div className="lk-live-room-tile-media">
        {participant.cameraPublication?.videoTrack && participant.isCameraEnabled ? (
          <VideoTrackView
            className="lk-live-room-tile-video"
            mirror={participant.isLocal}
            publication={participant.cameraPublication}
          />
        ) : (
          <div className="lk-live-room-tile-placeholder">
            <span>{getInitial(participant.name)}</span>
          </div>
        )}
      </div>

      <div className="lk-live-room-tile-meta">
        <div className="lk-live-room-tile-name-row">
          {!participant.isMicrophoneEnabled ? <span className="lk-live-room-muted-badge">음소거</span> : null}
          <strong>{participant.name}</strong>
        </div>
        <span>{participant.role}</span>
      </div>
    </article>
  );
}

export function LiveRoomPage({
  liveMeeting,
  meetingAi
}: {
  liveMeeting: WorkspaceData["liveMeeting"];
  meetingAi: WorkspaceData["meetingAi"];
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const roomRef = useRef<Room | null>(null);
  const [roomReady, setRoomReady] = useState(false);
  const [connectionStateLabel, setConnectionStateLabel] = useState("연결 중");
  const [roomError, setRoomError] = useState("");
  const [meetingAiNotice, setMeetingAiNotice] = useState("");
  const [participantCards, setParticipantCards] = useState<ParticipantCard[]>([]);
  const [activeSpeakerSid, setActiveSpeakerSid] = useState<string | null>(null);
  const [liveTranscriptRows, setLiveTranscriptRows] = useState(meetingAi.transcript.slice(0, 2));

  const participantProfile = useMemo(() => loadParticipantProfile(location.search), [location.search]);
  const roleLookup = useMemo(
    () =>
      new Map(
        liveMeeting.accessMembers.map((member) => [
          member.name,
          member.role.includes("·") ? member.role.split("·")[0]?.trim() ?? member.role : member.role
        ])
      ),
    [liveMeeting.accessMembers]
  );

  const activeScreenShare =
    participantCards.find((participant) => participant.screenSharePublication?.videoTrack) ?? null;
  const activeStageParticipant =
    activeScreenShare ??
    participantCards.find((participant) => participant.sid === activeSpeakerSid && participant.cameraPublication?.videoTrack) ??
    participantCards.find((participant) => !participant.isLocal && participant.cameraPublication?.videoTrack) ??
    participantCards.find((participant) => participant.cameraPublication?.videoTrack) ??
    null;
  const stagePublication = activeScreenShare?.screenSharePublication ?? activeStageParticipant?.cameraPublication;
  useEffect(() => {
    document.body.className = "app-theme lk-live-room-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  useEffect(() => {
    if (meetingAi.transcript.length <= 2) {
      return;
    }

    let currentIndex = 2;
    const intervalId = window.setInterval(() => {
      setLiveTranscriptRows((previous) => {
        const nextRow = meetingAi.transcript[currentIndex % meetingAi.transcript.length];
        currentIndex += 1;
        return [nextRow, ...previous].slice(0, 6);
      });
    }, 2400);

    return () => {
      window.clearInterval(intervalId);
    };
  }, [meetingAi.transcript]);

  useEffect(() => {
    let mounted = true;

    const formatRoomError = (error: unknown, fallback: string) => {
      if (error instanceof Error) {
        return error.message || fallback;
      }

      if (typeof error === "string" && error.trim()) {
        return error;
      }

      return fallback;
    };

    const syncSnapshot = (room: Room) => {
      if (!mounted) {
        return;
      }

      setParticipantCards(buildParticipantCards(room, roleLookup, participantProfile));
      setActiveSpeakerSid(room.activeSpeakers[0]?.sid ?? null);
      setConnectionStateLabel(
        room.state === ConnectionState.Connected
          ? "실시간 연결됨"
          : room.state === ConnectionState.Connecting
            ? "연결 중"
            : room.state === ConnectionState.Reconnecting
              ? "재연결 중"
              : "연결 종료"
      );
    };

    const initializeRoom = async () => {
      setRoomError("");
      setRoomReady(false);

      try {
        let tokenResponse: Response;
        try {
          tokenResponse = await fetch(`${API_BASE_URL}/api/livekit/token`, {
            method: "POST",
            headers: {
              "Content-Type": "application/json"
            },
            body: JSON.stringify({
              roomName: liveMeeting.roomCode.toLowerCase(),
              identity: participantProfile.identity,
              name: participantProfile.name
            })
          });
        } catch (error) {
          throw new Error(`토큰 API 연결 실패: ${formatRoomError(error, "백엔드 토큰 요청에 실패했습니다.")}`);
        }

        if (!tokenResponse.ok) {
          const message = await tokenResponse.text();
          throw new Error(message || `LiveKit 토큰 요청 실패 (${tokenResponse.status})`);
        }

        const connection = (await tokenResponse.json()) as RoomTokenResponse;
        const room = new Room({
          adaptiveStream: true,
          dynacast: true
        });

        roomRef.current = room;

        room
          .on(RoomEvent.ConnectionStateChanged, () => syncSnapshot(room))
          .on(RoomEvent.ParticipantConnected, () => syncSnapshot(room))
          .on(RoomEvent.ParticipantDisconnected, () => syncSnapshot(room))
          .on(RoomEvent.TrackSubscribed, () => syncSnapshot(room))
          .on(RoomEvent.TrackUnsubscribed, () => syncSnapshot(room))
          .on(RoomEvent.TrackMuted, () => syncSnapshot(room))
          .on(RoomEvent.TrackUnmuted, () => syncSnapshot(room))
          .on(RoomEvent.LocalTrackPublished, () => syncSnapshot(room))
          .on(RoomEvent.LocalTrackUnpublished, () => syncSnapshot(room))
          .on(RoomEvent.ActiveSpeakersChanged, () => syncSnapshot(room))
          .on(RoomEvent.Disconnected, () => syncSnapshot(room));

        try {
          await room.connect(connection.serverUrl, connection.participantToken);
        } catch (error) {
          throw new Error(
            `LiveKit Cloud 연결 실패: ${formatRoomError(
              error,
              "토큰은 발급됐지만 LiveKit 서버 연결에 실패했습니다."
            )}`
          );
        }

        await room.localParticipant.setCameraEnabled(participantProfile.cameraEnabled);
        await room.localParticipant.setMicrophoneEnabled(participantProfile.micEnabled);

        syncSnapshot(room);
        if (mounted) {
          setRoomReady(true);
        }
      } catch (error) {
        console.error("[LiveRoomPage] livekit init failed", error);
        if (mounted) {
          setRoomError(error instanceof Error ? error.message : "LiveKit 회의실 연결에 실패했습니다.");
          setConnectionStateLabel("연결 실패");
        }
      }
    };

    void initializeRoom();

    return () => {
      mounted = false;
      const room = roomRef.current;
      roomRef.current = null;
      if (room) {
        void room.disconnect();
      }
    };
  }, [liveMeeting.roomCode, participantProfile, roleLookup]);

  async function handleToggleMicrophone() {
    const room = roomRef.current;

    if (!room) {
      return;
    }

    try {
      await room.localParticipant.setMicrophoneEnabled(!room.localParticipant.isMicrophoneEnabled);
      setParticipantCards(buildParticipantCards(room, roleLookup, participantProfile));
    } catch {
      setRoomError("마이크 상태를 변경하지 못했습니다.");
    }
  }

  async function handleToggleCamera() {
    const room = roomRef.current;

    if (!room) {
      return;
    }

    try {
      await room.localParticipant.setCameraEnabled(!room.localParticipant.isCameraEnabled);
      setParticipantCards(buildParticipantCards(room, roleLookup, participantProfile));
    } catch {
      setRoomError("카메라 상태를 변경하지 못했습니다.");
    }
  }

  async function handleToggleScreenShare() {
    const room = roomRef.current;

    if (!room) {
      return;
    }

    try {
      await room.localParticipant.setScreenShareEnabled(!room.localParticipant.isScreenShareEnabled);
      setParticipantCards(buildParticipantCards(room, roleLookup, participantProfile));
    } catch {
      setRoomError("화면 공유를 시작할 수 없습니다. 브라우저 권한을 확인해주세요.");
    }
  }

  async function handleLeave(nextPath: string) {
    const room = roomRef.current;
    if (room) {
      await room.disconnect();
      roomRef.current = null;
    }
    navigate(nextPath);
  }

  function handleMeetingAiClick() {
    setMeetingAiNotice("Meeting AI 페이지는 회의 종료 후 확인할 수 있습니다.");
  }

  const localParticipant = participantCards.find((participant) => participant.isLocal) ?? null;
  const micEnabled = localParticipant?.isMicrophoneEnabled ?? false;
  const cameraEnabled = localParticipant?.isCameraEnabled ?? false;
  const sharingEnabled = localParticipant?.isScreenShareEnabled ?? false;

  return (
    <div className="lk-live-room-shell">
      <div className="lk-live-room-frame">
        <header className="lk-live-room-header">
          <div className="lk-live-room-header-copy">
            <p className="lk-live-room-breadcrumb">
              live-room / <span>{liveMeeting.overview.title}</span>
            </p>
            <strong>{liveMeeting.overview.title}</strong>
            <span>실시간 카메라 화면과 발표 화면, STT 요약을 한 번에 확인하는 회의실입니다.</span>
          </div>
          <div className="lk-live-room-status">
            <button className="lk-live-room-top-leave" onClick={() => handleLeave("/spaces")} type="button">
              나가기
            </button>
          </div>
        </header>

        <main className="lk-live-room-layout">
          <section className="lk-live-room-main">
            <div className="lk-live-room-stage">
              <span className="lk-live-room-stage-chip">
                {activeScreenShare ? `${activeScreenShare.name} 화면 공유` : "참가자 카메라"}
              </span>

              {stagePublication?.videoTrack ? (
                <VideoTrackView
                  className="lk-live-room-stage-video"
                  mirror={Boolean(activeStageParticipant?.isLocal && !activeScreenShare)}
                  publication={stagePublication}
                />
              ) : (
                <div className="lk-live-room-stage-placeholder">
                  <strong>{roomError ? "LiveKit 연결을 확인해주세요" : "참가자 카메라 연결 대기 중"}</strong>
                  <p>
                    {roomError
                      ? roomError
                      : "같은 룸으로 입장한 참가자들의 카메라 화면과 화면 공유가 이 영역에 실시간으로 표시됩니다."}
                  </p>
                </div>
              )}

              <div className="lk-live-room-stage-owner">
                {activeStageParticipant ? activeStageParticipant.name : participantProfile.name}
              </div>
            </div>

            <div className="lk-live-room-strip">
              {participantCards.map((participant) => (
                <ParticipantTile
                  key={participant.sid || participant.identity}
                  active={participant.sid === activeStageParticipant?.sid}
                  participant={participant}
                />
              ))}
            </div>

            <div className="lk-live-room-controls">
              <button className="lk-live-room-control" onClick={handleToggleMicrophone} type="button">
                <span className={`lk-live-room-control-icon mic ${micEnabled ? "on" : "off"}`}>
                  <MicGlyph off={!micEnabled} />
                </span>
                <span>{micEnabled ? "마이크" : "음소거"}</span>
              </button>

              <button className="lk-live-room-control" onClick={handleToggleCamera} type="button">
                <span className={`lk-live-room-control-icon cam ${cameraEnabled ? "on" : "off"}`}>
                  <CameraGlyph off={!cameraEnabled} />
                </span>
                <span>{cameraEnabled ? "카메라" : "카메라 끔"}</span>
              </button>

              <button className="lk-live-room-control" onClick={handleToggleScreenShare} type="button">
                <span className={`lk-live-room-control-icon share ${sharingEnabled ? "on" : "off"}`}>
                  <ShareGlyph />
                </span>
                <span>{sharingEnabled ? "공유 중" : "공유"}</span>
              </button>

              <button className="lk-live-room-control" onClick={handleMeetingAiClick} type="button">
                <span className="lk-live-room-control-icon ai on">
                  <SparkGlyph />
                </span>
                <span>Meeting AI</span>
              </button>
            </div>

            {meetingAiNotice ? <div className="lk-live-room-inline-notice">{meetingAiNotice}</div> : null}
          </section>

          <aside className="lk-live-room-sidebar">
            <div className="lk-live-room-sidebar-head">
              <strong>실시간 STT</strong>
              <span>LIVE</span>
            </div>

            <div className="lk-live-room-sidebar-list">
              {liveTranscriptRows.map((row, index) => (
                <article key={`${row.time}-${row.speaker}-${index}`} className={`lk-live-room-sidebar-feed-item ${index === 0 ? "latest" : ""}`}>
                  <div className="lk-live-room-sidebar-feed-meta">
                    <span>{row.time}</span>
                    <strong>{row.speaker}</strong>
                  </div>
                  <p>{row.text}</p>
                </article>
              ))}
            </div>

            <div className="lk-live-room-sidebar-card">
              <strong>Domain Dictionary</strong>
              <div className="lk-live-room-dictionary-term">
                <h4>pgvector</h4>
                <p>벡터 검색을 위한 PostgreSQL 확장</p>
              </div>
            </div>
          </aside>
        </main>

        {participantCards
          .filter((participant) => !participant.isLocal)
          .map((participant) => (
            <AudioTrackSink key={`audio-${participant.sid || participant.identity}`} publication={participant.audioPublication} />
          ))}
      </div>
    </div>
  );
}
