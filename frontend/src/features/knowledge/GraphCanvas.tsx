import {
  forceCollide,
  forceLink,
  forceManyBody,
  forceSimulation,
  forceX,
  forceY,
  type ForceLink,
  type Simulation
} from "d3-force";
import React, { forwardRef, useEffect, useImperativeHandle, useMemo, useRef } from "react";
import { INTRO_BURST_MS, burstPosition, captureTargets, nodeProgress, type BurstTarget } from "./introBurst";
import { depthOpacity, layerZ, perspectiveScale } from "./depth";
import { useKnowledgeGraphStore } from "./store";
import { KNOWLEDGE_KIND_COLOR_VARS, type GraphLinkVM, type GraphNodeVM } from "./types";

export interface GraphCanvasHandle {
  fitToView: () => void;
  reheat: () => void;
}

interface GraphCanvasProps {
  nodes: GraphNodeVM[];
  links: GraphLinkVM[];
  onSelect: (node: GraphNodeVM | null) => void;
  /** 플로팅 패널이 가리는 영역(px). fitToView가 보이는 영역 기준으로 중앙을 잡도록 한다. */
  insets?: { left: number; right: number };
}

interface ViewTransform {
  x: number;
  y: number;
  k: number;
}

interface ThemePalette {
  bg: string;
  line: string;
  accent: string;
  text: string;
  muted: string;
  dark: boolean;
  kind: Record<string, string>;
}

function readPalette(): ThemePalette {
  const style = getComputedStyle(document.documentElement);
  const read = (name: string) => style.getPropertyValue(name).trim();
  const kind: Record<string, string> = {};
  for (const [key, cssVar] of Object.entries(KNOWLEDGE_KIND_COLOR_VARS)) {
    kind[key] = read(cssVar) || "#64748b";
  }
  return {
    bg: read("--app-canvas") || "#edf2f7",
    line: read("--app-line-strong") || "#b8c7d9",
    accent: read("--app-accent") || "#4338ca",
    text: read("--app-text-strong") || "#0f172a",
    muted: read("--app-muted") || "#52637a",
    dark: document.documentElement.dataset.theme === "dark",
    kind
  };
}

