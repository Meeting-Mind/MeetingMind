import { useEffect, useMemo, useState } from "react";
import { Check, Download, FileText, RotateCcw, Send, Sparkles } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import { editMeetingReportWithAi, confirmMeetingReport, downloadMeetingReport, fetchMeetingReportDetail, fetchMeetingReports, generateReportCandidate, restoreMeetingReport, updateMeetingReport } from "../api/reports";
import { fetchMeetingDialogue } from "../api/transcripts";
import { ConfirmDialog } from "../components/common/ConfirmDialog";
import type { AiSource, ReportDetailResponse, ReportDownloadFormat, ReportSummary, StoredReportCandidate } from "../types";

type Props = { session: AuthSession | null };

function candidateDetail(candidate: StoredReportCandidate): ReportDetailResponse {
  return { id: candidate.id, meetingId: candidate.meetingId, status: candidate.status, title: candidate.title, summary: candidate.summary, markdown: candidate.markdown, version: candidate.version, isCurrent: candidate.isCurrent, createdAt: candidate.createdAt, confirmedAt: null, sourceIds: candidate.sourceIds };
}

function sectionItems(markdown: string, names: RegExp) {
  let active = false;
  return markdown.split("\n").reduce<string[]>((items, raw) => {
    const line = raw.trim();
    const heading = /^(#{1,3})\s+(.+)$/.exec(line);
    if (heading) active = names.test(heading[2]);
    else if (active && /^(?:[-*]|\d+\.)\s+/.test(line)) {
      const item = line.replace(/^(?:[-*]|\d+\.)\s+/, "");
      // Source references belong in the evidence section, not in the human-readable decision list.
      if (!/^출처\s*:/i.test(item) && !/transcript-segment-[0-9a-f]{8}-[0-9a-f-]{27,}/i.test(item)) {
        items.push(item);
      }
    }
    return items;
  }, []);
}

function renderInlineMarkdown(text: string) {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, index) => part.startsWith("**") && part.endsWith("**") ? <strong key={`${part}-${index}`}>{part.slice(2, -2)}</strong> : part);
}

function statusLabel(report: ReportDetailResponse) {
  return report.status === "CONFIRMED" ? "Confirmed" : report.status === "DRAFT" ? "Draft" : "Candidate";
}

