import { describe, expect, it } from "vitest";
import { formatStartsAt } from "./useLiveMeetingDetail";

describe("formatStartsAt", () => {
  it("formats a valid ISO timestamp as ko-KR time", () => {
    expect(formatStartsAt("2026-07-16T14:00:00+09:00")).toBe("오후 02:00");
  });

  it("falls back to the raw string when it cannot be parsed", () => {
    expect(formatStartsAt("not-a-date")).toBe("not-a-date");
  });
});
