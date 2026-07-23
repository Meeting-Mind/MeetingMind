import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { chatProjectAi, fetchProjectAiHistory } from "../api/ai";
import type { AuthSession } from "../auth/session";
import { DataState } from "../components/common/DataState";
import { PageHeader } from "../components/common/PageHeader";
import { RoleBadge } from "../components/common/RoleBadge";
import { StatusBadge } from "../components/common/StatusBadge";
import { AppShell } from "../components/layout/AppShell";
import { SpaceLayout } from "../components/layout/SpaceLayout";
import { WorkspaceSidebar } from "../components/WorkspaceSidebar";
import type { ProjectKnowledgeItem, UnsupportedReason, WorkspaceData } from "../types";
import type { TeamMember, WorkspaceDataSource } from "../app/workspaceTypes";

type ProjectAiMessage = {
  role: "user" | "ai";
  text: string;
  tags?: string[];
  unsupportedReason?: UnsupportedReason | null;
};

function unsupportedMessage(reason: UnsupportedReason | null) {
  switch (reason) {
    case "LOW_RELEVANCE":
      return "검색된 프로젝트 기록은 있지만 질문에 답할 만큼 관련성이 높지 않습니다.";
    case "MODEL_UNSUPPORTED":
      return "제공된 프로젝트 근거만으로는 답변을 확정할 수 없습니다.";
    case "UNVERIFIED_OUTPUT":
      return "응답의 근거를 확인하지 못해 답변을 제공할 수 없습니다.";
    default:
      return "접근 가능한 프로젝트 기록에서 확인 가능한 근거가 없습니다.";
  }
}

