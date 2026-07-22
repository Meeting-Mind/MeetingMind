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
import {
  explainMeetingTerm,
  fetchMeetingDialogue,
  startMeetingTranscription,
  stopMeetingTranscription
} from "../api/workspace";
import { bffFetch } from "../auth/csrf";
import type { AuthSession } from "../auth/session";
import { useLiveMeetingDetail } from "../hooks/useLiveMeetingDetail";
import type { TermExplanationResponse, WorkspaceData } from "../types";

const PREJOIN_STORAGE_KEY = "meetingmind-prejoin";

type RoomTokenResponse = {
  serverUrl: string;
  participantToken: string;
  roomName: string;
  identity: string;
  name: string;
};

// final로 확정된 말풍선. 한 번 messages 배열에 들어간 뒤로는 절대 수정하지 않는다.
type SttMessage = {
  key: string;
  speakerLabel: string;
  displayName: string;
  time: string;
  text: string;
};

// 지금 화자가 말하고 있는 중이라 아직 확정되지 않은 말풍선. final이 오면 사라지고
// SttMessage로 messages 배열에 옮겨 붙는다.
type SttPartialMessage = {
  speakerLabel: string;
  displayName: string;
  time: string;
  text: string;
};

type SttConnectionStatus = "idle" | "connecting" | "connected" | "disconnected";

function formatTranscriptTime(startMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(startMs / 1_000));
  const hours = Math.floor(totalSeconds / 3_600);
  const minutes = Math.floor((totalSeconds % 3_600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds].map((value) => String(value).padStart(2, "0")).join(":");
}

function formatNowTime(): string {
  const now = new Date();
  return [now.getHours(), now.getMinutes(), now.getSeconds()]
    .map((value) => String(value).padStart(2, "0"))
    .join(":");
}

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

function BookmarkGlyph({ filled = false }: { filled?: boolean }) {
  return (
    <svg aria-hidden="true" fill={filled ? "currentColor" : "none"} height="16" viewBox="0 0 24 24" width="16">
      <path d="M6 3.5h12a1 1 0 0 1 1 1V21l-7-4-7 4V4.5a1 1 0 0 1 1-1Z" stroke="currentColor" strokeLinejoin="round" strokeWidth="2" />
    </svg>
  );
}

