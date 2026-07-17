import { afterEach, describe, expect, it, vi } from "vitest";
import type { AuthSession } from "../auth/session";
import {
  addMeetingParticipant,
  createMeeting,
  deleteMeeting,
  fetchMeetingDetail,
  fetchMeetingDialogue,
  fetchMeetingParticipants,
  fetchMeetings,
  fetchSpaceMembers,
  startMeetingTranscription,
  stopMeetingTranscription,
  updateMeeting,
  updateMeetingParticipant
} from "./workspace";

vi.mock("../auth/csrf", () => {
  const prepareBffRequest = async (init: RequestInit = {}) => {
    const method = (init.method || "GET").toUpperCase();
    const headers = new Headers(init.headers);
    if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
      headers.set("X-CSRF-TOKEN", "csrf-value");
    }
    return { ...init, credentials: "same-origin" as RequestCredentials, headers };
  };
  return {
    bffFetch: async (input: RequestInfo | URL, init: RequestInit = {}) =>
      fetch(input, await prepareBffRequest(init)),
    prepareBffRequest,
    resetCsrfToken: vi.fn()
  };
});

const session: AuthSession = {
  user: {
    id: "user-owner",
    email: "owner@meetingmind.ai",
    displayName: "Owner",
    status: "ACTIVE"
  },
  session: {
    expiresAt: "2026-07-17T00:00:00Z",
    idleExpiresAt: "2026-07-16T13:00:00Z",
    rememberMe: false
  }
};

afterEach(() => {
  vi.unstubAllGlobals();
});

function requestAt(fetchMock: ReturnType<typeof vi.fn<typeof fetch>>, index: number) {
  const [url, init] = fetchMock.mock.calls[index] ?? [];
  return { url, init, headers: new Headers(init?.headers) };
}

describe("meeting CRUD API client", () => {
  it("uses the BFF cookie session and CSRF without a Browser authorization header", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ meetings: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "meeting-1", status: "SCHEDULED" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchMeetings(session, "space-1");
    await createMeeting(session, "space-1", {
      title: "CRUD 회의",
      scheduledAt: "2026-07-15T10:00:00+09:00",
      participantUserIds: ["user-2"]
    });

    const listRequest = requestAt(fetchMock, 0);
    expect(listRequest.url).toBe("/api/v1/spaces/space-1/meetings");
    expect(listRequest.init?.credentials).toBe("same-origin");
    expect(listRequest.headers.has("Authorization")).toBe(false);

    const createRequest = requestAt(fetchMock, 1);
    expect(createRequest.url).toBe("/api/v1/spaces/space-1/meetings");
    expect(createRequest.init?.method).toBe("POST");
    expect(createRequest.headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(createRequest.headers.has("Authorization")).toBe(false);
    expect(createRequest.init?.body).toBe(
      JSON.stringify({
        title: "CRUD 회의",
        scheduledAt: "2026-07-15T10:00:00+09:00",
        participantUserIds: ["user-2"]
      })
    );
  });

  it("loads meeting detail and Space members through cookie-authenticated routes", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "meeting-1", participants: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ participants: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ members: [] }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchMeetingDetail(session, "meeting-1");
    await fetchMeetingParticipants(session, "meeting-1");
    await fetchSpaceMembers(session, "space-1");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/meetings/meeting-1",
      "/api/v1/meetings/meeting-1/participants",
      "/api/v1/spaces/space-1/members"
    ]);
    fetchMock.mock.calls.forEach(([, init]) => {
      expect(init?.credentials).toBe("same-origin");
      expect(new Headers(init?.headers).has("Authorization")).toBe(false);
    });
  });

  it("adds CSRF to participant grant and role mutation routes", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ participantId: "participant-2" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ participantId: "participant-2" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await addMeetingParticipant(session, "meeting-1", {
      userId: "user-2",
      role: "VIEWER",
      participantType: "member"
    });
    await updateMeetingParticipant(session, "meeting-1", "participant-2", {
      role: "EDITOR",
      accessStatus: "ACTIVE"
    });

    expect(requestAt(fetchMock, 0).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(requestAt(fetchMock, 1).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(fetchMock.mock.calls[0]?.[1]?.body).toBe(
      JSON.stringify({ userId: "user-2", role: "VIEWER", participantType: "member" })
    );
    expect(fetchMock.mock.calls[1]?.[1]?.body).toBe(
      JSON.stringify({ role: "EDITOR", accessStatus: "ACTIVE" })
    );
  });

  it("uses meeting PATCH and DELETE with CSRF without local fallback", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "meeting-1", status: "IN_PROGRESS" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ deleted: true }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await updateMeeting(session, "meeting-1", { status: "IN_PROGRESS" });
    await deleteMeeting(session, "meeting-1");

    expect(requestAt(fetchMock, 0).init?.method).toBe("PATCH");
    expect(requestAt(fetchMock, 0).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(requestAt(fetchMock, 1).init?.method).toBe("DELETE");
    expect(requestAt(fetchMock, 1).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
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

  it("uses cookie and CSRF boundaries for live transcription", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ meetingId: "meeting-1", transcriptStatus: "PROCESSING", sessionId: "session-1" }), {
          status: 200
        })
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ meetingId: "meeting-1", status: "PROCESSING", rows: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ meetingId: "meeting-1", transcriptStatus: "COMPLETED" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await startMeetingTranscription(session, "meeting-1", { mode: "realtime", trackId: "track-1" });
    await fetchMeetingDialogue(session, "meeting-1");
    await stopMeetingTranscription(session, "meeting-1", "session-1");

    expect(requestAt(fetchMock, 0).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(requestAt(fetchMock, 1).headers.has("X-CSRF-TOKEN")).toBe(false);
    expect(requestAt(fetchMock, 2).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    fetchMock.mock.calls.forEach(([, init]) => {
      expect(new Headers(init?.headers).has("Authorization")).toBe(false);
    });
  });
});
