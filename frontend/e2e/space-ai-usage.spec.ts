import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

/**
 * T439.1: Space 개요의 AI 사용량 카드가 실제로 렌더되는지 확인한다.
 *
 * 빌드가 통과한다는 것은 화면이 뜬다는 뜻이 아니다. 카드가 조건부 렌더(`aiUsage ? ... : null`)라
 * 조회가 실패하면 조용히 사라지는데, 빌드도 단위 테스트도 그것을 잡지 못한다.
 *
 * quota는 Playwright backend에서 설정되지 않으므로(`MEETINGMIND_AI_TOKEN_QUOTA` 미설정)
 * limit/usagePercent가 null이고 진행률 막대는 렌더되지 않는다. 이 상태에서도 카드 자체는
 * 보여야 한다는 것이 이 테스트의 요지다.
 */

const password = "CiPass123!";
const backendBaseUrl = `http://127.0.0.1:${process.env.PLAYWRIGHT_BACKEND_PORT ?? "8080"}`;

type AuthSession = {
  accessToken: string;
  user: { id: string; email: string };
};

async function signup(request: APIRequestContext, label: string): Promise<AuthSession> {
  const response = await request.post(`${backendBaseUrl}/api/v1/auth/signup`, {
    data: {
      displayName: `CI ${label}`,
      email: `${label}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`,
      password
    }
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

test.beforeEach(async ({ page }) => {
  await page.route("https://accounts.google.com/**", (route) => route.abort());
});

test("space overview shows the AI usage card", async ({ page, request }) => {
  const owner = await signup(request, "ai-usage");

  const spaceResponse = await request.post(`${backendBaseUrl}/api/v1/spaces`, {
    headers: { Authorization: `Bearer ${owner.accessToken}` },
    data: { name: `AI Usage Space ${Date.now()}`, description: "T439.1 usage card" }
  });
  expect(spaceResponse.ok()).toBeTruthy();
  const spaceId = (await spaceResponse.json()).id as string;

  await signIn(page, owner, `/spaces/${spaceId}`);
  await page.goto(`/spaces/${spaceId}`);

  await expect(page.getByRole("heading", { name: "AI Usage This Month" })).toBeVisible();
  // 아직 AI를 쓰지 않았으므로 빈 상태 문구가 보여야 한다. 카드만 있고 내용이 비어 있으면
  // 조회가 실패한 것과 구분되지 않는다.
  await expect(page.getByText("No AI usage recorded yet.")).toBeVisible();
  await expect(page.getByText("total tokens")).toBeVisible();
});
