import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  archiveKnowledge,
  createKnowledge,
  fetchKnowledgeDetail,
  fetchKnowledgeGraphView,
  fetchKnowledgeList,
  restoreKnowledge,
  updateKnowledge
} from "./api";
import type {
  CreateProjectKnowledgeRequest,
  UpdateProjectKnowledgeRequest
} from "../../types";
import type { GraphNodeVM } from "./types";

export function useKnowledgeGraphQuery(spaceId: string) {
  return useQuery({
    queryKey: ["knowledge", "graph", spaceId],
    queryFn: () => fetchKnowledgeGraphView(spaceId),
    enabled: spaceId.length > 0
  });
}

export function useKnowledgeListQuery(spaceId: string, enabled = true) {
  return useQuery({
    queryKey: ["knowledge", "list", spaceId],
    queryFn: () => fetchKnowledgeList(spaceId),
    enabled: enabled && spaceId.length > 0
  });
}

/** projectKnowledge 노드에 한해 상세 본문을 조회한다. 그 외 노드는 메타 정보만 보여준다. */
export function useKnowledgeDetailQuery(spaceId: string, node: GraphNodeVM | null) {
  const target = node?.detailTarget?.kind === "projectKnowledge" ? node.detailTarget.id : null;
  return useQuery({
    queryKey: ["knowledge", "detail", spaceId, target],
    queryFn: () => fetchKnowledgeDetail(spaceId, target as string),
    enabled: spaceId.length > 0 && target != null
  });
}

/** 지식 CRUD. 성공 시 그래프/목록/상세 캐시를 함께 무효화한다. */
export function useKnowledgeMutations(spaceId: string) {
  const queryClient = useQueryClient();
  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["knowledge"] });
  };

  const create = useMutation({
    mutationFn: (request: CreateProjectKnowledgeRequest) => createKnowledge(spaceId, request),
    onSuccess: invalidate
  });

  const update = useMutation({
    mutationFn: (input: { knowledgeId: string; request: UpdateProjectKnowledgeRequest }) =>
      updateKnowledge(spaceId, input.knowledgeId, input.request),
    onSuccess: invalidate
  });

  const archive = useMutation({
    mutationFn: (knowledgeId: string) => archiveKnowledge(spaceId, knowledgeId),
    onSuccess: invalidate
  });

  const restore = useMutation({
    mutationFn: (knowledgeId: string) => restoreKnowledge(spaceId, knowledgeId),
    onSuccess: invalidate
  });

  return { create, update, archive, restore };
}
