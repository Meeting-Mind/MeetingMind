import { describe, expect, it } from "vitest";
import { describeChanges, diffByTitle, diffReport, diffText, type ReportSnapshot } from "./diff";
import type { ReportCandidateActionItem, ReportCandidateDecision } from "../../types";

function decision(title: string, id = `report-decision-${Math.random()}`): ReportCandidateDecision {
  return { id, title, rationale: null, sourceIds: [] };
}

function actionItem(title: string, id = `report-action-${Math.random()}`): ReportCandidateActionItem {
  return { id, title, assignee: null, dueDate: null, confirmationState: "candidate", sourceIds: [] };
}

function snapshot(partial: Partial<ReportSnapshot> = {}): ReportSnapshot {
  return { summary: null, decisions: [], actionItems: [], ...partial };
}

describe("diffText", () => {
  it("공백만 다른 문장은 바뀐 것으로 보지 않는다", () => {
    expect(diffText("베타는  다음 달에 시작한다", " 베타는 다음 달에 시작한다 ").changed).toBe(false);
  });

  it("내용이 다르면 바뀐 것으로 본다", () => {
    expect(diffText("정확도 81%", "정확도 87%").changed).toBe(true);
  });

  it("null과 빈 문자열을 같게 본다", () => {
    expect(diffText(null, "").changed).toBe(false);
  });
});

describe("diffByTitle", () => {
  it("id가 달라도 제목이 같으면 유지로 본다", () => {
    // 서버가 수정 때마다 id를 새로 만들기 때문에 이 동작이 핵심이다.
    const before = [decision("베타는 다음 달 시작", "report-decision-old")];
    const after = [decision("베타는 다음 달 시작", "report-decision-new")];

    const result = diffByTitle(before, after);

    expect(result.changed).toBe(false);
    expect(result.kept).toHaveLength(1);
    expect(result.added).toHaveLength(0);
    expect(result.removed).toHaveLength(0);
  });

  it("추가와 삭제를 구분한다", () => {
    const before = [decision("유지되는 결정"), decision("사라질 결정")];
    const after = [decision("유지되는 결정"), decision("새로 생긴 결정")];

    const result = diffByTitle(before, after);

    expect(result.kept.map((item) => item.title)).toEqual(["유지되는 결정"]);
    expect(result.added.map((item) => item.title)).toEqual(["새로 생긴 결정"]);
    expect(result.removed.map((item) => item.title)).toEqual(["사라질 결정"]);
  });

  it("제목만 다듬은 수정은 삭제 1건 + 추가 1건으로 보인다", () => {
    // 의도한 절충. 비슷한 제목을 억지로 이어붙이면 다른 결정을 "수정됨"으로
    // 잘못 묶을 수 있고, 그 오판은 사용자가 확인 없이 적용하게 만든다.
    const result = diffByTitle([decision("베타 시작")], [decision("베타는 다음 달 시작")]);

    expect(result.added).toHaveLength(1);
    expect(result.removed).toHaveLength(1);
    expect(result.kept).toHaveLength(0);
  });

  it("같은 제목이 여러 개일 때 개수까지 센다", () => {
    // 개수를 무시하면 중복 항목 하나가 지워져도 "바뀌지 않음"으로 보인다.
    const before = [decision("같은 제목"), decision("같은 제목")];
    const after = [decision("같은 제목")];

    const result = diffByTitle(before, after);

    expect(result.changed).toBe(true);
    expect(result.kept).toHaveLength(1);
    expect(result.removed).toHaveLength(1);
  });

  it("순서만 바뀌면 바뀐 것으로 보지 않는다", () => {
    const before = [decision("첫째"), decision("둘째")];
    const after = [decision("둘째"), decision("첫째")];

    expect(diffByTitle(before, after).changed).toBe(false);
  });
});

describe("diffReport", () => {
  it("요약만 바꾼 수정에서 결정과 할 일은 그대로다", () => {
    const decisions = [decision("베타는 다음 달 시작")];
    const items = [actionItem("오답 대응 방안 마련")];
    const before = snapshot({ summary: "긴 요약 문장입니다.", decisions, actionItems: items });
    const after = snapshot({ summary: "짧은 요약.", decisions: [decision("베타는 다음 달 시작")], actionItems: [actionItem("오답 대응 방안 마련")] });

    const result = diffReport(before, after);

    expect(result.changed).toBe(true);
    expect(result.summary.changed).toBe(true);
    // 화면에 "결정 2건 · 바뀌지 않음"을 띄우려면 이 단정이 성립해야 한다.
    expect(result.decisions.changed).toBe(false);
    expect(result.actionItems.changed).toBe(false);
  });

  it("아무것도 바뀌지 않으면 changed가 false다", () => {
    const before = snapshot({ summary: "같은 요약", decisions: [decision("같은 결정")] });
    const after = snapshot({ summary: "같은 요약", decisions: [decision("같은 결정")] });

    expect(diffReport(before, after).changed).toBe(false);
  });
});

describe("describeChanges", () => {
  it("바뀐 곳이 없으면 빈 배열이다", () => {
    const same = snapshot({ summary: "같은 요약" });
    expect(describeChanges(diffReport(same, same))).toEqual([]);
  });

  it("바뀐 영역을 사람이 읽을 문구로 만든다", () => {
    const before = snapshot({ summary: "이전 요약", decisions: [decision("사라질 결정")] });
    const after = snapshot({ summary: "새 요약", decisions: [decision("새 결정")] });

    expect(describeChanges(diffReport(before, after))).toEqual([
      "요약",
      "결정 추가 1건 · 삭제 1건",
    ]);
  });
});