export function MeetingReportPage({ session }: Props) {
  const { meetingId = "", spaceId = "" } = useParams();
  const [report, setReport] = useState<ReportDetailResponse | null>(null);
  const [reports, setReports] = useState<ReportSummary[]>([]);
  const [sources, setSources] = useState<AiSource[]>([]);
  const [summary, setSummary] = useState("");
  const [markdown, setMarkdown] = useState("");
  const [editing, setEditing] = useState(false);
  const [chatInput, setChatInput] = useState("");
  const [chatAnswer, setChatAnswer] = useState("");
  const [suggestion, setSuggestion] = useState<ReportDetailResponse | null>(null);
  const [transcriptState, setTranscriptState] = useState<"loading" | "ready" | "empty" | "error">("loading");
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState("");

  async function load() {
    if (!session || !meetingId) return;
    setLoading(true); setError("");
    try {
      const [reportResponse, dialogue] = await Promise.all([fetchMeetingReports(session, meetingId), fetchMeetingDialogue(session, meetingId)]);
      setReports(reportResponse.reports);
      setTranscriptState(dialogue.rows.length || dialogue.partials.length ? "ready" : "empty");
      setSources(dialogue.rows.map((row) => ({ sourceId: row.segmentId, type: "transcript" as const, title: `${row.speakerName ?? row.speakerLabel} · Transcript`, text: row.text })));
      // The newest draft/candidate must win over an older confirmed report.
      const current = reportResponse.reports[0];
      if (current) {
        const detail = await fetchMeetingReportDetail(session, meetingId, current.id);
        setReport(detail); setSummary(detail.summary ?? ""); setMarkdown(detail.markdown ?? "");
      } else setReport(null);
    } catch (cause) {
      setTranscriptState("error");
      setError(cause instanceof Error ? cause.message : "보고서와 전사 상태를 불러오지 못했습니다.");
    } finally { setLoading(false); }
  }

  useEffect(() => { void load(); }, [meetingId, session]);

  const decisions = useMemo(() => sectionItems(markdown, /(결정|decision)/i), [markdown]);
  const canGenerate = transcriptState === "ready";
  const isConfirmed = report?.status === "CONFIRMED";

  async function generate() {
    if (!session || !meetingId || pending || !canGenerate) return;
    setPending(true); setError("");
    try { const result = await generateReportCandidate(session, meetingId); if (!result.candidate) throw new Error("생성된 보고서가 없습니다."); const detail = candidateDetail(result.candidate); setReport(detail); setSummary(detail.summary ?? ""); setMarkdown(detail.markdown ?? ""); setSources(result.sources); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "보고서를 생성하지 못했습니다."); }
    finally { setPending(false); }
  }

  async function confirm() {
    if (!session || !meetingId || !report || pending) return;
    setPending(true); setError("");
    try { const result = await confirmMeetingReport(session, meetingId, report.id); setReport({ ...report, status: result.status, version: result.version, isCurrent: result.isCurrent, confirmedAt: result.confirmedAt }); setConfirmOpen(false); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "보고서를 확정하지 못했습니다."); }
    finally { setPending(false); }
  }

  async function save() {
    if (!session || !meetingId || !report || pending || isConfirmed) return;
    setPending(true); setError("");
    try { const result = await updateMeetingReport(session, meetingId, report.id, { summary, markdown }); setReport({ ...report, summary, markdown, status: result.status, version: result.version }); setEditing(false); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "보고서를 저장하지 못했습니다."); }
    finally { setPending(false); }
  }

  async function askAi(instruction: string) {
    if (!session || !meetingId || !report || pending || isConfirmed || !instruction.trim()) return;
    setPending(true); setError(""); setChatAnswer("");
    try { const result = await editMeetingReportWithAi(session, meetingId, report.id, instruction); if (!result.candidate) throw new Error("AI 제안이 반환되지 않았습니다."); setSuggestion(candidateDetail(result.candidate)); setSources(result.sources); setChatAnswer("수정 제안을 만들었습니다. 적용하기 전에 내용을 확인해주세요."); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "AI 수정 제안을 만들지 못했습니다."); }
    finally { setPending(false); }
  }

  async function download(format: ReportDownloadFormat) {
    if (!session || !meetingId || !report) return;
    try { const blob = await downloadMeetingReport(session, meetingId, report.id, format); const url = URL.createObjectURL(blob); const anchor = document.createElement("a"); anchor.href = url; anchor.download = `${report.title || "meeting-report"}.${format === "markdown" ? "md" : format}`; anchor.click(); URL.revokeObjectURL(url); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "보고서를 다운로드하지 못했습니다."); }
  }

  async function restore() {
    if (!session || !meetingId || !report || pending) return;
    setPending(true);
    try { const result = await restoreMeetingReport(session, meetingId, report.id); const detail = await fetchMeetingReportDetail(session, meetingId, result.id); setReport(detail); setSummary(detail.summary ?? ""); setMarkdown(detail.markdown ?? ""); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "새 초안을 만들지 못했습니다."); }
    finally { setPending(false); }
  }

  if (loading) return <div className="meeting-report-page"><div className="meeting-report-empty">보고서와 전사 상태를 불러오는 중입니다.</div></div>;
  if (pending && !report) return <div className="meeting-report-page"><div className="meeting-report-empty meeting-report-generating"><Sparkles size={30} /><h2>Generating Report</h2><p>회의 전사를 분석하고 요약, 결정사항, 실행 항목을 추출하고 있습니다.</p><span className="meeting-report-loader" aria-label="보고서 생성 중" /></div></div>;
  if (!report) return <div className="meeting-report-page"><div className="meeting-report-empty"><FileText size={30} /><h2>{transcriptState === "ready" ? "Transcript is ready" : transcriptState === "empty" ? "Transcript is empty" : "Transcript is not ready"}</h2><p>{transcriptState === "ready" ? "회의 전사가 준비되었습니다. AI 보고서를 생성할 수 있습니다." : transcriptState === "empty" ? "보고서를 생성할 수 있는 전사 내용이 없습니다." : transcriptState === "error" ? "Unable to check transcript status. Please try again." : "전사 상태를 확인하고 있습니다."}</p>{transcriptState === "error" ? <button className="meeting-report-secondary" onClick={() => void load()}>Retry</button> : <button className="meeting-report-primary" disabled={!canGenerate || pending} onClick={() => void generate()}><Sparkles size={16} /> Generate AI Report</button>}{error && transcriptState !== "error" ? <p className="meeting-report-error" role="alert">{error}</p> : null}</div></div>;

  return <div className="meeting-report-page">
    <div className={`meeting-report-status status-${report.status.toLowerCase()}`}><div><span>{statusLabel(report)}</span><p>{isConfirmed ? "공식 회의록으로 확정되었습니다." : "AI가 생성한 초안입니다. 검토 후 확정하세요."}</p></div><div className="meeting-report-actions">{!isConfirmed ? <button onClick={() => setEditing((value) => !value)}>{editing ? "Preview" : "Start Review"}</button> : <button onClick={() => void restore()} disabled={pending}><RotateCcw size={14} /> Create New Draft</button>}{!isConfirmed ? <button className="meeting-report-primary" disabled={pending} onClick={() => setConfirmOpen(true)}>Confirm Report</button> : null}<details className="meeting-report-menu"><summary><Download size={14} /> More Actions</summary><div className="meeting-report-menu-items"><button onClick={() => void download("markdown")}>Download Markdown</button><button onClick={() => void download("pdf")}>Download PDF</button><button onClick={() => void download("docx")}>Download DOCX</button></div></details></div></div>
    <ol className="meeting-report-stepper">{["Candidate", "Draft", "Confirmed"].map((label, index) => <li className={index <= (isConfirmed ? 2 : report.status === "DRAFT" ? 1 : 0) ? "active" : ""} key={label}><span>{index + 1}</span>{label}</li>)}</ol>
    <div className="meeting-report-layout"><main>
      <section className="meeting-report-card"><div className="meeting-report-card-head"><h2>Meeting Summary</h2>{editing && !isConfirmed ? <button disabled={pending} onClick={() => void save()}>Save draft</button> : null}</div>{editing && !isConfirmed ? <textarea value={summary} onChange={(event) => setSummary(event.target.value)} /> : <p>{summary || "No summary was generated."}</p>}</section>
      <section className="meeting-report-card"><h2>Key Decisions</h2><div className="meeting-report-list">{decisions.length ? decisions.map((item) => <div key={item}><b><Check size={13} /></b><p>{renderInlineMarkdown(item)}</p></div>) : <p>No decisions were extracted.</p>}</div></section>
      <section className="meeting-report-card"><h2>Sources / Transcript Evidence</h2><div className="meeting-report-sources">{sources.filter((source) => !report.sourceIds.length || report.sourceIds.includes(source.sourceId)).length ? sources.filter((source) => !report.sourceIds.length || report.sourceIds.includes(source.sourceId)).map((source) => <span className="meeting-report-source" key={source.sourceId} title={source.text}><strong>{source.title}</strong><small>{source.text}</small></span>) : report.sourceIds.length ? report.sourceIds.map((sourceId, index) => <span className="meeting-report-source" key={sourceId}><strong>Transcript segment {index + 1}</strong><small>Source evidence from this meeting</small></span>) : <p>사용된 전사 근거가 없습니다.</p>}</div></section>
      <Link className="meeting-report-back" to={`/spaces/${encodeURIComponent(spaceId)}/meetings/${encodeURIComponent(meetingId)}/transcript`}>View transcript</Link>
    </main><aside className="meeting-report-assistant"><div className="meeting-report-assistant-head"><Sparkles size={18} /><div><h2>AI Editing Assistant</h2><p>현재 회의의 보고서와 전사만 사용합니다.</p></div></div><div className="meeting-report-prompts">{["요약을 간결하게 정리해줘", "결정사항만 추려줘", "할 일을 추출해줘", "근거가 부족한 내용을 찾아줘"].map((prompt) => <button key={prompt} disabled={pending || isConfirmed} onClick={() => void askAi(prompt)}>{prompt}</button>)}</div>{chatAnswer ? <p className="meeting-report-ai-answer">{chatAnswer}</p> : null}{suggestion ? <div className="meeting-report-suggestion"><strong>수정 제안</strong><p>{suggestion.summary}</p><div><button onClick={() => { setReport(suggestion); setSummary(suggestion.summary); setMarkdown(suggestion.markdown ?? ""); setSuggestion(null); }}>Apply</button><button onClick={() => setSuggestion(null)}>Reject</button></div></div> : null}<div className="meeting-report-chat"><input value={chatInput} disabled={pending || isConfirmed} onChange={(event) => setChatInput(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") { void askAi(chatInput); setChatInput(""); } }} placeholder={isConfirmed ? "Confirmed Report는 직접 수정할 수 없습니다." : "Ask AI to edit..."} /><button disabled={pending || isConfirmed || !chatInput.trim()} onClick={() => { void askAi(chatInput); setChatInput(""); }} aria-label="AI 요청 전송"><Send size={16} /></button></div></aside></div>
    {error ? <p className="meeting-report-error" role="alert">{error}</p> : null}<ConfirmDialog open={confirmOpen} title="Confirm official report?" description="이 보고서를 공식 회의록으로 확정합니다. 확정 후에는 새 초안을 만들어 수정할 수 있습니다." confirmLabel="Confirm report" cancelLabel="Cancel" tone="default" busy={pending} onCancel={() => setConfirmOpen(false)} onConfirm={() => void confirm()}>{error ? <p className="meeting-report-dialog-error" role="alert">{error}</p> : null}</ConfirmDialog>
  </div>;
}
