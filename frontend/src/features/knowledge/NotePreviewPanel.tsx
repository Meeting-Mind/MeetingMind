import { X } from "lucide-react";
import React from "react";
import type { ProjectKnowledgeDetailResponse } from "../../types";
import { useKnowledgeDetailQuery } from "./hooks";
import {
  KNOWLEDGE_KIND_COLOR_VARS,
  KNOWLEDGE_KIND_LABELS,
  type GraphLinkVM,
  type GraphNodeVM
} from "./types";

interface NotePreviewPanelProps {
  spaceId: string;
  node: GraphNodeVM | null;
  links: GraphLinkVM[];
  canManage: boolean;
  onClose: () => void;
  onSelectNeighbor: (node: GraphNodeVM) => void;
  onEdit: (detail: ProjectKnowledgeDetailResponse) => void;
  onArchive: (detail: ProjectKnowledgeDetailResponse) => void;
}

function linkedNodes(node: GraphNodeVM, links: GraphLinkVM[]): GraphNodeVM[] {
  const neighbors: GraphNodeVM[] = [];
  for (const link of links) {
    const source = link.source as GraphNodeVM;
    const target = link.target as GraphNodeVM;
    if (typeof source !== "object" || typeof target !== "object") continue;
    if (source.id === node.id) neighbors.push(target);
    else if (target.id === node.id) neighbors.push(source);
  }
  return neighbors.sort((a, b) => b.connectionCount - a.connectionCount);
}

/** 노드 선택 시 우측 슬라이드 프리뷰. projectKnowledge는 본문, glossary는 정의를 보여준다. */
export function NotePreviewPanel({
  spaceId,
  node,
  links,
  canManage,
  onClose,
  onSelectNeighbor,
  onEdit,
  onArchive
}: NotePreviewPanelProps) {
  const detailQuery = useKnowledgeDetailQuery(spaceId, node);
  const editable = canManage && detailQuery.data != null;

  return (
    <div
      className={`absolute bottom-3.5 right-3.5 top-3.5 z-20 flex w-[330px] flex-col overflow-hidden rounded-xl border border-[var(--app-line)] bg-[var(--app-surface)] shadow-[var(--app-shadow)] transition-transform duration-200 ${node ? "translate-x-0" : "translate-x-[360px]"}`}
    >
      {node ? (
        <>
          <div className="relative border-b border-[var(--app-line)] px-4 pb-3 pt-3.5">
            <button
              aria-label="닫기"
              className="absolute right-2.5 top-2.5 grid h-6 w-6 place-items-center rounded-md text-[var(--app-subtle)] hover:bg-[var(--app-surface-muted)] hover:text-[var(--app-text-strong)]"
              onClick={onClose}
              type="button"
            >
              <X className="h-3.5 w-3.5" />
            </button>
            <span
              className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-[11px] font-bold"
              style={{
                color: `var(${KNOWLEDGE_KIND_COLOR_VARS[node.kind]})`,
                background: "var(--app-surface-muted)"
              }}
            >
              ● {KNOWLEDGE_KIND_LABELS[node.kind]}
            </span>
            <h3 className="mt-2 text-base font-extrabold leading-snug tracking-tight text-[var(--app-text-strong)]">
              {node.title}
            </h3>
            <div className="mt-1 text-[11px] font-semibold text-[var(--app-subtle)]">
              연결 {node.connectionCount}
              {node.orphan ? " · 단일 노드" : ""}
            </div>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-4 py-3">
            {detailQuery.isLoading ? (
              <p className="text-xs text-[var(--app-muted)]">본문을 불러오는 중…</p>
            ) : null}
            {detailQuery.data ? (
              <p className="mb-3 whitespace-pre-wrap text-xs leading-relaxed text-[var(--app-text)]">
                {detailQuery.data.content}
              </p>
            ) : null}
            {!detailQuery.data && node.description ? (
              <div className="mb-3">
                <div className="mb-1 text-[10.5px] font-extrabold uppercase tracking-wider text-[var(--app-subtle)]">
                  정의
                </div>
                <p className="whitespace-pre-wrap text-xs leading-relaxed text-[var(--app-text)]">
                  {node.description}
                </p>
              </div>
            ) : null}
            {detailQuery.isError ? (
              <p className="mb-3 text-xs text-[var(--app-danger-text)]">본문을 불러오지 못했습니다.</p>
            ) : null}
            <div className="mb-2 text-[10.5px] font-extrabold uppercase tracking-wider text-[var(--app-subtle)]">
              연결된 노트
            </div>
            {linkedNodes(node, links).slice(0, 12).map((neighbor) => (
              <button
                className="mb-1.5 w-full rounded-lg border border-[var(--app-line)] bg-[var(--app-surface)] px-2.5 py-2 text-left hover:border-[var(--app-accent-border)]"
                key={neighbor.id}
                onClick={() => onSelectNeighbor(neighbor)}
                type="button"
              >
                <div className="text-xs font-bold text-[var(--app-text-strong)]">{neighbor.title}</div>
                <div className="text-[11px] text-[var(--app-muted)]">
                  {KNOWLEDGE_KIND_LABELS[neighbor.kind]} · 연결 {neighbor.connectionCount}
                </div>
              </button>
            ))}
            {node.connectionCount === 0 ? (
              <p className="text-xs text-[var(--app-muted)]">아직 연결된 노트가 없습니다.</p>
            ) : null}
          </div>
          {editable ? (
            <div className="flex gap-1.5 border-t border-[var(--app-line)] px-4 py-3">
              <button
                className="flex-1 rounded-lg border border-[var(--app-line)] py-2 text-xs font-bold text-[var(--app-muted)] hover:bg-[var(--app-surface-soft)]"
                onClick={() => onArchive(detailQuery.data as ProjectKnowledgeDetailResponse)}
                type="button"
              >
                보관
              </button>
              <button
                className="flex-1 rounded-lg bg-[var(--app-accent)] py-2 text-xs font-bold text-white hover:brightness-110"
                onClick={() => onEdit(detailQuery.data as ProjectKnowledgeDetailResponse)}
                type="button"
              >
                수정
              </button>
            </div>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