function SearchGlyph() {
  return (
    <svg aria-hidden="true" fill="none" height="18" viewBox="0 0 24 24" width="18">
      <circle cx="11" cy="11" r="6.5" stroke="currentColor" strokeWidth="2" />
      <path d="m20 20-3.6-3.6" stroke="currentColor" strokeLinecap="round" strokeWidth="2" />
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
  liveMeeting: fallbackLiveMeeting,
  meetingAi,
  session
}: {
  liveMeeting: WorkspaceData["liveMeeting"];
  meetingAi: WorkspaceData["meetingAi"];
  session: AuthSession;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const meetingId = new URLSearchParams(location.search).get("meetingId");
  const { liveMeeting, error: liveMeetingError } = useLiveMeetingDetail(session, meetingId, fallbackLiveMeeting);
  const roomRef = useRef<Room | null>(null);
  const [roomReady, setRoomReady] = useState(false);
  const [connectionStateLabel, setConnectionStateLabel] = useState("연결 중");
  const [roomError, setRoomError] = useState("");
  const [meetingAiNotice, setMeetingAiNotice] = useState("");
  const [participantCards, setParticipantCards] = useState<ParticipantCard[]>([]);
  const [activeSpeakerSid, setActiveSpeakerSid] = useState<string | null>(null);
  const [messages, setMessages] = useState<SttMessage[]>([]);
  const [currentPartialMessage, setCurrentPartialMessage] = useState<SttPartialMessage | null>(null);
  const [sttConnectionStatus, setSttConnectionStatus] = useState<SttConnectionStatus>("idle");
  const [sttStatusBannerVisible, setSttStatusBannerVisible] = useState(false);
  const [sttSearchQuery, setSttSearchQuery] = useState("");
  const [bookmarkedKeys, setBookmarkedKeys] = useState<Set<string>>(new Set());
  const [selectedTerm, setSelectedTerm] = useState("");
  const [termExplanation, setTermExplanation] = useState<TermExplanationResponse | null>(null);
  const [termExplanationError, setTermExplanationError] = useState("");
  const [termExplanationLoading, setTermExplanationLoading] = useState(false);
  const termExplanationRequestIdRef = useRef(0);
  const sttSessionIdRef = useRef<string | null>(null);
  const sttStartedRef = useRef(false);
  const sttListRef = useRef<HTMLDivElement | null>(null);
  const seenSegmentIdsRef = useRef<Set<string>>(new Set());
  const sttConnectionStatusRef = useRef<SttConnectionStatus>("idle");
  const sttStatusHideTimerRef = useRef<number | null>(null);

  // STT 연결 상태 배너를 갱신한다. autoHide면 몇 초 뒤 배너만 사라지고(상태 자체는 유지),
  // 아니면(연결 중/끊김) 다음 상태 전환 전까지 계속 보인다.
  function showSttStatus(status: SttConnectionStatus, autoHide: boolean) {
    sttConnectionStatusRef.current = status;
    setSttConnectionStatus(status);
    setSttStatusBannerVisible(true);
    if (sttStatusHideTimerRef.current != null) {
      window.clearTimeout(sttStatusHideTimerRef.current);
      sttStatusHideTimerRef.current = null;
    }
    if (autoHide) {
      sttStatusHideTimerRef.current = window.setTimeout(() => {
        setSttStatusBannerVisible(false);
      }, 3000);
    }
  }

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
    if (!roomReady || !meetingId) {
      return;
    }

    let cancelled = false;
    seenSegmentIdsRef.current = new Set();
    setMessages([]);
    setCurrentPartialMessage(null);

    const poll = async () => {
      try {
        const dialogue = await fetchMeetingDialogue(session, meetingId);
        if (cancelled) {
          return;
        }

        // final로 확정된 row만 messages에 새로 추가한다. 이미 본 segmentId는 절대 다시 건드리지 않는다.
        const newRows = dialogue.rows
          .filter((row) => !seenSegmentIdsRef.current.has(row.segmentId))
          .sort((left, right) => left.startMs - right.startMs);

        if (newRows.length > 0) {
          newRows.forEach((row) => seenSegmentIdsRef.current.add(row.segmentId));
          setMessages((previous) => [
            ...previous,
            ...newRows.map((row) => ({
              key: row.segmentId,
              speakerLabel: row.speakerLabel,
              displayName: row.speakerName || row.speakerLabel,
              time: formatTranscriptTime(row.startMs),
              text: row.text
            }))
          ]);
        }

        const activePartial = dialogue.partials[0] ?? null;
        setCurrentPartialMessage((previous) => {
          if (!activePartial) {
            return null;
          }
          const displayName = activePartial.speakerName || activePartial.speakerLabel;
          if (previous && previous.speakerLabel === activePartial.speakerLabel) {
            return { ...previous, text: activePartial.text };
          }
          return { speakerLabel: activePartial.speakerLabel, displayName, time: formatNowTime(), text: activePartial.text };
        });

        if (sttConnectionStatusRef.current === "disconnected") {
          showSttStatus("connected", true);
        }
      } catch {
        showSttStatus("disconnected", false);
      }
    };

    void poll();
    const intervalId = window.setInterval(poll, 2500);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [roomReady, meetingId, session]);

  async function startSttStream(targetMeetingId: string, trackId: string) {
    showSttStatus("connecting", false);
    try {
      const data = await startMeetingTranscription(session, targetMeetingId, {
        mode: "realtime",
        trackId
      });
      sttSessionIdRef.current = data.sessionId;
      showSttStatus("connected", true);
    } catch (error) {
      console.warn("[LiveRoomPage] STT stream start failed", error);
      showSttStatus("disconnected", false);
    }
  }

  function stopSttStream() {
    const sessionId = sttSessionIdRef.current;
    sttSessionIdRef.current = null;

    if (!sessionId || !meetingId) {
      return;
    }

    void stopMeetingTranscription(session, meetingId, sessionId).catch(() => {});
  }

  function toggleBookmark(key: string) {
    setBookmarkedKeys((previous) => {
      const next = new Set(previous);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  function requestTermExplanation(term: string) {
    const requestId = termExplanationRequestIdRef.current + 1;
    termExplanationRequestIdRef.current = requestId;
    if (!meetingId) {
      setSelectedTerm(term);
      setTermExplanation(null);
      setTermExplanationError("실제 회의 데이터에서만 용어 설명을 조회할 수 있습니다.");
      return;
    }

    setSelectedTerm(term);
    setTermExplanation(null);
    setTermExplanationError("");
    setTermExplanationLoading(true);
    void explainMeetingTerm(session, meetingId, { term })
      .then((response) => {
        if (requestId === termExplanationRequestIdRef.current) {
          setTermExplanation(response);
        }
      })
      .catch((cause: unknown) => {
        if (requestId === termExplanationRequestIdRef.current) {
          setTermExplanationError(cause instanceof Error ? cause.message : "용어 설명을 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (requestId === termExplanationRequestIdRef.current) {
          setTermExplanationLoading(false);
        }
      });
  }

  function handleTranscriptSelection(event: React.MouseEvent<HTMLParagraphElement>) {
    const selection = window.getSelection();
    const term = selection?.toString().trim() ?? "";
    if (!term || !selection?.anchorNode || !selection.focusNode || !event.currentTarget.contains(selection.anchorNode) || !event.currentTarget.contains(selection.focusNode)) {
      return;
    }
    if (term.length > 120) {
      termExplanationRequestIdRef.current += 1;
      setSelectedTerm(term);
      setTermExplanation(null);
      setTermExplanationError("선택한 용어는 120자 이하여야 합니다.");
      setTermExplanationLoading(false);
      return;
    }
    requestTermExplanation(term);
  }

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
        if (!meetingId) {
          throw new Error("회의 ID가 없어 LiveKit 접근 권한을 확인할 수 없습니다.");
        }
        let tokenResponse: Response;
        try {
          tokenResponse = await bffFetch(
            `/api/v1/meetings/${encodeURIComponent(meetingId)}/livekit-token`,
            { method: "POST" }
          );
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
          .on(RoomEvent.LocalTrackPublished, (publication) => {
            syncSnapshot(room);
            if (!sttStartedRef.current && publication.source === Track.Source.Microphone && publication.trackSid) {
              sttStartedRef.current = true;
              void startSttStream(meetingId, publication.trackSid);
            }
          })
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
      sttStartedRef.current = false;
      stopSttStream();
      if (sttStatusHideTimerRef.current != null) {
        window.clearTimeout(sttStatusHideTimerRef.current);
        sttStatusHideTimerRef.current = null;
      }
    };
  }, [meetingId, participantProfile, roleLookup, session]);

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

  // 확정된 메시지(절대 안 바뀜) 뒤에, 지금 말하는 중인 partial 말풍선을 하나만 붙인다.
  const liveTranscriptEntries = useMemo(() => {
    const entries = messages.map((message) => ({ ...message, isPartial: false }));
    if (currentPartialMessage) {
      entries.push({
        key: `partial-${currentPartialMessage.speakerLabel}`,
        speakerLabel: currentPartialMessage.speakerLabel,
        displayName: currentPartialMessage.displayName,
        time: currentPartialMessage.time,
        text: currentPartialMessage.text,
        isPartial: true
      });
    }
    return entries;
  }, [messages, currentPartialMessage]);

  useEffect(() => {
    const list = sttListRef.current;
    if (list) {
      list.scrollTop = list.scrollHeight;
    }
  }, [liveTranscriptEntries]);

  const trimmedSttQuery = sttSearchQuery.trim().toLowerCase();
  const visibleTranscriptEntries = trimmedSttQuery
    ? liveTranscriptEntries.filter((entry) => `${entry.displayName} ${entry.text}`.toLowerCase().includes(trimmedSttQuery))
    : liveTranscriptEntries;

  const sttStatusMessage =
    sttConnectionStatus === "connecting"
      ? "STT 연결 중입니다..."
      : sttConnectionStatus === "connected"
        ? "STT가 연결되었습니다."
        : sttConnectionStatus === "disconnected"
          ? "STT 연결이 끊어졌습니다."
          : "";

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
            {liveMeetingError ? (
              <span className="live-meeting-data-warning" title={liveMeetingError}>
                임시 데이터 표시 중
              </span>
            ) : null}
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

            {sttStatusBannerVisible ? (
              <div className={`lk-live-room-stt-status lk-live-room-stt-status-${sttConnectionStatus}`} role="status">
                {sttStatusMessage}
              </div>
            ) : null}

            {selectedTerm ? (
              <section aria-live="polite" className="lk-live-room-term-explanation">
                <div className="lk-live-room-term-explanation-head">
                  <strong>{selectedTerm}</strong>
                  <button
                    aria-label="용어 설명 닫기"
                    className="lk-live-room-term-explanation-close"
                    onClick={() => {
                      termExplanationRequestIdRef.current += 1;
                      setSelectedTerm("");
                      setTermExplanation(null);
                      setTermExplanationError("");
                    }}
                    type="button"
                  >
                    닫기
                  </button>
                </div>
                {termExplanationLoading ? <p>설명을 확인하는 중입니다.</p> : null}
                {termExplanationError ? <p className="is-error">{termExplanationError}</p> : null}
                {termExplanation ? (
                  <>
                    <p>{termExplanation.explanation}</p>
                    {termExplanation.sources.length > 0 ? (
                      <ul>
                        {termExplanation.sources.map((source) => (
                          <li key={source.sourceId}>{source.title || source.text}</li>
                        ))}
                      </ul>
                    ) : null}
                  </>
                ) : null}
              </section>
            ) : null}

            <div className="lk-live-room-sidebar-list" ref={sttListRef}>
              {visibleTranscriptEntries.length === 0 ? (
                <p className="lk-live-room-sidebar-empty">
                  {sttSearchQuery.trim() ? "검색 결과가 없습니다." : "STT 연결 대기 중입니다."}
                </p>
              ) : (
                visibleTranscriptEntries.map((entry, index) => {
                  const bookmarked = bookmarkedKeys.has(entry.key);
                  const isLatest = index === visibleTranscriptEntries.length - 1;

                  return (
                    <article
                      key={entry.key}
                      className={`lk-live-room-sidebar-feed-item ${isLatest ? "latest" : ""} ${entry.isPartial ? "is-partial" : ""}`}
                    >
                      <button
                        aria-label={bookmarked ? "북마크 해제" : "북마크"}
                        className={`lk-live-room-bookmark ${bookmarked ? "is-active" : ""}`}
                        onClick={() => toggleBookmark(entry.key)}
                        type="button"
                      >
                        <BookmarkGlyph filled={bookmarked} />
                      </button>
                      <div className="lk-live-room-sidebar-feed-meta">
                        <span>{entry.time}</span>
                        <strong>{entry.displayName}</strong>
                      </div>
                      <p onMouseUp={handleTranscriptSelection}>{entry.text}</p>
                    </article>
                  );
                })
              )}
            </div>

            <form className="lk-live-room-sidebar-search" onSubmit={(event) => event.preventDefault()}>
              <input
                onChange={(event) => setSttSearchQuery(event.target.value)}
                placeholder="회의 기록 검색..."
                type="text"
                value={sttSearchQuery}
              />
              <span aria-hidden="true" className="lk-live-room-sidebar-search-icon">
                <SearchGlyph />
              </span>
            </form>
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
