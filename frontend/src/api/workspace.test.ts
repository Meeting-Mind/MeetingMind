import { afterEach, describe, expect, it, vi } from "vitest";
import type { AuthSession } from "../auth/session";
import {
  addMeetingParticipant,
  createSpaceInvitation,
  createDomainTerm,
  createTask,
  createMeeting,
  createProjectKnowledge,
  deleteMeeting,
  deleteTask,
  deleteProjectKnowledge,
  dismissTaskCandidate,
  editMeetingReportWithAi,
  explainMeetingTerm,
  archiveDomainTerm,
  fetchDomainTerms,
  fetchDashboardSummary,
  fetchMeetingDetail,
  fetchMeetingReportDetail,
  fetchMeetingDialogue,
  fetchMeetingParticipants,
  fetchProjectAiHistory,
  fetchProjectKnowledge,
  fetchProjectKnowledgeDetail,
  fetchMeetings,
  fetchSpaceMembers,
  startMeetingTranscription,
  stopMeetingTranscription,
  updateMeetingReport,
  restoreMeetingReport,
  updateMeeting,
  updateMeetingParticipant,
  updateProjectKnowledge,
  updateSpaceMemberRole,
  updateTask,
  updateDomainTerm
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
  it("loads only the current user's Project AI history through the cookie-authenticated BFF route", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ messages: [] }), { status: 200 })
    );
    vi.stubGlobal("fetch", fetchMock);

    await fetchProjectAiHistory(session, "space-1");

    const request = requestAt(fetchMock, 0);
    expect(request.url).toBe("/api/v1/spaces/space-1/ai/history");
    expect(request.init?.credentials).toBe("same-origin");
    expect(request.headers.has("Authorization")).toBe(false);
  });

  it("loads dashboard summary through the cookie-authenticated BFF route", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ todayMeetings: [], recentActivities: [], spaces: [], actionItems: [] }), { status: 200 })
    );
    vi.stubGlobal("fetch", fetchMock);

    await fetchDashboardSummary(session);

    expect(requestAt(fetchMock, 0).url).toBe("/api/v1/dashboard");
    expect(requestAt(fetchMock, 0).init?.credentials).toBe("same-origin");
    expect(requestAt(fetchMock, 0).headers.has("Authorization")).toBe(false);
  });

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
      scheduledEndAt: "2026-07-15T11:00:00+09:00",
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
        scheduledEndAt: "2026-07-15T11:00:00+09:00",
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

  it("requests report downloads through the cookie-authenticated BFF route", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(new Blob(["docx"]), { status: 200 }))
      .mockResolvedValueOnce(new Response(new Blob(["pdf"]), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    const { downloadMeetingReport } = await import("./workspace");
    await downloadMeetingReport(session, "meeting-1", "report-1", "docx");
    await downloadMeetingReport(session, "meeting-1", "report-1", "pdf");

    const request = requestAt(fetchMock, 0);
    expect(request.url).toBe("/api/v1/meetings/meeting-1/reports/report-1/download?format=docx");
    expect(request.init?.credentials).toBe("same-origin");
    expect(request.headers.has("Authorization")).toBe(false);
    expect(requestAt(fetchMock, 1).url).toBe("/api/v1/meetings/meeting-1/reports/report-1/download?format=pdf");
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

  it("requests selected-term explanations through the meeting-scoped BFF route", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(
        JSON.stringify({
          term: "RAG",
          explanation: "회의 검색 방식입니다.",
          sourceType: "transcript",
          sources: [],
          unsupported: false,
          unsupportedReason: null,
          model: "test"
        }),
        { status: 200 }
      )
    );
    vi.stubGlobal("fetch", fetchMock);

    await explainMeetingTerm(session, "meeting-1", { term: "RAG" });

    const request = requestAt(fetchMock, 0);
    expect(request.url).toBe("/api/v1/meetings/meeting-1/terms/explain");
    expect(request.init?.method).toBe("POST");
    expect(request.headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(request.headers.has("Authorization")).toBe(false);
    expect(request.init?.body).toBe(JSON.stringify({ term: "RAG" }));
  });

  it("dismisses task candidates through the cookie-authenticated BFF route", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ id: "candidate-1", status: "DISMISSED" }), { status: 200 })
    );
    vi.stubGlobal("fetch", fetchMock);

    await dismissTaskCandidate(session, "meeting-1", "candidate-1");

    const request = requestAt(fetchMock, 0);
    expect(request.url).toBe("/api/v1/meetings/meeting-1/task-candidates/candidate-1/dismiss");
    expect(request.init?.method).toBe("POST");
    expect(request.headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(request.headers.has("Authorization")).toBe(false);
  });

  it("uses scoped BFF routes for project knowledge reads and mutations", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ items: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "knowledge-1", content: "본문" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "knowledge-1", status: "PUBLISHED" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "knowledge-1", status: "PUBLISHED" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ deleted: true }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchProjectKnowledge(session, "space-1");
    await fetchProjectKnowledgeDetail(session, "space-1", "knowledge-1");
    await createProjectKnowledge(session, "space-1", {
      type: "manual",
      title: "권한 설계",
      content: "공식 지식"
    });
    await updateProjectKnowledge(session, "space-1", "knowledge-1", { title: "권한 설계 v2" });
    await deleteProjectKnowledge(session, "space-1", "knowledge-1");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/spaces/space-1/knowledge",
      "/api/v1/spaces/space-1/knowledge/knowledge-1",
      "/api/v1/spaces/space-1/knowledge",
      "/api/v1/spaces/space-1/knowledge/knowledge-1",
      "/api/v1/spaces/space-1/knowledge/knowledge-1"
    ]);
    expect(fetchMock.mock.calls.map(([, init]) => init?.method)).toEqual([
      undefined,
      undefined,
      "POST",
      "PATCH",
      "DELETE"
    ]);
    [2, 3, 4].forEach((index) => {
      expect(requestAt(fetchMock, index).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
      expect(requestAt(fetchMock, index).headers.has("Authorization")).toBe(false);
    });
  });

  it("views and restores report versions through the cookie-authenticated BFF routes", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "report-1", markdown: "# 원본" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "report-2", status: "DRAFT", version: 2 }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchMeetingReportDetail(session, "meeting-1", "report-1");
    await restoreMeetingReport(session, "meeting-1", "report-1");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/meetings/meeting-1/reports/report-1",
      "/api/v1/meetings/meeting-1/reports/report-1/restore"
    ]);
    expect(requestAt(fetchMock, 0).init?.method).toBeUndefined();
    expect(requestAt(fetchMock, 1).init?.method).toBe("POST");
    expect(requestAt(fetchMock, 1).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(requestAt(fetchMock, 1).headers.has("Authorization")).toBe(false);
  });

  it("sends report AI edits through the cookie-authenticated BFF route", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ candidate: { id: "report-2", status: "CANDIDATE" }, sources: [], unsupported: false }), {
        status: 200
      })
    );
    vi.stubGlobal("fetch", fetchMock);

    await editMeetingReportWithAi(session, "meeting-1", "report-1", "요약을 두 문장으로 줄여줘");

    const request = requestAt(fetchMock, 0);
    expect(request.url).toBe("/api/v1/meetings/meeting-1/reports/report-1/ai-edits");
    expect(request.init?.method).toBe("POST");
    expect(request.headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(request.headers.has("Authorization")).toBe(false);
    expect(request.init?.body).toBe(JSON.stringify({ instruction: "요약을 두 문장으로 줄여줘" }));
  });

  it("sends member, invitation, task, and report mutations only through BFF cookie and CSRF boundaries", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ invitationId: "invite-1" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ memberId: "member-1", role: "ADMIN" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "task-1", status: "TODO" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "task-1", status: "DONE" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ deleted: true }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "report-2", status: "DRAFT", version: 2 }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await createSpaceInvitation(session, "space-1", { email: "member@example.com", role: "MEMBER" });
    await updateSpaceMemberRole(session, "space-1", "member-1", { role: "ADMIN" });
    await createTask(session, "space-1", {
      title: "API 계약 확인",
      description: "BFF 경유",
      priority: "HIGH",
      labels: ["backend", "api"]
    });
    await updateTask(session, "space-1", "task-1", { status: "DONE", priority: "LOW", labels: [] });
    await deleteTask(session, "space-1", "task-1");
    await updateMeetingReport(session, "meeting-1", "report-1", { summary: "수정된 회의록 요약" });

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/spaces/space-1/invitations",
      "/api/v1/spaces/space-1/members/member-1",
      "/api/v1/spaces/space-1/tasks",
      "/api/v1/spaces/space-1/tasks/task-1",
      "/api/v1/spaces/space-1/tasks/task-1",
      "/api/v1/meetings/meeting-1/reports/report-1"
    ]);
    expect(fetchMock.mock.calls.map(([, init]) => init?.method)).toEqual(["POST", "PATCH", "POST", "PATCH", "DELETE", "PATCH"]);
    fetchMock.mock.calls.forEach(([, init]) => {
      expect(init?.credentials).toBe("same-origin");
      expect(new Headers(init?.headers).get("X-CSRF-TOKEN")).toBe("csrf-value");
      expect(new Headers(init?.headers).has("Authorization")).toBe(false);
    });
    expect(requestAt(fetchMock, 0).init?.body).toBe(JSON.stringify({ email: "member@example.com", role: "MEMBER" }));
    expect(requestAt(fetchMock, 2).init?.body).toBe(
      JSON.stringify({ title: "API 계약 확인", description: "BFF 경유", priority: "HIGH", labels: ["backend", "api"] })
    );
    expect(requestAt(fetchMock, 3).init?.body).toBe(JSON.stringify({ status: "DONE", priority: "LOW", labels: [] }));
    expect(requestAt(fetchMock, 5).init?.body).toBe(JSON.stringify({ summary: "수정된 회의록 요약" }));
  });

  it("uses Space-scoped term routes with query encoding and CSRF mutations", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(new Response(JSON.stringify({ terms: [] }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "term-1", status: "ACTIVE" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: "term-1", status: "ARCHIVED" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ deleted: true }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await fetchDomainTerms(session, "space-1", { keyword: "vector search", status: "ACTIVE" });
    await createDomainTerm(session, "space-1", { term: "pgvector", definition: "벡터 검색 확장" });
    await updateDomainTerm(session, "space-1", "term-1", { status: "ARCHIVED" });
    await archiveDomainTerm(session, "space-1", "term-1");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/spaces/space-1/terms?keyword=vector+search&status=ACTIVE",
      "/api/v1/spaces/space-1/terms",
      "/api/v1/spaces/space-1/terms/term-1",
      "/api/v1/spaces/space-1/terms/term-1"
    ]);
    expect(fetchMock.mock.calls.map(([, init]) => init?.method)).toEqual([undefined, "POST", "PATCH", "DELETE"]);
    expect(requestAt(fetchMock, 0).headers.has("X-CSRF-TOKEN")).toBe(false);
    expect(requestAt(fetchMock, 1).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(requestAt(fetchMock, 2).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(requestAt(fetchMock, 3).headers.get("X-CSRF-TOKEN")).toBe("csrf-value");
  });
});
