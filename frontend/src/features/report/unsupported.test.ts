import { describe, expect, it } from "vitest";
import { reportUnsupportedMessage } from "./unsupported";

describe("reportUnsupportedMessage", () => {
  it("근거 부재와 인용 검증 실패를 서로 다른 원인으로 보여준다", () => {
    expect(reportUnsupportedMessage("NO_EVIDENCE").title).toContain("전사 근거");
    expect(reportUnsupportedMessage("UNVERIFIED_OUTPUT").title).toContain("근거를 확인하지 못했습니다");
  });

  it("결정이나 할 일이 없다는 문구를 실패 원인으로 사용하지 않는다", () => {
    for (const reason of ["NO_EVIDENCE", "LOW_RELEVANCE", "MODEL_UNSUPPORTED", "UNVERIFIED_OUTPUT"] as const) {
      const message = reportUnsupportedMessage(reason);
      expect(`${message.title} ${message.why}`).not.toContain("결정이나 할 일");
    }
  });
});
