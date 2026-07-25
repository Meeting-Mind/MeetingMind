import { describe, expect, it } from "vitest";
import type { MeetingDialogueResponse } from "../../types";
import { buildTranscriptEntries, filterTranscriptEntries } from "./selectors";

function dialogue(partial: Partial<MeetingDialogueResponse> = {}): MeetingDialogueResponse {
  return {
    meetingId: "m1",
    status: "PROCESSING",
    rows: [],
    partials: [],
    ...partial
  };
}

function row(overrides: Partial<MeetingDialogueResponse["rows"][number]> = {}) {
  return {
    segmentId: "s1",
    speakerId: "u1",
    speakerLabel: "Speaker 1",
    speakerName: "김서연",
    startMs: 0,
    endMs: 1000,
    text: "안녕하세요",
    ...overrides
  };
}

describe("buildTranscriptEntries", () => {
  it("rows를 startMs 오름차순으로 정렬한다", () => {
    const entries = buildTranscriptEntries(
      dialogue({
        rows: [
          row({ segmentId: "b", startMs: 5000, text: "두번째" }),
          row({ segmentId: "a", startMs: 1000, text: "첫번째" })
        ]
      })
    );
    expect(entries.map((entry) => entry.text)).toEqual(["첫번째", "두번째"]);
  });

  it("partial key는 index가 아니라 speakerLabel 기준으로 안정적이다", () => {
    const first = buildTranscriptEntries(
      dialogue({
        partials: [
          { speakerLabel: "Speaker 2", speakerName: null, text: "말하는 중" },
          { speakerLabel: "Speaker 1", speakerName: null, text: "저도요" }
        ]
      })
    );
    const second = buildTranscriptEntries(
      dialogue({
        partials: [
          { speakerLabel: "Speaker 1", speakerName: null, text: "저도요..." },
          { speakerLabel: "Speaker 2", speakerName: null, text: "말하는 중입니다" }
        ]
      })
    );
    // 순서가 뒤바뀌어도 같은 화자는 같은 key를 유지한다.
    const keyOf = (entries: ReturnType<typeof buildTranscriptEntries>, speaker: string) =>
      entries.find((entry) => entry.speakerId === speaker)?.key;
    expect(keyOf(first, "Speaker 1")).toBe(keyOf(second, "Speaker 1"));
    expect(keyOf(first, "Speaker 2")).toBe(keyOf(second, "Speaker 2"));
  });

  it("이미 확정된 발화와 동일한 partial은 중복이므로 제거한다", () => {
    const entries = buildTranscriptEntries(
      dialogue({
        rows: [row({ speakerLabel: "Speaker 1", text: "회의를 시작하겠습니다" })],
        partials: [
          { speakerLabel: "Speaker 1", speakerName: null, text: "  회의를 시작하겠습니다 " }
        ]
      })
    );
    expect(entries).toHaveLength(1);
    expect(entries[0].isPartial).toBe(false);
  });

  it("같은 화자의 partial이 여러 개여도 하나만 남긴다", () => {
    const entries = buildTranscriptEntries(
      dialogue({
        partials: [
          { speakerLabel: "Speaker 1", speakerName: null, text: "첫 조각" },
          { speakerLabel: "Speaker 1", speakerName: null, text: "둘째 조각" }
        ]
      })
    );
    expect(entries).toHaveLength(1);
    expect(entries[0].text).toBe("첫 조각");
  });

  it("빈 partial과 undefined 응답을 안전하게 처리한다", () => {
    expect(buildTranscriptEntries(undefined)).toEqual([]);
    expect(
      buildTranscriptEntries(
        dialogue({ partials: [{ speakerLabel: "Speaker 1", speakerName: null, text: "   " }] })
      )
    ).toEqual([]);
  });
});

describe("filterTranscriptEntries", () => {
  it("본문과 화자명 모두에서 검색한다", () => {
    const entries = buildTranscriptEntries(
      dialogue({
        rows: [
          row({ segmentId: "a", speakerName: "김서연", text: "배포 일정 공유합니다", startMs: 0 }),
          row({ segmentId: "b", speakerName: "서동준", text: "확인했습니다", startMs: 100 })
        ]
      })
    );
    expect(filterTranscriptEntries(entries, "배포")).toHaveLength(1);
    expect(filterTranscriptEntries(entries, "서동준")).toHaveLength(1);
    expect(filterTranscriptEntries(entries, "")).toHaveLength(2);
  });
});
