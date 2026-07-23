import { useEffect, useMemo, useState, type FormEvent } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { chatMeetingAi } from "../api/ai";
import type { AuthSession } from "../auth/session";
import { AppShell } from "../components/layout/AppShell";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { MeetingDetailResponse, UnsupportedReason, WorkspaceData } from "../types";

type ChatMessage = {
  role: "user" | "ai";
  text: string;
  sources?: string[];
  unsupported?: boolean;
  unsupportedReason?: UnsupportedReason | null;
};

function unsupportedMessage(reason: UnsupportedReason | null): string {
  switch (reason) {
    case "LOW_RELEVANCE":
      return "검색된 기록은 있지만 질문에 답할 만큼 관련성이 높지 않습니다.";
    case "MODEL_UNSUPPORTED":
      return "제공된 근거만으로는 답변을 확정할 수 없습니다.";
    case "UNVERIFIED_OUTPUT":
      return "응답의 근거를 확인하지 못해 답변을 제공할 수 없습니다.";
    case "NO_EVIDENCE":
    default:
      return "현재 회의에서 확인 가능한 근거가 없어 답변할 수 없습니다.";
  }
}

function meetingStatusLabel(status: MeetingDetailResponse["status"]) {
  switch (status) {
    case "IN_PROGRESS":
      return "진행 중";
    case "ENDED":
      return "종료됨";
    case "CANCELED":
      return "취소됨";
    case "SCHEDULED":
    default:
      return "예정됨";
  }
}

