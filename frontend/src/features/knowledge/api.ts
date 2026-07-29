import { jsonHeaders, requestJson } from "../../api/client";
import type {
  CreateProjectKnowledgeRequest,
  DeleteProjectKnowledgeResponse,
  KnowledgeGraphNode,
  KnowledgeGraphResponse,
  ProjectKnowledgeDetailResponse,
  ProjectKnowledgeListResponse,
  ProjectKnowledgeMutationResponse,
  UpdateProjectKnowledgeRequest
} from "../../types";
import type { GraphLinkVM, GraphNodeVM, KnowledgeGraphView, KnowledgeKind } from "./types";

/**
 * knowledge feature 전용 API 레이어.
 * 기존 api/knowledge.ts 함수들은 AuthSession 파라미터를 받지만 실제로는 사용하지 않는다
 * (BFF 쿠키 세션). feature 레이어에서는 파라미터를 정리한 형태로 다시 정의한다.
 */

export async function fetchKnowledgeGraphResponse(spaceId: string): Promise<KnowledgeGraphResponse> {
  return requestJson<KnowledgeGraphResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/graph`,
    { headers: undefined }
  );
}

export async function fetchKnowledgeDetail(
  spaceId: string,
  knowledgeId: string
): Promise<ProjectKnowledgeDetailResponse> {
  return requestJson<ProjectKnowledgeDetailResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/${encodeURIComponent(knowledgeId)}`,
    { headers: undefined }
  );
}

export async function fetchKnowledgeList(spaceId: string): Promise<ProjectKnowledgeListResponse> {
  return requestJson<ProjectKnowledgeListResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge`,
    { headers: undefined }
  );
}

export async function createKnowledge(
  spaceId: string,
  request: CreateProjectKnowledgeRequest
): Promise<ProjectKnowledgeMutationResponse> {
  return requestJson<ProjectKnowledgeMutationResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge`,
    { method: "POST", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}

export async function updateKnowledge(
  spaceId: string,
  knowledgeId: string,
  request: UpdateProjectKnowledgeRequest
): Promise<ProjectKnowledgeMutationResponse> {
  return requestJson<ProjectKnowledgeMutationResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/${encodeURIComponent(knowledgeId)}`,
    { method: "PATCH", headers: jsonHeaders(), body: JSON.stringify(request) }
  );
}

export async function archiveKnowledge(
  spaceId: string,
  knowledgeId: string
): Promise<DeleteProjectKnowledgeResponse> {
  return requestJson<DeleteProjectKnowledgeResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/${encodeURIComponent(knowledgeId)}`,
    { method: "DELETE", headers: jsonHeaders() }
  );
}

export async function restoreKnowledge(
  spaceId: string,
  knowledgeId: string
): Promise<DeleteProjectKnowledgeResponse> {
  return requestJson<DeleteProjectKnowledgeResponse>(
    `/api/v1/spaces/${encodeURIComponent(spaceId)}/knowledge/${encodeURIComponent(knowledgeId)}/restore`,
    { method: "POST", headers: jsonHeaders() }
  );
}

const SOURCE_TYPE_TO_KIND: Record<KnowledgeGraphNode["sourceType"], KnowledgeKind> = {
  projectKnowledge: "manual",
  transcript: "transcript",
  meetingSummary: "summary",
  decision: "decision",
  actionItem: "action",
  report: "report",
  glossary: "glossary"
};

function nodeKind(node: KnowledgeGraphNode): KnowledgeKind {
  // nodeType(그래프 API v2)이 있으면 우선, 없으면 sourceType 기반.
  switch (node.nodeType) {
    case "REPORT": return "report";
    case "DECISION": return "decision";
    case "ACTION":
    case "TASK": return "action";
    case "GLOSSARY": return "glossary";
    case "PROJECT_KNOWLEDGE": return "manual";
    case "MEETING": return "summary";
    default:
      return SOURCE_TYPE_TO_KIND[node.sourceType] ?? "manual";
  }
}

function nodeRadius(connectionCount: number): number {
  return 3.2 + Math.sqrt(connectionCount + 1) * 2.1;
}

/**
 * KnowledgeGraphResponse → 화면 모델.
 * - nodes 필드가 있으면 사용, 없으면(구버전 응답) clusters에서 중복 제거 수집.
 * - edgeType이 명시적 참조를 나타내면 explicit, 그 외에는 유사도 링크로 취급.
 */
export function buildGraphView(response: KnowledgeGraphResponse): KnowledgeGraphView {
  const rawNodes = response.nodes && response.nodes.length > 0
    ? response.nodes
    : response.clusters.flatMap((cluster) => cluster.nodes);

  const byId = new Map<string, KnowledgeGraphNode>();
  for (const node of rawNodes) {
    if (!byId.has(node.id)) {
      byId.set(node.id, node);
    }
  }

  const degree = new Map<string, number>();
  const links: GraphLinkVM[] = [];
  for (const edge of response.edges) {
    if (!byId.has(edge.from) || !byId.has(edge.to) || edge.from === edge.to) {
      continue;
    }
    degree.set(edge.from, (degree.get(edge.from) ?? 0) + 1);
    degree.set(edge.to, (degree.get(edge.to) ?? 0) + 1);
    const explicit = edge.edgeType != null && edge.edgeType !== "SIMILARITY";
    links.push({
      source: edge.from,
      target: edge.to,
      explicit,
      similarity: edge.similarity
    });
  }

  const nodes: GraphNodeVM[] = Array.from(byId.values()).map((node) => {
    const connectionCount = node.connectionCount ?? degree.get(node.id) ?? 0;
    return {
      id: node.id,
      title: node.title,
      description: node.description ?? null,
      kind: nodeKind(node),
      sourceMeetingId: node.sourceMeetingId,
      connectionCount,
      radius: nodeRadius(connectionCount),
      orphan: (degree.get(node.id) ?? 0) === 0,
      detailTarget: node.detailTarget ?? (
        node.sourceType === "projectKnowledge"
          ? { kind: "projectKnowledge", id: node.entityId ?? node.id }
          : null
      )
    };
  });

  return {
    nodes,
    links,
    generatedAt: response.generatedAt,
    partial: response.partial ?? false
  };
}

export async function fetchKnowledgeGraphView(spaceId: string): Promise<KnowledgeGraphView> {
  return buildGraphView(await fetchKnowledgeGraphResponse(spaceId));
}
