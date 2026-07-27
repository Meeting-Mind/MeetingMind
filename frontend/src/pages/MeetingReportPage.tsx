import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import type { AuthSession } from "../auth/session";
import {
  confirmMeetingReport,
  downloadMeetingReport,
  editMeetingReportWithAi,
  fetchMeetingReportDetail,
  fetchMeetingReports,
  generateReportCandidate,
  restoreMeetingReport,
} from "../api/reports";
import { fetchMeetingDialogue } from "../api/transcripts";
import { ApiRequestError } from "../api/client";
import { ConfirmDialog } from "../components/common/ConfirmDialog";
import { ReportAssistant, type AssistantMessage } from "../features/report/ReportAssistant";
import { ReportDocument } from "../features/report/ReportDocument";
import { diffReport, type ReportDiff } from "../features/report/diff";
import { reportUnsupportedMessage } from "../features/report/unsupported";
import type {
  AiSource,
  ReportCandidateActionItem,
  ReportCandidateDecision,
  ReportDetailResponse,
  ReportDownloadFormat,
  StoredReportCandidate,
  UnsupportedReason,
} from "../types";

type Props = { session: AuthSession | null };

type ReportView = {
  id: string;
  status: ReportDetailResponse["status"];
  title: string;
  summary: string;
  decisions: ReportCandidateDecision[];
  actionItems: ReportCandidateActionItem[];
  version: number;
  confirmedAt: string | null;
};

function fromDetail(detail: ReportDetailResponse): ReportView {
  return {
    id: detail.id,
    status: detail.status,
    title: detail.title,
    summary: detail.summary ?? "",
    decisions: detail.decisions ?? [],
    actionItems: detail.actionItems ?? [],
    version: detail.version,
    confirmedAt: detail.confirmedAt,
  };
}

function fromCandidate(candidate: StoredReportCandidate): ReportView {
  return {
    id: candidate.id,
    status: candidate.status,
    title: candidate.title,
    summary: candidate.summary ?? "",
    decisions: candidate.decisions ?? [],
    actionItems: candidate.actionItems ?? [],
    version: candidate.version,
    confirmedAt: null,
  };
}

let messageSeq = 0;
function message(role: AssistantMessage["role"], text: string): AssistantMessage {
  messageSeq += 1;
  return { id: `msg-${messageSeq}`, role, text };
}

