import { RouterProvider, createBrowserRouter, Outlet, NavLink, useNavigate, useParams, useLocation, Link, useOutletContext, Navigate, useSearchParams } from "react-router-dom";
import {
  LayoutDashboard,
  FolderKanban,
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
  ChevronRight,
  ChevronLeft,
  Users,
  Calendar,
  BookOpen,
  ArrowRight,
  ShieldAlert,
  LogOut,
  Building,
  Activity,
  Filter,
  Mic,
  MicOff,
  VideoOff,
  X,
  PanelLeftClose,
  PanelLeftOpen,
  GripVertical
} from "lucide-react";
import {
  ConnectionState,
  Room,
  RoomEvent,
  Track,
  type Participant,
  type TrackPublication
} from "livekit-client";
import React, { createContext, useContext, useEffect, useRef, useState } from "react";
import {
  bootstrapAuthSession,
  loginWithGoogle,
  loginWithPassword,
  logoutAllDevices,
  logoutCurrentSession,
  signupWithPassword,
  type AuthSession
} from "./auth/session";
import { subscribeToSessionInvalid } from "./auth/sessionInvalidation";
import { AllDeviceLogoutModal } from "./components/AllDeviceLogoutModal";
import { GoogleCredentialButton } from "./components/GoogleCredentialButton";
import { chatMeetingAi, chatProjectAi, fetchProjectAiHistory } from "./api/ai";
import { createMeetingJoinRequest, fetchMeetingParticipants } from "./api/meetingAccess";
import {
  acceptSpaceInvitation,
  createSpace,
  createSpaceInvitation,
  deleteSpace,
  declineSpaceInvitation,
  fetchSpaceDetail,
  fetchSpaceMembers,
  fetchSpaces,
  removeSpaceMember,
  transferSpaceOwner,
  updateSpace,
  updateSpaceMemberRole
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
import { createProjectKnowledge, deleteProjectKnowledge, fetchProjectKnowledge, fetchProjectKnowledgeDetail, updateProjectKnowledge } from "./api/knowledge";
import { fetchCalendarEvents } from "./api/calendar";
import {
  fetchMeetingDialogue,
  startMeetingTranscription,
  stopActiveMeetingTranscription,
  stopMeetingTranscription
} from "./api/transcripts";
import { confirmMeetingReport, downloadMeetingReport, fetchMeetingReportDetail, fetchMeetingReports } from "./api/reports";
import { archiveDomainTerm, createDomainTerm, fetchDomainTerms, updateDomainTerm } from "./api/terms";
import { ApiRequestError } from "./api/client";
import { createInstantMeeting, createMeeting, fetchMeetingDetail, fetchMeetings, updateMeeting } from "./api/meetings";
import { fetchMeetingLiveKitToken } from "./api/live";
import type {
  CalendarEvent as ProjectCalendarEvent,
  DomainTerm,
  ProjectKnowledgeDetailResponse,
  ReportDetailResponse,
  ProjectKnowledgeType,
  SpaceDetail,
  SpaceMembersResponse,
  ProjectKnowledgeItem,
  MeetingDetailResponse,
  MeetingParticipantSummary,
  MeetingSummary,
  TaskCard,
  TaskCardPriority,
  TaskCardStatus,
  AiSource
} from "./types";

// --- Components ---

const GlossaryTerm = ({ children, definition }: { children: React.ReactNode, definition: string }) => {
  return (
    <span className="relative group inline-block cursor-help font-bold text-foreground border-b border-dashed border-foreground/40 hover:border-foreground transition-colors">
      {children}
      <span className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-56 p-2.5 bg-foreground text-background text-xs font-normal rounded-lg opacity-0 group-hover:opacity-100 transition-all translate-y-1 group-hover:translate-y-0 pointer-events-none z-50 text-left shadow-xl">
        <span className="font-bold block mb-1 text-primary-foreground/90 text-[10px] uppercase tracking-wider">Project Term</span>
        {definition}
        <span className="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-foreground"></span>
      </span>
    </span>
  );
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

function AudioTrackSurface({ publication }: { publication?: TrackPublication }) {
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
    return "bg-violet-50 text-violet-700 border-violet-200";
  }
  if (type === "report" || type === "meetingSummary") {
    return "bg-blue-50 text-blue-700 border-blue-200";
  }
  if (type === "decision" || type === "actionItem") {
    return "bg-emerald-50 text-emerald-700 border-emerald-200";
  }
  if (type === "glossary") {
    return "bg-amber-50 text-amber-700 border-amber-200";
  }
  return "bg-slate-100 text-slate-700 border-slate-200";
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
  toggleAI: () => void;
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
};

// --- Layouts ---

const AppShell = () => {
  const { session, setSession } = useAuthState();
  const authSession = session as AuthSession;
  const { spaceId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const [isAIOpen, setIsAIOpen] = useState(false);
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
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
    if (!isAIOpen || !spaceDetail || !session) {
      return;
    }

    let active = true;
    setProjectAiError("");
    setProjectAiModel("");
    setProjectAiMessages([
      {
        role: "assistant",
        text: `${spaceDetail.name}의 공식 지식과 접근 가능한 회의만 검색합니다.`
      }
    ]);

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
  }, [authSession, isAIOpen, session, spaceDetail]);

  const getBreadcrumbs = () => {
    const paths = location.pathname.split('/').filter(Boolean);
    if (paths.length === 0) return [];

    return paths.map((path, index) => {
      const url = `/${paths.slice(0, index + 1).join('/')}`;
      let label = path;
      if (path === 'spaces') label = 'Workspaces';
      if (path === spaceId) {
        label = spaceDetail?.name ?? (spaceLoading ? "Loading project..." : path);
      }
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
  const projectInitial = projectName.charAt(0).toUpperCase() || "P";
  const projectAiAvailable = spaceDetail?.aiEntrypoints.includes("project-ai") ?? true;
  const outletContext: ShellOutletContext = {
    toggleAI: () => setIsAIOpen(true),
    spaceDetail,
    spaceLoading,
    spaceError,
    reloadSpace: loadSpace
  };

  async function handleProjectAiAsk(question: string) {
    const trimmed = question.trim();
    if (!trimmed || !spaceDetail || projectAiLoading || !projectAiAvailable) {
      return;
    }

    setProjectAiInput("");
    setProjectAiError("");
    setProjectAiLoading(true);
    setProjectAiMessages((current) => [...current, { role: "user", text: trimmed }]);
    try {
      const response = await chatProjectAi(authSession, spaceDetail.id, { question: trimmed });
      setProjectAiModel(response.model);
      setProjectAiMessages((current) => [
        ...current,
        {
          role: "assistant",
          text: response.unsupported ? unsupportedAiMessage(response.unsupportedReason, "project") : response.answer,
          sources: response.sources,
          unsupported: response.unsupported
        }
      ]);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : "Project AI에 연결하지 못했습니다.";
      setProjectAiError(message);
      setProjectAiMessages((current) => [
        ...current,
        {
          role: "assistant",
          text: "Project AI 응답을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요.",
          unsupported: true
        }
      ]);
    } finally {
      setProjectAiLoading(false);
    }
  }

  return (
    <div className="flex h-screen bg-background font-sans text-foreground">
      {/* Sidebar */}
      <aside className={`${isSidebarOpen ? 'w-64' : 'w-[68px]'} border-r border-border bg-card flex flex-col h-full shrink-0 transition-all duration-300 relative z-20`}>
        {/* Project Selector & Collapse Button */}
        <div className="h-14 flex items-center border-b border-border shrink-0 overflow-hidden px-3">
          {isSidebarOpen ? (
            <>
              <div className="flex items-center gap-3 min-w-0 hover:bg-muted/50 cursor-pointer p-1.5 rounded transition-colors flex-1">
                <div className="w-6 h-6 rounded bg-foreground text-background flex items-center justify-center text-xs font-bold shrink-0">{projectInitial}</div>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold truncate leading-none">{projectName}</div>
                </div>
              </div>
              <button
                onClick={() => setIsSidebarOpen(false)}
                className="text-muted-foreground hover:bg-muted hover:text-foreground transition-colors shrink-0 p-1.5 rounded-md ml-1"
                title="Collapse Sidebar"
              >
                <PanelLeftClose className="w-4 h-4" />
              </button>
            </>
          ) : (
            <button
              onClick={() => setIsSidebarOpen(true)}
              className="text-muted-foreground hover:bg-muted hover:text-foreground transition-colors p-1.5 rounded-md mx-auto"
              title="Expand Sidebar"
            >
              <PanelLeftOpen className="w-4 h-4" />
            </button>
          )}
        </div>

        {/* Navigation */}
        <div className="flex-1 overflow-y-auto py-4 flex flex-col gap-6 custom-scrollbar overflow-x-hidden">
          <div className="px-3">
            <nav className="flex flex-col gap-0.5">
              <NavLink to={`/spaces/${spaceId}`} end title="Overview" className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive && !isAIOpen ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <LayoutDashboard className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>Overview</span>
              </NavLink>
              <button
                disabled={!projectAiAvailable}
                onClick={() => setIsAIOpen(!isAIOpen)}
                title="Project AI"
                className={`flex w-full items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${isAIOpen ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'} ${!projectAiAvailable ? 'opacity-50 cursor-not-allowed hover:bg-transparent hover:text-muted-foreground' : ''}`}
              >
                <Sparkles className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>Project AI</span>
              </button>
            </nav>
          </div>

          <div className="px-3">
            {isSidebarOpen && <div className="px-3 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-2 whitespace-nowrap">Collaboration</div>}
            {!isSidebarOpen && <div className="h-px w-full bg-border my-2"></div>}
            <nav className="flex flex-col gap-0.5">
              <NavLink to={`/spaces/${spaceId}/meetings`} title="Meetings" className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <Video className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>Meetings</span>
              </NavLink>
              <NavLink to={`/spaces/${spaceId}/tasks`} title="Tasks" className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <CheckSquare className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>Tasks</span>
              </NavLink>
              <NavLink to={`/spaces/${spaceId}/knowledge`} title="Knowledge" className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <Library className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>Knowledge</span>
              </NavLink>
              <NavLink to={`/spaces/${spaceId}/calendar`} title="Calendar" className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <Calendar className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>Calendar</span>
              </NavLink>
            </nav>
          </div>

          <div className="px-3">
            {isSidebarOpen && <div className="px-3 text-[11px] font-semibold text-muted-foreground uppercase tracking-wider mb-2 whitespace-nowrap">Administration</div>}
            {!isSidebarOpen && <div className="h-px w-full bg-border my-2"></div>}
            <nav className="flex flex-col gap-0.5">
              <NavLink to={`/spaces/${spaceId}/members`} title="Members" className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <Users className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>Members</span>
              </NavLink>
              <NavLink to={`/spaces/${spaceId}/terms`} title="Terms Dictionary" className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <BookOpen className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>Terms Dictionary</span>
              </NavLink>
              <NavLink to={`/spaces/${spaceId}/settings`} title="Settings" className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors ${isActive ? 'bg-muted text-foreground' : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground'} ${!isSidebarOpen && 'justify-center'}`}>
                <Settings className="w-4 h-4 shrink-0" />
                <span className={`transition-opacity duration-300 whitespace-nowrap ${isSidebarOpen ? 'opacity-100' : 'opacity-0 hidden'}`}>Settings</span>
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
            <div className="w-7 h-7 rounded bg-muted-foreground/20 flex items-center justify-center text-xs font-medium shrink-0">
              {sessionInitials(session)}
            </div>
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

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top Header */}
        <header className="h-14 px-6 flex items-center justify-between border-b border-border bg-background shrink-0">
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
            <div className="relative">
              <Search className="w-4 h-4 absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
              <input
                type="text"
                placeholder="Search..."
                className="pl-8 pr-3 py-1.5 w-64 rounded-md bg-muted/50 border border-transparent text-sm focus:bg-background focus:border-border focus:outline-none focus:ring-1 focus:ring-primary/50 transition-all"
              />
            </div>
            <button className="w-8 h-8 flex items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors relative">
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
              style={{ width: aiWidth }}
              className="border-l border-border bg-card shadow-[-10px_0_30px_-15px_rgba(0,0,0,0.1)] flex flex-col shrink-0 relative"
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

              <div className="p-4 border-b border-border flex items-center justify-between bg-muted/30">
                <div className="flex items-center gap-2">
                  <div className="w-6 h-6 rounded bg-primary/10 text-primary flex items-center justify-center">
                    <Sparkles className="w-3.5 h-3.5" />
                  </div>
                  <h2 className="font-semibold text-sm">Project AI</h2>
                </div>
                <button
                  onClick={() => setIsAIOpen(false)}
                  className="w-7 h-7 flex items-center justify-center rounded-md hover:bg-muted text-muted-foreground transition-colors"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>

              <div className="p-4 border-b border-border bg-card text-xs text-muted-foreground flex items-center gap-2">
                <ShieldAlert className="w-3.5 h-3.5" />
                Searching only official knowledge and authorized meetings.
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
                  <button className="whitespace-nowrap px-3 py-1.5 rounded-full border border-border text-xs text-muted-foreground hover:bg-muted transition-colors" onClick={() => void handleProjectAiAsk("최근 확정된 결정사항을 요약해줘")} type="button">최근 결정 요약</button>
                  <button className="whitespace-nowrap px-3 py-1.5 rounded-full border border-border text-xs text-muted-foreground hover:bg-muted transition-colors" onClick={() => void handleProjectAiAsk("현재 열려 있는 태스크를 요약해줘")} type="button">열린 태스크</button>
                </div>
                <div className="relative">
                  <input
                    type="text"
                    placeholder="Ask Project AI..."
                    value={projectAiInput}
                    onChange={(event) => setProjectAiInput(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        event.preventDefault();
                        void handleProjectAiAsk(projectAiInput);
                      }
                    }}
                    className="w-full bg-muted border-none rounded-xl pl-4 pr-10 py-3 text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 transition-all"
                  />
                  <button className="absolute right-2 top-1/2 -translate-y-1/2 w-7 h-7 rounded-md bg-foreground hover:bg-foreground/90 text-background flex items-center justify-center transition-colors disabled:opacity-60" disabled={!projectAiInput.trim() || projectAiLoading || !projectAiAvailable} onClick={() => void handleProjectAiAsk(projectAiInput)} type="button">
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

// 1. Landing Page (/)
const LandingPage = () => {
  const navigate = useNavigate();
  return (
    <div className="min-h-screen bg-background flex flex-col items-center justify-center text-center p-8">
      <div className="max-w-3xl space-y-6">
        <div className="w-16 h-16 bg-foreground text-background rounded-xl flex items-center justify-center mx-auto mb-8">
          <span className="text-2xl font-bold">M</span>
        </div>
        <h1 className="text-5xl font-bold tracking-tight text-foreground">
          Meetings shouldn't be the end.<br/>They should be the beginning.
        </h1>
        <p className="text-xl text-muted-foreground">
          MeetingMind seamlessly connects your meetings to transcripts, tasks, knowledge bases, and AI. Experience the continuous collaboration cycle.
        </p>
        <div className="pt-8">
          <button
            onClick={() => navigate('/spaces')}
            className="bg-foreground text-background px-8 py-3 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors inline-flex items-center gap-2"
          >
            Go to Workspaces <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
};

// 2. Workspace Home (/spaces)
const WorkspaceHome = () => {
  const { session } = useAuthState();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [spaces, setSpaces] = useState<Array<{
    id: string;
    name: string;
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
            role: space.role,
            meetings: space.meetingCount,
            members: membersResult.status === "fulfilled" ? membersResult.value.members.length : null,
            tasks: tasksResult.status === "fulfilled" ? tasksResult.value.tasks.length : null
          };
        })
      );
      setSpaces(hydrated);
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
            <h1 className="text-2xl font-bold text-foreground">Your Spaces</h1>
            <p className="text-sm text-muted-foreground">Select a project space to continue your work.</p>
          </div>
          <button
            className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors inline-flex items-center gap-2"
            onClick={() => {
              setCreateError("");
              setCreateOpen(true);
            }}
            type="button"
          >
            <Plus className="w-4 h-4" /> New Space
          </button>
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
                  <div className="w-10 h-10 rounded bg-muted flex items-center justify-center text-foreground font-bold group-hover:bg-foreground group-hover:text-background transition-colors">
                    {space.name.charAt(0)}
                  </div>
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
          <div className="w-full max-w-md bg-card rounded-xl border border-border shadow-2xl">
            <div className="px-6 py-5 border-b border-border flex items-start justify-between gap-4">
              <div>
                <h2 className="font-semibold text-foreground">New Space</h2>
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
  const { spaceId = "" } = useParams();
  const { session } = useAuthState();
  const { spaceDetail, spaceLoading, spaceError, reloadSpace } = useOutletContext<ShellOutletContext>();
  const [members, setMembers] = useState<SpaceMembersResponse["members"]>([]);
  const [knowledgeItems, setKnowledgeItems] = useState<ProjectKnowledgeItem[]>([]);
  const [instantMeetingPending, setInstantMeetingPending] = useState(false);
  const [instantMeetingError, setInstantMeetingError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    if (!session || !spaceId || !spaceDetail) {
      setMembers([]);
      setKnowledgeItems([]);
      return () => {
        active = false;
      };
    }

    void Promise.allSettled([
      fetchSpaceMembers(session, spaceId),
      fetchProjectKnowledge(session, spaceId)
    ]).then((results) => {
      if (!active) {
        return;
      }

      const [membersResult, knowledgeResult] = results;
      setMembers(membersResult.status === "fulfilled" ? membersResult.value.members : []);
      setKnowledgeItems(knowledgeResult.status === "fulfilled" ? knowledgeResult.value.items : []);
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
  const openTasks = spaceDetail.actionItems.filter((task) => task.status !== "DONE");
  const completedTasks = spaceDetail.actionItems.filter((task) => task.status === "DONE").length;
  const knowledgeIndexedCount = knowledgeItems.filter((item) => item.embeddingStatus === "COMPLETED").length;
  const knowledgeIndexedPercent = knowledgeItems.length > 0 ? Math.round((knowledgeIndexedCount / knowledgeItems.length) * 100) : 0;
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

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold truncate">Project Overview</h1>
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
              <div className="w-12 h-12 bg-blue-500/10 text-blue-600 rounded flex items-center justify-center shrink-0">
                <Video className="w-6 h-6" />
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-xs font-semibold text-blue-600 uppercase tracking-wider shrink-0">Up Next</span>
                  <span className="text-xs text-muted-foreground truncate">
                    {nextMeeting ? dateTimeLabel(nextMeeting.scheduledAt) : "No upcoming meeting"}
                  </span>
                </div>
                <h3 className="font-semibold text-foreground truncate block w-full">{nextMeeting?.title ?? "Schedule your next meeting"}</h3>
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
                    {instantMeetingPending ? "Starting..." : "Start meeting"}
                  </button>
                ) : null}
                <button
                  onClick={() => navigate(nextMeeting ? `meetings/${nextMeeting.id}` : "meetings")}
                  className="border border-border bg-background text-foreground px-4 py-2 rounded-md text-sm font-medium hover:bg-muted transition-colors shrink-0"
                  type="button"
                >
                  {nextMeeting ? "Open" : "View meetings"}
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
              <h3 className="font-semibold">My Open Tasks</h3>
              <button className="text-sm text-muted-foreground hover:text-foreground shrink-0" onClick={() => navigate("tasks")} type="button">View all</button>
            </div>
            <div className="divide-y divide-border">
              {openTasks.length > 0 ? openTasks.slice(0, 5).map((task) => (
                <div key={task.id} className="px-5 py-3 flex items-center justify-between hover:bg-muted/30 transition-colors cursor-pointer min-w-0 gap-4" onClick={() => navigate("tasks")}>
                  <div className="flex items-center gap-3 min-w-0 flex-1">
                    <div className="w-4 h-4 rounded border border-muted-foreground/40 shrink-0"></div>
                    <span className="text-sm font-medium truncate block w-full">{task.title}</span>
                  </div>
                  <div className="flex items-center gap-3 text-xs shrink-0">
                    <span className="text-muted-foreground">{task.dueDate ? dateLabel(task.dueDate) : "No due date"}</span>
                  </div>
                </div>
              )) : (
                <EmptyState
                  desc="Confirmed action items will appear here."
                  icon={<CheckSquare className="w-5 h-5" />}
                  title="No open tasks"
                />
              )}
            </div>
          </div>

          {/* Recent Transcripts/Reports */}
          <div className="bg-card border border-border rounded-lg overflow-hidden min-w-0">
            <div className="px-5 py-4 border-b border-border flex items-center justify-between">
              <h3 className="font-semibold">Recent Confirmed Reports</h3>
            </div>
            <div className="divide-y divide-border">
              {spaceDetail.recentReports.length > 0 ? spaceDetail.recentReports.slice(0, 5).map((report) => (
                <div key={report.id} className="px-5 py-3 flex items-center justify-between hover:bg-muted/30 transition-colors cursor-pointer min-w-0 gap-4" onClick={() => navigate(`meetings/${report.meetingId}/report`)}>
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
                  desc="Confirmed reports will appear here after a meeting report is finalized."
                  icon={<FileText className="w-5 h-5" />}
                  title="No reports yet"
                />
              )}
            </div>
          </div>
        </div>

        {/* Side Column */}
        <div className="lg:col-span-4 space-y-6 min-w-0">
          {/* Project Stats */}
          <div className="bg-card border border-border rounded-lg p-5">
            <h3 className="font-semibold mb-4 text-sm">Project Activity</h3>
            <div className="space-y-4">
              <div>
                <div className="flex justify-between text-xs mb-1.5">
                  <span className="text-muted-foreground">Task Completion</span>
                  <span className="font-medium">{completedTasks} / {spaceDetail.actionItems.length}</span>
                </div>
                <div className="h-1.5 w-full bg-muted rounded-full overflow-hidden">
                  <div className="h-full bg-foreground rounded-full" style={{ width: `${spaceDetail.actionItems.length > 0 ? Math.round((completedTasks / spaceDetail.actionItems.length) * 100) : 0}%` }}></div>
                </div>
              </div>
              <div>
                <div className="flex justify-between text-xs mb-1.5">
                  <span className="text-muted-foreground">Knowledge Indexed</span>
                  <span className="font-medium">{knowledgeIndexedPercent}%</span>
                </div>
                <div className="h-1.5 w-full bg-muted rounded-full overflow-hidden">
                  <div className="h-full bg-blue-500 rounded-full" style={{ width: `${knowledgeIndexedPercent}%` }}></div>
                </div>
              </div>
              <div>
                <div className="flex justify-between text-xs mb-1.5">
                  <span className="text-muted-foreground">Upcoming Meetings</span>
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
              <h3 className="font-semibold text-sm">Members</h3>
              <span className="text-xs text-muted-foreground">{members.length} people</span>
            </div>
            <div className="space-y-2.5">
              {members.length > 0 ? members.slice(0, 5).map((member) => {
                const name = member.displayName?.trim() || member.email || "Unknown";
                const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase() ?? "").join("") || "MM";
                return (
                  <div key={member.id} className="flex items-center gap-3">
                    <div className="w-7 h-7 rounded-full bg-muted flex items-center justify-center text-xs font-medium text-muted-foreground shrink-0">{initials}</div>
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
      return "bg-blue-50 text-blue-700 border-blue-200";
    }
    if (status === "IN_PROGRESS") {
      return "bg-emerald-50 text-emerald-700 border-emerald-200";
    }
    if (status === "ENDED") {
      return "bg-emerald-50 text-emerald-700 border-emerald-200";
    }
    if (status === "CANCELED") {
      return "bg-slate-100 text-slate-600 border-slate-200";
    }
    return "bg-amber-50 text-amber-700 border-amber-200";
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

  const filteredMeetings = meetings.filter((meeting) => {
    const matchesStatus = statusFilter === "ALL" || meeting.status === statusFilter;
    const normalizedQuery = searchQuery.trim().toLowerCase();
    const matchesQuery = !normalizedQuery
      || meeting.title.toLowerCase().includes(normalizedQuery)
      || meeting.host.toLowerCase().includes(normalizedQuery)
      || meetingStatusLabel(meeting.status).toLowerCase().includes(normalizedQuery);
    return matchesStatus && matchesQuery;
  });
  const hasMeetings = meetings.length > 0;

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Meetings</h1>
          <p className="text-sm text-muted-foreground">Manage upcoming, live, and past meetings.</p>
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
              <Play className="w-4 h-4" /> {instantPending ? "Starting..." : "Start Meeting"}
            </button>
            <button
              className={`px-4 py-2 rounded-md bg-card border text-sm font-medium flex items-center gap-2 transition-colors ${filtersOpen ? "border-foreground text-foreground" : "border-border hover:bg-muted"}`}
              onClick={() => setFiltersOpen((current) => !current)}
              type="button"
            >
              <Filter className="w-4 h-4" /> {filtersOpen ? "Hide Filters" : "Filter"}
            </button>
            <button
              className="px-4 py-2 rounded-md bg-foreground text-background text-sm font-medium hover:bg-foreground/90 flex items-center gap-2 transition-colors"
              onClick={openCreateModal}
              type="button"
            >
              <Plus className="w-4 h-4" /> Schedule Meeting
            </button>
          </div>
        ) : null}
      </div>

      {hasMeetings && filtersOpen ? (
        <div className="bg-card border border-border rounded-lg p-4 flex flex-col gap-4 md:flex-row md:items-end">
          <div className="flex-1 min-w-0">
            <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">Search</label>
            <input
              className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
              onChange={(event) => setSearchQuery(event.target.value)}
              placeholder="Search by title, host, or status"
              type="search"
              value={searchQuery}
            />
          </div>
          <div className="w-full md:w-56">
            <label className="block text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">Status</label>
            <select
              className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
              onChange={(event) => setStatusFilter(event.target.value as typeof statusFilter)}
              value={statusFilter}
            >
              <option value="ALL">All statuses</option>
              <option value="SCHEDULED">Upcoming</option>
              <option value="IN_PROGRESS">In Progress</option>
              <option value="ENDED">Confirmed</option>
              <option value="CANCELED">Canceled</option>
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
            Reset
          </button>
        </div>
      ) : null}

      {loading ? <LoadingState label="Loading meetings..." /> : null}

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
        <div className="bg-card border border-border rounded-lg overflow-hidden">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase border-b border-border bg-muted/30">
              <tr>
                <th className="px-6 py-4 font-medium">Meeting Topic</th>
                <th className="px-6 py-4 font-medium">Status</th>
                <th className="px-6 py-4 font-medium">Date & Time</th>
                <th className="px-6 py-4 font-medium">Host</th>
                <th className="px-6 py-4 font-medium">Participants</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {filteredMeetings.map((meeting) => (
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
                </tr>
              ))}
            </tbody>
          </table>
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
    </div>
  );
};

// 5. Meeting Context Layout (/spaces/:spaceId/meetings/:meetingId/*)
const MeetingContextLayout = () => {
  const { spaceId, meetingId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { session } = useAuthState();
  const [meetingDetail, setMeetingDetail] = useState<MeetingDetailResponse | null>(null);
  const [meetingLoading, setMeetingLoading] = useState(true);
  const [meetingError, setMeetingError] = useState<Error | null>(null);

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
      return "bg-blue-50 text-blue-700 border-blue-200";
    }
    if (status === "IN_PROGRESS") {
      return "bg-emerald-50 text-emerald-700 border-emerald-200";
    }
    if (status === "ENDED") {
      return "bg-emerald-50 text-emerald-700 border-emerald-200";
    }
    if (status === "CANCELED") {
      return "bg-slate-100 text-slate-600 border-slate-200";
    }
    return "bg-amber-50 text-amber-700 border-amber-200";
  }

  function timeRangeLabel() {
    if (!meetingDetail) {
      return "Loading schedule...";
    }
    const scheduledAt = new Date(meetingDetail.scheduledAt);
    const scheduledEndAt = new Date(meetingDetail.scheduledEndAt);
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

  if (meetingLoading) {
    return <LoadingState label="Loading meeting..." />;
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

  if (meetingError) {
    return (
      <ErrorState
        desc={meetingError.message}
        onRetry={() => {
          void loadMeeting();
        }}
        title="Couldn't load meeting"
      />
    );
  }

  if (!meetingDetail) {
    return <LoadingState label="Loading meeting..." />;
  }

  const joinTarget = meetingDetail.status === "IN_PROGRESS" ? "live" : "live/prejoin";
  const outletContext: MeetingOutletContext = {
    meetingDetail,
    meetingLoading,
    meetingError,
    reloadMeeting: loadMeeting
  };

  return (
    <div className="flex flex-col h-full bg-background">
      {/* Meeting Context Header */}
      <div className="border-b border-border bg-card px-8 pt-6 pb-0 flex flex-col gap-6 shrink-0">
        <div className="flex items-start justify-between">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase tracking-wider border ${meetingStatusStyle(meetingDetail.status)}`}>{meetingStatusLabel(meetingDetail.status)}</span>
              <span className="text-sm text-muted-foreground font-medium">{timeRangeLabel()}</span>
            </div>
            <h1 className="text-2xl font-bold text-foreground">{meetingDetail.title}</h1>
          </div>
          <button
            onClick={() => navigate(joinTarget)}
            className="bg-primary text-primary-foreground px-6 py-2.5 rounded-md text-sm font-bold shadow-sm hover:bg-primary/90 flex items-center gap-2 transition-colors"
          >
            <Video className="w-4 h-4" /> {meetingDetail.status === "IN_PROGRESS" ? "Resume Meeting Room" : "Enter Meeting Room"}
          </button>
        </div>

        {/* Context Navigation Tabs */}
        <nav className="flex items-center gap-6 text-sm font-medium">
          <NavLink
            to={`/spaces/${spaceId}/meetings/${meetingId}`}
            end
            className={({ isActive }) => `pb-3 border-b-2 transition-colors ${isActive ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground hover:border-border'}`}
          >
            Overview
          </NavLink>
          <NavLink
            to={`/spaces/${spaceId}/meetings/${meetingId}/transcript`}
            className={({ isActive }) => `pb-3 border-b-2 transition-colors ${isActive ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground hover:border-border'}`}
          >
            Transcript
          </NavLink>
          <NavLink
            to={`/spaces/${spaceId}/meetings/${meetingId}/report`}
            className={({ isActive }) => `pb-3 border-b-2 transition-colors ${isActive ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground hover:border-border'}`}
          >
            AI Report
          </NavLink>
          <NavLink
            to={`/spaces/${spaceId}/meetings/${meetingId}/tasks`}
            className={({ isActive }) => `pb-3 border-b-2 transition-colors ${isActive ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground hover:border-border'}`}
          >
            Task Candidates
          </NavLink>
          <NavLink
            to={`/spaces/${spaceId}/meetings/${meetingId}/ai`}
            className={({ isActive }) => `pb-3 border-b-2 transition-colors flex items-center gap-1.5 ${isActive ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground hover:border-border'}`}
          >
            <Sparkles className="w-3.5 h-3.5" /> Meeting AI
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
          <p className="text-sm text-muted-foreground leading-relaxed">
            {meetingDetail.description?.trim() || "No meeting description has been added yet."}
          </p>
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
// 5.2 Transcript
const MeetingTranscript = () => {
  const { session } = useAuthState();
  const navigate = useNavigate();
  const { spaceId = "", meetingId = "" } = useParams();
  const { meetingDetail } = useOutletContext<MeetingOutletContext>();
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [dialogueStatus, setDialogueStatus] = useState<"PROCESSING" | "COMPLETED" | "FAILED" | null>(null);
  const [segments, setSegments] = useState<Array<{
    segmentId: string;
    speakerId: string;
    speakerLabel: string;
    speakerName: string | null;
    startMs: number;
    endMs: number;
    text: string;
  }>>([]);
  const [partials, setPartials] = useState<Array<{
    speakerLabel: string;
    speakerName: string | null;
    text: string;
  }>>([]);

  const loadTranscript = React.useCallback(async () => {
    if (!session || !meetingId) {
      setSegments([]);
      setPartials([]);
      setDialogueStatus(null);
      setLoading(false);
      setError(null);
      return;
    }
    setError(null);
    try {
      const response = await fetchMeetingDialogue(session, meetingId);
      setDialogueStatus(response.status);
      setSegments(response.rows);
      setPartials(response.partials);
    } catch (cause) {
      setSegments([]);
      setPartials([]);
      setDialogueStatus(null);
      setError(cause instanceof Error ? cause : new Error("전사를 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [meetingId, session]);

  useEffect(() => {
    setLoading(true);
    void loadTranscript();
    const intervalId = window.setInterval(() => {
      void loadTranscript();
    }, 2500);
    return () => {
      window.clearInterval(intervalId);
    };
  }, [loadTranscript]);

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

  const entries = [
    ...segments.map((segment) => ({
      key: segment.segmentId,
      speakerId: segment.speakerId,
      speakerName: segment.speakerName ?? segment.speakerLabel,
      startMs: segment.startMs,
      text: segment.text,
      isPartial: false
    })),
    ...partials.map((partial, index) => ({
      key: `partial-${partial.speakerLabel}-${index}`,
      speakerId: partial.speakerLabel,
      speakerName: partial.speakerName ?? partial.speakerLabel,
      startMs: 0,
      text: partial.text,
      isPartial: true
    }))
  ];

  const filtered = entries.filter((entry) =>
    search === ""
    || entry.text.toLowerCase().includes(search.toLowerCase())
    || entry.speakerName.toLowerCase().includes(search.toLowerCase())
  );

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
          title="Transcript unavailable"
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

      {!loading && !error && dialogueStatus === "COMPLETED" && filtered.length === 0 && segments.length === 0 && partials.length === 0 ? (
        <EmptyState
          desc="No transcript segments were saved for this meeting."
          icon={<FileText className="w-5 h-5" />}
          title="No transcript yet"
        />
      ) : null}

      {!loading && !error && filtered.length === 0 && (segments.length > 0 || partials.length > 0) ? (
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

// 5.3 AI Report
function parseReportSections(markdown: string | null) {
  const decisions: string[] = [];
  const actions: string[] = [];
  if (!markdown) {
    return { decisions, actions };
  }

  let section = "";
  for (const rawLine of markdown.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) {
      continue;
    }
    if (line.startsWith("##")) {
      section = line.replace(/^##+\s*/, "").toLowerCase();
      continue;
    }
    if (!line.startsWith("-")) {
      continue;
    }
    const value = line.replace(/^-\s*/, "").trim();
    if (!value) {
      continue;
    }
    if (section.includes("결정")) {
      decisions.push(value);
      continue;
    }
    if (section.includes("action")) {
      actions.push(value);
    }
  }

  return { decisions, actions };
}

function parseActionItemLine(line: string) {
  const separators = [" — ", " – ", " - ", ": "];
  for (const separator of separators) {
    if (line.includes(separator)) {
      const [left, ...rest] = line.split(separator);
      const right = rest.join(separator).trim();
      if (left.trim() && right) {
        return {
          who: left.trim(),
          what: right,
          due: "Not specified"
        };
      }
    }
  }
  return {
    who: "Not specified",
    what: line,
    due: "Not specified"
  };
}

const MeetingAIReport = () => {
  const { session } = useAuthState();
  const navigate = useNavigate();
  const { spaceId = "", meetingId = "" } = useParams();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [downloading, setDownloading] = useState<"markdown" | "pdf" | "docx" | null>(null);
  const [report, setReport] = useState<ReportDetailResponse | null>(null);

  const loadReport = React.useCallback(async () => {
    if (!session || !meetingId) {
      setReport(null);
      setLoading(false);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const listResponse = await fetchMeetingReports(session, meetingId);
      const currentReport =
        listResponse.reports.find((item) => item.isCurrent)
        ?? [...listResponse.reports].sort((left, right) => right.version - left.version)[0]
        ?? null;

      if (!currentReport) {
        setReport(null);
      } else {
        const detail = await fetchMeetingReportDetail(session, meetingId, currentReport.id);
        setReport(detail);
      }
    } catch (cause) {
      setReport(null);
      setError(cause instanceof Error ? cause : new Error("회의록을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [meetingId, session]);

  useEffect(() => {
    void loadReport();
  }, [loadReport]);

  const reportSections = parseReportSections(report?.markdown ?? null);
  const decisionItems = reportSections.decisions.length > 0 ? reportSections.decisions : [];
  const actionItems = reportSections.actions.map(parseActionItemLine);

  async function handleConfirm() {
    if (!session || !meetingId || !report || report.status === "CONFIRMED" || confirming) {
      return;
    }
    setConfirming(true);
    try {
      await confirmMeetingReport(session, meetingId, report.id);
      await loadReport();
    } catch (cause) {
      setError(cause instanceof Error ? cause : new Error("회의록 확정에 실패했습니다."));
    } finally {
      setConfirming(false);
    }
  }

  async function handleDownload(format: "markdown" | "pdf" | "docx") {
    if (!session || !meetingId || !report || downloading) {
      return;
    }
    setDownloading(format);
    try {
      const blob = await downloadMeetingReport(session, meetingId, report.id, format);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `meeting-report-${report.id}.${format === "markdown" ? "md" : format}`;
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    } catch (cause) {
      setError(cause instanceof Error ? cause : new Error("회의록 다운로드에 실패했습니다."));
    } finally {
      setDownloading(null);
    }
  }

  if (loading) {
    return <LoadingState label="Loading report..." />;
  }

  if (error instanceof ApiRequestError && error.status === 403) {
    return <PermissionDenied type="report" />;
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
        desc="The report may not exist yet or you no longer have access."
        icon={<FileText className="w-5 h-5" />}
        title="Report unavailable"
      />
    );
  }

  if (error) {
    return (
      <ErrorState
        desc={error.message}
        onRetry={() => { void loadReport(); }}
        title="Couldn't load report"
      />
    );
  }

  if (!report) {
    return (
      <EmptyState
        desc="Generate or confirm a meeting report to see the official summary here."
        icon={<FileText className="w-5 h-5" />}
        title="No report yet"
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* Status Bar */}
      <div className={`flex items-center justify-between rounded-lg px-5 py-3 border ${report.status === "CONFIRMED" ? "bg-emerald-50 border-emerald-200" : "bg-amber-50 border-amber-200"}`}>
        <div className="flex items-center gap-2">
          {report.status === "CONFIRMED"
            ? <><span className="w-2 h-2 rounded-full bg-emerald-500 inline-block"></span><span className="text-sm font-medium text-emerald-800">Report Confirmed — added to project knowledge base</span></>
            : <><span className="w-2 h-2 rounded-full bg-amber-500 inline-block"></span><span className="text-sm font-medium text-amber-800">Draft — review and confirm to add to knowledge base</span></>
          }
        </div>
        <div className="flex items-center gap-2">
          <button
            className="px-3 py-1.5 rounded-md border border-border text-xs font-semibold hover:bg-background transition-colors disabled:opacity-60"
            disabled={downloading !== null}
            onClick={() => void handleDownload("markdown")}
            type="button"
          >
            {downloading === "markdown" ? "Downloading..." : "MD"}
          </button>
          <button
            className="px-3 py-1.5 rounded-md border border-border text-xs font-semibold hover:bg-background transition-colors disabled:opacity-60"
            disabled={downloading !== null}
            onClick={() => void handleDownload("pdf")}
            type="button"
          >
            {downloading === "pdf" ? "Downloading..." : "PDF"}
          </button>
          {report.status !== "CONFIRMED" ? (
            <button
              onClick={() => void handleConfirm()}
              className="bg-foreground text-background text-xs font-semibold px-4 py-1.5 rounded-md hover:bg-foreground/90 transition-colors disabled:opacity-60"
              disabled={confirming}
              type="button"
            >
              {confirming ? "Confirming..." : "Confirm Report"}
            </button>
          ) : null}
        </div>
      </div>

      {/* Summary */}
      <div className="bg-card border border-border rounded-lg p-6 space-y-3">
        <h3 className="font-semibold text-foreground">Meeting Summary</h3>
        <p className="text-sm text-foreground/80 leading-relaxed">{report.summary}</p>
      </div>

      {/* Key Decisions */}
      <div className="bg-card border border-border rounded-lg p-6">
        <h3 className="font-semibold text-foreground mb-4">Key Decisions</h3>
        {decisionItems.length > 0 ? (
          <ul className="space-y-3">
            {decisionItems.map((decision, index) => (
              <li key={`${report.id}-decision-${index}`} className="flex gap-3 text-sm text-foreground/80">
                <span className="w-5 h-5 rounded-full bg-primary/10 text-primary text-xs font-bold flex items-center justify-center shrink-0 mt-0.5">{index + 1}</span>
                {decision}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-muted-foreground">No decision list was explicitly captured in this report.</p>
        )}
      </div>

      {/* Action Items */}
      <div className="bg-card border border-border rounded-lg p-6">
        <h3 className="font-semibold text-foreground mb-4">Action Items</h3>
        {actionItems.length > 0 ? (
          <div className="space-y-3">
            {actionItems.map((action, index) => (
              <div key={`${report.id}-action-${index}`} className="flex items-start gap-3 py-2 border-b border-border last:border-0">
                <div className="w-4 h-4 rounded border border-muted-foreground/40 shrink-0 mt-0.5"></div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm text-foreground">{action.what}</p>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="text-xs text-muted-foreground">→ {action.who}</span>
                    <span className="text-xs text-muted-foreground">· {action.due}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">No action items were explicitly captured in this report.</p>
        )}
      </div>
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
      return "bg-emerald-50 text-emerald-700 border-emerald-200";
    }
    if (status === "DISMISSED") {
      return "bg-slate-100 text-slate-600 border-slate-200";
    }
    return "bg-blue-50 text-blue-700 border-blue-200";
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
const MeetingAIChat = () => {
  const { session } = useAuthState();
  const { meetingId = "" } = useParams();
  const { meetingDetail } = useOutletContext<MeetingOutletContext>();
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
  const [dueDate, setDueDate] = useState("");
  const [priority, setPriority] = useState<TaskCardPriority>("MEDIUM");
  const [status, setStatus] = useState<TaskCardStatus>("TODO");

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
    HIGH: "bg-red-50 text-red-600 border-red-200",
    MEDIUM: "bg-amber-50 text-amber-600 border-amber-200",
    LOW: "bg-slate-50 text-slate-500 border-slate-200"
  };

  const memberLookup = new Map(
    members.map((member) => [
      member.userId,
      {
        name: member.displayName?.trim() || member.email || "Unknown",
        initials: (member.displayName?.trim() || member.email || "MM")
          .split(/\s+/)
          .filter(Boolean)
          .slice(0, 2)
          .map((part) => part[0]?.toUpperCase() ?? "")
          .join("") || "MM"
      }
    ])
  );

  const normalizedTasks = tasks.filter((task) => !showOpenOnly || task.status !== "DONE");
  const columns = [
    { id: "todo", label: "To Do", color: "text-slate-600", dot: "bg-slate-400", status: "TODO" as TaskCardStatus },
    { id: "inprogress", label: "In Progress", color: "text-blue-600", dot: "bg-blue-500", status: "IN_PROGRESS" as TaskCardStatus },
    { id: "review", label: "In Review", color: "text-purple-600", dot: "bg-purple-500", status: null },
    { id: "done", label: "Done", color: "text-emerald-600", dot: "bg-emerald-500", status: "DONE" as TaskCardStatus }
  ];

  const tasksByColumn = columns.map((column) => ({
    ...column,
    tasks: column.status ? normalizedTasks.filter((task) => task.status === column.status) : []
  }));

  return (
    <div className="p-6 flex flex-col gap-4 h-full">
      <div className="flex items-center justify-between shrink-0">
        <h1 className="text-xl font-bold">Tasks</h1>
        <div className="flex items-center gap-2">
          <button
            className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground px-3 py-1.5 rounded-md hover:bg-muted transition-colors"
            onClick={() => setShowOpenOnly((current) => !current)}
            type="button"
          >
            <Filter className="w-3.5 h-3.5" /> {showOpenOnly ? "Show all" : "Open only"}
          </button>
          <button
            className="flex items-center gap-1.5 text-sm font-medium bg-foreground text-background px-3 py-1.5 rounded-md hover:bg-foreground/90 transition-colors"
            onClick={openCreateModal}
            type="button"
          >
            <Plus className="w-3.5 h-3.5" /> New Task
          </button>
        </div>
      </div>

      {loading ? <LoadingState label="Loading tasks..." /> : null}

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
            <div key={col.id} className="flex flex-col gap-3 min-w-[280px] w-[280px]">
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
                      className="bg-card border border-border rounded-lg p-3.5 cursor-pointer hover:border-border/80 hover:shadow-sm transition-all group"
                      onClick={() => openEditModal(task)}
                    >
                      <p className="text-sm font-medium text-foreground leading-snug mb-3">{task.title}</p>
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-1.5">
                          <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded border ${priorityStyle[task.priority]}`}>{priorityLabel(task.priority)}</span>
                          <span className="text-[10px] text-muted-foreground px-1.5 py-0.5 rounded border border-border bg-muted/30">{chip}</span>
                        </div>
                        <div className="w-6 h-6 rounded-full bg-muted flex items-center justify-center text-[10px] font-bold text-muted-foreground shrink-0">
                          {assignee?.initials ?? "—"}
                        </div>
                      </div>
                    </div>
                  );
                }) : (
                  <div className="bg-card border border-dashed border-border rounded-lg p-4 text-xs text-muted-foreground min-h-[92px] flex items-center justify-center text-center">
                    {col.id === "review" ? "No review state in the current backend workflow." : "No tasks in this column."}
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
                  <select
                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                    onChange={(event) => setAssigneeId(event.target.value)}
                    value={assigneeId}
                  >
                    <option value="">Unassigned</option>
                    {members.map((member) => (
                      <option key={member.id} value={member.userId}>
                        {member.displayName?.trim() || member.email || member.userId}
                      </option>
                    ))}
                  </select>
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

// 7. Knowledge Base — Graph View
type KnowledgeNodeType = "hub" | ProjectKnowledgeItem["type"];
type KNode = {
  id: string;
  label: string;
  type: KnowledgeNodeType;
  x: number;
  y: number;
  desc: string;
  connections: string[];
  sourceMeetingId: string | null;
  embeddingStatus?: ProjectKnowledgeItem["embeddingStatus"];
  updatedAt?: string;
};
type KEdge = { from: string; to: string };
type KFolder = { id: string; label: string; nodeIds: string[] };

const NODE_STYLE: Record<KnowledgeNodeType, { fill: string; stroke: string; label: string; dot: string }> = {
  hub: { fill: "#09090B", stroke: "#09090B", label: "Hub", dot: "bg-foreground" },
  report: { fill: "#2563EB", stroke: "#2563EB", label: "Report", dot: "bg-blue-600" },
  decision: { fill: "#7C3AED", stroke: "#7C3AED", label: "Decision", dot: "bg-violet-600" },
  manual: { fill: "#059669", stroke: "#059669", label: "Manual", dot: "bg-emerald-600" },
  external: { fill: "#D97706", stroke: "#D97706", label: "External", dot: "bg-amber-600" }
};

const KNOWLEDGE_TYPE_LABELS: Record<ProjectKnowledgeItem["type"], string> = {
  report: "Report",
  decision: "Decision",
  manual: "Manual",
  external: "External"
};

const KNOWLEDGE_STATUS_LABELS: Record<ProjectKnowledgeItem["embeddingStatus"], string> = {
  PENDING: "Pending",
  PROCESSING: "Processing",
  COMPLETED: "Search Ready",
  FAILED: "Failed"
};

const FolderIcon = ({ open }: { open: boolean }) => (
  <svg width="14" height="14" viewBox="0 0 14 14" fill="none" className="shrink-0 text-muted-foreground">
    {open
      ? <path d="M1 4.5C1 3.67 1.67 3 2.5 3H5.38L6.5 4.5H11.5C12.33 4.5 13 5.17 13 6V10.5C13 11.33 12.33 12 11.5 12H2.5C1.67 12 1 11.33 1 10.5V4.5Z" fill="currentColor" opacity="0.15" stroke="currentColor" strokeWidth="1"/>
      : <path d="M1 4.5C1 3.67 1.67 3 2.5 3H5.38L6.5 4.5H11.5C12.33 4.5 13 5.17 13 6V10.5C13 11.33 12.33 12 11.5 12H2.5C1.67 12 1 11.33 1 10.5V4.5Z" stroke="currentColor" strokeWidth="1" fill="none"/>
    }
  </svg>
);

const FileIcon = ({ type }: { type: KnowledgeNodeType }) => {
  const colors: Record<KnowledgeNodeType, string> = {
    hub: "text-foreground",
    report: "text-blue-500",
    decision: "text-violet-500",
    manual: "text-emerald-500",
    external: "text-amber-500"
  };
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" fill="none" className={`shrink-0 ${colors[type]}`}>
      <rect x="1.5" y="0.5" width="7" height="9" rx="1" stroke="currentColor" strokeWidth="1" fill="currentColor" fillOpacity="0.1"/>
      <path d="M8.5 0.5L10.5 2.5V10.5C10.5 11.05 10.05 11.5 9.5 11.5H2.5" stroke="currentColor" strokeWidth="1" fill="none"/>
    </svg>
  );
};

function buildKnowledgeNodes(items: ProjectKnowledgeItem[], projectName: string): KNode[] {
  const hubId = "hub";
  const itemNodes = items.map((item, index) => {
    const ring = Math.floor(index / 8);
    const indexInRing = index % 8;
    const nodesInRing = Math.min(8, items.length - ring * 8);
    const angle = (Math.PI * 2 * indexInRing) / Math.max(nodesInRing, 1) - Math.PI / 2;
    const radius = 180 + ring * 110;
    return {
      id: item.id,
      label: item.title,
      type: item.type,
      x: 500 + Math.cos(angle) * radius,
      y: 300 + Math.sin(angle) * radius,
      desc: item.contentPreview,
      connections: [hubId],
      sourceMeetingId: item.sourceMeetingId,
      embeddingStatus: item.embeddingStatus,
      updatedAt: item.updatedAt
    } satisfies KNode;
  });

  return [
    {
      id: hubId,
      label: projectName,
      type: "hub",
      x: 500,
      y: 300,
      desc: `Central project hub for ${projectName}.`,
      connections: itemNodes.map((node) => node.id),
      sourceMeetingId: null
    },
    ...itemNodes
  ];
}

function buildKnowledgeEdges(nodes: KNode[]): KEdge[] {
  return Array.from(
    new Set(nodes.flatMap((node) => node.connections.map((connection) => [node.id, connection].sort().join("--"))))
  ).map((key) => {
    const [from, to] = key.split("--");
    return { from, to };
  });
}

function buildKnowledgeFolders(nodes: KNode[], projectName: string): KFolder[] {
  const itemNodes = nodes.filter((node) => node.type !== "hub");
  return [
    { id: "f-hub", label: projectName, nodeIds: ["hub"] },
    { id: "f-reports", label: "Reports", nodeIds: itemNodes.filter((node) => node.type === "report").map((node) => node.id) },
    { id: "f-decisions", label: "Decisions", nodeIds: itemNodes.filter((node) => node.type === "decision").map((node) => node.id) },
    { id: "f-manual", label: "Manual Notes", nodeIds: itemNodes.filter((node) => node.type === "manual").map((node) => node.id) },
    { id: "f-external", label: "External Sources", nodeIds: itemNodes.filter((node) => node.type === "external").map((node) => node.id) }
  ].filter((folder) => folder.nodeIds.length > 0);
}

const ProjectKnowledge = () => {
  const { session } = useAuthState();
  const navigate = useNavigate();
  const { spaceId = "" } = useParams();
  const { spaceDetail } = useOutletContext<ShellOutletContext>();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedDetail, setSelectedDetail] = useState<ProjectKnowledgeDetailResponse | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<Error | null>(null);
  const [hovered, setHovered] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [knowledgeItems, setKnowledgeItems] = useState<ProjectKnowledgeItem[]>([]);
  const [openFolders, setOpenFolders] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [createMode, setCreateMode] = useState(false);
  const [pendingAction, setPendingAction] = useState<null | "create" | "update" | "archive">(null);
  const [mutationError, setMutationError] = useState("");
  const [notice, setNotice] = useState("");
  const [archiveConfirmOpen, setArchiveConfirmOpen] = useState(false);
  const [draft, setDraft] = useState<{
    type: ProjectKnowledgeType;
    title: string;
    content: string;
    sourceMeetingId: string;
  }>({
    type: "manual",
    title: "",
    content: "",
    sourceMeetingId: ""
  });
  const [transform, setTransform] = useState({ x: 0, y: 0, scale: 1 });
  const svgRef = useRef<SVGSVGElement>(null);
  const isPanning = useRef(false);
  const lastMouse = useRef({ x: 0, y: 0 });
  const projectName = spaceDetail?.name ?? "Project Knowledge";
  const currentRole = spaceDetail?.role ?? "MEMBER";
  const canManageKnowledge = currentRole === "OWNER" || currentRole === "ADMIN";
  const nodes = React.useMemo(() => buildKnowledgeNodes(knowledgeItems, projectName), [knowledgeItems, projectName]);
  const edges = React.useMemo(() => buildKnowledgeEdges(nodes), [nodes]);
  const folders = React.useMemo(() => buildKnowledgeFolders(nodes, projectName), [nodes, projectName]);
  const selected = nodes.find((node) => node.id === selectedId) ?? null;
  const saving = pendingAction !== null;
  const isCreating = pendingAction === "create";
  const isUpdating = pendingAction === "update";
  const isArchiving = pendingAction === "archive";

  const loadKnowledge = React.useCallback(async () => {
    if (!session || !spaceId) {
      setKnowledgeItems([]);
      setLoading(false);
      setError(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await fetchProjectKnowledge(session, spaceId);
      setKnowledgeItems(response.items);
      setSelectedId((current) => current && response.items.some((item) => item.id === current) ? current : null);
    } catch (cause) {
      setKnowledgeItems([]);
      setError(cause instanceof Error ? cause : new Error("지식 목록을 불러오지 못했습니다."));
    } finally {
      setLoading(false);
    }
  }, [session, spaceId]);

  useEffect(() => {
    void loadKnowledge();
  }, [loadKnowledge]);

  useEffect(() => {
    setOpenFolders(new Set(folders.map((folder) => folder.id)));
  }, [folders]);

  useEffect(() => {
    if (!session || !spaceId || !selected || selected.type === "hub") {
      setSelectedDetail(null);
      setDetailError(null);
      setDetailLoading(false);
      return;
    }

    let cancelled = false;
    setDetailLoading(true);
    setDetailError(null);

    void fetchProjectKnowledgeDetail(session, spaceId, selected.id)
      .then((detail) => {
        if (!cancelled) {
          setSelectedDetail(detail);
          setDraft({
            type: detail.type,
            title: detail.title,
            content: detail.content,
            sourceMeetingId: detail.sourceMeetingId ?? ""
          });
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setSelectedDetail(null);
          setDetailError(cause instanceof Error ? cause : new Error("지식 상세를 불러오지 못했습니다."));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setDetailLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selected, session, spaceId]);

  async function reloadSelectedDetail() {
    if (!session || !spaceId || !selected || selected.type === "hub") {
      return;
    }

    setDetailLoading(true);
    setDetailError(null);
    try {
      const detail = await fetchProjectKnowledgeDetail(session, spaceId, selected.id);
      setSelectedDetail(detail);
      setDraft({
        type: detail.type,
        title: detail.title,
        content: detail.content,
        sourceMeetingId: detail.sourceMeetingId ?? ""
      });
    } catch (cause) {
      setSelectedDetail(null);
      setDetailError(cause instanceof Error ? cause : new Error("지식 상세를 불러오지 못했습니다."));
    } finally {
      setDetailLoading(false);
    }
  }

  function openCreatePanel() {
    setCreateMode(true);
    setSelectedId(null);
    setSelectedDetail(null);
    setDetailError(null);
    setArchiveConfirmOpen(false);
    setMutationError("");
    setNotice("");
    setDraft({
      type: "manual",
      title: "",
      content: "",
      sourceMeetingId: ""
    });
  }

  async function handleCreateKnowledge() {
    if (!session || !spaceId || !canManageKnowledge || saving) {
      return;
    }
    if (!draft.title.trim() || !draft.content.trim()) {
      setMutationError("제목과 내용을 모두 입력해 주세요.");
      return;
    }
    setPendingAction("create");
    setMutationError("");
    setNotice("");
    try {
      const response = await createProjectKnowledge(session, spaceId, {
        type: draft.type,
        title: draft.title.trim(),
        content: draft.content.trim(),
        sourceMeetingId: draft.sourceMeetingId.trim() || null
      });
      await loadKnowledge();
      setSelectedId(response.id);
      setCreateMode(false);
      setNotice("지식을 등록했습니다.");
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "지식을 등록하지 못했습니다.");
    } finally {
      setPendingAction(null);
    }
  }

  async function handleUpdateKnowledge() {
    if (!session || !spaceId || !selectedDetail || !canManageKnowledge || saving) {
      return;
    }
    if (!draft.title.trim() || !draft.content.trim()) {
      setMutationError("제목과 내용을 모두 입력해 주세요.");
      return;
    }
    setPendingAction("update");
    setMutationError("");
    setNotice("");
    try {
      await updateProjectKnowledge(session, spaceId, selectedDetail.id, {
        title: draft.title.trim(),
        content: draft.content.trim()
      });
      await Promise.all([loadKnowledge(), fetchProjectKnowledgeDetail(session, spaceId, selectedDetail.id).then((detail) => {
        setSelectedDetail(detail);
        setDraft({
          type: detail.type,
          title: detail.title,
          content: detail.content,
          sourceMeetingId: detail.sourceMeetingId ?? ""
        });
      })]);
      setNotice("지식을 저장했습니다.");
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "지식을 저장하지 못했습니다.");
    } finally {
      setPendingAction(null);
    }
  }

  async function handleArchiveKnowledge() {
    if (!session || !spaceId || !selectedDetail || !canManageKnowledge || saving) {
      return;
    }
    setPendingAction("archive");
    setMutationError("");
    setNotice("");
    try {
      await deleteProjectKnowledge(session, spaceId, selectedDetail.id);
      setSelectedId(null);
      setSelectedDetail(null);
      setArchiveConfirmOpen(false);
      await loadKnowledge();
      setNotice("지식을 보관했습니다.");
    } catch (cause) {
      setMutationError(cause instanceof Error ? cause.message : "지식을 보관하지 못했습니다.");
    } finally {
      setPendingAction(null);
    }
  }

  const highlightIds = hovered
    ? new Set([hovered, ...(nodes.find((node) => node.id === hovered)?.connections ?? [])])
    : selected
    ? new Set([selected.id, ...selected.connections])
    : null;

  const toggleFolder = (id: string) =>
    setOpenFolders((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });

  const matchesSearch = (node: KNode) =>
    search === ""
    || [node.label, node.desc, node.type === "hub" ? "hub" : KNOWLEDGE_TYPE_LABELS[node.type]].join(" ").toLowerCase().includes(search.toLowerCase());

  const onSvgMouseDown = (e: React.MouseEvent) => {
    if ((e.target as SVGElement).tagName === "svg" || (e.target as SVGElement).tagName === "rect") {
      isPanning.current = true;
      lastMouse.current = { x: e.clientX, y: e.clientY };
    }
  };
  const onSvgMouseMove = (e: React.MouseEvent) => {
    if (!isPanning.current) return;
    const dx = e.clientX - lastMouse.current.x;
    const dy = e.clientY - lastMouse.current.y;
    lastMouse.current = { x: e.clientX, y: e.clientY };
    setTransform((current) => ({ ...current, x: current.x + dx, y: current.y + dy }));
  };
  const onSvgMouseUp = () => { isPanning.current = false; };
  const onWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const delta = e.deltaY > 0 ? 0.9 : 1.1;
    setTransform((current) => ({ ...current, scale: Math.min(3, Math.max(0.3, current.scale * delta)) }));
  };

  function formatUpdatedAt(value?: string) {
    if (!value) {
      return "";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
  }

  if (loading) {
    return <LoadingState label="Loading knowledge..." />;
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
        icon={<Library className="w-5 h-5" />}
        title="Knowledge unavailable"
      />
    );
  }

  if (error) {
    return <ErrorState desc={error.message} onRetry={() => { void loadKnowledge(); }} title="Couldn't load knowledge" />;
  }

  if (knowledgeItems.length === 0 && !createMode) {
    return (
      <EmptyState
        action={canManageKnowledge ? (
          <button
            className="bg-foreground text-background px-4 py-2 rounded-md text-sm font-medium hover:bg-foreground/90 transition-colors"
            onClick={openCreatePanel}
            type="button"
          >
            Add knowledge
          </button>
        ) : undefined}
        desc={canManageKnowledge
          ? "Official project knowledge will appear here after reports or manual knowledge are registered."
          : "Official project knowledge will appear here after reports or manual knowledge are registered. Your current role can view only."}
        icon={<Library className="w-5 h-5" />}
        title="No project knowledge yet"
      />
    );
  }

  return (
    <div className="flex h-full overflow-hidden">
      <div className="w-60 shrink-0 border-r border-border bg-card flex flex-col h-full">
        <div className="px-3 py-2.5 border-b border-border flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              type="text"
              placeholder="Search nodes..."
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              className="w-full pl-7 pr-2 py-1.5 rounded-md border border-border bg-background text-xs focus:outline-none focus:ring-1 focus:ring-primary/50"
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto custom-scrollbar py-1.5 px-1">
          {folders.map((folder) => {
            const isOpen = openFolders.has(folder.id);
            const visibleNodes = folder.nodeIds
              .map((id) => nodes.find((node) => node.id === id))
              .filter((node): node is KNode => Boolean(node))
              .filter(matchesSearch);
            if (search !== "" && visibleNodes.length === 0) return null;
            return (
              <div key={folder.id} className="mb-0.5">
                <div className="flex items-center gap-1 px-1 py-0.5 rounded-md transition-colors hover:bg-muted/30">
                  <button onClick={() => toggleFolder(folder.id)} className="flex items-center gap-1.5 flex-1 min-w-0 py-1">
                    <svg width="10" height="10" viewBox="0 0 10 10" className={`shrink-0 text-muted-foreground transition-transform ${isOpen ? "rotate-90" : ""}`}>
                      <path d="M3 2L7 5L3 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" fill="none"/>
                    </svg>
                    <FolderIcon open={isOpen} />
                    <span className="text-xs font-medium text-foreground truncate">{folder.label}</span>
                    <span className="text-[9px] text-muted-foreground ml-auto shrink-0 pr-1">{folder.nodeIds.length}</span>
                  </button>
                </div>

                {isOpen ? (
                  <div className="ml-3 border-l border-border pl-2 mt-0.5 space-y-0.5">
                    {visibleNodes.map((node) => (
                      <button
                        key={node.id}
                        onClick={() => setSelectedId(selected?.id === node.id ? null : node.id)}
                        onMouseEnter={() => setHovered(node.id)}
                        onMouseLeave={() => setHovered(null)}
                        className={`w-full flex items-center gap-2 px-2 py-1.5 rounded-md text-left cursor-pointer transition-colors hover:bg-muted/50 ${selected?.id === node.id ? "bg-muted" : ""}`}
                        type="button"
                      >
                        <FileIcon type={node.type} />
                        <span className={`text-xs truncate flex-1 ${selected?.id === node.id ? "font-semibold text-foreground" : "text-foreground/80"}`}>{node.label}</span>
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
        <div className="p-3 border-t border-border">
          <button
            className="w-full flex items-center justify-center gap-1.5 py-2 rounded-md bg-foreground text-background text-xs font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60"
            disabled={!canManageKnowledge}
            onClick={openCreatePanel}
            type="button"
          >
            <Plus className="w-3.5 h-3.5" /> Add Knowledge
          </button>
          {!canManageKnowledge ? <p className="mt-2 text-[11px] text-muted-foreground">OWNER 또는 ADMIN만 지식을 등록할 수 있습니다.</p> : null}
          {notice ? <p className="mt-2 text-[11px] text-emerald-700">{notice}</p> : null}
        </div>
      </div>

      <div className="flex-1 relative overflow-hidden bg-[#FAFAFA]" style={{ backgroundImage: "radial-gradient(circle, #E4E4E7 1px, transparent 1px)", backgroundSize: "24px 24px" }}>
        <div className="absolute top-3 left-3 z-10 flex items-center gap-1.5 bg-card border border-border rounded-lg px-2 py-1.5 shadow-sm">
          <button onClick={() => setTransform((current) => ({ ...current, scale: Math.min(3, current.scale * 1.2) }))} className="w-6 h-6 flex items-center justify-center rounded hover:bg-muted text-muted-foreground text-sm font-bold transition-colors">+</button>
          <span className="text-xs text-muted-foreground w-10 text-center font-mono">{Math.round(transform.scale * 100)}%</span>
          <button onClick={() => setTransform((current) => ({ ...current, scale: Math.max(0.3, current.scale * 0.8) }))} className="w-6 h-6 flex items-center justify-center rounded hover:bg-muted text-muted-foreground text-sm font-bold transition-colors">−</button>
          <div className="w-px h-4 bg-border mx-0.5"></div>
          <button onClick={() => setTransform({ x: 0, y: 0, scale: 1 })} className="text-[10px] text-muted-foreground hover:text-foreground px-1.5 py-0.5 rounded hover:bg-muted transition-colors">Reset</button>
        </div>

        <div className="absolute top-3 right-3 z-10 bg-card border border-border rounded-lg px-3 py-2 shadow-sm space-y-1.5">
          {(Object.entries(NODE_STYLE) as [KnowledgeNodeType, typeof NODE_STYLE[KnowledgeNodeType]][]).map(([type, style]) => (
            <div key={type} className="flex items-center gap-2">
              <span className={`w-2 h-2 rounded-full ${style.dot}`}></span>
              <span className="text-[10px] text-muted-foreground capitalize">{style.label}</span>
            </div>
          ))}
        </div>

        <svg
          ref={svgRef}
          className="w-full h-full cursor-grab active:cursor-grabbing select-none"
          onMouseDown={onSvgMouseDown}
          onMouseMove={onSvgMouseMove}
          onMouseUp={onSvgMouseUp}
          onMouseLeave={onSvgMouseUp}
          onWheel={onWheel}
        >
          <rect width="100%" height="100%" fill="transparent" />
          <g transform={`translate(${transform.x}, ${transform.y}) scale(${transform.scale})`}>
            {edges.map((edge) => {
              const fromNode = nodes.find((node) => node.id === edge.from);
              const toNode = nodes.find((node) => node.id === edge.to);
              if (!fromNode || !toNode) return null;
              const isHighlighted = highlightIds ? highlightIds.has(edge.from) && highlightIds.has(edge.to) : false;
              const dimmed = highlightIds && !isHighlighted;
              return (
                <line
                  key={`${edge.from}-${edge.to}`}
                  x1={fromNode.x}
                  y1={fromNode.y}
                  x2={toNode.x}
                  y2={toNode.y}
                  stroke={isHighlighted ? "#09090B" : "#D4D4D8"}
                  strokeWidth={isHighlighted ? 1.5 : 1}
                  strokeOpacity={dimmed ? 0.15 : 1}
                  strokeDasharray={isHighlighted ? undefined : "4 3"}
                  style={{ transition: "all 0.15s" }}
                />
              );
            })}

            {nodes.map((node) => {
              const style = NODE_STYLE[node.type];
              const isHub = node.type === "hub";
              const radius = isHub ? 22 : 14;
              const isSelected = selected?.id === node.id;
              const isHovered = hovered === node.id;
              const dimmed = highlightIds && !highlightIds.has(node.id);
              return (
                <g
                  key={node.id}
                  transform={`translate(${node.x}, ${node.y})`}
                  onClick={() => setSelectedId(selected?.id === node.id ? null : node.id)}
                  onMouseEnter={() => setHovered(node.id)}
                  onMouseLeave={() => setHovered(null)}
                  className="cursor-pointer"
                  style={{ opacity: dimmed ? 0.2 : 1, transition: "opacity 0.15s" }}
                >
                  {isSelected || isHovered ? <circle r={radius + 6} fill={style.fill} opacity={0.15} /> : null}
                  <circle
                    r={radius}
                    fill={style.fill}
                    stroke="white"
                    strokeWidth={isSelected ? 3 : 2}
                    style={{ transition: "r 0.15s" }}
                  />
                  {isHub ? <text textAnchor="middle" dominantBaseline="central" fill="white" fontSize={10} fontWeight="700">MM</text> : null}
                  <text
                    y={radius + 10}
                    textAnchor="middle"
                    fill="#09090B"
                    fontSize={10}
                    fontWeight={isSelected ? "700" : "500"}
                    style={{ pointerEvents: "none" }}
                  >
                    {node.label}
                  </text>
                </g>
              );
            })}
          </g>
        </svg>
      </div>

      {createMode ? (
        <div className="w-64 shrink-0 border-l border-border bg-card flex flex-col h-full">
          <div className="p-4 border-b border-border flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Plus className="w-3.5 h-3.5 text-muted-foreground" />
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">New knowledge</span>
            </div>
            <button
              onClick={() => {
                setCreateMode(false);
                setArchiveConfirmOpen(false);
                setMutationError("");
                setNotice("");
              }}
              className="w-6 h-6 flex items-center justify-center rounded hover:bg-muted text-muted-foreground transition-colors"
              type="button"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            <div>
              <label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground block mb-1.5">Type</label>
              <select
                className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50"
                disabled={!canManageKnowledge || saving}
                onChange={(event) => setDraft((current) => ({ ...current, type: event.target.value as ProjectKnowledgeType }))}
                value={draft.type}
              >
                {Object.entries(KNOWLEDGE_TYPE_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground block mb-1.5">Title</label>
              <input
                className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 disabled:opacity-60"
                disabled={!canManageKnowledge || saving}
                onChange={(event) => setDraft((current) => ({ ...current, title: event.target.value }))}
                value={draft.title}
              />
            </div>
            <div>
              <label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground block mb-1.5">Content</label>
              <textarea
                className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 resize-none disabled:opacity-60"
                disabled={!canManageKnowledge || saving}
                onChange={(event) => setDraft((current) => ({ ...current, content: event.target.value }))}
                rows={8}
                value={draft.content}
              />
            </div>
            <div>
              <label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground block mb-1.5">Source Meeting ID (optional)</label>
              <input
                className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 disabled:opacity-60"
                disabled={!canManageKnowledge || saving}
                onChange={(event) => setDraft((current) => ({ ...current, sourceMeetingId: event.target.value }))}
                value={draft.sourceMeetingId}
              />
            </div>
            {mutationError ? <p className="text-xs text-red-600">{mutationError}</p> : null}
            {!canManageKnowledge ? <p className="text-[11px] text-muted-foreground">Your current role can review knowledge only.</p> : null}
          </div>
          <div className="p-4 border-t border-border space-y-2">
            <button
              className="w-full py-2 rounded-md bg-foreground text-background text-xs font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60"
              disabled={!canManageKnowledge || saving}
              onClick={() => void handleCreateKnowledge()}
              type="button"
            >
              {isCreating ? "Creating..." : "Create Knowledge"}
            </button>
          </div>
        </div>
      ) : selected ? (
        <div className="w-64 shrink-0 border-l border-border bg-card flex flex-col h-full">
          <div className="p-4 border-b border-border flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className={`w-2.5 h-2.5 rounded-full ${NODE_STYLE[selected.type].dot}`}></span>
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground capitalize">
                {selected.type === "hub" ? "hub" : KNOWLEDGE_TYPE_LABELS[selected.type]}
              </span>
            </div>
            <button onClick={() => setSelectedId(null)} className="w-6 h-6 flex items-center justify-center rounded hover:bg-muted text-muted-foreground transition-colors" type="button">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            <div>
              <h3 className="font-semibold text-foreground text-sm leading-snug">{selected.label}</h3>
            </div>
            {selected.type === "hub" ? (
              <p className="text-xs text-muted-foreground leading-relaxed">
                Official project knowledge connected to this space. Only accessible knowledge is shown here.
              </p>
            ) : detailLoading ? (
              <p className="text-xs text-muted-foreground leading-relaxed">Loading detail...</p>
            ) : detailError ? (
              <div className="space-y-2">
                <p className="text-xs text-red-600 leading-relaxed">{detailError.message}</p>
                <button
                  className="text-xs font-semibold text-foreground underline underline-offset-2"
                  onClick={() => { void reloadSelectedDetail(); }}
                  type="button"
                >
                  Try loading again
                </button>
              </div>
            ) : (
              <p className="text-xs text-muted-foreground leading-relaxed">{selectedDetail?.content ?? selected.desc}</p>
            )}

            {selected.type !== "hub" ? (
              <div className="space-y-2">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className={`text-[10px] font-semibold px-2 py-0.5 rounded ${NODE_STYLE[selected.type].dot} text-white`}>
                    {KNOWLEDGE_TYPE_LABELS[selected.type]}
                  </span>
                  {selected.embeddingStatus ? <span className="text-[10px] text-muted-foreground">{KNOWLEDGE_STATUS_LABELS[selected.embeddingStatus]}</span> : null}
                </div>
                {selected.updatedAt ? <p className="text-[11px] text-muted-foreground">Updated {formatUpdatedAt(selected.updatedAt)}</p> : null}
                {selected.sourceMeetingId ? <p className="text-[11px] text-muted-foreground">Source meeting linked</p> : null}
              </div>
            ) : null}

            {selected.type !== "hub" && selectedDetail && !detailLoading ? (
              <div className="space-y-3">
                <div>
                  <label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground block mb-1.5">Title</label>
                  <input
                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 disabled:opacity-60"
                    disabled={!canManageKnowledge || saving}
                    onChange={(event) => setDraft((current) => ({ ...current, title: event.target.value }))}
                    value={draft.title}
                  />
                </div>
                <div>
                  <label className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground block mb-1.5">Content</label>
                  <textarea
                    className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 resize-none disabled:opacity-60"
                    disabled={!canManageKnowledge || saving}
                    onChange={(event) => setDraft((current) => ({ ...current, content: event.target.value }))}
                    rows={8}
                    value={draft.content}
                  />
                </div>
                {!canManageKnowledge ? <p className="text-[11px] text-muted-foreground">현재 계정은 지식을 조회만 할 수 있습니다.</p> : null}
                {mutationError ? <p className="text-xs text-red-600">{mutationError}</p> : null}
                {notice ? <p className="text-xs text-emerald-700">{notice}</p> : null}
              </div>
            ) : null}

            <div>
              <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground mb-2">Connected to</p>
              <div className="space-y-1">
                {selected.connections.map((connectionId) => {
                  const connectedNode = nodes.find((node) => node.id === connectionId);
                  if (!connectedNode) return null;
                  return (
                    <button
                      key={connectionId}
                      onClick={() => setSelectedId(connectedNode.id)}
                      className="w-full flex items-center gap-2 px-2 py-1.5 rounded-md hover:bg-muted transition-colors text-left"
                      type="button"
                    >
                      <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${NODE_STYLE[connectedNode.type].dot}`}></span>
                      <span className="text-xs text-foreground truncate">{connectedNode.label}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>
          <div className="p-4 border-t border-border">
            <div className="space-y-2">
              {selected.type !== "hub" ? (
                <>
                  <button
                    className="w-full py-2 rounded-md bg-foreground text-background text-xs font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60"
                    disabled={!canManageKnowledge || saving || !selectedDetail}
                    onClick={() => void handleUpdateKnowledge()}
                    type="button"
                  >
                    {isUpdating ? "Saving..." : "Save Changes"}
                  </button>
                  <button
                    className="w-full py-2 rounded-md border border-red-200 text-red-700 text-xs font-semibold hover:bg-red-50 transition-colors disabled:opacity-60"
                    disabled={!canManageKnowledge || saving || !selectedDetail}
                    onClick={() => setArchiveConfirmOpen(true)}
                    type="button"
                  >
                    {isArchiving ? "Archiving..." : "Archive"}
                  </button>
                </>
              ) : null}
              <button
                className="w-full py-2 rounded-md bg-foreground text-background text-xs font-semibold hover:bg-foreground/90 transition-colors disabled:bg-muted disabled:text-muted-foreground"
                disabled={!selected.sourceMeetingId}
                onClick={() => {
                  if (selected.sourceMeetingId) {
                    navigate(`/spaces/${spaceId}/meetings/${selected.sourceMeetingId}`);
                  }
                }}
                type="button"
              >
                Open Full Page
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {archiveConfirmOpen && selectedDetail ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm"
          onClick={(event) => {
            if (event.target === event.currentTarget && !saving) {
              setArchiveConfirmOpen(false);
            }
          }}
        >
          <div className="w-full max-w-md rounded-xl border border-border bg-card shadow-2xl">
            <div className="border-b border-border px-6 py-5">
              <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-red-600">Confirm archive</p>
              <h2 className="mt-1 text-lg font-semibold text-foreground">Archive this knowledge?</h2>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                Archived knowledge is removed from the active list and Project AI search candidates.
              </p>
            </div>
            <div className="px-6 py-4">
              <div className="rounded-lg border border-border bg-muted/30 px-4 py-3">
                <p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">Knowledge</p>
                <p className="mt-1 text-sm font-medium text-foreground">{selectedDetail.title}</p>
              </div>
              {mutationError ? <p className="mt-3 text-xs text-red-600">{mutationError}</p> : null}
            </div>
            <div className="flex items-center justify-end gap-2 border-t border-border px-6 py-4">
              <button
                className="rounded-md border border-border px-4 py-2 text-sm text-muted-foreground transition-colors hover:bg-muted"
                disabled={saving}
                onClick={() => setArchiveConfirmOpen(false)}
                type="button"
              >
                Cancel
              </button>
              <button
                className="rounded-md bg-red-600 px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-red-700 disabled:opacity-60"
                disabled={saving}
                onClick={() => void handleArchiveKnowledge()}
                type="button"
              >
                {isArchiving ? "Archiving..." : "Archive"}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};

// 8. Members & Roles
const ProjectMembers = () => {
  const { session } = useAuthState();
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
  const [confirmState, setConfirmState] = useState<null | {
    type: "role" | "remove" | "transfer";
    member: SpaceMembersResponse["members"][number];
    nextRole?: "ADMIN" | "MEMBER";
    previousOwnerRole?: "ADMIN" | "MEMBER";
  }>(null);

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

  const roleColor: Record<"OWNER" | "ADMIN" | "MEMBER", string> = {
    OWNER: "bg-foreground text-background",
    ADMIN: "bg-blue-50 text-blue-700 border border-blue-200",
    MEMBER: "bg-muted text-muted-foreground border border-border"
  };

  function memberIdentity(member: SpaceMembersResponse["members"][number]) {
    const name = member.displayName?.trim() || member.email || member.userId;
    const initials = name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase() ?? "").join("") || "MM";
    return { name, initials };
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
          <h1 className="text-xl font-bold">Members & Roles</h1>
          <p className="text-sm text-muted-foreground mt-0.5">{members.length} members in this space</p>
        </div>
      </div>

      {/* Invite */}
      <div className="bg-card border border-border rounded-lg p-5 space-y-3">
        <h3 className="font-semibold text-sm">Invite Member</h3>
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
            {invitePending ? "Sending..." : "Send Invite"}
          </button>
        </div>
        {!canManageMembers ? <p className="text-xs text-muted-foreground">Only project owner or admin can invite members.</p> : null}
        {inviteNotice ? <p className="text-xs text-emerald-700">{inviteNotice}</p> : null}
      </div>
      {actionError ? <p className="text-xs text-red-600" role="alert">{actionError}</p> : null}
      {loading ? <LoadingState label="Loading members..." /> : null}
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
          <span className="col-span-2 text-right">Status</span>
        </div>
        <div className="divide-y divide-border">
          {members.length > 0 ? members.map(m => {
            const { name, initials } = memberIdentity(m);
            return (
            <div key={m.id} className="px-5 py-3.5 grid grid-cols-12 gap-4 items-center hover:bg-muted/20 transition-colors group">
              <div className="col-span-5 flex items-center gap-3 min-w-0">
                <div className="w-8 h-8 rounded-full bg-muted flex items-center justify-center text-xs font-bold text-foreground shrink-0">{initials}</div>
                <div className="min-w-0">
                  <div className="text-sm font-medium text-foreground truncate">{name}</div>
                  <div className="text-xs text-muted-foreground truncate">{m.email}</div>
                </div>
              </div>
              <div className="col-span-3">
                {m.role === "OWNER" ? (
                  <span className={`text-xs font-semibold px-2 py-0.5 rounded ${roleColor[m.role]}`}>Owner</span>
                ) : (
                  <select
                    value={m.role}
                    disabled={!canManageMembers || actionPending}
                    onChange={e => setConfirmState({ type: "role", member: m, nextRole: e.target.value as "ADMIN" | "MEMBER" })}
                    className="text-xs border border-border rounded-md px-2 py-1 bg-background focus:outline-none focus:ring-1 focus:ring-primary/50"
                  >
                    <option value="ADMIN">Admin</option>
                    <option value="MEMBER">Member</option>
                  </select>
                )}
              </div>
              <div className="col-span-2 text-xs text-muted-foreground">{joinedLabel(m.joinedAt)}</div>
              <div className="col-span-2 flex items-center justify-end gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
                <span className="text-xs text-muted-foreground">Active</span>
                {canTransferOwner && m.role !== "OWNER" ? (
                  <button
                    className="opacity-0 group-hover:opacity-100 transition-opacity ml-1 text-xs text-primary hover:underline"
                    onClick={() => setConfirmState({ type: "transfer", member: m, previousOwnerRole: "ADMIN" })}
                    type="button"
                  >
                    Transfer
                  </button>
                ) : null}
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
  const navigate = useNavigate();
  const { spaceId = "" } = useParams();
  const { spaceDetail } = useOutletContext<ShellOutletContext>();
  const [search, setSearch] = useState("");
  const [terms, setTerms] = useState<DomainTerm[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [saving, setSaving] = useState(false);
  const [mutationError, setMutationError] = useState("");
  const [notice, setNotice] = useState("");
  const [newTerm, setNewTerm] = useState({ term: "", definition: "" });
  const [draftTerm, setDraftTerm] = useState({ term: "", definition: "" });

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
      const response = await fetchDomainTerms(session, spaceId, { status: "ACTIVE" });
      setTerms(response.terms);
      setSelectedId((current) => current && response.terms.some((term) => term.id === current) ? current : response.terms[0]?.id ?? null);
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

  useEffect(() => {
    if (!selected) {
      setDraftTerm({ term: "", definition: "" });
      return;
    }
    setDraftTerm({ term: selected.term, definition: selected.definition });
  }, [selected]);

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
              placeholder="Search terms..."
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
        </div>
        <div className="p-3 border-t border-border">
          <button
            onClick={() => setShowAdd(true)}
            disabled={!canManageTerms}
            className="w-full flex items-center justify-center gap-1.5 py-2 rounded-md bg-foreground text-background text-xs font-semibold hover:bg-foreground/90 transition-colors"
          >
            <Plus className="w-3.5 h-3.5" /> Add Term
          </button>
          {!canManageTerms ? <p className="mt-2 text-[11px] text-muted-foreground">OWNER 또는 ADMIN만 용어를 수정할 수 있습니다.</p> : null}
        </div>
      </div>

      {/* Detail / Add */}
      <div className="flex-1 overflow-y-auto">
        {showAdd ? (
          <div className="p-8 max-w-xl space-y-4">
            <h2 className="font-bold text-lg">Add New Term</h2>
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
        ) : selected ? (
          <div className="p-8 max-w-xl space-y-6">
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="flex items-center gap-2 mb-1">
                  <span className={`text-[10px] font-medium px-2 py-0.5 rounded border ${statusTone[selected.status]}`}>{selected.status}</span>
                </div>
                <h2 className="text-2xl font-bold text-foreground">{selected.term}</h2>
                <p className="text-sm text-muted-foreground mt-0.5">Updated {formatUpdatedAt(selected.updatedAt)}</p>
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
              <button
                className="px-4 py-2 rounded-md bg-foreground text-background text-sm font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60"
                disabled={!canManageTerms || saving}
                onClick={() => void handleUpdateTerm()}
                type="button"
              >
                {saving ? "Saving..." : "Save Changes"}
              </button>
              <button
                className="px-4 py-2 rounded-md border border-red-200 text-red-700 text-sm font-semibold hover:bg-red-50 transition-colors disabled:opacity-60"
                disabled={!canManageTerms || saving}
                onClick={() => void handleArchiveTerm()}
                type="button"
              >
                Archive
              </button>
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
  const navigate = useNavigate();
  const { spaceId = "" } = useParams();
  const today = new Date();
  const [currentMonth, setCurrentMonth] = useState({ year: today.getFullYear(), month: today.getMonth() + 1 });
  const [selectedDay, setSelectedDay] = useState<number | null>(today.getDate());
  const [events, setEvents] = useState<ProjectCalendarEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const monthStart = React.useMemo(
    () => new Date(currentMonth.year, currentMonth.month - 1, 1),
    [currentMonth.month, currentMonth.year]
  );
  const monthEnd = React.useMemo(
    () => new Date(currentMonth.year, currentMonth.month, 0),
    [currentMonth.month, currentMonth.year]
  );
  const monthKey = React.useMemo(
    () => `${currentMonth.year}-${String(currentMonth.month).padStart(2, "0")}`,
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
        from: `${monthKey}-01`,
        to: `${monthKey}-${String(monthEnd.getDate()).padStart(2, "0")}`,
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
  }, [monthEnd, monthKey, session, spaceId]);

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
    ENDED: "bg-zinc-500",
    CANCELED: "bg-rose-500"
  };
  const statusBadge: Record<ProjectCalendarEvent["status"], string> = {
    SCHEDULED: "bg-blue-50 text-blue-700 border-blue-200",
    IN_PROGRESS: "bg-emerald-50 text-emerald-700 border-emerald-200",
    ENDED: "bg-zinc-100 text-zinc-700 border-zinc-200",
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
              Today
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
                onClick={() => setSelectedDay(day)}
                className={`bg-background p-2 cursor-pointer hover:bg-muted/30 transition-colors min-h-[72px] ${isSelected ? "ring-1 ring-inset ring-primary" : ""}`}
              >
                <span className={`text-xs font-semibold inline-flex w-6 h-6 items-center justify-center rounded-full ${isToday ? "bg-foreground text-background" : "text-foreground"}`}>
                  {day}
                </span>
                <div className="mt-1 space-y-0.5">
                  {dayMeetings.map((m, i) => (
                    <div key={i} className="flex items-center gap-1">
                      <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${statusStyle[m.status]}`}></span>
                      <span className="text-[10px] text-foreground truncate">{m.title}</span>
                    </div>
                  ))}
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
              {dayMeetings.map((m) => (
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
  const [transcriptRows, setTranscriptRows] = useState<LiveTranscriptRow[]>([]);
  const [transcriptStatus, setTranscriptStatus] = useState<"PENDING" | "PROCESSING" | "COMPLETED" | "FAILED">("PENDING");
  const [transcriptError, setTranscriptError] = useState("");
  const [roomRetrySeed, setRoomRetrySeed] = useState(0);
  const [sttState, setSttState] = useState<"idle" | "starting" | "active" | "failed">("idle");
  const [ending, setEnding] = useState(false);
  const roomRef = useRef<Room | null>(null);
  const sttSessionIdRef = useRef<string | null>(null);
  const sttStartedRef = useRef(false);
  const meetingDetailRef = useRef<MeetingDetailResponse | null>(null);
  const liveStartedAtRef = useRef(Date.now());
  const initialDevicePreferencesRef = useRef({ micOn, camOn });
  const currentUserName = authSession.user.displayName?.trim() || "MeetingMind User";

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
      setRoomReady(false);
      setRoomError(null);
      setTranscriptError("");
      setTranscriptRows([]);
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
      if (sessionId) {
        void stopMeetingTranscription(authSession, meetingId, sessionId)
          .then(() => sessionStorage.removeItem(sttSessionStorageKey(meetingId)))
          .catch(() => {})
          .finally(disconnectRoom);
        return;
      }
      disconnectRoom();
    };
  }, [authSession, currentUserName, meetingError, meetingId, meetingLoading, roomRetrySeed]);

  useEffect(() => {
    if (!roomReady || !meetingDetail) {
      return;
    }

    let cancelled = false;
    const pollDialogue = async () => {
      try {
        const response = await fetchMeetingDialogue(authSession, meetingId);
        if (cancelled) {
          return;
        }
        setTranscriptStatus(response.status);
        if (response.status === "PROCESSING" || response.status === "COMPLETED") {
          setSttState("active");
        } else if (response.status === "FAILED") {
          setSttState("failed");
        }
        setTranscriptError((current) => {
          if (response.status === "FAILED" && current) {
            return current;
          }
          return "";
        });
        setTranscriptRows(
          [
            ...response.rows
              .slice()
              .sort((left, right) => left.startMs - right.startMs)
              .map((row) => {
                const speaker = row.speakerName || row.speakerLabel || "Unknown";
                return {
                  key: row.segmentId,
                  speaker,
                  initials: participantInitials(speaker),
                  time: formatTranscriptTime(row.startMs),
                  text: row.text
                };
              }),
            ...response.partials.map((partial, index) => {
              const speaker = partial.speakerName || partial.speakerLabel || "Unknown";
              return {
                key: `partial-${partial.speakerLabel}-${index}`,
                speaker,
                initials: participantInitials(speaker),
                time: "LIVE",
                text: partial.text
              };
            })
          ]
        );
      } catch (cause) {
        if (cancelled) {
          return;
        }
        setTranscriptError(cause instanceof Error ? cause.message : "실시간 자막을 불러오지 못했습니다.");
      }
    };

    void pollDialogue();
    const intervalId = window.setInterval(pollDialogue, 2500);
    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [authSession, meetingDetail, meetingId, roomReady]);

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

  async function handleLeave() {
    if (ending) {
      return;
    }
    setEnding(true);
    const sessionId = sttSessionIdRef.current ?? sessionStorage.getItem(sttSessionStorageKey(meetingId));
    if (sttState === "active" || sessionId) {
      try {
        const response = sessionId
          ? await stopMeetingTranscription(authSession, meetingId, sessionId)
          : await stopActiveMeetingTranscription(authSession, meetingId);
        sttSessionIdRef.current = null;
        sttStartedRef.current = false;
        sessionStorage.removeItem(sttSessionStorageKey(meetingId));
        setTranscriptStatus(response.transcriptStatus);
      } catch (cause) {
        setEnding(false);
        setRoomError(
          cause instanceof Error
            ? new Error(`전사를 안전하게 종료하지 못했습니다. ${cause.message}`)
            : new Error("전사를 안전하게 종료하지 못했습니다.")
        );
        return;
      }
    }
    const room = roomRef.current;
    if (room) {
      await room.disconnect();
      roomRef.current = null;
    }
    if (meetingDetail?.myRole === "HOST") {
      if (meetingDetail.status === "SCHEDULED") {
        await updateMeeting(authSession, meetingId, { status: "IN_PROGRESS" }).catch(() => {});
      }
      await updateMeeting(authSession, meetingId, { status: "ENDED" }).catch(() => {});
    }
    navigate(`/spaces/${spaceId}/meetings/${meetingId}`);
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
          <div className="flex-1 grid grid-cols-2 gap-2 p-3">
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
              className="px-5 h-11 rounded-full bg-red-500 hover:bg-red-600 disabled:opacity-60 text-white text-sm font-bold transition-colors ml-4"
            >
              {ending ? "Ending..." : "End"}
            </button>
          </div>
          {roomError ? <div className="px-4 py-2 text-xs text-red-300 bg-red-500/10 border-t border-red-500/20">{roomError.message}</div> : null}
        </div>

        {/* Right Panel — Live Transcript */}
        <div className="w-72 shrink-0 bg-zinc-900 border-l border-white/5 flex flex-col">
          <div className="border-b border-white/5 px-4 py-3 text-xs font-semibold text-white shrink-0">
            Live Transcript
          </div>

          <div className="flex-1 overflow-y-auto p-3 space-y-3 custom-scrollbar">
            {transcriptRows.map((row) => (
              <div key={row.key} className="flex gap-2.5">
                <div className="w-6 h-6 rounded-full bg-zinc-700 flex items-center justify-center text-[9px] font-bold text-white shrink-0 mt-0.5">{row.initials}</div>
                <div>
                  <div className="flex items-baseline gap-2 mb-0.5">
                    <span className="text-[11px] font-semibold text-zinc-300">{row.speaker}</span>
                    <span className="text-[10px] text-zinc-600 font-mono">{row.time}</span>
                  </div>
                  <p className="text-xs text-zinc-400 leading-relaxed">{row.text}</p>
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
        </div>
      </div>
      {participants
        .filter((participant) => participant.isConnected && !participant.isLocal)
        .map((participant) => (
          <AudioTrackSurface key={`audio-${participant.key}`} publication={participant.audioPublication} />
        ))}
    </div>
  );
};

// 13. Project Settings
const ProjectSettings = () => {
  const { session } = useAuthState();
  const authSession = session as AuthSession;
  const navigate = useNavigate();
  const { spaceId = "" } = useParams<{ spaceId: string }>();
  const { spaceDetail, spaceLoading, spaceError, reloadSpace } = useOutletContext<ShellOutletContext>();
  const [projectName, setProjectName] = useState("");
  const [description, setDescription] = useState("");
  const [savePending, setSavePending] = useState(false);
  const [saveMessage, setSaveMessage] = useState("");
  const [mutationError, setMutationError] = useState("");
  const [deletePending, setDeletePending] = useState(false);
  const [showDanger, setShowDanger] = useState(false);

  useEffect(() => {
    setProjectName(spaceDetail?.name ?? "");
    setDescription(spaceDetail?.description ?? "");
  }, [spaceDetail?.description, spaceDetail?.name]);

  const Toggle = ({ value }: { value: boolean }) => (
    <button
      disabled
      className={`relative w-9 h-5 rounded-full transition-colors shrink-0 ${value ? "bg-foreground" : "bg-muted-foreground/30"} opacity-60 cursor-not-allowed`}
      type="button"
    >
      <span className={`absolute top-0.5 left-0.5 w-4 h-4 bg-white rounded-full shadow transition-transform ${value ? "translate-x-4" : ""}`} />
    </button>
  );

  const canManage = spaceDetail?.role === "OWNER" || spaceDetail?.role === "ADMIN";
  const canDelete = spaceDetail?.role === "OWNER";
  const projectAiEnabled = spaceDetail?.aiEntrypoints.includes("project-ai") ?? false;
  const settingsRows = [
    {
      label: "Project AI",
      desc: "현재 프로젝트에서 Project AI 진입 가능 여부를 표시합니다.",
      value: projectAiEnabled
    },
    {
      label: "Auto-confirm Reports",
      desc: "자동 확정 설정 API가 아직 없습니다. 서버 계약이 준비되면 연결합니다.",
      value: false
    },
    {
      label: "Live STT in Meetings",
      desc: "Live STT 설정 저장 API가 아직 없습니다. 회의 단위 제어만 지원합니다.",
      value: false
    }
  ];

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
        description: description.trim() || null
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
    <div className="p-8 max-w-2xl mx-auto space-y-8">
      <h1 className="text-xl font-bold">Project Settings</h1>

      {/* General */}
      <section className="bg-card border border-border rounded-lg overflow-hidden">
        <div className="px-5 py-4 border-b border-border">
          <h2 className="font-semibold text-sm">General</h2>
        </div>
        <div className="p-5 space-y-4">
          <div>
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Project Name</label>
            <input
              className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 disabled:opacity-60"
              disabled={!canManage || savePending || deletePending}
              value={projectName}
              onChange={(e) => setProjectName(e.target.value)}
            />
          </div>
          <div>
            <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block mb-1.5">Description</label>
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
            {savePending ? "Saving..." : "Save Changes"}
          </button>
        </div>
      </section>

      {/* AI & Automation */}
      <section className="bg-card border border-border rounded-lg overflow-hidden">
        <div className="px-5 py-4 border-b border-border">
          <h2 className="font-semibold text-sm">AI & Automation</h2>
        </div>
        <div className="divide-y divide-border">
          {settingsRows.map((item) => (
            <div key={item.label} className="px-5 py-4 flex items-center justify-between gap-4">
              <div>
                <p className="text-sm font-medium text-foreground">{item.label}</p>
                <p className="text-xs text-muted-foreground mt-0.5">{item.desc}</p>
              </div>
              <Toggle value={item.value} />
            </div>
          ))}
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
            {!canDelete ? (
              <p className="text-xs text-red-700">프로젝트 삭제는 OWNER만 할 수 있습니다.</p>
            ) : null}
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

  return (
    <div className="min-h-screen bg-background">
      <div className="max-w-3xl mx-auto px-8 py-10">
        {/* Back */}
        <button onClick={() => navigate(-1)} className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground mb-6 transition-colors">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M9 2L4 7L9 12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>
          Back
        </button>

        <div className="flex items-center gap-4 mb-8">
          <div className="w-14 h-14 rounded-full bg-foreground text-background flex items-center justify-center text-xl font-bold">{sessionInitials(session)}</div>
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
                <input
                  className="w-full px-3 py-2 rounded-md border border-border bg-muted text-sm text-muted-foreground cursor-not-allowed"
                  readOnly
                  value={authSession.user.displayName}
                />
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
              <p className="text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-md px-3 py-2">
                프로필 수정과 이미지 저장 API는 아직 연결되지 않았습니다.
              </p>
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
          <Link to="/spaces" className="block w-full py-2.5 rounded-lg bg-foreground text-background text-center text-sm font-semibold hover:bg-foreground/90 transition-colors">
            Go to Workspaces
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
  const [confirmed, setConfirmed] = useState(false);

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
  const location = useLocation();
  const navigate = useNavigate();
  const [mode, setMode] = useState<"login" | "signup">("login");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();

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
    <div className="min-h-screen bg-background flex">
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
            "Every decision we make in meetings deserves to be remembered, acted on, and built upon."
          </blockquote>
          <div className="flex flex-col gap-4">
            {[
              { icon: <Mic className="w-4 h-4" />, text: "Real-time transcription with speaker attribution" },
              { icon: <Sparkles className="w-4 h-4" />, text: "AI-generated reports, decisions, and task extraction" },
              { icon: <Library className="w-4 h-4" />, text: "Project knowledge base built automatically from meetings" },
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
            <h1 className="text-2xl font-bold text-foreground">Welcome back</h1>
            <p className="text-sm text-muted-foreground mt-1">{mode === "signup" ? "Create your workspace account" : "Sign in to your workspace"}</p>
          </div>

          <div className="grid grid-cols-2 rounded-lg border border-border p-1 bg-muted/40">
            <button
              type="button"
              onClick={() => setMode("login")}
              className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${mode === "login" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground"}`}
            >
              Sign in
            </button>
            <button
              type="button"
              onClick={() => setMode("signup")}
              className={`rounded-md px-3 py-2 text-sm font-medium transition-colors ${mode === "signup" ? "bg-card text-foreground shadow-sm" : "text-muted-foreground"}`}
            >
              Sign up
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            {mode === "signup" ? (
              <div>
                <div id="signup-display-name-label" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">Display Name</div>
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
              <div id="auth-email-label" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">Work Email</div>
              <input
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
                <div id="auth-password-label" className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Password</div>
                <button type="button" className="text-xs text-primary hover:underline">Forgot password?</button>
              </div>
              <input
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
            {error && <p className="text-xs text-red-500 bg-red-50 border border-red-200 rounded-lg px-3 py-2">{error}</p>}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 rounded-lg bg-foreground text-background text-sm font-semibold hover:bg-foreground/90 transition-colors disabled:opacity-60 flex items-center justify-center gap-2"
            >
              {loading ? <><div className="w-4 h-4 border-2 border-background/30 border-t-background rounded-full animate-spin" /> Processing...</> : mode === "signup" ? "Create account" : "Sign in"}
            </button>
          </form>

          <div className="relative">
            <div className="absolute inset-0 flex items-center"><div className="w-full border-t border-border" /></div>
            <div className="relative flex justify-center"><span className="bg-background px-3 text-xs text-muted-foreground">or continue with</span></div>
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
              ? "Already have an account? "
              : "Need an account? "}
            <button
              onClick={() => setMode(mode === "signup" ? "login" : "signup")}
              className="text-foreground font-semibold hover:underline"
              type="button"
            >
              {mode === "signup" ? "Go to sign in" : "Create one"}
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
  const { spaceId = "" } = useParams();
  const { spaceDetail, spaceLoading, spaceError } = useOutletContext<ShellOutletContext>();
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
    doc: "bg-violet-50 text-violet-700 border-violet-200",
    term: "bg-amber-50 text-amber-700 border-amber-200",
  };

  if (spaceLoading) {
    return <LoadingState label="Loading Project AI..." />;
  }

  if (spaceError) {
    return <ErrorState title="Couldn't load Project AI" desc={spaceError.message} />;
  }

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="px-6 py-4 border-b border-border bg-card shrink-0 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center">
            <Sparkles className="w-4 h-4 text-primary" />
          </div>
          <div>
            <h2 className="font-semibold text-sm">Project AI</h2>
            <p className="text-[10px] text-muted-foreground">Searches confirmed reports & official knowledge only</p>
          </div>
        </div>
        <div className="flex items-center gap-1.5 text-[10px] text-muted-foreground border border-border rounded-full px-2.5 py-1">
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
          {spaceDetail?.name ? `${spaceDetail.name} only` : "Authorized sources only"}
        </div>
      </div>

      {/* Messages */}
      <div className="flex-1 overflow-y-auto custom-scrollbar px-6 py-6 space-y-6">
        {messages.map((m, i) => (
          <div key={i} className={`flex gap-3 ${m.role === "user" ? "flex-row-reverse" : ""}`}>
            {m.role === "assistant" && (
              <div className="w-7 h-7 rounded-full bg-primary/10 flex items-center justify-center shrink-0 mt-0.5">
                <Sparkles className="w-3.5 h-3.5 text-primary" />
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
    </div>
  );
};

// ─────────────────────────────────────────────
// 19. Meeting Report — enhanced with workflow states
// ─────────────────────────────────────────────
type ReportStatus = "draft" | "editing" | "confirmed";

const MeetingReport = () => {
  const [status, setStatus] = useState<ReportStatus>("draft");
  const [editMode, setEditMode] = useState(false);
  const [summaryText, setSummaryText] = useState(
    "The team aligned on the JWT auth flow for the Q3 launch. Key decisions included handling silent re-authentication for expired refresh tokens and defining a 24-hour idle timeout. Sarah Jenkins will update the design system with a timeout screen and a new outlined button variant, both treated as P1 priority."
  );

  const statusConfig: Record<ReportStatus, { label: string; color: string; bg: string; border: string; desc: string }> = {
    draft:     { label: "Draft",      color: "text-amber-700",   bg: "bg-amber-50",   border: "border-amber-200", desc: "AI-generated — not yet reviewed" },
    editing:   { label: "Editing",    color: "text-blue-700",    bg: "bg-blue-50",    border: "border-blue-200",  desc: "Under review and editing" },
    confirmed: { label: "Confirmed",  color: "text-emerald-700", bg: "bg-emerald-50", border: "border-emerald-200", desc: "Added to project knowledge base" },
  };

  const sc = statusConfig[status];

  const decisions = [
    { id: 1, text: "Silent re-authentication flow when refresh token expires; user sees interruption only after 24h idle.", accepted: true },
    { id: 2, text: "Outlined button variant to be added to design system as P1 before auth screen implementation.", accepted: true },
    { id: 3, text: "Timeout/error screen design to follow existing error state patterns.", accepted: false },
  ];

  const [extractedTasks, setExtractedTasks] = useState([
    { id: 1, title: "Design timeout screen matching existing error state patterns", assignee: "Sarah Jenkins", added: false },
    { id: 2, title: "Add outlined button variant to design system (P1)", assignee: "Sarah Jenkins", added: false },
    { id: 3, title: "Review final design spec for backend JWT implementation", assignee: "David Chen", added: false },
  ]);

  const addedCount = extractedTasks.filter(t => t.added).length;

  return (
    <div className="space-y-5">
      {/* Status bar */}
      <div className={`flex items-center justify-between rounded-lg px-5 py-3 border ${sc.bg} ${sc.border}`}>
        <div className="flex items-center gap-3">
          <span className={`text-xs font-bold uppercase tracking-wider px-2.5 py-1 rounded-full border ${sc.bg} ${sc.border} ${sc.color}`}>{sc.label}</span>
          <span className={`text-sm ${sc.color}`}>{sc.desc}</span>
        </div>
        <div className="flex items-center gap-2">
          {status === "draft" && (
            <><button onClick={() => { setStatus("editing"); setEditMode(true); }} className="px-3 py-1.5 rounded-md border border-blue-300 text-blue-700 bg-white text-xs font-semibold hover:bg-blue-50 transition-colors">Start Review</button>
            <button onClick={() => setStatus("confirmed")} className="px-3 py-1.5 rounded-md bg-foreground text-background text-xs font-semibold hover:bg-foreground/90 transition-colors">Quick Confirm</button></>
          )}
          {status === "editing" && (
            <><button onClick={() => setEditMode(p => !p)} className="px-3 py-1.5 rounded-md border border-border text-xs font-semibold hover:bg-muted transition-colors">{editMode ? "Preview" : "Edit"}</button>
            <button onClick={() => { setStatus("confirmed"); setEditMode(false); }} className="px-3 py-1.5 rounded-md bg-foreground text-background text-xs font-semibold hover:bg-foreground/90 transition-colors">Confirm Report</button></>
          )}
          {status === "confirmed" && (
            <button onClick={() => setStatus("editing")} className="px-3 py-1.5 rounded-md border border-border text-xs text-muted-foreground hover:bg-muted transition-colors">Reopen for editing</button>
          )}
        </div>
      </div>

      {/* Status stepper */}
      <div className="flex items-center gap-2">
        {(["draft","editing","confirmed"] as ReportStatus[]).map((s, i) => {
          const past = ["draft","editing","confirmed"].indexOf(status) >= i;
          return (
            <React.Fragment key={s}>
              <div className={`flex items-center gap-1.5 ${past ? "" : "opacity-40"}`}>
                <div className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-bold ${past ? "bg-foreground text-background" : "bg-muted text-muted-foreground"}`}>{i+1}</div>
                <span className="text-xs font-medium capitalize text-foreground">{s}</span>
              </div>
              {i < 2 && <div className={`flex-1 h-px ${past && status !== (["draft","editing","confirmed"][i] as ReportStatus) ? "bg-foreground" : "bg-border"}`} />}
            </React.Fragment>
          );
        })}
      </div>

      {/* Summary */}
      <div className="bg-card border border-border rounded-lg p-6 space-y-3">
        <div className="flex items-center justify-between">
          <h3 className="font-semibold text-foreground">Meeting Summary</h3>
          {status === "editing" && (
            <button onClick={() => setEditMode(p => !p)} className="text-xs text-primary hover:underline">{editMode ? "Preview" : "Edit"}</button>
          )}
        </div>
        {editMode && status === "editing" ? (
          <textarea value={summaryText} onChange={e => setSummaryText(e.target.value)} rows={6} className="w-full px-3 py-2 rounded-md border border-border bg-background text-sm focus:outline-none focus:ring-1 focus:ring-primary/50 resize-none leading-relaxed" />
        ) : (
          <p className="text-sm text-foreground/80 leading-relaxed">{summaryText}</p>
        )}
      </div>

      {/* Key Decisions */}
      <div className="bg-card border border-border rounded-lg p-6">
        <h3 className="font-semibold text-foreground mb-4">Key Decisions</h3>
        <div className="space-y-3">
          {decisions.map((d, i) => (
            <div key={d.id} className={`flex gap-3 p-3 rounded-lg border ${d.accepted ? "border-emerald-200 bg-emerald-50" : "border-border bg-muted/30"}`}>
              <div className={`w-5 h-5 rounded-full flex items-center justify-center text-xs font-bold shrink-0 mt-0.5 ${d.accepted ? "bg-emerald-500 text-white" : "bg-muted-foreground/20 text-muted-foreground"}`}>{i + 1}</div>
              <p className="text-sm text-foreground/80 flex-1 leading-relaxed">{d.text}</p>
              {!d.accepted && status !== "confirmed" && (
                <span className="text-[10px] font-medium text-muted-foreground border border-border bg-background rounded px-1.5 py-0.5 shrink-0 self-start">Pending</span>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Task Extraction */}
      <div className="bg-card border border-border rounded-lg overflow-hidden">
        <div className="px-5 py-4 border-b border-border flex items-center justify-between">
          <h3 className="font-semibold">Extracted Tasks</h3>
          <button
            disabled={addedCount === 0}
            className={`text-xs font-semibold px-3 py-1.5 rounded-md transition-colors ${addedCount > 0 ? "bg-foreground text-background hover:bg-foreground/90" : "bg-muted text-muted-foreground cursor-not-allowed"}`}
          >
            Add {addedCount > 0 ? addedCount : ""} to Board
          </button>
        </div>
        <div className="divide-y divide-border">
          {extractedTasks.map(task => (
            <div
              key={task.id}
              onClick={() => setExtractedTasks(prev => prev.map(t => t.id === task.id ? { ...t, added: !t.added } : t))}
              className={`flex items-center gap-3 px-5 py-3.5 cursor-pointer transition-colors ${task.added ? "bg-primary/5" : "hover:bg-muted/30"}`}
            >
              <div className={`w-4 h-4 rounded border-2 flex items-center justify-center shrink-0 transition-colors ${task.added ? "bg-primary border-primary" : "border-muted-foreground/40"}`}>
                {task.added && <span className="text-white text-[9px] font-bold">✓</span>}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm text-foreground truncate">{task.title}</p>
                <p className="text-xs text-muted-foreground">→ {task.assignee}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
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

const PlaceholderPage = ({ title }: { title: string }) => (
  <div className="p-8 flex flex-col items-center justify-center h-full text-center">
    <div className="w-12 h-12 bg-muted rounded flex items-center justify-center mb-4">
      <Activity className="w-6 h-6 text-muted-foreground" />
    </div>
    <h2 className="text-lg font-semibold mb-2">{title}</h2>
    <p className="text-sm text-muted-foreground max-w-sm">This screen is part of the 21-page architecture but is currently a placeholder awaiting detailed implementation.</p>
  </div>
);

// --- Router Setup ---

const router = createBrowserRouter([
  { path: "/", Component: LandingPage },
  { path: "/login", Component: LoginPage },
  { path: "/meeting-access", Component: MeetingAccess },
  { path: "/meetings/:meetingId", Component: MeetingAccessCompatRoute },
  { path: "/spaces", element: <RequireAuth><WorkspaceHome /></RequireAuth> },
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
          { path: "report", Component: MeetingReport },
          { path: "tasks", Component: MeetingTaskCandidates },
          { path: "ai", Component: MeetingAIChat },
        ]
      },
      { path: "tasks", Component: ProjectTasks },
      { path: "knowledge", Component: ProjectKnowledge },
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
  { path: "/space-invitations/:spaceId/:invitationId", element: <RequireAuth><InvitationResponse /></RequireAuth> },
  { path: "/settings", element: <RequireAuth><AccountSettings /></RequireAuth> },
  { path: "/denied", Component: () => <PermissionDenied type="project" /> },
]);

export function App() {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [loading, setLoading] = useState(true);

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
    <AuthContext.Provider value={{ session, loading, setSession }}>
      <RouterProvider router={router} />
    </AuthContext.Provider>
  );
}

export default App;
