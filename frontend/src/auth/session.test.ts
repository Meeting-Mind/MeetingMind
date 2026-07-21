import { afterEach, describe, expect, it, vi } from "vitest";
import { resetCsrfToken } from "./csrf";
import {
  bootstrapAuthSession,
  changeCurrentPassword,
  loginWithPassword,
  confirmPasswordReset,
  requestPasswordReset,
  withdrawCurrentAccount,
  logoutAllSessions,
  logoutCurrentSession,
  updateCurrentProfile,
  type AuthSession
} from "./session";

const validSession: AuthSession = {
  user: {
    id: "user-1",
    email: "user@example.com",
    displayName: "User",
    status: "ACTIVE"
  },
  session: {
    expiresAt: "2026-07-17T00:00:00Z",
    idleExpiresAt: "2026-07-16T13:00:00Z",
    rememberMe: false
  }
};

afterEach(() => {
  resetCsrfToken();
  vi.unstubAllGlobals();
});

describe("BFF auth session", () => {
  it("restores an authenticated session from the bootstrap endpoint", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ authenticated: true, ...validSession }), { status: 200 })
    );
    vi.stubGlobal("fetch", fetchMock);

    await expect(bootstrapAuthSession()).resolves.toEqual({
      session: validSession,
      accountManagementAvailable: false
    });
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/auth/session", {
      credentials: "same-origin",
      headers: { Accept: "application/json" }
    });
  });

  it("returns null only for the explicit unauthenticated shape", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(JSON.stringify({ authenticated: false, user: null, session: null }), { status: 200 })
      )
    );

    await expect(bootstrapAuthSession()).resolves.toEqual({
      session: null,
      accountManagementAvailable: false
    });
  });

  it("exposes target account-management capability only when the BFF declares it", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(JSON.stringify({ authenticated: true, ...validSession, accountManagementAvailable: true }), {
          status: 200
        })
      )
    );

    await expect(bootstrapAuthSession()).resolves.toEqual({
      session: validSession,
      accountManagementAvailable: true
    });
  });

  it("rejects malformed bootstrap data instead of guessing authentication", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(JSON.stringify({ authenticated: true, user: validSession.user, session: null }), { status: 200 })
      )
    );

    await expect(bootstrapAuthSession()).rejects.toThrow("세션 확인 응답이 올바르지 않습니다.");
  });

  it("logs in with CSRF and receives only the user and server session view", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-value", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(JSON.stringify(validSession), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(loginWithPassword({ email: "user@example.com", password: "password-123!" })).resolves.toEqual(
      validSession
    );
    const loginInit = fetchMock.mock.calls[1]?.[1];
    expect(fetchMock.mock.calls[1]?.[0]).toBe("/api/v1/auth/login");
    expect(loginInit?.credentials).toBe("same-origin");
    expect(new Headers(loginInit?.headers).get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(JSON.parse(String(loginInit?.body))).toEqual({
      email: "user@example.com",
      password: "password-123!",
      rememberMe: false
    });
  });

  it("logs out idempotently with a fresh CSRF token for each completed session", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-first", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-second", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(logoutCurrentSession()).resolves.toBeUndefined();
    await expect(logoutCurrentSession()).resolves.toBeUndefined();

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/auth/csrf",
      "/api/v1/auth/logout",
      "/api/v1/auth/csrf",
      "/api/v1/auth/logout"
    ]);
    expect(new Headers(fetchMock.mock.calls[1]?.[1]?.headers).get("X-CSRF-TOKEN")).toBe("csrf-first");
    expect(new Headers(fetchMock.mock.calls[3]?.[1]?.headers).get("X-CSRF-TOKEN")).toBe("csrf-second");
  });

  it("keeps the client session usable when the logout server cannot be reached", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-value", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockRejectedValueOnce(new TypeError("network unavailable"));
    vi.stubGlobal("fetch", fetchMock);

    await expect(logoutCurrentSession()).rejects.toThrow(
      "로그아웃 서버에 연결하지 못했습니다. 연결을 확인하고 다시 시도해 주세요."
    );
  });

  it("sends logout-all and password changes through the CSRF BFF boundary", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-logout-all", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-password", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await logoutAllSessions({ password: "Password-123!" });
    await changeCurrentPassword({ currentPassword: "Password-123!", newPassword: "Password-456!" });

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/auth/csrf",
      "/api/v1/auth/logout-all",
      "/api/v1/auth/csrf",
      "/api/v1/auth/password"
    ]);
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({ password: "Password-123!" });
    expect(JSON.parse(String(fetchMock.mock.calls[3]?.[1]?.body))).toEqual({
      currentPassword: "Password-123!",
      newPassword: "Password-456!"
    });
  });

  it("sends account withdrawal only through the CSRF BFF boundary", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-withdrawal", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await withdrawCurrentAccount({ confirmation: "DELETE", password: "Password-123!" });

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/auth/csrf",
      "/api/v1/auth/withdrawal"
    ]);
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({
      confirmation: "DELETE",
      password: "Password-123!"
    });
  });

  it("updates the browser session user from the tokenless profile response", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-profile", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...validSession.user, displayName: "Updated" }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(updateCurrentProfile("Updated")).resolves.toEqual({ ...validSession.user, displayName: "Updated" });
    expect(fetchMock.mock.calls[1]?.[0]).toBe("/api/v1/auth/profile");
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({ displayName: "Updated" });
  });

  it("requests and confirms a password reset only through CSRF BFF endpoints", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-reset-request", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(JSON.stringify({ accepted: true }), { status: 202 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-reset-confirm", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await requestPasswordReset("user@example.com");
    await confirmPasswordReset("mmpr_token", "Password-456!");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/auth/csrf",
      "/api/v1/auth/password-reset-requests",
      "/api/v1/auth/csrf",
      "/api/v1/auth/password-resets"
    ]);
    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({ email: "user@example.com" });
    expect(JSON.parse(String(fetchMock.mock.calls[3]?.[1]?.body))).toEqual({
      token: "mmpr_token",
      newPassword: "Password-456!"
    });
  });
});
