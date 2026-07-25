import { RouterProvider, createBrowserRouter, Outlet, NavLink, useNavigate, useParams, useLocation, Link, useOutletContext, Navigate, useSearchParams } from "react-router-dom";
import {
  LayoutDashboard,
  Video,
  CheckSquare,
  Library,
  Sparkles,
  Search,
  Bell,
  Settings,
  MoreVertical,
  Plus,
  Play,
  FileText,
  Clock,
  ChevronDown,
  Users,
  Calendar,
  BookOpen,
  ArrowRight,
  ShieldAlert,
  LogOut,
  Building,
  Filter,
  Mic,
  MicOff,
  VideoOff,
  X,
  PanelLeftClose,
  PanelLeftOpen
} from "lucide-react";
import { DisplayPreferences } from "./app/DisplayPreferences";
import {
  AppPreferencesContext,
  LOCALE_STORAGE_KEY,
  THEME_STORAGE_KEY,
  storedLocale,
  storedTheme,
  useAppPreferences,
  type AppLocale,
  type ThemeMode
} from "./app/preferences";
import {
  ConnectionState,
  Room,
  RoomEvent,
  Track,
  type Participant,
  type TrackPublication
} from "livekit-client";
import React, { createContext, useContext, useEffect, useMemo, useRef, useState } from "react";
import {
  bootstrapAuthSession,
  loginWithGoogle,
  loginWithPassword,
  logoutAllDevices,
  logoutCurrentSession,
  signupWithPassword,
  updateProfile,
  uploadProfileImage,
  type AuthSession
} from "./auth/session";
import { subscribeToSessionInvalid } from "./auth/sessionInvalidation";
import { KnowledgeGraphPage } from "./features/knowledge";
import { LandingPage } from "./features/landing";
import { useMeetingDialogueQuery } from "./features/transcription/hooks";
import { filterTranscriptEntries } from "./features/transcription/selectors";
import { AllDeviceLogoutModal } from "./components/AllDeviceLogoutModal";
import { GoogleCredentialButton } from "./components/GoogleCredentialButton";
import { MeetingReportPage } from "./pages/MeetingReportPage";
import { chatMeetingAi, chatProjectAi, fetchProjectAiHistory } from "./api/ai";
import { addMeetingParticipant, createMeetingInvitation, createMeetingJoinRequest, fetchMeetingParticipants, resolveMeetingInvitation } from "./api/meetingAccess";
import {
  acceptSpaceInvitation,
  createSpace,
  createSpaceInvitation,
  deleteSpace,
  leaveSpace,
  declineSpaceInvitation,
  fetchPendingSpaceInvitations,
  acceptPendingSpaceInvitation,
  declinePendingSpaceInvitation,
  fetchSpaceInvitations,
  resendSpaceInvitation,
  cancelSpaceInvitation,
  fetchSpaceDetail,
  fetchSpaceAiUsage,
  fetchSpaceMembers,
  fetchSpaces,
  removeSpaceMember,
  transferSpaceOwner,
  updateSpace,
  updateSpaceMemberRole,
  uploadSpaceImage
} from "./api/spaces";
import {
  confirmTaskCandidate,
  createTask,
  deleteTask,
  dismissTaskCandidate,
  extractTaskCandidates,
  fetchTaskCandidates,
  fetchTasks,
  updateTask
} from "./api/tasks";
import { fetchProjectKnowledge } from "./api/knowledge";
import { fetchCalendarEvents } from "./api/calendar";
import {
  startMeetingTranscription,
  stopActiveMeetingTranscription,
  stopMeetingTranscription
} from "./api/transcripts";
import { archiveDomainTerm, createDomainTerm, fetchDomainTerms, updateDomainTerm } from "./api/terms";
import { highlightTranscriptTerms } from "./components/common/TranscriptTerm";
import { ApiRequestError } from "./api/client";
import { createInstantMeeting, createMeeting, deleteMeeting, fetchAccessibleMeetings, fetchMeetingDetail, fetchMeetings, updateMeeting } from "./api/meetings";
import { fetchMeetingLiveKitToken } from "./api/live";
import type {
  CalendarEvent as ProjectCalendarEvent,
  DomainTerm,
  SpaceDetail,
  SpaceSummary,
  SpaceAiUsageResponse,
  SpaceMembersResponse,
  ProjectKnowledgeItem,
  MeetingDetailResponse,
  MeetingParticipantSummary,
  SpaceMemberSummary,
  MeetingSummary,
  TaskCard,
  TaskCardPriority,
  TaskCardStatus,
  AiSource
} from "./types";

// --- Components ---


function renderMarkdownInline(value: string, keyPrefix: string) {
  return value.split(/(\*\*[^*]+\*\*)/g).map((part, index) => {
    if (part.startsWith("**") && part.endsWith("**")) {
      return <strong key={`${keyPrefix}-bold-${index}`}>{part.slice(2, -2)}</strong>;
    }
    return <React.Fragment key={`${keyPrefix}-text-${index}`}>{part}</React.Fragment>;
  });
}

const MeetingDescription = ({ value }: { value: string | null }) => {
  const text = value?.trim();
  if (!text) {
    return <p className="text-sm leading-relaxed text-muted-foreground">No meeting description has been added yet.</p>;
  }

  const lines = text.split(/\r?\n/);
  const content: React.ReactNode[] = [];
  let index = 0;
  while (index < lines.length) {
    const line = lines[index];
    if (/^\s*-\s+/.test(line)) {
      const items: React.ReactNode[] = [];
      while (index < lines.length && /^\s*-\s+/.test(lines[index])) {
        items.push(<li key={`bullet-${index}`}>{renderMarkdownInline(lines[index].replace(/^\s*-\s+/, ""), `bullet-${index}`)}</li>);
        index += 1;
      }
      content.push(<ul className="my-2 list-disc space-y-1 pl-5" key={`list-${index}`}>{items}</ul>);
      continue;
    }
    if (/^\s*\d+\.\s+/.test(line)) {
      const items: React.ReactNode[] = [];
      while (index < lines.length && /^\s*\d+\.\s+/.test(lines[index])) {
        items.push(<li key={`ordered-${index}`}>{renderMarkdownInline(lines[index].replace(/^\s*\d+\.\s+/, ""), `ordered-${index}`)}</li>);
        index += 1;
      }
      content.push(<ol className="my-2 list-decimal space-y-1 pl-5" key={`ordered-list-${index}`}>{items}</ol>);
      continue;
    }
    if (/^\s*#{1,3}\s+/.test(line)) {
      content.push(<h4 className="my-2 font-semibold text-foreground" key={`heading-${index}`}>{renderMarkdownInline(line.replace(/^\s*#{1,3}\s+/, ""), `heading-${index}`)}</h4>);
      index += 1;
      continue;
    }
    if (line.trim()) {
      content.push(<p className="my-1" key={`paragraph-${index}`}>{renderMarkdownInline(line, `paragraph-${index}`)}</p>);
    } else {
      content.push(<div className="h-2" key={`space-${index}`} />);
    }
    index += 1;
  }
  return <div className="text-sm leading-relaxed text-muted-foreground">{content}</div>;
};

const LIVE_PREJOIN_STORAGE_KEY = "meetingmind-prejoin";

function sttSessionStorageKey(meetingId: string) {
  return `meetingmind-stt-session:${meetingId}`;
}

function isTranscriptionAlreadyProcessingError(cause: unknown) {
  return cause instanceof ApiRequestError
    && cause.status === 409
    && cause.code === "TRANSCRIPTION_ALREADY_PROCESSING";
}

function sttStartErrorMessage(cause: unknown) {
  if (cause instanceof ApiRequestError && cause.code === "STT_PROVIDER_UNAVAILABLE") {
    return "실시간 회의 연결을 일시적으로 사용할 수 없습니다. LiveKit 또는 STT 연동 상태를 확인한 뒤 다시 시도해 주세요.";
  }
  if (cause instanceof Error && cause.message) {
    return cause.message;
  }
  return "실시간 전사를 시작하지 못했습니다.";
}

type LiveParticipantCard = {
  key: string;
  sid: string | null;
  identity: string;
  name: string;
  initials: string;
  isLocal: boolean;
  isConnected: boolean;
  isCameraEnabled: boolean;
  isMicrophoneEnabled: boolean;
  cameraPublication?: TrackPublication;
  audioPublication?: TrackPublication;
};

type LiveTranscriptRow = {
  key: string;
  speaker: string;
  initials: string;
  time: string;
  text: string;
};

function displayParticipantName(participant: Pick<MeetingParticipantSummary, "displayName" | "email" | "userId">) {
  return participant.displayName?.trim() || participant.email?.trim() || participant.userId;
}

function participantInitials(name: string) {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return "?";
  }
  return parts.slice(0, 2).map((part) => part[0]?.toUpperCase() ?? "").join("") || "?";
}

function ProfileAvatar({
  pictureUrl,
  initials,
  className,
  alt = ""
}: {
  pictureUrl?: string | null;
  initials: string;
  className: string;
  alt?: string;
}) {
  const src = pictureUrl?.trim();
  return src ? (
    <img alt={alt} className={`${className} object-cover`} src={src} />
  ) : (
    <div aria-hidden="true" className={`${className} flex items-center justify-center bg-muted text-foreground`}>
      {initials}
    </div>
  );
}

function formatMeetingSchedule(detail: MeetingDetailResponse | null) {
  if (!detail) {
    return "회의 정보를 확인하는 중입니다.";
  }

  const start = new Date(detail.scheduledAt);
  const end = new Date(detail.scheduledEndAt);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
    return detail.scheduledAt;
  }

  const dayLabel = new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "short",
    timeZone: "Asia/Seoul"
  }).format(start);
  const startLabel = new Intl.DateTimeFormat("ko-KR", {
    hour: "numeric",
    minute: "2-digit",
    timeZone: "Asia/Seoul"
  }).format(start);
  const endLabel = new Intl.DateTimeFormat("ko-KR", {
    hour: "numeric",
    minute: "2-digit",
    timeZone: "Asia/Seoul"
  }).format(end);
  return `${dayLabel} · ${startLabel} - ${endLabel}`;
}

function formatLiveElapsed(seconds: number) {
  const safe = Math.max(0, seconds);
  const hours = Math.floor(safe / 3600);
  const minutes = Math.floor((safe % 3600) / 60);
  const remain = safe % 60;
  if (hours > 0) {
    return [hours, minutes, remain].map((value) => String(value).padStart(2, "0")).join(":");
  }
  return `${String(minutes).padStart(2, "0")}:${String(remain).padStart(2, "0")}`;
}

function formatTranscriptTime(startMs: number) {
  const totalSeconds = Math.max(0, Math.floor(startMs / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds].map((value) => String(value).padStart(2, "0")).join(":");
}


function buildRoomParticipantCards(
  room: Room,
  roster: MeetingParticipantSummary[],
  currentUserName: string
): LiveParticipantCard[] {
  const rosterByIdentity = new Map(roster.map((participant) => [participant.userId, participant]));
  const liveParticipants: Participant[] = [room.localParticipant, ...room.remoteParticipants.values()];
  const liveCards = liveParticipants.map((participant) => {
    const rosterEntry = rosterByIdentity.get(participant.identity);
    const name = rosterEntry
      ? displayParticipantName(rosterEntry)
      : participant.name?.trim() || participant.identity || (participant.isLocal ? currentUserName : "참가자");
    return {
      key: participant.sid || participant.identity,
      sid: participant.sid,
      identity: participant.identity,
      name,
      initials: participantInitials(name),
      isLocal: participant.isLocal,
      isConnected: true,
      isCameraEnabled: participant.isCameraEnabled,
      isMicrophoneEnabled: participant.isMicrophoneEnabled,
      cameraPublication: participant.getTrackPublication(Track.Source.Camera),
      audioPublication: participant.getTrackPublication(Track.Source.Microphone)
    } satisfies LiveParticipantCard;
  });

  const missingRosterCards = roster
    .filter((participant) => participant.accessStatus === "ACTIVE")
    .filter((participant) => !liveCards.some((card) => card.identity === participant.userId))
    .map((participant) => {
      const name = displayParticipantName(participant);
      return {
        key: participant.id,
        sid: null,
        identity: participant.userId,
        name,
        initials: participantInitials(name),
        isLocal: participant.userId === room.localParticipant.identity,
        isConnected: false,
        isCameraEnabled: false,
        isMicrophoneEnabled: false
      } satisfies LiveParticipantCard;
    });

  return [...liveCards, ...missingRosterCards].sort((left, right) => {
    if (left.isLocal && !right.isLocal) {
      return -1;
    }
    if (!left.isLocal && right.isLocal) {
      return 1;
    }
    if (left.isConnected !== right.isConnected) {
      return left.isConnected ? -1 : 1;
    }
    return left.name.localeCompare(right.name, "ko");
  });
}

function VideoTrackSurface({
  className,
  publication,
  mirror = false
}: {
  className: string;
  publication?: TrackPublication;
  mirror?: boolean;
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

  return <video autoPlay className={className} muted playsInline ref={videoRef} style={mirror ? { transform: "scaleX(-1)" } : undefined} />;
}

function AudioTrackSurface({ publication, volume = 1 }: { publication?: TrackPublication; volume?: number }) {
  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    const element = audioRef.current;
    const track = publication?.audioTrack;
    if (!element || !track) {
      return;
    }

    track.attach(element);
    element.volume = volume;
    void element.play().catch(() => {});
    return () => {
      track.detach(element);
    };
  }, [publication, volume]);

  return <audio autoPlay hidden ref={audioRef} />;
}

function aiSourceLabel(source: AiSource) {
  if (source.type === "projectKnowledge") {
    return `${source.title} · Official knowledge`;
  }
  if (source.type === "report") {
    return `${source.title} · Report`;
  }
  if (source.type === "decision") {
    return `${source.title} · Decision`;
  }
  if (source.type === "actionItem") {
    return `${source.title} · Action Item`;
  }
  if (source.type === "meetingSummary") {
    return `${source.title} · Meeting summary`;
  }
  if (source.type === "glossary") {
    return `${source.title} · Glossary`;
  }
  return `${source.title} · Transcript`;
}

function aiSourceTone(type: AiSource["type"]) {
  if (type === "projectKnowledge") {
    return "bg-[var(--app-knowledge-soft)] text-[var(--app-knowledge)] border-[color:color-mix(in_srgb,var(--app-knowledge)_30%,white)]";
  }
  if (type === "report" || type === "meetingSummary") {
    return "bg-[var(--app-accent-soft)] text-[var(--app-accent-text)] border-[color:color-mix(in_srgb,var(--app-accent)_24%,white)]";
  }
  if (type === "decision" || type === "actionItem") {
    return "bg-[var(--app-ai-soft)] text-[var(--app-ai)] border-[color:color-mix(in_srgb,var(--app-ai)_28%,white)]";
  }
  if (type === "glossary") {
    return "bg-[var(--app-highlight-soft)] text-[var(--app-highlight)] border-[color:color-mix(in_srgb,var(--app-highlight)_24%,white)]";
  }
  return "bg-[var(--app-surface-muted)] text-[var(--app-text)] border-[var(--app-line)]";
}

function unsupportedAiMessage(reason: string | null | undefined, scope: "meeting" | "project") {
  if (reason === "LOW_RELEVANCE") {
    return scope === "meeting"
      ? "현재 회의에서 관련 근거를 찾았지만 답변할 만큼 충분하지 않습니다."
      : "현재 프로젝트에서 관련 근거를 찾았지만 답변할 만큼 충분하지 않습니다.";
  }
  if (reason === "MODEL_UNSUPPORTED") {
    return "제공된 근거만으로는 답변을 확정할 수 없습니다.";
  }
  if (reason === "UNVERIFIED_OUTPUT") {
    return "응답의 근거를 확인하지 못해 답변을 제공할 수 없습니다.";
  }
  return scope === "meeting"
    ? "현재 회의에서 확인 가능한 근거가 없어 답변할 수 없습니다."
    : "현재 프로젝트에서 접근 가능한 근거가 없어 답변할 수 없습니다.";
}

type AuthState = {
  session: AuthSession | null;
  loading: boolean;
  setSession: React.Dispatch<React.SetStateAction<AuthSession | null>>;
};

const AuthContext = createContext<AuthState | null>(null);

function useAuthState() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("AuthContext is not available.");
  }
  return context;
}

