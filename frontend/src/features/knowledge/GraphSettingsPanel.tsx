import { Search, X } from "lucide-react";
import React from "react";
import { DEFAULT_DISPLAY, DEFAULT_FORCES, useKnowledgeGraphStore } from "./store";
import {
  KNOWLEDGE_KIND_COLOR_VARS,
  KNOWLEDGE_KIND_LABELS,
  type GraphNodeVM,
  type KnowledgeKind
} from "./types";

interface GraphSettingsPanelProps {
  allNodes: GraphNodeVM[];
  onClose?: () => void;
}

function SliderControl({
  label,
  min,
  max,
  step,
  value,
  format,
  onChange
}: {
  label: string;
  min: number;
  max: number;
  step: number;
  value: number;
  format?: (value: number) => string;
  onChange: (value: number) => void;
}) {
  return (
    <div className="mb-2.5 last:mb-0">
      <label className="mb-1 flex items-center justify-between text-[11px] font-bold text-[var(--app-subtle)]">
        {label}
        <output className="text-[var(--app-accent-text)]">{format ? format(value) : value}</output>
      </label>
      <input
        className="h-1 w-full accent-[var(--app-accent)]"
        max={max}
        min={min}
        onChange={(event) => onChange(Number(event.target.value))}
        step={step}
        type="range"
        value={value}
      />
    </div>
  );
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <div className="border-b border-[var(--app-line)] px-3.5 py-2.5 text-[11px] font-extrabold uppercase tracking-wider text-[var(--app-subtle)]">
      {children}
    </div>
  );
}

