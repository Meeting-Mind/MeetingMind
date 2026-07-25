import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

const password = "CiPass123!";
const backendBaseUrl = `http://127.0.0.1:${process.env.PLAYWRIGHT_BACKEND_PORT ?? "8080"}`;

type AuthSession = { accessToken: string; user: { id: string; email: string } };

async function signup(request: APIRequestContext, label: string): Promise<AuthSession> {
  const response = await request.post(`${backendBaseUrl}/api/v1/auth/signup`, {
    data: { displayName: `CI ${label}`, email: `${label}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`, password }
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as AuthSession;
}

async function signIn(page: Page, session: AuthSession, targetPath: string) {
  await page.goto(targetPath);
  const signInPage = page.getByTestId("sign-in-page");
  await expect(signInPage).toBeVisible();
  await signInPage.getByTestId("sign-in-email").fill(session.user.email);
  await signInPage.getByTestId("sign-in-password").fill(password);
  await signInPage.getByTestId("sign-in-submit").click();
  await expect(signInPage).toBeHidden();
}

test("회의록 탭이 렌더된다", async ({ page, request }) => {
  await page.route("https://accounts.google.com/**", (route) => route.abort());
  const owner = await signup(request, "report-smoke");
  const auth = { Authorization: `Bearer ${owner.accessToken}` };
  const stamp = Date.now();

  const space = await (await request.post(`${backendBaseUrl}/api/v1/spaces`, { headers: auth, data: { name: `보고서 ${stamp}`, description: "x" } })).json();
  const meetingResponse = await request.post(`${backendBaseUrl}/api/v1/spaces/${space.id}/meetings`, {
    headers: auth,
    data: { title: `회의 ${stamp}`, description: "x", scheduledAt: new Date(stamp).toISOString(), scheduledEndAt: new Date(stamp + 3600000).toISOString(), participantUserIds: [] }
  });
  expect(meetingResponse.ok(), await meetingResponse.text()).toBeTruthy();
  const meeting = await meetingResponse.json();

  await signIn(page, owner, `/spaces/${space.id}/meetings/${meeting.id}/report`);
  await page.goto(`/spaces/${space.id}/meetings/${meeting.id}/report`);

  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));

  // 전사가 없으므로 빈 상태가 떠야 한다. 화면이 죽으면 아무것도 안 뜬다.
  await expect(page.getByText("아직 회의록이 없습니다")).toBeVisible({ timeout: 15000 });
  await expect(page.getByText("회의록을 만들 전사 내용이 없습니다.")).toBeVisible();
  expect(errors, `런타임 오류: ${errors.join(" / ")}`).toEqual([]);
});
