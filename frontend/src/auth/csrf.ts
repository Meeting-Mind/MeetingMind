type CsrfToken = {
  token: string;
  headerName: "X-CSRF-TOKEN";
};

let csrfTokenRequest: Promise<CsrfToken> | null = null;

export async function prepareBffRequest(init: RequestInit = {}): Promise<RequestInit> {
  const request: RequestInit = { ...init, credentials: "same-origin" };
  const method = (request.method || "GET").toUpperCase();
  if (method === "GET" || method === "HEAD" || method === "OPTIONS") {
    return request;
  }

  const csrf = await getCsrfToken();
  const headers = new Headers(request.headers);
  headers.set(csrf.headerName, csrf.token);
  return { ...request, headers };
}

export function resetCsrfToken() {
  csrfTokenRequest = null;
}

export async function bffFetch(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const response = await fetch(input, await prepareBffRequest(init));
  await notifyIfSessionInvalid(response);
  return response;
}

async function getCsrfToken(): Promise<CsrfToken> {
  if (!csrfTokenRequest) {
    csrfTokenRequest = fetch("/api/v1/auth/csrf", {
      credentials: "same-origin",
      headers: { Accept: "application/json" }
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`CSRF token request failed with ${response.status}`);
        }
        const payload = (await response.json()) as Partial<CsrfToken>;
        if (!payload.token || payload.headerName !== "X-CSRF-TOKEN") {
          throw new Error("CSRF token response is invalid");
        }
        return payload as CsrfToken;
      })
      .catch((error) => {
        csrfTokenRequest = null;
        throw error;
      });
  }
  return csrfTokenRequest;
}
import { notifyIfSessionInvalid } from "./sessionInvalidation";