export function ProjectAiPage({
  currentUserEmail,
  projectAiSpaceIds,
  projectKnowledge,
  projectMembers,
  session,
  spaces,
  workspaceDataSource,
  onCreateProject
}: {
  currentUserEmail: string;
  projectAiSpaceIds: string[];
  projectKnowledge: Record<string, ProjectKnowledgeItem[]>;
  projectMembers: Record<string, TeamMember[]>;
  session: AuthSession | null;
  spaces: WorkspaceData["workspaceHome"]["spaces"];
  workspaceDataSource: WorkspaceDataSource;
  onCreateProject?: (payload: { name: string; description: string }) => Promise<void>;
}) {
  const { spaceId = "" } = useParams<{ spaceId: string }>();
  const [messages, setMessages] = useState<ProjectAiMessage[]>([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [model, setModel] = useState("");
  const chatRef = useRef<HTMLDivElement | null>(null);
  const selectedSpace = spaces.find((space) => space.id === spaceId);
  const members = selectedSpace ? projectMembers[selectedSpace.name] ?? [] : [];
  const currentMember = members.find((member) => member.email === currentUserEmail);
  const projectAiAvailable = projectAiSpaceIds.includes(spaceId);
  const knowledge = projectKnowledge[spaceId] ?? [];

  useEffect(() => {
    document.body.className = "app-theme project-ai-body";
    return () => {
      document.body.className = "";
    };
  }, []);

  useEffect(() => {
    let active = true;
    if (!session || !selectedSpace || !projectAiAvailable) {
      setMessages([]);
      return () => {
        active = false;
      };
    }

    setMessages([{
      role: "ai",
      text: `${selectedSpace.name}의 공식 지식과 접근 가능한 회의만 검색합니다.`
    }]);
    void fetchProjectAiHistory(session, selectedSpace.id)
      .then((history) => {
        if (active && history.messages.length) {
          setMessages(history.messages.map((message) => ({
            role: message.role === "USER" ? "user" : "ai",
            text: message.content
          })));
        }
      })
      .catch(() => {
        // Optional history failure should not disable a new Project AI question.
      });

    return () => {
      active = false;
    };
  }, [projectAiAvailable, selectedSpace, session]);

  useEffect(() => {
    chatRef.current?.scrollTo({ top: chatRef.current.scrollHeight, behavior: "smooth" });
  }, [loading, messages]);

  if (!selectedSpace) {
    return (
      <AppShell
        contentClassName="project-ai-main"
        sidebar={<WorkspaceSidebar activeItem="catalog" onCreateProject={onCreateProject} />}
      >
        <DataState state="notFound" title="프로젝트를 찾을 수 없습니다" description="프로젝트 목록에서 접근 가능한 프로젝트를 선택해 주세요." />
      </AppShell>
    );
  }

  const selectedSpaceId = selectedSpace.id;

  async function ask(question: string) {
    const trimmed = question.trim();
    if (!trimmed || loading || !session || !projectAiAvailable) {
      return;
    }

    setInput("");
    setError("");
    setLoading(true);
    setMessages((previous) => [...previous, { role: "user", text: trimmed }]);
    try {
      const response = await chatProjectAi(session, selectedSpaceId, { question: trimmed });
      const tags = Array.from(new Set(response.sources.map((source) => source.type === "projectKnowledge" ? `공식 지식 · ${source.title}` : `회의 기록 · ${source.title}`)));
      setModel(response.model);
      setMessages((previous) => [...previous, {
        role: "ai",
        text: response.unsupported ? unsupportedMessage(response.unsupportedReason) : response.answer,
        tags: tags.length ? tags : response.unsupported ? ["근거 없음"] : undefined,
        unsupportedReason: response.unsupportedReason
      }]);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Project AI에 연결하지 못했습니다.");
      setMessages((previous) => [...previous, { role: "ai", text: "Project AI 응답을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요." }]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <SpaceLayout
      activeItem="ai"
      contentClassName="project-ai-main"
      dataSource={workspaceDataSource}
      onCreateProject={onCreateProject}
      projectName={selectedSpace.name}
      spaceId={selectedSpace.id}
    >
      <PageHeader
        actions={<Link className="mm-common-button mm-common-button--secondary" to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>프로젝트 홈</Link>}
        breadcrumb={(
          <>
            <Link to="/spaces">프로젝트 목록</Link>
            <span aria-hidden="true">/</span>
            <Link to={`/spaces/${encodeURIComponent(selectedSpace.id)}`}>{selectedSpace.name}</Link>
            <span aria-hidden="true">/</span>
            <strong>Project AI</strong>
          </>
        )}
        description="공식 프로젝트 지식과 접근 가능한 회의 기록만 검색합니다. 근거가 없으면 추정하지 않습니다."
        eyebrow="Project AI"
        meta={currentMember ? <RoleBadge role={currentMember.spaceRole} scope="space" /> : null}
        title="프로젝트에 질문하기"
      />

      <div className="project-ai-layout">
        <section aria-labelledby="project-ai-chat-title" className="project-ai-chat-surface">
          <div className="project-ai-surface-header">
            <div>
              <p className="project-home-section-kicker">Scoped answer</p>
              <h2 id="project-ai-chat-title">대화</h2>
            </div>
            <StatusBadge context="generic" label={projectAiAvailable ? "검색 가능" : "준비 중"} status={projectAiAvailable ? "COMPLETED" : "PENDING"} />
          </div>
          <div aria-busy={loading} aria-label="Project AI 대화" className="project-ai-chat-log" ref={chatRef} role="log">
            {projectAiAvailable ? messages.map((message, index) => (
              <article className={`project-ai-message ${message.role} ${message.unsupportedReason ? "unsupported" : ""}`} key={`${message.role}-${index}`}>
                <p>{message.text}</p>
                {message.tags?.length ? (
                  <div aria-label="답변 근거" className="project-ai-citations">
                    <span className="project-ai-citations-label">근거</span>
                    <div className="project-ai-source-tags" role="list">
                      {message.tags.map((tag) => <span key={`${index}-${tag}`} role="listitem">{tag}</span>)}
                    </div>
                  </div>
                ) : null}
              </article>
            )) : <DataState state="empty" title="Project AI가 아직 준비되지 않았습니다" description="프로젝트 지식과 접근 가능한 회의가 준비되면 질문할 수 있습니다." />}
            {loading ? <p aria-live="polite" className="project-ai-loading">근거를 확인하고 있습니다...</p> : null}
          </div>
          {error ? <p aria-live="assertive" className="project-ai-error">{error}</p> : null}
          <form className="project-ai-form" onSubmit={(event) => { event.preventDefault(); void ask(input); }}>
            <input aria-label="Project AI 질문 입력" disabled={!projectAiAvailable || loading} onChange={(event) => setInput(event.target.value)} placeholder="접근 가능한 회의와 공식 지식에 대해 질문하세요" value={input} />
            <button className="mm-common-button mm-common-button--primary" disabled={!projectAiAvailable || loading || !input.trim()} type="submit">{loading ? "확인 중" : "질문"}</button>
          </form>
          {model ? <p className="project-ai-model">응답 모델: {model}</p> : null}
        </section>

        <aside className="project-ai-scope-column">
          <section className="project-ai-scope-surface" aria-labelledby="project-ai-scope-title">
            <p className="project-home-section-kicker">Search scope</p>
            <h2 id="project-ai-scope-title">검색 범위</h2>
            <ul>
              <li>현재 프로젝트의 공식 Project Knowledge</li>
              <li>내가 접근할 수 있는 회의 기록</li>
              <li>권한 필터가 검색 전에 적용됨</li>
            </ul>
            <p className="project-ai-scope-note">근거가 없으면 추정하지 않고 확인 불가로 표시합니다.</p>
          </section>
          <section className="project-ai-sources-surface" aria-labelledby="project-ai-sources-title">
            <div className="project-ai-surface-header">
              <div>
                <p className="project-home-section-kicker">Knowledge</p>
                <h2 id="project-ai-sources-title">공식 지식</h2>
              </div>
              <span>{knowledge.length}건</span>
            </div>
            {knowledge.length ? knowledge.slice(0, 5).map((item) => (
              <div className="project-ai-knowledge-row" key={item.id}>
                <strong>{item.title}</strong>
                <span>{item.type} · {item.embeddingStatus === "COMPLETED" ? "검색 가능" : "처리 중"}</span>
              </div>
            )) : <p className="project-ai-muted">등록된 공식 지식이 없습니다.</p>}
            <Link className="project-home-inline-link" to={`/spaces/${encodeURIComponent(selectedSpace.id)}/knowledge`}>지식 관리 열기 →</Link>
          </section>
        </aside>
      </div>
    </SpaceLayout>
  );
}
