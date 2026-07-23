import { useCallback, useEffect, useState } from "react";
import {
  confirmTaskCandidate,
  dismissTaskCandidate,
  extractTaskCandidates,
  fetchTaskCandidates
} from "../api/tasks";
import type { AuthSession } from "../auth/session";
import type { TaskAssigneeOption, TaskCandidateSummary } from "../types";

export type MeetingTaskCandidateDraft = {
  id: string;
  title: string;
  description: string;
  assigneeId: string | null;
  dueDate: string;
  status: "candidate" | "registered" | "dismissed";
  sourceIds: string[];
  taskId: string | null;
};

type TaskCandidateStatus = "loading" | "ready" | "error";
type TaskCandidateAction = "idle" | "extracting" | "confirming" | "dismissing";

function toDraft(candidate: TaskCandidateSummary): MeetingTaskCandidateDraft {
  return {
    id: candidate.id,
    title: candidate.title,
    description: "",
    assigneeId: candidate.suggestedAssigneeId,
    dueDate: candidate.dueDate || "",
    status: candidate.status === "CONFIRMED"
      ? "registered"
      : candidate.status === "DISMISSED"
        ? "dismissed"
        : "candidate",
    sourceIds: candidate.sourceIds,
    taskId: null
  };
}

export function useMeetingTaskCandidates(session: AuthSession | null, meetingId: string) {
  const [status, setStatus] = useState<TaskCandidateStatus>("loading");
  const [candidates, setCandidates] = useState<MeetingTaskCandidateDraft[]>([]);
  const [assignees, setAssignees] = useState<TaskAssigneeOption[]>([]);
  const [canConfirm, setCanConfirm] = useState(false);
  const [action, setAction] = useState<TaskCandidateAction>("idle");
  const [activeCandidateId, setActiveCandidateId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  useEffect(() => {
    let active = true;

    if (!session || !meetingId) {
      setStatus("error");
      setError("태스크 후보를 확인하려면 로그인과 올바른 회의 주소가 필요합니다.");
      return () => {
        active = false;
      };
    }

    setStatus("loading");
    setError("");
    setNotice("");
    fetchTaskCandidates(session, meetingId)
      .then((response) => {
        if (!active) {
          return;
        }
        setCandidates(response.candidates.map(toDraft));
        setAssignees(response.assignees);
        setCanConfirm(response.canConfirm);
        setStatus("ready");
      })
      .catch((cause: unknown) => {
        if (!active) {
          return;
        }
        setStatus("error");
        setError(cause instanceof Error ? cause.message : "태스크 후보 조회에 실패했습니다.");
      });

    return () => {
      active = false;
    };
  }, [meetingId, session]);

  const updateCandidate = useCallback((candidateId: string, updates: Partial<Pick<MeetingTaskCandidateDraft, "assigneeId" | "description" | "dueDate" | "title">>) => {
    setCandidates((current) => current.map((candidate) => (
      candidate.id === candidateId ? { ...candidate, ...updates } : candidate
    )));
  }, []);

  const extract = useCallback(async () => {
    if (!session || !meetingId || action !== "idle") {
      return;
    }
    setAction("extracting");
    setError("");
    setNotice("");
    try {
      const response = await extractTaskCandidates(session, meetingId);
      if (response.unsupported || !response.candidates.length) {
        setError("현재 회의에는 태스크 후보를 생성할 근거가 없습니다.");
        return;
      }
      const generated = response.candidates.map(toDraft);
      const generatedIds = new Set(generated.map((candidate) => candidate.id));
      setCandidates((current) => [
        ...current.filter((candidate) => !generatedIds.has(candidate.id)),
        ...generated
      ]);
      setAssignees(response.assignees);
      setCanConfirm(response.canConfirm);
      setNotice("태스크 후보를 불러왔습니다. 내용을 검토한 뒤 확정하세요.");
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : "태스크 후보 생성에 실패했습니다.");
    } finally {
      setAction("idle");
    }
  }, [action, meetingId, session]);

  const confirm = useCallback(async (candidateId: string) => {
    if (!session || !meetingId || !canConfirm || action !== "idle") {
      return;
    }
    const candidate = candidates.find((item) => item.id === candidateId);
    if (!candidate || candidate.status !== "candidate" || !candidate.title.trim()) {
      return;
    }
    setAction("confirming");
    setActiveCandidateId(candidateId);
    setError("");
    setNotice("");
    try {
      const response = await confirmTaskCandidate(session, meetingId, candidateId, {
        title: candidate.title.trim(),
        description: candidate.description.trim() || null,
        assigneeId: candidate.assigneeId,
        dueDate: candidate.dueDate || null,
        status: "TODO"
      });
      setCandidates((current) => current.map((item) => (
        item.id === candidateId ? { ...item, status: "registered", taskId: response.taskId } : item
      )));
      setNotice("태스크가 프로젝트 칸반에 등록되었습니다.");
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : "태스크 후보 확정에 실패했습니다.");
    } finally {
      setAction("idle");
      setActiveCandidateId(null);
    }
  }, [action, canConfirm, candidates, meetingId, session]);

  const dismiss = useCallback(async (candidateId: string) => {
    if (!session || !meetingId || !canConfirm || action !== "idle") {
      return;
    }
    const candidate = candidates.find((item) => item.id === candidateId);
    if (!candidate || candidate.status !== "candidate") {
      return;
    }
    setAction("dismissing");
    setActiveCandidateId(candidateId);
    setError("");
    setNotice("");
    try {
      await dismissTaskCandidate(session, meetingId, candidateId);
      setCandidates((current) => current.map((item) => (
        item.id === candidateId ? { ...item, status: "dismissed" } : item
      )));
      setNotice("태스크 후보를 등록 대상에서 제외했습니다.");
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : "태스크 후보 제외에 실패했습니다.");
    } finally {
      setAction("idle");
      setActiveCandidateId(null);
    }
  }, [action, canConfirm, candidates, meetingId, session]);

  return {
    action,
    activeCandidateId,
    assignees,
    canConfirm,
    candidates,
    error,
    extract,
    notice,
    status,
    updateCandidate,
    confirm,
    dismiss
  };
}