/** hex(#rrggbb) + alpha(0~1) → rgba 문자열. 토큰이 항상 hex라는 전제를 두지 않도록 방어. */
function withAlpha(color: string, alpha: number): string {
  if (/^#[0-9a-fA-F]{6}$/.test(color)) {
    const r = parseInt(color.slice(1, 3), 16);
    const g = parseInt(color.slice(3, 5), 16);
    const b = parseInt(color.slice(5, 7), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
  }
  return color;
}

const MIN_ZOOM = 0.15;
const MAX_ZOOM = 6;

/**
 * d3-force 실시간 시뮬레이션 + canvas 렌더링 그래프.
 * 노드는 소프트 글로우 스타일(확정 시안 B), 색·배경은 전부 디자인 토큰에서 읽는다.
 * pan/zoom/노드 드래그/hover는 PointerEvent로 직접 처리해 d3 DOM 의존성을 피한다.
 */
export const GraphCanvas = forwardRef<GraphCanvasHandle, GraphCanvasProps>(
  function GraphCanvas({ nodes, links, onSelect, insets }, ref) {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const simRef = useRef<Simulation<GraphNodeVM, GraphLinkVM> | null>(null);
    const transformRef = useRef<ViewTransform>({ x: 0, y: 0, k: 1 });
    const paletteRef = useRef<ThemePalette | null>(null);
    const hoverRef = useRef<GraphNodeVM | null>(null);
    const dragRef = useRef<GraphNodeVM | null>(null);
    const panRef = useRef<{ startX: number; startY: number; originX: number; originY: number } | null>(null);
    const sizeRef = useRef({ width: 0, height: 0, dpr: 1 });
    const dataRef = useRef({ nodes, links });
    const adjacencyRef = useRef(new Map<string, Set<string>>());
    const movedRef = useRef(false);
    const fittedRef = useRef(false);
    // 진입 모션 상태. 시작 시각이 null이면 모션이 끝났거나 아직 준비되지 않은 것이다.
    const burstRef = useRef<{ startedAt: number; targets: BurstTarget[] } | null>(null);
    const insetsRef = useRef({ left: 0, right: 0 });
    insetsRef.current = insets ?? { left: 0, right: 0 };

    const forces = useKnowledgeGraphStore((state) => state.forces);
    const selectedId = useKnowledgeGraphStore((state) => state.selectedId);
    const selectedIdRef = useRef<string | null>(selectedId);
    selectedIdRef.current = selectedId;

    const adjacency = useMemo(() => {
      const map = new Map<string, Set<string>>();
      for (const link of links) {
        const sourceId = typeof link.source === "object" ? link.source.id : String(link.source);
        const targetId = typeof link.target === "object" ? link.target.id : String(link.target);
        if (!map.has(sourceId)) map.set(sourceId, new Set());
        if (!map.has(targetId)) map.set(targetId, new Set());
        map.get(sourceId)?.add(targetId);
        map.get(targetId)?.add(sourceId);
      }
      return map;
    }, [links]);
    adjacencyRef.current = adjacency;

    /* --- simulation lifecycle --- */
    useEffect(() => {
      dataRef.current = { nodes, links };
      let simulation = simRef.current;
      if (!simulation) {
        simulation = forceSimulation<GraphNodeVM>()
          .force("link", forceLink<GraphNodeVM, GraphLinkVM>().id((node) => node.id))
          .force("charge", forceManyBody<GraphNodeVM>().theta(0.9).distanceMax(420))
          .force("x", forceX<GraphNodeVM>(0))
          .force("y", forceY<GraphNodeVM>(0))
          .force("collide", forceCollide<GraphNodeVM>((node) => node.radius + 3).iterations(2))
          .velocityDecay(0.32)
          .alphaDecay(0.012)
          .alphaMin(0.003);
        simRef.current = simulation;
      }
      simulation.nodes(nodes);
      (simulation.force("link") as ForceLink<GraphNodeVM, GraphLinkVM>).links(links);
      applyForces();

      // 진입 모션: 최종 배치를 먼저 계산해 두고 중앙에서 그 자리까지 직접 보간한다.
      // 시뮬레이션을 중앙에서 그냥 시작하면 힘이 서서히 밀어내 흐물흐물하게 퍼진다.
      // 움직임을 줄이도록 설정한 사용자에게는 모션 없이 최종 배치를 바로 보여준다.
      const reduceMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false;
      if (nodes.length > 0 && !reduceMotion) {
        simulation.alpha(1).stop();
        // 충분히 수렴할 만큼만 미리 돌린다. 화면에는 그리지 않는다.
        simulation.tick(220);
        burstRef.current = { startedAt: performance.now(), targets: captureTargets(nodes) };
      } else {
        burstRef.current = null;
      }

      simulation.alpha(0.9).restart();
      return undefined;
    }, [nodes, links]);

    useEffect(() => () => {
      simRef.current?.stop();
      simRef.current = null;
    }, []);

    function applyForces() {
      const simulation = simRef.current;
      if (!simulation) return;
      const { center, repel, linkStrength, linkDistance } = useKnowledgeGraphStore.getState().forces;
      (simulation.force("charge") as ReturnType<typeof forceManyBody<GraphNodeVM>>).strength(-repel);
      (simulation.force("link") as ForceLink<GraphNodeVM, GraphLinkVM>)
        .distance((link) => (link.explicit ? linkDistance : linkDistance * 1.6))
        .strength(linkStrength);
      (simulation.force("x") as ReturnType<typeof forceX<GraphNodeVM>>).strength(center);
      (simulation.force("y") as ReturnType<typeof forceY<GraphNodeVM>>).strength(center);
    }

    useEffect(() => {
      applyForces();
      simRef.current?.alpha(0.4).restart();
    }, [forces]);

    /* --- imperative API --- */
    function fitToView() {
      const { nodes: current } = dataRef.current;
      const { width, height } = sizeRef.current;
      if (current.length === 0 || width === 0) return;
      const { left, right } = insetsRef.current;
      const visibleWidth = Math.max(120, width - left - right);
      const xs = current.map((node) => node.x ?? 0);
      const ys = current.map((node) => node.y ?? 0);
      const minX = Math.min(...xs);
      const maxX = Math.max(...xs);
      const minY = Math.min(...ys);
      const maxY = Math.max(...ys);
      const k = Math.min(
        2,
        0.82 / Math.max((maxX - minX) / visibleWidth, (maxY - minY) / height, 0.01)
      );
      transformRef.current = {
        k,
        x: left + visibleWidth / 2 - ((minX + maxX) / 2) * k,
        y: height / 2 - ((minY + maxY) / 2) * k
      };
    }

    useImperativeHandle(ref, () => ({
      fitToView,
      reheat: () => simRef.current?.alpha(1).restart()
    }));

    /* --- theme observation --- */
    useEffect(() => {
      paletteRef.current = readPalette();
      const observer = new MutationObserver(() => {
        paletteRef.current = readPalette();
      });
      observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
      return () => observer.disconnect();
    }, []);

    /* --- resize --- */
    useEffect(() => {
      const canvas = canvasRef.current;
      if (!canvas || !canvas.parentElement) return;
      const parent = canvas.parentElement;
      const applySize = () => {
        const rect = parent.getBoundingClientRect();
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        sizeRef.current = { width: rect.width, height: rect.height, dpr };
        canvas.width = Math.max(1, Math.round(rect.width * dpr));
        canvas.height = Math.max(1, Math.round(rect.height * dpr));
        canvas.style.width = `${rect.width}px`;
        canvas.style.height = `${rect.height}px`;
      };
      applySize();
      const observer = new ResizeObserver(applySize);
      observer.observe(parent);
      return () => observer.disconnect();
    }, []);

    /* --- pointer interaction --- */
    function pickNode(clientX: number, clientY: number): GraphNodeVM | null {
      const canvas = canvasRef.current;
      if (!canvas) return null;
      const rect = canvas.getBoundingClientRect();
      const { x, y, k } = transformRef.current;
      const worldX = (clientX - rect.left - x) / k;
      const worldY = (clientY - rect.top - y) / k;
      let best: GraphNodeVM | null = null;
      let bestDistance = Infinity;
      const { nodeScale } = useKnowledgeGraphStore.getState().display;
      // 그릴 때와 같은 원근을 적용한다. 안 하면 보이는 자리와 눌리는 자리가 달라진다.
      for (const node of dataRef.current.nodes) {
        const depth = perspectiveScale(layerZ(node.kind));
        const dx = (node.x ?? 0) * depth - worldX;
        const dy = (node.y ?? 0) * depth - worldY;
        const distance = dx * dx + dy * dy;
        const hitRadius = (node.radius * nodeScale * depth + 6) / Math.min(k, 1);
        if (distance < hitRadius * hitRadius && distance < bestDistance) {
          best = node;
          bestDistance = distance;
        }
      }
      return best;
    }

    useEffect(() => {
      const canvas = canvasRef.current;
      if (!canvas) return;

      const handlePointerDown = (event: PointerEvent) => {
        canvas.setPointerCapture(event.pointerId);
        movedRef.current = false;
        const node = pickNode(event.clientX, event.clientY);
        if (node) {
          dragRef.current = node;
          node.fx = node.x;
          node.fy = node.y;
          simRef.current?.alphaTarget(0.25).restart();
        } else {
          const { x, y } = transformRef.current;
          panRef.current = { startX: event.clientX, startY: event.clientY, originX: x, originY: y };
        }
      };

      const handlePointerMove = (event: PointerEvent) => {
        if (dragRef.current) {
          movedRef.current = true;
          const rect = canvas.getBoundingClientRect();
          const { x, y, k } = transformRef.current;
          dragRef.current.fx = (event.clientX - rect.left - x) / k;
          dragRef.current.fy = (event.clientY - rect.top - y) / k;
          return;
        }
        if (panRef.current) {
          const pan = panRef.current;
          if (Math.abs(event.clientX - pan.startX) + Math.abs(event.clientY - pan.startY) > 3) {
            movedRef.current = true;
          }
          transformRef.current.x = pan.originX + (event.clientX - pan.startX);
          transformRef.current.y = pan.originY + (event.clientY - pan.startY);
          return;
        }
        hoverRef.current = pickNode(event.clientX, event.clientY);
        canvas.style.cursor = hoverRef.current ? "pointer" : "default";
      };

      const handlePointerUp = (event: PointerEvent) => {
        if (dragRef.current) {
          dragRef.current.fx = null;
          dragRef.current.fy = null;
          dragRef.current = null;
          simRef.current?.alphaTarget(0);
        }
        panRef.current = null;
        if (!movedRef.current) {
          onSelect(pickNode(event.clientX, event.clientY));
        }
      };

      const handleWheel = (event: WheelEvent) => {
        event.preventDefault();
        const rect = canvas.getBoundingClientRect();
        const pointerX = event.clientX - rect.left;
        const pointerY = event.clientY - rect.top;
        const { x, y, k } = transformRef.current;
        const nextK = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, k * (event.deltaY > 0 ? 0.92 : 1.08)));
        const scale = nextK / k;
        transformRef.current = {
          k: nextK,
          x: pointerX - (pointerX - x) * scale,
          y: pointerY - (pointerY - y) * scale
        };
      };

      const handlePointerLeave = () => {
        hoverRef.current = null;
      };

      canvas.addEventListener("pointerdown", handlePointerDown);
      canvas.addEventListener("pointermove", handlePointerMove);
      canvas.addEventListener("pointerup", handlePointerUp);
      canvas.addEventListener("pointerleave", handlePointerLeave);
      canvas.addEventListener("wheel", handleWheel, { passive: false });
      return () => {
        canvas.removeEventListener("pointerdown", handlePointerDown);
        canvas.removeEventListener("pointermove", handlePointerMove);
        canvas.removeEventListener("pointerup", handlePointerUp);
        canvas.removeEventListener("pointerleave", handlePointerLeave);
        canvas.removeEventListener("wheel", handleWheel);
      };
    }, [onSelect]);

    /* --- render loop --- */
    useEffect(() => {
      const canvas = canvasRef.current;
      const context = canvas?.getContext("2d");
      if (!canvas || !context) return;
      let frameId = 0;

      const draw = () => {
        frameId = requestAnimationFrame(draw);
        const palette = paletteRef.current;
        if (!palette) return;
        const { width, height, dpr } = sizeRef.current;
        if (width === 0) return;

        // 데이터가 처음 그려질 때 한 번 화면 맞춤.
        if (!fittedRef.current && dataRef.current.nodes.length > 0) {
          fittedRef.current = true;
          // 진입 모션이 끝난 뒤에 맞춘다. 모션 중에 맞추면 중앙에 뭉친 상태를
          // 기준으로 확대해 버려서 퍼진 다음 화면 밖으로 나간다.
          window.setTimeout(fitToView, INTRO_BURST_MS + 120);
        }

        // 진입 모션 중에는 시뮬레이션을 멈추고 좌표를 직접 덮어쓴다. 둘이 동시에
        // 위치를 건드리면 힘과 보간이 싸워 노드가 떨린다.
        const burst = burstRef.current;
        if (burst) {
          const ratio = (performance.now() - burst.startedAt) / INTRO_BURST_MS;
          const graphNodes = dataRef.current.nodes;
          if (ratio >= 1) {
            for (let index = 0; index < graphNodes.length; index += 1) {
              const target = burst.targets[index];
              if (target) {
                graphNodes[index].x = target.x;
                graphNodes[index].y = target.y;
              }
            }
            burstRef.current = null;
            // 모션이 끝난 뒤 시뮬레이션이 이어받는다. 알파를 낮게 줘서 자리가 튀지 않게 한다.
            simRef.current?.alpha(0.12).restart();
          } else {
            // 좌표는 원근 이전 값이다. 그리기 단계에서 층 배율이 곱해지므로
            // 중앙에서 퍼질 때 앞 층은 더 멀리, 뒤 층은 덜 퍼져 깊이가 함께 열린다.
            simRef.current?.stop();
            for (let index = 0; index < graphNodes.length; index += 1) {
              const target = burst.targets[index];
              if (!target) continue;
              const moved = burstPosition(target, nodeProgress(ratio, index, graphNodes.length));
              graphNodes[index].x = moved.x;
              graphNodes[index].y = moved.y;
              // 보간이 끝난 뒤 잔여 속도로 튀지 않도록 매 프레임 속도를 지운다.
              graphNodes[index].vx = 0;
              graphNodes[index].vy = 0;
            }
          }
        }

        const { display, search } = useKnowledgeGraphStore.getState();
        const { x, y, k } = transformRef.current;
        const query = search.trim().toLowerCase();
        const searching = query.length > 0;
        const matchesQuery = (node: GraphNodeVM) =>
          !searching || node.title.toLowerCase().includes(query);

        const selected = selectedIdRef.current
          ? dataRef.current.nodes.find((node) => node.id === selectedIdRef.current) ?? null
          : null;
        const anchor = hoverRef.current ?? selected;
        const neighborhood = anchor
          ? new Set<string>([anchor.id, ...(adjacencyRef.current.get(anchor.id) ?? [])])
          : null;
        const isDimmed = (node: GraphNodeVM) =>
          (neighborhood != null && !neighborhood.has(node.id)) || (searching && !matchesQuery(node));

        context.setTransform(dpr, 0, 0, dpr, 0, 0);
        context.fillStyle = palette.bg;
        context.fillRect(0, 0, width, height);
        context.translate(x, y);
        context.scale(k, k);
        context.lineCap = "round";

        /* links */
        for (const link of dataRef.current.links) {
          const source = link.source as GraphNodeVM;
          const target = link.target as GraphNodeVM;
          if (typeof source !== "object" || typeof target !== "object") continue;
          const hot = neighborhood != null
            && neighborhood.has(source.id) && neighborhood.has(target.id)
            && anchor != null && (source.id === anchor.id || target.id === anchor.id);
          const dimmed = (neighborhood != null && !hot)
            || (searching && (!matchesQuery(source) || !matchesQuery(target)));
          context.globalAlpha = hot ? 0.9 : dimmed ? display.linkOpacity * 0.12
            : display.linkOpacity * (link.explicit ? 1 : 0.55);
          context.strokeStyle = hot ? palette.accent : palette.line;
          context.lineWidth = (hot ? 1.7 : link.explicit ? 1.1 : 0.8) / k;
          if (hot && palette.dark) {
            context.shadowColor = palette.accent;
            context.shadowBlur = 7;
          }
          context.setLineDash(link.explicit ? [] : [4 / k, 4 / k]);
          context.beginPath();
          // 노드와 같은 원근을 적용한다. 안 하면 선이 노드에서 떨어져 그려진다.
          const sourceDepth = perspectiveScale(layerZ(source.kind));
          const targetDepth = perspectiveScale(layerZ(target.kind));
          context.moveTo((source.x ?? 0) * sourceDepth, (source.y ?? 0) * sourceDepth);
          context.lineTo((target.x ?? 0) * targetDepth, (target.y ?? 0) * targetDepth);
          context.stroke();
          context.shadowBlur = 0;
        }
        context.setLineDash([]);

        /* nodes — 소프트 글로우 */
        // 뒤 층부터 그린다. 순서가 뒤바뀌면 뒤 노드가 앞 노드를 덮어 깊이가 사라진다.
        const byDepth = [...dataRef.current.nodes].sort((left, right) => layerZ(left.kind) - layerZ(right.kind));

        for (const node of byDepth) {
          const z = layerZ(node.kind);
          const depth = perspectiveScale(z);
          const radius = node.radius * display.nodeScale * depth;
          const dimmed = isDimmed(node);
          const isAnchor = anchor != null && node.id === anchor.id;
          const color = palette.kind[node.kind];
          // 원근 투영: 앞 층은 중심에서 멀어지고 뒤 층은 중심으로 모인다.
          const nodeX = (node.x ?? 0) * depth;
          const nodeY = (node.y ?? 0) * depth;
          context.globalAlpha = dimmed ? 0.09 : depthOpacity(z);
          const glowRadius = palette.dark
            ? (isAnchor ? radius * 4 : radius * 2.9)
            : (isAnchor ? radius * 3.2 : radius * 2.3);
          const gradient = context.createRadialGradient(nodeX, nodeY, radius * 0.3, nodeX, nodeY, glowRadius);
          gradient.addColorStop(0, withAlpha(color, palette.dark ? (isAnchor ? 0.8 : 0.6) : (isAnchor ? 0.34 : 0.18)));
          gradient.addColorStop(1, withAlpha(color, 0));
          context.fillStyle = gradient;
          context.beginPath();
          context.arc(nodeX, nodeY, glowRadius, 0, Math.PI * 2);
          context.fill();
          context.beginPath();
          context.arc(nodeX, nodeY, radius, 0, Math.PI * 2);
          context.fillStyle = color;
          context.fill();
          if (palette.dark) {
            context.beginPath();
            context.arc(nodeX - radius * 0.28, nodeY - radius * 0.28, radius * 0.32, 0, Math.PI * 2);
            context.fillStyle = "rgba(255, 255, 255, 0.33)";
            context.fill();
          }
          if (searching && matchesQuery(node)) {
            context.strokeStyle = palette.accent;
            context.lineWidth = 1.6 / k;
            context.stroke();
          }
        }

        /* labels — screen space */
        context.setTransform(dpr, 0, 0, dpr, 0, 0);
        context.textAlign = "center";
        for (const node of dataRef.current.nodes) {
          const dimmed = isDimmed(node);
          const isAnchor = anchor != null && node.id === anchor.id;
          const inNeighborhood = neighborhood != null && neighborhood.has(node.id);
          const bigNode = node.connectionCount >= 6;
          const show = isAnchor || inNeighborhood || (searching && matchesQuery(node))
            || (neighborhood == null && (k >= display.labelThreshold || bigNode));
          if (!show || dimmed) continue;
          const labelDepth = perspectiveScale(layerZ(node.kind));
          const screenX = (node.x ?? 0) * labelDepth * k + x;
          const screenY = (node.y ?? 0) * labelDepth * k + y + node.radius * display.nodeScale * labelDepth * k + 12;
          if (screenX < -80 || screenX > width + 80 || screenY < -20 || screenY > height + 20) continue;
          let alpha = isAnchor ? 1 : bigNode ? 0.95 : Math.min(1, (k - display.labelThreshold + 0.35) / 0.5);
          if (neighborhood != null || searching) alpha = Math.max(alpha, 0.95);
          context.globalAlpha = Math.max(0, Math.min(1, alpha));
          context.font = `${isAnchor ? 800 : 700} ${isAnchor ? 12 : 10.5}px "SUIT Variable", "Pretendard Variable", sans-serif`;
          const label = node.title.length > 24 ? `${node.title.slice(0, 23)}…` : node.title;
          context.strokeStyle = palette.bg;
          context.lineWidth = 3.5;
          context.strokeText(label, screenX, screenY);
          context.fillStyle = isAnchor ? palette.text : palette.muted;
          context.fillText(label, screenX, screenY);
        }
        context.globalAlpha = 1;
      };

      frameId = requestAnimationFrame(draw);
      return () => cancelAnimationFrame(frameId);
    }, []);

    return <canvas ref={canvasRef} className="block h-full w-full" aria-label="지식 그래프" role="img" />;
  }
);
