import { bffFetch, resetCsrfToken } from "../auth/csrf";

export class ApiRequestError extends Error {
  status: number;
  code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.code = code;
  }
}

export function jsonHeaders(): HeadersInit {
  return {
    "Content-Type": "application/json"
  };
}

export async function requestJson<T>(path: string, init: RequestInit): Promise<T> {
  const response = await bffFetch(path, init);

  if (!response.ok) {
    if (response.status === 403) {
      resetCsrfToken();
    }
    const error = await readErrorPayload(response);
    throw new ApiRequestError(response.status, error.message || `API request failed with ${response.status}`, error.code);
  }

  return (await response.json()) as T;
}

export async function requestBlob(path: string, init: RequestInit): Promise<Blob> {
  const response = await bffFetch(path, init);

  if (!response.ok) {
    if (response.status === 403) {
      resetCsrfToken();
    }
    const error = await readErrorPayload(response);
    throw new ApiRequestError(response.status, error.message || `API request failed with ${response.status}`, error.code);
  }

  return response.blob();
}

async function readErrorPayload(response: Response): Promise<{ message: string; code?: string }> {
  try {
    const text = await response.text();
    if (!text) {
      return { message: "" };
    }

    try {
      const payload = JSON.parse(text) as { message?: string; code?: string };
      return { message: payload.message || text, code: payload.code };
    } catch {
      return { message: text };
    }
  } catch {
    return { message: "" };
  }
}
