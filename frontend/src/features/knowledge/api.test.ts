import { describe, expect, it } from "vitest";
import { buildGraphView } from "./api";
import type { KnowledgeGraphResponse } from "../../types";

function baseResponse(): KnowledgeGraphResponse {
  return {
    clusters: [],
    edges: [],
    generatedAt: "2026-07-25T00:00:00Z",
    nodes: []
  };
}

describe("buildGraphView", () => {
  it("nodes 필드가 없으면 clusters에서 노드를 수집하고 중복을 제거한다", () => {
    const node = {
      id: "n1",
      sourceType: "projectKnowledge" as const,
      title: "지식",
      sourceMeetingId: null,
      embeddingStatus: "COMPLETED" as const
    };
    const response: KnowledgeGraphResponse = {
      ...baseResponse(),
      nodes: undefined,
      clusters: [
        { id: "c1", label: "A", sourceCount: 1, nodes: [node] },
        { id: "c2", label: "B", sourceCount: 1, nodes: [node] }
      ]
    };
    const view = buildGraphView(response);
    expect(view.nodes).toHaveLength(1);
    expect(view.nodes[0].kind).toBe("manual");
  });

  it("존재하지 않는 노드를 가리키는 엣지와 self-loop를 제거한다", () => {
    const response: KnowledgeGraphResponse = {
      ...baseResponse(),
      nodes: [
        { id: "a", sourceType: "report", title: "보고서", sourceMeetingId: null, embeddingStatus: "COMPLETED" },
        { id: "b", sourceType: "glossary", title: "용어", sourceMeetingId: null, embeddingStatus: "COMPLETED" }
      ],
      edges: [
        { from: "a", to: "b", similarity: 0.8 },
        { from: "a", to: "ghost", similarity: 0.9 },
        { from: "a", to: "a", similarity: 1 }
      ]
    };
    const view = buildGraphView(response);
    expect(view.links).toHaveLength(1);
    expect(view.nodes.find((node) => node.id === "a")?.connectionCount).toBe(1);
    expect(view.nodes.find((node) => node.id === "a")?.orphan).toBe(false);
  });

  it("edgeType이 SIMILARITY가 아니면 explicit 링크로 분류한다", () => {
    const response: KnowledgeGraphResponse = {
      ...baseResponse(),
      nodes: [
        { id: "a", sourceType: "decision", title: "결정", sourceMeetingId: null, embeddingStatus: "COMPLETED" },
        { id: "b", sourceType: "actionItem", title: "액션", sourceMeetingId: null, embeddingStatus: "COMPLETED" }
      ],
      edges: [
        { from: "a", to: "b", similarity: 1, edgeType: "REFERENCE" }
      ]
    };
    const view = buildGraphView(response);
    expect(view.links[0].explicit).toBe(true);
    expect(view.nodes.find((node) => node.id === "b")?.kind).toBe("action");
  });

  it("연결이 없는 노드는 orphan으로 표시한다", () => {
    const response: KnowledgeGraphResponse = {
      ...baseResponse(),
      nodes: [
        { id: "solo", sourceType: "transcript", title: "전사", sourceMeetingId: "m1", embeddingStatus: "COMPLETED" }
      ]
    };
    const view = buildGraphView(response);
    expect(view.nodes[0].orphan).toBe(true);
    expect(view.nodes[0].kind).toBe("transcript");
  });
});
