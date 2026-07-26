import type { ClusterBasis } from "./clustering";
import { create } from "zustand";
import type { GraphDisplayConfig, GraphForcesConfig, KnowledgeKind } from "./types";

/**
 * knowledge 그래프 화면의 client state.
 * server state(그래프 데이터)는 TanStack Query가 담당하고,
 * 여기는 필터·선택·뷰 파라미터 같은 UI 상태만 둔다.
 */
interface KnowledgeGraphState {
  hiddenKinds: ReadonlySet<KnowledgeKind>;
  showOrphans: boolean;
  /** 연결된 노드끼리 덩어리로 모아 보는 상태. */
  clustered: boolean;
  clusterBasis: ClusterBasis;
  search: string;
  selectedId: string | null;
  forces: GraphForcesConfig;
  display: GraphDisplayConfig;
  toggleKind: (kind: KnowledgeKind) => void;
  setShowOrphans: (value: boolean) => void;
  setClustered: (value: boolean) => void;
  setClusterBasis: (value: ClusterBasis) => void;
  setSearch: (value: string) => void;
  setSelectedId: (id: string | null) => void;
  setForces: (patch: Partial<GraphForcesConfig>) => void;
  setDisplay: (patch: Partial<GraphDisplayConfig>) => void;
}

export const DEFAULT_FORCES: GraphForcesConfig = {
  center: 0.05,
  repel: 140,
  linkStrength: 0.6,
  linkDistance: 64
};

export const DEFAULT_DISPLAY: GraphDisplayConfig = {
  labelThreshold: 1.1,
  linkOpacity: 0.45,
  nodeScale: 1
};

export const useKnowledgeGraphStore = create<KnowledgeGraphState>((set) => ({
  hiddenKinds: new Set<KnowledgeKind>(),
  showOrphans: true,
  clustered: false,
  clusterBasis: "link",
  search: "",
  selectedId: null,
  forces: DEFAULT_FORCES,
  display: DEFAULT_DISPLAY,
  toggleKind: (kind) => set((state) => {
    const next = new Set(state.hiddenKinds);
    if (next.has(kind)) {
      next.delete(kind);
    } else {
      next.add(kind);
    }
    return { hiddenKinds: next };
  }),
  setShowOrphans: (value) => set({ showOrphans: value }),
  setClustered: (value) => set({ clustered: value }),
  setClusterBasis: (value) => set({ clusterBasis: value }),
  setSearch: (value) => set({ search: value }),
  setSelectedId: (id) => set({ selectedId: id }),
  setForces: (patch) => set((state) => ({ forces: { ...state.forces, ...patch } })),
  setDisplay: (patch) => set((state) => ({ display: { ...state.display, ...patch } }))
}));
