import { afterEach, describe, expect, it, vi } from "vitest";
import type { AuthSession } from "../auth/session";
import { createMeeting, deleteMeeting, fetchMeetings, updateMeeting } from "./workspace";

const session: AuthSession = {
  accessToken: "access-token",
  refreshToken: "refresh-token",
  tokenType: "Bearer",
  expiresIn: 3600,
  refreshExpiresIn: 1209600,
  user: {
    id: "user-owner",
    email: "owner@meetingmind.ai",
    displayName: "Owner",
    status: "active"
  }
};

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("meeting CRUD API client", () => {
  it("uses the target list and create routes with bearer auth", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ meetings: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "meeting-1", status: "SCHEDULED" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchMeetings(session, "space-1");
    await createMeeting(session, "space-1", {
      title: "CRUD 회의",
      scheduledAt: "2026-07-15T10:00:00+09:00"
    });

    expect(fetchMock).toHaveBeenNthCalledWith(1, "/api/v1/spaces/space-1/meetings", {
      headers: { Authorization: "Bearer access-token" }
    });
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      "/api/v1/spaces/space-1/meetings",
      expect.objectContaining({ method: "POST", body: expect.stringContaining("CRUD 회의") })
    );
  });

  it("uses meeting PATCH and DELETE without local fallback", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "meeting-1", status: "IN_PROGRESS" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ deleted: true }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await updateMeeting(session, "meeting-1", { status: "IN_PROGRESS" });
    await deleteMeeting(session, "meeting-1");

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      "/api/v1/meetings/meeting-1",
      expect.objectContaining({ method: "PATCH", body: JSON.stringify({ status: "IN_PROGRESS" }) })
    );
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/v1/meetings/meeting-1", {
      method: "DELETE",
      headers: { Authorization: "Bearer access-token" }
    });
  });

  it("surfaces backend CRUD errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(JSON.stringify({ code: "MEETING_ALREADY_PROCESSING", message: "진행 중인 회의는 삭제할 수 없습니다." }), {
          status: 409
        })
      )
    );

    await expect(deleteMeeting(session, "meeting-1")).rejects.toThrow("진행 중인 회의는 삭제할 수 없습니다.");
  });
});