export function MeetingReportPage({ session }: Props) {
  const { meetingId = "", spaceId = "" } = useParams();
  const [report, setReport] = useState<ReportView | null>(null);
  const [sources, setSources] = useState<AiSource[]>([]);
  const [transcriptState, setTranscriptState] = useState<"loading" | "ready" | "empty" | "error">("loading");
  const [messages, setMessages] = useState<AssistantMessage[]>([]);
  const [suggestion, setSuggestion] = useState<ReportView | null>(null);
  const [diff, setDiff] = useState<ReportDiff | null>(null);
  const [unsupported, setUnsupported] = useState(false);
  const [unsupportedReason, setUnsupportedReason] = useState<UnsupportedReason | null>(null);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    if (!session || !meetingId) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      const [list, dialogue] = await Promise.all([
        fetchMeetingReports(session, meetingId),
        // 전사가 아직 없는 회의는 404를 준다. 이는 고장이 아니라 정상 상태이므로
        // "확인하지 못했습니다"로 보여주면 안 된다.
        fetchMeetingDialogue(session, meetingId).catch((cause) => {
          if (cause instanceof ApiRequestError && cause.status === 404) {
            return { rows: [], partials: [] };
          }
          throw cause;
        }),
      ]);
      setTranscriptState(dialogue.rows.length || dialogue.partials.length ? "ready" : "empty");
      setSources(
        dialogue.rows.map((row) => ({
          sourceId: row.segmentId,
          type: "transcript" as const,
          title: row.speakerName ?? row.speakerLabel,
          speaker: row.speakerName ?? row.speakerLabel,
          text: row.text,
        }))
      );
      // 가장 최근 초안이 더 오래된 확정본보다 우선한다.
      const current = list.reports[0];
      setReport(current ? fromDetail(await fetchMeetingReportDetail(session, meetingId, current.id)) : null);
    } catch (cause) {
      setTranscriptState("error");
      setError(cause instanceof Error ? cause.message : "회의록과 전사 상태를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [session, meetingId]);

  useEffect(() => {
    void load();
  }, [load]);

  const isConfirmed = report?.status === "CONFIRMED";

  async function generate() {
    if (!session || !meetingId || pending || transcriptState !== "ready") {
      return;
    }
    setPending(true);
    setError("");
    setUnsupported(false);
    setUnsupportedReason(null);
    try {
      const result = await generateReportCandidate(session, meetingId);
      if (result.unsupported) {
        if (result.candidate) {
          throw new Error("회의록 생성 응답 상태가 올바르지 않습니다.");
        }
        setUnsupported(true);
        setUnsupportedReason(result.unsupportedReason);
        return;
      }
      if (!result.candidate) {
        throw new Error("회의록 생성 결과가 없습니다.");
      }
      setReport(fromCandidate(result.candidate));
      setSources(result.sources);
      const droppedNotice = result.droppedCount > 0
        ? ` 근거를 확인할 수 없는 항목 ${result.droppedCount}개는 제외했습니다.`
        : "";
      setMessages([message("ai", `회의록을 만들었습니다. 검토하고 고칠 수 있습니다.${droppedNotice}`)]);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "회의록을 만들지 못했습니다.");
    } finally {
      setPending(false);
    }
  }

  async function ask(instruction: string) {
    if (!session || !meetingId || !report || pending || isConfirmed) {
      return;
    }
    setPending(true);
    setError("");
    setMessages((current) => [...current, message("user", instruction)]);
    try {
      const result = await editMeetingReportWithAi(session, meetingId, report.id, instruction);
      if (result.unsupported) {
        if (result.candidate) {
          throw new Error("회의록 수정 응답 상태가 올바르지 않습니다.");
        }
        const reason = reportUnsupportedMessage(result.unsupportedReason);
        setMessages((current) => [...current, message("ai", `${reason.title} ${reason.why}`)]);
        return;
      }
      if (!result.candidate) {
        throw new Error("회의록 수정 결과가 없습니다.");
      }
      const next = fromCandidate(result.candidate);
      setSuggestion(next);
      // 서버는 무엇을 바꿨는지 알려주지 않는다. 이전 상태와 비교해 직접 계산한다.
      setDiff(diffReport(report, next));
      setSources(result.sources);
      const droppedNotice = result.droppedCount > 0
        ? ` 근거를 확인할 수 없는 항목 ${result.droppedCount}개는 제외했습니다.`
        : "";
      setMessages((current) => [...current, message("ai", `수정을 준비했습니다. 바뀔 내용을 확인하세요.${droppedNotice}`)]);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "수정 제안을 만들지 못했습니다.");
    } finally {
      setPending(false);
    }
  }

  function applySuggestion() {
    if (!suggestion) {
      return;
    }
    setReport(suggestion);
    setSuggestion(null);
    setDiff(null);
    setMessages((current) => [...current, message("ai", "적용했습니다.")]);
  }

  function discardSuggestion() {
    setSuggestion(null);
    setDiff(null);
  }

  async function confirm() {
    if (!session || !meetingId || !report || pending) {
      return;
    }
    setPending(true);
    try {
      const result = await confirmMeetingReport(session, meetingId, report.id);
      setReport({ ...report, status: result.status, version: result.version, confirmedAt: result.confirmedAt });
      setConfirmOpen(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "회의록을 확정하지 못했습니다.");
    } finally {
      setPending(false);
    }
  }

  async function restore() {
    if (!session || !meetingId || !report || pending) {
      return;
    }
    setPending(true);
    try {
      const result = await restoreMeetingReport(session, meetingId, report.id);
      setReport(fromDetail(await fetchMeetingReportDetail(session, meetingId, result.id)));
      setMessages([]);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "새 초안을 만들지 못했습니다.");
    } finally {
      setPending(false);
    }
  }

  async function download(format: ReportDownloadFormat) {
    if (!session || !meetingId || !report) {
      return;
    }
    try {
      const blob = await downloadMeetingReport(session, meetingId, report.id, format);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${report.title || "meeting-report"}.${format === "markdown" ? "md" : format}`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "회의록을 내려받지 못했습니다.");
    }
  }

  if (loading) {
    return <div className="report-page"><p className="report-none">회의록을 불러오는 중입니다.</p></div>;
  }

  if (unsupported || (!report && transcriptState !== "loading")) {
    const reason = reportUnsupportedMessage(unsupportedReason);
    return (
      <div className="report-page">
        <div className="report-empty">
          <b>{unsupported ? "회의록을 만들지 못했습니다" : "아직 회의록이 없습니다"}</b>
          {unsupported ? (
            <div className="report-why">
              <b>{reason.title}</b>
              <span>{reason.why}</span>
            </div>
          ) : (
            <p>
              {transcriptState === "ready"
                ? "전사가 준비되었습니다. 회의록을 만들 수 있습니다."
                : transcriptState === "empty"
                  ? "회의록을 만들 전사 내용이 없습니다."
                  : "전사 상태를 확인하지 못했습니다."}
            </p>
          )}
          <div className="report-empty-row">
            <Link className="report-link" to={`/spaces/${encodeURIComponent(spaceId)}/meetings/${encodeURIComponent(meetingId)}/transcript`}>
              전사 보기
            </Link>
            <button className="report-link report-primary" disabled={transcriptState !== "ready" || pending} onClick={() => void generate()} type="button">
              {unsupported ? "다시 생성" : "회의록 만들기"}
            </button>
          </div>
          {error ? <p className="report-error" role="alert">{error}</p> : null}
        </div>
      </div>
    );
  }

  if (!report) {
    return <div className="report-page"><p className="report-none">회의록을 불러오는 중입니다.</p></div>;
  }

  const shown = suggestion ?? report;

  return (
    <div className="report-page report-split">
      <div className="report-main">
        <div className="report-bar">
          <div className="report-bar-left">
            <span className={`report-badge report-badge-${report.status.toLowerCase()}`}>
              {isConfirmed ? "확정" : "초안"}
            </span>
            <span className="report-meta">
              버전 {report.version}
              {isConfirmed && report.confirmedAt ? ` · ${new Date(report.confirmedAt).toLocaleString("ko-KR")} 확정` : ""}
            </span>
          </div>
          <div className="report-bar-right">
            <button className="report-link" onClick={() => void download("markdown")} type="button">내려받기</button>
            {isConfirmed ? (
              <button className="report-link" disabled={pending} onClick={() => void restore()} type="button">새 초안 만들기</button>
            ) : (
              <button className="report-link report-primary" disabled={pending} onClick={() => setConfirmOpen(true)} type="button">확정하기</button>
            )}
          </div>
        </div>

        <h2 className="report-title">{report.title}</h2>

        {isConfirmed ? (
          <p className="report-locked">확정된 회의록은 바로 고칠 수 없습니다. 새 초안을 만들면 다음 버전으로 이어집니다.</p>
        ) : null}

        <ReportDocument
          actionItems={shown.actionItems}
          decisions={shown.decisions}
          sources={sources}
          summary={shown.summary}
          summaryChanged={Boolean(diff?.summary.changed)}
        />

        {error ? <p className="report-error" role="alert">{error}</p> : null}
      </div>

      <ReportAssistant
        diff={diff}
        lockedReason={isConfirmed ? "확정된 회의록은 프로젝트 검색의 근거가 됩니다. 고치려면 새 초안을 만드세요." : null}
        messages={messages}
        onApply={applySuggestion}
        onAsk={(instruction) => void ask(instruction)}
        onDiscard={discardSuggestion}
        pending={pending}
      />

      <ConfirmDialog
        busy={pending}
        cancelLabel="취소"
        confirmLabel="확정하기"
        description="이 회의록을 공식 기록으로 확정합니다. 확정하면 프로젝트 검색의 근거가 되고, 고치려면 새 초안을 만들어야 합니다."
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => void confirm()}
        open={confirmOpen}
        title="회의록을 확정할까요?"
        tone="default"
      />
    </div>
  );
}
