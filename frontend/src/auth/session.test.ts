import { afterEach, describe, expect, it, vi } from "vitest";
import { resetCsrfToken } from "./csrf";
import {
  bootstrapAuthSession,
  loginWithPassword,
  logoutAllDevices,
  logoutCurrentSession,
  reauthenticateWithGoogle,
  reauthenticateWithPassword,
  ReauthenticationRequiredError,
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

    await expect(bootstrapAuthSession()).resolves.toEqual(validSession);
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

    await expect(bootstrapAuthSession()).resolves.toBeNull();
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

  it("requires step-up authentication without clearing the active CSRF session", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-value", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            code: "REAUTHENTICATION_REQUIRED",
            message: "다시 인증해 주세요."
          }),
          { status: 403 }
        )
      );
    vi.stubGlobal("fetch", fetchMock);

    await expect(logoutAllDevices()).rejects.toBeInstanceOf(ReauthenticationRequiredError);
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      "/api/v1/auth/csrf",
      "/api/v1/auth/logout-all"
    ]);
  });

  it("sends only method-specific reauthentication proof and then logs out all devices", async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ token: "csrf-value", headerName: "X-CSRF-TOKEN" }), { status: 200 })
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(reauthenticateWithPassword("password-123!")).resolves.toBeUndefined();
    await expect(reauthenticateWithGoogle("google-credential")).resolves.toBeUndefined();
    await expect(logoutAllDevices()).resolves.toBeUndefined();

    expect(JSON.parse(String(fetchMock.mock.calls[1]?.[1]?.body))).toEqual({
      method: "PASSWORD",
      password: "password-123!"
    });
    expect(JSON.parse(String(fetchMock.mock.calls[2]?.[1]?.body))).toEqual({
      method: "GOOGLE",
      credential: "google-credential"
    });
    expect(fetchMock.mock.calls[3]?.[0]).toBe("/api/v1/auth/logout-all");
  });
});
