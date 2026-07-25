import { Group, Maximize2, Plus, RefreshCw, SlidersHorizontal, Sparkles } from "lucide-react";
import { CLUSTER_BASIS_HINTS, CLUSTER_BASIS_LABELS, type ClusterBasis } from "./clustering";
import React, { useCallback, useMemo, useRef, useState } from "react";
import { useOutletContext, useParams } from "react-router-dom";
import type { ProjectKnowledgeDetailResponse, SpaceDetail } from "../../types";
import { GraphCanvas, type GraphCanvasHandle } from "./GraphCanvas";
import { GraphSettingsPanel } from "./GraphSettingsPanel";
import { KnowledgeEditorDialog } from "./KnowledgeEditorDialog";
import { NotePreviewPanel } from "./NotePreviewPanel";
import { useKnowledgeGraphQuery, useKnowledgeMutations } from "./hooks";
import { useKnowledgeGraphStore } from "./store";
import type { GraphLinkVM, GraphNodeVM } from "./types";

/**
 * Knowledge 그래프 화면 (확정 시안 B · Graph-first).
 * 전역 그래프가 랜딩, 좌측 설정 패널, 노드 선택 시 우측 프리뷰.
 */
export function KnowledgeGraphPage() {
  const { spaceId = "" } = useParams();
  const outlet = useOutletContext<{ spaceDetail: SpaceDetail | null }>();
  const role = outlet?.spaceDetail?.role ?? "MEMBER";
  const canManage = role === "OWNER" || role === "ADMIN";

  const graphQuery = useKnowledgeGraphQuery(spaceId);
  const { archive, restore } = useKnowledgeMutations(spaceId);
  const canvasRef = useRef<GraphCanvasHandle>(null);
  const nodeCacheRef = useRef(new Map<string, GraphNodeVM>());
  // 설정은 기본으로 닫아 둔다. 그래프가 주인공이고 설정은 가끔 쓴다.
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<ProjectKnowledgeDetailResponse | null>(null);
  const [archived, setArchived] = useState<ProjectKnowledgeDetailResponse | null>(null);
  const [notice, setNotice] = useState("");

  const hiddenKinds = useKnowledgeGraphStore((state) => state.hiddenKinds);
  const showOrphans = useKnowledgeGraphStore((state) => state.showOrphans);
  const clustered = useKnowledgeGraphStore((state) => state.clustered);
  const setClustered = useKnowledgeGraphStore((state) => state.setClustered);
  const clusterBasis = useKnowledgeGraphStore((state) => state.clusterBasis);
  const setClusterBasis = useKnowledgeGraphStore((state) => state.setClusterBasis);
  const selectedId = useKnowledgeGraphStore((state) => state.selectedId);
  const setSelectedId = useKnowledgeGraphStore((state) => state.setSelectedId);

  const allNodes = useMemo(() => {
    // 필터가 바뀌어도 시뮬레이션 좌표를 유지하도록 노드 객체를 id 기준으로 재사용한다.
    const cache = nodeCacheRef.current;
    const nodes = (graphQuery.data?.nodes ?? []).map((node) => {
      const cached = cache.get(node.id);
      if (cached) {
        Object.assign(cached, node, {
          x: cached.x,
          y: cached.y,
          vx: cached.vx,
          vy: cached.vy
        });
        return cached;
      }
      cache.set(node.id, node);
      return node;
    });
    return nodes;
  }, [graphQuery.data]);

  const { nodes, links } = useMemo(() => {
    const visible = allNodes.filter(
      (node) => !hiddenKinds.has(node.kind) && (showOrphans || !node.orphan)
    );
    const visibleIds = new Set(visible.map((node) => node.id));
    const visibleLinks: GraphLinkVM[] = (graphQuery.data?.links ?? [])
      .filter((link) => {
        const sourceId = typeof link.source === "object" ? link.source.id : String(link.source);
        const targetId = typeof link.target === "object" ? link.target.id : String(link.target);
        return visibleIds.has(sourceId) && visibleIds.has(targetId);
      })
      .map((link) => ({ ...link }));
    return { nodes: visible, links: visibleLinks };
  }, [allNodes, graphQuery.data, hiddenKinds, showOrphans]);

  const selectedNode = useMemo(
    () => nodes.find((node) => node.id === selectedId) ?? null,
    [nodes, selectedId]
  );

  const handleSelect = useCallback(
    (node: GraphNodeVM | null) => setSelectedId(node?.id ?? null),
    [setSelectedId]
  );

  async function handleArchive(detail: ProjectKnowledgeDetailResponse) {
    try {
      await archive.mutateAsync(detail.id);
      setSelectedId(null);
      setArchived(detail);
      setNotice(`"${detail.title}"을(를) 보관했습니다.`);
    } catch (cause) {
      setNotice(cause instanceof Error ? cause.message : "보관하지 못했습니다.");
    }
  }

  async function handleRestore() {
    if (!archived) return;
    try {
      await restore.mutateAsync(archived.id);
      setNotice(`"${archived.title}"을(를) 복구했습니다.`);
      setArchived(null);
    } catch (cause) {
      setNotice(cause instanceof Error ? cause.message : "복구하지 못했습니다.");
    }
  }

  const insets = {
    left: settingsOpen && nodes.length > 0 ? 276 : 0,
    right: selectedNode ? 358 : 0
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col px-6 py-5">
      <div className="mb-3 flex items-center gap-3">
        <h1 className="text-lg font-extrabold tracking-tight text-[var(--app-text-strong)]">
          지식 그래프
        </h1>
        <span className="text-xs font-semibold text-[var(--app-subtle)]">
          노드 {nodes.length} · 링크 {links.length}
          {graphQuery.data?.partial ? " · 일부만 표시됨" : ""}
        </span>
        <div className="ml-auto flex items-center gap-1.5">
          <button
            aria-pressed={clustered}
            className={`inline-flex items-center gap-1.5 rounded-lg border px-3 py-1.5 text-xs font-bold ${
              clustered
                ? "border-[var(--app-accent)] bg-[var(--app-accent-soft)] text-[var(--app-accent-text)]"
                : "border-[var(--app-line)] text-[var(--app-muted)] hover:bg-[var(--app-surface-soft)]"
            }`}
            onClick={() => setClustered(!clustered)}
            title="연결된 노드끼리 덩어리로 모읍니다"
            type="button"
          >
            <Group className="h-3.5 w-3.5" /> {clustered ? "묶음 해제" : "묶어보기"}
          </button>
          {/* 기준은 묶은 뒤에만 보여준다. 묶지 않았는데 기준만 있으면 무엇을 고르는지 알 수 없다. */}
          {clustered ? (
            <div className="flex overflow-hidden rounded-lg border border-[var(--app-line)]">
              {(Object.keys(CLUSTER_BASIS_LABELS) as ClusterBasis[]).map((basis) => (
                <button
                  aria-pressed={clusterBasis === basis}
                  className={`px-2.5 py-1.5 text-xs font-bold ${
                    clusterBasis === basis
                      ? "bg-[var(--app-accent)] text-white"
                      : "text-[var(--app-muted)] hover:bg-[var(--app-surface-soft)]"
                  } ${basis === "link" ? "" : "border-l border-[var(--app-line)]"}`}
                  key={basis}
                  onClick={() => setClusterBasis(basis)}
                  title={CLUSTER_BASIS_HINTS[basis]}
                  type="button"
                >
                  {CLUSTER_BASIS_LABELS[basis]}
                </button>
              ))}
            </div>
          ) : null}
          <button
            className="inline-flex items-center gap-1.5 rounded-lg border border-[var(--app-line)] px-3 py-1.5 text-xs font-bold text-[var(--app-muted)] hover:bg-[var(--app-surface-soft)]"
            disabled={clustered}
            onClick={() => canvasRef.current?.reheat()}
            title={clustered ? "묶음을 해제한 뒤 쓸 수 있습니다" : undefined}
            type="button"
          >
            <Sparkles className="h-3.5 w-3.5" /> 재배치
          </button>
          <button
            className="inline-flex items-center gap-1.5 rounded-lg border border-[var(--app-line)] px-3 py-1.5 text-xs font-bold text-[var(--app-muted)] hover:bg-[var(--app-surface-soft)]"
            onClick={() => canvasRef.current?.fitToView()}
            type="button"
          >
            <Maximize2 className="h-3.5 w-3.5" /> 화면 맞춤
          </button>
          <button
            className="inline-flex items-center gap-1.5 rounded-lg border border-[var(--app-line)] px-3 py-1.5 text-xs font-bold text-[var(--app-muted)] hover:bg-[var(--app-surface-soft)]"
            disabled={graphQuery.isFetching}
            onClick={() => void graphQuery.refetch()}
            type="button"
          >
            <RefreshCw className={`h-3.5 w-3.5 ${graphQuery.isFetching ? "animate-spin" : ""}`} /> 새로고침
          </button>
          {canManage ? (
            <button
              className="inline-flex items-center gap-1.5 rounded-lg bg-[var(--app-accent)] px-3 py-1.5 text-xs font-bold text-white hover:brightness-110"
              onClick={() => {
                setEditing(null);
                setEditorOpen(true);
              }}
              type="button"
            >
              <Plus className="h-3.5 w-3.5" /> 지식 등록
            </button>
          ) : null}
        </div>
      </div>

      <div className="relative min-h-0 flex-1 overflow-hidden rounded-2xl border border-[var(--app-line)] bg-[var(--app-canvas)]">
        {graphQuery.isLoading ? (
          <div className="grid h-full place-items-center text-sm text-[var(--app-muted)]">
            그래프를 불러오는 중…
          </div>
        ) : graphQuery.isError ? (
          <div className="grid h-full place-items-center">
            <div className="text-center">
              <p className="mb-2 text-sm font-bold text-[var(--app-text-strong)]">
                그래프를 불러오지 못했습니다
              </p>
              <button
                className="rounded-lg border border-[var(--app-line)] px-3 py-1.5 text-xs font-bold text-[var(--app-muted)] hover:bg-[var(--app-surface-soft)]"
                onClick={() => void graphQuery.refetch()}
                type="button"
              >
                다시 시도
              </button>
            </div>
          </div>
        ) : nodes.length === 0 ? (
          <div className="grid h-full place-items-center text-sm text-[var(--app-muted)]">
            표시할 지식 노드가 없습니다. 회의록과 지식을 등록하면 그래프가 자라납니다.
          </div>
        ) : (
          <>
            <GraphCanvas insets={insets} links={links} nodes={nodes} onSelect={handleSelect} ref={canvasRef} />
            {settingsOpen ? (
              <div className="pointer-events-none absolute inset-y-3.5 left-3.5 z-10 flex items-start">
                <div className="pointer-events-auto max-h-full">
                  <GraphSettingsPanel allNodes={allNodes} onClose={() => setSettingsOpen(false)} />
                </div>
              </div>
            ) : (
              <button
                aria-label="그래프 설정 열기"
                className="absolute left-3.5 top-3.5 z-10 inline-flex items-center gap-1.5 rounded-xl border border-[var(--app-line)] bg-[var(--app-surface)] px-3 py-2 text-xs font-bold text-[var(--app-muted)] shadow-[var(--app-shadow-sm)] hover:text-[var(--app-text-strong)]"
                onClick={() => setSettingsOpen(true)}
                type="button"
              >
                <SlidersHorizontal className="h-3.5 w-3.5" /> 그래프 설정
              </button>
            )}
            <NotePreviewPanel
              canManage={canManage}
              links={links}
              node={selectedNode}
              onArchive={(detail) => void handleArchive(detail)}
              onClose={() => setSelectedId(null)}
              onEdit={(detail) => {
                setEditing(detail);
                setEditorOpen(true);
              }}
              onSelectNeighbor={(node) => setSelectedId(node.id)}
              spaceId={spaceId}
            />
          </>
        )}

        {notice ? (
          <div className="absolute bottom-3.5 left-1/2 z-30 flex -translate-x-1/2 items-center gap-3 rounded-xl border border-[var(--app-line)] bg-[var(--app-surface)] px-4 py-2.5 text-xs font-semibold text-[var(--app-text)] shadow-[var(--app-shadow)]">
            {notice}
            {archived ? (
              <button
                className="font-bold text-[var(--app-accent-text)] hover:underline"
                onClick={() => void handleRestore()}
                type="button"
              >
                실행 취소
              </button>
            ) : null}
            <button
              aria-label="알림 닫기"
              className="text-[var(--app-subtle)] hover:text-[var(--app-text-strong)]"
              onClick={() => {
                setNotice("");
                setArchived(null);
              }}
              type="button"
            >
              ✕
            </button>
          </div>
        ) : null}
      </div>

      {editorOpen ? (
        <KnowledgeEditorDialog
          editing={editing}
          onClose={() => setEditorOpen(false)}
          onSaved={(knowledgeId) => {
            setNotice(editing ? "지식을 저장했습니다." : "지식을 등록했습니다.");
            setArchived(null);
            if (!editing) setSelectedId(`knowledge:${knowledgeId}`);
          }}
          spaceId={spaceId}
        />
      ) : null}
    </div>
  );
}
