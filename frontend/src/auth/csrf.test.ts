import { afterEach, describe, expect, it, vi } from "vitest";
import { bffFetch, prepareBffRequest, resetCsrfToken } from "./csrf";
import { subscribeToSessionInvalid } from "./sessionInvalidation";

afterEach(() => {
  resetCsrfToken();
  vi.unstubAllGlobals();
});

describe("prepareBffRequest", () => {
  it("uses the cookie session without fetching CSRF for a safe request", async () => {
    const fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal("fetch", fetchMock);

    await expect(prepareBffRequest()).resolves.toEqual({ credentials: "same-origin" });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("fetches and caches the server CSRF token for state-changing requests", async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      new Response(JSON.stringify({ token: "csrf-value", headerName: "X-CSRF-TOKEN" }), { status: 200 })
    );
    vi.stubGlobal("fetch", fetchMock);

    const first = await prepareBffRequest({ method: "POST", headers: { "Content-Type": "application/json" } });
    const second = await prepareBffRequest({ method: "DELETE" });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(new Headers(first.headers).get("X-CSRF-TOKEN")).toBe("csrf-value");
    expect(new Headers(first.headers).get("Content-Type")).toBe("application/json");
    expect(new Headers(second.headers).get("X-CSRF-TOKEN")).toBe("csrf-value");
  });

  it("rejects an unexpected CSRF header name", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(JSON.stringify({ token: "csrf-value", headerName: "X-Injected-Header" }), { status: 200 })
      )
    );

    await expect(prepareBffRequest({ method: "POST" })).rejects.toThrow("CSRF token response is invalid");
  });

  it("publishes only the final SESSION_INVALID 401 through the common BFF fetch boundary", async () => {
    const listener = vi.fn();
    const unsubscribe = subscribeToSessionInvalid(listener);
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ code: "INVALID_CREDENTIALS", message: "로그인 실패" }), { status: 401 })
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ code: "SESSION_INVALID", message: "로그인이 만료되었습니다." }), { status: 401 })
      );
    vi.stubGlobal("fetch", fetchMock);

    try {
      await bffFetch("/api/v1/auth/login");
      expect(listener).not.toHaveBeenCalled();

      const finalResponse = await bffFetch("/api/v1/spaces");
      expect(finalResponse.status).toBe(401);
      expect(listener).toHaveBeenCalledTimes(1);
      await expect(finalResponse.json()).resolves.toMatchObject({ code: "SESSION_INVALID" });
    } finally {
      unsubscribe();
    }
  });
});
