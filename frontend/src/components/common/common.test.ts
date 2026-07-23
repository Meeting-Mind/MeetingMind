import { describe, expect, it } from "vitest";
import { getRoleBadgePresentation } from "./roleBadgeModel";
import { getStatusBadgePresentation } from "./statusBadgeModel";

describe("common status and role presentation", () => {
  it("maps meeting and report states to product labels", () => {
    expect(getStatusBadgePresentation("IN_PROGRESS")).toEqual({ label: "진행 중", tone: "warning" });
    expect(getStatusBadgePresentation("CONFIRMED")).toEqual({ label: "확정", tone: "positive" });
  });

  it("preserves an existing human-readable status label", () => {
    expect(getStatusBadgePresentation("보고서 생성됨")).toEqual({ label: "보고서 생성됨", tone: "neutral" });
  });

  it("keeps project and meeting roles distinct", () => {
    expect(getRoleBadgePresentation("OWNER")).toEqual({ label: "프로젝트 오너", tone: "owner" });
    expect(getRoleBadgePresentation("HOST")).toEqual({ label: "회의 호스트", tone: "host" });
  });

  it("supports an unknown role without inventing a permission", () => {
    expect(getRoleBadgePresentation("UNKNOWN_ROLE")).toEqual({ label: "UNKNOWN_ROLE", tone: "neutral" });
  });
});
