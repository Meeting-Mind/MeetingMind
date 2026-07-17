import { bffFetch, resetCsrfToken } from "./csrf";

export type AuthUser = {
  id: string;
  email: string;
  displayName: string;
  pictureUrl?: string | null;
  status: string;
};

export type AuthSessionView = {
  expiresAt: string;
  idleExpiresAt: string;
  rememberMe: boolean;
};

export type AuthSession = {
  user: AuthUser;
  session: AuthSessionView;
};

type AuthSessionBootstrap = {
  authenticated: boolean;
  user: AuthUser | null;
  session: AuthSessionView | null;
};

export async function bootstrapAuthSession(): Promise<AuthSession | null> {
  const response = await fetch("/api/v1/auth/session", {
    credentials: "same-origin",
    headers: { Accept: "application/json" }
  });
  if (!response.ok) {
    throw new Error(`세션 확인 실패 (${response.status})`);
  }

  const payload = (await response.json()) as Partial<AuthSessionBootstrap>;
  if (payload.authenticated === false && payload.user == null && payload.session == null) {
    return null;
  }
  if (payload.authenticated !== true || !isAuthUser(payload.user) || !isSessionView(payload.session)) {
    throw new Error("세션 확인 응답이 올바르지 않습니다.");
  }
  return { user: payload.user, session: payload.session };
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
  return authRequest("/api/v1/auth/signup", { email, password, displayName, rememberMe: false });
}

export async function loginWithPassword({ email, password }: { email: string; password: string }) {
  return authRequest("/api/v1/auth/login", { email, password, rememberMe: false });
}

export async function loginWithGoogle(credential: string) {
  return authRequest("/api/v1/auth/google", { credential, rememberMe: false });
}

export async function logoutCurrentSession(): Promise<void> {
  let response: Response;
  try {
    response = await bffFetch(
      "/api/v1/auth/logout",
      {
        method: "POST",
        headers: { Accept: "application/json" }
      }
    );
  } catch {
    throw new Error("로그아웃 서버에 연결하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.");
  }

  if (response.status !== 204) {
    if (response.status === 403) {
      resetCsrfToken();
    }
    const message = await readErrorMessage(response);
    throw new Error(message || `로그아웃 요청 실패 (${response.status})`);
  }

  resetCsrfToken();
}

async function authRequest(path: string, body: Record<string, string | boolean>): Promise<AuthSession> {
  const response = await bffFetch(
    path,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify(body)
    }
  );

  if (!response.ok) {
    if (response.status === 403) {
      resetCsrfToken();
    }
    const message = await readErrorMessage(response);
    throw new Error(message || `인증 요청 실패 (${response.status})`);
  }

  const payload = (await response.json()) as Partial<AuthSession>;
  if (!isAuthUser(payload.user) || !isSessionView(payload.session)) {
    throw new Error("인증 응답이 올바르지 않습니다.");
  }
  resetCsrfToken();
  return { user: payload.user, session: payload.session };
}

function isAuthUser(value: unknown): value is AuthUser {
  if (!value || typeof value !== "object") {
    return false;
  }
  const user = value as Partial<AuthUser>;
  return Boolean(user.id && user.email && user.displayName && user.status);
}

function isSessionView(value: unknown): value is AuthSessionView {
  if (!value || typeof value !== "object") {
    return false;
  }
  const session = value as Partial<AuthSessionView>;
  return Boolean(
    session.expiresAt &&
      session.idleExpiresAt &&
      typeof session.rememberMe === "boolean" &&
      !Number.isNaN(Date.parse(session.expiresAt)) &&
      !Number.isNaN(Date.parse(session.idleExpiresAt))
  );
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
