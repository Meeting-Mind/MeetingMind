export type AuthUser = {
  id: string;
  email: string;
  displayName: string;
  pictureUrl?: string | null;
  status: string;
};

export type AuthSession = {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  refreshExpiresIn: number;
  user: AuthUser;
};

const STORAGE_KEY = "meetingmind.auth.session";
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.trim() || "";

export function readStoredAuthSession(): AuthSession | null {
  return parseStoredAuthSession(sessionStorage.getItem(STORAGE_KEY));
}

export function parseStoredAuthSession(raw: string | null): AuthSession | null {
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<AuthSession>;
    if (!parsed.accessToken || !parsed.refreshToken || parsed.tokenType !== "Bearer" || !parsed.user?.id) {
      return null;
    }
    return parsed as AuthSession;
  } catch {
    return null;
  }
}

export function saveAuthSession(session: AuthSession) {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearAuthSession() {
  sessionStorage.removeItem(STORAGE_KEY);
}

export function buildAuthHeaders(session: AuthSession | null): HeadersInit | undefined {
  if (!session?.accessToken) {
    return undefined;
  }

  return {
    Authorization: `Bearer ${session.accessToken}`
  };
}

export async function signupWithPassword({
  email,
  password,
  displayName
}: {
  email: string;
  password: string;
  displayName: string;
}) {
  return authRequest("/api/v1/auth/signup", { email, password, displayName });
}

export async function loginWithPassword({ email, password }: { email: string; password: string }) {
  return authRequest("/api/v1/auth/login", { email, password });
}

export async function loginWithGoogle(credential: string) {
  return authRequest("/api/v1/auth/google", { credential });
}

async function authRequest(path: string, body: Record<string, string>): Promise<AuthSession> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message || `인증 요청 실패 (${response.status})`);
  }

  return (await response.json()) as AuthSession;
}

async function readErrorMessage(response: Response) {
  try {
    const text = await response.text();
    if (!text) {
      return "";
    }

    try {
      const payload = JSON.parse(text) as { message?: string };
      return payload.message || text;
    } catch {
      return text;
    }
  } catch {
    return "";
  }
}
