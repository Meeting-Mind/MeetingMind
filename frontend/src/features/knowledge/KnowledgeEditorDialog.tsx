import { X } from "lucide-react";
import React, { useEffect, useState } from "react";
import type { ProjectKnowledgeDetailResponse, ProjectKnowledgeType } from "../../types";
import { useKnowledgeMutations } from "./hooks";

const TYPE_OPTIONS: Array<{ value: ProjectKnowledgeType; label: string }> = [
  { value: "manual", label: "수동 지식" },
  { value: "decision", label: "결정" },
  { value: "report", label: "보고서" },
  { value: "external", label: "외부 자료" }
];

interface KnowledgeEditorDialogProps {
  spaceId: string;
  /** null이면 신규 등록, 값이 있으면 해당 지식 수정 */
  editing: ProjectKnowledgeDetailResponse | null;
  onClose: () => void;
  onSaved: (knowledgeId: string) => void;
}

/** 지식 등록·수정 다이얼로그. legacy ProjectKnowledge의 편집 기능을 대체한다. */
export function KnowledgeEditorDialog({ spaceId, editing, onClose, onSaved }: KnowledgeEditorDialogProps) {
  const { create, update } = useKnowledgeMutations(spaceId);
  const [type, setType] = useState<ProjectKnowledgeType>(editing?.type ?? "manual");
  const [title, setTitle] = useState(editing?.title ?? "");
  const [content, setContent] = useState(editing?.content ?? "");
  const [sourceMeetingId, setSourceMeetingId] = useState(editing?.sourceMeetingId ?? "");
  const [error, setError] = useState("");

  useEffect(() => {
    setType(editing?.type ?? "manual");
    setTitle(editing?.title ?? "");
    setContent(editing?.content ?? "");
    setSourceMeetingId(editing?.sourceMeetingId ?? "");
    setError("");
  }, [editing]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  const saving = create.isPending || update.isPending;

  async function handleSubmit() {
    if (saving) return;
    if (!title.trim() || !content.trim()) {
      setError("제목과 내용을 모두 입력해 주세요.");
      return;
    }
    setError("");
    try {
      if (editing) {
        await update.mutateAsync({
          knowledgeId: editing.id,
          request: { title: title.trim(), content: content.trim() }
        });
        onSaved(editing.id);
      } else {
        const response = await create.mutateAsync({
          type,
          title: title.trim(),
          content: content.trim(),
          sourceMeetingId: sourceMeetingId.trim() || null
        });
        onSaved(response.id);
      }
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "저장하지 못했습니다.");
    }
  }

  return (
    <div className="fixed inset-0 z-[800] flex items-center justify-center bg-black/35 p-6" onClick={onClose}>
      <div
        className="flex max-h-full w-full max-w-[600px] flex-col overflow-hidden rounded-2xl border border-[var(--app-line)] bg-[var(--app-surface)] shadow-[var(--app-shadow-lg)]"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-center border-b border-[var(--app-line)] px-5 py-3.5">
          <h2 className="text-sm font-extrabold text-[var(--app-text-strong)]">
            {editing ? "지식 수정" : "지식 등록"}
          </h2>
          <button
            aria-label="닫기"
            className="ml-auto grid h-7 w-7 place-items-center rounded-md text-[var(--app-subtle)] hover:bg-[var(--app-surface-muted)] hover:text-[var(--app-text-strong)]"
            onClick={onClose}
            type="button"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          {!editing ? (
            <div className="mb-3.5">
              <label className="mb-1.5 block text-[11px] font-bold text-[var(--app-subtle)]">종류</label>
              <div className="flex flex-wrap gap-1.5">
                {TYPE_OPTIONS.map((option) => (
                  <button
                    className={`rounded-full border px-3 py-1 text-xs font-bold transition-colors ${
                      type === option.value
                        ? "border-[var(--app-accent-border)] bg-[var(--app-accent-soft)] text-[var(--app-accent-text)]"
                        : "border-[var(--app-line)] text-[var(--app-muted)] hover:bg-[var(--app-surface-soft)]"
                    }`}
                    key={option.value}
                    onClick={() => setType(option.value)}
                    type="button"
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          <div className="mb-3.5">
            <label className="mb-1.5 block text-[11px] font-bold text-[var(--app-subtle)]" htmlFor="knowledge-title">
              제목
            </label>
            <input
              autoFocus
              className="w-full rounded-lg border border-[var(--app-line)] bg-[var(--app-surface-soft)] px-3 py-2 text-sm text-[var(--app-text)] outline-none focus:border-[var(--app-accent-border)]"
              id="knowledge-title"
              onChange={(event) => setTitle(event.target.value)}
              placeholder="지식 제목"
              value={title}
            />
          </div>

          <div className="mb-3.5">
            <label className="mb-1.5 block text-[11px] font-bold text-[var(--app-subtle)]" htmlFor="knowledge-content">
              내용
            </label>
            <textarea
              className="min-h-[220px] w-full resize-y rounded-lg border border-[var(--app-line)] bg-[var(--app-surface-soft)] px-3 py-2 text-sm leading-relaxed text-[var(--app-text)] outline-none focus:border-[var(--app-accent-border)]"
              id="knowledge-content"
              onChange={(event) => setContent(event.target.value)}
              placeholder="프로젝트의 공식 지식으로 남길 내용을 작성합니다."
              value={content}
            />
          </div>

          {!editing ? (
            <div>
              <label className="mb-1.5 block text-[11px] font-bold text-[var(--app-subtle)]" htmlFor="knowledge-source">
                출처 회의 ID <span className="font-semibold text-[var(--app-subtle)]">(선택)</span>
              </label>
              <input
                className="w-full rounded-lg border border-[var(--app-line)] bg-[var(--app-surface-soft)] px-3 py-2 text-sm text-[var(--app-text)] outline-none focus:border-[var(--app-accent-border)]"
                id="knowledge-source"
                onChange={(event) => setSourceMeetingId(event.target.value)}
                placeholder="회의에서 파생된 지식이면 회의 ID를 입력"
                value={sourceMeetingId}
              />
            </div>
          ) : null}

          {error ? (
            <p className="mt-3 text-xs font-semibold text-[var(--app-danger-text)]">{error}</p>
          ) : null}
        </div>

        <div className="flex items-center gap-2 border-t border-[var(--app-line)] px-5 py-3">
          <span className="text-[11px] text-[var(--app-subtle)]">
            저장하면 임베딩이 다시 생성됩니다.
          </span>
          <button
            className="ml-auto rounded-lg border border-[var(--app-line)] px-3.5 py-2 text-xs font-bold text-[var(--app-muted)] hover:bg-[var(--app-surface-soft)]"
            onClick={onClose}
            type="button"
          >
            취소
          </button>
          <button
            className="rounded-lg bg-[var(--app-accent)] px-4 py-2 text-xs font-bold text-white hover:brightness-110 disabled:opacity-60"
            disabled={saving}
            onClick={() => void handleSubmit()}
            type="button"
          >
            {saving ? "저장 중…" : editing ? "저장" : "등록"}
          </button>
        </div>
      </div>
    </div>
  );
}