/** 옵시디언 그래프 설정 패널: 필터 / 그룹 / 표시 / 힘 */
export function GraphSettingsPanel({ allNodes, onClose }: GraphSettingsPanelProps) {
  const hiddenKinds = useKnowledgeGraphStore((state) => state.hiddenKinds);
  const showOrphans = useKnowledgeGraphStore((state) => state.showOrphans);
  const search = useKnowledgeGraphStore((state) => state.search);
  const forces = useKnowledgeGraphStore((state) => state.forces);
  const display = useKnowledgeGraphStore((state) => state.display);
  const toggleKind = useKnowledgeGraphStore((state) => state.toggleKind);
  const setShowOrphans = useKnowledgeGraphStore((state) => state.setShowOrphans);
  const setSearch = useKnowledgeGraphStore((state) => state.setSearch);
  const setForces = useKnowledgeGraphStore((state) => state.setForces);
  const setDisplay = useKnowledgeGraphStore((state) => state.setDisplay);

  const counts = new Map<KnowledgeKind, number>();
  for (const node of allNodes) {
    counts.set(node.kind, (counts.get(node.kind) ?? 0) + 1);
  }
  const kinds = Array.from(counts.keys()).sort(
    (a, b) => (counts.get(b) ?? 0) - (counts.get(a) ?? 0)
  );
  const orphanCount = allNodes.filter((node) => node.orphan).length;

  return (
    <div className="flex max-h-full w-[248px] flex-col overflow-hidden rounded-xl border border-[var(--app-line)] bg-[var(--app-surface)] shadow-[var(--app-shadow)]">
      <div className="flex items-center border-b border-[var(--app-line)] py-2.5 pl-3.5 pr-2 text-[11px] font-extrabold uppercase tracking-wider text-[var(--app-subtle)]">
        그래프 설정
        {onClose ? (
          <button
            aria-label="그래프 설정 닫기"
            className="ml-auto grid h-6 w-6 place-items-center rounded-md text-[var(--app-subtle)] hover:bg-[var(--app-surface-muted)] hover:text-[var(--app-text-strong)]"
            onClick={onClose}
            type="button"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        ) : null}
      </div>
      <div className="overflow-y-auto">
        <SectionTitle>필터</SectionTitle>
        <div className="border-b border-[var(--app-line)] p-3">
          <div className="relative mb-2.5">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3 w-3 -translate-y-1/2 text-[var(--app-subtle)]" />
            <input
              className="w-full rounded-lg border border-[var(--app-line)] bg-[var(--app-surface-soft)] py-1.5 pl-7 pr-2.5 text-xs text-[var(--app-text)] outline-none focus:border-[var(--app-accent-border)]"
              onChange={(event) => setSearch(event.target.value)}
              placeholder="노트 검색…"
              type="text"
              value={search}
            />
          </div>
          <button
            className={`flex w-full items-center gap-2 rounded-md px-1.5 py-1 text-xs hover:bg-[var(--app-surface-muted)] ${showOrphans ? "" : "opacity-40"}`}
            onClick={() => setShowOrphans(!showOrphans)}
            type="button"
          >
            <span className="h-2 w-2 rounded-full bg-[var(--app-subtle)]" />
            <span className="text-[var(--app-text)]">단일 노드 표시</span>
            <span className="ml-auto text-[10.5px] font-bold text-[var(--app-subtle)]">{orphanCount}</span>
          </button>
        </div>

        <SectionTitle>그룹</SectionTitle>
        <div className="border-b border-[var(--app-line)] px-3 py-2">
          {kinds.map((kind) => (
            <button
              className={`flex w-full items-center gap-2 rounded-md px-1.5 py-1 text-xs hover:bg-[var(--app-surface-muted)] ${hiddenKinds.has(kind) ? "opacity-40" : ""}`}
              key={kind}
              onClick={() => toggleKind(kind)}
              type="button"
            >
              <span
                className="h-2 w-2 rounded-full"
                style={{ background: `var(${KNOWLEDGE_KIND_COLOR_VARS[kind]})` }}
              />
              <span className="text-[var(--app-text)]">{KNOWLEDGE_KIND_LABELS[kind]}</span>
              <span className="ml-auto text-[10.5px] font-bold text-[var(--app-subtle)]">
                {counts.get(kind)}
              </span>
            </button>
          ))}
        </div>

        <SectionTitle>표시</SectionTitle>
        <div className="border-b border-[var(--app-line)] px-3 py-2.5">
          <SliderControl
            format={(value) => value.toFixed(2)}
            label="라벨 표시 임계값"
            max={2.4}
            min={0.4}
            onChange={(value) => setDisplay({ labelThreshold: value })}
            step={0.05}
            value={display.labelThreshold}
          />
          <SliderControl
            format={(value) => value.toFixed(2)}
            label="링크 불투명도"
            max={1}
            min={0.05}
            onChange={(value) => setDisplay({ linkOpacity: value })}
            step={0.05}
            value={display.linkOpacity}
          />
          <SliderControl
            format={(value) => value.toFixed(1)}
            label="노드 크기"
            max={2}
            min={0.5}
            onChange={(value) => setDisplay({ nodeScale: value })}
            step={0.1}
            value={display.nodeScale}
          />
        </div>

        <SectionTitle>힘 (Forces)</SectionTitle>
        <div className="px-3 py-2.5">
          <SliderControl
            format={(value) => value.toFixed(2)}
            label="중심 인력"
            max={0.3}
            min={0}
            onChange={(value) => setForces({ center: value })}
            step={0.01}
            value={forces.center}
          />
          <SliderControl
            label="반발력"
            max={500}
            min={20}
            onChange={(value) => setForces({ repel: value })}
            step={10}
            value={forces.repel}
          />
          <SliderControl
            format={(value) => value.toFixed(2)}
            label="링크 힘"
            max={1.5}
            min={0}
            onChange={(value) => setForces({ linkStrength: value })}
            step={0.05}
            value={forces.linkStrength}
          />
          <SliderControl
            label="링크 거리"
            max={180}
            min={20}
            onChange={(value) => setForces({ linkDistance: value })}
            step={4}
            value={forces.linkDistance}
          />
          <button
            className="mt-1 w-full rounded-lg border border-[var(--app-line)] py-1.5 text-[11.5px] font-bold text-[var(--app-muted)] hover:bg-[var(--app-surface-soft)]"
            onClick={() => {
              setForces(DEFAULT_FORCES);
              setDisplay(DEFAULT_DISPLAY);
            }}
            type="button"
          >
            기본값 복원
          </button>
        </div>
      </div>
    </div>
  );
}
