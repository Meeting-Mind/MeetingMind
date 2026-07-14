import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const password = "CiPass123!";
const sessionStorageKey = "meetingmind.auth.session";

type AuthSession = {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  refreshExpiresIn: number;
  user: {
    id: string;
    email: string;
    displayName: string;
    status: string;
  };
};

async function signup(request: APIRequestContext, label: string): Promise<AuthSession> {
  const response = await request.post("/api/v1/auth/signup", {
    data: {
      displayName: `CI ${label}`,
      email: `${label}-${Date.now()}@example.com`,
      password
    }
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as AuthSession;
}

async function storeSession(page: Page, session: AuthSession) {
  await page.addInitScript(
    ({ key, value }) => window.sessionStorage.setItem(key, value),
    { key: sessionStorageKey, value: JSON.stringify(session) }
  );
}

test.beforeEach(async ({ page }) => {
  await page.route("https://accounts.google.com/**", (route) => route.abort());
});

test("password login opens the requested protected route", async ({ page, request }) => {
  const session = await signup(request, "login");

  await page.goto("/spaces");
  const dialog = page.getByRole("dialog", { name: "로그인 후 이용할 수 있습니다" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("이메일").fill(session.user.email);
  await dialog.getByLabel("비밀번호").fill(password);
  await dialog.locator("form").getByRole("button", { name: "로그인", exact: true }).click();

  await expect(page).toHaveURL(/\/spaces$/);
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("textbox", { name: "프로젝트 검색" })).toBeVisible();
});

test("meeting prejoin allows an active HOST and denies an unknown meeting", async ({ page, request }) => {
  const session = await signup(request, "meeting-access");
  const authorization = { Authorization: `Bearer ${session.accessToken}` };

  const spaceResponse = await request.post("/api/v1/spaces", {
    data: { name: "CI access space", description: "Playwright meeting access verification" },
    headers: authorization
  });
  expect(spaceResponse.ok()).toBeTruthy();
  const space = (await spaceResponse.json()) as { id: string };

  const meetingResponse = await request.post(`/api/v1/spaces/${space.id}/meetings`, {
    data: {
      title: "CI permission meeting",
      scheduledAt: "2030-01-01T09:00:00+09:00",
      participantUserIds: []
    },
    headers: authorization
  });
  expect(meetingResponse.ok()).toBeTruthy();
  const meeting = (await meetingResponse.json()) as { id: string };

  await storeSession(page, session);
  await page.goto(`/live-meeting?spaceId=${space.id}&meetingId=${meeting.id}&meeting=CI+permission+meeting`);
  await expect(page.getByText("참여 권한 확인됨")).toBeVisible();
  await expect(page.getByText("HOST 권한으로 입장합니다")).toBeVisible();

  await page.goto("/live-meeting?meetingId=missing-meeting");
  await expect(page.getByRole("heading", { name: "회의에 접근할 수 없습니다" })).toBeVisible();
  await expect(page.getByText("default-deny")).toBeVisible();
});
