import type { ReportCandidateActionItem, ReportCandidateDecision } from "../../types";

/**
 * AI 수정 제안이 회의록의 어디를 바꾸는지 프론트에서 계산한다.
 *
 * 서버는 수정된 회의록 **전체**를 새로 내려줄 뿐 무엇을 바꿨는지 알려주지 않는다.
 * 그래서 이전 상태와 새 상태를 비교한다.
 *
 * ID로 맞출 수 없다: `ReportCandidateService`가 결정과 할 일의 id를 매번
 * `UUID.randomUUID()`로 새로 만든다. 같은 내용이어도 id가 달라지므로 제목 텍스트로 맞춘다.
 *
 * 그래서 "제목만 다듬은 수정"은 삭제 1건 + 추가 1건으로 보인다. 이는 의도한 절충이다.
 * 비슷한 제목을 억지로 이어붙이면 실제로는 다른 결정을 "수정됨"으로 잘못 묶을 수 있고,
 * 그 오판은 사용자가 확인 없이 적용하게 만든다. 과하게 묶기보다 정직하게 나눠 보여준다.
 */

export type TextChange = { changed: boolean; before: string; after: string };

export type ListChange<T> = {
  changed: boolean;
  kept: T[];
  added: T[];
  removed: T[];
};

export type ReportDiff = {
  /** 하나라도 바뀐 곳이 있는지. 없으면 "바뀐 내용이 없습니다"를 보여준다. */
  changed: boolean;
  summary: TextChange;
  decisions: ListChange<ReportCandidateDecision>;
  actionItems: ListChange<ReportCandidateActionItem>;
};

type Titled = { title: string };

/**
 * 앞뒤 공백과 연속 공백만 정리해 비교한다. 대소문자는 건드리지 않는다 —
 * 한국어에는 의미가 없고, 영문 고유명사의 대소문자 차이는 실제 수정일 수 있다.
 */
function normalize(value: string | null | undefined): string {
  return (value ?? "").trim().replace(/\s+/g, " ");
}

export function diffText(before: string | null | undefined, after: string | null | undefined): TextChange {
  const left = normalize(before);
  const right = normalize(after);
  return { changed: left !== right, before: before ?? "", after: after ?? "" };
}

export function diffByTitle<T extends Titled>(before: T[], after: T[]): ListChange<T> {
  // 같은 제목이 여러 번 나올 수 있으므로 개수까지 센다. 개수를 무시하면
  // 중복 항목 하나가 지워져도 "바뀌지 않음"으로 보인다.
  const beforeCounts = new Map<string, number>();
  for (const item of before) {
    const key = normalize(item.title);
    beforeCounts.set(key, (beforeCounts.get(key) ?? 0) + 1);
  }

  const kept: T[] = [];
  const added: T[] = [];
  for (const item of after) {
    const key = normalize(item.title);
    const remaining = beforeCounts.get(key) ?? 0;
    if (remaining > 0) {
      beforeCounts.set(key, remaining - 1);
      kept.push(item);
    } else {
      added.push(item);
    }
  }

  // 위에서 소진되지 않고 남은 것이 사라진 항목이다.
  const leftover = new Map(beforeCounts);
  const removed: T[] = [];
  for (const item of before) {
    const key = normalize(item.title);
    const remaining = leftover.get(key) ?? 0;
    if (remaining > 0) {
      leftover.set(key, remaining - 1);
      removed.push(item);
    }
  }

  return { changed: added.length > 0 || removed.length > 0, kept, added, removed };
}

export type ReportSnapshot = {
  summary: string | null;
  decisions: ReportCandidateDecision[];
  actionItems: ReportCandidateActionItem[];
};

export function diffReport(before: ReportSnapshot, after: ReportSnapshot): ReportDiff {
  const summary = diffText(before.summary, after.summary);
  const decisions = diffByTitle(before.decisions, after.decisions);
  const actionItems = diffByTitle(before.actionItems, after.actionItems);
  return {
    changed: summary.changed || decisions.changed || actionItems.changed,
    summary,
    decisions,
    actionItems,
  };
}

/** "요약 1곳이 바뀝니다" 같은 안내 문구. 바뀐 곳이 없으면 빈 배열이다. */
export function describeChanges(diff: ReportDiff): string[] {
  const parts: string[] = [];
  if (diff.summary.changed) {
    parts.push("요약");
  }
  if (diff.decisions.changed) {
    const { added, removed } = diff.decisions;
    parts.push(`결정 ${added.length ? `추가 ${added.length}건` : ""}${added.length && removed.length ? " · " : ""}${removed.length ? `삭제 ${removed.length}건` : ""}`.trim());
  }
  if (diff.actionItems.changed) {
    const { added, removed } = diff.actionItems;
    parts.push(`할 일 ${added.length ? `추가 ${added.length}건` : ""}${added.length && removed.length ? " · " : ""}${removed.length ? `삭제 ${removed.length}건` : ""}`.trim());
  }
  return parts;
}
