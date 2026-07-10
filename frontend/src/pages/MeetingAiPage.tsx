import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { chatMeetingAi } from "../api/workspace";
import type { WorkspaceData } from "../types";

type ChatMessage = { role: "user" | "ai"; text: string; sources?: string[]; unsupported?: boolean };

export function MeetingAiPage({ data }: { data: WorkspaceData["meetingAi"] }) {
  const [searchParams] = useSearchParams();
  const projectId = searchParams.get("projectId") ?? "project-local";
  const meetingId = searchParams.get("meetingId") ?? "meeting-local-current";
  const meetingTitle = searchParams.get("meeting") ?? data.overview.title;
  const [messages, setMessages] = useState<ChatMessage[]>(data.chat);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [modelLabel, setModelLabel] = useState("");

  const canSubmit = input.trim().length > 0 && !loading;

  const payloadSource = useMemo(
    () => ({
      transcript: data.transcript,
      decisions: data.decisions,
      actions: data.actions
    }),
    [data.actions, data.decisions, data.transcript]
  );

  useEffect(() => {
    document.body.className = "app-theme";
    return () => {
      document.body.className = "";
    };
  }, []);

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
      const result = await chatMeetingAi({
        projectId,
        meetingId,
        meetingTitle,
        question: trimmed,
        transcript: payloadSource.transcript,
        decisions: payloadSource.decisions,
        actions: payloadSource.actions
      });
      setModelLabel(result.model);
      setMessages((previous) => [
        ...previous,
        {
          role: "ai",
          text: result.unsupported ? "확인 가능한 근거가 없어 답변할 수 없습니다." : result.answer,
          sources: result.sources.map((source) => `${source.title} · ${source.type}`),
          unsupported: result.unsupported
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
          text: "Meeting AI 서비스에 연결하지 못했습니다. AI 서버와 OPENAI_API_KEY 설정을 확인해주세요."
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

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="topbar-left">
          <Link className="brand" to="/">
            <span className="brand-copy">
              <span className="brand-wordmark-main">meeting</span>
              <span className="brand-wordmark-accent">mind</span>
            </span>
          </Link>
          <div className="meeting-meta">
            <div className="meeting-title-row">
              <h1>{data.overview.title}</h1>
              {data.overview.status.map((status) => (
                <span key={status} className="status-pill live">
                  {status}
                </span>
              ))}
            </div>
            <p>{data.overview.subtitle}</p>
          </div>
        </div>
      </header>

      <main className="product-flow">
        <article className="flow-canvas split-ui">
          <div className="split-left">
            <div className="title-block">
              <h4>회의 STT 원문 / 보고서 미리보기</h4>
              <p>현재 회의에서 발췌된 원문과 AI 요약을 함께 탐색</p>
            </div>
            <div className="range-box">
              <strong>검색 범위 (현재 회의 한정)</strong>
              <div className="range-chips">
                <span>현재 회의 STT 원문</span>
                <span>현재 회의 AI 보고서</span>
                <span>현재 회의 결정사항</span>
                <span>현재 회의 Action Item</span>
              </div>
            </div>
            <div className="transcript-box">
              {data.transcript.map((row) => (
                <div key={`${row.time}-${row.speaker}`} className="transcript-row">
                  <span>{row.time}</span>
                  <strong>{row.speaker}</strong>
                  <p>{row.text}</p>
                </div>
              ))}
              <div className="placeholder-lines">
                <span />
                <span />
              </div>
            </div>
            <div className="source-grid">
              <div className="source-card">
                <div className="table-head">
                  <strong>핵심 결정사항</strong>
                  <span>{data.decisions.length}건</span>
                </div>
                <ul className="doc-list">
                  {data.decisions.map((item) => (
                    <li key={item.title}>
                      <strong>{item.title}</strong>
                      <span>{item.meta}</span>
                    </li>
                  ))}
                </ul>
              </div>
              <div className="source-card">
                <div className="table-head">
                  <strong>추출된 Action Item</strong>
                  <span>{data.actions.length}건</span>
                </div>
                <ul className="doc-list">
                  {data.actions.map((item) => (
                    <li key={item.title}>
                      <strong>{item.title}</strong>
                      <span>{item.meta}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </div>

          <aside className="split-right">
            <div className="chat-head">
              <strong>Meeting AI</strong>
              <span>검색 범위: 3회차 전용 (Project 전체 미포함)</span>
            </div>

            <div className="assistant-meta">
              <div className="assistant-chip">출처 표시</div>
              <div className="assistant-chip">현재 회의만 검색</div>
              <div className="assistant-chip">결정사항 우선</div>
              {modelLabel ? <div className="assistant-chip">모델: {modelLabel}</div> : null}
            </div>

            <div className="chat-thread">
              {messages.map((message, index) => (
                <div key={`${message.role}-${index}`} className={`bubble ${message.role}`}>
                  {message.text}
                  {message.sources?.length ? (
                    <div className="meeting-ai-source-list">
                      {message.sources.map((source) => (
                        <span key={`${message.role}-${index}-${source}`}>{source}</span>
                      ))}
                    </div>
                  ) : null}
                  {message.unsupported ? <div className="meeting-ai-unsupported">근거 없음</div> : null}
                </div>
              ))}
              {loading ? <div className="bubble ai">답변 생성 중입니다...</div> : null}
            </div>

            <div className="answer-foot">
              <strong>추천 질문</strong>
              <div className="quick-list">
                {data.suggestions.map((item) => (
                  <button key={item.label} onClick={() => void askMeetingAi(item.label)} type="button">
                    {item.label}
                  </button>
                ))}
              </div>
            </div>

            {error ? <div className="meeting-ai-error">{error}</div> : null}

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
    </div>
  );
}
