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

export type AuthBootstrap = {
  session: AuthSession | null;
  accountManagementAvailable: boolean;
};

type AuthSessionBootstrap = {
  authenticated: boolean;
  user: AuthUser | null;
  session: AuthSessionView | null;
  accountManagementAvailable?: boolean;
};

export async function bootstrapAuthSession(): Promise<AuthBootstrap> {
  const response = await fetch("/api/v1/auth/session", {
    credentials: "same-origin",
    headers: { Accept: "application/json" }
  });
  if (!response.ok) {
    throw new Error(`세션 확인 실패 (${response.status})`);
  }

  const payload = (await response.json()) as Partial<AuthSessionBootstrap>;
  const accountManagementAvailable = payload.accountManagementAvailable === true;
  if (payload.authenticated === false && payload.user == null && payload.session == null) {
    return { session: null, accountManagementAvailable };
  }
  if (payload.authenticated !== true || !isAuthUser(payload.user) || !isSessionView(payload.session)) {
    throw new Error("세션 확인 응답이 올바르지 않습니다.");
  }
  return {
    session: { user: payload.user, session: payload.session },
    accountManagementAvailable
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

export async function logoutAllSessions({
  password,
  googleCredential
}: {
  password?: string;
  googleCredential?: string;
} = {}): Promise<void> {
  await noContentAuthRequest("/api/v1/auth/logout-all", { password, googleCredential }, "모든 기기 로그아웃");
}

export async function changeCurrentPassword({
  currentPassword,
  newPassword
}: {
  currentPassword: string;
  newPassword: string;
}): Promise<void> {
  await noContentAuthRequest("/api/v1/auth/password", { currentPassword, newPassword }, "비밀번호 변경");
}

export async function withdrawCurrentAccount({
  confirmation,
  password,
  googleCredential
}: {
  confirmation: "DELETE";
  password?: string;
  googleCredential?: string;
}): Promise<void> {
  await noContentAuthRequest(
    "/api/v1/auth/withdrawal",
    { confirmation, password, googleCredential },
    "계정 탈퇴"
  );
}

export async function updateCurrentProfile(displayName: string): Promise<AuthUser> {
  const response = await bffFetch("/api/v1/auth/profile", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ displayName })
  });
  if (!response.ok) {
    if (response.status === 403) {
      resetCsrfToken();
    }
    const message = await readErrorMessage(response);
    throw new Error(message || `프로필 수정 요청 실패 (${response.status})`);
  }
  const user = (await response.json()) as unknown;
  if (!isAuthUser(user)) {
    throw new Error("프로필 수정 응답이 올바르지 않습니다.");
  }
  resetCsrfToken();
  return user;
}

export async function updateCurrentProfileImage(image: File): Promise<AuthUser> {
  const body = new FormData();
  body.append("image", image, image.name);
  const response = await bffFetch("/api/v1/auth/profile-image", {
    method: "POST",
    body
  });
  if (!response.ok) {
    if (response.status === 403) {
      resetCsrfToken();
    }
    const message = await readErrorMessage(response);
    throw new Error(message || `프로필 사진 업로드 실패 (${response.status})`);
  }
  const user = (await response.json()) as unknown;
  if (!isAuthUser(user)) {
    throw new Error("프로필 사진 응답이 올바르지 않습니다.");
  }
  resetCsrfToken();
  return user;
}

export async function requestPasswordReset(email: string): Promise<void> {
  const response = await bffFetch("/api/v1/auth/password-reset-requests", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email })
  });
  if (response.status !== 202) {
    if (response.status === 403) {
      resetCsrfToken();
    }
    const message = await readErrorMessage(response);
    throw new Error(message || `비밀번호 재설정 요청 실패 (${response.status})`);
  }
  resetCsrfToken();
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  await noContentAuthRequest("/api/v1/auth/password-resets", { token, newPassword }, "비밀번호 재설정");
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

async function noContentAuthRequest(
  path: string,
  body: Record<string, string | undefined>,
  action: string
): Promise<void> {
  const response = await bffFetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (response.status !== 204) {
    if (response.status === 403) {
      resetCsrfToken();
    }
    const message = await readErrorMessage(response);
    throw new Error(message || `${action} 요청 실패 (${response.status})`);
  }
  resetCsrfToken();
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
