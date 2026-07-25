import type { ReportCandidateActionItem, ReportCandidateDecision } from "../../types";

/**
 * 실제로 인용된 근거에만 번호를 붙인다.
 *
 * 전사 전체를 나열하지 않는다. 결정과 할 일이 실제로 인용한 것만 각주 번호를 받으며,
 * 결정 -> 할 일 순서로 매기므로 문서를 위에서 아래로 읽을 때 번호가 커진다.
 */
export function buildCitationIndex(
  decisions: ReportCandidateDecision[],
  actionItems: ReportCandidateActionItem[]
): Map<string, number> {
  const index = new Map<string, number>();
  for (const sourceId of [...decisions, ...actionItems].flatMap((item) => item.sourceIds)) {
    if (!index.has(sourceId)) {
      index.set(sourceId, index.size + 1);
    }
  }
  return index;
}