export function MeetingAiPage({
  data,
  session,
  onCreateProject,
  embedded = false,
  meeting
}: {
  data: WorkspaceData["meetingAi"];
  session: AuthSession | null;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
  embedded?: boolean;
  meeting?: MeetingDetailResponse;
}) {
  const [searchParams] = useSearchParams();
  const routeParams = useParams<{ spaceId: string; meetingId: string }>();
  const meetingId = routeParams.meetingId ?? searchParams.get("meetingId");
  const projectName = searchParams.get("project");
  const spaceId = routeParams.spaceId ?? searchParams.get("spaceId");
  const targetMeeting = embedded ? meeting : undefined;
  const initialMessages = useMemo<ChatMessage[]>(
    () => embedded && meeting
      ? [{ role: "ai", text: "현재 회의 범위에서 질문할 수 있습니다. 근거가 없는 내용은 답하지 않습니다." }]
      : data.chat,
    [data.chat, embedded, meeting]
  );
  const transcriptRows = targetMeeting ? [] : data.transcript;
  const decisions = targetMeeting ? [] : data.decisions;
  const actions = targetMeeting ? [] : data.actions;
  const suggestions = targetMeeting
    ? [{ label: "현재 회의의 핵심 내용을 정리해줘" }, { label: "근거가 있는 결정사항을 알려줘" }]
    : data.suggestions;
  const [messages, setMessages] = useState<ChatMessage[]>(initialMessages);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [modelLabel, setModelLabel] = useState("");

  const canSubmit = input.trim().length > 0 && !loading && Boolean(meetingId);

  useEffect(() => {
    document.body.className = "app-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

  useEffect(() => {
    setMessages(initialMessages);
    setInput("");
    setError("");
    setModelLabel("");
  }, [embedded, initialMessages, meeting?.id]);

  async function askMeetingAi(question: string) {
    const trimmed = question.trim();
    if (!trimmed || loading) {
      return;
    }

    const nextUserMessage: ChatMessage = { role: "user", text: trimmed };
    setMessages((previous) => [...previous, nextUserMessage]);
    setInput("");
    setError("");
    setLoading(true);

    try {
      if (!meetingId) {
        throw new Error("회의 식별자가 필요합니다.");
      }
      if (!session) {
        throw new Error("로그인이 필요합니다.");
      }
      const result = await chatMeetingAi(session, meetingId, {
        question: trimmed
      });
      setModelLabel(result.model);
      setMessages((previous) => [
        ...previous,
        {
          role: "ai",
          text: result.unsupported ? unsupportedMessage(result.unsupportedReason) : result.answer,
          sources: result.sources.map((source) => `${source.title} · ${source.type}`),
          unsupported: result.unsupported,
          unsupportedReason: result.unsupportedReason
        }
      ]);
    } catch (fetchError) {
      const message =
        fetchError instanceof Error
          ? fetchError.message
          : "Meeting AI 서비스에 연결하지 못했습니다.";
      setError(message);
      setMessages((previous) => [
        ...previous,
        {
          role: "ai",
          text: "Meeting AI 서비스에 연결하지 못했습니다. Backend와 AI 서버 연결 상태를 확인해주세요."
        }
      ]);
    } finally {
      setLoading(false);
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void askMeetingAi(input);
  }

  const aiSurface = (
    <>
      {!embedded ? (
        <header className="topbar meeting-ai-page-header">
          <div className="topbar-left">
            <div className="meeting-meta">
              <div className="meeting-title-row">
                <h1>{targetMeeting?.title ?? data.overview.title}</h1>
                {(targetMeeting ? [meetingStatusLabel(targetMeeting.status)] : data.overview.status).map((status) => (
                  <span key={status} className="status-pill live">
                    {status}
                  </span>
                ))}
              </div>
              <p>{targetMeeting ? "현재 회의 기록만 검색하는 Meeting AI" : data.overview.subtitle}</p>
            </div>
          </div>
        </header>
      ) : null}

      <main className="product-flow meeting-ai-page-flow">
        <article aria-label="회의 전사와 Meeting AI" className="flow-canvas split-ui meeting-ai-page-canvas">
          <section aria-labelledby="meeting-transcript-title" className="split-left meeting-ai-page-transcript">
            <div className="title-block meeting-ai-page-title">
              <span className="meeting-ai-page-kicker">Transcript</span>
              <h2 id="meeting-transcript-title">회의 전사</h2>
              <p>현재 회의에서 확인할 수 있는 발화 기록과 결정사항입니다.</p>
            </div>
            <section aria-label="전사 검색 범위" className="range-box meeting-ai-page-scope">
              <strong>검색 범위 (현재 회의 한정)</strong>
              <div className="range-chips">
                <span>현재 회의 STT 원문</span>
                <span>현재 회의 AI 보고서</span>
                <span>현재 회의 결정사항</span>
                <span>현재 회의 Action Item</span>
              </div>
            </section>
            <div aria-label="회의 발화 기록" className="transcript-box meeting-ai-page-transcript-list" role="list">
              {transcriptRows.map((row) => (
                <div key={`${row.time}-${row.speaker}`} className="transcript-row" role="listitem">
                  <span>{row.time}</span>
                  <strong>{row.speaker}</strong>
                  <p>{row.text}</p>
                </div>
              ))}
              <p className="meeting-ai-page-transcript-note">
                {transcriptRows.length ? "현재 회의에서 확인 가능한 전사 기록입니다." : "표시할 실제 전사 기록이 아직 없습니다."}
              </p>
            </div>
            <div className="source-grid meeting-ai-page-source-grid">
              <section aria-label="핵심 결정사항" className="source-card">
                <div className="table-head">
                  <strong>핵심 결정사항</strong>
                  <span>{decisions.length}건</span>
                </div>
                <ul className="doc-list">
                  {decisions.map((item) => (
                    <li key={item.title}>
                      <strong>{item.title}</strong>
                      <span>{item.meta}</span>
                    </li>
                  ))}
                </ul>
              </section>
              <section aria-label="추출된 Action Item" className="source-card">
                <div className="table-head">
                  <strong>추출된 Action Item</strong>
                  <span>{actions.length}건</span>
                </div>
                <ul className="doc-list">
                  {actions.map((item) => (
                    <li key={item.title}>
                      <strong>{item.title}</strong>
                      <span>{item.meta}</span>
                    </li>
                  ))}
                </ul>
              </section>
            </div>
          </section>

          <aside aria-label="Meeting AI 보조 패널" className="split-right meeting-ai-page-assistant">
            <div className="chat-head">
              <span className="meeting-ai-page-ai-kicker">Meeting AI / Current meeting</span>
              <strong>Meeting AI</strong>
              <span>검색 범위: 현재 회의 전용 · Project 전체 미포함</span>
            </div>

            <div className="assistant-meta">
              <div className="assistant-chip">출처 표시</div>
              <div className="assistant-chip">현재 회의만 검색</div>
              <div className="assistant-chip">결정사항 우선</div>
              {modelLabel ? <div className="assistant-chip">모델: {modelLabel}</div> : null}
            </div>

            <div aria-label="Meeting AI 대화" className="chat-thread" role="log">
              {messages.map((message, index) => (
                <div key={`${message.role}-${index}`} className={`bubble ${message.role} ${message.unsupported ? "is-unsupported" : ""}`}>
                  {message.text}
                  {message.sources?.length ? (
                    <div aria-label="답변 근거" className="meeting-ai-source-list" role="list">
                      {message.sources.map((source) => (
                        <span key={`${message.role}-${index}-${source}`} role="listitem">{source}</span>
                      ))}
                    </div>
                  ) : null}
                  {message.unsupported ? (
                    <div aria-label="근거 부족 상태" className="meeting-ai-unsupported">
                      {message.unsupportedReason === "LOW_RELEVANCE" ? "관련도 부족" : "근거 없음"}
                    </div>
                  ) : null}
                </div>
              ))}
              {loading ? <div aria-live="polite" className="bubble ai">답변 생성 중입니다...</div> : null}
            </div>

            <div className="answer-foot">
              <strong>추천 질문</strong>
              <div aria-label="추천 질문 목록" className="quick-list">
                {suggestions.map((item) => (
                  <button disabled={!meetingId || loading} key={item.label} onClick={() => void askMeetingAi(item.label)} type="button">
                    {item.label}
                  </button>
                ))}
              </div>
            </div>

            {!meetingId ? <div aria-live="polite" className="meeting-ai-error">회의 목록에서 Meeting AI로 진입하면 현재 회의 범위로 질문할 수 있습니다.</div> : null}
            {error ? <div aria-live="assertive" className="meeting-ai-error">{error}</div> : null}

            <form className="chat-input-row" onSubmit={handleSubmit}>
              <input
                aria-label="Meeting AI 입력"
                onChange={(event) => setInput(event.target.value)}
                placeholder="이번 회의에 대해 질문해보세요..."
                type="text"
                value={input}
              />
              <button disabled={!canSubmit} type="submit">
                {loading ? "생성 중" : "전송"}
              </button>
            </form>
          </aside>
        </article>
      </main>
    </>
  );

  if (embedded) {
    return <div className="meeting-ai-page meeting-ai-page--embedded">{aiSurface}</div>;
  }

  return (
    <AppShell
      contentClassName="meeting-ai-page"
      sidebar={(
        <WorkspaceSidebar
          activeItem="none"
          contextOverride={`${projectName ? `${projectName} · ` : ""}${targetMeeting?.title ?? data.overview.title}`}
          mode={projectName || spaceId ? "project" : "catalog"}
          onCreateProject={onCreateProject}
          projectName={projectName ?? undefined}
          spaceId={spaceId ?? undefined}
        />
      )}
    >
      {aiSurface}
    </AppShell>
  );
}