function RequireAuth({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const { loading, session } = useAuthState();

  if (loading) {
    return <LoadingState label="Checking your session..." />;
  }

  if (!session) {
    return (
      <Navigate
        replace
        state={{ requestedPath: `${location.pathname}${location.search}${location.hash}` }}
        to="/login"
      />
    );
  }

  return <>{children}</>;
}

function sessionInitials(session: AuthSession | null) {
  const displayName = session?.user.displayName?.trim();
  if (!displayName) {
    return "MM";
  }
  const parts = displayName.split(/\s+/).filter(Boolean);
  return parts.slice(0, 2).map((part) => part[0]?.toUpperCase() ?? "").join("") || "MM";
}

type ShellOutletContext = {
  toggleAI: (scope?: "project" | "meeting", meetingId?: string) => void;
  setMeetingAiContext: (meetingId: string | null) => void;
  spaceDetail: SpaceDetail | null;
  spaceLoading: boolean;
  spaceError: Error | null;
  reloadSpace: () => Promise<void>;
};

type MeetingOutletContext = {
  meetingDetail: MeetingDetailResponse | null;
  meetingLoading: boolean;
  meetingError: Error | null;
  reloadMeeting: () => Promise<void>;
  canManageParticipants: boolean;
};

// --- Layouts ---

const AppShell = () => {
  const { session, setSession } = useAuthState();
  const { locale } = useAppPreferences();
  const authSession = session as AuthSession;
  const { spaceId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [isAIOpen, setIsAIOpen] = useState(false);
  const [aiScope, setAiScope] = useState<"project" | "meeting">("project");
  const [selectedMeetingId, setSelectedMeetingId] = useState<string | null>(null);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [workspaceMenuOpen, setWorkspaceMenuOpen] = useState(false);
  const [workspaceMenuLoading, setWorkspaceMenuLoading] = useState(false);
  const [workspaceMenuError, setWorkspaceMenuError] = useState("");
  const [availableSpaces, setAvailableSpaces] = useState<SpaceSummary[]>([]);
  const [createWorkspaceOpen, setCreateWorkspaceOpen] = useState(false);
  const [createWorkspaceName, setCreateWorkspaceName] = useState("");
  const [createWorkspaceDescription, setCreateWorkspaceDescription] = useState("");
  const [createWorkspacePending, setCreateWorkspacePending] = useState(false);
  const [createWorkspaceError, setCreateWorkspaceError] = useState("");
  const [aiWidth, setAiWidth] = useState(400);
  const [logoutPending, setLogoutPending] = useState(false);
  const [logoutError, setLogoutError] = useState("");
  const [spaceDetail, setSpaceDetail] = useState<SpaceDetail | null>(null);
  const [spaceLoading, setSpaceLoading] = useState(true);
  const [spaceError, setSpaceError] = useState<Error | null>(null);
  const [projectAiMessages, setProjectAiMessages] = useState<Array<{
    role: "user" | "assistant";
    text: string;
    sources?: AiSource[];
    unsupported?: boolean;
  }>>([]);
  const [projectAiInput, setProjectAiInput] = useState("");
  const [projectAiLoading, setProjectAiLoading] = useState(false);
  const [projectAiError, setProjectAiError] = useState("");
  const [projectAiModel, setProjectAiModel] = useState("");

  const startDrag = (e: React.MouseEvent) => {
    e.preventDefault();
    const onDrag = (moveEvent: MouseEvent) => {
      const newWidth = document.body.clientWidth - moveEvent.clientX;
      if (newWidth > 300 && newWidth < 800) {
        setAiWidth(newWidth);
      }
    };
    const onMouseUp = () => {
      document.removeEventListener('mousemove', onDrag);
      document.removeEventListener('mouseup', onMouseUp);
    };
    document.addEventListener('mousemove', onDrag);
    document.addEventListener('mouseup', onMouseUp);
  };

  const loadSpace = React.useCallback(async () => {
    if (!session || !spaceId) {
      setSpaceDetail(null);
      setSpaceLoading(false);
      setSpaceError(null);
      return;
    }

    setSpaceLoading(true);
    setSpaceError(null);
    try {
      const detail = await fetchSpaceDetail(session, spaceId);
      setSpaceDetail(detail);
    } catch (cause) {
      setSpaceDetail(null);
      setSpaceError(cause instanceof Error ? cause : new Error("프로젝트를 불러오지 못했습니다."));
    } finally {
      setSpaceLoading(false);
    }
  }, [session, spaceId]);

  useEffect(() => {
    void loadSpace();
  }, [loadSpace]);

  useEffect(() => {
    setWorkspaceMenuOpen(false);
  }, [spaceId]);

  const toggleWorkspaceMenu = async () => {
    if (workspaceMenuOpen) {
      setWorkspaceMenuOpen(false);
      return;
    }

    setWorkspaceMenuOpen(true);
    setWorkspaceMenuError("");
    setWorkspaceMenuLoading(true);
    try {
      const response = await fetchSpaces(authSession);
      setAvailableSpaces(response.spaces);
    } catch (cause) {
      setWorkspaceMenuError(cause instanceof Error ? cause.message : "워크스페이스 목록을 불러오지 못했습니다.");
    } finally {
      setWorkspaceMenuLoading(false);
    }
  };

  const createWorkspaceFromMenu = async () => {
    const name = createWorkspaceName.trim();
    if (!name || createWorkspacePending) {
      return;
    }

    setCreateWorkspacePending(true);
    setCreateWorkspaceError("");
    try {
      const created = await createSpace(authSession, {
        name,
        description: createWorkspaceDescription.trim() || null
      });
      setCreateWorkspaceOpen(false);
      setWorkspaceMenuOpen(false);
      setCreateWorkspaceName("");
      setCreateWorkspaceDescription("");
      navigate(`/spaces/${encodeURIComponent(created.id)}`);
    } catch (cause) {
      setCreateWorkspaceError(cause instanceof Error ? cause.message : "워크스페이스를 생성하지 못했습니다.");
    } finally {
      setCreateWorkspacePending(false);
    }
  };

  const setMeetingAiContext = React.useCallback((meetingId: string | null) => {
    setSelectedMeetingId(meetingId);
    if (meetingId === null) {
      setAiScope("project");
    }
  }, []);

  const openAi = (scope: "project" | "meeting" = "project", meetingId?: string) => {
    const isSameProjectPanel = isAIOpen && scope === "project" && aiScope === "project";
    const isSameMeetingPanel = isAIOpen && scope === "meeting" && aiScope === "meeting" && !!meetingId && meetingId === selectedMeetingId;

    if (isSameProjectPanel || isSameMeetingPanel) {
      setIsAIOpen(false);
      return;
    }

    if (scope === "meeting") {
      if (!meetingId) {
        return;
      }
      setSelectedMeetingId(meetingId);
      setAiScope("meeting");
      setIsAIOpen(true);
      return;
    }

    setAiScope("project");
    setIsAIOpen(true);
  };

  useEffect(() => {
    if (!isAIOpen || !spaceDetail || !session) {
      return;
    }

    let active = true;
    setProjectAiError("");
    setProjectAiModel("");
    setProjectAiMessages([
      {
        role: "assistant",
        text: aiScope === "meeting"
          ? "현재 선택한 회의 범위에서만 질문할 수 있습니다. 근거가 없는 내용은 답하지 않습니다."
          : `${spaceDetail.name}의 공식 지식과 접근 가능한 회의만 검색합니다.`
      }
    ]);

    if (aiScope === "meeting") {
      return () => {
        active = false;
      };
    }

    void fetchProjectAiHistory(authSession, spaceDetail.id)
      .then((history) => {
        if (!active || history.messages.length === 0) {
          return;
        }
        setProjectAiMessages(history.messages.map((message) => ({
          role: message.role === "USER" ? "user" : "assistant",
          text: message.content
        })));
      })
      .catch(() => {});

    return () => {
      active = false;
    };
  }, [aiScope, authSession, isAIOpen, session, spaceDetail]);

  const getBreadcrumbs = () => {
    const paths = location.pathname.split('/').filter(Boolean);
    if (paths.length === 0) return [];

    return paths.map((path, index) => {
      const url = `/${paths.slice(0, index + 1).join('/')}`;
      let label = path;
      if (path === 'spaces') label = locale === "ko" ? "워크스페이스" : "Workspaces";
      if (path === spaceId) {
        label = spaceDetail?.name ?? (spaceLoading ? "Loading project..." : path);
      }
      if (path.startsWith("meeting-")) label = locale === "ko" ? "회의" : "Meeting";
      return { label, url };
    });
  };

  const handleLogout = async (event: React.MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    if (logoutPending) {
      return;
    }
    setLogoutError("");
    setLogoutPending(true);
    try {
      await logoutCurrentSession();
      setSession(null);
      navigate("/login", { replace: true });
    } catch (error) {
      setLogoutError(error instanceof Error ? error.message : "Sign out failed.");
    } finally {
      setLogoutPending(false);
    }
  };

  const projectName = spaceDetail?.name ?? (spaceLoading ? "Loading project..." : "Unknown project");
  const canAccessAdministration = spaceDetail?.role === "OWNER" || spaceDetail?.role === "ADMIN";
  const projectInitial = projectName.charAt(0).toUpperCase() || "P";
  const korean = locale === "ko";
  const projectAiAvailable = spaceDetail?.aiEntrypoints.includes("project-ai") ?? true;
  const activeAiAvailable = aiScope === "meeting" ? Boolean(selectedMeetingId) : projectAiAvailable;
  const canOpenAi = projectAiAvailable || Boolean(selectedMeetingId);
  const outletContext: ShellOutletContext = {
    toggleAI: openAi,
    setMeetingAiContext,
    spaceDetail,
    spaceLoading,
    spaceError,
    reloadSpace: loadSpace
  };

  async function handleAiAsk(question: string) {
    const trimmed = question.trim();
    if (!trimmed || !spaceDetail || projectAiLoading || !activeAiAvailable) {
      return;
    }

    setProjectAiInput("");
    setProjectAiError("");
    setProjectAiLoading(true);
    setProjectAiMessages((current) => [...current, { role: "user", text: trimmed }]);
    try {
      const response = aiScope === "meeting"
        ? await chatMeetingAi(authSession, selectedMeetingId as string, { question: trimmed })
        : await chatProjectAi(authSession, spaceDetail.id, { question: trimmed });
      setProjectAiModel(response.model);
      setProjectAiMessages((current) => [
        ...current,
        {
          role: "assistant",
          text: response.unsupported ? unsupportedAiMessage(response.unsupportedReason, aiScope) : response.answer,
          sources: response.sources,
          unsupported: response.unsupported
        }
      ]);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : `${aiScope === "meeting" ? "Meeting AI" : "Project AI"}에 연결하지 못했습니다.`;
      setProjectAiError(message);
      setProjectAiMessages((current) => [
        ...current,
        {
          role: "assistant",
          text: `${aiScope === "meeting" ? "Meeting AI" : "Project AI"} 응답을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.`,
          unsupported: true
        }
      ]);
    } finally {
      setProjectAiLoading(false);
    }
  }

  return (
    <div className="mm-active-app-shell flex h-screen bg-background font-sans text-foreground">
      {/* Sidebar */}
      <aside className={`${isSidebarOpen ? 'w-64' : 'w-[68px]'} border-r border-border bg-card flex flex-col h-full shrink-0 transition-all duration-300 relative z-20`}>
        {/* Project Selector & Collapse Button */}
        <div className="h-14 flex items-center border-b border-border shrink-0 overflow-visible px-3">
          {isSidebarOpen ? (
            <>
              <div className="relative min-w-0 flex-1">
                <button
                  aria-expanded={workspaceMenuOpen}
                  aria-haspopup="menu"
                  className="flex w-full items-center gap-3 rounded p-1.5 text-left transition-colors hover:bg-muted/50 focus:outline-none focus:ring-2 focus:ring-primary/50"
                  onClick={() => void toggleWorkspaceMenu()}
                  title={korean ? "워크스페이스 전환" : "Switch workspace"}
                  type="button"
                >
                  <ProfileAvatar
                    alt={projectName}
                    className="h-7 w-7 shrink-0 rounded-md text-xs font-bold"
                    initials={projectInitial}
                    pictureUrl={spaceDetail?.imageUrl}
                  />
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-semibold truncate leading-none">{projectName}</div>
                  </div>
                  <ChevronDown className={`h-4 w-4 shrink-0 text-muted-foreground transition-transform ${workspaceMenuOpen ? "rotate-180" : ""}`} />
                </button>
                {workspaceMenuOpen ? (
                  <div className="absolute left-0 top-[calc(100%+0.5rem)] z-50 w-72 overflow-hidden rounded-lg border border-border bg-card p-1.5 shadow-xl" role="menu">
                    <p className="px-2.5 py-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                      {korean ? "워크스페이스 전환" : "Switch workspace"}
                    </p>
                    {workspaceMenuLoading ? <p className="px-2.5 py-3 text-sm text-muted-foreground">{korean ? "불러오는 중..." : "Loading workspaces..."}</p> : null}
                    {!workspaceMenuLoading && workspaceMenuError ? <p className="px-2.5 py-3 text-sm text-red-600">{workspaceMenuError}</p> : null}
                    {!workspaceMenuLoading && !workspaceMenuError && availableSpaces.length === 0 ? <p className="px-2.5 py-3 text-sm text-muted-foreground">{korean ? "접근 가능한 워크스페이스가 없습니다." : "No workspaces available."}</p> : null}
                    {!workspaceMenuLoading && !workspaceMenuError ? availableSpaces.map((space) => {
                      const active = space.id === spaceId;
                      return (
                        <button
                          aria-current={active ? "page" : undefined}
                          className={`flex w-full items-center gap-3 rounded-md px-2.5 py-2.5 text-left text-sm transition-colors ${active ? "bg-primary/10 text-primary" : "text-foreground hover:bg-muted"}`}
                          disabled={active}
                          key={space.id}
                          onClick={() => navigate(`/spaces/${encodeURIComponent(space.id)}`)}
                          role="menuitem"
                          type="button"
                        >
                          <ProfileAvatar
                            alt={space.name}
                            className={`h-7 w-7 shrink-0 rounded text-xs font-semibold ${active ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"}`}
                            initials={space.name.charAt(0).toUpperCase() || "P"}
                            pictureUrl={space.imageUrl}
                          />
                          <span className="min-w-0 flex-1 truncate font-medium">{space.name}</span>
                          <span className="shrink-0 text-xs text-muted-foreground">{space.role}</span>
                        </button>
                      );
                    }) : null}
                    <div className="mx-1.5 mt-1.5 border-t border-border pt-1.5">
                      <button
                        className="flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-sm font-semibold text-primary transition-colors hover:bg-primary/10"
                        onClick={() => {
                          setCreateWorkspaceError("");
                          setCreateWorkspaceOpen(true);
                        }}
                        role="menuitem"
                        type="button"
                      >
                        <Plus className="h-4 w-4" />
                        {korean ? "새 워크스페이스" : "New workspace"}
                      </button>
                    </div>
                  </div>
                ) : null}
              </div>
              <button
                onClick={() => setIsSidebarOpen(false)}
                className="text-muted-foreground hover:bg-muted hover:text-foreground transition-colors shrink-0 p-1.5 rounded-md ml-1"
                title={korean ? "사이드바 접기" : "Collapse Sidebar"}
              >
                <PanelLeftClose className="w-4 h-4" />
              </button>
            </>
          ) : (
            <button
              onClick={() => setIsSidebarOpen(true)}
              className="text-muted-foreground hover:bg-muted hover:text-foreground transition-colors p-1.5 rounded-md mx-auto"
              title={korean ? "사이드바 펼치기" : "Expand Sidebar"}
            >
              <PanelLeftOpen className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Navigation */}
        <div className="flex-1 overflow-y-auto py-4 flex flex-col gap-6 custom-scrollbar overflow-x-hidden">
          <div className="px-3">
            <nav className="flex flex-col gap-0.5">
              <NavLink to={`/spaces/${spaceId}`} end title={korean ? "개요" : "Overview"} className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-semibold transition-colors ${isActive && !isAIOpen ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <LayoutDashboard className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>{korean ? "개요" : "Overview"}</span>
              </NavLink>
              <button
                disabled={!canOpenAi}
                onClick={() => openAi("project")}
                title="AI"
                className={`flex w-full items-center gap-3 px-3 py-2 rounded-md text-sm font-semibold transition-colors ${isAIOpen ? 'bg-[color:var(--app-ai-soft)] text-[color:var(--app-ai)]' : 'text-muted-foreground hover:bg-[color:var(--app-ai-soft)] hover:text-[color:var(--app-ai)]'} ${!isSidebarOpen && 'justify-center'} ${!canOpenAi ? 'opacity-50 cursor-not-allowed hover:bg-transparent hover:text-muted-foreground' : ''}`}
              >
                <Sparkles className="w-4 h-4 shrink-0 text-[color:var(--app-ai)]" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>AI</span>
              </button>
            </nav>
          </div>

          <div className="px-3">
            {isSidebarOpen && <div className="px-3 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-2 whitespace-nowrap">{korean ? "협업" : "Collaboration"}</div>}
            {!isSidebarOpen && <div className="h-px w-full bg-border my-2"></div>}
            <nav className="flex flex-col gap-0.5">
              <NavLink to={`/spaces/${spaceId}/meetings`} title={korean ? "회의" : "Meetings"} className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-semibold transition-colors ${isActive ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <Video className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>{korean ? "회의" : "Meetings"}</span>
              </NavLink>
              <NavLink to={`/spaces/${spaceId}/tasks`} title={korean ? "태스크" : "Tasks"} className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-semibold transition-colors ${isActive ? 'bg-[color:var(--app-action-soft)] text-[color:var(--app-action)]' : 'text-muted-foreground hover:bg-[color:var(--app-action-soft)] hover:text-[color:var(--app-action)]'} ${!isSidebarOpen && 'justify-center'}`}>
                <CheckSquare className="w-4 h-4 shrink-0 text-[color:var(--app-action)]" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>{korean ? "태스크" : "Tasks"}</span>
              </NavLink>
              <NavLink to={`/spaces/${spaceId}/knowledge`} title={korean ? "지식" : "Knowledge"} className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-semibold transition-colors ${isActive ? 'bg-[color:var(--app-knowledge-soft)] text-[color:var(--app-knowledge)]' : 'text-muted-foreground hover:bg-[color:var(--app-knowledge-soft)] hover:text-[color:var(--app-knowledge)]'} ${!isSidebarOpen && 'justify-center'}`}>
                <Library className="w-4 h-4 shrink-0 text-[color:var(--app-knowledge)]" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>{korean ? "지식" : "Knowledge"}</span>
              </NavLink>
              <NavLink to={`/spaces/${spaceId}/calendar`} title={korean ? "캘린더" : "Calendar"} className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-semibold transition-colors ${isActive ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <Calendar className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>{korean ? "캘린더" : "Calendar"}</span>
              </NavLink>
            </nav>
          </div>

          {canAccessAdministration ? (
            <div className="px-3">
              {isSidebarOpen && <div className="px-3 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-2 whitespace-nowrap">{korean ? "관리" : "Administration"}</div>}
              {!isSidebarOpen && <div className="h-px w-full bg-border my-2"></div>}
              <nav className="flex flex-col gap-0.5">
                <NavLink to={`/spaces/${spaceId}/members`} title={korean ? "멤버" : "Members"} className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-semibold transition-colors ${isActive ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                  <Users className="w-4 h-4 shrink-0" />
                  <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>{korean ? "멤버" : "Members"}</span>
                </NavLink>
                <NavLink to={`/spaces/${spaceId}/terms`} title={korean ? "용어사전" : "Terms Dictionary"} className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-semibold transition-colors ${isActive ? 'bg-[color:var(--app-knowledge-soft)] text-[color:var(--app-knowledge)]' : 'text-muted-foreground hover:bg-[color:var(--app-knowledge-soft)] hover:text-[color:var(--app-knowledge)]'} ${!isSidebarOpen && 'justify-center'}`}>
                  <BookOpen className="w-4 h-4 shrink-0 text-[color:var(--app-knowledge)]" />
                  <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>{korean ? "용어사전" : "Terms Dictionary"}</span>
                </NavLink>
              </nav>
            </div>
          ) : null}
          <div className="px-3">
            <nav className="flex flex-col gap-0.5">
              <NavLink to={`/spaces/${spaceId}/settings`} title={korean ? "설정" : "Settings"} className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-semibold transition-colors ${isActive ? 'bg-primary/10 text-primary' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <Settings className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>{korean ? "설정" : "Settings"}</span>
              </NavLink>
            </nav>
          </div>
        </div>

        {/* User Profile */}
        <div className="p-3 border-t border-border overflow-hidden whitespace-nowrap">
          <div
            onClick={() => navigate('/settings')}
            className={`flex items-center gap-3 p-2 rounded-md hover:bg-muted cursor-pointer transition-colors ${!isSidebarOpen && 'justify-center w-full'}`}
            title="Account Settings"
          >
            <ProfileAvatar
              alt={session?.user.displayName ?? "MeetingMind User"}
              className="h-7 w-7 shrink-0 rounded-md text-xs font-medium"
              initials={sessionInitials(session)}
              pictureUrl={session?.user.pictureUrl}
            />
            <div className={`flex-1 overflow-hidden transition-opacity duration-300 ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>
              <div className="text-sm font-medium truncate">{session?.user.displayName ?? "MeetingMind User"}</div>
            </div>
            {isSidebarOpen && (
              <div className="flex items-center gap-1 shrink-0">
                <Settings className="w-4 h-4 text-muted-foreground" />
                <button
                  className="w-7 h-7 flex items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
                  disabled={logoutPending}
                  onClick={handleLogout}
                  title="Sign Out"
                  type="button"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            )}
          </div>
          {isSidebarOpen && logoutError && (
            <p className="px-2 pt-1 text-[11px] text-red-600 truncate">{logoutError}</p>
          )}
        </div>
      </aside>

      {createWorkspaceOpen ? (
        <div className="fixed inset-0 z-[70] flex items-center justify-center bg-black/40 p-4" role="presentation">
          <section aria-labelledby="create-workspace-title" aria-modal="true" className="w-full max-w-md rounded-lg border border-border bg-card p-6 shadow-xl" role="dialog">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-lg font-semibold text-foreground" id="create-workspace-title">{korean ? "새 워크스페이스" : "New workspace"}</h2>
                <p className="mt-1 text-sm text-muted-foreground">{korean ? "새 프로젝트 협업 공간을 만듭니다." : "Create a new project collaboration space."}</p>
              </div>
              <button aria-label={korean ? "닫기" : "Close"} className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground" onClick={() => setCreateWorkspaceOpen(false)} type="button"><X className="h-4 w-4" /></button>
            </div>
            <div className="mt-5 space-y-4">
              <label className="block text-sm font-medium text-foreground">
                {korean ? "워크스페이스 이름" : "Workspace name"}
                <input autoFocus className="mt-1.5 w-full rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-primary/50" onChange={(event) => setCreateWorkspaceName(event.target.value)} placeholder={korean ? "예: Q3 제품 출시" : "e.g. Q3 product launch"} value={createWorkspaceName} />
              </label>
              <label className="block text-sm font-medium text-foreground">
                {korean ? "설명" : "Description"} <span className="font-normal text-muted-foreground">({korean ? "선택" : "optional"})</span>
                <textarea className="mt-1.5 min-h-24 w-full resize-y rounded-md border border-border bg-background px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-primary/50" onChange={(event) => setCreateWorkspaceDescription(event.target.value)} placeholder={korean ? "이 프로젝트의 목적을 입력하세요." : "What is this project about?"} value={createWorkspaceDescription} />
              </label>
              {createWorkspaceError ? <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{createWorkspaceError}</p> : null}
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <button className="rounded-md border border-border px-3 py-2 text-sm font-medium text-foreground hover:bg-muted" disabled={createWorkspacePending} onClick={() => setCreateWorkspaceOpen(false)} type="button">{korean ? "취소" : "Cancel"}</button>
              <button className="rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground hover:bg-primary/90 disabled:opacity-60" disabled={!createWorkspaceName.trim() || createWorkspacePending} onClick={() => void createWorkspaceFromMenu()} type="button">{createWorkspacePending ? (korean ? "생성 중..." : "Creating...") : (korean ? "워크스페이스 생성" : "Create workspace")}</button>
            </div>
          </section>
        </div>
      ) : null}

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top Header */}
        <header className="h-14 px-3 sm:px-6 flex items-center justify-between border-b border-border bg-background shrink-0">
          <div className="flex items-center gap-2 text-sm min-w-0">
            {getBreadcrumbs().map((crumb, i, arr) => (
              <div key={crumb.url} className="flex items-center gap-2 shrink-0">
                <Link to={crumb.url} className={`hover:underline truncate max-w-[150px] ${i === arr.length - 1 ? 'text-foreground font-medium' : 'text-muted-foreground'}`}>
                  {crumb.label}
                </Link>
                {i < arr.length - 1 && <span className="text-muted-foreground">/</span>}
              </div>
            ))}
          </div>

          <div className="flex items-center gap-3">
            <div className="relative hidden sm:block">
              <Search className="w-4 h-4 absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
              <input
                type="text"
                placeholder={korean ? "검색..." : "Search..."}
                className="pl-8 pr-3 py-1.5 w-64 rounded-md bg-muted/50 border border-transparent text-sm focus:bg-background focus:border-border focus:outline-none focus:ring-1 focus:ring-primary/50 transition-all"
              />
            </div>
            <DisplayPreferences compact />
            <button aria-label={korean ? "알림" : "Notifications"} className="w-8 h-8 flex items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors relative">
              <Bell className="w-4 h-4" />
              <span className="absolute top-2 right-2 w-1.5 h-1.5 rounded-full bg-blue-500"></span>
            </button>
          </div>
        </header>

        {/* Page Content & AI Overlay */}
        <main className="flex-1 min-h-0 bg-muted/10 relative flex overflow-hidden">
          <div className="flex-1 min-w-0 overflow-y-auto custom-scrollbar flex flex-col">
            <Outlet context={outletContext} />
          </div>

          {/* Project AI Slide-out Drawer */}
          {isAIOpen && (
            <div
              style={{ width: `min(100vw, ${aiWidth}px)` }}
              className="absolute inset-y-0 right-0 z-30 border-l border-border bg-card shadow-[-10px_0_30px_-15px_rgba(0,0,0,0.1)] flex flex-col"
            >
              {/* Drag Handle */}
              <div
                className="absolute left-0 top-0 bottom-0 w-1.5 cursor-col-resize hover:bg-primary/50 active:bg-primary z-50 transition-colors flex items-center justify-center group"
                onMouseDown={startDrag}
              >
                <div className="opacity-0 group-hover:opacity-100 transition-opacity flex flex-col items-center justify-center">
                  <div className="w-1 h-8 rounded-full bg-primary/80"></div>
                </div>
              </div>

              <div className="p-4 border-b border-border bg-muted/30">
                <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6 rounded bg-[color:var(--app-ai-soft)] text-[color:var(--app-ai)] flex items-center justify-center">
                    <Sparkles className="w-3.5 h-3.5" />
                  </div>
                  <h2 className="font-semibold text-sm">{aiScope === "meeting" ? "Meeting AI" : "Project AI"}</h2>
                </div>
                <button
                  onClick={() => setIsAIOpen(false)}
                  className="w-7 h-7 flex items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
                </div>
                <div className="mt-3 inline-flex rounded-md bg-muted p-1" role="tablist" aria-label="AI search scope">
                  <button aria-selected={aiScope === "project"} className={`rounded px-3 py-1.5 text-xs font-semibold transition-colors ${aiScope === "project" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`} disabled={!projectAiAvailable} onClick={() => openAi("project")} role="tab" type="button">Project AI</button>
                  <button aria-selected={aiScope === "meeting"} className={`rounded px-3 py-1.5 text-xs font-semibold transition-colors ${aiScope === "meeting" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`} disabled={!selectedMeetingId} onClick={() => selectedMeetingId && openAi("meeting", selectedMeetingId)} role="tab" title={selectedMeetingId ? "Search this meeting only" : "Open AI from a meeting you can access to use Meeting AI"} type="button">Meeting AI</button>
                </div>
              </div>

              <div className="p-4 border-b border-border bg-card text-xs text-muted-foreground flex items-center gap-2">
                <ShieldAlert className="w-3.5 h-3.5" />
                {aiScope === "meeting" ? "Searching the selected meeting only." : "Searching only official knowledge and authorized meetings."}
              </div>

              <div className="flex-1 overflow-y-auto p-4 space-y-6">
                {projectAiMessages.map((message, index) => (
                  <div key={`${message.role}-${index}`} className={`flex ${message.role === "user" ? "justify-end" : "justify-start"}`}>
                    <div className={`${message.role === "user" ? "bg-foreground text-background rounded-tr-sm max-w-[90%]" : "bg-muted text-foreground rounded-tl-sm max-w-[95%]"} px-4 py-3 rounded-2xl text-sm`}>
                      <p>{message.text}</p>
                      {message.sources?.length ? (
                        <div className="bg-card rounded-lg p-3 border border-border space-y-2 mt-3">
                          <div className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider">Sources</div>
                          <div className="flex flex-wrap gap-1.5">
                            {message.sources.map((source) => (
                              <span key={`${source.sourceId}-${source.type}`} className={`text-[10px] font-medium px-2 py-0.5 rounded-full border ${aiSourceTone(source.type)}`}>
                                {aiSourceLabel(source)}
                              </span>
                            ))}
                          </div>
                        </div>
                      ) : null}
                    </div>
                  </div>
                ))}
                {projectAiLoading ? <p className="text-xs text-muted-foreground">근거를 확인하고 있습니다...</p> : null}
                {projectAiError ? <p className="text-xs text-red-600">{projectAiError}</p> : null}
              </div>

              <div className="p-4 border-t border-border bg-card">
                <div className="flex gap-2 mb-3 overflow-x-auto custom-scrollbar pb-1">
                  <button className="whitespace-nowrap px-3 py-1.5 rounded-full border border-border text-xs text-muted-foreground hover:bg-muted transition-colors" onClick={() => void handleAiAsk(aiScope === "meeting" ? "이 회의의 핵심 결정사항을 요약해줘" : "최근 확정된 결정사항을 요약해줘")} type="button">{aiScope === "meeting" ? "회의 결정 요약" : "최근 결정 요약"}</button>
                  <button className="whitespace-nowrap px-3 py-1.5 rounded-full border border-border text-xs text-muted-foreground hover:bg-muted transition-colors" onClick={() => void handleAiAsk(aiScope === "meeting" ? "이 회의의 실행 항목을 알려줘" : "현재 열려 있는 태스크를 요약해줘")} type="button">{aiScope === "meeting" ? "회의 실행 항목" : "열린 태스크"}</button>
                </div>
                <div className="relative">
                  <input
                    type="text"
                    placeholder={aiScope === "meeting" ? "Ask Meeting AI..." : "Ask Project AI..."}
                    value={projectAiInput}
                    onChange={(event) => setProjectAiInput(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        void handleAiAsk(projectAiInput);
                      }
                    }}
                    className="w-full bg-muted border-none rounded-xl pl-4 pr-10 py-3 text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 transition-all"
                  />
                  <button className="absolute right-2 top-1/2 -translate-y-1/2 w-7 h-7 rounded-md bg-foreground hover:bg-foreground/90 text-background flex items-center justify-center transition-colors disabled:opacity-60" disabled={!projectAiInput.trim() || projectAiLoading || !activeAiAvailable} onClick={() => void handleAiAsk(projectAiInput)} type="button">
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
                {projectAiModel ? <p className="mt-2 text-[10px] text-muted-foreground">모델: {projectAiModel}</p> : null}
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
};

// --- Pages ---

// 2. Workspace Home (/spaces)
const WorkspaceHome = () => {
  const { session, setSession } = useAuthState();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const guestWorkspace = location.pathname === "/guest";
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [spaces, setSpaces] = useState<Array<{
    id: string;
    name: string;
    imageUrl: string | null;
    role: string;
    meetings: number;
    members: number | null;
    tasks: number | null;
  }>>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createDescription, setCreateDescription] = useState("");
  const [createPending, setCreatePending] = useState(false);
  const [createError, setCreateError] = useState("");
  const [pendingInvitations, setPendingInvitations] = useState<Awaited<ReturnType<typeof fetchPendingSpaceInvitations>>["invitations"]>([]);
  const [invitationsOpen, setInvitationsOpen] = useState(false);
  const [invitationPendingId, setInvitationPendingId] = useState("");
  const [invitationError, setInvitationError] = useState("");
  const [profileOpen, setProfileOpen] = useState(false);
  const [profileLogoutPending, setProfileLogoutPending] = useState(false);
  const [guestMeetings, setGuestMeetings] = useState<MeetingSummary[]>([]);
  const selectedGuestMeetingId = searchParams.get("meetingId") ?? "";
  const selectedGuestMeeting = guestMeetings.find((meeting) => meeting.id === selectedGuestMeetingId) ?? guestMeetings[0] ?? null;

  const loadSpaces = React.useCallback(async () => {
    if (!session) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      const response = await fetchSpaces(session);
      const hydrated = await Promise.all(
        response.spaces.map(async (space) => {
          const [membersResult, tasksResult] = await Promise.allSettled([
            fetchSpaceMembers(session, space.id),
            fetchTasks(session, space.id)
          ]);

          return {
            id: space.id,
            name: space.name,
            imageUrl: space.imageUrl,
            role: space.role,
            meetings: space.meetingCount,
            members: membersResult.status === "fulfilled" ? membersResult.value.members.length : null,
            tasks: tasksResult.status === "fulfilled" ? tasksResult.value.tasks.length : null
          };
        })
      );
      setSpaces(hydrated);
      const accessible = await fetchAccessibleMeetings(session).catch(() => ({ meetings: [] as MeetingSummary[] }));
      setGuestMeetings(accessible.meetings);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "워크스페이스를 불러오지 못했습니다.");
      setSpaces([]);
    } finally {
      setLoading(false);
    }
  }, [session]);

  useEffect(() => {
    void loadSpaces();
  }, [loadSpaces]);

  const loadPendingInvitations = React.useCallback(async () => {
    if (!session) return;
    try {
      const response = await fetchPendingSpaceInvitations(session);
      setPendingInvitations(response.invitations);
    } catch (cause) {
      setInvitationError(cause instanceof Error ? cause.message : "초대 알림을 불러오지 못했습니다.");
    }
  }, [session]);

  useEffect(() => {
    void loadPendingInvitations();
  }, [loadPendingInvitations]);

  async function resolvePendingInvitation(invitation: (typeof pendingInvitations)[number], accept: boolean) {
    if (!session || invitationPendingId) return;
    setInvitationPendingId(invitation.invitationId);
    setInvitationError("");
    try {
      if (accept) {
        await acceptPendingSpaceInvitation(session, invitation.spaceId, invitation.invitationId);
        await loadSpaces();
      } else {
        await declinePendingSpaceInvitation(session, invitation.spaceId, invitation.invitationId);
      }
      setPendingInvitations((previous) => previous.filter((item) => item.invitationId !== invitation.invitationId));
    } catch (cause) {
      setInvitationError(cause instanceof Error ? cause.message : "초대를 처리하지 못했습니다.");
    } finally {
      setInvitationPendingId("");
    }
  }

  async function handleWorkspaceLogout() {
    if (profileLogoutPending) return;
    setProfileLogoutPending(true);
    try {
      await logoutCurrentSession();
      setSession(null);
      navigate("/", { replace: true });
    } finally {
      setProfileLogoutPending(false);
      setProfileOpen(false);
    }
  }

  async function handleCreateSpace(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || createPending) {
      return;
    }

    const normalizedName = createName.trim();
    if (!normalizedName) {
      setCreateError("프로젝트 이름을 입력해 주세요.");
      return;
    }

    setCreatePending(true);
    setCreateError("");
    try {
      const created = await createSpace(session, {
        name: normalizedName,
        description: createDescription.trim() || null
      });
      setSpaces((previous) => [
        {
          id: created.id,
          name: created.name,
          imageUrl: null,
          role: created.role,
          meetings: 0,
          members: 1,
          tasks: 0
        },
        ...previous
      ]);
      setCreateName("");
      setCreateDescription("");
      setCreateOpen(false);
    } catch (cause) {
      setCreateError(cause instanceof Error ? cause.message : "프로젝트를 생성하지 못했습니다.");
    } finally {
      setCreatePending(false);
    }
  }

  return (
    <div className="min-h-screen bg-muted/10 p-8 lg:p-12">
      <div className="max-w-6xl mx-auto">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h1 className="text-2xl font-bold text-foreground">{guestWorkspace ? "Guest workspace" : "Your Spaces"}</h1>
            <p className="text-sm text-muted-foreground">{guestWorkspace ? "Access only the meetings shared with you." : "Select a project space to continue your work."}</p>
          </div>
          <div className="flex items-center gap-3">
          <div className="relative hidden sm:block">
            <button aria-expanded={profileOpen} className="flex h-[38px] items-center gap-2 rounded-lg border border-foreground bg-card px-3 text-left hover:bg-muted transition-colors" onClick={() => setProfileOpen((value) => !value)} type="button">
              {session?.user.pictureUrl ? <img alt="" className="h-7 w-7 rounded-full object-cover" src={session.user.pictureUrl} /> : <div className="h-7 w-7 rounded-full bg-foreground text-background flex items-center justify-center text-xs font-semibold">{session?.user.displayName?.charAt(0) ?? "U"}</div>}
              <div className="min-w-0 -space-y-px"><p className="max-w-32 truncate text-xs font-semibold leading-4 text-foreground">{session?.user.displayName}</p><p className="max-w-40 truncate text-[11px] leading-4 text-muted-foreground">{session?.user.email}</p></div>
            </button>
            {profileOpen ? <div className="absolute right-0 top-11 z-30 w-44 rounded-lg border border-border bg-card p-1.5 shadow-xl"><button className="flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-foreground hover:bg-muted disabled:opacity-50" disabled={profileLogoutPending} onClick={() => void handleWorkspaceLogout()} type="button"><LogOut className="h-4 w-4" /> {profileLogoutPending ? "Logging out..." : "Log out"}</button></div> : null}
          </div>
          <div className="relative">
            <button
              aria-label="Space invitations"
              className="relative h-[38px] w-9 rounded-md border border-foreground bg-card text-foreground hover:bg-muted transition-colors inline-flex items-center justify-center"
              onClick={() => { setInvitationsOpen((value) => !value); setInvitationError(""); }}
              type="button"
            >
              <Bell className="w-4 h-4" />
              {pendingInvitations.length > 0 ? <span className="absolute -right-1 -top-1 min-w-4 h-4 px-1 rounded-full bg-blue-600 text-white text-[10px] leading-4 font-semibold">{pendingInvitations.length}</span> : null}
            </button>
            {invitationsOpen ? (
              <div className="absolute right-0 top-11 z-30 w-[min(360px,calc(100vw-2rem))] overflow-hidden rounded-xl border border-border bg-card shadow-xl">
                <div className="flex items-center justify-between border-b border-border px-4 py-3"><div><h2 className="text-sm font-semibold text-foreground">Space invitations</h2><p className="mt-0.5 text-xs text-muted-foreground">Invitations waiting for your response</p></div><button aria-label="Close invitations" className="rounded-md px-2 py-1 text-xs text-muted-foreground hover:bg-muted hover:text-foreground" onClick={() => setInvitationsOpen(false)} type="button">Close</button></div>
                <div className="max-h-80 overflow-y-auto px-4">
                {invitationError ? <p className="py-3 text-xs text-red-600">{invitationError}</p> : null}
                {pendingInvitations.length === 0 ? <p className="py-8 text-center text-sm text-muted-foreground">No pending invitations.</p> : pendingInvitations.map((invitation) => (
                  <div className="border-b border-border py-4 last:border-b-0" key={invitation.invitationId}>
                    <p className="text-sm font-medium">{invitation.spaceName}</p>
                    <p className="mt-1 text-xs text-muted-foreground">Invited as {invitation.role.toLowerCase()}</p>
                    <div className="mt-3 flex justify-end gap-2"><button className="rounded-md border border-border px-2.5 py-1.5 text-xs" disabled={Boolean(invitationPendingId)} onClick={() => void resolvePendingInvitation(invitation, false)} type="button">Decline</button><button className="rounded-md bg-foreground px-2.5 py-1.5 text-xs text-background" disabled={Boolean(invitationPendingId)} onClick={() => void resolvePendingInvitation(invitation, true)} type="button">Accept</button></div>
                  </div>
                ))}
                </div>
              </div>
            ) : null}
          </div>
          <button
            className="h-[38px] bg-foreground text-background px-4 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors inline-flex items-center gap-2"
            data-testid="create-space-trigger"
            onClick={() => {
              setCreateError("");
              setCreateOpen(true);
            }}
            type="button"
          >
            <Plus className="w-4 h-4" /> New Space
          </button>
          {guestMeetings.length > 0 && !guestWorkspace ? (
            <button
              className="h-[38px] rounded-md border border-foreground bg-card px-3 text-sm font-medium text-foreground transition-colors hover:bg-muted"
              onClick={() => navigate(`/guest?meetingId=${encodeURIComponent(guestMeetings[0].id)}`)}
              type="button"
            >
              Guest meetings <span className="ml-1 text-muted-foreground">{guestMeetings.length}</span>
            </button>
          ) : null}
          </div>
        </div>

        {loading ? <LoadingState label="Loading spaces..." /> : null}

        {!loading && error ? (
          <ErrorState
            desc={error}
            onRetry={() => {
              void loadSpaces();
            }}
            title="Couldn't load your spaces"
          />
        ) : null}

        {!loading && !error && spaces.length === 0 ? (
          guestWorkspace && guestMeetings.length > 0 ? (
            <section className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(320px,380px)]">
              <div className="rounded-lg border border-border bg-card p-5">
                <div className="mb-5 flex items-start justify-between gap-4">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-wide text-primary">Guest workspace</p>
                    <h2 className="mt-1 text-xl font-semibold text-foreground">{selectedGuestMeeting?.title ?? "Meeting access"}</h2>
                    <p className="mt-1 text-sm text-muted-foreground">Project space ID · {selectedGuestMeeting?.spaceId}</p>
                  </div>
                  <span className="shrink-0 rounded-full border border-border bg-muted px-2.5 py-1 text-xs font-medium text-muted-foreground">Guest</span>
                </div>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <button className="flex min-h-24 flex-col items-start justify-between rounded-md border border-foreground p-4 text-left transition-colors hover:border-primary/40 hover:bg-primary/5" onClick={() => selectedGuestMeeting && navigate(`/spaces/${selectedGuestMeeting.spaceId}/meetings/${selectedGuestMeeting.id}/${selectedGuestMeeting.status === "IN_PROGRESS" ? "live" : "live/prejoin"}`)} type="button">
                    <Video className="h-5 w-5 text-primary" />
                    <span><span className="block text-sm font-semibold text-foreground">Join meeting</span><span className="mt-1 block text-xs text-muted-foreground">Check camera and microphone before joining.</span></span>
                  </button>
                  <button className="flex min-h-24 flex-col items-start justify-between rounded-md border border-foreground p-4 text-left transition-colors hover:border-primary/40 hover:bg-primary/5" onClick={() => selectedGuestMeeting && navigate(`/guest/meetings/${selectedGuestMeeting.id}/ai`)} type="button">
                    <Sparkles className="h-5 w-5 text-primary" />
                    <span><span className="block text-sm font-semibold text-foreground">Meeting AI</span><span className="mt-1 block text-xs text-muted-foreground">Ask questions about this meeting only.</span></span>
                  </button>
                  <button className="flex min-h-24 flex-col items-start justify-between rounded-md border border-foreground p-4 text-left transition-colors hover:border-primary/40 hover:bg-primary/5" onClick={() => selectedGuestMeeting && navigate(`/spaces/${selectedGuestMeeting.spaceId}/meetings/${selectedGuestMeeting.id}/transcript`)} type="button">
                    <FileText className="h-5 w-5 text-primary" />
                    <span><span className="block text-sm font-semibold text-foreground">Transcript</span><span className="mt-1 block text-xs text-muted-foreground">View the transcript for this meeting.</span></span>
                  </button>
                  <button className="flex min-h-24 flex-col items-start justify-between rounded-md border border-foreground p-4 text-left transition-colors hover:border-primary/40 hover:bg-primary/5" onClick={() => selectedGuestMeeting && navigate(`/spaces/${selectedGuestMeeting.spaceId}/meetings/${selectedGuestMeeting.id}/report`)} type="button">
                    <BookOpen className="h-5 w-5 text-primary" />
                    <span><span className="block text-sm font-semibold text-foreground">Meeting report</span><span className="mt-1 block text-xs text-muted-foreground">Open the report when access is available.</span></span>
                  </button>
                </div>
              </div>
              <aside className="rounded-lg border border-border bg-card p-5">
                <div className="flex items-center justify-between">
                  <div><h3 className="text-sm font-semibold text-foreground">Invited meetings</h3><p className="mt-1 text-xs text-muted-foreground">Only meetings shared with you are shown.</p></div>
                  <span className="text-xs text-muted-foreground">{guestMeetings.length}</span>
                </div>
                <div className="mt-4 space-y-2">
                  {guestMeetings.map((meeting) => (
                    <button className={`flex w-full items-center justify-between rounded-md border px-3 py-2.5 text-left transition-colors ${selectedGuestMeeting?.id === meeting.id ? "border-primary/50 bg-primary/5" : "border-foreground hover:bg-muted/50"}`} key={meeting.id} onClick={() => setSearchParams({ guest: "1", meetingId: meeting.id })} type="button">
                      <span className="min-w-0"><span className="block truncate text-sm font-medium text-foreground">{meeting.title}</span><span className="mt-1 block text-xs text-muted-foreground">{new Date(meeting.scheduledAt).toLocaleString()}</span></span>
                      <span className="ml-3 shrink-0 text-[11px] font-medium text-muted-foreground">{meeting.status}</span>
                    </button>
                  ))}
                </div>
              </aside>
            </section>
          ) : (
          <EmptyState
            action={(
              <button
                className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors inline-flex items-center gap-2"
                onClick={() => setCreateOpen(true)}
                type="button"
              >
                <Plus className="w-4 h-4" /> Create your first space
              </button>
            )}
            desc="Create a project space to start meetings, reports, tasks, and knowledge."
            icon={<Building className="w-5 h-5" />}
            title="No spaces yet"
          />
          )
        ) : null}

        {!loading && !error && spaces.length > 0 ? (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {spaces.map((space) => (
              <div
                key={space.id}
                onClick={() => navigate(`/spaces/${space.id}`)}
                className="bg-card border border-border rounded-lg p-5 hover:border-foreground/30 hover:shadow-sm transition-all cursor-pointer group"
              >
                <div className="flex items-start justify-between mb-4">
                  <ProfileAvatar
                    alt={space.name}
                    className="h-10 w-10 rounded-md text-sm font-bold shrink-0"
                    initials={space.name.charAt(0).toUpperCase() || "P"}
                    pictureUrl={space.imageUrl}
                  />
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-muted text-muted-foreground uppercase tracking-wider">
                    {space.role}
                  </span>
                </div>
                <h3 className="font-semibold text-foreground mb-1">{space.name}</h3>
                <div className="flex gap-4 text-xs text-muted-foreground mt-4 pt-4 border-t border-border">
                  <span className="flex items-center gap-1"><Users className="w-3 h-3" /> {space.members ?? "—"}</span>
                  <span className="flex items-center gap-1"><Video className="w-3 h-3" /> {space.meetings}</span>
                  <span className="flex items-center gap-1"><CheckSquare className="w-3 h-3" /> {space.tasks ?? "—"}</span>
                </div>
              </div>
            ))}
          </div>
        ) : null}
      </div>

      {createOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm"
          onClick={(event) => {
            if (event.target === event.currentTarget && !createPending) {
              setCreateOpen(false);
            }
          }}
        >
          <div aria-labelledby="create-space-dialog-title" className="w-full max-w-md bg-card rounded-xl border border-border shadow-2xl" data-testid="create-space-dialog" role="dialog">
            <div className="px-6 py-5 border-b border-border flex items-start justify-between gap-4">
              <div>
                <h2 className="font-semibold text-foreground" id="create-space-dialog-title">New Space</h2>
                <p className="text-xs text-muted-foreground mt-0.5">Create a new collaboration space.</p>
              </div>
              <button
                className="w-7 h-7 flex items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors shrink-0"
                disabled={createPending}
                onClick={() => setCreateOpen(false)}
                type="button"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <form className="px-6 py-5 space-y-4" onSubmit={handleCreateSpace}>
              <div>
                <div id="create-space-name-label" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">Project Name</div>
                <input
                  aria-labelledby="create-space-name-label"
                  className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                  data-testid="create-space-name"
                  maxLength={120}
                  onChange={(event) => setCreateName(event.target.value)}
                  placeholder="e.g. Q3 Launch"
                  value={createName}
                />
              </div>
              <div>
                <div id="create-space-description-label" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">Description</div>
                <textarea
                  aria-labelledby="create-space-description-label"
                  className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 resize-none"
                  data-testid="create-space-description"
                  maxLength={500}
                  onChange={(event) => setCreateDescription(event.target.value)}
                  placeholder="What is this project about?"
                  rows={3}
                  value={createDescription}
                />
              </div>
              {createError ? <p className="text-xs text-red-600" role="alert">{createError}</p> : null}
              <div className="pt-2 flex items-center justify-end gap-2">
                <button
                  className="px-4 py-2 rounded-md border border-border text-sm text-muted-foreground hover:bg-muted transition-colors"
                  disabled={createPending}
                  onClick={() => setCreateOpen(false)}
                  type="button"
                >
                  Cancel
                </button>
                <button
                  className="px-4 py-2 rounded-md text-sm font-semibold bg-foreground text-background hover:bg-foreground/90 transition-colors"
                  data-testid="create-space-submit"
                  disabled={createPending}
                  type="submit"
                >
                  {createPending ? "Creating..." : "Create Space"}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </div>
  );
};

// 3. Project Home (/spaces/:spaceId)
const ProjectHome = () => {
  const navigate = useNavigate();
  const { locale } = useAppPreferences();
  const { spaceId = "" } = useParams();
  const { session } = useAuthState();
  const { spaceDetail, spaceLoading, spaceError, reloadSpace } = useOutletContext<ShellOutletContext>();
  const [members, setMembers] = useState<SpaceMembersResponse["members"]>([]);
  const [_knowledgeItems, setKnowledgeItems] = useState<ProjectKnowledgeItem[]>([]);
  const [instantMeetingPending, setInstantMeetingPending] = useState(false);
  const [instantMeetingError, setInstantMeetingError] = useState<string | null>(null);
  const [completingTaskId, setCompletingTaskId] = useState<string | null>(null);
  const [taskCompletionError, setTaskCompletionError] = useState<string | null>(null);
  const [taskPage, setTaskPage] = useState(0);
  const [aiUsage, setAiUsage] = useState<SpaceAiUsageResponse | null>(null);
  const korean = locale === "ko";

  useEffect(() => {
    let active = true;

    if (!session || !spaceId || !spaceDetail) {
      setMembers([]);
      setKnowledgeItems([]);
      setAiUsage(null);
      return () => {
        active = false;
      };
    }

    void Promise.allSettled([
      fetchSpaceMembers(session, spaceId),
      fetchProjectKnowledge(session, spaceId),
      fetchSpaceAiUsage(session, spaceId, "month")
    ]).then((results) => {
      if (!active) {
        return;
      }

      const [membersResult, knowledgeResult, aiUsageResult] = results;
      setMembers(membersResult.status === "fulfilled" ? membersResult.value.members : []);
      setKnowledgeItems(knowledgeResult.status === "fulfilled" ? knowledgeResult.value.items : []);
      // 사용량 조회가 실패해도 개요 화면 전체를 깨뜨리지 않는다. 카드만 숨긴다.
      setAiUsage(aiUsageResult.status === "fulfilled" ? aiUsageResult.value : null);
    });

    return () => {
      active = false;
    };
  }, [session, spaceId, spaceDetail]);

  if (spaceLoading) {
    return <LoadingState label="Loading project..." />;
  }

  if (spaceError instanceof ApiRequestError && spaceError.status === 403) {
    return <PermissionDenied type="project" />;
  }

  if (spaceError instanceof ApiRequestError && spaceError.status === 404) {
    return (
      <EmptyState
        action={(
          <button
            className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
            onClick={() => navigate("/spaces")}
            type="button"
          >
            Back to workspaces
          </button>
        )}
        desc="This project may have been removed or you may no longer have access."
        icon={<Building className="w-5 h-5" />}
        title="Project not found"
      />
    );
  }

  if (spaceError) {
    return (
      <ErrorState
        desc={spaceError.message}
        onRetry={() => {
          void reloadSpace();
        }}
        title="Couldn't load this project"
      />
    );
  }

  if (!spaceDetail) {
    return <LoadingState label="Loading project..." />;
  }

  const nextMeeting = spaceDetail.upcomingMeetings
    .slice()
    .sort((left, right) => new Date(left.scheduledAt).getTime() - new Date(right.scheduledAt).getTime())[0] ?? null;
  const myTasks = spaceDetail.actionItems
    .filter((task) => task.assigneeId === session?.user.id)
    .sort((left, right) => {
      const doneDifference = Number(left.status === "DONE") - Number(right.status === "DONE");
      if (doneDifference !== 0) return doneDifference;
      if (!left.dueDate && !right.dueDate) return 0;
      if (!left.dueDate) return 1;
      if (!right.dueDate) return -1;
      return new Date(left.dueDate).getTime() - new Date(right.dueDate).getTime();
    });
  const completedTasks = myTasks.filter((task) => task.status === "DONE").length;
  const visibleTasks = myTasks.slice(taskPage * 5, taskPage * 5 + 5);
  const confirmedReportsCount = spaceDetail.recentReports.filter((report) => report.status === "CONFIRMED").length;
  const confirmedReportsProgressPercent = Math.min(100, confirmedReportsCount * 20);
  const meetingProgressPercent = Math.min(100, spaceDetail.upcomingMeetings.length * 20);
  const canManageMeetings = spaceDetail.role === "OWNER" || spaceDetail.role === "ADMIN";

  function roleLabel(role: string) {
    if (role === "OWNER") {
      return "OWNER";
    }
    if (role === "ADMIN") {
      return "ADMIN";
    }
    return "MEMBER";
  }

  function memberRoleLabel(role: string) {
    if (role === "OWNER") {
      return "Owner";
    }
    if (role === "ADMIN") {
      return "Admin";
    }
    return "Member";
  }

  function dateLabel(value: string) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleDateString("en-US", { month: "short", day: "numeric" });
  }

  function dateTimeLabel(value: string) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString("en-US", {
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit"
    });
  }

  function reportStatusLabel(status: string) {
    if (status === "CONFIRMED") {
      return "Confirmed";
    }
    if (status === "DRAFT") {
      return "Draft";
    }
    return status;
  }

  async function handleStartInstantMeeting() {
    if (!session || !spaceId || instantMeetingPending || !canManageMeetings) {
      return;
    }
    setInstantMeetingPending(true);
    setInstantMeetingError(null);
    try {
      const created = await createInstantMeeting(session, spaceId);
      navigate(`meetings/${created.id}/live/prejoin`);
    } catch (cause) {
      setInstantMeetingError(cause instanceof Error ? cause.message : "Couldn't start a meeting.");
    } finally {
      setInstantMeetingPending(false);
    }
  }

  async function handleCompleteTask(taskId: string) {
    if (!session || !spaceId || completingTaskId) {
      return;
    }
    setCompletingTaskId(taskId);
    setTaskCompletionError(null);
    try {
      await updateTask(session, spaceId, taskId, { status: "DONE" });
      await reloadSpace();
    } catch (cause) {
      setTaskCompletionError(cause instanceof Error ? cause.message : "Couldn't complete this task.");
    } finally {
      setCompletingTaskId(null);
    }
  }

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold truncate">{korean ? "프로젝트 개요" : "Project Overview"}</h1>
        <div className="flex items-center gap-2 shrink-0">
          <span className="px-2 py-1 rounded bg-muted text-xs font-medium text-muted-foreground">Space Role: {roleLabel(spaceDetail.role)}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Main Column */}
        <div className="lg:col-span-8 space-y-6 min-w-0">

          {/* Next Meeting Banner */}
          <div className="bg-card border border-border rounded-lg p-5 space-y-3">
            <div className="flex items-center justify-between gap-4">
            <div className="flex items-center gap-4 min-w-0 flex-1">
              <div className="w-12 h-12 bg-[color:var(--app-accent-soft)] text-[color:var(--app-accent)] rounded flex items-center justify-center shrink-0">
                <Video className="w-6 h-6" />
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-xs font-semibold text-[color:var(--app-accent)] uppercase tracking-wider shrink-0">{korean ? "다음 회의" : "Up Next"}</span>
                  <span className="text-xs text-muted-foreground truncate">
                    {nextMeeting ? dateTimeLabel(nextMeeting.scheduledAt) : (korean ? "예정된 회의 없음" : "No upcoming meeting")}
                  </span>
                </div>
                <h3 className="font-semibold text-foreground truncate block w-full">{nextMeeting?.title ?? (korean ? "다음 회의 일정을 잡아보세요" : "Schedule your next meeting")}</h3>
              </div>
            </div>
              <div className="flex items-center gap-2 shrink-0">
                {canManageMeetings ? (
                  <button
                    onClick={() => {
                      void handleStartInstantMeeting();
                    }}
                    className="inline-flex items-center gap-2 bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors disabled:opacity-60"
                    disabled={instantMeetingPending}
                    type="button"
                  >
                    <Play className="w-4 h-4" />
                    {instantMeetingPending ? (korean ? "시작 중..." : "Starting...") : (korean ? "회의 시작" : "Start meeting")}
                  </button>
                ) : null}
                <button
                  onClick={() => navigate(nextMeeting ? `meetings/${nextMeeting.id}` : "meetings")}
                  className="border border-border bg-background text-foreground px-4 py-2 rounded-md text-sm font-medium hover:bg-muted transition-colors shrink-0"
                  type="button"
                >
                  {nextMeeting ? (korean ? "열기" : "Open") : (korean ? "회의 보기" : "View meetings")}
                </button>
              </div>
            </div>
            {instantMeetingError ? (
              <p className="text-xs text-red-600">{instantMeetingError}</p>
            ) : null}
          </div>

          {/* Open Tasks */}
          <div className="bg-card border border-border rounded-lg overflow-hidden min-w-0">
            <div className="px-5 py-4 border-b border-border flex items-center justify-between">
              <h3 className="font-semibold">{korean ? "내 태스크" : "My Tasks"}</h3>
              <button className="text-sm text-muted-foreground hover:text-foreground shrink-0" onClick={() => navigate("tasks")} type="button">{korean ? "전체 보기" : "View all"}</button>
            </div>
            <div className="divide-y divide-border">
              {myTasks.length > 0 ? visibleTasks.map((task) => (
                <div key={task.id} className="px-5 py-3 flex items-center justify-between hover:bg-muted/30 transition-colors cursor-pointer min-w-0 gap-4" onClick={() => navigate("tasks")}>
                  <div className="flex items-center gap-3 min-w-0 flex-1">
                    <button
                      aria-label={task.status === "DONE" ? `Completed ${task.title}` : `Complete ${task.title}`}
                      className={`flex h-4 w-4 items-center justify-center rounded border p-0 leading-none shrink-0 focus:outline-none focus:ring-2 focus:ring-primary/50 disabled:cursor-wait ${task.status === "DONE" ? "border-emerald-500 bg-emerald-500" : "border-muted-foreground/40 hover:border-foreground"}`}
                      disabled={task.status === "DONE" || completingTaskId === task.id}
                      onClick={(event) => {
                        event.stopPropagation();
                        void handleCompleteTask(task.id);
                      }}
                      type="button"
                    >
                      {task.status === "DONE" ? <span className="-translate-y-px text-[10px] leading-none text-white">✓</span> : <span className="sr-only">Complete</span>}
                    </button>
                    <span className={`text-sm font-medium truncate block w-full ${task.status === "DONE" ? "text-muted-foreground line-through" : ""}`}>{task.title}</span>
                  </div>
                  <div className="flex items-center gap-3 text-xs shrink-0">
                    <span className="text-muted-foreground">{task.dueDate ? dateLabel(task.dueDate) : "No due date"}</span>
                  </div>
                </div>
              )) : (
                <EmptyState
                  desc={korean ? "내게 할당된 태스크가 여기에 표시됩니다." : "You will see tasks assigned to you here."}
                  icon={<CheckSquare className="w-5 h-5" />}
                  title={korean ? "할당된 태스크가 없습니다" : "No assigned tasks"}
                />
              )}
            </div>
            {myTasks.length > 5 ? (
              <div className="flex items-center justify-between border-t border-border px-5 py-3 text-sm">
                <button className="text-muted-foreground hover:text-foreground disabled:opacity-40" disabled={taskPage === 0} onClick={() => setTaskPage((page) => Math.max(0, page - 1))} type="button">{korean ? "이전" : "Previous"}</button>
                <span className="text-xs text-muted-foreground">{taskPage + 1} / {Math.ceil(Math.min(myTasks.length, 10) / 5)}</span>
                <button className="text-muted-foreground hover:text-foreground disabled:opacity-40" disabled={taskPage >= Math.ceil(Math.min(myTasks.length, 10) / 5) - 1} onClick={() => setTaskPage((page) => page + 1)} type="button">{korean ? "다음" : "Next"}</button>
              </div>
            ) : null}
            {taskCompletionError ? <p className="px-5 py-3 text-xs text-red-600 border-t border-border" role="alert">{taskCompletionError}</p> : null}
          </div>

          {/* Recent Transcripts/Reports */}
          <div className="bg-card border border-border rounded-lg overflow-hidden min-w-0">
            <div className="px-5 py-4 border-b border-border flex items-center justify-between">
              <h3 className="font-semibold">{korean ? "최근 확정 회의록" : "Recent Confirmed Reports"}</h3>
            </div>
            <div className="divide-y divide-border">
              {spaceDetail.recentReports.length > 0 ? spaceDetail.recentReports.slice(0, 5).map((report) => (
                <div key={report.id} className="px-5 py-3 flex items-center justify-between hover:bg-muted/30 transition-colors min-w-0 gap-4">
                  <div className="flex items-center gap-3 min-w-0 flex-1">
                    <FileText className="w-4 h-4 text-muted-foreground shrink-0" />
                    <span className="text-sm font-medium truncate block w-full">{report.title}</span>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="px-2 py-0.5 rounded-full bg-emerald-50 text-emerald-700 text-[10px] font-medium border border-emerald-200">
                      {reportStatusLabel(report.status)}
                    </span>
                    <span className="text-xs text-muted-foreground w-20 text-right">{dateLabel(report.createdAt)}</span>
                  </div>
                </div>
              )) : (
                <EmptyState
                  desc={korean ? "회의록이 확정되면 여기에 표시됩니다." : "Confirmed reports will appear here after a meeting report is finalized."}
                  icon={<FileText className="w-5 h-5" />}
                  title={korean ? "아직 확정된 회의록이 없습니다" : "No reports yet"}
                />
              )}
            </div>
          </div>

          {/* AI Usage (T439.1). quota는 표시 전용이며 초과해도 AI 호출을 막지 않는다. */}
          {aiUsage ? (
            <div className="bg-card border border-border rounded-lg overflow-hidden min-w-0">
              <div className="px-5 py-4 border-b border-border flex items-center justify-between">
                <h3 className="font-semibold">{korean ? "이번 달 AI 사용량" : "AI Usage This Month"}</h3>
                <span className="text-xs text-muted-foreground">
                  {korean ? `요청 ${aiUsage.totalRequests}건` : `${aiUsage.totalRequests} requests`}
                </span>
              </div>
              <div className="px-5 py-4 space-y-3">
                <div className="flex items-baseline justify-between gap-4">
                  <span className="text-2xl font-semibold tabular-nums">
                    {(aiUsage.totalInputTokens + aiUsage.totalOutputTokens).toLocaleString()}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {korean ? "총 토큰" : "total tokens"}
                    {aiUsage.limit !== null ? ` / ${aiUsage.limit.toLocaleString()}` : ""}
                  </span>
                </div>
                {aiUsage.usagePercent !== null ? (
                  <div className="space-y-1">
                    <div className="h-2 w-full rounded-full bg-muted overflow-hidden">
                      <div
                        className={`h-full rounded-full ${aiUsage.usagePercent >= 100 ? "bg-amber-500" : "bg-primary"}`}
                        style={{ width: `${Math.min(100, aiUsage.usagePercent)}%` }}
                      />
                    </div>
                    <p className="text-xs text-muted-foreground">
                      {korean ? `한도의 ${aiUsage.usagePercent}%` : `${aiUsage.usagePercent}% of quota`}
                      {aiUsage.usagePercent >= 100
                        ? korean
                          ? " · 한도를 넘었지만 AI 사용은 계속됩니다"
                          : " · over quota, AI remains available"
                        : ""}
                    </p>
                  </div>
                ) : null}
                {aiUsage.features.filter((feature) => feature.requests > 0).length > 0 ? (
                  <ul className="space-y-1 pt-1">
                    {aiUsage.features.filter((feature) => feature.requests > 0).map((feature) => (
                      <li className="flex items-center justify-between text-xs" key={feature.feature}>
                        <span className="text-muted-foreground truncate">{feature.feature}</span>
                        <span className="tabular-nums shrink-0">
                          {(feature.inputTokens + feature.outputTokens).toLocaleString()}
                          <span className="text-muted-foreground"> · {feature.requests}</span>
                        </span>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="text-xs text-muted-foreground">
                    {korean ? "아직 AI 사용 기록이 없습니다." : "No AI usage recorded yet."}
                  </p>
                )}
              </div>
            </div>
          ) : null}
        </div>

        {/* Side Column */}
        <div className="lg:col-span-4 space-y-6 min-w-0">
          {/* Project Stats */}
          <div className="bg-card border border-border rounded-lg p-5">
            <h3 className="font-semibold mb-4 text-sm">{korean ? "프로젝트 현황" : "Project Activity"}</h3>
            <div className="space-y-4">
              <div>
                <div className="flex justify-between text-xs mb-1.5">
                  <span className="text-muted-foreground">{korean ? "태스크 완료" : "Task Completion"}</span>
                  <span className="font-medium">{completedTasks} / {myTasks.length}</span>
                </div>
                <div className="h-1.5 w-full bg-muted rounded-full overflow-hidden">
                  <div className="h-full bg-foreground rounded-full" style={{ width: `${myTasks.length > 0 ? Math.round((completedTasks / myTasks.length) * 100) : 0}%` }}></div>
                </div>
              </div>
              <div>
                <div className="flex justify-between text-xs mb-1.5">
                  <span className="text-muted-foreground">{korean ? "확정 회의록" : "Confirmed Reports"}</span>
                  <span className="font-medium">{confirmedReportsCount}</span>
                </div>
                <div className="h-1.5 w-full bg-muted rounded-full overflow-hidden">
                  <div className="h-full bg-[color:var(--app-accent)] rounded-full" style={{ width: `${confirmedReportsProgressPercent}%` }}></div>
                </div>
              </div>
              <div>
                <div className="flex justify-between text-xs mb-1.5">
                  <span className="text-muted-foreground">{korean ? "예정된 회의" : "Upcoming Meetings"}</span>
                  <span className="font-medium">{spaceDetail.upcomingMeetings.length}</span>
                </div>
                <div className="h-1.5 w-full bg-muted rounded-full overflow-hidden">
                  <div className="h-full bg-emerald-500 rounded-full" style={{ width: `${meetingProgressPercent}%` }}></div>
                </div>
              </div>
            </div>
          </div>

          {/* Members */}
          <div className="bg-card border border-border rounded-lg p-5">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-sm">{korean ? "멤버" : "Members"}</h3>
              <span className="text-xs text-muted-foreground">{members.length} people</span>
            </div>
            <div className="space-y-2.5">
              {members.length > 0 ? members.slice(0, 5).map((member) => {
                const name = member.displayName?.trim() || member.email || "Unknown";
                const initials = participantInitials(name);
                return (
                  <div key={member.id} className="flex items-center gap-3">
                    <ProfileAvatar alt={name} className="w-7 h-7 rounded-full shrink-0 text-xs font-medium" initials={initials} pictureUrl={member.pictureUrl} />
                    <div className="flex-1 min-w-0">
                      <span className="text-sm font-medium truncate block">{name}</span>
                    </div>
                    <span className="text-[11px] text-muted-foreground">{memberRoleLabel(member.role)}</span>
                  </div>
                )}) : (
                <EmptyState
                  desc="Project members will appear here when access is granted."
                  icon={<Users className="w-5 h-5" />}
                  title="No members available"
                />
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

// 4. Meeting List (/spaces/:spaceId/meetings)
const MeetingList = () => {
  const { session } = useAuthState();
  const { locale } = useAppPreferences();
  const { spaceDetail } = useOutletContext<ShellOutletContext>();
  const { spaceId = "" } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [meetings, setMeetings] = useState<Array<MeetingSummary & { host: string; participantCount: number }>>([]);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | MeetingSummary["status"]>("ALL");
  const [createOpen, setCreateOpen] = useState(false);
  const [createTitle, setCreateTitle] = useState("");
  const [createDescription, setCreateDescription] = useState("");
  const [createStartAt, setCreateStartAt] = useState("");
  const [createEndAt, setCreateEndAt] = useState("");
  const [createPending, setCreatePending] = useState(false);
  const [createError, setCreateError] = useState("");
  const [instantPending, setInstantPending] = useState(false);
  const [instantError, setInstantError] = useState("");
  const [meetingPage, setMeetingPage] = useState(0);
  const [openMeetingMenu, setOpenMeetingMenu] = useState<string | null>(null);
  const [deletePendingMeetingId, setDeletePendingMeetingId] = useState<string | null>(null);
  const [editingMeeting, setEditingMeeting] = useState<MeetingSummary | null>(null);
  const [editTitle, setEditTitle] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [editStartAt, setEditStartAt] = useState("");
  const [editEndAt, setEditEndAt] = useState("");
  const [editPending, setEditPending] = useState(false);
  const korean = locale === "ko";

  useEffect(() => {
    setMeetingPage(0);
  }, [searchQuery, statusFilter]);

  const loadMeetings = React.useCallback(async () => {
    if (!session || !spaceId) {
      setMeetings([]);
      setLoading(false);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const response = await fetchMeetings(session, spaceId);
      const hydrated = await Promise.all(
        response.meetings.map(async (meeting) => {
          try {
            const detail = await fetchMeetingDetail(session, meeting.id);
            const host = detail.participants.find((participant) => participant.role === "HOST")?.displayName ?? "—";
            return {
              ...meeting,
              host,
              participantCount: detail.participants.length
            };
          } catch {
            return {
              ...meeting,
              host: "—",
              participantCount: 0
            };
          }
        })
      );
      setMeetings(hydrated);
    } catch (cause) {
      setMeetings([]);
      setError(cause instanceof Error ? cause : new Error("회의를 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [session, spaceId]);

  useEffect(() => {
    void loadMeetings();
  }, [loadMeetings]);

  function toOffsetDateTime(value: string) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return "";
    }
    const pad = (segment: number) => String(segment).padStart(2, "0");
    const offsetMinutes = -date.getTimezoneOffset();
    const sign = offsetMinutes >= 0 ? "+" : "-";
    const absOffset = Math.abs(offsetMinutes);
    const offsetHours = Math.floor(absOffset / 60);
    const offsetRemainder = absOffset % 60;
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00${sign}${pad(offsetHours)}:${pad(offsetRemainder)}`;
  }

  function resetCreateForm() {
    setCreateTitle("");
    setCreateDescription("");
    setCreateStartAt("");
    setCreateEndAt("");
    setCreateError("");
  }

  function openCreateModal() {
    resetCreateForm();
    setCreateOpen(true);
  }

  async function handleStartInstantMeeting() {
    if (!session || !spaceId || instantPending) {
      return;
    }
    setInstantPending(true);
    setInstantError("");
    try {
      const created = await createInstantMeeting(session, spaceId);
      await loadMeetings();
      navigate(`${created.id}/live/prejoin`);
    } catch (cause) {
      setInstantError(cause instanceof Error ? cause.message : "회의를 시작하지 못했습니다.");
    } finally {
      setInstantPending(false);
    }
  }

  async function handleCreateMeeting(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !spaceId || createPending) {
      return;
    }
    if (!createTitle.trim() || !createStartAt || !createEndAt) {
      setCreateError("회의 제목과 시작/종료 시간을 입력해 주세요.");
      return;
    }
    const startAt = new Date(createStartAt);
    const endAt = new Date(createEndAt);
    if (Number.isNaN(startAt.getTime()) || Number.isNaN(endAt.getTime())) {
      setCreateError("유효한 일시를 입력해 주세요.");
      return;
    }
    if (endAt <= startAt) {
      setCreateError("종료 시간은 시작 시간보다 뒤여야 합니다.");
      return;
    }

    setCreatePending(true);
    setCreateError("");
    try {
      const created = await createMeeting(session, spaceId, {
        title: createTitle.trim(),
        description: createDescription.trim() || undefined,
        scheduledAt: toOffsetDateTime(createStartAt),
        scheduledEndAt: toOffsetDateTime(createEndAt)
      });
      setCreateOpen(false);
      resetCreateForm();
      await loadMeetings();
      navigate(created.id);
    } catch (cause) {
      setCreateError(cause instanceof Error ? cause.message : "회의를 생성하지 못했습니다.");
    } finally {
      setCreatePending(false);
    }
  }

  function canManageMeeting(meeting: MeetingSummary) {
    return spaceDetail?.role === "OWNER" || spaceDetail?.role === "ADMIN" || meeting.myRole === "HOST";
  }

  async function handleDeleteMeeting(meeting: MeetingSummary) {
    if (!session || !canManageMeeting(meeting) || deletePendingMeetingId) return;
    if (!window.confirm(`'${meeting.title}' 회의를 삭제하시겠습니까?`)) return;
    setDeletePendingMeetingId(meeting.id);
    try {
      await deleteMeeting(session, meeting.id);
      setOpenMeetingMenu(null);
      await loadMeetings();
    } catch (cause) {
      setError(cause instanceof Error ? cause : new Error("회의를 삭제하지 못했습니다."));
    } finally {
      setDeletePendingMeetingId(null);
    }
  }

  async function handleCancelMeeting(meeting: MeetingSummary) {
    if (!session || !canManageMeeting(meeting) || meeting.status !== "SCHEDULED" || deletePendingMeetingId) return;
    if (!window.confirm(`'${meeting.title}' 회의를 취소하시겠습니까?`)) return;
    setDeletePendingMeetingId(meeting.id);
    try {
      await updateMeeting(session, meeting.id, { status: "CANCELED" });
      setOpenMeetingMenu(null);
      await loadMeetings();
    } catch (cause) {
      setError(cause instanceof Error ? cause : new Error("회의를 취소하지 못했습니다."));
    } finally {
      setDeletePendingMeetingId(null);
    }
  }

  function openEditMeeting(meeting: MeetingSummary) {
    setEditingMeeting(meeting);
    setEditTitle(meeting.title);
    setEditDescription(meeting.description ?? "");
    setEditStartAt(meeting.scheduledAt.slice(0, 16));
    setEditEndAt(meeting.scheduledEndAt.slice(0, 16));
    setOpenMeetingMenu(null);
  }

  async function handleEditMeeting(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !editingMeeting || editPending || !editTitle.trim() || !editStartAt || !editEndAt) return;
    const startAt = new Date(editStartAt);
    const endAt = new Date(editEndAt);
    if (Number.isNaN(startAt.getTime()) || Number.isNaN(endAt.getTime()) || endAt <= startAt) {
      setError(new Error("유효한 제목과 시작·종료 시간을 입력해 주세요."));
      return;
    }
    setEditPending(true);
    try {
      await updateMeeting(session, editingMeeting.id, {
        title: editTitle.trim(),
        description: editDescription.trim() || undefined,
        scheduledAt: startAt.toISOString(),
        scheduledEndAt: endAt.toISOString()
      });
      setEditingMeeting(null);
      await loadMeetings();
    } catch (cause) {
      setError(cause instanceof Error ? cause : new Error("회의를 수정하지 못했습니다."));
    } finally {
      setEditPending(false);
    }
  }

  function meetingStatusLabel(status: string) {
    if (status === "SCHEDULED") {
      return "Upcoming";
    }
    if (status === "IN_PROGRESS") {
      return "In Progress";
    }
    if (status === "ENDED") {
      return "Confirmed";
    }
    if (status === "CANCELED") {
      return "Canceled";
    }
    return status;
  }

  function meetingStatusStyle(status: string) {
    if (status === "SCHEDULED") {
      return "bg-[var(--app-accent-soft)] text-[var(--app-accent-text)] border-[color:color-mix(in_srgb,var(--app-accent)_24%,white)]";
    }
    if (status === "IN_PROGRESS") {
      return "bg-[var(--app-success-soft)] text-[var(--app-success)] border-[color:color-mix(in_srgb,var(--app-success)_28%,white)]";
    }
    if (status === "ENDED") {
      return "bg-[var(--app-ai-soft)] text-[var(--app-ai)] border-[color:color-mix(in_srgb,var(--app-ai)_28%,white)]";
    }
    if (status === "CANCELED") {
      return "bg-[var(--app-danger-soft)] text-[var(--app-danger)] border-[color:color-mix(in_srgb,var(--app-danger)_24%,white)]";
    }
    return "bg-[var(--app-warning-soft)] text-[var(--app-warning)] border-[color:color-mix(in_srgb,var(--app-warning)_26%,white)]";
  }

  function meetingDateTimeLabel(meeting: MeetingSummary) {
    const scheduledAt = new Date(meeting.scheduledAt);
    const scheduledEndAt = new Date(meeting.scheduledEndAt);
    if (Number.isNaN(scheduledAt.getTime()) || Number.isNaN(scheduledEndAt.getTime())) {
      return "Schedule unavailable";
    }
    return `${scheduledAt.toLocaleDateString("en-US", { month: "short", day: "numeric" })}, ${scheduledAt.toLocaleTimeString("en-US", {
      hour: "numeric",
      minute: "2-digit"
    })} - ${scheduledEndAt.toLocaleTimeString("en-US", {
      hour: "numeric",
      minute: "2-digit"
    })}`;
  }

  const meetingStatusOrder: Record<string, number> = { IN_PROGRESS: 0, SCHEDULED: 1, ENDED: 2, CANCELED: 3 };
  const orderedMeetings = [...meetings].sort((left, right) => {
    const statusDifference = (meetingStatusOrder[left.status] ?? 99) - (meetingStatusOrder[right.status] ?? 99);
    if (statusDifference !== 0) return statusDifference;
    const leftTime = new Date(left.status === "SCHEDULED" || left.status === "IN_PROGRESS" ? left.scheduledAt : left.scheduledEndAt).getTime();
    const rightTime = new Date(right.status === "SCHEDULED" || right.status === "IN_PROGRESS" ? right.scheduledAt : right.scheduledEndAt).getTime();
    return left.status === "SCHEDULED" || left.status === "IN_PROGRESS" ? leftTime - rightTime : rightTime - leftTime;
  });
  const filteredMeetings = orderedMeetings.filter((meeting) => {
    const matchesStatus = statusFilter === "ALL" || meeting.status === statusFilter;
    const normalizedQuery = searchQuery.trim().toLowerCase();
    const matchesQuery = !normalizedQuery
      || meeting.title.toLowerCase().includes(normalizedQuery)
      || meeting.host.toLowerCase().includes(normalizedQuery)
      || meetingStatusLabel(meeting.status).toLowerCase().includes(normalizedQuery);
    return matchesStatus && matchesQuery;
  });
  const hasMeetings = meetings.length > 0;
  const meetingPageCount = Math.ceil(filteredMeetings.length / 7);
  const visibleMeetings = filteredMeetings.slice(meetingPage * 7, meetingPage * 7 + 7);

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">{korean ? "회의" : "Meetings"}</h1>
          <p className="text-sm text-muted-foreground">{korean ? "예정, 진행 중, 종료된 회의를 관리합니다." : "Manage upcoming, live, and past meetings."}</p>
        </div>
        {hasMeetings ? (
          <div className="flex items-center gap-3">
            <button
              className="px-4 py-2 rounded-md bg-foreground text-background text-sm font-medium hover:bg-foreground/90 flex items-center gap-2 transition-colors disabled:opacity-60"
              onClick={() => {
                void handleStartInstantMeeting();
              }}
              disabled={instantPending}
              type="button"
            >
              <Play className="w-4 h-4" /> {instantPending ? (korean ? "시작 중..." : "Starting...") : (korean ? "회의 시작" : "Start Meeting")}
            </button>
            <button
              className={`px-4 py-2 rounded-md bg-card border text-sm font-medium flex items-center gap-2 transition-colors ${filtersOpen ? "border-foreground text-foreground" : "border-border hover:bg-muted"}`}
              onClick={() => setFiltersOpen((current) => !current)}
              type="button"
            >
              <Filter className="w-4 h-4" /> {filtersOpen ? (korean ? "필터 숨기기" : "Hide Filters") : (korean ? "필터" : "Filter")}
            </button>
            <button
              className="px-4 py-2 rounded-md bg-foreground text-background text-sm font-medium hover:bg-foreground/90 flex items-center gap-2 transition-colors"
              onClick={openCreateModal}
              type="button"
            >
              <Plus className="w-4 h-4" /> {korean ? "회의 일정 만들기" : "Schedule Meeting"}
            </button>
          </div>
        ) : null}
      </div>

      {hasMeetings && filtersOpen ? (
        <div className="bg-card border border-border rounded-lg p-4 flex flex-col gap-4 md:flex-row md:items-end">
          <div className="flex-1 min-w-0">
            <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">{korean ? "검색" : "Search"}</label>
            <input
              className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder={korean ? "제목, 호스트 또는 상태로 검색" : "Search by title, host, or status"}
              type="search"
              value={searchQuery}
            />
          </div>
          <div className="w-full md:w-56">
            <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">{korean ? "상태" : "Status"}</label>
            <select
              className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
              onChange={(event) => setStatusFilter(event.target.value as typeof statusFilter)}
              value={statusFilter}
            >
              <option value="ALL">{korean ? "모든 상태" : "All statuses"}</option>
              <option value="SCHEDULED">{korean ? "예정" : "Upcoming"}</option>
              <option value="IN_PROGRESS">{korean ? "진행 중" : "In Progress"}</option>
              <option value="ENDED">{korean ? "종료됨" : "Confirmed"}</option>
              <option value="CANCELED">{korean ? "취소됨" : "Canceled"}</option>
            </select>
          </div>
          <button
            className="px-4 py-2 rounded-md border border-border text-sm font-medium hover:bg-muted transition-colors"
            onClick={() => {
              setSearchQuery("");
              setStatusFilter("ALL");
            }}
            type="button"
          >
            {korean ? "초기화" : "Reset"}
          </button>
        </div>
      ) : null}

      {loading ? <LoadingState label={korean ? "회의를 불러오는 중..." : "Loading meetings..."} /> : null}

      {!loading && error instanceof ApiRequestError && error.status === 403 ? <PermissionDenied type="project" /> : null}

      {!loading && error instanceof ApiRequestError && error.status === 404 ? (
        <EmptyState
          action={(
            <button
              className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
              onClick={() => navigate("/spaces")}
              type="button"
            >
              Back to workspaces
            </button>
          )}
          desc="The project was not found or you no longer have access."
          icon={<Video className="w-5 h-5" />}
          title="Meetings unavailable"
        />
      ) : null}

      {!loading && error && !(error instanceof ApiRequestError && (error.status === 403 || error.status === 404)) ? (
        <ErrorState
          desc={error.message}
          onRetry={() => {
            void loadMeetings();
          }}
          title="Couldn't load meetings"
        />
      ) : null}

      {!loading && !error && meetings.length === 0 ? (
        <EmptyState
          action={(
            <div className="flex items-center gap-2">
              <button
                className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors inline-flex items-center gap-2 disabled:opacity-60"
                onClick={() => {
                  void handleStartInstantMeeting();
                }}
                disabled={instantPending}
                type="button"
              >
                <Play className="w-4 h-4" /> {instantPending ? "Starting..." : "Start first meeting"}
              </button>
              <button
                className="px-4 py-2 rounded-md border border-border text-sm font-medium hover:bg-muted transition-colors inline-flex items-center gap-2"
                onClick={openCreateModal}
                type="button"
              >
                <Plus className="w-4 h-4" /> Schedule meeting
              </button>
            </div>
          )}
          desc="Schedule a meeting to start transcript, report, and task workflows."
          icon={<Video className="w-5 h-5" />}
          title="No meetings yet"
        />
      ) : null}

      {!loading && !error && instantError ? (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {instantError}
        </div>
      ) : null}

      {!loading && !error && meetings.length > 0 && filteredMeetings.length === 0 ? (
        <EmptyState
          action={(
            <button
              className="px-4 py-2 rounded-md border border-border text-sm font-medium hover:bg-muted transition-colors"
              onClick={() => {
                setSearchQuery("");
                setStatusFilter("ALL");
              }}
              type="button"
            >
              Clear filters
            </button>
          )}
          desc="No meetings match the current search or status filters."
          icon={<Filter className="w-5 h-5" />}
          title="No filtered meetings"
        />
      ) : null}

      {!loading && !error && filteredMeetings.length > 0 ? (
        <div className="bg-card border border-border rounded-lg overflow-visible">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase border-b border-border bg-muted/30">
              <tr>
                <th className="px-6 py-4 font-medium">Meeting Topic</th>
                <th className="px-6 py-4 font-medium">Status</th>
                <th className="px-6 py-4 font-medium">Date & Time</th>
                <th className="px-6 py-4 font-medium">Host</th>
                <th className="px-6 py-4 font-medium">Participants</th>
                <th className="px-6 py-4 font-medium text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {visibleMeetings.map((meeting) => (
                <tr
                  key={meeting.id}
                  onClick={() => navigate(meeting.id)}
                  className="hover:bg-muted/50 cursor-pointer transition-colors"
                >
                  <td className="px-6 py-4 font-medium text-foreground">{meeting.title}</td>
                  <td className="px-6 py-4">
                    <span className={`px-2 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider border ${meetingStatusStyle(meeting.status)}`}>
                      {meetingStatusLabel(meeting.status)}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-muted-foreground">{meetingDateTimeLabel(meeting)}</td>
                  <td className="px-6 py-4 text-muted-foreground">{meeting.host}</td>
                  <td className="px-6 py-4 text-muted-foreground flex items-center gap-1"><Users className="w-3.5 h-3.5" /> {meeting.participantCount}</td>
                  <td className="px-6 py-4 text-right">
                    {canManageMeeting(meeting) ? (
                      <div className="relative inline-block" onClick={(event) => event.stopPropagation()}>
                        <button aria-label={`${meeting.title} meeting actions`} className="rounded-md p-2 text-muted-foreground hover:bg-muted hover:text-foreground" onClick={() => setOpenMeetingMenu((current) => current === meeting.id ? null : meeting.id)} type="button">
                          <MoreVertical className="h-4 w-4" />
                        </button>
                        {openMeetingMenu === meeting.id ? (
                          <div className="absolute right-0 top-full z-50 mt-1 w-32 rounded-md border border-border bg-card p-1 text-left shadow-lg">
                            <button className="w-full rounded px-2 py-1.5 text-xs hover:bg-muted" onClick={() => openEditMeeting(meeting)} type="button">Edit</button>
                            {meeting.status === "SCHEDULED" ? (
                              <button
                                className="w-full rounded px-2 py-1.5 text-xs hover:bg-muted disabled:opacity-50"
                                disabled={deletePendingMeetingId === meeting.id}
                                onClick={() => void handleCancelMeeting(meeting)}
                                type="button"
                              >
                                {deletePendingMeetingId === meeting.id ? "Canceling..." : "Cancel meeting"}
                              </button>
                            ) : null}
                            <button className="w-full rounded px-2 py-1.5 text-xs text-red-600 hover:bg-red-50 disabled:opacity-50" disabled={deletePendingMeetingId === meeting.id} onClick={() => void handleDeleteMeeting(meeting)} type="button">{deletePendingMeetingId === meeting.id ? "Deleting..." : "Delete"}</button>
                          </div>
                        ) : null}
                      </div>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {meetingPageCount > 1 ? (
            <div className="flex items-center justify-between border-t border-border px-6 py-3 text-sm">
              <button className="text-muted-foreground hover:text-foreground disabled:opacity-40" disabled={meetingPage === 0} onClick={() => setMeetingPage((page) => Math.max(0, page - 1))} type="button">Previous</button>
              <span className="text-xs text-muted-foreground">{meetingPage + 1} / {meetingPageCount}</span>
              <button className="text-muted-foreground hover:text-foreground disabled:opacity-40" disabled={meetingPage >= meetingPageCount - 1} onClick={() => setMeetingPage((page) => page + 1)} type="button">Next</button>
            </div>
          ) : null}
        </div>
      ) : null}

      {createOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm"
          onClick={(event) => {
            if (event.target === event.currentTarget && !createPending) {
              setCreateOpen(false);
            }
          }}
        >
          <div className="w-full max-w-lg bg-card rounded-xl border border-border shadow-2xl">
            <div className="px-6 py-5 border-b border-border flex items-start justify-between gap-4">
              <div>
                <h2 className="font-semibold text-foreground">Schedule Meeting</h2>
                <p className="text-xs text-muted-foreground mt-0.5">Creates a real meeting in this project.</p>
              </div>
              <button
                className="w-7 h-7 flex items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors shrink-0"
                disabled={createPending}
                onClick={() => setCreateOpen(false)}
                type="button"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <form className="px-6 py-5 space-y-4" onSubmit={handleCreateMeeting}>
              <div>
                <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">Title</label>
                <input
                  autoFocus
                  className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                  maxLength={160}
                  onChange={(event) => setCreateTitle(event.target.value)}
                  placeholder="e.g. Weekly product sync"
                  value={createTitle}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">Description</label>
                <textarea
                  className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 resize-none"
                  maxLength={1000}
                  onChange={(event) => setCreateDescription(event.target.value)}
                  placeholder="Agenda or meeting purpose"
                  rows={3}
                  value={createDescription}
                />
              </div>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">Start</label>
                  <input
                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                    onChange={(event) => setCreateStartAt(event.target.value)}
                    type="datetime-local"
                    value={createStartAt}
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">End</label>
                  <input
                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                    onChange={(event) => setCreateEndAt(event.target.value)}
                    type="datetime-local"
                    value={createEndAt}
                  />
                </div>
              </div>
              {createError ? <p className="text-xs text-red-600" role="alert">{createError}</p> : null}
              <div className="pt-2 flex items-center justify-end gap-2">
                <button
                  className="px-4 py-2 rounded-md border border-border text-sm text-muted-foreground hover:bg-muted transition-colors"
                  disabled={createPending}
                  onClick={() => setCreateOpen(false)}
                  type="button"
                >
                  Cancel
                </button>
                <button
                  className="px-4 py-2 rounded-md text-sm font-semibold bg-foreground text-background hover:bg-foreground/90 transition-colors disabled:opacity-60"
                  disabled={createPending}
                  type="submit"
                >
                  {createPending ? "Creating..." : "Create Meeting"}
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {editingMeeting ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={(event) => { if (event.target === event.currentTarget && !editPending) setEditingMeeting(null); }}>
          <form className="w-full max-w-lg space-y-4 rounded-lg border border-border bg-card p-6 shadow-xl" onSubmit={(event) => void handleEditMeeting(event)}>
            <div className="flex items-start justify-between gap-4">
              <div><p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Meeting management</p><h2 className="text-lg font-semibold">Edit meeting</h2></div>
              <button aria-label="Close edit meeting" className="rounded-md p-1 text-muted-foreground hover:bg-muted" onClick={() => setEditingMeeting(null)} type="button"><X className="h-4 w-4" /></button>
            </div>
            <label className="block text-sm font-medium">Title<input className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2" onChange={(event) => setEditTitle(event.target.value)} value={editTitle} /></label>
            <label className="block text-sm font-medium">Description<textarea className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2" onChange={(event) => setEditDescription(event.target.value)} rows={3} value={editDescription} /></label>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <label className="block text-sm font-medium">Start<input className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2" onChange={(event) => setEditStartAt(event.target.value)} type="datetime-local" value={editStartAt} /></label>
              <label className="block text-sm font-medium">End<input className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2" onChange={(event) => setEditEndAt(event.target.value)} type="datetime-local" value={editEndAt} /></label>
            </div>
            <div className="flex justify-end gap-2"><button className="rounded-md border border-border px-4 py-2 text-sm" disabled={editPending} onClick={() => setEditingMeeting(null)} type="button">Cancel</button><button className="rounded-md bg-foreground px-4 py-2 text-sm font-semibold text-background disabled:opacity-60" disabled={editPending} type="submit">{editPending ? "Saving..." : "Save changes"}</button></div>
          </form>
        </div>
      ) : null}
    </div>
  );
};

// 5. Meeting Context Layout (/spaces/:spaceId/meetings/:meetingId/*)
const MeetingContextLayout = () => {
  const { spaceId, meetingId } = useParams();
  const navigate = useNavigate();
  const { session } = useAuthState();
  const { locale } = useAppPreferences();
  const shellContext = useOutletContext<ShellOutletContext>();
  const [meetingDetail, setMeetingDetail] = useState<MeetingDetailResponse | null>(null);
  const [meetingLoading, setMeetingLoading] = useState(true);
  const [meetingError, setMeetingError] = useState<Error | null>(null);
  const korean = locale === "ko";

  const loadMeeting = React.useCallback(async () => {
    if (!session || !meetingId) {
      setMeetingDetail(null);
      setMeetingLoading(false);
      setMeetingError(null);
      return;
    }

    setMeetingLoading(true);
    setMeetingError(null);
    try {
      const detail = await fetchMeetingDetail(session, meetingId);
      setMeetingDetail(detail);
    } catch (cause) {
      setMeetingDetail(null);
      setMeetingError(cause instanceof Error ? cause : new Error("회의를 불러오지 못했습니다."));
    } finally {
      setMeetingLoading(false);
    }
  }, [meetingId, session]);

  useEffect(() => {
    void loadMeeting();
  }, [loadMeeting]);

  useEffect(() => {
    if (!meetingId || meetingDetail?.id !== meetingId) {
      shellContext.setMeetingAiContext(null);
      return;
    }

    const hasMeetingAiPermission = meetingDetail.myRole !== null
      || shellContext.spaceDetail?.role === "OWNER"
      || shellContext.spaceDetail?.role === "ADMIN";
    // Meeting AI requires a meeting participant role or a Space ACL override.
    shellContext.setMeetingAiContext(hasMeetingAiPermission ? meetingId : null);
    return () => shellContext.setMeetingAiContext(null);
  }, [meetingDetail, meetingId, shellContext.setMeetingAiContext, shellContext.spaceDetail?.role]);

  function meetingStatusLabel(status: string) {
    if (status === "SCHEDULED") {
      return korean ? "예정" : "Upcoming";
    }
    if (status === "IN_PROGRESS") {
      return korean ? "진행 중" : "In Progress";
    }
    if (status === "ENDED") {
      return korean ? "종료됨" : "Confirmed";
    }
    if (status === "CANCELED") {
      return korean ? "취소됨" : "Canceled";
    }
    return status;
  }

  function meetingStatusStyle(status: string) {
    if (status === "SCHEDULED") {
      return "bg-[var(--app-accent-soft)] text-[var(--app-accent-text)] border-[color:color-mix(in_srgb,var(--app-accent)_24%,white)]";
    }
    if (status === "IN_PROGRESS") {
      return "bg-[var(--app-success-soft)] text-[var(--app-success)] border-[color:color-mix(in_srgb,var(--app-success)_28%,white)]";
    }
    if (status === "ENDED") {
      return "bg-[var(--app-ai-soft)] text-[var(--app-ai)] border-[color:color-mix(in_srgb,var(--app-ai)_28%,white)]";
    }
    if (status === "CANCELED") {
      return "bg-[var(--app-danger-soft)] text-[var(--app-danger)] border-[color:color-mix(in_srgb,var(--app-danger)_24%,white)]";
    }
    return "bg-[var(--app-warning-soft)] text-[var(--app-warning)] border-[color:color-mix(in_srgb,var(--app-warning)_26%,white)]";
  }

  function timeRangeLabel() {
    if (!meetingDetail) {
      return korean ? "일정을 불러오는 중..." : "Loading schedule...";
    }
    const scheduledAt = new Date(meetingDetail.scheduledAt);
    const scheduledEndAt = new Date(meetingDetail.scheduledEndAt);
    if (Number.isNaN(scheduledAt.getTime()) || Number.isNaN(scheduledEndAt.getTime())) {
      return korean ? "일정 정보 없음" : "Schedule unavailable";
    }
    const dateLocale = korean ? "ko-KR" : "en-US";
    return `${scheduledAt.toLocaleDateString(dateLocale, { month: "short", day: "numeric" })}, ${scheduledAt.toLocaleTimeString(dateLocale, {
      hour: "numeric",
      minute: "2-digit"
    })} - ${scheduledEndAt.toLocaleTimeString(dateLocale, {
      hour: "numeric",
      minute: "2-digit"
    })}`;
  }

  if (meetingLoading) {
    return <LoadingState label={korean ? "회의를 불러오는 중..." : "Loading meeting..."} />;
  }

  if (meetingError instanceof ApiRequestError && meetingError.status === 403) {
    return <PermissionDenied type="meeting" />;
  }

  if (meetingError instanceof ApiRequestError && meetingError.status === 404) {
    return (
      <EmptyState
        action={(
          <button
            className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
            onClick={() => navigate(`/spaces/${spaceId}/meetings`)}
            type="button"
          >
            {korean ? "회의 목록으로" : "Back to meetings"}
          </button>
        )}
        desc={korean ? "회의가 삭제되었거나 더 이상 접근할 수 없습니다." : "The meeting may have been deleted or you may no longer have access."}
        icon={<Video className="w-5 h-5" />}
        title={korean ? "회의를 찾을 수 없습니다" : "Meeting not found"}
      />
    );
  }

  if (meetingError) {
    return (
      <ErrorState
        desc={meetingError.message}
        onRetry={() => {
          void loadMeeting();
        }}
        title={korean ? "회의를 불러올 수 없습니다" : "Couldn't load meeting"}
      />
    );
  }

  if (!meetingDetail) {
    return <LoadingState label={korean ? "회의를 불러오는 중..." : "Loading meeting..."} />;
  }

  const joinTarget = meetingDetail.status === "IN_PROGRESS" ? "live" : "live/prejoin";
  const outletContext: MeetingOutletContext = {
    meetingDetail,
    meetingLoading,
    meetingError,
    reloadMeeting: loadMeeting,
    canManageParticipants: shellContext.spaceDetail?.role === "OWNER"
      || shellContext.spaceDetail?.role === "ADMIN"
      || meetingDetail.myRole === "HOST"
  };

  return (
    <div className="flex flex-col h-full bg-background">
      {/* Meeting Context Header */}
      <div className="border-b border-border bg-card px-4 sm:px-8 pt-6 pb-0 flex flex-col gap-5 sm:gap-6 shrink-0">
        <div className="flex flex-col items-start justify-between gap-4 sm:flex-row">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider border ${meetingStatusStyle(meetingDetail.status)}`}>{meetingStatusLabel(meetingDetail.status)}</span>
              <span className="text-sm text-muted-foreground font-medium">{timeRangeLabel()}</span>
            </div>
            <h1 className="text-2xl font-bold text-foreground">{meetingDetail.title}</h1>
          </div>
          <div className="flex w-full items-center gap-2 sm:w-auto">
            <button
              onClick={() => navigate(joinTarget)}
              className="bg-primary text-primary-foreground px-6 py-2.5 rounded-md text-sm font-bold shadow-sm hover:bg-primary/90 flex items-center gap-2 transition-colors"
            >
              <Video className="w-4 h-4" /> {meetingDetail.status === "IN_PROGRESS" ? (korean ? "회의 다시 참여" : "Resume Meeting Room") : (korean ? "회의실 입장" : "Enter Meeting Room")}
            </button>
          </div>
        </div>

        {/* Context Navigation Tabs */}
        <nav className="flex items-center gap-6 overflow-x-auto text-sm font-medium">
          <NavLink
            to={`/spaces/${spaceId}/meetings/${meetingId}`}
            end
            className={({ isActive }) => `pb-3 border-b-2 transition-colors ${isActive ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground hover:border-border'}`}
          >
            {korean ? "개요" : "Overview"}
          </NavLink>
          <NavLink
            to={`/spaces/${spaceId}/meetings/${meetingId}/transcript`}
            className={({ isActive }) => `pb-3 border-b-2 transition-colors ${isActive ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground hover:border-border'}`}
          >
            {korean ? "전사" : "Transcript"}
          </NavLink>
          <NavLink
            to={`/spaces/${spaceId}/meetings/${meetingId}/report`}
            className={({ isActive }) => `pb-3 border-b-2 transition-colors ${isActive ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground hover:border-border'}`}
          >
            {korean ? "AI 회의록" : "AI Report"}
          </NavLink>
          <NavLink
            to={`/spaces/${spaceId}/meetings/${meetingId}/tasks`}
            className={({ isActive }) => `pb-3 border-b-2 transition-colors ${isActive ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground hover:border-border'}`}
          >
            {korean ? "태스크 후보" : "Task Candidates"}
          </NavLink>
          <NavLink
            to={`/spaces/${spaceId}/meetings/${meetingId}/participants`}
            className={({ isActive }) => `pb-3 border-b-2 transition-colors flex items-center gap-1.5 ${isActive ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground hover:border-border'}`}
          >
            <Users className="w-3.5 h-3.5" /> {korean ? "참가자" : "Participants"}
          </NavLink>
        </nav>
      </div>

      <div className="flex-1 overflow-y-auto p-8 max-w-5xl mx-auto w-full">
        <Outlet context={outletContext} />
      </div>
    </div>
  );
};

// 5.1 Meeting Detail Overview
const MeetingOverview = () => {
  const { meetingDetail } = useOutletContext<MeetingOutletContext>();

  if (!meetingDetail) {
    return <LoadingState label="Loading meeting overview..." />;
  }

  const activeParticipants = meetingDetail.participants.filter((participant) => participant.accessStatus === "ACTIVE");

  return (
    <div className="grid grid-cols-1 gap-6 xl:grid-cols-3 xl:gap-8">
      <div className="space-y-6 xl:col-span-2">
        <div className="bg-card border border-border rounded-lg p-6">
          <h3 className="font-semibold mb-4 text-foreground">Agenda & Description</h3>
          <MeetingDescription value={meetingDetail.description} />
        </div>
      </div>

      <div className="space-y-6 xl:col-span-1">
        <div className="bg-card border border-border rounded-lg p-6">
          <div className="mb-4 space-y-2">
            <h3 className="font-semibold text-foreground">
              Participants ({activeParticipants.length})
            </h3>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Direct meeting invitations are not connected yet. Use the project members screen or meeting access request flow instead.
            </p>
          </div>
          <div className="space-y-4">
            {activeParticipants.length > 0 ? activeParticipants.map((participant) => {
              const name = participant.displayName?.trim() || participant.userId;
              return (
              <div key={participant.id} className="flex items-center gap-3">
                <div className="w-8 h-8 rounded bg-muted flex items-center justify-center text-xs font-bold text-foreground">
                  {name.charAt(0).toUpperCase()}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-medium text-foreground truncate">{name}</div>
                  <div className="text-xs text-muted-foreground truncate">{participant.participantType}</div>
                </div>
                <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">{participant.role}</span>
              </div>
            )}) : (
              <EmptyState
                desc="No active participants are visible for this meeting."
                icon={<Users className="w-5 h-5" />}
                title="No participants"
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

const MeetingParticipants = () => {
  const { session } = useAuthState();
  const authSession = session as AuthSession;
  const { spaceId = "" } = useParams();
  const { meetingDetail, canManageParticipants, reloadMeeting } = useOutletContext<MeetingOutletContext>();
  const [spaceMembers, setSpaceMembers] = useState<SpaceMemberSummary[]>([]);
  const [selectedMemberId, setSelectedMemberId] = useState("");
  const [memberSearch, setMemberSearch] = useState("");
  const [memberPending, setMemberPending] = useState(false);
  const [invitePending, setInvitePending] = useState(false);
  const [notice, setNotice] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!canManageParticipants || !spaceId) return;
    void fetchSpaceMembers(authSession, spaceId).then((response) => setSpaceMembers(response.members)).catch(() => setSpaceMembers([]));
  }, [authSession, canManageParticipants, spaceId]);

  if (!meetingDetail) {
    return <LoadingState label="Loading participants..." />;
  }

  const activeParticipants = meetingDetail.participants.filter((participant) => participant.accessStatus === "ACTIVE");
  const availableMembers = spaceMembers.filter((member) => !activeParticipants.some((participant) => participant.userId === member.userId));
  const matchingMembers = availableMembers.filter((member) => {
    const query = memberSearch.trim().toLocaleLowerCase();
    if (!query) return true;
    return [member.displayName, member.email, member.userId]
      .filter(Boolean)
      .some((value) => value!.toLocaleLowerCase().includes(query));
  });

  async function handleAddMember(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedMemberId || memberPending) return;
    setMemberPending(true); setError("");
    try {
      await addMeetingParticipant(authSession, meetingDetail!.id, { userId: selectedMemberId, role: "VIEWER", participantType: "member" });
      setNotice("Space member added to this meeting."); setSelectedMemberId(""); await reloadMeeting();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Couldn't add the space member."); }
    finally { setMemberPending(false); }
  }
  async function handleCreateGuestInvite(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const targetMeetingId = meetingDetail?.id;
    if (invitePending || !targetMeetingId) return;
    setInvitePending(true);
    setError("");
    try {
      const result = await createMeetingInvitation(authSession, targetMeetingId);
      const url = `${window.location.origin}/meeting-invitations/${encodeURIComponent(targetMeetingId)}/${encodeURIComponent(result.invitationId)}#token=${encodeURIComponent(result.inviteToken)}`;
      await navigator.clipboard.writeText(url);
      setNotice("Guest invite link copied. Sign in to join this meeting directly.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Couldn't create the guest invitation.");
    } finally {
      setInvitePending(false);
    }
  }

  return (
    <div className="max-w-3xl space-y-5 p-4 sm:p-8">
      <section className="rounded-lg border border-border bg-card p-6">
        <div className="flex flex-col items-start justify-between gap-3 border-b border-border pb-4 sm:flex-row sm:gap-4">
          <div>
            <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">Meeting access</p>
            <h2 className="mt-1 text-lg font-semibold text-foreground">Participants</h2>
            <p className="mt-1 text-sm leading-relaxed text-muted-foreground">Space membership does not automatically grant access to this meeting. Only active participants can view its transcript, report, and Meeting AI.</p>
          </div>
          <span className="shrink-0 rounded-full border border-border px-2.5 py-1 text-xs font-medium text-muted-foreground">{activeParticipants.length} active</span>
        </div>
        {notice ? <p className="mt-3 text-xs text-emerald-700" role="status">{notice}</p> : null}
        {error ? <p className="mt-3 text-xs text-red-600" role="alert">{error}</p> : null}
        <div className="mt-2 divide-y divide-border">
          {activeParticipants.length ? activeParticipants.map((participant) => {
            const name = participant.displayName?.trim() || participant.email?.trim() || participant.userId;
            return (
              <div className="flex items-center gap-3 py-4" key={participant.id}>
                <ProfileAvatar alt={name} className="h-9 w-9 shrink-0 rounded-md text-sm font-semibold" initials={participantInitials(name)} pictureUrl={participant.pictureUrl} />
                <div className="min-w-0 flex-1"><p className="truncate text-sm font-medium text-foreground">{name}</p><p className="truncate text-xs text-muted-foreground">{participant.role === "HOST" ? "Host" : participant.participantType === "member" ? "Member" : "Guest"}</p></div>
              </div>
            );
          }) : <EmptyState desc="An owner, admin, or active host can grant access. Guests can join with a valid invite link after signing in." icon={<Users className="w-5 h-5" />} title="No active participants" />}
        </div>
      </section>
      {canManageParticipants ? <section className="rounded-lg border border-border bg-card p-6">
        <form className="mb-5 flex flex-wrap items-end gap-3 border-b border-border pb-5" onSubmit={handleAddMember}>
          <div className="min-w-0 flex-1">
            <label className="mb-1 block text-xs font-semibold text-muted-foreground" htmlFor="meeting-member-search">Add Space Member</label>
            <input
              aria-label="Search space members"
              className="w-full rounded-md border border-border bg-card px-3 py-2 text-sm text-foreground outline-none focus:ring-1 focus:ring-primary/50"
              disabled={memberPending}
              id="meeting-member-search"
              onChange={(event) => { setMemberSearch(event.target.value); setSelectedMemberId(""); }}
              placeholder="Search by name or email"
              value={selectedMemberId ? (availableMembers.find((member) => member.userId === selectedMemberId)?.displayName || availableMembers.find((member) => member.userId === selectedMemberId)?.email || "") : memberSearch}
            />
            {memberSearch.trim() && !selectedMemberId ? (
              <div className="mt-1 max-h-40 overflow-y-auto rounded-md border border-border bg-card shadow-sm" role="listbox">
                {matchingMembers.length ? matchingMembers.map((member) => {
                  const label = member.displayName || member.email || member.userId;
                  return <button className="block w-full px-3 py-2 text-left text-sm hover:bg-muted" key={member.id} onClick={() => { setSelectedMemberId(member.userId); setMemberSearch(""); }} type="button">{label}<span className="ml-2 text-xs text-muted-foreground">{member.email && member.displayName ? member.email : ""}</span></button>;
                }) : <p className="px-3 py-2 text-xs text-muted-foreground">No matching members.</p>}
              </div>
            ) : null}
            {selectedMemberId ? <p className="mt-1 text-xs text-muted-foreground">Selected: {availableMembers.find((member) => member.userId === selectedMemberId)?.displayName || availableMembers.find((member) => member.userId === selectedMemberId)?.email}</p> : null}
          </div>
          <button className="min-h-10 rounded-md bg-foreground px-4 py-2 text-sm font-semibold text-background hover:bg-foreground/90 disabled:opacity-50" disabled={!selectedMemberId || memberPending} type="submit">{memberPending ? "Adding..." : "Grant access"}</button>
        </form>
        <form className="flex items-center justify-between gap-3 border-b border-border pb-4" onSubmit={handleCreateGuestInvite}>
          <p className="text-sm text-muted-foreground">Create a guest link. Recipients sign in and join this meeting directly.</p>
          <button className="min-h-10 shrink-0 rounded-md bg-foreground px-4 py-2 text-sm font-semibold text-background hover:bg-foreground/90 disabled:opacity-50" disabled={invitePending} type="submit">{invitePending ? "Creating..." : "Copy guest invite"}</button>
        </form>
      </section> : null}
    </div>
  );
};

const MeetingReportRoute = () => {
  const { session } = useAuthState();
  return <MeetingReportPage session={session} />;
};
// 5.2 Transcript
const MeetingTranscript = () => {
  const { session } = useAuthState();
  const navigate = useNavigate();
  const { spaceId = "", meetingId = "" } = useParams();
  const { meetingDetail } = useOutletContext<MeetingOutletContext>();
  const [search, setSearch] = useState("");

  const dialogueQuery = useMeetingDialogueQuery(meetingId, { enabled: Boolean(session) });
  const loading = dialogueQuery.isLoading;
  const error = dialogueQuery.error instanceof Error ? dialogueQuery.error : null;
  const dialogueStatus = dialogueQuery.status;
  const loadTranscript = dialogueQuery.refetch;

  function formatTime(startMs: number) {
    const totalSeconds = Math.max(0, Math.floor(startMs / 1000));
    const hours = String(Math.floor(totalSeconds / 3600)).padStart(2, "0");
    const minutes = String(Math.floor((totalSeconds % 3600) / 60)).padStart(2, "0");
    const seconds = String(totalSeconds % 60).padStart(2, "0");
    return `${hours}:${minutes}:${seconds}`;
  }

  function avatarTone(value: string) {
    const tones = [
      "bg-blue-100 text-blue-700",
      "bg-purple-100 text-purple-700",
      "bg-emerald-100 text-emerald-700",
      "bg-amber-100 text-amber-700",
      "bg-rose-100 text-rose-700"
    ];
    const hash = Array.from(value).reduce((acc, char) => acc + char.charCodeAt(0), 0);
    return tones[hash % tones.length];
  }

  const entries = dialogueQuery.entries;
  const filtered = filterTranscriptEntries(entries, search);

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-3 mb-6">
        <div className="relative flex-1 max-w-sm">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Search transcript..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full pl-9 pr-3 py-2 rounded-md border border-border bg-card text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
          />
        </div>
        <span className="text-xs text-muted-foreground">{loading ? "Loading..." : `${filtered.length} entries`}</span>
      </div>

      {loading ? <LoadingState label="Loading transcript..." /> : null}

      {!loading && error instanceof ApiRequestError && error.status === 403 ? <PermissionDenied type="meeting" /> : null}

      {!loading && error instanceof ApiRequestError && error.status === 404 ? (
        <EmptyState
          action={(
            <button
              className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
              onClick={() => navigate(`/spaces/${spaceId}/meetings`)}
              type="button"
            >
              Back to meetings
            </button>
          )}
          desc="The meeting may have been deleted or you may no longer have access."
          icon={<FileText className="w-5 h-5" />}
          title="Couldn't load transcript"
        />
      ) : null}

      {!loading && error && !(error instanceof ApiRequestError && (error.status === 403 || error.status === 404)) ? (
        <ErrorState
          desc={error.message}
          onRetry={() => { void loadTranscript(); }}
          title="Couldn't load transcript"
        />
      ) : null}

      {!loading && !error && dialogueStatus === "FAILED" ? (
        <ErrorState
          desc="STT processing failed for this meeting."
          onRetry={() => { void loadTranscript(); }}
          title="Transcript failed"
        />
      ) : null}

      {!loading && !error && dialogueStatus === "PROCESSING" && filtered.length === 0 ? (
        <EmptyState
          desc="Transcript is still processing. Refresh again in a moment."
          icon={<Mic className="w-5 h-5" />}
          title="Transcript in progress"
        />
      ) : null}

      {!loading && !error && dialogueStatus === "COMPLETED" && entries.length === 0 ? (
        <EmptyState
          desc="No transcript segments were saved for this meeting."
          icon={<FileText className="w-5 h-5" />}
          title="No transcript yet"
        />
      ) : null}

      {!loading && !error && filtered.length === 0 && entries.length > 0 ? (
        <EmptyState
          desc="Try a different speaker or keyword."
          icon={<Search className="w-5 h-5" />}
          title="No matching transcript"
        />
      ) : null}

      {!loading && !error && filtered.length > 0 ? filtered.map((entry) => {
        const speakerName = entry.speakerName;
        return (
          <div key={entry.key} className="flex gap-4 group hover:bg-muted/30 rounded-lg p-3 -mx-3 transition-colors">
            <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold shrink-0 mt-0.5 ${avatarTone(entry.speakerId || speakerName)}`}>
              {speakerName.charAt(0)}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-baseline gap-2 mb-1">
                <span className="text-sm font-semibold text-foreground">
                  {speakerName}
                  {entry.isPartial ? <span className="ml-2 text-[11px] text-primary">LIVE</span> : null}
                </span>
                <span className="text-xs text-muted-foreground font-mono">{entry.isPartial ? "Listening…" : formatTime(entry.startMs)}</span>
              </div>
              <p className="text-sm text-foreground/80 leading-relaxed">{entry.text}</p>
            </div>
          </div>
        );
      }) : null}

      {!loading && !error && meetingDetail?.status === "IN_PROGRESS" ? (
        <p className="text-xs text-muted-foreground mt-4">This transcript can continue updating while the meeting is live.</p>
      ) : null}
    </div>
  );
};

// 5.4 Task Candidates
const MeetingTaskCandidates = () => {
  const { session } = useAuthState();
  const navigate = useNavigate();
  const { spaceId = "", meetingId = "" } = useParams();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [candidates, setCandidates] = useState<Array<{
    id: string;
    meetingId: string;
    title: string;
    assigneeName: string | null;
    suggestedAssigneeId: string | null;
    dueDate: string | null;
    status: "CANDIDATE" | "CONFIRMED" | "DISMISSED";
    sourceIds: string[];
  }>>([]);
  const [canConfirm, setCanConfirm] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [mutationError, setMutationError] = useState("");

  const loadCandidates = React.useCallback(async () => {
    if (!session || !meetingId) {
      setCandidates([]);
      setCanConfirm(false);
      setLoading(false);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const response = await fetchTaskCandidates(session, meetingId);
      setCandidates(response.candidates);
      setCanConfirm(response.canConfirm);
      setSelectedIds((current) => current.filter((id) => response.candidates.some((candidate) => candidate.id === id && candidate.status === "CANDIDATE")));
    } catch (cause) {
      setCandidates([]);
      setCanConfirm(false);
      setError(cause instanceof Error ? cause : new Error("태스크 후보를 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [meetingId, session]);

  useEffect(() => {
    void loadCandidates();
  }, [loadCandidates]);

  const addedCount = selectedIds.length;

  function toggle(candidateId: string) {
    setSelectedIds((current) =>
      current.includes(candidateId)
        ? current.filter((id) => id !== candidateId)
        : [...current, candidateId]
    );
  }

  function statusBadge(status: "CANDIDATE" | "CONFIRMED" | "DISMISSED") {
    if (status === "CONFIRMED") {
      return "bg-[var(--app-success-soft)] text-[var(--app-success)] border-[color:color-mix(in_srgb,var(--app-success)_28%,white)]";
    }
    if (status === "DISMISSED") {
      return "bg-[var(--app-surface-muted)] text-[var(--app-muted)] border-[var(--app-line)]";
    }
    return "bg-[var(--app-accent-soft)] text-[var(--app-accent-text)] border-[color:color-mix(in_srgb,var(--app-accent)_24%,white)]";
  }

  function statusLabel(status: "CANDIDATE" | "CONFIRMED" | "DISMISSED") {
    if (status === "CONFIRMED") {
      return "Registered";
    }
    if (status === "DISMISSED") {
      return "Dismissed";
    }
    return "Candidate";
  }

  async function handleGenerate() {
    if (!session || !meetingId || generating) {
      return;
    }
    setGenerating(true);
    setMutationError("");
    try {
      const response = await extractTaskCandidates(session, meetingId);
      setCandidates(response.candidates);
      setCanConfirm(response.canConfirm);
      setSelectedIds([]);
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "태스크 후보 생성에 실패했습니다.");
    } finally {
      setGenerating(false);
    }
  }

  async function handleAddSelected() {
    if (!session || !meetingId || submitting || selectedIds.length === 0) {
      return;
    }
    setSubmitting(true);
    setMutationError("");
    try {
      for (const candidateId of selectedIds) {
        const candidate = candidates.find((item) => item.id === candidateId);
        if (!candidate || candidate.status !== "CANDIDATE") {
          continue;
        }
        await confirmTaskCandidate(session, meetingId, candidate.id, {
          title: candidate.title,
          description: null,
          assigneeId: candidate.suggestedAssigneeId,
          dueDate: candidate.dueDate,
          status: "TODO"
        });
      }
      setSelectedIds([]);
      await loadCandidates();
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "태스크 후보 확정에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDismiss(candidateId: string) {
    if (!session || !meetingId || submitting) {
      return;
    }
    setSubmitting(true);
    setMutationError("");
    try {
      await dismissTaskCandidate(session, meetingId, candidateId);
      setSelectedIds((current) => current.filter((id) => id !== candidateId));
      await loadCandidates();
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "태스크 후보 제외에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-muted-foreground">AI-extracted task candidates from this meeting. Select which ones to add to the project board.</p>
        <div className="flex items-center gap-2">
          <button
            className="px-4 py-2 rounded-md border border-border text-sm font-medium hover:bg-muted transition-colors disabled:opacity-60"
            disabled={generating}
            onClick={() => void handleGenerate()}
            type="button"
          >
            {generating ? "Generating..." : "Generate Candidates"}
          </button>
          <button
            className={`text-sm font-semibold px-4 py-2 rounded-md transition-colors ${addedCount > 0 ? "bg-foreground text-background hover:bg-foreground/90" : "bg-muted text-muted-foreground cursor-not-allowed"}`}
            disabled={addedCount === 0 || !canConfirm || submitting}
            onClick={() => void handleAddSelected()}
            type="button"
          >
            {submitting ? "Adding..." : `Add ${addedCount > 0 ? `${addedCount} ` : ""}to Board`}
          </button>
        </div>
      </div>

      {!canConfirm ? <p className="text-xs text-muted-foreground">Only users with meeting edit permission can register or dismiss task candidates.</p> : null}
      {mutationError ? <p className="text-xs text-red-600" role="alert">{mutationError}</p> : null}

      {loading ? <LoadingState label="Loading task candidates..." /> : null}

      {!loading && error instanceof ApiRequestError && error.status === 403 ? <PermissionDenied type="meeting" /> : null}

      {!loading && error instanceof ApiRequestError && error.status === 404 ? (
        <EmptyState
          action={(
            <button
              className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
              onClick={() => navigate(`/spaces/${spaceId}/meetings`)}
              type="button"
            >
              Back to meetings
            </button>
          )}
          desc="The meeting may have been deleted or you no longer have access."
          icon={<CheckSquare className="w-5 h-5" />}
          title="Task candidates unavailable"
        />
      ) : null}

      {!loading && error && !(error instanceof ApiRequestError && (error.status === 403 || error.status === 404)) ? (
        <ErrorState
          desc={error.message}
          onRetry={() => { void loadCandidates(); }}
          title="Couldn't load task candidates"
        />
      ) : null}

      {!loading && !error && candidates.length === 0 ? (
        <EmptyState
          action={(
            <button
              className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
              disabled={generating}
              onClick={() => void handleGenerate()}
              type="button"
            >
              {generating ? "Generating..." : "Generate Candidates"}
            </button>
          )}
          desc="Generate task candidates from the current meeting report and transcript."
          icon={<CheckSquare className="w-5 h-5" />}
          title="No task candidates yet"
        />
      ) : null}

      {!loading && !error && candidates.length > 0 ? (
        <div className="bg-card border border-border rounded-lg divide-y divide-border overflow-hidden">
          {candidates.map((candidate) => {
            const selected = selectedIds.includes(candidate.id);
            const selectable = candidate.status === "CANDIDATE" && canConfirm;
            return (
              <div
                key={candidate.id}
                onClick={() => { if (selectable) { toggle(candidate.id); } }}
                className={`flex items-center gap-4 px-5 py-4 transition-colors ${selectable ? "cursor-pointer" : "cursor-default"} ${selected ? "bg-primary/5" : "hover:bg-muted/30"}`}
              >
                <div className={`w-5 h-5 rounded border-2 flex items-center justify-center shrink-0 transition-colors ${selected ? "bg-primary border-primary" : "border-muted-foreground/40"}`}>
                  {selected ? <span className="text-white text-xs font-bold">✓</span> : null}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-foreground truncate">{candidate.title}</p>
                  <div className="flex items-center gap-2 mt-0.5 flex-wrap">
                    <span className="text-xs text-muted-foreground">{candidate.assigneeName ?? "Assignee not specified"}</span>
                    {candidate.dueDate ? <span className="text-xs text-muted-foreground">· {candidate.dueDate}</span> : null}
                    <span className="text-xs text-muted-foreground">· {candidate.sourceIds.length} source{candidate.sourceIds.length === 1 ? "" : "s"}</span>
                  </div>
                </div>
                <span className={`text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded border shrink-0 ${statusBadge(candidate.status)}`}>{statusLabel(candidate.status)}</span>
                {candidate.status === "CANDIDATE" && canConfirm ? (
                  <button
                    className="text-xs text-muted-foreground hover:text-red-600 transition-colors"
                    onClick={(event) => {
                      event.stopPropagation();
                      void handleDismiss(candidate.id);
                    }}
                    type="button"
                  >
                    Dismiss
                  </button>
                ) : null}
              </div>
            );
          })}
        </div>
      ) : null}
    </div>
  );
};

// 5.5 Meeting AI Chat
const MeetingAIChat = ({
  meetingIdOverride,
  meetingDetailOverride
}: {
  meetingIdOverride?: string;
  meetingDetailOverride?: MeetingDetailResponse | null;
}) => {
  const { session } = useAuthState();
  const { meetingId: routeMeetingId = "" } = useParams();
  const meetingContext = useOutletContext<MeetingOutletContext | undefined>();
  const meetingId = meetingIdOverride ?? routeMeetingId;
  const meetingDetail = meetingDetailOverride ?? meetingContext?.meetingDetail;
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<Array<{
    role: "user" | "assistant";
    text: string;
    sources?: AiSource[];
    unsupported?: boolean;
  }>>([
    {
      role: "assistant",
      text: "현재 회의 범위에서만 질문할 수 있습니다. 근거가 없는 내용은 답하지 않습니다."
    }
  ]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [model, setModel] = useState("");

  useEffect(() => {
    setMessages([
      {
        role: "assistant",
        text: "현재 회의 범위에서만 질문할 수 있습니다. 근거가 없는 내용은 답하지 않습니다."
      }
    ]);
    setInput("");
    setError("");
    setModel("");
  }, [meetingId]);

  const send = async () => {
    if (!input.trim() || !session || !meetingId || loading) {
      return;
    }
    const userMsg = input.trim();
    setInput("");
    setError("");
    setLoading(true);
    setMessages((prev) => [...prev, { role: "user", text: userMsg }]);
    try {
      const response = await chatMeetingAi(session, meetingId, { question: userMsg });
      setModel(response.model);
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          text: response.unsupported ? unsupportedAiMessage(response.unsupportedReason, "meeting") : response.answer,
          sources: response.sources,
          unsupported: response.unsupported
        }
      ]);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Meeting AI에 연결하지 못했습니다.";
      setError(message);
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          text: "Meeting AI 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.",
          unsupported: true
        }
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col h-full" style={{ minHeight: "60vh" }}>
      <div className="mb-4 flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
        <span className="px-2 py-1 rounded-full border border-border bg-card">Current meeting only</span>
        <span className="px-2 py-1 rounded-full border border-border bg-card">Sources required</span>
        {meetingDetail?.status ? <span className="px-2 py-1 rounded-full border border-border bg-card">Status: {meetingDetail.status}</span> : null}
        {model ? <span className="px-2 py-1 rounded-full border border-border bg-card">Model: {model}</span> : null}
      </div>
      <div className="flex-1 space-y-4 overflow-y-auto pb-4">
        {messages.map((m, i) => (
          <div key={i} className={`flex gap-3 ${m.role === "user" ? "flex-row-reverse" : ""}`}>
            {m.role === "assistant" && (
              <div className="w-7 h-7 rounded-full bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
                <Sparkles className="w-3.5 h-3.5 text-primary" />
              </div>
            )}
            <div className={`max-w-[80%] rounded-xl px-4 py-3 text-sm leading-relaxed ${m.role === "user" ? "bg-foreground text-background rounded-tr-sm" : "bg-card border border-border text-foreground rounded-tl-sm"}`}>
              {m.text}
              {m.sources?.length ? (
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {m.sources.map((source) => (
                    <span key={`${source.sourceId}-${source.type}`} className={`text-[10px] font-medium px-2 py-0.5 rounded-full border ${aiSourceTone(source.type)}`}>
                      {aiSourceLabel(source)}
                    </span>
                  ))}
                </div>
              ) : null}
            </div>
          </div>
        ))}
        {loading ? <p className="text-xs text-muted-foreground">근거를 확인하고 있습니다...</p> : null}
        {error ? <p className="text-xs text-red-600">{error}</p> : null}
      </div>
      <div className="flex gap-2 pt-4 border-t border-border">
        <input
          type="text"
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => { if (e.key === "Enter") { void send(); } }}
          placeholder="Ask about this meeting..."
          className="flex-1 px-4 py-2.5 rounded-lg border border-border bg-card text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
        />
        <button
          onClick={() => void send()}
          disabled={!input.trim() || loading || !session || !meetingId}
          className="px-4 py-2.5 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors disabled:opacity-60"
        >
          {loading ? "Checking..." : "Send"}
        </button>
      </div>
    </div>
  );
};

// 6. Project Tasks — Kanban Board
const ProjectTasks = () => {
  const { session } = useAuthState();
  const { locale } = useAppPreferences();
  const { spaceId = "" } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [tasks, setTasks] = useState<TaskCard[]>([]);
  const [members, setMembers] = useState<SpaceMembersResponse["members"]>([]);
  const [showOpenOnly, setShowOpenOnly] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [selectedTask, setSelectedTask] = useState<TaskCard | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [formError, setFormError] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [assigneeId, setAssigneeId] = useState("");
  const [assigneeSearch, setAssigneeSearch] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [priority, setPriority] = useState<TaskCardPriority>("MEDIUM");
  const [status, setStatus] = useState<TaskCardStatus>("TODO");
  const [draggedTaskId, setDraggedTaskId] = useState<string | null>(null);
  const [dropTargetStatus, setDropTargetStatus] = useState<TaskCardStatus | null>(null);
  const [movingTaskId, setMovingTaskId] = useState<string | null>(null);
  const [moveError, setMoveError] = useState("");
  const korean = locale === "ko";

  const loadTasks = React.useCallback(async () => {
    if (!session || !spaceId) {
      setTasks([]);
      setMembers([]);
      setLoading(false);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const [tasksResult, membersResult] = await Promise.all([
        fetchTasks(session, spaceId),
        fetchSpaceMembers(session, spaceId)
      ]);
      setTasks(tasksResult.tasks);
      setMembers(membersResult.members);
    } catch (cause) {
      setTasks([]);
      setMembers([]);
      setError(cause instanceof Error ? cause : new Error("태스크를 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [session, spaceId]);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  function resetForm() {
    setTitle("");
    setDescription("");
    setAssigneeId("");
    setAssigneeSearch("");
    setDueDate("");
    setPriority("MEDIUM");
    setStatus("TODO");
    setFormError("");
  }

  function openCreateModal() {
    resetForm();
    setSelectedTask(null);
    setCreateOpen(true);
  }

  function openEditModal(task: TaskCard) {
    setSelectedTask(task);
    setTitle(task.title);
    setDescription(task.description ?? "");
    setAssigneeId(task.assigneeId ?? "");
    setAssigneeSearch("");
    setDueDate(task.dueDate ?? "");
    setPriority(task.priority);
    setStatus(task.status);
    setFormError("");
    setCreateOpen(true);
  }

  async function handleSaveTask(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session || !spaceId || saving) {
      return;
    }
    if (!title.trim()) {
      setFormError("Task title is required.");
      return;
    }

    setSaving(true);
    setFormError("");
    try {
      if (selectedTask) {
        await updateTask(session, spaceId, selectedTask.id, {
          title: title.trim(),
          description: description.trim() || null,
          assigneeId: assigneeId || null,
          dueDate: dueDate || null,
          priority,
          status
        });
      } else {
        await createTask(session, spaceId, {
          title: title.trim(),
          description: description.trim() || null,
          assigneeId: assigneeId || null,
          dueDate: dueDate || null,
          priority,
          labels: []
        });
      }
      await loadTasks();
      setCreateOpen(false);
      resetForm();
      setSelectedTask(null);
    } catch (cause) {
      setFormError(cause instanceof Error ? cause.message : "Task save failed.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDeleteTask() {
    if (!session || !spaceId || !selectedTask || deleting) {
      return;
    }
    setDeleting(true);
    setFormError("");
    try {
      await deleteTask(session, spaceId, selectedTask.id);
      await loadTasks();
      setCreateOpen(false);
      resetForm();
      setSelectedTask(null);
    } catch (cause) {
      setFormError(cause instanceof Error ? cause.message : "Task delete failed.");
    } finally {
      setDeleting(false);
    }
  }

  async function moveTaskToStatus(taskId: string, nextStatus: TaskCardStatus) {
    const task = tasks.find((item) => item.id === taskId);
    if (!session || !spaceId || !task || task.status === nextStatus || movingTaskId) {
      return;
    }

    setMoveError("");
    setMovingTaskId(taskId);
    try {
      const updated = await updateTask(session, spaceId, taskId, { status: nextStatus });
      setTasks((currentTasks) => currentTasks.map((item) => (
        item.id === taskId ? { ...item, status: updated.status } : item
      )));
      try {
        await loadTasks();
      } catch {
        // Keep the successful PATCH response visible while the list refresh catches up.
      }
    } catch (cause) {
      setMoveError(cause instanceof Error ? cause.message : "Task status update failed.");
    } finally {
      setMovingTaskId(null);
    }
  }

  function handleTaskDragStart(event: React.DragEvent<HTMLDivElement>, taskId: string) {
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", taskId);
    setDraggedTaskId(taskId);
    setMoveError("");
  }

  function handleTaskDragEnd() {
    setDraggedTaskId(null);
    setDropTargetStatus(null);
  }

  function handleTaskDrop(event: React.DragEvent<HTMLDivElement>, nextStatus: TaskCardStatus) {
    event.preventDefault();
    const taskId = event.dataTransfer.getData("text/plain") || draggedTaskId;
    setDraggedTaskId(null);
    setDropTargetStatus(null);
    if (taskId) {
      void moveTaskToStatus(taskId, nextStatus);
    }
  }

  function priorityLabel(taskPriority: TaskCardPriority) {
    if (taskPriority === "HIGH") {
      return "High";
    }
    if (taskPriority === "LOW") {
      return "Low";
    }
    return "Medium";
  }

  const priorityStyle: Record<TaskCardPriority, string> = {
    HIGH: "bg-[var(--app-danger-soft)] text-[var(--app-danger)] border-[color:color-mix(in_srgb,var(--app-danger)_24%,white)]",
    MEDIUM: "bg-[var(--app-warning-soft)] text-[var(--app-warning)] border-[color:color-mix(in_srgb,var(--app-warning)_26%,white)]",
    LOW: "bg-[var(--app-surface-muted)] text-[var(--app-muted)] border-[var(--app-line)]"
  };

  const memberLookup = new Map(
    members.map((member) => [
      member.userId,
      {
        name: member.displayName?.trim() || member.email || "Unknown",
        initials: participantInitials(member.displayName?.trim() || member.email || "MM"),
        pictureUrl: member.pictureUrl
      }
    ])
  );

  const normalizedTasks = tasks.filter((task) => !showOpenOnly || task.status !== "DONE");
  const columns = [
    { id: "todo", label: "To Do", color: "text-[var(--app-muted)]", dot: "bg-[var(--app-line-strong)]", status: "TODO" as TaskCardStatus },
    { id: "inprogress", label: "In Progress", color: "text-[var(--app-accent)]", dot: "bg-[var(--app-accent)]", status: "IN_PROGRESS" as TaskCardStatus },
    { id: "review", label: "In Review", color: "text-[var(--app-ai)]", dot: "bg-[var(--app-ai)]", status: "IN_REVIEW" as TaskCardStatus },
    { id: "done", label: "Done", color: "text-[var(--app-success)]", dot: "bg-[var(--app-success)]", status: "DONE" as TaskCardStatus }
  ];

  const tasksByColumn = columns.map((column) => ({
    ...column,
    tasks: normalizedTasks.filter((task) => task.status === column.status)
  }));
  const matchingAssignees = members.filter((member) => {
    const query = assigneeSearch.trim().toLocaleLowerCase();
    if (!query) return false;
    return [member.displayName, member.email, member.userId]
      .filter(Boolean)
      .some((value) => value!.toLocaleLowerCase().includes(query));
  });

  return (
    <div className="p-6 flex flex-col gap-4 h-full">
      <div className="flex items-center justify-between shrink-0">
          <h1 className="text-xl font-bold">{korean ? "태스크" : "Tasks"}</h1>
        <div className="flex items-center gap-2">
          <button
            className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground px-3 py-1.5 rounded-md hover:bg-muted transition-colors"
            onClick={() => setShowOpenOnly((current) => !current)}
            type="button"
          >
            <Filter className="w-3.5 h-3.5" /> {showOpenOnly ? (korean ? "전체 보기" : "Show all") : (korean ? "진행 중만" : "Open only")}
          </button>
          <button
            className="flex items-center gap-1.5 text-sm font-medium bg-foreground text-background px-3 py-1.5 rounded-md hover:bg-foreground/90 transition-colors"
            onClick={openCreateModal}
            type="button"
          >
            <Plus className="w-3.5 h-3.5" /> {korean ? "태스크 추가" : "New Task"}
          </button>
        </div>
      </div>

      {moveError ? <p className="text-sm text-red-600" role="alert">{moveError}</p> : null}

      {loading ? <LoadingState label={korean ? "태스크를 불러오는 중..." : "Loading tasks..."} /> : null}

      {!loading && error instanceof ApiRequestError && error.status === 403 ? <PermissionDenied type="project" /> : null}

      {!loading && error instanceof ApiRequestError && error.status === 404 ? (
        <EmptyState
          action={(
            <button
              className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
              onClick={() => navigate("/spaces")}
              type="button"
            >
              Back to workspaces
            </button>
          )}
          desc="The project was not found or you no longer have access."
          icon={<CheckSquare className="w-5 h-5" />}
          title="Tasks unavailable"
        />
      ) : null}

      {!loading && error && !(error instanceof ApiRequestError && (error.status === 403 || error.status === 404)) ? (
        <ErrorState
          desc={error.message}
          onRetry={() => {
            void loadTasks();
          }}
          title="Couldn't load tasks"
        />
      ) : null}

      {!loading && !error && tasks.length === 0 ? (
        <EmptyState
          action={(
            <button
              className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors inline-flex items-center gap-2"
              onClick={openCreateModal}
              type="button"
            >
              <Plus className="w-4 h-4" /> Create first task
            </button>
          )}
          desc="Confirmed action items and manual tasks will appear here."
          icon={<CheckSquare className="w-5 h-5" />}
          title="No tasks yet"
        />
      ) : null}

      {!loading && !error && tasks.length > 0 ? (
        <div className="flex gap-4 overflow-x-auto pb-2 flex-1">
          {tasksByColumn.map((col) => (
            <div
              key={col.id}
              className={`flex flex-col gap-3 min-w-[280px] w-[280px] ${dropTargetStatus === col.status ? "rounded-lg bg-primary/5" : ""}`}
              onDragLeave={() => {
                if (dropTargetStatus === col.status) {
                  setDropTargetStatus(null);
                }
              }}
              onDragOver={(event) => {
                if (!draggedTaskId || movingTaskId) {
                  return;
                }
                event.preventDefault();
                event.dataTransfer.dropEffect = "move";
                setDropTargetStatus(col.status);
              }}
              onDrop={(event) => {
                handleTaskDrop(event, col.status);
              }}
            >
              <div className="flex items-center gap-2 px-1">
                <span className={`w-2 h-2 rounded-full shrink-0 ${col.dot}`}></span>
                <span className={`text-sm font-semibold ${col.color}`}>{col.label}</span>
                <span className="text-xs text-muted-foreground ml-auto">{col.tasks.length}</span>
              </div>

              <div className="flex flex-col gap-2 flex-1">
                {col.tasks.length > 0 ? col.tasks.map((task) => {
                  const assignee = task.assigneeId ? memberLookup.get(task.assigneeId) : null;
                  const chip = task.labels[0] ?? (task.meetingId ? "Meeting" : "General");
                  return (
                    <div
                      key={task.id}
                      aria-grabbed={draggedTaskId === task.id}
                      className={`bg-card border border-border rounded-lg p-3.5 cursor-pointer hover:border-border/80 hover:shadow-sm transition-all group ${draggedTaskId === task.id ? "opacity-50" : ""}`}
                      draggable={!movingTaskId}
                      onDragEnd={handleTaskDragEnd}
                      onDragStart={(event) => handleTaskDragStart(event, task.id)}
                      onClick={() => openEditModal(task)}
                    >
                      <p className="text-sm font-medium text-foreground leading-snug mb-3">{task.title}</p>
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-1.5">
                          <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded border ${priorityStyle[task.priority]}`}>{priorityLabel(task.priority)}</span>
                          <span className="text-[10px] text-muted-foreground px-1.5 py-0.5 rounded border border-border bg-muted/30">{chip}</span>
                        </div>
                        {assignee ? <ProfileAvatar alt={assignee.name} className="w-6 h-6 rounded-full text-[10px] font-bold shrink-0" initials={assignee.initials} pictureUrl={assignee.pictureUrl} /> : <div className="w-6 h-6 rounded-full bg-muted flex items-center justify-center text-[10px] font-bold text-muted-foreground shrink-0">—</div>}
                      </div>
                    </div>
                  );
                }) : (
                  <div className="bg-card border border-dashed border-border rounded-lg p-4 text-xs text-muted-foreground min-h-[92px] flex items-center justify-center text-center">
                    No tasks in this column.
                  </div>
                )}
                <button
                  className="flex items-center gap-2 px-3 py-2.5 rounded-lg text-sm text-muted-foreground hover:text-foreground hover:bg-muted/50 transition-colors w-full"
                  onClick={openCreateModal}
                  type="button"
                >
                  <Plus className="w-3.5 h-3.5" /> Add task
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : null}

      {createOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm"
          onClick={(event) => {
            if (event.target === event.currentTarget && !saving && !deleting) {
              setCreateOpen(false);
            }
          }}
        >
          <div className="w-full max-w-md bg-card rounded-xl border border-border shadow-2xl">
            <div className="px-6 py-5 border-b border-border flex items-start justify-between gap-4">
              <div>
                <h2 className="font-semibold text-foreground">{selectedTask ? "Edit Task" : "New Task"}</h2>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {selectedTask ? "Update status, assignee, and due date." : "Create a new task in this project."}
                </p>
              </div>
              <button
                className="w-7 h-7 flex items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors shrink-0"
                disabled={saving || deleting}
                onClick={() => setCreateOpen(false)}
                type="button"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <form className="px-6 py-5 space-y-4" onSubmit={handleSaveTask}>
              <div>
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Title</label>
                <input
                  className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                  maxLength={160}
                  onChange={(event) => setTitle(event.target.value)}
                  value={title}
                />
              </div>
              <div>
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Description</label>
                <textarea
                  className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 resize-none"
                  maxLength={1000}
                  onChange={(event) => setDescription(event.target.value)}
                  rows={3}
                  value={description}
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Assignee</label>
                  <input
                    aria-label="Search assignee"
                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                    onChange={(event) => { setAssigneeSearch(event.target.value); setAssigneeId(""); }}
                    placeholder={assigneeId ? (memberLookup.get(assigneeId)?.name ?? "Search member") : "Search by name or email"}
                    value={assigneeSearch}
                  />
                  {assigneeId ? (
                    <button className="mt-1 text-xs text-muted-foreground hover:text-foreground" onClick={() => { setAssigneeId(""); setAssigneeSearch(""); }} type="button">Clear assignee</button>
                  ) : null}
                  {assigneeSearch.trim() ? (
                    <div className="mt-1 max-h-32 overflow-y-auto rounded-md border border-border bg-card shadow-sm" role="listbox">
                      {matchingAssignees.length ? matchingAssignees.map((member) => {
                        const label = member.displayName?.trim() || member.email || member.userId;
                        return <button className="block w-full px-3 py-2 text-left text-sm hover:bg-muted" key={member.id} onClick={() => { setAssigneeId(member.userId); setAssigneeSearch(""); }} type="button">{label}<span className="ml-2 text-xs text-muted-foreground">{member.email && member.displayName ? member.email : ""}</span></button>;
                      }) : <p className="px-3 py-2 text-xs text-muted-foreground">No matching members.</p>}
                    </div>
                  ) : null}
                </div>
                <div>
                  <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Due date</label>
                  <input
                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                    onChange={(event) => setDueDate(event.target.value)}
                    type="date"
                    value={dueDate}
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Priority</label>
                  <select
                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                    onChange={(event) => setPriority(event.target.value as TaskCardPriority)}
                    value={priority}
                  >
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Status</label>
                  <select
                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                    disabled={!selectedTask}
                    onChange={(event) => setStatus(event.target.value as TaskCardStatus)}
                    value={status}
                  >
                    <option value="TODO">To Do</option>
                    <option value="IN_PROGRESS">In Progress</option>
                    <option value="IN_REVIEW">In Review</option>
                    <option value="DONE">Done</option>
                  </select>
                </div>
              </div>
              {formError ? <p className="text-xs text-red-600" role="alert">{formError}</p> : null}
              <div className="pt-2 flex items-center justify-between gap-2">
                <div>
                  {selectedTask ? (
                    <button
                      className="px-4 py-2 rounded-md text-sm font-semibold bg-red-600 text-white hover:bg-red-700 transition-colors"
                      disabled={saving || deleting}
                      onClick={() => void handleDeleteTask()}
                      type="button"
                    >
                      {deleting ? "Deleting..." : "Delete"}
                    </button>
                  ) : null}
                </div>
                <div className="flex items-center gap-2">
                  <button
                    className="px-4 py-2 rounded-md border border-border text-sm text-muted-foreground hover:bg-muted transition-colors"
                    disabled={saving || deleting}
                    onClick={() => setCreateOpen(false)}
                    type="button"
                  >
                    Cancel
                  </button>
                  <button
                    className="px-4 py-2 rounded-md text-sm font-semibold bg-foreground text-background hover:bg-foreground/90 transition-colors"
                    disabled={saving || deleting}
                    type="submit"
                  >
                    {saving ? "Saving..." : selectedTask ? "Save Changes" : "Create Task"}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </div>
  );
};

// 8. Members & Roles
const ProjectMembers = () => {
  const { session } = useAuthState();
  const { locale } = useAppPreferences();
  const authSession = session as AuthSession;
  const { spaceId = "" } = useParams();
  const { spaceDetail } = useOutletContext<ShellOutletContext>();
  const navigate = useNavigate();
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<"ADMIN" | "MEMBER">("MEMBER");
  const [members, setMembers] = useState<SpaceMembersResponse["members"]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [invitePending, setInvitePending] = useState(false);
  const [actionPending, setActionPending] = useState(false);
  const [actionError, setActionError] = useState("");
  const [inviteNotice, setInviteNotice] = useState("");
  const [invitations, setInvitations] = useState<Awaited<ReturnType<typeof fetchSpaceInvitations>>["invitations"]>([]);
  const [invitationActionId, setInvitationActionId] = useState("");
  const [confirmState, setConfirmState] = useState<null | {
    type: "role" | "remove" | "transfer";
    member: SpaceMembersResponse["members"][number];
    nextRole?: "ADMIN" | "MEMBER";
    previousOwnerRole?: "ADMIN" | "MEMBER";
  }>(null);
  const korean = locale === "ko";

  const loadMembers = React.useCallback(async () => {
    if (!session || !spaceId) {
      setMembers([]);
      setLoading(false);
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await fetchSpaceMembers(session, spaceId);
      setMembers(response.members);
    } catch (cause) {
      setMembers([]);
      setError(cause instanceof Error ? cause : new Error("멤버를 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [session, spaceId]);

  useEffect(() => {
    void loadMembers();
  }, [loadMembers]);

  const currentRole = spaceDetail?.role ?? "MEMBER";
  const canManageMembers = currentRole === "OWNER" || currentRole === "ADMIN";
  const canTransferOwner = currentRole === "OWNER";

  const loadInvitations = React.useCallback(async () => {
    if (!session || !spaceId || !canManageMembers) return;
    try { setInvitations((await fetchSpaceInvitations(session, spaceId)).invitations); } catch { setInvitations([]); }
  }, [session, spaceId, canManageMembers]);

  useEffect(() => { void loadInvitations(); }, [loadInvitations]);

  const roleColor: Record<"OWNER" | "ADMIN" | "MEMBER", string> = {
    OWNER: "bg-foreground text-background",
    ADMIN: "bg-[var(--app-accent-soft)] text-[var(--app-accent-text)] border border-[color:color-mix(in_srgb,var(--app-accent)_24%,white)]",
    MEMBER: "bg-muted text-muted-foreground border border-border"
  };

  function memberIdentity(member: SpaceMembersResponse["members"][number]) {
    const name = member.displayName?.trim() || member.email || member.userId;
    const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase() ?? "").join("") || "MM";
    return { name, initials, pictureUrl: member.pictureUrl };
  }

  function joinedLabel(value: string) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
  }

  async function handleInvite() {
    if (!session || !spaceId || invitePending) {
      return;
    }
    if (!inviteEmail.trim()) {
      setActionError("Email is required.");
      return;
    }
    setInvitePending(true);
    setActionError("");
    setInviteNotice("");
    try {
      const response = await createSpaceInvitation(session, spaceId, {
        email: inviteEmail.trim(),
        role: inviteRole
      });
      setInviteNotice(`Invite created (${response.status}).`);
      setInviteEmail("");
      setInviteRole("MEMBER");
      await loadInvitations();
    } catch (cause) {
      setActionError(cause instanceof Error ? cause.message : "Invite failed.");
    } finally {
      setInvitePending(false);
    }
  }

  async function handleConfirmAction() {
    if (!session || !spaceId || !confirmState || actionPending) {
      return;
    }
    setActionPending(true);
    setActionError("");
    try {
      if (confirmState.type === "role" && confirmState.nextRole) {
        await updateSpaceMemberRole(session, spaceId, confirmState.member.id, {
          role: confirmState.nextRole
        });
      }
      if (confirmState.type === "remove") {
        await removeSpaceMember(session, spaceId, confirmState.member.id);
      }
      if (confirmState.type === "transfer" && confirmState.previousOwnerRole) {
        await transferSpaceOwner(session, spaceId, {
          targetMemberId: confirmState.member.id,
          previousOwnerRole: confirmState.previousOwnerRole,
          confirmation: "TRANSFER OWNER"
        });
      }
      setConfirmState(null);
      await loadMembers();
    } catch (cause) {
      setActionError(cause instanceof Error ? cause.message : "Member action failed.");
    } finally {
      setActionPending(false);
    }
  }

  return (
    <div className="p-8 max-w-3xl mx-auto space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold">{korean ? "멤버 및 역할" : "Members & Roles"}</h1>
          <p className="text-sm text-muted-foreground mt-0.5">{korean ? `이 스페이스의 멤버 ${members.length}명` : `${members.length} members in this space`}</p>
        </div>
      </div>

      {/* Invite */}
      <div className="bg-card border border-border rounded-lg p-5 space-y-3">
        <h3 className="font-semibold text-sm">{korean ? "멤버 초대" : "Invite Member"}</h3>
        <div className="flex gap-2">
          <input
            type="email"
            placeholder="colleague@company.com"
            value={inviteEmail}
            onChange={e => setInviteEmail(e.target.value)}
            disabled={!canManageMembers || invitePending}
            className="flex-1 px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
          />
          <select
            className="px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none text-muted-foreground"
            disabled={!canManageMembers || invitePending}
            onChange={(event) => setInviteRole(event.target.value as "ADMIN" | "MEMBER")}
            value={inviteRole}
          >
            <option value="ADMIN">Admin</option>
            <option value="MEMBER">Member</option>
          </select>
          <button
            onClick={() => void handleInvite()}
            disabled={!canManageMembers || invitePending}
            className="px-4 py-2 bg-foreground text-background rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
          >
            {invitePending ? (korean ? "보내는 중..." : "Sending...") : (korean ? "초대 보내기" : "Send Invite")}
          </button>
        </div>
        {!canManageMembers ? <p className="text-xs text-muted-foreground">{korean ? "프로젝트 OWNER 또는 ADMIN만 멤버를 초대할 수 있습니다." : "Only project owner or admin can invite members."}</p> : null}
      {inviteNotice ? <p className="text-xs text-emerald-700">{inviteNotice}</p> : null}
      </div>
      {canManageMembers ? <div className="bg-card border border-border rounded-lg overflow-hidden">
        <div className="px-5 py-4 border-b border-border"><h3 className="font-semibold text-sm">{korean ? "대기 중인 초대" : "Pending invitations"}</h3><p className="mt-1 text-xs text-muted-foreground">{korean ? "아직 참여하지 않은 사람에게 보낸 초대를 관리합니다." : "Manage invitations sent to people who have not joined yet."}</p></div>
        {invitations.length === 0 ? <p className="px-5 py-6 text-sm text-muted-foreground">No pending invitations.</p> : <div className="divide-y divide-border">{invitations.filter((item) => item.status === "PENDING").map((invitation) => <div className="flex items-center justify-between gap-4 px-5 py-3" key={invitation.invitationId}><div className="min-w-0"><p className="truncate text-sm font-medium">{invitation.email}</p><p className="mt-1 text-xs text-muted-foreground">{invitation.role} · expires {joinedLabel(invitation.expiresAt)}</p></div><div className="flex shrink-0 gap-2"><button className="rounded-md border border-border px-3 py-1.5 text-xs hover:bg-muted disabled:opacity-50" disabled={Boolean(invitationActionId)} onClick={async () => { setInvitationActionId(invitation.invitationId); try { await resendSpaceInvitation(authSession, spaceId, invitation.invitationId); await loadInvitations(); setInviteNotice("Invitation resent."); } catch (cause) { setActionError(cause instanceof Error ? cause.message : "Resend failed."); } finally { setInvitationActionId(""); } }} type="button">Resend</button><button className="rounded-md px-3 py-1.5 text-xs text-red-600 hover:bg-red-50 disabled:opacity-50" disabled={Boolean(invitationActionId)} onClick={async () => { if (!window.confirm("Cancel this invitation?")) return; setInvitationActionId(invitation.invitationId); try { await cancelSpaceInvitation(authSession, spaceId, invitation.invitationId); await loadInvitations(); } catch (cause) { setActionError(cause instanceof Error ? cause.message : "Cancel failed."); } finally { setInvitationActionId(""); } }} type="button">Cancel</button></div></div>)}</div>}
      </div> : null}
      {actionError ? <p className="text-xs text-red-600" role="alert">{actionError}</p> : null}
      {loading ? <LoadingState label={korean ? "멤버를 불러오는 중..." : "Loading members..."} /> : null}
      {!loading && error instanceof ApiRequestError && error.status === 403 ? <PermissionDenied type="project" /> : null}
      {!loading && error instanceof ApiRequestError && error.status === 404 ? (
        <EmptyState
          action={(
            <button
              className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
              onClick={() => navigate("/spaces")}
              type="button"
            >
              Back to workspaces
            </button>
          )}
          desc="The project was not found or you no longer have access."
          icon={<Users className="w-5 h-5" />}
          title="Members unavailable"
        />
      ) : null}
      {!loading && error && !(error instanceof ApiRequestError && (error.status === 403 || error.status === 404)) ? (
        <ErrorState
          desc={error.message}
          onRetry={() => { void loadMembers(); }}
          title="Couldn't load members"
        />
      ) : null}

      {/* Member List */}
      {!loading && !error ? <div className="bg-card border border-border rounded-lg overflow-hidden">
        <div className="px-5 py-3 border-b border-border bg-muted/30 grid grid-cols-12 gap-4 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
          <span className="col-span-5">Member</span>
          <span className="col-span-3">Role</span>
          <span className="col-span-2">Joined</span>
          <span className="col-span-2 text-left">Status</span>
        </div>
        <div className="divide-y divide-border">
          {members.length > 0 ? members.map(m => {
            const { name, initials } = memberIdentity(m);
            return (
            <div key={m.id} className="px-5 py-3.5 grid grid-cols-12 gap-4 items-center hover:bg-muted/20 transition-colors group">
              <div className="col-span-5 flex items-center gap-3 min-w-0">
                <ProfileAvatar alt={name} className="w-8 h-8 rounded-full text-xs font-bold shrink-0" initials={initials} pictureUrl={m.pictureUrl} />
                <div className="min-w-0">
                  <div className="text-sm font-medium text-foreground truncate">{name}</div>
                  <div className="text-xs text-muted-foreground truncate">{m.email}</div>
                </div>
              </div>
              <div className="col-span-3 flex items-center gap-2">
                <span className={`text-xs font-semibold px-2 py-0.5 rounded ${roleColor[m.role]}`}>
                  {m.role === "OWNER" ? "Owner" : m.role === "ADMIN" ? "Admin" : "Member"}
                </span>
                {canTransferOwner && m.role !== "OWNER" ? (
                  <button
                    className="text-xs text-primary hover:underline"
                    onClick={() => setConfirmState({ type: "transfer", member: m, previousOwnerRole: "ADMIN" })}
                    type="button"
                  >
                    Transfer
                  </button>
                ) : null}
              </div>
              <div className="col-span-2 text-xs text-muted-foreground">{joinedLabel(m.joinedAt)}</div>
              <div className="col-span-2 flex items-center justify-start gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                <span className="text-xs text-muted-foreground">Active</span>
                {canManageMembers && m.role !== "OWNER" && (
                  <button onClick={() => setConfirmState({ type: "remove", member: m })} className="opacity-0 group-hover:opacity-100 transition-opacity ml-1 text-muted-foreground hover:text-red-500">
                    <X className="w-3.5 h-3.5" />
                  </button>
                )}
              </div>
            </div>
          )}) : (
            <EmptyState
              desc="Invited and active project members will appear here."
              icon={<Users className="w-5 h-5" />}
              title="No members yet"
            />
          )}
        </div>
      </div> : null}

      {/* Role Descriptions */}
      <div className="grid grid-cols-3 gap-4">
        {[
          { role: "OWNER" as const, label: "Owner", desc: "Full access. Can manage members, settings, and transfer ownership." },
          { role: "ADMIN" as const, label: "Admin", desc: "Can manage members, meetings, knowledge, and tasks." },
          { role: "MEMBER" as const, label: "Member", desc: "Can use allowed project features and access granted meetings." },
        ].map(r => (
          <div key={r.role} className="bg-card border border-border rounded-lg p-4">
            <span className={`text-xs font-semibold px-2 py-0.5 rounded inline-block mb-2 ${roleColor[r.role]}`}>{r.label}</span>
            <p className="text-xs text-muted-foreground leading-relaxed">{r.desc}</p>
          </div>
        ))}
      </div>

      {confirmState ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm"
          onClick={(event) => {
            if (event.target === event.currentTarget && !actionPending) {
              setConfirmState(null);
            }
          }}
        >
          <div className="w-full max-w-md bg-card rounded-xl border border-border shadow-2xl">
            <div className="px-6 py-5 border-b border-border">
              <h2 className="font-semibold text-foreground">
                {confirmState.type === "role" ? "Change Role" : confirmState.type === "remove" ? "Remove Member" : "Transfer Ownership"}
              </h2>
              <p className="text-xs text-muted-foreground mt-1">
                {confirmState.type === "role"
                  ? "Confirm the new project role."
                  : confirmState.type === "remove"
                  ? "This member will lose project access."
                  : "Ownership transfer changes top-level project control."}
              </p>
            </div>
            <div className="px-6 py-5 space-y-4">
              <p className="text-sm text-foreground">
                {confirmState.type === "role"
                  ? `Change ${memberIdentity(confirmState.member).name} to ${confirmState.nextRole === "ADMIN" ? "Admin" : "Member"}?`
                  : confirmState.type === "remove"
                  ? `Remove ${memberIdentity(confirmState.member).name} from this project?`
                  : `Transfer ownership to ${memberIdentity(confirmState.member).name}?`}
              </p>
              {confirmState.type === "transfer" ? (
                <div>
                  <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Previous owner role</label>
                  <select
                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                    onChange={(event) => setConfirmState((current) => current && current.type === "transfer" ? { ...current, previousOwnerRole: event.target.value as "ADMIN" | "MEMBER" } : current)}
                    value={confirmState.previousOwnerRole ?? "ADMIN"}
                  >
                    <option value="ADMIN">Admin</option>
                    <option value="MEMBER">Member</option>
                  </select>
                </div>
              ) : null}
            </div>
            <div className="px-6 py-4 border-t border-border flex items-center justify-end gap-2">
              <button
                className="px-4 py-2 rounded-md border border-border text-sm text-muted-foreground hover:bg-muted transition-colors"
                disabled={actionPending}
                onClick={() => setConfirmState(null)}
                type="button"
              >
                Cancel
              </button>
              <button
                className={`px-4 py-2 rounded-md text-sm font-semibold transition-colors ${confirmState.type === "remove" ? "bg-red-600 text-white hover:bg-red-700" : "bg-foreground text-background hover:bg-foreground/90"}`}
                disabled={actionPending}
                onClick={() => void handleConfirmAction()}
                type="button"
              >
                {actionPending ? "Applying..." : confirmState.type === "remove" ? "Remove" : confirmState.type === "transfer" ? "Transfer" : "Update Role"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};

// 9. Terms Dictionary
const TermsDictionary = () => {
  const { session } = useAuthState();
  const { locale } = useAppPreferences();
  const navigate = useNavigate();
  const { spaceId = "" } = useParams();
  const { spaceDetail } = useOutletContext<ShellOutletContext>();
  const [search, setSearch] = useState("");
  const [terms, setTerms] = useState<DomainTerm[]>([]);
  const [archivedTerms, setArchivedTerms] = useState<DomainTerm[]>([]);
  const [showArchived, setShowArchived] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [saving, setSaving] = useState(false);
  const [mutationError, setMutationError] = useState("");
  const [notice, setNotice] = useState("");
  const [newTerm, setNewTerm] = useState({ term: "", definition: "" });
  const [draftTerm, setDraftTerm] = useState({ term: "", definition: "" });
  const korean = locale === "ko";

  const currentRole = spaceDetail?.role ?? "MEMBER";
  const canManageTerms = currentRole === "OWNER" || currentRole === "ADMIN";

  const loadTerms = React.useCallback(async () => {
    if (!session || !spaceId) {
      setTerms([]);
      setLoading(false);
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [activeResponse, archivedResponse] = await Promise.all([
        fetchDomainTerms(session, spaceId, { status: "ACTIVE" }),
        fetchDomainTerms(session, spaceId, { status: "ARCHIVED" })
      ]);
      setTerms(activeResponse.terms);
      setArchivedTerms(archivedResponse.terms);
      setSelectedId((current) => current && [...activeResponse.terms, ...archivedResponse.terms].some((term) => term.id === current) ? current : activeResponse.terms[0]?.id ?? null);
    } catch (cause) {
      setTerms([]);
      setError(cause instanceof Error ? cause : new Error("용어 사전을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [session, spaceId]);

  useEffect(() => {
    void loadTerms();
  }, [loadTerms]);

  const selected = terms.find((term) => term.id === selectedId) ?? null;
  const selectedArchived = archivedTerms.find((term) => term.id === selectedId) ?? null;
  const selectedTerm = selected ?? selectedArchived;

  useEffect(() => {
    if (!selectedTerm) {
      setDraftTerm({ term: "", definition: "" });
      return;
    }
    setDraftTerm({ term: selectedTerm.term, definition: selectedTerm.definition });
  }, [selectedTerm]);

  const statusTone: Record<DomainTerm["status"], string> = {
    ACTIVE: "bg-emerald-50 text-emerald-700 border-emerald-200",
    ARCHIVED: "bg-slate-100 text-slate-700 border-slate-200"
  };

  const filtered = terms.filter(t =>
    search === "" || t.term.toLowerCase().includes(search.toLowerCase()) || t.definition.toLowerCase().includes(search.toLowerCase())
  );

  function formatUpdatedAt(value: string) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleDateString("ko-KR", {
      year: "numeric",
      month: "short",
      day: "numeric"
    });
  }

  async function handleCreateTerm() {
    if (!session || !spaceId || !canManageTerms || saving) {
      return;
    }
    if (!newTerm.term.trim() || !newTerm.definition.trim()) {
      setMutationError("용어와 설명을 모두 입력해 주세요.");
      return;
    }
    setSaving(true);
    setMutationError("");
    setNotice("");
    try {
      const response = await createDomainTerm(session, spaceId, {
        term: newTerm.term.trim(),
        definition: newTerm.definition.trim()
      });
      await loadTerms();
      setSelectedId(response.id);
      setNewTerm({ term: "", definition: "" });
      setShowAdd(false);
      setNotice("용어를 등록했습니다.");
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "용어를 등록하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function handleUpdateTerm() {
    if (!session || !spaceId || !selected || !canManageTerms || saving) {
      return;
    }
    if (!draftTerm.term.trim() || !draftTerm.definition.trim()) {
      setMutationError("용어와 설명을 모두 입력해 주세요.");
      return;
    }
    setSaving(true);
    setMutationError("");
    setNotice("");
    try {
      await updateDomainTerm(session, spaceId, selected.id, {
        term: draftTerm.term.trim(),
        definition: draftTerm.definition.trim()
      });
      await loadTerms();
      setNotice("용어를 저장했습니다.");
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "용어를 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function handleArchiveTerm() {
    if (!session || !spaceId || !selected || !canManageTerms || saving) {
      return;
    }
    setSaving(true);
    setMutationError("");
    setNotice("");
    try {
      await archiveDomainTerm(session, spaceId, selected.id);
      await loadTerms();
      setNotice("용어를 보관했습니다.");
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "용어를 보관하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function handleRestoreTerm() {
    if (!session || !spaceId || !selectedArchived || !canManageTerms || saving) return;
    setSaving(true);
    setMutationError("");
    setNotice("");
    try {
      await updateDomainTerm(session, spaceId, selectedArchived.id, { status: "ACTIVE" });
      await loadTerms();
      setShowArchived(false);
      setSelectedId(selectedArchived.id);
      setNotice("용어를 복구했습니다.");
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "용어를 복구하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading terms..." />;
  }

  if (error instanceof ApiRequestError && error.status === 403) {
    return <PermissionDenied type="project" />;
  }

  if (error instanceof ApiRequestError && error.status === 404) {
    return (
      <EmptyState
        action={(
          <button
            className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
            onClick={() => navigate("/spaces")}
            type="button"
          >
            Back to workspaces
          </button>
        )}
        desc="The project was not found or you no longer have access."
        icon={<BookOpen className="w-5 h-5" />}
        title="Terms unavailable"
      />
    );
  }

  if (error) {
    return <ErrorState desc={error.message} onRetry={() => { void loadTerms(); }} title="Couldn't load terms" />;
  }

  return (
    <div className="flex h-full overflow-hidden">
      {/* List */}
      <div className="w-72 shrink-0 border-r border-border bg-card flex flex-col h-full">
        <div className="p-3 border-b border-border space-y-2">
          <div className="relative">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              type="text"
              placeholder={korean ? "용어 검색..." : "Search terms..."}
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full pl-7 pr-2 py-1.5 rounded-md border border-border bg-background text-xs focus:outline-none focus:ring-1 focus:ring-primary/50"
            />
          </div>
        </div>
        <div className="flex-1 overflow-y-auto custom-scrollbar divide-y divide-border">
          {filtered.map(t => (
            <button
              key={t.id}
              onClick={() => setSelectedId(t.id)}
              className={`w-full px-4 py-3 text-left hover:bg-muted/30 transition-colors ${selected?.id === t.id ? "bg-muted" : ""}`}
            >
              <div className="flex items-center justify-between gap-2 mb-0.5">
                <span className="text-sm font-semibold text-foreground">{t.term}</span>
                <span className={`text-[9px] font-medium px-1.5 py-0.5 rounded border shrink-0 ${statusTone[t.status]}`}>{t.status}</span>
              </div>
              <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed">{t.definition}</p>
            </button>
          ))}
          <div className="border-t border-border">
              <button className="flex w-full items-center justify-between px-4 py-3 text-left text-xs font-semibold text-muted-foreground hover:bg-muted/30" onClick={() => setShowArchived((value) => !value)} type="button">
                <span>{korean ? "보관된 용어" : "Archived terms"}</span>
                <span>{archivedTerms.length}</span>
              </button>
              {showArchived ? archivedTerms.map((term) => (
                <button key={term.id} onClick={() => setSelectedId(term.id)} className={`w-full px-4 py-3 text-left hover:bg-muted/30 ${selectedId === term.id ? "bg-muted" : ""}`} type="button">
                  <div className="flex items-center justify-between gap-2"><span className="text-sm font-semibold text-foreground">{term.term}</span><span className={`text-[9px] font-medium px-1.5 py-0.5 rounded border ${statusTone[term.status]}`}>{term.status}</span></div>
                  <p className="text-xs text-muted-foreground line-clamp-2">{term.definition}</p>
                </button>
              )) : null}
          </div>
        </div>
        <div className="p-3 border-t border-border">
          <button
            onClick={() => setShowAdd(true)}
            disabled={!canManageTerms}
            className="w-full flex items-center justify-center gap-1.5 py-2 rounded-md bg-foreground text-background text-xs font-semibold hover:bg-foreground/90 transition-colors"
          >
            <Plus className="w-3.5 h-3.5" /> {korean ? "용어 추가" : "Add Term"}
          </button>
          {!canManageTerms ? <p className="mt-2 text-[11px] text-muted-foreground">OWNER 또는 ADMIN만 용어를 수정할 수 있습니다.</p> : null}
        </div>
      </div>

      {/* Detail / Add */}
      <div className="flex-1 overflow-y-auto">
        {showAdd ? (
          <div className="p-8 max-w-xl space-y-4">
            <h2 className="font-bold text-lg">{korean ? "새 용어 추가" : "Add New Term"}</h2>
            <div className="space-y-3">
              <div>
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1">Term / Abbreviation</label>
                <input value={newTerm.term} onChange={e => setNewTerm(p => ({ ...p, term: e.target.value }))} placeholder="e.g. JWT" className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50" />
              </div>
              <div>
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1">Definition</label>
                <textarea value={newTerm.definition} onChange={e => setNewTerm(p => ({ ...p, definition: e.target.value }))} rows={4} placeholder="Explain what this term means in your project context..." className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 resize-none" />
              </div>
            </div>
            {mutationError ? <p className="text-xs text-red-600">{mutationError}</p> : null}
            <div className="flex gap-2">
              <button onClick={() => { setShowAdd(false); setMutationError(""); }} className="px-4 py-2 rounded-md border border-border text-sm text-muted-foreground hover:bg-muted transition-colors">Cancel</button>
              <button onClick={() => void handleCreateTerm()} disabled={!canManageTerms || saving} className="px-4 py-2 rounded-md bg-foreground text-background text-sm font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60">{saving ? "Saving..." : "Save Term"}</button>
            </div>
          </div>
        ) : selectedTerm ? (
          <div className="p-8 max-w-xl space-y-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <span className={`text-[10px] font-medium px-2 py-0.5 rounded border ${statusTone[selectedTerm.status]}`}>{selectedTerm.status}</span>
                </div>
                <h2 className="text-2xl font-bold text-foreground">{selectedTerm.term}</h2>
                <p className="text-sm text-muted-foreground mt-0.5">Updated {formatUpdatedAt(selectedTerm.updatedAt)}</p>
              </div>
            </div>
            <div className="bg-card border border-border rounded-lg p-5 space-y-4">
              <div>
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1">Term</label>
                <input
                  className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 disabled:opacity-60"
                  disabled={!canManageTerms || saving}
                  onChange={(event) => setDraftTerm((current) => ({ ...current, term: event.target.value }))}
                  value={draftTerm.term}
                />
              </div>
              <div>
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1">Definition</label>
                <textarea
                  className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 resize-none disabled:opacity-60"
                  disabled={!canManageTerms || saving}
                  onChange={(event) => setDraftTerm((current) => ({ ...current, definition: event.target.value }))}
                  rows={6}
                  value={draftTerm.definition}
                />
              </div>
              {!canManageTerms ? <p className="text-xs text-muted-foreground">현재 계정은 용어를 조회만 할 수 있습니다.</p> : null}
              {mutationError ? <p className="text-xs text-red-600">{mutationError}</p> : null}
              {notice ? <p className="text-xs text-emerald-700">{notice}</p> : null}
            </div>
            <div className="flex gap-2">
              {selectedArchived ? (
                <button
                  className="px-4 py-2 rounded-md bg-foreground text-background text-sm font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60"
                  disabled={!canManageTerms || saving}
                  onClick={() => void handleRestoreTerm()}
                  type="button"
                >
                  {saving ? "Restoring..." : "Restore"}
                </button>
              ) : <button
                className="px-4 py-2 rounded-md bg-foreground text-background text-sm font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60"
                disabled={!canManageTerms || saving}
                onClick={() => void handleUpdateTerm()}
                type="button"
              >
                {saving ? "Saving..." : "Save Changes"}
              </button>}
              {!selectedArchived ? <button
                className="px-4 py-2 rounded-md border border-red-200 text-red-700 text-sm font-semibold hover:bg-red-50 transition-colors disabled:opacity-60"
                disabled={!canManageTerms || saving}
                onClick={() => void handleArchiveTerm()}
                type="button"
              >
                Archive
              </button> : null}
            </div>
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">Usage</p>
              <div className="rounded-lg border border-border bg-card px-4 py-3 text-sm text-muted-foreground leading-relaxed">
                등록된 프로젝트 용어는 Meeting AI와 Project AI가 근거를 해석할 때 우선 참고합니다.
              </div>
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center justify-center h-full text-center p-8 text-muted-foreground">
            <BookOpen className="w-10 h-10 mb-3 opacity-30" />
            <p className="text-sm">{terms.length === 0 ? "등록된 용어가 없습니다" : "Select a term to view its definition"}</p>
            {terms.length === 0 ? <p className="mt-2 text-xs">프로젝트 용어를 추가하면 AI와 문서 화면에서 같은 의미로 사용됩니다.</p> : null}
          </div>
        )}
      </div>
    </div>
  );
};

// 10. Calendar
const ProjectCalendar = () => {
  const { session } = useAuthState();
  const { locale } = useAppPreferences();
  const navigate = useNavigate();
  const { spaceId = "" } = useParams();
  const today = new Date();
  const [currentMonth, setCurrentMonth] = useState({ year: today.getFullYear(), month: today.getMonth() + 1 });
  const [selectedDay, setSelectedDay] = useState<number | null>(today.getDate());
  const [dayMeetingPage, setDayMeetingPage] = useState(0);
  const [events, setEvents] = useState<ProjectCalendarEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const korean = locale === "ko";

  const monthStart = React.useMemo(
    () => new Date(currentMonth.year, currentMonth.month - 1, 1),
    [currentMonth.month, currentMonth.year]
  );
  const monthEnd = React.useMemo(
    () => new Date(currentMonth.year, currentMonth.month, 0),
    [currentMonth.month, currentMonth.year]
  );
  useEffect(() => {
    setSelectedDay((current) => {
      if (current === null) {
        return current;
      }
      return current > monthEnd.getDate() ? monthEnd.getDate() : current;
    });
  }, [monthEnd]);

  const loadEvents = React.useCallback(async () => {
    if (!session || !spaceId) {
      setEvents([]);
      setLoading(false);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const response = await fetchCalendarEvents(session, {
        from: monthStart.toISOString(),
        to: new Date(currentMonth.year, currentMonth.month, 1).toISOString(),
        spaceId
      });
      setEvents(response.events);
      setSelectedDay((current) => {
        if (current !== null && current <= monthEnd.getDate()) {
          return current;
        }
        const firstEvent = response.events[0];
        if (!firstEvent) {
          return null;
        }
        return new Date(firstEvent.startsAt).getDate();
      });
    } catch (cause) {
      setEvents([]);
      setError(cause instanceof Error ? cause : new Error("일정을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [currentMonth.month, currentMonth.year, monthEnd, monthStart, session, spaceId]);

  useEffect(() => {
    void loadEvents();
  }, [loadEvents]);

  const meetingsByDay = events.reduce<Record<number, ProjectCalendarEvent[]>>((acc, event) => {
    const day = new Date(event.startsAt).getDate();
    if (!acc[day]) {
      acc[day] = [];
    }
    acc[day].push(event);
    return acc;
  }, {});

  const statusLabel: Record<ProjectCalendarEvent["status"], string> = {
    SCHEDULED: "Upcoming",
    IN_PROGRESS: "In Progress",
    ENDED: "Completed",
    CANCELED: "Canceled"
  };
  const statusStyle: Record<ProjectCalendarEvent["status"], string> = {
    SCHEDULED: "bg-blue-500",
    IN_PROGRESS: "bg-emerald-500",
    ENDED: "bg-violet-500",
    CANCELED: "bg-rose-500"
  };
  const statusBadge: Record<ProjectCalendarEvent["status"], string> = {
    SCHEDULED: "bg-blue-50 text-blue-700 border-blue-200",
    IN_PROGRESS: "bg-emerald-50 text-emerald-700 border-emerald-200",
    ENDED: "bg-violet-50 text-violet-700 border-violet-200",
    CANCELED: "bg-rose-50 text-rose-700 border-rose-200"
  };

  const daysInMonth = monthEnd.getDate();
  const firstDay = monthStart.getDay();
  const days = Array.from({ length: daysInMonth }, (_, i) => i + 1);
  const blanks = Array.from({ length: firstDay }, (_, i) => i);
  const weekdays = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
  const isCurrentMonth =
    currentMonth.year === today.getFullYear() &&
    currentMonth.month === today.getMonth() + 1;
  const monthLabel = monthStart.toLocaleDateString("en-US", { month: "long", year: "numeric" });
  const dayMeetings = selectedDay ? meetingsByDay[selectedDay] ?? [] : [];
  const visibleDayMeetings = dayMeetings.slice(dayMeetingPage * 3, dayMeetingPage * 3 + 3);
  const dayMeetingPageCount = Math.ceil(dayMeetings.length / 3);

  function shiftMonth(offset: number) {
    setCurrentMonth((current) => {
      const nextDate = new Date(current.year, current.month - 1 + offset, 1);
      return {
        year: nextDate.getFullYear(),
        month: nextDate.getMonth() + 1
      };
    });
  }

  function formatTime(value: string) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleTimeString("en-US", {
      hour: "numeric",
      minute: "2-digit"
    });
  }

  return (
    <div className="p-6 flex gap-6 h-full overflow-hidden">
      {/* Calendar Grid */}
      <div className="flex-1 flex flex-col min-w-0">
        <div className="flex items-center justify-between mb-5 shrink-0">
          <h1 className="text-xl font-bold">{monthLabel}</h1>
          <div className="flex items-center gap-1">
            <button
              className="w-8 h-8 flex items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors text-sm font-bold"
              onClick={() => shiftMonth(-1)}
              type="button"
            >
              ‹
            </button>
            <button
              className="px-3 py-1.5 text-xs rounded-md border border-border hover:bg-muted text-muted-foreground transition-colors"
              onClick={() => {
                setCurrentMonth({ year: today.getFullYear(), month: today.getMonth() + 1 });
                setSelectedDay(today.getDate());
              }}
              type="button"
            >
              {korean ? "오늘" : "Today"}
            </button>
            <button
              className="w-8 h-8 flex items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors text-sm font-bold"
              onClick={() => shiftMonth(1)}
              type="button"
            >
              ›
            </button>
          </div>
        </div>

        {loading ? <LoadingState label="Loading calendar..." /> : null}
        {!loading && error instanceof ApiRequestError && error.status === 403 ? <PermissionDenied type="project" /> : null}
        {!loading && error instanceof ApiRequestError && error.status === 404 ? (
          <EmptyState
            action={(
              <button
                className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
                onClick={() => navigate("/spaces")}
                type="button"
              >
                Back to workspaces
              </button>
            )}
            desc="The project was not found or you no longer have access."
            icon={<Calendar className="w-5 h-5" />}
            title="Calendar unavailable"
          />
        ) : null}
        {!loading && error && !(error instanceof ApiRequestError && (error.status === 403 || error.status === 404)) ? (
          <ErrorState
            desc={error.message}
            onRetry={() => { void loadEvents(); }}
            title="Couldn't load calendar"
          />
        ) : null}

        {!loading && !error ? <div className="grid grid-cols-7 gap-px bg-border rounded-lg overflow-hidden border border-border flex-1">
          {/* Weekday headers */}
          {weekdays.map(d => (
            <div key={d} className="bg-muted/50 px-2 py-2 text-center text-[10px] font-semibold text-muted-foreground uppercase tracking-wider">
              {d}
            </div>
          ))}
          {/* Blank cells */}
          {blanks.map(i => <div key={`b${i}`} className="bg-background" />)}
          {/* Day cells */}
          {days.map(day => {
            const dayMeetings = meetingsByDay[day] ?? [];
            const isToday = isCurrentMonth && day === today.getDate();
            const isSelected = day === selectedDay;
            return (
              <div
                key={day}
                onClick={() => {
                  setSelectedDay(day);
                  setDayMeetingPage(0);
                }}
                className={`bg-background p-2 cursor-pointer hover:bg-muted/30 transition-colors min-h-[72px] ${isSelected ? "ring-1 ring-inset ring-primary" : ""}`}
              >
                <span className={`text-xs font-semibold inline-flex w-6 h-6 items-center justify-center rounded-full ${isToday ? "bg-foreground text-background" : "text-foreground"}`}>
                  {day}
                </span>
                <div className="mt-1 space-y-0.5">
                  {dayMeetings.slice(0, 3).map((m, i) => (
                    <div key={i} className="flex items-center gap-1">
                      <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${statusStyle[m.status]}`}></span>
                      <span className="text-[10px] text-foreground truncate">{m.title}</span>
                    </div>
                  ))}
                  {dayMeetings.length > 3 ? (
                    <div className="relative group/calendar-more">
                      <span className="text-[10px] font-semibold text-primary">...</span>
                      <div className="pointer-events-none absolute bottom-full left-0 z-20 mb-1 hidden min-w-44 rounded-md border border-border bg-card p-2 shadow-lg group-hover/calendar-more:block">
                        {dayMeetings.slice(3).map((meeting) => <p className="truncate text-[10px] text-foreground" key={meeting.id}>{formatTime(meeting.startsAt)} {meeting.title}</p>)}
                      </div>
                    </div>
                  ) : null}
                </div>
              </div>
            );
          })}
        </div> : null}
      </div>

      {/* Side Panel */}
      <div className="w-64 shrink-0 flex flex-col gap-4">
        <div className="bg-card border border-border rounded-lg p-4">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">
            {selectedDay ? `${monthLabel} ${selectedDay}` : "Select a day"}
          </p>
          {selectedDay && dayMeetings.length > 0 ? (
            <div className="space-y-3">
              {visibleDayMeetings.map((m) => (
                <button
                  key={m.id}
                  className="w-full space-y-1 text-left rounded-md hover:bg-muted/30 transition-colors p-1"
                  onClick={() => navigate(`/spaces/${spaceId}/meetings/${m.meetingId}`)}
                  type="button"
                >
                  <div className="flex items-center gap-2">
                    <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${statusStyle[m.status]}`}></span>
                    <span className="text-sm font-medium text-foreground leading-snug">{m.title}</span>
                  </div>
                  <div className="flex items-center gap-2 pl-3.5">
                    <Clock className="w-3 h-3 text-muted-foreground" />
                    <span className="text-xs text-muted-foreground">{formatTime(m.startsAt)}</span>
                    <span className={`text-[9px] font-bold uppercase tracking-wider px-1.5 py-0.5 rounded border ${statusBadge[m.status]}`}>
                      {statusLabel[m.status]}
                    </span>
                  </div>
                </button>
              ))}
              {dayMeetingPageCount > 1 ? (
                <div className="flex items-center justify-between border-t border-border pt-3 text-xs">
                  <button className="text-muted-foreground hover:text-foreground disabled:opacity-40" disabled={dayMeetingPage === 0} onClick={() => setDayMeetingPage((page) => Math.max(0, page - 1))} type="button">Previous</button>
                  <span className="text-muted-foreground">{dayMeetingPage + 1} / {dayMeetingPageCount}</span>
                  <button className="text-muted-foreground hover:text-foreground disabled:opacity-40" disabled={dayMeetingPage >= dayMeetingPageCount - 1} onClick={() => setDayMeetingPage((page) => page + 1)} type="button">Next</button>
                </div>
              ) : null}
            </div>
          ) : (
            <p className="text-xs text-muted-foreground">No meetings scheduled</p>
          )}
        </div>

        <div className="bg-card border border-border rounded-lg p-4 space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">Legend</p>
          {(Object.keys(statusStyle) as Array<ProjectCalendarEvent["status"]>).map((status) => (
            <div key={status} className="flex items-center gap-2">
              <span className={`w-2 h-2 rounded-full ${statusStyle[status]}`}></span>
              <span className="text-xs text-muted-foreground">{statusLabel[status]}</span>
            </div>
          ))}
        </div>

        <button
          className="w-full flex items-center justify-center gap-1.5 py-2.5 rounded-lg bg-foreground text-background text-sm font-semibold hover:bg-foreground/90 transition-colors"
          onClick={() => navigate(`/spaces/${spaceId}/meetings`)}
          type="button"
        >
          <Plus className="w-4 h-4" /> Schedule Meeting
        </button>
      </div>
    </div>
  );
};

// 11. Pre-join Room
const PrejoinRoom = () => {
  const navigate = useNavigate();
  const { session } = useAuthState();
  const { spaceId = "", meetingId = "" } = useParams();
  const authSession = session as AuthSession;
  const [micOn, setMicOn] = useState(true);
  const [camOn, setCamOn] = useState(true);
  const [meetingDetail, setMeetingDetail] = useState<MeetingDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const name = authSession.user.displayName?.trim() || "MeetingMind User";

  const loadMeeting = React.useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const detail = await fetchMeetingDetail(authSession, meetingId);
      setMeetingDetail(detail);
    } catch (cause) {
      setMeetingDetail(null);
      setError(cause instanceof Error ? cause : new Error("회의 정보를 확인하지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [authSession, meetingId]);

  useEffect(() => {
    void loadMeeting();
  }, [loadMeeting]);

  const participantNames = meetingDetail?.participants
    .filter((participant) => participant.accessStatus === "ACTIVE")
    .map((participant) => displayParticipantName(participant))
    .slice(0, 4) ?? [];
  const participantCount = meetingDetail?.participants.filter((participant) => participant.accessStatus === "ACTIVE").length ?? 0;
  const joinDisabled =
    loading
    || Boolean(error)
    || !meetingDetail
    || meetingDetail.status === "CANCELED"
    || meetingDetail.status === "ENDED";

  function handleJoin() {
    sessionStorage.setItem(
      LIVE_PREJOIN_STORAGE_KEY,
      JSON.stringify({
        cameraEnabled: camOn,
        micEnabled: micOn
      })
    );
    navigate(`/spaces/${spaceId}/meetings/${meetingId}/live`);
  }

  if (loading) {
    return <LoadingState label="Loading prejoin..." />;
  }

  if (error instanceof ApiRequestError && error.status === 403) {
    return <PermissionDenied type="meeting" />;
  }

  if (error instanceof ApiRequestError && error.status === 404) {
    return (
      <EmptyState
        action={(
          <button
            className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
            onClick={() => navigate(`/spaces/${spaceId}/meetings`)}
            type="button"
          >
            Back to meetings
          </button>
        )}
        desc="The meeting may have been deleted or you may no longer have access."
        icon={<Video className="w-5 h-5" />}
        title="Meeting not found"
      />
    );
  }

  if (error) {
    return (
      <ErrorState
        desc={error.message}
        onRetry={() => { void loadMeeting(); }}
        title="Unable to open prejoin"
      />
    );
  }

  if (!meetingDetail) {
    return <LoadingState label="Loading prejoin..." />;
  }

  return (
    <div className="min-h-screen bg-[#09090B] flex items-center justify-center p-6">
      <div className="w-full max-w-4xl grid grid-cols-5 gap-8 items-center">
        {/* Video Preview */}
        <div className="col-span-3 space-y-4">
          <div className="aspect-video bg-zinc-900 rounded-2xl overflow-hidden relative border border-white/10 flex items-center justify-center">
            {camOn ? (
              <div className="w-full h-full bg-gradient-to-br from-zinc-800 to-zinc-900 flex items-center justify-center">
                <div className="w-20 h-20 rounded-full bg-zinc-700 flex items-center justify-center text-3xl font-bold text-white">A</div>
              </div>
            ) : (
              <div className="flex flex-col items-center gap-3 text-zinc-500">
                <VideoOff className="w-10 h-10" />
                <span className="text-sm">Camera is off</span>
              </div>
            )}
            {/* Name tag */}
            <div className="absolute bottom-4 left-4 bg-black/60 backdrop-blur-sm text-white text-xs px-3 py-1.5 rounded-full font-medium">
              {name} (You)
            </div>
            {/* Controls */}
            <div className="absolute bottom-4 right-4 flex gap-2">
              <button
                onClick={() => setMicOn(p => !p)}
                className={`w-10 h-10 rounded-full flex items-center justify-center transition-colors ${micOn ? "bg-white/10 hover:bg-white/20 text-white" : "bg-red-500 hover:bg-red-600 text-white"}`}
              >
                {micOn ? <Mic className="w-4 h-4" /> : <MicOff className="w-4 h-4" />}
              </button>
              <button
                onClick={() => setCamOn(p => !p)}
                className={`w-10 h-10 rounded-full flex items-center justify-center transition-colors ${camOn ? "bg-white/10 hover:bg-white/20 text-white" : "bg-red-500 hover:bg-red-600 text-white"}`}
              >
                {camOn ? <Video className="w-4 h-4" /> : <VideoOff className="w-4 h-4" />}
              </button>
            </div>
          </div>
          <div className="flex items-center gap-2 justify-center text-zinc-500 text-xs">
            <span className={`w-1.5 h-1.5 rounded-full ${micOn ? "bg-emerald-500" : "bg-red-500"}`}></span>
            {micOn ? "Microphone active" : "Microphone muted"}
            <span className="mx-2">·</span>
            <span className={`w-1.5 h-1.5 rounded-full ${camOn ? "bg-emerald-500" : "bg-red-500"}`}></span>
            {camOn ? "Camera active" : "Camera off"}
          </div>
        </div>

        {/* Info Panel */}
        <div className="col-span-2 space-y-6">
          <div>
            <div className="text-zinc-500 text-xs font-semibold uppercase tracking-wider mb-1">You are joining</div>
            <h1 className="text-white text-2xl font-bold leading-tight">
              {meetingDetail.title}
            </h1>
            <p className="text-zinc-400 text-sm mt-1">
              {formatMeetingSchedule(meetingDetail)}
            </p>
            {meetingDetail?.status ? (
              <p className="text-zinc-500 text-xs mt-2">현재 상태: {meetingDetail.status}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <div className="text-zinc-500 text-[10px] font-semibold uppercase tracking-wider">Participants ({participantCount})</div>
            <div className="flex -space-x-2">
              {(participantNames.length > 0 ? participantNames : [name]).map((participantName, idx) => (
                <div key={`${participantName}-${idx}`} className="w-8 h-8 rounded-full bg-zinc-700 border-2 border-zinc-900 flex items-center justify-center text-[10px] font-bold text-white">
                  {participantInitials(participantName)}
                </div>
              ))}
            </div>
            <p className="text-zinc-500 text-xs">
              {participantNames.length > 0
                ? participantNames.join(", ")
                : "등록된 참가자가 없습니다."}
            </p>
          </div>

          <div className="space-y-2">
            <div className="text-zinc-500 text-[10px] font-semibold uppercase tracking-wider">STT Recording</div>
            <div className="flex items-center gap-2 bg-zinc-800 rounded-lg px-3 py-2.5">
              <span className={`w-2 h-2 rounded-full ${meetingDetail.status === "ENDED" || meetingDetail.status === "CANCELED" ? "bg-zinc-500" : "bg-emerald-500 animate-pulse"}`}></span>
              <span className="text-zinc-300 text-xs">
                {meetingDetail.status === "ENDED"
                  ? "This meeting has ended. Review transcript and report from the meeting workspace."
                  : meetingDetail.status === "CANCELED"
                    ? "This meeting was canceled."
                    : "Realtime transcription will start after the room is connected."}
              </span>
            </div>
          </div>

          <button
            className={`w-full py-3 rounded-xl font-bold text-base transition-colors shadow-lg shadow-primary/20 ${joinDisabled ? "bg-zinc-700 text-zinc-400 cursor-not-allowed" : "bg-primary text-white hover:bg-primary/90"}`}
            disabled={joinDisabled}
            onClick={handleJoin}
          >
            {meetingDetail.status === "ENDED"
              ? "Meeting ended"
              : meetingDetail.status === "CANCELED"
                ? "Meeting canceled"
                : "Join Now"}
          </button>
          <button
            onClick={() => navigate(-1)}
            className="w-full py-2 text-zinc-500 hover:text-zinc-300 text-sm transition-colors"
          >
            Go back
          </button>
        </div>
      </div>
    </div>
  );
};

// 12. Live Meeting & STT
const LiveMeeting = () => {
  const navigate = useNavigate();
  const { session } = useAuthState();
  const { spaceId = "", meetingId = "" } = useParams();
  const authSession = session as AuthSession;
  const [micOn, setMicOn] = useState(() => {
    const saved = sessionStorage.getItem(LIVE_PREJOIN_STORAGE_KEY);
    if (!saved) {
      return true;
    }
    try {
      return JSON.parse(saved).micEnabled ?? true;
    } catch {
      return true;
    }
  });
  const [camOn, setCamOn] = useState(() => {
    const saved = sessionStorage.getItem(LIVE_PREJOIN_STORAGE_KEY);
    if (!saved) {
      return true;
    }
    try {
      return JSON.parse(saved).cameraEnabled ?? true;
    } catch {
      return true;
    }
  });
  const [elapsed, setElapsed] = useState(0);
  const [meetingDetail, setMeetingDetail] = useState<MeetingDetailResponse | null>(null);
  const [meetingLoading, setMeetingLoading] = useState(true);
  const [meetingError, setMeetingError] = useState<Error | null>(null);
  const [roomReady, setRoomReady] = useState(false);
  const [roomError, setRoomError] = useState<Error | null>(null);
  const [connectionStateLabel, setConnectionStateLabel] = useState("Connecting");
  const [participants, setParticipants] = useState<LiveParticipantCard[]>([]);
  const [activeSpeakerSid, setActiveSpeakerSid] = useState<string | null>(null);
  const [dictionaryTerms, setDictionaryTerms] = useState<DomainTerm[]>([]);
  const [selectedDictionaryTerm, setSelectedDictionaryTerm] = useState<DomainTerm | null>(null);
  const [chatMessages, setChatMessages] = useState<Array<{ id: string; sender: string; text: string; time: string; local?: boolean }>>([]);
  const [chatDraft, setChatDraft] = useState("");
  const [audioInputDevices, setAudioInputDevices] = useState<MediaDeviceInfo[]>([]);
  const [videoInputDevices, setVideoInputDevices] = useState<MediaDeviceInfo[]>([]);
  const [selectedAudioInput, setSelectedAudioInput] = useState("");
  const [selectedVideoInput, setSelectedVideoInput] = useState("");
  const [remoteVolumes, setRemoteVolumes] = useState<Record<string, number>>({});
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [micVolume, setMicVolume] = useState(1);
  const [transcriptStatus, setTranscriptStatus] = useState<"PENDING" | "PROCESSING" | "COMPLETED" | "FAILED">("PENDING");
  const [transcriptError, setTranscriptError] = useState("");
  const [roomRetrySeed, setRoomRetrySeed] = useState(0);
  const [sttState, setSttState] = useState<"idle" | "starting" | "active" | "failed">("idle");
  const [ending, setEnding] = useState(false);
  const roomRef = useRef<Room | null>(null);
  const sttSessionIdRef = useRef<string | null>(null);
  const sttStartedRef = useRef(false);
  const stopSttOnUnmountRef = useRef(true);
  const meetingDetailRef = useRef<MeetingDetailResponse | null>(null);
  const liveStartedAtRef = useRef(Date.now());
  const initialDevicePreferencesRef = useRef({ micOn, camOn });
  const currentUserName = authSession.user.displayName?.trim() || "MeetingMind User";

  useEffect(() => {
    if (!spaceId || !session) return;
    void fetchDomainTerms(authSession, spaceId, { status: "ACTIVE" })
      .then((response) => setDictionaryTerms(response.terms))
      .catch(() => setDictionaryTerms([]));
  }, [authSession, session, spaceId]);

  useEffect(() => {
    if (!roomReady || !navigator.mediaDevices?.enumerateDevices) return;
    void navigator.mediaDevices.enumerateDevices().then((devices) => {
      setAudioInputDevices(devices.filter((device) => device.kind === "audioinput"));
      setVideoInputDevices(devices.filter((device) => device.kind === "videoinput"));
    }).catch(() => {});
  }, [roomReady]);

  useEffect(() => {
    let active = true;
    setMeetingLoading(true);
    setMeetingError(null);

    fetchMeetingDetail(authSession, meetingId)
      .then((detail) => {
        if (!active) {
          return;
        }
        setMeetingDetail(detail);
        if (detail.startedAt) {
          const startedAt = new Date(detail.startedAt).getTime();
          if (!Number.isNaN(startedAt)) {
            liveStartedAtRef.current = startedAt;
            setElapsed(Math.max(0, Math.floor((Date.now() - startedAt) / 1000)));
          }
        }
      })
      .catch((cause) => {
        if (!active) {
          return;
        }
        setMeetingDetail(null);
        setMeetingError(cause instanceof Error ? cause : new Error("회의 정보를 불러오지 못했습니다."));
      })
      .finally(() => {
        if (active) {
          setMeetingLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [authSession, meetingId]);

  useEffect(() => {
    meetingDetailRef.current = meetingDetail;
  }, [meetingDetail]);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      setElapsed(Math.max(0, Math.floor((Date.now() - liveStartedAtRef.current) / 1000)));
    }, 1000);
    return () => window.clearInterval(intervalId);
  }, []);

  useEffect(() => {
    if (meetingLoading || meetingError || !meetingDetailRef.current) {
      return;
    }

    let mounted = true;
    const syncSnapshot = (room: Room) => {
      if (!mounted) {
        return;
      }
      const nextParticipants = buildRoomParticipantCards(
        room,
        meetingDetailRef.current?.participants ?? [],
        currentUserName
      );
      setParticipants(nextParticipants);
      setActiveSpeakerSid(room.activeSpeakers[0]?.sid ?? null);
      setMicOn(room.localParticipant.isMicrophoneEnabled);
      setCamOn(room.localParticipant.isCameraEnabled);
      setConnectionStateLabel(
        room.state === ConnectionState.Connected
          ? "Connected"
          : room.state === ConnectionState.Connecting
            ? "Connecting"
            : room.state === ConnectionState.Reconnecting
              ? "Reconnecting"
              : "Disconnected"
      );
    };

    const initializeRoom = async () => {
      stopSttOnUnmountRef.current = true;
      setRoomReady(false);
      setRoomError(null);
      setTranscriptError("");
      setTranscriptStatus("PENDING");
      setSttState("idle");
      setConnectionStateLabel("Connecting");
      try {
        const connection = await fetchMeetingLiveKitToken(authSession, meetingId);
        if (!mounted) {
          return;
        }
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
          .on(RoomEvent.ActiveSpeakersChanged, () => syncSnapshot(room))
          .on(RoomEvent.DataReceived, (payload, participant) => {
            try {
              const data = JSON.parse(new TextDecoder().decode(payload)) as { type?: string; text?: string };
              const text = data.text?.trim();
              if (data.type !== "meeting-chat" || !text) return;
              setChatMessages((previous) => [...previous, {
                id: `${participant?.identity ?? "remote"}-${Date.now()}`,
                sender: participant?.name || participant?.identity || "Participant",
                text,
                time: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
              }]);
            } catch {
              // Ignore data messages that are not MeetingMind chat payloads.
            }
          })
          .on(RoomEvent.LocalTrackPublished, async (publication) => {
            syncSnapshot(room);
            if (sttStartedRef.current || publication.source !== Track.Source.Microphone || !publication.trackSid) {
              return;
            }
            sttStartedRef.current = true;
            setSttState("starting");
            try {
              const response = await startMeetingTranscription(authSession, meetingId, {
                mode: "realtime",
                trackId: publication.trackSid
              });
              if (!mounted) {
                void stopMeetingTranscription(authSession, meetingId, response.sessionId).catch(() => {});
                return;
              }
              sttSessionIdRef.current = response.sessionId;
              sessionStorage.setItem(sttSessionStorageKey(meetingId), response.sessionId);
              setSttState("active");
            } catch (cause) {
              if (isTranscriptionAlreadyProcessingError(cause)) {
                sttSessionIdRef.current = sessionStorage.getItem(sttSessionStorageKey(meetingId));
                setSttState("active");
                setTranscriptError("");
                return;
              }
              sttStartedRef.current = false;
              sttSessionIdRef.current = null;
              setSttState("failed");
              setTranscriptError(sttStartErrorMessage(cause));
            }
          })
          .on(RoomEvent.Disconnected, () => syncSnapshot(room));

        await room.connect(connection.serverUrl, connection.participantToken);
        const liveMeetingDetail = meetingDetailRef.current;
        if (liveMeetingDetail?.myRole === "HOST" && liveMeetingDetail.status === "SCHEDULED") {
          try {
            await updateMeeting(authSession, meetingId, { status: "IN_PROGRESS" });
            if (mounted) {
              const startedAt = new Date().toISOString();
              liveStartedAtRef.current = Date.now();
              setElapsed(0);
              setMeetingDetail((current) =>
                current
                  ? {
                      ...current,
                      status: "IN_PROGRESS",
                      startedAt
                    }
                  : current
              );
            }
          } catch (cause) {
            if (mounted) {
              setRoomError(cause instanceof Error ? cause : new Error("회의 상태를 진행 중으로 변경하지 못했습니다."));
            }
          }
        }
        try {
          await room.localParticipant.setCameraEnabled(initialDevicePreferencesRef.current.camOn);
        } catch (cause) {
          console.warn("[LiveMeeting] camera setup failed", cause);
        }
        try {
          await room.localParticipant.setMicrophoneEnabled(initialDevicePreferencesRef.current.micOn);
        } catch (cause) {
          console.warn("[LiveMeeting] microphone setup failed", cause);
        }
        const existingSttSessionId = sessionStorage.getItem(sttSessionStorageKey(meetingId));
        if (existingSttSessionId) {
          sttSessionIdRef.current = existingSttSessionId;
          sttStartedRef.current = true;
          setSttState("active");
          setTranscriptError("");
        }
        syncSnapshot(room);
        if (mounted) {
          setRoomReady(true);
        }
      } catch (cause) {
        if (!mounted) {
          return;
        }
        setRoomError(cause instanceof Error ? cause : new Error("실시간 회의실 연결에 실패했습니다."));
        setConnectionStateLabel("Connection failed");
      }
    };

    // React StrictMode의 첫 effect setup은 즉시 cleanup된다. 연결을 한 tick 뒤로 미뤄
    // 취소 가능한 첫 setup이 LiveKit/STT 세션을 만들지 않도록 한다.
    const initializeTimer = window.setTimeout(() => {
      void initializeRoom();
    }, 0);

    return () => {
      mounted = false;
      window.clearTimeout(initializeTimer);
      const sessionId = sttSessionIdRef.current ?? sessionStorage.getItem(sttSessionStorageKey(meetingId));
      sttSessionIdRef.current = null;
      sttStartedRef.current = false;
      const disconnectRoom = () => {
        const room = roomRef.current;
        roomRef.current = null;
        if (room) {
          void room.disconnect();
        }
      };
      if (sessionId && stopSttOnUnmountRef.current) {
        void stopMeetingTranscription(authSession, meetingId, sessionId)
          .then(() => sessionStorage.removeItem(sttSessionStorageKey(meetingId)))
          .catch(() => {})
          .finally(disconnectRoom);
        return;
      }
      disconnectRoom();
    };
  }, [authSession, currentUserName, meetingError, meetingId, meetingLoading, roomRetrySeed]);

  // 전사 조회는 features/transcription 훅이 담당한다.
  // MeetingTranscript 화면과 queryKey를 공유하므로 요청이 중복되지 않는다.
  const dialogueQuery = useMeetingDialogueQuery(meetingId, {
    enabled: Boolean(authSession) && roomReady && Boolean(meetingDetail)
  });

  useEffect(() => {
    const status = dialogueQuery.status;
    if (!status) {
      return;
    }
    setTranscriptStatus(status);
    if (status === "PROCESSING" || status === "COMPLETED") {
      setSttState("active");
    } else if (status === "FAILED") {
      setSttState("failed");
    }
  }, [dialogueQuery.status]);

  useEffect(() => {
    if (dialogueQuery.error) {
      setTranscriptError(
        dialogueQuery.error instanceof Error
          ? dialogueQuery.error.message
          : "실시간 자막을 불러오지 못했습니다."
      );
    } else if (dialogueQuery.data && dialogueQuery.data.status !== "FAILED") {
      setTranscriptError("");
    }
  }, [dialogueQuery.data, dialogueQuery.error]);

  const transcriptRows: LiveTranscriptRow[] = useMemo(
    () =>
      dialogueQuery.entries.map((entry) => ({
        key: entry.key,
        speaker: entry.speakerName,
        initials: participantInitials(entry.speakerName),
        time: entry.isPartial ? "LIVE" : formatTranscriptTime(entry.startMs),
        text: entry.text
      })),
    [dialogueQuery.entries]
  );

  const stageParticipant =
    participants.find((participant) => participant.sid && participant.sid === activeSpeakerSid && participant.cameraPublication?.videoTrack) ??
    participants.find((participant) => participant.isConnected && participant.cameraPublication?.videoTrack) ??
    participants.find((participant) => participant.isConnected) ??
    null;

  async function handleToggleMicrophone() {
    const room = roomRef.current;
    if (!room) {
      return;
    }
    try {
      await room.localParticipant.setMicrophoneEnabled(!room.localParticipant.isMicrophoneEnabled);
      setMicOn(room.localParticipant.isMicrophoneEnabled);
      setParticipants(buildRoomParticipantCards(room, meetingDetail?.participants ?? [], currentUserName));
    } catch (cause) {
      setRoomError(
        cause instanceof Error && cause.message
          ? new Error(`마이크 상태를 변경하지 못했습니다. ${cause.message}`)
          : new Error("마이크 상태를 변경하지 못했습니다.")
      );
    }
  }

  async function handleToggleCamera() {
    const room = roomRef.current;
    if (!room) {
      return;
    }
    try {
      await room.localParticipant.setCameraEnabled(!room.localParticipant.isCameraEnabled);
      setCamOn(room.localParticipant.isCameraEnabled);
      setParticipants(buildRoomParticipantCards(room, meetingDetail?.participants ?? [], currentUserName));
    } catch (cause) {
      setRoomError(
        cause instanceof Error && cause.message
          ? new Error(`카메라 상태를 변경하지 못했습니다. ${cause.message}`)
          : new Error("카메라 상태를 변경하지 못했습니다.")
      );
    }
  }

  async function handleAudioInputChange(deviceId: string) {
    const room = roomRef.current;
    if (!room || !deviceId) return;
    try {
      await room.localParticipant.setMicrophoneEnabled(true, { deviceId });
      setSelectedAudioInput(deviceId);
      setMicOn(true);
    } catch (cause) {
      setRoomError(cause instanceof Error ? new Error(`마이크 장치를 변경하지 못했습니다. ${cause.message}`) : new Error("마이크 장치를 변경하지 못했습니다."));
    }
  }

  async function handleVideoInputChange(deviceId: string) {
    const room = roomRef.current;
    if (!room || !deviceId) return;
    try {
      await room.localParticipant.setCameraEnabled(true, { deviceId });
      setSelectedVideoInput(deviceId);
      setCamOn(true);
    } catch (cause) {
      setRoomError(cause instanceof Error ? new Error(`카메라를 변경하지 못했습니다. ${cause.message}`) : new Error("카메라를 변경하지 못했습니다."));
    }
  }

  async function handleLeave() {
    if (ending) {
      return;
    }
    setEnding(true);
    stopSttOnUnmountRef.current = false;
    const room = roomRef.current;
    if (room) {
      await room.disconnect();
      roomRef.current = null;
    }
    navigate(`/spaces/${spaceId}/meetings/${meetingId}`);
  }

  async function handleEndMeeting() {
    if (ending) return;
    setEnding(true);
    const sessionId = sttSessionIdRef.current ?? sessionStorage.getItem(sttSessionStorageKey(meetingId));
    try {
      if (sessionId || sttState === "active") {
        const response = sessionId
          ? await stopMeetingTranscription(authSession, meetingId, sessionId)
          : await stopActiveMeetingTranscription(authSession, meetingId);
        setTranscriptStatus(response.transcriptStatus);
        sessionStorage.removeItem(sttSessionStorageKey(meetingId));
      }
      stopSttOnUnmountRef.current = false;
      await roomRef.current?.disconnect();
      roomRef.current = null;
      await updateMeeting(authSession, meetingId, { status: "ENDED" });
      navigate(`/spaces/${spaceId}/meetings/${meetingId}`);
    } catch (cause) {
      setRoomError(cause instanceof Error ? cause : new Error("회의를 종료하지 못했습니다."));
      setEnding(false);
    }
  }

  async function handleSendLiveChat(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const text = chatDraft.trim();
    const room = roomRef.current;
    if (!text || !room || room.state !== ConnectionState.Connected) return;
    try {
      await room.localParticipant.publishData(
        new TextEncoder().encode(JSON.stringify({ type: "meeting-chat", text })),
        { reliable: true }
      );
      setChatMessages((previous) => [...previous, {
        id: `local-${Date.now()}`,
        sender: currentUserName,
        text,
        time: new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }),
        local: true
      }]);
      setChatDraft("");
    } catch {
      setRoomError(new Error("채팅을 전송하지 못했습니다. 잠시 후 다시 시도해주세요."));
    }
  }

  if (meetingLoading) {
    return <LoadingState label="Loading live room..." />;
  }

  if (meetingError instanceof ApiRequestError && meetingError.status === 403) {
    return <PermissionDenied type="meeting" />;
  }

  if (meetingError instanceof ApiRequestError && meetingError.status === 404) {
    return (
      <EmptyState
        action={(
          <button
            className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
            onClick={() => navigate(`/spaces/${spaceId}/meetings`)}
            type="button"
          >
            Back to meetings
          </button>
        )}
        desc="The meeting may have been deleted or you may no longer have access."
        icon={<Video className="w-5 h-5" />}
        title="Meeting not found"
      />
    );
  }

  if (meetingError || !meetingDetail) {
    return (
      <ErrorState
        desc={meetingError?.message || "회의 정보를 확인하지 못했습니다."}
        onRetry={() => navigate(`/spaces/${spaceId}/meetings/${meetingId}`)}
        title="Unable to open the meeting room"
      />
    );
  }

  if (meetingDetail.status === "ENDED" || meetingDetail.status === "CANCELED") {
    return (
      <EmptyState
        action={(
          <button
            className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
            onClick={() => navigate(`/spaces/${spaceId}/meetings/${meetingId}`)}
            type="button"
          >
            Back to meeting
          </button>
        )}
        desc={meetingDetail.status === "ENDED"
          ? "The live room is closed because this meeting has ended. Review the transcript, report, or tasks from the meeting workspace."
          : "The live room is unavailable because this meeting was canceled."}
        icon={<Video className="w-5 h-5" />}
        title={meetingDetail.status === "ENDED" ? "Meeting has ended" : "Meeting was canceled"}
      />
    );
  }

  if (roomError instanceof ApiRequestError && roomError.status === 403) {
    return <PermissionDenied type="meeting" />;
  }

  if (roomError instanceof ApiRequestError && roomError.status === 404) {
    return (
      <EmptyState
        action={(
          <button
            className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
            onClick={() => navigate(`/spaces/${spaceId}/meetings/${meetingId}`)}
            type="button"
          >
            Back to meeting
          </button>
        )}
        desc="The live meeting room is no longer available."
        icon={<Video className="w-5 h-5" />}
        title="Live room unavailable"
      />
    );
  }

  if (roomError && !roomReady) {
    return (
      <ErrorState
        desc={roomError.message}
        onRetry={() => setRoomRetrySeed((current) => current + 1)}
        title="Couldn't connect to the meeting room"
      />
    );
  }

  return (
    <div className="h-screen bg-zinc-950 flex flex-col overflow-hidden">
      {/* Top Bar */}
      <div className="h-12 flex items-center justify-between px-6 bg-zinc-900 border-b border-white/5 shrink-0">
        <div className="flex items-center gap-3">
          <span className="text-white text-sm font-semibold truncate max-w-xs">{meetingDetail.title}</span>
          <span className="flex items-center gap-1.5 text-xs text-emerald-400 font-medium">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse"></span>
            LIVE · {formatLiveElapsed(elapsed)}
          </span>
          <span className="text-[11px] text-zinc-500">{connectionStateLabel}</span>
        </div>
        <div className="flex items-center gap-2 text-zinc-400 text-xs">
          <Mic className={`w-3.5 h-3.5 ${sttState === "active" ? "text-emerald-400" : sttState === "failed" ? "text-red-400" : "text-zinc-500"}`} />
          <span className={sttState === "active" ? "text-emerald-400" : sttState === "failed" ? "text-red-400" : "text-zinc-400"}>
            {sttState === "active" ? "STT Active" : sttState === "starting" ? "STT Starting" : sttState === "failed" ? "STT Failed" : "STT Waiting"}
          </span>
        </div>
      </div>

      <div className="flex flex-1 min-h-0">
        {/* Video Grid */}
        <div className="flex-1 flex flex-col min-w-0">
          <div className="live-video-grid flex-1 grid grid-cols-2 gap-2 p-3">
            {participants.map((participant) => (
              <div key={participant.key} className={`relative bg-zinc-900 rounded-xl overflow-hidden flex items-center justify-center border ${participant.sid === stageParticipant?.sid ? "border-emerald-500/60" : "border-white/5"}`}>
                {participant.cameraPublication?.videoTrack && participant.isCameraEnabled ? (
                  <VideoTrackSurface
                    className="h-full w-full object-cover"
                    mirror={participant.isLocal}
                    publication={participant.cameraPublication}
                  />
                ) : (
                  <div className="w-14 h-14 rounded-full bg-zinc-700 flex items-center justify-center text-xl font-bold text-white">{participant.initials}</div>
                )}
                {!participant.isConnected ? (
                  <div className="absolute inset-x-3 top-3 rounded-full bg-zinc-950/80 px-2 py-1 text-[10px] font-medium text-zinc-300">
                    Waiting to join
                  </div>
                ) : null}
                <div className="absolute bottom-3 left-3 flex items-center gap-2">
                  <span className="bg-black/60 backdrop-blur-sm text-white text-xs px-2 py-1 rounded-full font-medium">{participant.name}</span>
                  {participant.isConnected && participant.isMicrophoneEnabled ? <span className="w-5 h-5 bg-black/60 rounded-full flex items-center justify-center"><Mic className="w-2.5 h-2.5 text-emerald-400" /></span> : null}
                </div>
              </div>
            ))}
            {!participants.length ? (
              <div className="col-span-2 rounded-xl border border-dashed border-white/10 bg-zinc-900/80 flex items-center justify-center text-zinc-500 text-sm">
                {roomError?.message || "LiveKit 연결 대기 중입니다."}
              </div>
            ) : null}
          </div>

          {/* Controls */}
          <div className="h-16 flex items-center justify-center gap-3 bg-zinc-900 border-t border-white/5 shrink-0">
            <button onClick={() => void handleToggleMicrophone()} className={`w-11 h-11 rounded-full flex items-center justify-center transition-colors ${micOn ? "bg-zinc-700 hover:bg-zinc-600 text-white" : "bg-red-500 hover:bg-red-600 text-white"}`}>
              {micOn ? <Mic className="w-5 h-5" /> : <MicOff className="w-5 h-5" />}
            </button>
            <button onClick={() => void handleToggleCamera()} className={`w-11 h-11 rounded-full flex items-center justify-center transition-colors ${camOn ? "bg-zinc-700 hover:bg-zinc-600 text-white" : "bg-red-500 hover:bg-red-600 text-white"}`}>
              {camOn ? <Video className="w-5 h-5" /> : <VideoOff className="w-5 h-5" />}
            </button>
            <button
              onClick={() => void handleLeave()}
              disabled={ending}
              className="px-5 h-11 rounded-full bg-zinc-700 hover:bg-zinc-600 disabled:opacity-60 text-white text-sm font-bold transition-colors ml-4"
            >
              {ending ? "Leaving..." : "Leave"}
            </button>
            {meetingDetail?.myRole === "HOST" ? (
              <button onClick={() => void handleEndMeeting()} disabled={ending} className="px-5 h-11 rounded-full bg-red-500 hover:bg-red-600 disabled:opacity-60 text-white text-sm font-bold transition-colors">End meeting</button>
            ) : null}
            <div className="relative">
              <button aria-expanded={settingsOpen} aria-label="회의 장치 설정" className="w-11 h-11 rounded-full bg-zinc-700 hover:bg-zinc-600 text-white flex items-center justify-center" onClick={() => setSettingsOpen((open) => !open)} type="button">
                <Settings className="w-5 h-5" />
              </button>
              {settingsOpen ? (
                <div className="absolute bottom-14 right-0 z-20 w-72 rounded-xl border border-white/10 bg-zinc-800 p-3 text-left shadow-2xl">
                  <div className="mb-3 flex items-center justify-between">
                    <strong className="text-sm text-white">Device settings</strong>
                    <button aria-label="설정 닫기" className="text-zinc-400 hover:text-white" onClick={() => setSettingsOpen(false)} type="button"><X className="h-4 w-4" /></button>
                  </div>
                  <label className="mb-3 block text-[11px] text-zinc-400">Microphone
                    <select aria-label="마이크 선택" className="mt-1 w-full rounded-md border border-white/10 bg-zinc-900 px-2 py-2 text-xs text-zinc-200" onChange={(event) => void handleAudioInputChange(event.target.value)} value={selectedAudioInput}>
                      <option value="">Default microphone</option>
                      {audioInputDevices.map((device, index) => <option key={device.deviceId} value={device.deviceId}>{device.label || `Microphone ${index + 1}`}</option>)}
                    </select>
                  </label>
                  <label className="mb-3 block text-[11px] text-zinc-400">Camera
                    <select aria-label="카메라 선택" className="mt-1 w-full rounded-md border border-white/10 bg-zinc-900 px-2 py-2 text-xs text-zinc-200" onChange={(event) => void handleVideoInputChange(event.target.value)} value={selectedVideoInput}>
                      <option value="">Default camera</option>
                      {videoInputDevices.map((device, index) => <option key={device.deviceId} value={device.deviceId}>{device.label || `Camera ${index + 1}`}</option>)}
                    </select>
                  </label>
                  <label className="block text-[11px] text-zinc-400">Microphone level
                    <input aria-label="마이크 음량" className="mt-2 w-full accent-emerald-400" max="1" min="0" onChange={(event) => setMicVolume(Number(event.target.value))} step="0.05" type="range" value={micVolume} />
                    <span className="mt-1 block text-right text-[10px] text-zinc-500">{Math.round(micVolume * 100)}%</span>
                  </label>
                  <p className="mt-2 text-[10px] leading-relaxed text-zinc-500">입력 레벨은 현재 장치 미리보기 설정입니다. 실제 송출 gain은 오디오 처리 모듈 연결 후 적용됩니다.</p>
                </div>
              ) : null}
            </div>
          </div>
          {roomError ? <div className="px-4 py-2 text-xs text-red-300 bg-red-500/10 border-t border-red-500/20">{roomError.message}</div> : null}
        </div>

        {/* Live communication panels: transcript and chat stay visible side by side. */}
        <div className="live-communication-panels shrink-0">
          <section className="live-communication-pane">
            <div className="border-b border-white/5 px-3 py-3 shrink-0 text-xs font-semibold text-white">
              Live Transcript
            </div>
          {selectedDictionaryTerm ? (
            <div className="live-term-definition" role="status">
              <div className="flex items-center justify-between gap-2">
                <strong>{selectedDictionaryTerm.term}</strong>
                <button aria-label="용어 설명 닫기" onClick={() => setSelectedDictionaryTerm(null)} type="button">닫기</button>
              </div>
              <p>{selectedDictionaryTerm.definition}</p>
            </div>
          ) : null}
          <div className="flex-1 overflow-y-auto p-2 space-y-2 custom-scrollbar">
            {transcriptRows.map((row) => (
              <div key={row.key} className="flex gap-2.5">
                <div className="w-5 h-5 rounded-full bg-zinc-700 flex items-center justify-center text-[8px] font-bold text-white shrink-0 mt-0.5">{row.initials}</div>
                <div>
                  <div className="flex items-baseline gap-2 mb-0.5">
                    <span className="text-[10px] font-semibold text-zinc-300">{row.speaker}</span>
                    <span className="text-[9px] text-zinc-600 font-mono">{row.time}</span>
                  </div>
                  <p className="text-[11px] text-zinc-400 leading-relaxed">{highlightTranscriptTerms(row.text, dictionaryTerms, (term) => setSelectedDictionaryTerm(dictionaryTerms.find((candidate) => candidate.term === term.term) ?? null))}</p>
                </div>
              </div>
            ))}
            {transcriptStatus === "PROCESSING" ? (
              <div className="flex gap-2.5 items-center">
                <div className="w-6 h-6 rounded-full bg-zinc-700 flex items-center justify-center text-[9px] font-bold text-white shrink-0">STT</div>
                <div className="flex gap-1 py-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-zinc-500 animate-bounce" style={{ animationDelay: "0ms" }}></span>
                  <span className="w-1.5 h-1.5 rounded-full bg-zinc-500 animate-bounce" style={{ animationDelay: "150ms" }}></span>
                  <span className="w-1.5 h-1.5 rounded-full bg-zinc-500 animate-bounce" style={{ animationDelay: "300ms" }}></span>
                </div>
              </div>
            ) : null}
            {!transcriptRows.length && transcriptStatus !== "PROCESSING" ? (
              <div className="rounded-lg border border-white/5 bg-zinc-950/60 px-3 py-3 text-xs text-zinc-500">
                {transcriptStatus === "FAILED"
                  ? transcriptError || "전사 처리에 실패했습니다."
                  : "실시간 자막을 기다리는 중입니다."}
              </div>
            ) : null}
            {transcriptError ? (
              <div className="rounded-lg border border-red-500/20 bg-red-500/10 px-3 py-3 text-xs text-red-200">
                {transcriptError}
              </div>
            ) : null}
          </div>
          </section>
          <section className="live-communication-pane">
            <div className="border-b border-white/5 px-3 py-3 shrink-0 text-xs font-semibold text-white">
              Chat
            </div>
            <div className="border-b border-white/5 px-2 py-2">
              <div className="mb-2 text-[10px] font-semibold uppercase tracking-wide text-zinc-500">Participants</div>
              <div className="space-y-1.5">
                {participants.filter((participant) => participant.isConnected && !participant.isLocal).map((participant) => (
                  <label className="flex items-center gap-2 text-[10px] text-zinc-300" key={`volume-${participant.key}`}>
                    <span className="min-w-0 flex-1 truncate">{participant.name}</span>
                    <input aria-label={`${participant.name} volume`} className="w-20 accent-emerald-400" max="1" min="0" onChange={(event) => setRemoteVolumes((current) => ({ ...current, [participant.key]: Number(event.target.value) }))} step="0.05" type="range" value={remoteVolumes[participant.key] ?? 1} />
                  </label>
                ))}
                {!participants.some((participant) => participant.isConnected && !participant.isLocal) ? <p className="text-[10px] text-zinc-600">No other participants</p> : null}
              </div>
            </div>
            <div className="live-chat-panel">
              <div className="live-chat-list" aria-live="polite">
                {chatMessages.length === 0 ? <p className="text-xs text-zinc-500 text-center py-6">참가자와 실시간으로 메시지를 주고받을 수 있습니다.</p> : chatMessages.map((message) => (
                  <article className={`live-chat-message ${message.local ? "is-local" : ""}`} key={message.id}>
                    <div><strong>{message.sender}</strong><time>{message.time}</time></div>
                    <p>{message.text}</p>
                  </article>
                ))}
              </div>
              <form className="live-chat-form" onSubmit={handleSendLiveChat}>
                <input aria-label="회의 채팅 입력" onChange={(event) => setChatDraft(event.target.value)} placeholder="메시지를 입력하세요" type="text" value={chatDraft} />
                <button disabled={!chatDraft.trim() || !roomReady} type="submit">전송</button>
              </form>
            </div>
          </section>
        </div>
      </div>
      {participants
        .filter((participant) => participant.isConnected && !participant.isLocal)
        .map((participant) => (
          <AudioTrackSurface key={`audio-${participant.key}`} publication={participant.audioPublication} volume={remoteVolumes[participant.key] ?? 1} />
        ))}
    </div>
  );
};

// 13. Project Settings
const ProjectSettings = () => {
  const { session } = useAuthState();
  const { locale } = useAppPreferences();
  const authSession = session as AuthSession;
  const navigate = useNavigate();
  const { spaceId = "" } = useParams<{ spaceId: string }>();
  const { spaceDetail, spaceLoading, spaceError, reloadSpace } = useOutletContext<ShellOutletContext>();
  const [projectName, setProjectName] = useState("");
  const [description, setDescription] = useState("");
  const [imageUrl, setImageUrl] = useState("");
  const [savePending, setSavePending] = useState(false);
  const [saveMessage, setSaveMessage] = useState("");
  const [mutationError, setMutationError] = useState("");
  const [deletePending, setDeletePending] = useState(false);
  const [showDanger, setShowDanger] = useState(false);
  const [leavePending, setLeavePending] = useState(false);
  const korean = locale === "ko";

  useEffect(() => {
    setProjectName(spaceDetail?.name ?? "");
    setDescription(spaceDetail?.description ?? "");
    setImageUrl(spaceDetail?.imageUrl ?? "");
  }, [spaceDetail?.description, spaceDetail?.imageUrl, spaceDetail?.name]);

  const canManage = spaceDetail?.role === "OWNER" || spaceDetail?.role === "ADMIN";
  const canDelete = spaceDetail?.role === "OWNER";

  async function handleSave() {
    if (!spaceDetail || !canManage || !projectName.trim() || savePending) {
      return;
    }

    setSavePending(true);
    setSaveMessage("");
    setMutationError("");
    try {
      await updateSpace(authSession, spaceDetail.id, {
        name: projectName.trim(),
        description: description.trim() || null,
        imageUrl: imageUrl.trim() || null
      });
      await reloadSpace();
      setSaveMessage("프로젝트 정보를 저장했습니다.");
    } catch (cause) {
      if (cause instanceof ApiRequestError) {
        if (cause.status === 403) {
          setMutationError("프로젝트 수정 권한이 없습니다.");
        } else if (cause.status === 404) {
          setMutationError("프로젝트를 찾을 수 없습니다.");
        } else {
          setMutationError(cause.message);
        }
      } else {
        setMutationError(cause instanceof Error ? cause.message : "프로젝트 정보를 저장하지 못했습니다.");
      }
    } finally {
      setSavePending(false);
    }
  }

  async function handleImageUpload(file: File) {
    if (!spaceDetail || !canManage || savePending || deletePending) return;
    setSavePending(true);
    setMutationError("");
    try {
      setImageUrl(await uploadSpaceImage(authSession, spaceDetail.id, file));
      setSaveMessage("이미지를 업로드했습니다. 변경사항 저장을 눌러 적용하세요.");
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "프로젝트 이미지를 업로드하지 못했습니다.");
    } finally {
      setSavePending(false);
    }
  }

  async function handleDelete() {
    if (!spaceDetail || !canDelete || deletePending) {
      return;
    }

    setDeletePending(true);
    setMutationError("");
    setSaveMessage("");
    try {
      await deleteSpace(authSession, spaceDetail.id);
      navigate("/spaces", { replace: true });
    } catch (cause) {
      if (cause instanceof ApiRequestError) {
        if (cause.status === 403) {
          setMutationError("프로젝트 삭제 권한이 없습니다.");
        } else if (cause.status === 404) {
          setMutationError("이미 삭제되었거나 접근할 수 없는 프로젝트입니다.");
        } else {
          setMutationError(cause.message);
        }
      } else {
        setMutationError(cause instanceof Error ? cause.message : "프로젝트를 삭제하지 못했습니다.");
      }
      setDeletePending(false);
    }
  }

  async function handleLeave() {
    if (!spaceDetail || leavePending) return;
    if (spaceDetail.role === "OWNER") {
      setMutationError("OWNER는 소유권을 이양한 뒤에만 이 스페이스에서 나갈 수 있습니다.");
      return;
    }
    if (!window.confirm("이 프로젝트 스페이스에서 나가시겠습니까?")) return;
    setLeavePending(true);
    setMutationError("");
    try {
      await leaveSpace(authSession, spaceDetail.id);
      navigate("/spaces", { replace: true });
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "프로젝트에서 나가지 못했습니다.");
      setLeavePending(false);
    }
  }

  if (spaceLoading) {
    return <LoadingState label="Loading project settings..." />;
  }

  if (!spaceId || spaceError || !spaceDetail) {
    return (
      <ErrorState
        desc={spaceError?.message || "프로젝트 설정을 불러오지 못했습니다."}
        onRetry={() => void reloadSpace()}
        title="Unable to load project settings"
      />
    );
  }

  return (
    <div className="w-full max-w-5xl mx-auto p-8 space-y-8">
      <h1 className="text-xl font-bold">{korean ? "프로젝트 설정" : "Project Settings"}</h1>
      <div className="flex flex-wrap items-center gap-2">
        <button className="px-4 py-2 rounded-md border border-border bg-background text-sm font-semibold hover:bg-muted transition-colors" onClick={() => navigate(`/spaces/${encodeURIComponent(spaceDetail.id)}/meetings`)} type="button">
          {korean ? "회의 관리" : "Meeting management"}
        </button>
        <button className="px-4 py-2 rounded-md border border-red-300 bg-white text-sm font-semibold text-red-700 hover:bg-red-50 transition-colors disabled:opacity-60" disabled={leavePending} onClick={() => void handleLeave()} type="button">
          {leavePending ? (korean ? "나가는 중..." : "Leaving...") : (korean ? "스페이스 나가기" : "Leave this space")}
        </button>
      </div>

      {/* General */}
      <section className="bg-card border border-border rounded-lg overflow-hidden">
        <div className="px-5 py-4 border-b border-border">
          <h2 className="font-semibold text-sm">{korean ? "일반" : "General"}</h2>
        </div>
        <div className="p-5 space-y-4">
          <div>
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">{korean ? "대표 이미지" : "Workspace Image"}</label>
            <div className="flex flex-wrap items-center gap-3">
              {imageUrl ? <img alt="Workspace preview" className="h-12 w-12 rounded-md border border-border object-cover" src={imageUrl} /> : <div className="h-12 w-12 rounded-md border border-dashed border-border bg-muted" />}
              <label className="cursor-pointer rounded-md border border-border bg-background px-3 py-2 text-sm font-semibold hover:bg-muted disabled:opacity-60">
                {korean ? "이미지 업로드" : "Upload image"}
                <input accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={!canManage || savePending || deletePending} onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void handleImageUpload(file);
                  event.target.value = "";
                }} type="file" />
              </label>
              {imageUrl ? <button className="text-sm text-muted-foreground hover:text-foreground" disabled={!canManage || savePending || deletePending} onClick={() => setImageUrl("")} type="button">{korean ? "제거" : "Remove"}</button> : null}
            </div>
          </div>
          <div>
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">{korean ? "프로젝트 이름" : "Project Name"}</label>
            <input
              className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 disabled:opacity-60"
              disabled={!canManage || savePending || deletePending}
              value={projectName}
              onChange={(e) => setProjectName(e.target.value)}
            />
          </div>
          <div>
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">{korean ? "설명" : "Description"}</label>
            <textarea
              className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 resize-none disabled:opacity-60"
              disabled={!canManage || savePending || deletePending}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
            />
          </div>
          {!canManage ? (
            <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
              프로젝트 수정은 OWNER 또는 ADMIN만 할 수 있습니다.
            </p>
          ) : null}
          {mutationError ? (
            <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">
              {mutationError}
            </p>
          ) : null}
          {saveMessage ? (
            <p className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-md px-3 py-2">
              {saveMessage}
            </p>
          ) : null}
          <button
            className="px-4 py-2 bg-foreground text-background rounded-md text-sm font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60"
            disabled={!canManage || savePending || deletePending || !projectName.trim()}
            onClick={() => void handleSave()}
            type="button"
          >
            {savePending ? (korean ? "저장 중..." : "Saving...") : (korean ? "변경사항 저장" : "Save Changes")}
          </button>
        </div>
      </section>

      {/* Danger Zone */}
      <section className="bg-card border border-red-200 rounded-lg overflow-hidden">
        <button
          onClick={() => setShowDanger(p => !p)}
          className="w-full px-5 py-4 flex items-center justify-between text-left hover:bg-red-50/50 transition-colors"
        >
          <h2 className="font-semibold text-sm text-red-600">Danger Zone</h2>
          <svg width="12" height="12" viewBox="0 0 10 10" className={`text-red-400 transition-transform ${showDanger ? "rotate-90" : ""}`}>
            <path d="M3 2L7 5L3 8" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round"/>
          </svg>
        </button>
        {showDanger && (
          <div className="px-5 pb-5 space-y-3 border-t border-red-100">
            <div className="mt-4 flex items-center justify-between gap-4 p-4 rounded-lg bg-red-50 border border-red-200">
              <div>
                <p className="text-sm font-medium text-red-800">Archive this space</p>
                <p className="text-xs text-red-600 mt-0.5">보관 전용 API는 아직 없습니다. 현재 서버는 삭제만 지원합니다.</p>
              </div>
              <span className="shrink-0 rounded-md border border-red-200 bg-white px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wider text-red-500">
                Backend pending
              </span>
            </div>
            <div className="flex items-center justify-between gap-4 p-4 rounded-lg bg-red-50 border border-red-200">
              <div>
                <p className="text-sm font-medium text-red-800">Delete this space</p>
                <p className="text-xs text-red-600 mt-0.5">Permanently deletes all meetings, reports, and knowledge. Cannot be undone.</p>
              </div>
              <button
                className="px-3 py-1.5 rounded-md bg-red-600 text-white text-xs font-semibold hover:bg-red-700 transition-colors shrink-0 disabled:opacity-60"
                disabled={!canDelete || deletePending}
                onClick={() => void handleDelete()}
                type="button"
              >
                {deletePending ? "Deleting..." : "Delete"}
              </button>
            </div>
            {!canDelete ? <p className="text-xs text-red-700">프로젝트 삭제는 OWNER만 할 수 있습니다.</p> : null}
          </div>
        )}
      </section>
    </div>
  );
};

// 14. Account Settings
const AccountSettings = () => {
  const { session, setSession } = useAuthState();
  const authSession = session as AuthSession;
  const navigate = useNavigate();
  const [logoutError, setLogoutError] = useState("");
  const [logoutPending, setLogoutPending] = useState(false);
  const [allDeviceLogoutOpen, setAllDeviceLogoutOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<"profile" | "notifications" | "security">("profile");
  const [displayName, setDisplayName] = useState(authSession.user.displayName);
  const [pictureUrl, setPictureUrl] = useState(authSession.user.pictureUrl ?? "");
  const [profilePending, setProfilePending] = useState(false);
  const [profileMessage, setProfileMessage] = useState("");
  const [profileError, setProfileError] = useState("");

  useEffect(() => {
    setDisplayName(authSession.user.displayName);
    setPictureUrl(authSession.user.pictureUrl ?? "");
  }, [authSession.user.displayName, authSession.user.pictureUrl]);

  const tabs = [
    { id: "profile" as const, label: "Profile" },
    { id: "notifications" as const, label: "Notifications" },
    { id: "security" as const, label: "Security" },
  ];

  const Toggle = ({ value }: { value: boolean }) => (
    <button className={`relative w-9 h-5 rounded-full transition-colors shrink-0 ${value ? "bg-foreground" : "bg-muted-foreground/30"} opacity-60 cursor-not-allowed`} disabled type="button">
      <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform ${value ? "translate-x-4" : ""}`} />
    </button>
  );

  const readOnlyNotifications = [
    { key: "meetingReminder", label: "Meeting Reminders", desc: "15-minute notice before scheduled meetings.", value: true },
    { key: "reportReady", label: "AI Report Ready", desc: "When a meeting AI report is generated and ready for review.", value: true },
    { key: "taskAssigned", label: "Task Assigned", desc: "When a task is assigned to you from a meeting.", value: true },
    { key: "weeklyDigest", label: "Weekly Digest", desc: "Summary of the week's meetings and decisions every Monday.", value: false }
  ] as const;

  async function handleLogout() {
    if (logoutPending) {
      return;
    }
    setLogoutError("");
    setLogoutPending(true);
    try {
      await logoutCurrentSession();
      setSession(null);
      navigate("/login", { replace: true });
    } catch (cause) {
      setLogoutError(cause instanceof Error ? cause.message : "로그아웃하지 못했습니다.");
      setLogoutPending(false);
    }
  }

  async function handleLogoutAll() {
    await logoutAllDevices();
    setSession(null);
    navigate("/login", { replace: true });
  }

  async function handleProfileSave() {
    if (!displayName.trim() || profilePending) return;
    setProfilePending(true);
    setProfileMessage("");
    setProfileError("");
    try {
      const user = await updateProfile({ displayName: displayName.trim(), pictureUrl: pictureUrl.trim() || null });
      setSession({ ...authSession, user });
      setProfileMessage("Profile saved.");
    } catch (cause) {
      setProfileError(cause instanceof Error ? cause.message : "Unable to save profile.");
    } finally {
      setProfilePending(false);
    }
  }

  async function handleProfileImageUpload(file: File) {
    if (profilePending) return;
    setProfilePending(true);
    setProfileError("");
    try {
      setPictureUrl(await uploadProfileImage(file));
      setProfileMessage("Image uploaded. Save profile to apply it.");
    } catch (cause) {
      setProfileError(cause instanceof Error ? cause.message : "Unable to upload profile image.");
    } finally {
      setProfilePending(false);
    }
  }

  return (
    <div className="min-h-screen bg-background">
      <div className="max-w-3xl mx-auto px-8 py-10">
        {/* Back */}
        <button onClick={() => navigate(-1)} className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-6 transition-colors">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M9 2L4 7L9 12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>
          Back
        </button>

        <div className="flex items-center gap-4 mb-8">
          {pictureUrl ? <img alt="Profile" className="w-14 h-14 rounded-full border border-border object-cover" src={pictureUrl} /> : <div className="w-14 h-14 rounded-full bg-foreground text-background flex items-center justify-center text-xl font-bold">{sessionInitials(session)}</div>}
          <div>
            <h1 className="text-xl font-bold">{authSession.user.displayName}</h1>
            <p className="text-sm text-muted-foreground">{authSession.user.email}</p>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex gap-6 border-b border-border mb-8">
          {tabs.map(t => (
            <button
              key={t.id}
              onClick={() => setActiveTab(t.id)}
              className={`pb-3 text-sm font-medium border-b-2 transition-colors ${activeTab === t.id ? "border-foreground text-foreground" : "border-transparent text-muted-foreground hover:text-foreground"}`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {activeTab === "profile" && (
          <div className="space-y-6">
            <div className="bg-card border border-border rounded-lg p-5 space-y-4">
              <div>
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Full Name</label>
                <input className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50" disabled={profilePending} onChange={(event) => setDisplayName(event.target.value)} value={displayName} />
              </div>
              <div>
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Profile Image</label>
                <div className="flex items-center gap-3">
                  {pictureUrl ? <img alt="Profile preview" className="h-10 w-10 rounded-full border border-border object-cover" src={pictureUrl} /> : <div className="h-10 w-10 rounded-full bg-muted" />}
                  <label className="cursor-pointer rounded-md border border-border bg-background px-3 py-2 text-sm font-semibold hover:bg-muted">
                    Upload image
                    <input accept="image/jpeg,image/png,image/webp" className="sr-only" disabled={profilePending} onChange={(event) => { const file = event.target.files?.[0]; if (file) void handleProfileImageUpload(file); event.target.value = ""; }} type="file" />
                  </label>
                  {pictureUrl ? <button className="text-sm text-muted-foreground hover:text-foreground" disabled={profilePending} onClick={() => setPictureUrl("")} type="button">Remove</button> : null}
                </div>
              </div>
              <div>
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Email</label>
                <input value={authSession.user.email} readOnly className="w-full px-3 py-2 rounded-md border border-border bg-muted text-sm text-muted-foreground cursor-not-allowed" />
              </div>
              <div>
                <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Account Status</label>
                <input
                  className="w-full px-3 py-2 rounded-md border border-border bg-muted text-sm text-muted-foreground cursor-not-allowed"
                  readOnly
                  value={authSession.user.status}
                />
              </div>
              {profileError ? <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">{profileError}</p> : null}
              {profileMessage ? <p className="text-xs text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-md px-3 py-2">{profileMessage}</p> : null}
              <button className="px-4 py-2 bg-foreground text-background rounded-md text-sm font-semibold hover:bg-foreground/90 disabled:opacity-60" disabled={profilePending || !displayName.trim()} onClick={() => void handleProfileSave()} type="button">{profilePending ? "Saving..." : "Save Profile"}</button>
            </div>
          </div>
        )}

        {activeTab === "notifications" && (
          <div className="bg-card border border-border rounded-lg divide-y divide-border overflow-hidden">
            {readOnlyNotifications.map((item) => (
              <div key={item.key} className="px-5 py-4 flex items-center justify-between gap-4">
                <div>
                  <p className="text-sm font-medium">{item.label}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">{item.desc}</p>
                </div>
                <Toggle value={item.value} />
              </div>
            ))}
            <div className="px-5 py-4 text-xs text-amber-700 bg-amber-50">
              알림 설정 저장 API는 아직 연결되지 않았습니다.
            </div>
          </div>
        )}

        {activeTab === "security" && (
          <div className="space-y-4">
            <div className="bg-card border border-border rounded-lg p-5 space-y-4">
              <h3 className="font-semibold text-sm">Session Security</h3>
              <div className="space-y-3 text-sm">
                <div className="flex items-center justify-between gap-4">
                  <span className="text-muted-foreground">Idle Expiry</span>
                  <strong className="text-foreground">{new Date(authSession.session.idleExpiresAt).toLocaleString("ko-KR")}</strong>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <span className="text-muted-foreground">Absolute Expiry</span>
                  <strong className="text-foreground">{new Date(authSession.session.expiresAt).toLocaleString("ko-KR")}</strong>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <span className="text-muted-foreground">Remember Me</span>
                  <strong className="text-foreground">{authSession.session.rememberMe ? "Enabled" : "Disabled"}</strong>
                </div>
              </div>
              <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                비밀번호 변경과 계정 삭제 API는 아직 연결되지 않았습니다.
              </p>
              {logoutError ? (
                <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded-md px-3 py-2">
                  {logoutError}
                </p>
              ) : null}
              <div className="flex gap-3">
                <button
                  className="px-4 py-2 border border-border rounded-md text-sm font-semibold hover:bg-muted transition-colors disabled:opacity-60"
                  disabled={logoutPending}
                  onClick={() => setAllDeviceLogoutOpen(true)}
                  type="button"
                >
                  모든 기기 로그아웃
                </button>
                <button
                  className="px-4 py-2 bg-foreground text-background rounded-md text-sm font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60"
                  disabled={logoutPending}
                  onClick={() => void handleLogout()}
                  type="button"
                >
                  {logoutPending ? "로그아웃 중..." : "현재 기기 로그아웃"}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
      {allDeviceLogoutOpen ? (
        <AllDeviceLogoutModal
          onClose={() => setAllDeviceLogoutOpen(false)}
          onLogoutAll={handleLogoutAll}
        />
      ) : null}
    </div>
  );
};

// 15. Meeting Join Request (unauthenticated)
const MeetingAccess = () => {
  const { loading: authLoading, session } = useAuthState();
  const authSession = session as AuthSession;
  const navigate = useNavigate();
  const location = useLocation();
  const routeParams = useParams<{ meetingId?: string }>();
  const [searchParams] = useSearchParams();
  const queryMeetingId = searchParams.get("meetingId")?.trim() ?? "";
  const queryJoinCode = searchParams.get("joinCode")?.trim() ?? "";
  const requestedMeetingId = routeParams.meetingId ?? queryMeetingId;
  const [joinCodeOrUrl, setJoinCodeOrUrl] = useState(queryJoinCode);
  const [meetingId, setMeetingId] = useState(requestedMeetingId);
  const [requestId, setRequestId] = useState("");
  const [accessState, setAccessState] = useState<"idle" | "checking" | "submitting" | "pending" | "allowed" | "denied">(requestedMeetingId ? "checking" : "idle");
  const [message, setMessage] = useState("");
  const [resolvedSpaceId, setResolvedSpaceId] = useState("");
  const [meetingStatus, setMeetingStatus] = useState<MeetingDetailResponse["status"] | null>(null);

  useEffect(() => {
    if (queryJoinCode) {
      setJoinCodeOrUrl(queryJoinCode);
    }
  }, [queryJoinCode]);

  async function checkAccess(targetMeetingId: string, pendingRequestId?: string) {
    if (!session) {
      return;
    }
    if (!targetMeetingId) {
      setAccessState("denied");
      setMessage("확인할 회의 ID가 없습니다. 회의 URL 또는 참가 코드를 먼저 제출해 주세요.");
      return;
    }

    setAccessState("checking");
    setMessage("");
    try {
      const participants = await fetchMeetingParticipants(authSession, targetMeetingId);
      const participant = participants.participants.find((item) => item.userId === authSession.user.id);
      if (!participant) {
        throw new Error("접근 권한이 없습니다.");
      }
      const detail = await fetchMeetingDetail(authSession, targetMeetingId);
      setMeetingId(targetMeetingId);
      setResolvedSpaceId(detail.spaceId);
      setMeetingStatus(detail.status);
      setAccessState("allowed");
      setMessage("회의 접근 권한이 확인되었습니다. 입장 준비 화면으로 이동할 수 있습니다.");
    } catch (cause) {
      setResolvedSpaceId("");
      setMeetingStatus(null);
      setAccessState(pendingRequestId ? "pending" : "denied");
      setMessage(
        pendingRequestId
          ? "참가 신청이 접수되었습니다. HOST 또는 OWNER/ADMIN 승인 전에는 회의 데이터에 접근할 수 없습니다."
          : cause instanceof Error
            ? cause.message
            : "현재 이 회의에 접근할 수 없습니다. 참가 신청이 필요합니다."
      );
    }
  }

  useEffect(() => {
    if (session && requestedMeetingId) {
      void checkAccess(requestedMeetingId, requestId);
    }
    // requestId는 승인 대기 문구 전환용으로만 사용한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestedMeetingId, session]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = joinCodeOrUrl.trim();
    if (!normalized || accessState === "submitting" || !session) {
      return;
    }

    setAccessState("submitting");
    setMessage("");
    try {
      const response = await createMeetingJoinRequest(authSession, { joinCodeOrUrl: normalized });
      setMeetingId(response.meetingId);
      setRequestId(response.requestId);
      setResolvedSpaceId("");
      setMeetingStatus(null);
      setAccessState("pending");
      setMessage("참가 신청을 보냈습니다. 승인 상태를 다시 확인해 주세요.");
    } catch (cause) {
      setAccessState("denied");
      setMessage(cause instanceof Error ? cause.message : "참가 신청을 처리하지 못했습니다.");
    }
  }

  if (authLoading) {
    return <LoadingState label="Checking your session..." />;
  }

  if (!session) {
    return (
      <Navigate
        replace
        state={{ requestedPath: `${location.pathname}${location.search}${location.hash}` }}
        to="/login"
      />
    );
  }

  const canEnter = accessState === "allowed" && Boolean(resolvedSpaceId) && Boolean(meetingId);
  const meetingRoomTarget = canEnter
    ? `/spaces/${encodeURIComponent(resolvedSpaceId)}/meetings/${encodeURIComponent(meetingId)}/${meetingStatus === "IN_PROGRESS" ? "live" : "live/prejoin"}`
    : "";

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-6">
      <div className="w-full max-w-sm space-y-6">
        <div className="text-center">
          <div className="w-10 h-10 rounded-lg bg-foreground flex items-center justify-center mx-auto mb-4">
            <span className="text-background font-bold text-sm">M</span>
          </div>
          <h1 className="text-xl font-bold">Join Meeting</h1>
          <p className="text-sm text-muted-foreground mt-1">Request access to this meeting</p>
        </div>

        <div className="bg-card border border-border rounded-xl p-5 space-y-2 text-sm">
          <p className="font-semibold text-foreground">{meetingId ? `Meeting ${meetingId}` : "Meeting access request"}</p>
          <p className="text-muted-foreground">
            승인 전에는 회의 원문, 회의록, Meeting AI, Project AI 범위가 모두 차단됩니다.
          </p>
          {requestId ? <p className="text-xs text-muted-foreground">Request ID: {requestId}</p> : null}
        </div>

        <form className="space-y-3" onSubmit={handleSubmit}>
          <div>
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Meeting URL or Code</label>
            <input
              value={joinCodeOrUrl}
              onChange={(event) => setJoinCodeOrUrl(event.target.value)}
              placeholder="Paste a meeting URL or join code"
              className="w-full px-3 py-2.5 rounded-lg border border-border bg-card text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
            />
          </div>
          <div>
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Signed-in Account</label>
            <input
              disabled
              value={session.user.email}
              className="w-full px-3 py-2.5 rounded-lg border border-border bg-muted/50 text-sm text-muted-foreground"
            />
          </div>
          <button
            type="submit"
            disabled={!joinCodeOrUrl.trim() || accessState === "submitting"}
            className={`w-full py-2.5 rounded-lg text-sm font-semibold transition-colors ${joinCodeOrUrl.trim() && accessState !== "submitting" ? "bg-foreground text-background hover:bg-foreground/90" : "bg-muted text-muted-foreground cursor-not-allowed"}`}
          >
            {accessState === "submitting" ? "Requesting..." : "Request Access"}
          </button>
        </form>

        {message ? (
          <div className={`rounded-lg border px-3 py-2 text-xs ${accessState === "allowed" ? "border-emerald-200 bg-emerald-50 text-emerald-700" : accessState === "pending" || accessState === "checking" || accessState === "submitting" ? "border-amber-200 bg-amber-50 text-amber-700" : "border-red-200 bg-red-50 text-red-700"}`}>
            {message}
          </div>
        ) : null}

        <div className="flex gap-3">
          {meetingId ? (
            <button
              onClick={() => void checkAccess(meetingId, requestId)}
              type="button"
              className="flex-1 py-2.5 rounded-lg border border-border text-sm font-semibold text-muted-foreground hover:bg-muted transition-colors"
            >
              Check Again
            </button>
          ) : null}
          {canEnter ? (
            <button
              onClick={() => navigate(meetingRoomTarget)}
              type="button"
              className="flex-1 py-2.5 rounded-lg bg-foreground text-background text-sm font-semibold hover:bg-foreground/90 transition-colors"
            >
              Enter Meeting
            </button>
          ) : null}
        </div>

        <p className="text-center text-xs text-muted-foreground">The host will be notified and can admit you to the meeting.</p>
      </div>
    </div>
  );
};

const MeetingAccessCompatRoute = () => {
  const { meetingId = "" } = useParams<{ meetingId: string }>();
  const location = useLocation();
  const searchParams = new URLSearchParams(location.search);
  const joinCode = searchParams.get("joinCode") ?? "";
  const nextParams = new URLSearchParams();
  if (meetingId) {
    nextParams.set("meetingId", meetingId);
  }
  if (joinCode) {
    nextParams.set("joinCode", joinCode);
  }
  return <Navigate replace to={`/meeting-access${nextParams.toString() ? `?${nextParams.toString()}` : ""}`} />;
};

const GlobalMeetings = () => {
  const { session } = useAuthState();
  const [meetings, setMeetings] = useState<MeetingSummary[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!session) return;
    void fetchAccessibleMeetings(session).then((response) => setMeetings(response.meetings)).catch((cause) => setError(cause instanceof Error ? cause.message : "Couldn't load meetings."));
  }, [session]);
  return <div className="min-h-screen bg-background p-4 sm:p-8"><main className="mx-auto max-w-4xl"><header className="mb-8"><Link className="text-sm font-semibold text-muted-foreground hover:text-foreground" to="/">MeetingMind</Link><h1 className="mt-3 text-3xl font-bold">Meetings</h1><p className="mt-1 text-sm text-muted-foreground">Meetings you can access, including guest invitations.</p></header>{error ? <p className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</p> : null}<div className="space-y-3">{meetings.map((meeting) => <Link className="block rounded-lg border border-border bg-card p-5 transition-colors hover:bg-muted/40" key={meeting.id} to={`/spaces/${encodeURIComponent(meeting.spaceId)}/meetings/${encodeURIComponent(meeting.id)}/${meeting.status === "IN_PROGRESS" ? "live" : "live/prejoin"}`}><div className="flex items-start justify-between gap-4"><div><h2 className="font-semibold">{meeting.title}</h2><p className="mt-1 text-sm text-muted-foreground">{new Date(meeting.scheduledAt).toLocaleString()}</p></div><span className="rounded border border-border px-2 py-1 text-xs font-semibold text-muted-foreground">{meeting.status}</span></div></Link>)}{!error && meetings.length === 0 ? <div className="rounded-lg border border-dashed border-border p-10 text-center text-sm text-muted-foreground">No accessible meetings yet.</div> : null}</div></main></div>;
};

// 16. Invitation Response
const InvitationResponse = () => {
  const { session } = useAuthState();
  const { invitationId = "", spaceId = "" } = useParams<{ invitationId: string; spaceId: string }>();
  const location = useLocation();
  const authSession = session as AuthSession;
  const token = React.useMemo(() => {
    const fragment = location.hash.startsWith("#") ? location.hash.slice(1) : location.hash;
    return new URLSearchParams(fragment).get("token")?.trim() ?? "";
  }, [location.hash]);
  const [resolution, setResolution] = useState<"ready" | "submitting" | "accepted" | "declined" | "error">("ready");
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (location.hash) {
      window.history.replaceState(null, "", `${location.pathname}${location.search}`);
    }
  }, [location.hash, location.pathname, location.search]);

  async function resolveInvitation(action: "accept" | "decline") {
    if (!session) {
      return;
    }
    if (!spaceId || !invitationId || !token || resolution === "submitting") {
      setResolution("error");
      setMessage("초대 링크가 올바르지 않거나 token이 없습니다. 원본 초대 링크로 다시 접속해 주세요.");
      return;
    }

    setResolution("submitting");
    setMessage("");
    try {
      if (action === "accept") {
        await acceptSpaceInvitation(authSession, spaceId, invitationId, { token });
        setResolution("accepted");
        setMessage("Space 초대를 수락했습니다. 이제 프로젝트 목록에서 접근할 수 있습니다.");
      } else {
        await declineSpaceInvitation(authSession, spaceId, invitationId, { token });
        setResolution("declined");
        setMessage("Space 초대를 거절했습니다.");
      }
    } catch (cause) {
      setResolution("error");
      setMessage(cause instanceof Error ? cause.message : "Space 초대를 처리하지 못했습니다.");
    }
  }

  const statusLabel =
    resolution === "accepted"
      ? "수락됨"
      : resolution === "declined"
        ? "거절됨"
        : resolution === "error"
          ? "처리 실패"
          : resolution === "submitting"
            ? "처리 중"
            : "응답 대기";
  const statusTone =
    resolution === "accepted"
      ? "bg-emerald-50 text-emerald-700 border-emerald-200"
      : resolution === "declined"
        ? "bg-slate-100 text-slate-700 border-slate-200"
        : resolution === "error"
          ? "bg-red-50 text-red-700 border-red-200"
          : "bg-amber-50 text-amber-700 border-amber-200";
  const isComplete = resolution === "accepted" || resolution === "declined";

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-6">
      <div className="w-full max-w-sm space-y-6">
        <div className="text-center">
          <div className="w-10 h-10 rounded-lg bg-foreground flex items-center justify-center mx-auto mb-4">
            <span className="text-background font-bold text-sm">M</span>
          </div>
          <p className="text-sm text-muted-foreground">You've been invited to join</p>
          <h1 className="text-2xl font-bold mt-1">Project Workspace</h1>
        </div>

        <div className="bg-card border border-border rounded-xl p-5 space-y-3">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-sm font-medium">Space invitation response</p>
              <p className="text-xs text-muted-foreground">현재 로그인한 계정으로 초대 대상 여부를 검증합니다.</p>
            </div>
            <span className={`px-2 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider border ${statusTone}`}>
              {statusLabel}
            </span>
          </div>
          <div className="border-t border-border pt-3 space-y-1.5 text-xs text-muted-foreground">
            <div className="flex items-center justify-between gap-3">
              <span>Signed-in account</span>
              <strong className="text-foreground">{authSession.user.email}</strong>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span>Space ID</span>
              <strong className="text-foreground">{spaceId || "-"}</strong>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span>Invitation ID</span>
              <strong className="text-foreground">{invitationId || "-"}</strong>
            </div>
          </div>
        </div>

        {message ? (
          <div className={`rounded-lg border px-3 py-2 text-xs ${resolution === "accepted" ? "border-emerald-200 bg-emerald-50 text-emerald-700" : resolution === "declined" ? "border-slate-200 bg-slate-100 text-slate-700" : resolution === "error" ? "border-red-200 bg-red-50 text-red-700" : "border-amber-200 bg-amber-50 text-amber-700"}`}>
            {message}
          </div>
        ) : null}

        {isComplete ? (
          <Link to={`/spaces/${encodeURIComponent(spaceId)}`} className="block w-full py-2.5 rounded-lg bg-foreground text-background text-center text-sm font-semibold hover:bg-foreground/90 transition-colors">
            Open workspace
          </Link>
        ) : (
          <div className="flex gap-3">
            <button
              onClick={() => void resolveInvitation("decline")}
              disabled={resolution === "submitting" || !token}
              className="flex-1 py-2.5 rounded-lg border border-border text-sm font-semibold text-muted-foreground hover:bg-muted transition-colors disabled:opacity-60"
              type="button"
            >
              Decline
            </button>
            <button
              onClick={() => void resolveInvitation("accept")}
              disabled={resolution === "submitting" || !token}
              className="flex-1 py-2.5 rounded-lg bg-foreground text-background text-sm font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60"
              type="button"
            >
              {resolution === "submitting" ? "Processing..." : "Accept"}
            </button>
          </div>
        )}

        {!token ? <p className="text-center text-xs text-red-600">초대 token이 없습니다. 원본 초대 링크로 다시 접속해 주세요.</p> : null}
      </div>
    </div>
  );
};

const MeetingInvitationResponse = () => {
  const { session } = useAuthState();
  const { meetingId = "", invitationId = "" } = useParams<{ meetingId: string; invitationId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const token = new URLSearchParams(location.hash.replace(/^#/, "")).get("token")?.trim() ?? "";
  const [state, setState] = useState<"ready" | "submitting" | "accepted" | "declined" | "error">("ready");
  const [message, setMessage] = useState("");

  async function resolve(accept: boolean) {
    if (!session || !token || state === "submitting") return;
    setState("submitting");
    try {
      const result = await resolveMeetingInvitation(session, meetingId, invitationId, token, accept);
      setState(accept ? "accepted" : "declined");
      setMessage(accept ? "Meeting access granted." : "Invitation declined.");
      if (accept && result.participantId) setTimeout(() => navigate("/meetings", { replace: true }), 700);
    } catch (cause) {
      setState("error");
      setMessage(cause instanceof Error ? cause.message : "Couldn't resolve the invitation.");
    }
  }

  if (!session) return <Navigate replace state={{ requestedPath: `${location.pathname}${location.search}${location.hash}` }} to="/login" />;
  return <div className="min-h-screen bg-background flex items-center justify-center p-6"><section className="w-full max-w-md rounded-xl border border-border bg-card p-6 space-y-4"><p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Meeting invitation</p><h1 className="text-xl font-semibold">Join this meeting</h1><p className="text-sm leading-relaxed text-muted-foreground">This invitation grants access to this meeting only. It does not add you to the Space.</p>{message ? <p className={state === "error" ? "text-sm text-red-600" : "text-sm text-emerald-700"}>{message}</p> : null}{!token ? <p className="text-sm text-red-600">This invitation link is missing its token.</p> : <div className="flex justify-end gap-2"><button className="rounded-md border border-border px-4 py-2 text-sm font-semibold hover:bg-muted" disabled={state === "submitting"} onClick={() => void resolve(false)} type="button">Decline</button><button className="rounded-md bg-foreground px-4 py-2 text-sm font-semibold text-background hover:bg-foreground/90" disabled={state === "submitting"} onClick={() => void resolve(true)} type="button">{state === "submitting" ? "Processing..." : "Accept invitation"}</button></div>}</section></div>;
};

// ─────────────────────────────────────────────
// SHARED UI PRIMITIVES
// ─────────────────────────────────────────────

// Empty / Loading / Error / Retry states
const EmptyState = ({ icon, title, desc, action }: { icon: React.ReactNode; title: string; desc?: string; action?: React.ReactNode }) => (
  <div className="flex flex-col items-center justify-center py-20 px-6 text-center">
    <div className="w-12 h-12 rounded-xl bg-muted flex items-center justify-center mb-4 text-muted-foreground">{icon}</div>
    <h3 className="text-sm font-semibold text-foreground mb-1">{title}</h3>
    {desc && <p className="text-xs text-muted-foreground max-w-xs leading-relaxed mb-4">{desc}</p>}
    {action}
  </div>
);

const LoadingState = ({ label = "Loading..." }: { label?: string }) => (
  <div className="flex flex-col items-center justify-center py-20 gap-3">
    <div className="w-6 h-6 border-2 border-border border-t-foreground rounded-full animate-spin" />
    <p className="text-xs text-muted-foreground">{label}</p>
  </div>
);

const ErrorState = ({ title = "Something went wrong", desc, onRetry }: { title?: string; desc?: string; onRetry?: () => void }) => (
  <div className="flex flex-col items-center justify-center py-20 px-6 text-center">
    <div className="w-12 h-12 rounded-xl bg-red-50 border border-red-200 flex items-center justify-center mb-4">
      <svg width="20" height="20" viewBox="0 0 20 20" fill="none"><path d="M10 6v4m0 4h.01M3 10a7 7 0 1114 0A7 7 0 013 10z" stroke="#EF4444" strokeWidth="1.5" strokeLinecap="round"/></svg>
    </div>
    <h3 className="text-sm font-semibold text-foreground mb-1">{title}</h3>
    {desc && <p className="text-xs text-muted-foreground max-w-xs leading-relaxed mb-4">{desc}</p>}
    {onRetry && (
      <button onClick={onRetry} className="flex items-center gap-1.5 px-4 py-2 rounded-md bg-foreground text-background text-xs font-semibold hover:bg-foreground/90 transition-colors">
        <svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M1 6a5 5 0 005 5 5 5 0 004.9-4M11 6a5 5 0 00-5-5 5 5 0 00-4.9 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/><path d="M9 2l2 2-2 2" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>
        Retry
      </button>
    )}
  </div>
);

// ─────────────────────────────────────────────
// MODAL SYSTEM
// ─────────────────────────────────────────────

type ModalType =
  | "create-project" | "edit-project" | "delete-project"
  | "create-meeting" | "edit-meeting" | "delete-meeting"
  | "invite-member" | "change-role" | "transfer-owner"
  | null;

const Modal = ({ type, onClose }: { type: ModalType; onClose: () => void }) => {
  const [value, setValue] = useState("");
  const [role, setRole] = useState("Editor");

  if (!type) return null;

  const overlay = (children: React.ReactNode, width = "max-w-md") => (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm" onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className={`w-full ${width} bg-card rounded-xl border border-border shadow-2xl`} onClick={e => e.stopPropagation()}>
        {children}
      </div>
    </div>
  );

  const Header = ({ title, desc }: { title: string; desc?: string }) => (
    <div className="px-6 py-5 border-b border-border flex items-start justify-between gap-4">
      <div>
        <h2 className="font-semibold text-foreground">{title}</h2>
        {desc && <p className="text-xs text-muted-foreground mt-0.5">{desc}</p>}
      </div>
      <button onClick={onClose} className="w-7 h-7 flex items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors shrink-0"><X className="w-4 h-4" /></button>
    </div>
  );

  const Footer = ({ primary, primaryDanger, onPrimary }: { primary: string; primaryDanger?: boolean; onPrimary: () => void }) => (
    <div className="px-6 py-4 border-t border-border flex items-center justify-end gap-2">
      <button onClick={onClose} className="px-4 py-2 rounded-md border border-border text-sm text-muted-foreground hover:bg-muted transition-colors">Cancel</button>
      <button onClick={onPrimary} className={`px-4 py-2 rounded-md text-sm font-semibold transition-colors ${primaryDanger ? "bg-red-600 text-white hover:bg-red-700" : "bg-foreground text-background hover:bg-foreground/90"}`}>{primary}</button>
    </div>
  );

  const Field = ({ label, children }: { label: string; children: React.ReactNode }) => (
    <div>
      <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">{label}</label>
      {children}
    </div>
  );

  const inputCls = "w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50";

  // ── Create / Edit Project
  if (type === "create-project" || type === "edit-project") {
    const isEdit = type === "edit-project";
    return overlay(
      <>
        <Header title={isEdit ? "Edit Project" : "New Project"} desc={isEdit ? "Update project details." : "Create a new collaboration space."} />
        <div className="px-6 py-5 space-y-4">
          <Field label="Project Name"><input className={inputCls} placeholder="e.g. Q3 Launch" defaultValue={isEdit ? "Q3 Launch" : ""} /></Field>
          <Field label="Description"><textarea className={`${inputCls} resize-none`} rows={3} placeholder="What is this project about?" defaultValue={isEdit ? "End-to-end collaboration space for the Q3 product launch cycle." : ""} /></Field>
          <Field label="Visibility">
            <select className={inputCls}>
              <option>Private — invite only</option>
              <option>Internal — all workspace members</option>
            </select>
          </Field>
        </div>
        <Footer primary={isEdit ? "Save Changes" : "Create Project"} onPrimary={onClose} />
      </>
    );
  }

  // ── Delete Project
  if (type === "delete-project") {
    return overlay(
      <>
        <Header title="Delete Project" desc="This action cannot be undone." />
        <div className="px-6 py-5 space-y-4">
          <div className="rounded-lg bg-red-50 border border-red-200 p-4 text-sm text-red-800 leading-relaxed">
            All meetings, reports, tasks, and knowledge in <strong>Q3 Launch</strong> will be permanently deleted.
          </div>
          <Field label='Type "Q3 Launch" to confirm'>
            <input className={inputCls} value={value} onChange={e => setValue(e.target.value)} placeholder="Q3 Launch" />
          </Field>
        </div>
        <Footer primary="Delete Project" primaryDanger onPrimary={onClose} />
      </>
    );
  }

  // ── Create / Edit Meeting
  if (type === "create-meeting" || type === "edit-meeting") {
    const isEdit = type === "edit-meeting";
    return overlay(
      <>
        <Header title={isEdit ? "Edit Meeting" : "Schedule Meeting"} />
        <div className="px-6 py-5 space-y-4">
          <Field label="Title"><input className={inputCls} placeholder="e.g. Weekly Sync: Product & Design" defaultValue={isEdit ? "Weekly Sync: Product & Design" : ""} /></Field>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Date"><input type="date" className={inputCls} defaultValue="2024-10-22" /></Field>
            <Field label="Time"><input type="time" className={inputCls} defaultValue="14:00" /></Field>
          </div>
          <Field label="Duration">
            <select className={inputCls}>
              <option>30 minutes</option>
              <option selected>1 hour</option>
              <option>1.5 hours</option>
              <option>2 hours</option>
            </select>
          </Field>
          <Field label="Agenda / Description"><textarea className={`${inputCls} resize-none`} rows={3} placeholder="What will be covered?" /></Field>
        </div>
        <Footer primary={isEdit ? "Save Changes" : "Schedule"} onPrimary={onClose} />
      </>
    );
  }

  // ── Delete Meeting
  if (type === "delete-meeting") {
    return overlay(
      <>
        <Header title="Delete Meeting" desc="This will remove the meeting and its associated transcript." />
        <div className="px-6 py-5">
          <div className="rounded-lg bg-muted p-4 text-sm text-foreground">
            <p className="font-medium">Weekly Sync: Product & Design</p>
            <p className="text-muted-foreground text-xs mt-0.5">Today, 2:00 PM · 4 participants</p>
          </div>
        </div>
        <Footer primary="Delete Meeting" primaryDanger onPrimary={onClose} />
      </>
    );
  }

  // ── Invite Member
  if (type === "invite-member") {
    return overlay(
      <>
        <Header title="Invite Member" desc="They'll receive an email with a link to join." />
        <div className="px-6 py-5 space-y-4">
          <Field label="Email Address"><input className={inputCls} type="email" placeholder="colleague@company.com" /></Field>
          <Field label="Role">
            <div className="grid grid-cols-2 gap-2 mt-1">
              {["Editor", "Viewer"].map(r => (
                <button key={r} onClick={() => setRole(r)} className={`px-3 py-2.5 rounded-lg border text-sm font-medium text-left transition-colors ${role === r ? "border-foreground bg-foreground text-background" : "border-border text-muted-foreground hover:border-foreground/40"}`}>
                  <div className="font-semibold">{r}</div>
                  <div className={`text-[10px] mt-0.5 ${role === r ? "text-background/70" : "text-muted-foreground"}`}>{r === "Editor" ? "Can create & edit" : "Read-only access"}</div>
                </button>
              ))}
            </div>
          </Field>
          <Field label="Personal Message (optional)"><textarea className={`${inputCls} resize-none`} rows={2} placeholder="Add a note..." /></Field>
        </div>
        <Footer primary="Send Invite" onPrimary={onClose} />
      </>
    );
  }

  // ── Change Role
  if (type === "change-role") {
    return overlay(
      <>
        <Header title="Change Role" desc="Update Sarah Jenkins's access level." />
        <div className="px-6 py-5 space-y-3">
          {["Editor", "Viewer"].map(r => (
            <button key={r} onClick={() => setRole(r)} className={`w-full flex items-center justify-between px-4 py-3 rounded-lg border transition-colors ${role === r ? "border-foreground bg-muted" : "border-border hover:border-foreground/30"}`}>
              <div>
                <p className="text-sm font-semibold text-foreground">{r}</p>
                <p className="text-xs text-muted-foreground">{r === "Editor" ? "Can create meetings, reports, and tasks" : "Read-only — confirmed reports and knowledge base"}</p>
              </div>
              <div className={`w-4 h-4 rounded-full border-2 shrink-0 transition-colors ${role === r ? "border-foreground bg-foreground" : "border-muted-foreground"}`} />
            </button>
          ))}
        </div>
        <Footer primary="Update Role" onPrimary={onClose} />
      </>
    );
  }

  // ── Transfer Owner
  if (type === "transfer-owner") {
    return overlay(
      <>
        <Header title="Transfer Ownership" desc="You will lose owner privileges after transfer." />
        <div className="px-6 py-5 space-y-4">
          <div className="rounded-lg bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800 leading-relaxed">
            After transfer, your role becomes <strong>Editor</strong>. Only the new owner can reverse this.
          </div>
          <Field label="Transfer to">
            <select className={inputCls}>
              <option>Sarah Jenkins (Editor)</option>
              <option>David Chen (Editor)</option>
              <option>Mina Park (Viewer)</option>
            </select>
          </Field>
          <Field label='Type "transfer" to confirm'>
            <input className={inputCls} value={value} onChange={e => setValue(e.target.value)} placeholder="transfer" />
          </Field>
        </div>
        <Footer primary="Transfer Ownership" primaryDanger onPrimary={onClose} />
      </>
    );
  }

  return null;
};

// Demo: modal launcher for testing all modal types
const ModalDemo = () => {
  const [activeModal, setActiveModal] = useState<ModalType>(null);
  const modals: { type: ModalType; label: string; group: string }[] = [
    { group: "Project", type: "create-project", label: "Create Project" },
    { group: "Project", type: "edit-project", label: "Edit Project" },
    { group: "Project", type: "delete-project", label: "Delete Project" },
    { group: "Meeting", type: "create-meeting", label: "Schedule Meeting" },
    { group: "Meeting", type: "edit-meeting", label: "Edit Meeting" },
    { group: "Meeting", type: "delete-meeting", label: "Delete Meeting" },
    { group: "Member", type: "invite-member", label: "Invite Member" },
    { group: "Member", type: "change-role", label: "Change Role" },
    { group: "Member", type: "transfer-owner", label: "Transfer Owner" },
  ];
  const groups = Array.from(new Set(modals.map(m => m.group)));
  return (
    <div className="p-8 max-w-2xl mx-auto space-y-8">
      <div>
        <h1 className="text-xl font-bold mb-1">Modal Library</h1>
        <p className="text-sm text-muted-foreground">All shared modals — click to preview</p>
      </div>
      {groups.map(g => (
        <div key={g}>
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">{g}</p>
          <div className="flex flex-wrap gap-2">
            {modals.filter(m => m.group === g).map(m => (
              <button key={m.type} onClick={() => setActiveModal(m.type)} className="px-4 py-2 rounded-md border border-border text-sm hover:bg-muted transition-colors">
                {m.label}
              </button>
            ))}
          </div>
        </div>
      ))}

      {/* States demo */}
      <div>
        <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground mb-3">UI States</p>
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-card border border-border rounded-lg overflow-hidden">
            <p className="px-4 py-2 border-b border-border text-xs font-semibold text-muted-foreground">Empty</p>
            <EmptyState icon={<FileText className="w-5 h-5" />} title="No reports yet" desc="Confirmed meeting reports will appear here." action={<button className="px-3 py-1.5 text-xs rounded-md bg-foreground text-background font-medium">Schedule a meeting</button>} />
          </div>
          <div className="bg-card border border-border rounded-lg overflow-hidden">
            <p className="px-4 py-2 border-b border-border text-xs font-semibold text-muted-foreground">Loading</p>
            <LoadingState label="Fetching reports..." />
          </div>
          <div className="bg-card border border-border rounded-lg overflow-hidden">
            <p className="px-4 py-2 border-b border-border text-xs font-semibold text-muted-foreground">Error</p>
            <ErrorState title="Failed to load" desc="Could not reach the server." onRetry={() => {}} />
          </div>
          <div className="bg-card border border-border rounded-lg overflow-hidden">
            <p className="px-4 py-2 border-b border-border text-xs font-semibold text-muted-foreground">Empty (no action)</p>
            <EmptyState icon={<Users className="w-5 h-5" />} title="No members invited" desc="Invite teammates to start collaborating." />
          </div>
        </div>
      </div>

      <Modal type={activeModal} onClose={() => setActiveModal(null)} />
    </div>
  );
};

// ─────────────────────────────────────────────
// 17. Login / Sign In
// ─────────────────────────────────────────────
const LoginPage = () => {
  const { loading: authLoading, session, setSession } = useAuthState();
  const { locale } = useAppPreferences();
  const location = useLocation();
  const navigate = useNavigate();
  // The landing page hands off `?mode=signup&email=…`; treat it as the initial
  // form state only, so later edits and tab switches are not clobbered.
  const [searchParams] = useSearchParams();
  const [mode, setMode] = useState<"login" | "signup">(
    () => searchParams.get("mode") === "signup" ? "signup" : "login"
  );
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState(() => searchParams.get("email")?.trim() ?? "");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();
  const korean = locale === "ko";

  useEffect(() => {
    if (authLoading || !session) {
      return;
    }
    const requestedPath =
      typeof location.state === "object" &&
      location.state !== null &&
      "requestedPath" in location.state &&
      typeof (location.state as { requestedPath?: unknown }).requestedPath === "string"
        ? (location.state as { requestedPath: string }).requestedPath
        : "/spaces";
    navigate(requestedPath, { replace: true });
  }, [authLoading, location.state, navigate, session]);

  const handleGoogleCredential = async (credential: string) => {
    setError("");
    setLoading(true);
    try {
      const nextSession = await loginWithGoogle(credential);
      setSession(nextSession);
      const requestedPath =
        typeof location.state === "object" &&
        location.state !== null &&
        "requestedPath" in location.state &&
        typeof (location.state as { requestedPath?: unknown }).requestedPath === "string"
          ? (location.state as { requestedPath: string }).requestedPath
          : "/spaces";
      navigate(requestedPath, { replace: true });
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Google sign-in failed.");
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!email || !password || (mode === "signup" && !displayName.trim())) {
      setError("Please complete the required fields.");
      return;
    }
    setLoading(true);
    try {
      const nextSession =
        mode === "signup"
          ? await signupWithPassword({ email: email.trim(), password, displayName: displayName.trim() })
          : await loginWithPassword({ email: email.trim(), password });
      setSession(nextSession);
      const requestedPath =
        typeof location.state === "object" &&
        location.state !== null &&
        "requestedPath" in location.state &&
        typeof (location.state as { requestedPath?: unknown }).requestedPath === "string"
          ? (location.state as { requestedPath: string }).requestedPath
          : "/spaces";
      navigate(requestedPath, { replace: true });
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Sign in failed.");
    } finally {
      setLoading(false);
    }
  };

  if (authLoading) {
    return <LoadingState label="Checking your session..." />;
  }

  return (
    <div className="min-h-screen bg-background flex relative" data-testid="sign-in-page">
      <div className="absolute right-4 top-4 z-10 sm:right-6 sm:top-6">
        <DisplayPreferences />
      </div>
      {/* Left — Brand panel */}
      <div className="hidden lg:flex lg:w-1/2 bg-foreground flex-col justify-between p-12">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center">
            <Sparkles className="w-4 h-4 text-white" />
          </div>
          <span className="text-white font-bold text-lg">MeetingMind</span>
        </div>
        <div className="space-y-6">
          <blockquote className="text-white/80 text-xl leading-relaxed font-light">
            {korean
              ? "회의에서 내린 모든 결정은 기억되고, 실행되며, 다음 업무로 이어져야 합니다."
              : "Every decision we make in meetings deserves to be remembered, acted on, and built upon."}
          </blockquote>
          <div className="flex flex-col gap-4">
            {[
              { icon: <Mic className="w-4 h-4" />, text: korean ? "화자 정보를 포함한 실시간 전사" : "Real-time transcription with speaker attribution" },
              { icon: <Sparkles className="w-4 h-4" />, text: korean ? "AI 회의록, 결정사항, 태스크 후보 생성" : "AI-generated reports, decisions, and task extraction" },
              { icon: <Library className="w-4 h-4" />, text: korean ? "회의에서 자동으로 쌓이는 프로젝트 지식" : "Project knowledge base built automatically from meetings" },
            ].map(f => (
              <div key={f.text} className="flex items-center gap-3 text-white/60 text-sm">
                <div className="w-7 h-7 rounded-lg bg-white/10 flex items-center justify-center shrink-0">{f.icon}</div>
                {f.text}
              </div>
            ))}
          </div>
        </div>
        <p className="text-white/30 text-xs">© 2024 MeetingMind. All rights reserved.</p>
      </div>

      {/* Right — Form */}
      <div className="flex-1 flex items-center justify-center p-8">
        <div className="w-full max-w-sm space-y-7">
          <div>
            <div className="lg:hidden flex items-center gap-2 mb-8">
              <div className="w-7 h-7 rounded-lg bg-foreground flex items-center justify-center"><Sparkles className="w-3.5 h-3.5 text-background" /></div>
              <span className="font-bold">MeetingMind</span>
            </div>
            <h1 className="text-2xl font-bold text-foreground">{mode === "signup" ? (korean ? "환영합니다" : "Welcome!") : (korean ? "다시 오셨군요" : "Welcome back")}</h1>
            <p className="text-sm text-muted-foreground mt-1">{mode === "signup" ? (korean ? "워크스페이스 계정을 만들어 시작하세요" : "Create your workspace account") : (korean ? "워크스페이스에 로그인하세요" : "Sign in to your workspace")}</p>
          </div>

          <div className="grid grid-cols-2 rounded-lg border border-border p-1 bg-muted/40">
            <button
              type="button"
              onClick={() => setMode("login")}
              className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${mode === "login" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground"}`}
            >
              {korean ? "로그인" : "Sign in"}
            </button>
            <button
              type="button"
              onClick={() => setMode("signup")}
              className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${mode === "signup" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground"}`}
            >
              {korean ? "회원가입" : "Sign up"}
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {mode === "signup" ? (
              <div>
                <div id="signup-display-name-label" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">{korean ? "이름" : "Display Name"}</div>
                <input
                  id="signup-display-name"
                  aria-labelledby="signup-display-name-label"
                  name="displayName"
                  type="text"
                  autoComplete="name"
                  required
                  value={displayName}
                  onChange={e => setDisplayName(e.target.value)}
                  placeholder="Alex Kim"
                  className="w-full px-3 py-2.5 rounded-lg border border-border bg-card text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 transition-all"
                />
              </div>
            ) : null}
            <div>
              <div id="auth-email-label" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">{korean ? "이메일" : "Work Email"}</div>
              <input
                data-testid="sign-in-email"
                id="auth-email"
                aria-labelledby="auth-email-label"
                name="email"
                type="email"
                autoComplete="email"
                required
                spellCheck={false}
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="alex@company.com"
                className="w-full px-3 py-2.5 rounded-lg border border-border bg-card text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 transition-all"
              />
            </div>
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <div id="auth-password-label" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">{korean ? "비밀번호" : "Password"}</div>
                <button type="button" className="text-xs text-primary hover:underline">{korean ? "비밀번호를 잊으셨나요?" : "Forgot password?"}</button>
              </div>
              <input
                data-testid="sign-in-password"
                id="auth-password"
                aria-labelledby="auth-password-label"
                name="password"
                type="password"
                autoComplete={mode === "signup" ? "new-password" : "current-password"}
                required
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full px-3 py-2.5 rounded-lg border border-border bg-card text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 transition-all"
              />
            </div>
            {error && <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded-lg px-3 py-2" data-testid="sign-in-error">{error}</p>}
            <button
              type="submit"
              data-testid="sign-in-submit"
              disabled={loading}
              className="w-full py-2.5 rounded-lg bg-foreground text-background text-sm font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60 flex items-center justify-center gap-2"
            >
              {loading ? <><div className="w-4 h-4 border-2 border-background/30 border-t-background rounded-full animate-spin" /> {korean ? "처리 중..." : "Processing..."}</> : mode === "signup" ? (korean ? "계정 만들기" : "Create account") : (korean ? "로그인" : "Sign in")}
            </button>
          </form>

          <div className="relative">
            <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-border" /></div>
            <div className="relative flex justify-center"><span className="bg-background px-3 text-xs text-muted-foreground">{korean ? "또는" : "or continue with"}</span></div>
          </div>

          {clientId ? (
            <GoogleCredentialButton
              clientId={clientId}
              disabled={loading}
              onCredential={handleGoogleCredential}
              onError={setError}
            />
          ) : (
            <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
              Google sign-in is not configured. Check `VITE_GOOGLE_CLIENT_ID` in `frontend/.env`.
            </p>
          )}

          <p className="text-center text-xs text-muted-foreground">
            {mode === "signup"
              ? (korean ? "이미 계정이 있나요? " : "Already have an account? ")
              : (korean ? "계정이 없나요? " : "Need an account? ")}
            <button
              onClick={() => setMode(mode === "signup" ? "login" : "signup")}
              className="text-foreground font-semibold hover:underline"
              type="button"
            >
              {mode === "signup" ? (korean ? "로그인" : "Go to sign in") : (korean ? "회원가입" : "Create one")}
            </button>
          </p>
        </div>
      </div>
    </div>
  );
};

// ─────────────────────────────────────────────
// 18. Project AI — full page with citations
// ─────────────────────────────────────────────
type AIMessage = { role: "user" | "assistant"; text: string; sources?: { label: string; type: "report" | "doc" | "term" }[]; noEvidence?: boolean };

const ProjectAIPage = () => {
  const { session } = useAuthState();
  const { locale } = useAppPreferences();
  const { spaceId = "" } = useParams();
  const [searchParams] = useSearchParams();
  const { spaceDetail, spaceLoading, spaceError } = useOutletContext<ShellOutletContext>();
  const aiScope = searchParams.get("scope") === "meeting" ? "meeting" : "project";
  const selectedMeetingId = searchParams.get("meetingId") ?? "";
  const projectAiHref = `/spaces/${encodeURIComponent(spaceId)}/ai`;
  const meetingAiHref = selectedMeetingId
    ? `${projectAiHref}?scope=meeting&meetingId=${encodeURIComponent(selectedMeetingId)}`
    : null;
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState<AIMessage[]>([
    {
      role: "assistant",
      text: "현재 프로젝트의 공식 지식과 접근 가능한 회의만 검색합니다.",
      sources: []
    }
  ]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [model, setModel] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);
  const korean = locale === "ko";

  const suggestions = [
    "최근 확정된 결정사항을 정리해줘",
    "현재 담당자별 열린 태스크를 요약해줘",
    "공식 지식 기준으로 프로젝트 상태를 설명해줘",
    "최근 회의에서 반복된 이슈를 알려줘"
  ];

  useEffect(() => {
    if (!session || !spaceId) {
      return;
    }

    let active = true;
    setError("");
    setModel("");
    setMessages([
      {
        role: "assistant",
        text: "현재 프로젝트의 공식 지식과 접근 가능한 회의만 검색합니다.",
        sources: []
      }
    ]);

    void fetchProjectAiHistory(session, spaceId)
      .then((history) => {
        if (!active || history.messages.length === 0) {
          return;
        }
        setMessages(history.messages.map((message) => ({
          role: message.role === "USER" ? "user" : "assistant",
          text: message.content
        })));
      })
      .catch(() => {});

    return () => {
      active = false;
    };
  }, [session, spaceId]);

  const send = async (text?: string) => {
    const q = (text ?? input).trim();
    if (!q || !session || !spaceId || loading) {
      return;
    }
    setInput("");
    setError("");
    setLoading(true);
    setMessages((prev) => [...prev, { role: "user", text: q }]);
    try {
      const response = await chatProjectAi(session, spaceId, { question: q });
      setModel(response.model);
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          text: response.unsupported ? unsupportedAiMessage(response.unsupportedReason, "project") : response.answer,
          noEvidence: response.unsupported,
          sources: response.sources.map((source) => ({
            label: aiSourceLabel(source),
            type:
              source.type === "projectKnowledge"
                ? "doc"
                : source.type === "glossary"
                  ? "term"
                  : "report"
          }))
        }
      ]);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Project AI에 연결하지 못했습니다.";
      setError(message);
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          text: "Project AI 응답을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.",
          noEvidence: true
        }
      ]);
    } finally {
      setLoading(false);
      setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: "smooth" }), 50);
    }
  };

  const sourceStyle: Record<string, string> = {
    report: "bg-blue-50 text-blue-700 border-blue-200",
    doc: "bg-cyan-50 text-cyan-700 border-cyan-200",
    term: "bg-amber-50 text-amber-700 border-amber-200",
  };

  if (spaceLoading) {
    return <LoadingState label={korean ? "프로젝트 AI를 불러오는 중..." : "Loading Project AI..."} />;
  }

  if (spaceError) {
    return <ErrorState title={korean ? "프로젝트 AI를 불러올 수 없습니다" : "Couldn't load Project AI"} desc={spaceError.message} />;
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border bg-card shrink-0 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-[color:var(--app-ai-soft)] flex items-center justify-center">
            <Sparkles className="w-4 h-4 text-[color:var(--app-ai)]" />
          </div>
          <div>
            <h2 className="font-semibold text-sm">{aiScope === "meeting" ? "Meeting AI" : "Project AI"}</h2>
            <p className="text-[10px] text-muted-foreground">{aiScope === "meeting" ? (korean ? "선택한 회의만 검색합니다" : "Searches the selected meeting only") : (korean ? "확정 회의록과 공식 지식만 검색합니다" : "Searches confirmed reports & official knowledge only")}</p>
          </div>
        </div>
        <div className="flex items-center gap-1.5 text-[10px] text-muted-foreground border border-border rounded-full px-2.5 py-1">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
          {spaceDetail?.name ? (korean ? `${spaceDetail.name} 범위만` : `${spaceDetail.name} only`) : (korean ? "권한 있는 자료만" : "Authorized sources only")}
        </div>
        <nav aria-label="AI scope" className="flex items-center gap-1 rounded-md bg-muted p-1">
          <Link className={`rounded px-3 py-1.5 text-xs font-medium transition-colors ${aiScope === "project" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`} to={projectAiHref}>Project AI</Link>
          {meetingAiHref ? <Link className={`rounded px-3 py-1.5 text-xs font-medium transition-colors ${aiScope === "meeting" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground hover:text-foreground"}`} to={meetingAiHref}>Meeting AI</Link> : <span className="px-3 py-1.5 text-xs text-muted-foreground">{korean ? "Meeting AI용 회의를 선택하세요" : "Select a meeting for Meeting AI"}</span>}
        </nav>
      </div>

      {aiScope === "meeting" ? (
        <div className="flex-1 min-h-0 overflow-y-auto p-6">
          {selectedMeetingId ? <MeetingAIChat meetingIdOverride={selectedMeetingId} /> : <EmptyState desc={korean ? "회의 화면에서 Meeting AI를 열면 해당 회의 범위만 검색합니다." : "Open Meeting AI from a meeting to keep the search scope limited to that meeting."} icon={<Sparkles className="w-5 h-5" />} title={korean ? "회의를 선택하세요" : "Select a meeting"} />}
        </div>
      ) : (
      <>
      {/* Messages */}
      <div className="flex-1 overflow-y-auto custom-scrollbar px-6 py-6 space-y-6">
        {messages.map((m, i) => (
          <div key={i} className={`flex gap-3 ${m.role === "user" ? "flex-row-reverse" : ""}`}>
            {m.role === "assistant" && (
              <div className="w-7 h-7 rounded-full bg-[color:var(--app-ai-soft)] flex items-center justify-center shrink-0 mt-0.5">
                <Sparkles className="w-3.5 h-3.5 text-[color:var(--app-ai)]" />
              </div>
            )}
            <div className={`max-w-[75%] space-y-2 ${m.role === "user" ? "items-end flex flex-col" : ""}`}>
              {m.noEvidence ? (
                <div className="rounded-xl px-4 py-3 border border-dashed border-border bg-muted/30 text-sm text-muted-foreground flex items-start gap-2.5">
                  <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5 text-muted-foreground/60" />
                  <div>
                    <p className="font-medium text-foreground text-xs mb-0.5">No evidence found</p>
                    <p className="text-xs leading-relaxed">{m.text}</p>
                  </div>
                </div>
              ) : (
                <div className={`rounded-xl px-4 py-3 text-sm leading-relaxed ${m.role === "user" ? "bg-foreground text-background rounded-tr-sm" : "bg-card border border-border text-foreground rounded-tl-sm"}`}>
                  {m.text.split("**").map((chunk, ci) => ci % 2 === 1 ? <strong key={ci}>{chunk}</strong> : chunk)}
                </div>
              )}
              {m.sources && m.sources.length > 0 && (
                <div className="flex flex-wrap gap-1.5 pl-1">
                  <span className="text-[10px] text-muted-foreground self-center">Sources:</span>
                  {m.sources.map(s => (
                    <span key={s.label} className={`text-[10px] font-medium px-2 py-0.5 rounded-full border ${sourceStyle[s.type]}`}>{s.label}</span>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
        {loading ? <p className="text-xs text-muted-foreground">근거를 확인하고 있습니다...</p> : null}
        {error ? <p className="text-xs text-red-600">{error}</p> : null}
        {messages.length === 1 && (
          <div className="space-y-2">
            <p className="text-xs text-muted-foreground font-medium">Suggested questions</p>
            <div className="grid grid-cols-1 gap-2 max-w-lg">
              {suggestions.map(s => (
                <button key={s} onClick={() => void send(s)} className="text-left px-4 py-2.5 rounded-lg border border-border bg-card hover:bg-muted/50 text-sm text-foreground transition-colors">
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="px-6 py-4 border-t border-border bg-card shrink-0">
        <div className="flex gap-2">
          <input
            value={input} onChange={e => setInput(e.target.value)}
            onKeyDown={e => { if (e.key === "Enter") { void send(); } }}
            placeholder="Ask about this project..."
            className="flex-1 px-4 py-2.5 rounded-lg border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
          />
          <button disabled={!input.trim() || loading || !session || !spaceId} onClick={() => void send()} className="px-4 py-2.5 bg-primary text-white rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors disabled:opacity-60">{loading ? "Checking..." : "Send"}</button>
        </div>
        <p className="text-[10px] text-muted-foreground mt-2 text-center">AI answers are based only on official project knowledge and authorized meetings.</p>
        {model ? <p className="text-[10px] text-muted-foreground mt-1 text-center">Model: {model}</p> : null}
      </div>
      </>
      )}
    </div>
  );
};

const GuestMeetingAiPage = () => {
  const { meetingId = "" } = useParams<{ meetingId: string }>();
  const navigate = useNavigate();
  const [meeting, setMeeting] = useState<MeetingDetailResponse | null>(null);
  const { session } = useAuthState();

  useEffect(() => {
    if (!session || !meetingId) return;
    void fetchMeetingDetail(session, meetingId).then(setMeeting).catch(() => setMeeting(null));
  }, [meetingId, session]);

  return (
    <div className="min-h-screen bg-muted/10 p-6 lg:p-10">
      <div className="mx-auto flex min-h-[calc(100vh-3rem)] max-w-5xl flex-col overflow-hidden rounded-lg border border-border bg-card">
        <header className="flex items-center justify-between border-b border-border px-5 py-4">
          <div className="min-w-0"><p className="text-xs font-semibold uppercase tracking-wide text-primary">Guest workspace</p><h1 className="mt-1 truncate text-lg font-semibold text-foreground">{meeting?.title ?? "Meeting AI"}</h1></div>
          <button className="rounded-md border border-foreground px-3 py-2 text-sm font-medium text-foreground hover:bg-muted" onClick={() => navigate(`/spaces?guest=1&meetingId=${encodeURIComponent(meetingId)}`)} type="button">Back to meeting</button>
        </header>
        <main className="min-h-0 flex-1 p-5 lg:p-8"><MeetingAIChat meetingIdOverride={meetingId} meetingDetailOverride={meeting} /></main>
      </div>
    </div>
  );
};

const LegacyMeetingAiRedirect = () => {
  const { spaceId = "", meetingId = "" } = useParams();
  return <Navigate replace to={`/spaces/${encodeURIComponent(spaceId)}/ai?scope=meeting&meetingId=${encodeURIComponent(meetingId)}`} />;
};

// ─────────────────────────────────────────────
// 20. Permission Denied
// ─────────────────────────────────────────────
const PermissionDenied = ({ type = "project" }: { type?: "project" | "meeting" | "report" }) => {
  const navigate = useNavigate();
  const { meetingId } = useParams<{ meetingId?: string }>();
  const config = {
    project: { title: "Access Denied", desc: "You don't have permission to view this project. Contact the project owner to request access.", back: "Go to Workspaces", backTo: "/spaces" },
    meeting:  { title: "Meeting Restricted", desc: "This meeting is not accessible with your current role. Only authorized members can view transcripts and reports.", back: "Back to Meetings", backTo: -1 },
    report:   { title: "Report Not Available", desc: "This report hasn't been confirmed yet, or you don't have permission to view draft reports.", back: "Back to Overview", backTo: -1 },
  }[type];

  return (
    <div className="flex flex-col items-center justify-center h-full py-24 px-6 text-center">
      <div className="w-14 h-14 rounded-2xl bg-muted flex items-center justify-center mb-5">
        <ShieldAlert className="w-7 h-7 text-muted-foreground" />
      </div>
      <h1 className="text-xl font-bold text-foreground mb-2">{config.title}</h1>
      <p className="text-sm text-muted-foreground max-w-sm leading-relaxed mb-8">{config.desc}</p>
      <div className="flex items-center gap-3">
        <button
          onClick={() => typeof config.backTo === "string" ? navigate(config.backTo) : navigate(-1)}
          className="px-5 py-2.5 rounded-lg border border-border text-sm font-medium hover:bg-muted transition-colors"
        >
          {config.back}
        </button>
        {type === "meeting" && meetingId ? (
          <button
            className="px-5 py-2.5 rounded-lg bg-foreground text-background text-sm font-medium hover:bg-foreground/90 transition-colors"
            onClick={() => navigate(`/meeting-access?meetingId=${encodeURIComponent(meetingId)}`)}
            type="button"
          >
            Request Access
          </button>
        ) : null}
      </div>
    </div>
  );
};


// --- Router Setup ---
// This is the active frontend route source. main.tsx renders App directly.

const router = createBrowserRouter([
  { path: "/", Component: LandingPage },
  { path: "/login", Component: LoginPage },
  { path: "/meeting-access", Component: MeetingAccess },
  { path: "/meetings/:meetingId", Component: MeetingAccessCompatRoute },
  { path: "/spaces", element: <RequireAuth><WorkspaceHome /></RequireAuth> },
  { path: "/guest", element: <RequireAuth><WorkspaceHome /></RequireAuth> },
  {
    path: "/spaces/:spaceId",
    element: <RequireAuth><AppShell /></RequireAuth>,
    children: [
      { index: true, Component: ProjectHome },
      { path: "meetings", Component: MeetingList },
      {
        path: "meetings/:meetingId",
        Component: MeetingContextLayout,
        children: [
          { index: true, Component: MeetingOverview },
          { path: "transcript", Component: MeetingTranscript },
          { path: "report", Component: MeetingReportRoute },
          { path: "tasks", Component: MeetingTaskCandidates },
          { path: "participants", Component: MeetingParticipants },
          { path: "ai", Component: LegacyMeetingAiRedirect },
        ]
      },
      { path: "tasks", Component: ProjectTasks },
      { path: "knowledge", Component: KnowledgeGraphPage },
      { path: "ai", Component: ProjectAIPage },
      { path: "calendar", Component: ProjectCalendar },
      { path: "members", Component: ProjectMembers },
      { path: "terms", Component: TermsDictionary },
      { path: "settings", Component: ProjectSettings },
      { path: "denied", Component: () => <PermissionDenied type="project" /> },
      { path: "ui", Component: ModalDemo },
    ],
  },
  { path: "/spaces/:spaceId/meetings/:meetingId/live/prejoin", element: <RequireAuth><PrejoinRoom /></RequireAuth> },
  { path: "/spaces/:spaceId/meetings/:meetingId/live", element: <RequireAuth><LiveMeeting /></RequireAuth> },
  { path: "/guest/meetings/:meetingId/ai", element: <RequireAuth><GuestMeetingAiPage /></RequireAuth> },
  { path: "/space-invitations/:spaceId/:invitationId", element: <RequireAuth><InvitationResponse /></RequireAuth> },
  { path: "/meeting-invitations/:meetingId/:invitationId", element: <RequireAuth><MeetingInvitationResponse /></RequireAuth> },
  { path: "/meetings", element: <RequireAuth><GlobalMeetings /></RequireAuth> },
  { path: "/settings", element: <RequireAuth><AccountSettings /></RequireAuth> },
  { path: "/denied", Component: () => <PermissionDenied type="project" /> },
]);

export function App() {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [loading, setLoading] = useState(true);
  const [theme, setTheme] = useState<ThemeMode>(storedTheme);
  const [locale, setLocale] = useState<AppLocale>(storedLocale);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    window.localStorage.setItem(THEME_STORAGE_KEY, theme);
  }, [theme]);

  useEffect(() => {
    document.documentElement.lang = locale;
    window.localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  }, [locale]);

  useEffect(() => {
    let active = true;

    void bootstrapAuthSession()
      .then((nextSession) => {
        if (!active) {
          return;
        }
        setSession(nextSession);
      })
      .catch(() => {
        if (!active) {
          return;
        }
        setSession(null);
      })
      .finally(() => {
        if (!active) {
          return;
        }
        setLoading(false);
      });

    const unsubscribe = subscribeToSessionInvalid(() => {
      if (!active) {
        return;
      }
      setSession(null);
      setLoading(false);
    });

    return () => {
      active = false;
      unsubscribe();
    };
  }, []);

  return (
    <AppPreferencesContext.Provider value={{ theme, setTheme, locale, setLocale }}>
      <AuthContext.Provider value={{ session, loading, setSession }}>
        <RouterProvider router={router} />
      </AuthContext.Provider>
    </AppPreferencesContext.Provider>
  );
}

export default App;
