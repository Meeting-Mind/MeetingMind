import type { SimulationLinkDatum, SimulationNodeDatum } from "d3-force";

/** 그래프 시각화에서 쓰는 노드 종류. 백엔드 sourceType/nodeType을 화면용으로 정규화한 값. */
export type KnowledgeKind =
  | "report"
  | "decision"
  | "manual"
  | "external"
  | "glossary"
  | "action"
  | "transcript"
  | "summary";

export const KNOWLEDGE_KIND_LABELS: Record<KnowledgeKind, string> = {
  report: "보고서",
  decision: "결정",
  manual: "수동 지식",
  external: "외부 자료",
  glossary: "용어",
  action: "액션",
  transcript: "전사",
  summary: "요약"
};

/** kind → 디자인 토큰 CSS 변수 매핑. 색은 항상 토큰에서 가져온다. */
export const KNOWLEDGE_KIND_COLOR_VARS: Record<KnowledgeKind, string> = {
  report: "--app-accent",
  decision: "--app-highlight",
  manual: "--app-knowledge",
  external: "--app-warning",
  glossary: "--app-ai",
  action: "--app-action",
  transcript: "--app-subtle",
  summary: "--app-info"
};

export interface GraphNodeVM extends SimulationNodeDatum {
  id: string;
  title: string;
  description: string | null;
  kind: KnowledgeKind;
  sourceMeetingId: string | null;
  connectionCount: number;
  /** 화면 반지름(px). connectionCount 기반. */
  radius: number;
  orphan: boolean;
  detailTarget: { kind: string; id: string } | null;
}

export interface GraphLinkVM extends SimulationLinkDatum<GraphNodeVM> {
  /** true = 명시적 참조(위키링크/출처), false = 임베딩 유사도 링크 */
  explicit: boolean;
  similarity: number;
}

export interface KnowledgeGraphView {
  nodes: GraphNodeVM[];
  links: GraphLinkVM[];
  generatedAt: string;
  partial: boolean;
}

export interface GraphForcesConfig {
  center: number;
  repel: number;
  linkStrength: number;
  linkDistance: number;
}

export interface GraphDisplayConfig {
  /** 이 줌 배율 이상에서 일반 노드 라벨 표시 */
  labelThreshold: number;
  linkOpacity: number;
  nodeScale: number;
}
