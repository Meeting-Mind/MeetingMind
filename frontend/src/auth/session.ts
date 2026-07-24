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

export class ReauthenticationRequiredError extends Error {
  constructor(message = "모든 기기 로그아웃을 위해 다시 인증해 주세요.") {
    super(message);
    this.name = "ReauthenticationRequiredError";
  }
}

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

export async function updateProfile({
  displayName,
  pictureUrl
}: {
  displayName: string;
  pictureUrl: string | null;
}): Promise<AuthUser> {
  const response = await bffFetch("/api/v1/auth/profile", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ displayName, pictureUrl })
  });
  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message || `프로필 저장 실패 (${response.status})`);
  }
  const user = (await response.json()) as Partial<AuthUser>;
  if (!isAuthUser(user)) {
    throw new Error("프로필 저장 응답이 올바르지 않습니다.");
  }
  return user;
}

export async function uploadProfileImage(file: File): Promise<string> {
  const body = new FormData();
  body.append("file", file);
  const response = await bffFetch("/api/v1/assets/profile-image", { method: "POST", body });
  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message || `프로필 이미지 업로드 실패 (${response.status})`);
  }
  const payload = (await response.json()) as { imageUrl?: unknown };
  if (typeof payload.imageUrl !== "string" || !payload.imageUrl) {
    throw new Error("프로필 이미지 업로드 응답이 올바르지 않습니다.");
  }
  return payload.imageUrl;
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

export async function logoutAllDevices(): Promise<void> {
  let response: Response;
  try {
    response = await bffFetch("/api/v1/auth/logout-all", {
      method: "POST",
      headers: { Accept: "application/json" }
    });
  } catch {
    throw new Error("전체 로그아웃 서버에 연결하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.");
  }

  if (response.status !== 204) {
    const error = await readError(response);
    if (response.status === 403 && error.code === "REAUTHENTICATION_REQUIRED") {
      throw new ReauthenticationRequiredError(error.message);
    }
    throw new Error(error.message || `모든 기기 로그아웃 요청 실패 (${response.status})`);
  }

  resetCsrfToken();
}

export async function reauthenticateWithPassword(password: string): Promise<void> {
  return reauthenticate({ method: "PASSWORD", password });
}

export async function reauthenticateWithGoogle(credential: string): Promise<void> {
  return reauthenticate({ method: "GOOGLE", credential });
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

async function reauthenticate(body: Record<string, string>): Promise<void> {
  let response: Response;
  try {
    response = await bffFetch("/api/v1/auth/reauthenticate", {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json"
      },
      body: JSON.stringify(body)
    });
  } catch {
    throw new Error("재인증 서버에 연결하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.");
  }

  if (response.status !== 204) {
    const error = await readError(response);
    throw new Error(error.message || `재인증 요청 실패 (${response.status})`);
  }
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
  return (await readError(response)).message;
}

async function readError(response: Response): Promise<{ code: string; message: string }> {
  try {
    const text = await response.text();
    if (!text) {
      return { code: "", message: "" };
    }

    try {
      const payload = JSON.parse(text) as { code?: string; message?: string };
      return {
        code: payload.code || "",
        message: payload.message || text
      };
    } catch {
      return { code: "", message: text };
    }
  } catch {
    return { code: "", message: "" };
  }
}
