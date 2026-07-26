import { describe, expect, it } from "vitest";
import { buildCitationIndex } from "./citations";
import type { ReportCandidateActionItem, ReportCandidateDecision } from "../../types";

function decision(id: string, sourceIds: string[]): ReportCandidateDecision {
  return { id, title: `결정 ${id}`, rationale: null, sourceIds };
}

function actionItem(id: string, sourceIds: string[]): ReportCandidateActionItem {
  return { id, title: `할 일 ${id}`, assignee: null, dueDate: null, confirmationState: "candidate", sourceIds };
}

describe("buildCitationIndex", () => {
  it("인용 순서대로 1번부터 번호를 붙인다", () => {
    const index = buildCitationIndex([decision("d1", ["segment-b", "segment-a"])], []);

    expect(index.get("segment-b")).toBe(1);
    expect(index.get("segment-a")).toBe(2);
  });

  it("같은 근거를 여러 항목이 인용해도 번호는 하나다", () => {
    const index = buildCitationIndex([decision("d1", ["segment-a"])], [actionItem("a1", ["segment-a"])]);

    expect(index.get("segment-a")).toBe(1);
    expect(index.size).toBe(1);
  });

  it("결정 다음에 할 일 순서로 번호를 이어간다", () => {
    // 문서를 위에서 아래로 읽을 때 번호가 커져야 한다.
    const index = buildCitationIndex([decision("d1", ["segment-a"])], [actionItem("a1", ["segment-b"])]);

    expect(index.get("segment-a")).toBe(1);
    expect(index.get("segment-b")).toBe(2);
  });

  it("인용된 근거가 없으면 비어 있다", () => {
    // 전사 전체를 나열하지 않는다. 실제로 인용된 것만 번호를 받는다.
    expect(buildCitationIndex([decision("d1", [])], []).size).toBe(0);
  });
});
